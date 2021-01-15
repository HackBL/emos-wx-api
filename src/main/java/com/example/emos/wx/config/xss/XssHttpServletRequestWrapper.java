package com.example.emos.wx.config.xss;

import cn.hutool.core.util.StrUtil;
import cn.hutool.http.HtmlUtil;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletRequestWrapper;

public class XssHttpServletRequestWrapper extends HttpServletRequestWrapper {
    public XssHttpServletRequestWrapper(HttpServletRequest request) {
        super(request);
    }

    @Override
    public String getParameter(String name) {
        String value = super.getParameter(name);
        // filter value to prevent XSS Attack
        if (!StrUtil.hasEmpty(value)) {
            value = HtmlUtil.filter(value);
        }

        return value;
    }

    @Override
    public String[] getParameterValues(String name) {
        String[] values = super.getParameterValues(name);

        if(values != null) {
            // Iterate arr and filter each value to prevent XSS Attack
            for (int i = 0; i < values.length; i++) {
                String value = values[i];
                if (!StrUtil.hasEmpty(values)) {
                    value = HtmlUtil.filter(value);
                }
                values[i] = value;
            }
        }

        return values;
    }
}
