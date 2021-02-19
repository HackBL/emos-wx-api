package com.example.emos.wx.controller;

import cn.hutool.core.util.StrUtil;
import com.example.emos.wx.common.util.R;
import com.example.emos.wx.config.shiro.JwtUtil;
import com.example.emos.wx.controller.form.LoginForm;
import com.example.emos.wx.controller.form.RegisterForm;
import com.example.emos.wx.service.UserService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;
import java.util.Set;
import java.util.concurrent.TimeUnit;

@RestController
@RequestMapping("/user")
@Api("用户模块Web接口")
public class userController {
    @Autowired
    private UserService userService;

    @Autowired
    private JwtUtil jwtUtil;

    @Autowired
    private RedisTemplate redisTemplate;
    
    @Value("${emos.jwt.cache-expire}")
    private int cacheExpire;

    /**
     *  Process register request
     * */
    @PostMapping("/register")
    @ApiOperation("注册用户")
    public R register(@Valid @RequestBody RegisterForm form) {
        int userId = userService.registerUser(form.getRegisterCode(), form.getCode(), form.getNickname(), form.getPhoto());
        String token = jwtUtil.createToken(userId);
        Set<String> permsSet = userService.searchUserPermissions(userId);
        saveCacheToken(token, userId);
        
        return R.ok("用户注册成功").put("token", token).put("permission", permsSet);
    }

    /**
     *  Process login request
     * */
    @PostMapping("/login")
    @ApiOperation("登陆系统")
    public R login(@Valid @RequestBody LoginForm form, @RequestHeader("token") String token) {
        int userId;
        try {                       // token正常登陆
            jwtUtil.verifierToken(token);
            userId = jwtUtil.getUserId(token);
        } catch (Exception e) {     // 没注册登陆 & token过期登陆
            userId = userService.login(form.getCode());
            token = jwtUtil.createToken(userId);
            saveCacheToken(token, userId);
        }

        Set<String> permsSet = userService.searchUserPermissions(userId);
        return R.ok("登陆成功").put("token", token).put("permission", permsSet);
    }

    /**
     *  Save token into Redis
     * */
    private void saveCacheToken(String token, int userId) {
        redisTemplate.opsForValue().set(token, userId + "", cacheExpire, TimeUnit.DAYS);
    }
}
