package com.warehouse.service;

import freemarker.template.Configuration;
import freemarker.template.Template;
import freemarker.template.TemplateException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import org.springframework.ui.freemarker.FreeMarkerTemplateUtils;

import jakarta.mail.internet.MimeMessage;
import jakarta.mail.MessagingException;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

@Service
public class EmailService {

    private final JavaMailSender mailSender;
    private final Configuration freemarkerConfig;

    @Autowired
    public EmailService(JavaMailSender mailSender, Configuration freemarkerConfig) {
        this.mailSender = mailSender;
        this.freemarkerConfig = freemarkerConfig;
    }

    public void sendConfirmationEmail(String email, String name, String confirmationLink) {
        try {
            Template template = freemarkerConfig.getTemplate("confirm_reg_mail.ftl");
            Map<String, Object> model = new HashMap<>();
            model.put("name", name);
            model.put("confirmationLink", confirmationLink);

            String htmlContent = FreeMarkerTemplateUtils.processTemplateIntoString(template, model);

            MimeMessage mimeMessage = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(mimeMessage, true, "UTF-8");
            String fromAddress = System.getenv("MAIL_USERNAME");
            if (fromAddress == null) {
                throw new IllegalStateException("Переменная окружения MAIL_USERNAME не установлена");
            }

            helper.setFrom(fromAddress);
            helper.setTo(email);
            helper.setSubject("Подтверждение регистрации");
            helper.setText(htmlContent, true);

            mailSender.send(mimeMessage);
        } catch (IOException | TemplateException | MessagingException e) {
            throw new RuntimeException(e);
        }
    }

    public void sendGenericEmail(String email, String subject, String message) {
        try {
            MimeMessage mimeMessage = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(mimeMessage, true, "UTF-8");

            String fromAddress = System.getenv("MAIL_USERNAME"); // Отправить от имени MAIL_USERNAME
            if (fromAddress == null) {
                throw new IllegalStateException("Переменная окружения MAIL_USERNAME не установлена");
            }

            helper.setFrom(fromAddress);
            helper.setTo(email);
            helper.setSubject(subject);
            helper.setText(message, true);

            mailSender.send(mimeMessage); // Отправка сообщения через JavaMailSender
        } catch (MessagingException e) {
            throw new RuntimeException("Ошибка при отправке email", e);
        }
    }

}