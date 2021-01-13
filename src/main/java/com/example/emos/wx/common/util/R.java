package com.example.emos.wx.common.util;

import org.apache.http.HttpStatus;

import java.util.HashMap;

public class R extends HashMap<String, Object> {
    public R() {
        put("code", HttpStatus.SC_OK);  // 200
        put("msg", "success");
    }
}
