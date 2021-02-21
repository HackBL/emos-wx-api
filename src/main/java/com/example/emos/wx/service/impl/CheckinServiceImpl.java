package com.example.emos.wx.service.impl;

import cn.hutool.core.date.DateField;
import cn.hutool.core.date.DateRange;
import cn.hutool.core.date.DateTime;
import cn.hutool.core.date.DateUtil;
import cn.hutool.core.io.FileUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.http.HttpRequest;
import cn.hutool.http.HttpResponse;
import cn.hutool.http.HttpUtil;
import com.example.emos.wx.config.SystemConstants;
import com.example.emos.wx.db.dao.*;
import com.example.emos.wx.db.pojo.TbCheckin;
import com.example.emos.wx.db.pojo.TbFaceModel;
import com.example.emos.wx.exception.EmosException;
import com.example.emos.wx.service.CheckinService;
import com.example.emos.wx.task.EmailTask;
import lombok.extern.slf4j.Slf4j;
import org.apache.shiro.crypto.hash.Hash;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Scope;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.stereotype.Service;

import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;

@Service
@Scope("prototype") // 成为多例的对象, 实现线程的异步执行
@Slf4j
public class CheckinServiceImpl implements CheckinService {
    @Autowired
    private SystemConstants constants;

    @Autowired
    private TbHolidaysDao holidaysDao;

    @Autowired
    private TbWorkdayDao workdayDao;

    @Autowired
    private TbCheckinDao checkinDao;

    @Autowired
    private TbFaceModelDao faceModelDao;

    @Autowired
    private TbCityDao cityDao;

    @Autowired
    private TbUserDao userDao;

    @Value("${emos.face.createFaceModelUrl}")
    private String createFaceModelUrl;

    @Value("${emos.face.checkinUrl}")
    private String checkinUrl;

    @Value("${emos.email.hr}")
    private String hrEmail;

    @Value("${emos.code}")  // code to access to Python Model
    private String code;

    @Autowired
    private EmailTask emailTask;


    /**
     *  判断当前时间是否可以checkin
     *  判断用户是否已经考勤
     * */
    @Override
    public String validCanCheckIn(int userId, String date) {
        boolean isHoliday = holidaysDao.searchTodayIsHolidays() != null;
        boolean isWorkDay = workdayDao.searchTodayIsWorkday() != null;
        String type = "工作日";

        if (DateUtil.date().isWeekend()) {
            type = "节假日";
        }

        if (isHoliday) {
            type = "节假日";
        }
        else if (isWorkDay) {
            type = "工作日";
        }

        // 当天是节假日
        if ("节假日".equals(type)) {
            return "节假日不需要考勤";
        }
        // 当天是工作日
        else {
            DateTime now = DateUtil.date();
            String start = DateUtil.today() + " " + constants.attendanceStartTime;
            String end = DateUtil.today() + " " + constants.attendanceEndTime;
            DateTime attendanceStart = DateUtil.parse(start);
            DateTime attendanceEnd = DateUtil.parse(end);

            if (now.isBefore(attendanceStart)) {
                return "没到上班考勤开始时间";
            }
            else if (now.isAfterOrEquals(attendanceEnd)) {
                return "超过了上班考勤结束时间";
            }
            else {
                HashMap map = new HashMap();
                map.put("userId", userId);
                map.put("date", date);
                map.put("start", start);
                map.put("end", end);

                // 判断用户是否已经考勤
                boolean checkedIn = checkinDao.haveCheckin(map) != null;
                return checkedIn ? "今日已经考勤，不用重复考勤" : "可以考勤";
            }
        }
    }

    /**
     *  在签到时间内进行签到
     *  判断签到用户表是否存在人脸模型
     *  实现发送告警邮件
     * */
    @Override
    public void checkin(HashMap param) {
        DateTime now = DateUtil.date();
        DateTime attend = DateUtil.parse(DateUtil.today() + " " + constants.attendanceTime);
        DateTime end = DateUtil.parse(DateUtil.today() + " " + constants.attendanceEndTime);
        int status = 1; // 1: 正常签到, 2: 迟到

        if (now.isBeforeOrEquals(attend)) {
            status = 1;
        }
        else if (now.isAfter(attend) && now.isBefore(end)) {
            status = 2;
        }
        int userId = (Integer) param.get("userId");
        String faceModel = faceModelDao.searchFaceModel(userId);

        if (faceModel == null) {    // no faceModel from DB on cur user
            throw new EmosException("不存在人脸模型");
        }
        else {
            String path = (String) param.get("path");
            HttpRequest request = HttpUtil.createPost(checkinUrl); // send request to Python via HTTP
            request.form("photo", FileUtil.file(path), "targetModel", faceModel); // Both faceModel from client & DB send request to Python to analyze
            request.form("code", code);
            HttpResponse response = request.execute();
            if (response.getStatus() != 200) {
                log.error("人脸识别服务异常");
                throw new EmosException("人脸识别服务异常");
            }

            // Result of Response by Python
            String body = response.body();
            if ("无法识别出人脸".equals(body) || "照片中存在多张人脸".equals(body)) {
                throw new EmosException(body);
            }
            else if ("False".equals(body)) {
                throw new EmosException("签到无效，非本人签到");
            }
            else if ("True".equals(body)) {
                // 查询疫情风险等级
                int risk = 1; // 1: low risk, 2: medium risk, 3: high risk
                String address = (String) param.get("address");
                String country = (String) param.get("country");
                String province = (String) param.get("province");
                String city = (String) param.get("city");
                String district = (String) param.get("district");

                if (!StrUtil.isBlank(city) && !StrUtil.isBlank(district)) {
                    String code = cityDao.searchCode(city);
                    // 调用"本地宝"得知疫情风险等级
                    try {
                        String url = "http://m." + code + ".bendibao.com/news/yqdengji/?qu=" + district;
                        Document document = Jsoup.connect(url).get();
                        Elements elements = document.getElementsByClass("list-content");
                        if (elements.size() > 0) {
                            Element element = elements.get(0);
                            String result = element.select("p:last-child").text();

                            if ("高风险".equals(result)) {
                                risk = 3;
                                // 发送告警邮件
                                HashMap<String, String> map = userDao.searchNameAndDept(userId);
                                String name = map.get("name");
                                String deptName = map.get("dept_name");
                                deptName = deptName != null ? deptName : "";
                                SimpleMailMessage message = new SimpleMailMessage();
                                message.setTo(hrEmail);
                                message.setSubject("员工" + name + "身处高风险疫情地区警告");
                                message.setText(deptName + "员工" + name + "，" + DateUtil.format(new Date(), "yyyy年MM月dd日") + "处于" + address + "，属于新冠疫情高风险地区，请及时与该员工联系，核实情况！");
                                message.setSentDate(DateUtil.date());   //发送日期
                                emailTask.sendAsync(message);
                            }
                            else if ("中风险".equals(result)) {
                                risk = 2;
                            }
                        }
                    } catch (Exception e) {
                        log.error("执行异常", e);
                        throw new EmosException("获取风险等级失败");
                    }
                }

                // 保存签到记录
                TbCheckin entity = new TbCheckin();
                entity.setUserId(userId);
                entity.setAddress(address);
                entity.setCountry(country);
                entity.setProvince(province);
                entity.setCity(city);
                entity.setDistrict(district);
                entity.setStatus((byte) status);
                entity.setRisk(risk);
                entity.setDate(DateUtil.today());   // include date without time
                entity.setCreateTime(now);

                checkinDao.insertCheckin(entity);
            }
        }
    }

    /**
     *  上传人脸模型到数据库
     * */
    @Override
    public void createFaceModel(int userId, String path) {
        HttpRequest request = HttpUtil.createPost(createFaceModelUrl);  // send request to Python via HTTP
        request.form("photo", FileUtil.file(path));
        request.form("code", code);
        HttpResponse response = request.execute();

        String body = response.body(); // 响应体内容
        if ("无法识别出人脸".equals(body) || "照片中存在多张人脸".equals(body)) {
            throw new EmosException(body);
        }

        TbFaceModel entity = new TbFaceModel();
        entity.setUserId(userId);
        entity.setFaceModel(body);
        faceModelDao.insertFaceModel(entity);
    }

    /**
     *  查看用户当天签到信息
     *  For checkin result page
     * */
    @Override
    public HashMap searchTodayCheckin(int userId) {
        HashMap map = checkinDao.searchTodayCheckin(userId);
        return map;
    }

    /**
     *  查询用户总的签到天数
     *  For checkin result page
     * */
    @Override
    public long searchCheckinDays(int userId) {
        long days = checkinDao.searchCheckinDays(userId);
        return days;
    }

    /**
     *  查询用户时间范围内签到状态记录
     *  For checkin result page bottom layer
     *  需要参数：startDate，endDate，userId
     * */
    @Override
    public ArrayList<HashMap> searchWeekCheckin(HashMap param) {
        ArrayList<HashMap> checkinList = checkinDao.searchWeekCheckin(param);           // 查询时间范围内用户考勤情况(签到时间，签到状态)
        ArrayList<String> holidaysList = holidaysDao.searchHolidaysInRange(param);      // 查询时间范围内哪天是特殊节假日
        ArrayList<String> workdayList = workdayDao.searchWorkdayInRange(param);         // 查询时间范围内哪天是特殊工作日
        DateTime startDate = DateUtil.parseDate(param.get("startDate").toString());
        DateTime endDate = DateUtil.parseDate(param.get("endDate").toString());
        DateRange range = DateUtil.range(startDate, endDate, DateField.DAY_OF_MONTH);   // 时间范围

        ArrayList<HashMap> list = new ArrayList<>();

        // every day in a range of dates
        for (DateTime one: range) {
            String date = one.toString("yyyy-MM-dd");
            // 当天日子
            String type = "工作日";
            if (one.isWeekend()) {  // 周末休息日
                type = "休息日";
            }
            if (holidaysList != null && holidaysList.contains(date)) {      // 特殊节假日
                type = "休息日";
            }
            else if (workdayList != null && workdayList.contains(date)) {   // 特殊工作日
                type = "工作日";
            }

            // 考勤结果
            String status = "";     // 空字符串：未到考勤结束时间，未来日期，不记录考勤结果
            if (type.equals("工作日") && one.isBeforeOrEquals(DateUtil.date())) {  // 查询日期 <= 当前日期
                status = "缺勤";  // 默认为缺勤状态
                boolean flag = false;   // 如果当天在签到结束前签过到，被记录下来

                for (HashMap<String, String> map: checkinList) {
                    if (map.containsValue(date)) {  // 用户已经签过到
                        status = map.get("status");
                        flag = true;
                        break;
                    }
                }

                DateTime endTime = DateUtil.parse(DateUtil.today() + " " + constants.attendanceEndTime);    // 当天签到结束时间
                String today = DateUtil.today();
                if (date.equals(today) && DateUtil.date().isBefore(endTime) && !flag) { // 当天签到结束前，并且没有签到，无记录
                    status = "";
                }
            }

            HashMap map = new HashMap();
            map.put("date", date);
            map.put("status", status);
            map.put("type", type);
            map.put("day", one.dayOfWeekEnum().toChinese("周"));  // day of week
            list.add(map);
        }

        return list;
    }

    @Override
    public ArrayList<HashMap> searchMonthCheckin(HashMap param) {
        return this.searchWeekCheckin(param);
    }
}


