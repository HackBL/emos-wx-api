package com.example.emos.wx;

import cn.hutool.core.util.StrUtil;
import com.example.emos.wx.config.SystemConstants;
import com.example.emos.wx.db.dao.SysConfigDao;
import com.example.emos.wx.db.pojo.SysConfig;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.web.servlet.ServletComponentScan;

import javax.annotation.PostConstruct;
import java.lang.reflect.Field;
import java.util.List;

@SpringBootApplication
@ServletComponentScan
@Slf4j
public class EmosWxApiApplication {
    @Autowired
    private SysConfigDao sysConfigDao;

    @Autowired
    private SystemConstants constants;

    public static void main(String[] args) {
        SpringApplication.run(EmosWxApiApplication.class, args);
    }

    @PostConstruct  // 在程序启动时，投入服务之前调用此方法，初始方法
    public void init() {
        List<SysConfig> list = sysConfigDao.selectAllParam();
        for (SysConfig one: list) {
            String key = one.getParamKey();
            // remove '_' and uppercase letter after '_'
            key = StrUtil.toCamelCase(key);
            String value = one.getParamValue();

            try {
                Field field = constants.getClass().getDeclaredField(key);
                field.set(constants, value);
            } catch (Exception e) {
                log.error("执行异常", e);
            }
        }
    }
}
