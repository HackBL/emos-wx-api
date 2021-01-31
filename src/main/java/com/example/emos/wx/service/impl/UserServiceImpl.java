package com.example.emos.wx.service.impl;

import cn.hutool.http.HttpUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.example.emos.wx.db.dao.TbUserDao;
import com.example.emos.wx.exception.EmosException;
import com.example.emos.wx.service.UserService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Service;

import java.util.Date;
import java.util.HashMap;



@Service
@Slf4j
@Scope("prototype")
public class UserServiceImpl implements UserService {

    @Value("${wx.app-id}")
    private String appId;

    @Value("${wx.app-secret}")
    private String appSecret;

    @Value("${wx.super-admin}")
    private String superAdmin;

    @Autowired
    private TbUserDao userDao;

    /**
     *  通过临时授权字符串，得到openId
     * */
    private String getOpenId(String code) {
        String url = "https://api.weixin.qq.com/sns/jscode2session";
        HashMap map = new HashMap();
        map.put("appid", appId);
        map.put("secret", appSecret);
        map.put("js_code", code);
        map.put("grant_type", "authorization_code");

        String response = HttpUtil.post(url, map);
        JSONObject json = JSONUtil.parseObj(response);
        String openId = json.getStr("openid");

        if (openId == null || openId.length() == 0) {
            throw new RuntimeException("临时登陆凭证错误");
        }
        return openId;
    }

    @Override
    public int registerUser(String registerCode, String code, String nickname, String photo) {
        //如果激活码是000000，代表是超级管理员
        if (registerCode.equals(superAdmin)) {
            boolean existAdmin = userDao.haveRootUser();

            //查询超级管理员帐户是否已经绑定
            if (!existAdmin) {
                //把当前用户绑定到ROOT帐户
                String openId = getOpenId(code);
                HashMap map = new HashMap();
                map.put("openId", openId);
                map.put("nickname", nickname);
                map.put("photo", photo);
                map.put("role", "[0]");     // [0] 代表超级管理员角色
                map.put("status", 1);       // 1 代表在职有效状态
                map.put("createTime", new Date());
                map.put("root", true);

                // 传入到MySQL中
                userDao.insert(map);
                int id = userDao.searchIdByOpenId(openId);
                return id;
            }
            else {
                //如果root已经绑定了，就抛出异常
                throw new EmosException("无法绑定超级管理员账号");
            }
        }
        //TODO 此处还有其他判断内容
        else {

        }
        return 0;
    }
}
