package com.sentinel.remediation.email;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class EmailAlertSender {

    private final JavaMailSender mailSender;

    @Value("${sentinel.notify.from-address}")
    private String fromAddress;

    public EmailAlertSender(JavaMailSender mailSender) {
        this.mailSender = mailSender;
    }

    public void send(List<String> recipients, String subject, String body) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(fromAddress);
        message.setTo(recipients.toArray(new String[0]));
        message.setSubject(subject);
        message.setText(body);
        mailSender.send(message);
    }
}
