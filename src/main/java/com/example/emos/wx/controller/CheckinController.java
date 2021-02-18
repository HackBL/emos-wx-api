package com.example.emos.wx.controller;

import cn.hutool.core.date.DateTime;
import cn.hutool.core.date.DateUtil;
import cn.hutool.core.io.FileUtil;
import com.example.emos.wx.common.util.R;
import com.example.emos.wx.config.SystemConstants;
import com.example.emos.wx.config.shiro.JwtUtil;
import com.example.emos.wx.controller.form.CheckinForm;
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
}
