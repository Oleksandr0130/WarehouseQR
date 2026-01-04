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

    /**
     * 🔹 СТАРЫЙ МЕТОД — БЕЗ ИЗМЕНЕНИЙ
     * Используется там, где язык не передаётся.
     * Поведение остаётся как раньше (RU).
     */
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

    /**
     * 🔹 НОВЫЙ МЕТОД — С ЯЗЫКОМ
     * НИЧЕГО не ломает, используется ТОЛЬКО если его вызвать явно
     */
    public void sendConfirmationEmail(
            String email,
            String name,
            String confirmationLink,
            String lang
    ) {
        try {
            String safeLang = normalizeLang(lang);

            Template template;
            try {
                template = freemarkerConfig.getTemplate(
                        "confirm_reg_mail_" + safeLang + ".ftl"
                );
            } catch (IOException e) {
                // fallback — старый шаблон
                template = freemarkerConfig.getTemplate("confirm_reg_mail.ftl");
            }

            Map<String, Object> model = new HashMap<>();
            model.put("name", name);
            model.put("confirmationLink", confirmationLink);

            String htmlContent =
                    FreeMarkerTemplateUtils.processTemplateIntoString(template, model);

            MimeMessage mimeMessage = mailSender.createMimeMessage();
            MimeMessageHelper helper =
                    new MimeMessageHelper(mimeMessage, true, "UTF-8");

            String fromAddress = System.getenv("MAIL_USERNAME");
            if (fromAddress == null) {
                throw new IllegalStateException("Переменная окружения MAIL_USERNAME не установлена");
            }

            helper.setFrom(fromAddress);
            helper.setTo(email);
            helper.setSubject(subjectByLang(safeLang));
            helper.setText(htmlContent, true);

            mailSender.send(mimeMessage);

        } catch (IOException | TemplateException | MessagingException e) {
            throw new RuntimeException(e);
        }
    }

    /* ===================== helpers ===================== */

    private String normalizeLang(String lang) {
        if (lang == null || lang.isBlank()) {
            return "ru"; // поведение как раньше
        }

        String l = lang.toLowerCase();
        if (l.startsWith("de")) return "de";
        if (l.startsWith("pl")) return "pl";
        if (l.startsWith("en")) return "en";
        if (l.startsWith("ru")) return "ru";

        return "en";
    }

    private String subjectByLang(String lang) {
        return switch (lang) {
            case "de" -> "Registrierung bestätigen";
            case "pl" -> "Potwierdzenie rejestracji";
            case "ru" -> "Подтверждение регистрации";
            default -> "Confirm registration";
        };
    }
}
