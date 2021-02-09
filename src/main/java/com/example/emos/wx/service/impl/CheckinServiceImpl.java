package com.example.emos.wx.service.impl;

import cn.hutool.core.date.DateTime;
import cn.hutool.core.date.DateUtil;
import cn.hutool.core.io.FileUtil;
import cn.hutool.http.HttpRequest;
import cn.hutool.http.HttpResponse;
import cn.hutool.http.HttpUtil;
import com.example.emos.wx.config.SystemConstants;
import com.example.emos.wx.db.dao.TbCheckinDao;
import com.example.emos.wx.db.dao.TbFaceModelDao;
import com.example.emos.wx.db.dao.TbHolidaysDao;
import com.example.emos.wx.db.dao.TbWorkdayDao;
import com.example.emos.wx.exception.EmosException;
import com.example.emos.wx.service.CheckinService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Service;

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

    @Value("${emos.face.createFaceModelUrl}")
    private String createFaceModelUrl;

    @Value("${emos.face.checkinUrl}")
    private String checkinUrl;

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
        if (type.equals("节假日")) {
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
     *  判断签到用户是否存在人脸模型
     * */
    @Override
    public void checkin(HashMap param) {
        DateTime now = DateUtil.date();
        DateTime start = DateUtil.parse(DateUtil.today() + " " + constants.attendanceStartTime);
        DateTime attend = DateUtil.parse(DateUtil.today() + " " + constants.attendanceTime);
        DateTime end = DateUtil.parse(DateUtil.today() + " " + constants.attendanceEndTime);
        int status = 1; // 1: on time, 2: late

        if (now.isBeforeOrEquals(attend) && now.isAfterOrEquals(start)) {       // TODO Pending
            status = 1;
        }
        else if (now.isAfter(attend) && now.isBefore(end)) {
            status = 2;
        }

        int userId = (Integer) param.get("userId");
        String faceModel = faceModelDao.searchFaceModel(userId);

        if (faceModel == null) {    // no faceModel from DB on cur user
            throw new EmosException("不存在人脸模型"); // TODO Pending
        }
        else {
            String path = (String) param.get("path");
            HttpRequest request = HttpUtil.createPost(checkinUrl); // send request to Python via HTTP
            request.form("photo", FileUtil.file(path), "targetModel", faceModel); // Both faceModel from client & DB send request to Python to analyze
            HttpResponse response = request.execute();

            if (response.getStatus() != 200) {
                log.error("人脸识别服务异常");
                throw new EmosException("人脸识别服务异常");
            }

            // Result of Response by Python
            String body = response.body();
            if (body.equals("无法识别出人脸") || body.equals("照片中存在多张人脸")) {
                throw new EmosException(body);
            }
            else if (body.equals("False")) {
                throw new EmosException("签到无效，非本人签到");
            }
            else if (body.equals("True")) {
                // TODO 查询疫情风险等级
                // TODO 保存签到记录
            }

        }


    }
}
