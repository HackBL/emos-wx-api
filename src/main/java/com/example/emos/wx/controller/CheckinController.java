package com.example.emos.wx.controller;

import cn.hutool.core.date.DateTime;
import cn.hutool.core.date.DateUtil;
import cn.hutool.core.io.FileUtil;
import com.example.emos.wx.common.util.R;
import com.example.emos.wx.config.SystemConstants;
import com.example.emos.wx.config.shiro.JwtUtil;
import com.example.emos.wx.controller.form.CheckinForm;
import com.example.emos.wx.controller.form.SearchMonthCheckinForm;
import com.example.emos.wx.exception.EmosException;
import com.example.emos.wx.service.CheckinService;
import com.example.emos.wx.service.UserService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import javax.validation.Valid;
import java.io.IOException;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;

@RequestMapping("/checkin")
@RestController
@Api("签到模块Web接口")
@Slf4j
public class CheckinController {
    @Autowired
    private JwtUtil jwtUtil;

    @Value("${emos.image-folder}")
    private String imageFolder;

    @Autowired
    private CheckinService checkinService;

    @Autowired
    private UserService userService;

    @Autowired
    private SystemConstants constants;


    @GetMapping("validCanCheckIn")
    @ApiOperation("查看用户今天是否可以签到")
    public R validCanCheckIn(@RequestHeader("token") String token) {    // Retrieve token from request header
        int userId = jwtUtil.getUserId(token);
        String result = checkinService.validCanCheckIn(userId, DateUtil.today());
        return R.ok(result);
    }


    /**
     *  如果加了@RequestBody注解，图片也会被传入到form类，
     *  但是在CheckinForm类中，并没有定义图片这个变量，
     *  所以会出现超出范围的异常。
     *  但是图片的变量是无法定义在CheckinForm类中的
     * */
    @PostMapping("/checkin") // 上传图片，需要Post Request
    @ApiOperation("签到")
    public R checkin(@Valid CheckinForm form, @RequestParam("photo") MultipartFile file, @RequestHeader("token") String token) {
        if (file == null) {
            return R.error("没有上传文件");
        }
        // 图片格式为.jpg
        String fileName = file.getOriginalFilename().toLowerCase();
        if (!fileName.endsWith(".jpg")) {
            return R.error("必须提交JPG格式图片");
        }

        int userId = jwtUtil.getUserId(token);
        String path = imageFolder + "/" + fileName; // 图片路径
        try {
            // 保存图片到path中
            file.transferTo(Paths.get(path));
            // 用户签到信息进行封装
            HashMap param = new HashMap();
            param.put("userId", userId);
            param.put("path", path);
            param.put("address", form.getAddress());
            param.put("country", form.getCountry());
            param.put("province", form.getProvince());
            param.put("city", form.getCity());
            param.put("district", form.getDistrict());

            checkinService.checkin(param);
            return R.ok("签到成功");
        } catch (IOException e) {
            log.error(e.getMessage(), e);
            throw new EmosException("图片保存错误");
        } finally { // 签到成功后，删除路径照片
            FileUtil.del(path);
        }
    }


    @PostMapping("/createFaceModel")
    @ApiOperation("创建人脸模型")
    public R createFaceModel(@RequestParam("photo") MultipartFile file, @RequestHeader("token") String token) {
        if (file == null) {
            return R.error("没有上传文件");
        }
        // 图片格式为.jpg
        String fileName = file.getOriginalFilename().toLowerCase();
        if (!fileName.endsWith(".jpg")) {
            return R.error("必须提交JPG格式图片");
        }
        int userId = jwtUtil.getUserId(token);
        String path = imageFolder + "/" + fileName; // 图片路径
        try {
            // 保存图片到path中
            file.transferTo(Paths.get(path));
            checkinService.createFaceModel(userId, path);
            return R.ok("人脸建模成功");
        } catch (IOException e) {
            log.error(e.getMessage(), e);
            throw new EmosException("图片保存错误");
        } finally { // 签到成功后，删除路径照片
            FileUtil.del(path);
        }
    }

    @GetMapping("/searchTodayCheckin")
    @ApiOperation("查询用户当日签到数据")
    public R searchTodayCheckin(@RequestHeader("token") String token) {
        int userId = jwtUtil.getUserId(token);
        HashMap map = checkinService.searchTodayCheckin(userId);    // 查询用户当天签到信息： name, photo, dept, addr, status, risk, checkinTime, date
        map.put("attendanceTime", constants.attendanceTime);    // 上班时间
        map.put("closingTime", constants.closingTime);          // 下班时间

        long days = checkinService.searchCheckinDays(userId);   // 累计签到
        map.put("checkinDays", days);

        // 入职日期不是一周的第一天（周一）
        DateTime hiredate = DateUtil.parse(userService.searchUserHiredate(userId));
        DateTime startDate = DateUtil.beginOfWeek(DateUtil.date());
        if (startDate.isBefore(hiredate)) {
            startDate = hiredate;
        }
        DateTime endDate = DateUtil.endOfWeek(DateUtil.date());
        //  查询用户时间范围内签到状态记录
        HashMap param = new HashMap();
        param.put("startDate", startDate.toString());
        param.put("endDate", endDate.toString());
        param.put("userId", userId);
        ArrayList<HashMap> list = checkinService.searchWeekCheckin(param);

        map.put("weekCheckin", list);
        return R.ok().put("result", map);
    }

    @PostMapping("/searchMonthCheckin")
    @ApiOperation("查询用户某月签到数据")
    public R searchMonthCheckin(@Valid @RequestBody SearchMonthCheckinForm form, @RequestHeader("token") String token) {
        int userId = jwtUtil.getUserId(token);
        // 查询入职日期
        DateTime hiredate = DateUtil.parse(userService.searchUserHiredate(userId));
        // 把月份处理成双数字
        String month = form.getMonth() < 10 ? "0" + form.getMonth() : form.getMonth().toString();
        // 某年某月的起始日期
        DateTime startDate = DateUtil.parse(form.getYear() + "-" + month + "-01");
        // 如果查询的月份早于员工入职日期的月份就抛出异常
        // 都是01号，判断年和月
        if (startDate.isBefore(DateUtil.beginOfMonth(hiredate))) {
            throw new EmosException("只能查询考勤之后月份的日期的数据");
        }
        //如果查询月份与入职月份恰好是同月，本月考勤查询开始日期设置成入职日期
        if (startDate.isBefore(hiredate)) {
            startDate = hiredate;
        }
        //某年某月的截止日期
        DateTime endDate = DateUtil.endOfMonth(startDate);

        // 根据userId，入职起始日期，当月结束日期，查询签到状态
        HashMap param = new HashMap();
        param.put("startDate", startDate);
        param.put("endDate", endDate);
        param.put("userId", userId);
        ArrayList<HashMap> list = checkinService.searchMonthCheckin(param);

        //统计月考勤数据
        int sum_1 = 0, sum_2 = 0, sum_3 = 0;
        for (HashMap<String, String> map: list) {
            String type = map.get("type");
            String status = map.get("status");

            if ("工作日".equals(type)) {
                if ("正常".equals(status)) {
                    sum_1++;
                }
                else if ("迟到".equals(status)) {
                    sum_2++;
                }
                else if ("缺勤".equals(status)) {
                    sum_3++;
                }
            }
        }

        return R.ok().put("list", list).put("sum_1", sum_1).put("sum_2", sum_2).put("sum_3", sum_3);
    }
}
