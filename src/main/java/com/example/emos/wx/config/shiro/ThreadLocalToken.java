package com.example.emos.wx.config.shiro;

import org.springframework.stereotype.Component;

@Component
public class ThreadLocalToken {
    private ThreadLocal<String> local = new ThreadLocal<>();
}
