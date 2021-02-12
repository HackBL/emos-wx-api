package com.example.emos.wx.controller;

import cn.hutool.core.date.DateUtil;
import cn.hutool.core.io.FileUtil;
import com.example.emos.wx.common.util.R;
import com.example.emos.wx.config.shiro.JwtUtil;
import com.example.emos.wx.controller.form.CheckinForm;
import com.example.emos.wx.exception.EmosException;
import com.example.emos.wx.service.CheckinService;
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


    @GetMapping("validCanCheckIn")
    @ApiOperation("查看用户今天是否可以签到")
    public R validCanCheckIn(@RequestHeader("token") String token) {    // Retrieve token from request header
        int userId = jwtUtil.getUserId(token);
        String result = checkinService.validCanCheckIn(userId, DateUtil.today());
        return R.ok(result);
    }


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
            param.put("path", param);
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
}
