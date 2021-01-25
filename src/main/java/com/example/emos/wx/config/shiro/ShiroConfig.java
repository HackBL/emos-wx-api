package com.example.emos.wx.config.shiro;

import org.apache.shiro.mgt.SecurityManager;
import org.apache.shiro.web.mgt.DefaultWebSecurityManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ShiroConfig {
    /**
     *  用于封装Realm类对象
     * */
    @Bean("securityManager")
    public SecurityManager securityManager(OAuth2Realm realm) {
        DefaultWebSecurityManager securityManager = new DefaultWebSecurityManager();
        securityManager.setRealm(realm);
        // Token stores in Client but not in Server
        securityManager.setRememberMeManager(null);
        return securityManager;
    }
}
