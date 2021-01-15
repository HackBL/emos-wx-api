package com.example.emos.wx.config.xss;

import cn.hutool.core.util.StrUtil;
import cn.hutool.http.HtmlUtil;
import cn.hutool.json.JSONUtil;
import springfox.documentation.spring.web.json.Json;

import javax.servlet.ServletInputStream;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletRequestWrapper;
import java.io.*;
import java.util.LinkedHashMap;
import java.util.Map;

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
                if (!StrUtil.hasEmpty(value)) {
                    value = HtmlUtil.filter(value);
                }
                values[i] = value;
            }
        }

        return values;
    }

    @Override
    public Map<String, String[]> getParameterMap() {
        Map<String, String[]> parameters = super.getParameterMap();
        // Use for store filtered values
        Map<String, String[]> map = new LinkedHashMap<>();
        if (parameters != null) {
            for (String key: parameters.keySet()) {
                String[] values = parameters.get(key);

                // Iterate arr and filter each value to prevent XSS Attack
                for (int i = 0; i < values.length; i++) {
                    String value = values[i];
                    if (!StrUtil.hasEmpty(value)) {
                        value = HtmlUtil.filter(value);
                    }
                    values[i] = value;
                }
                map.put(key, values);
            }
        }

        return map;
    }

    @Override
    public String getHeader(String name) {
        String value = super.getHeader(name);
        if (!StrUtil.hasEmpty(value)) {
            value = HtmlUtil.filter(value);
        }
        
        return value;
    }

    @Override
    public ServletInputStream getInputStream() throws IOException {
        InputStream in = super.getInputStream();
        InputStreamReader reader = new InputStreamReader(in);
        BufferedReader buffer = new BufferedReader(reader);

        // use for store data from buffer
        StringBuffer sb = new StringBuffer();

        String line = buffer.readLine();
        while (line != null) {
            sb.append(line);
            line = buffer.readLine();
        }

        // close all streams
        buffer.close();
        reader.close();
        in.close();

        // Convert JSON to Map
        Map<String, Object> map = JSONUtil.parseObj(sb.toString());
        // Use for store filtered values
        Map<String, Object> result = new LinkedHashMap<>();

        for (String key: map.keySet()) {
            Object val = map.get(key);
            // put object into new map only if object is String type
            if (val instanceof String) {
                if (!StrUtil.hasEmpty(val.toString())) {
                    // store filtered values into new map
                    result.put(key, HtmlUtil.filter(val.toString()));
                }
            }
             else {
                result.put(key, val);
            }
        }

        // convert Map to (String)Json
        String json = JSONUtil.toJsonStr(result);
        // Create I/O Stream to read from Json
        ByteArrayInputStream byteArrayInputStream = new ByteArrayInputStream(json.getBytes());

        return null;
    }
}
