package com.example.emos.wx.task;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Scope;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.io.Serializable;

@Component
@Scope("prototype") // 多例不会出现线程安全问题
public class EmailTask implements Serializable {
    @Autowired
    private JavaMailSender javaMailSender;

    @Value("${emos.email.system}")
    private String mailbox;

    @Async  // 实现方法异步执行
    public void sendAsync(SimpleMailMessage message) {
        message.setFrom(mailbox); // 发件人
//        message.setCc(mailbox); // 防止发送地址为垃圾邮件
        javaMailSender.send(message); // 发送邮件
    }
}
