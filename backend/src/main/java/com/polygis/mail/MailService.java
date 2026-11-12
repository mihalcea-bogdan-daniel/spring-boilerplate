package com.polygis.mail;

import java.util.Locale;
import java.util.Map;

import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

import com.polygis.auth.OtpPurpose;
import com.polygis.config.AppProperties;

import jakarta.mail.MessagingException;
import lombok.extern.slf4j.Slf4j;

/** Renders Thymeleaf e-mail templates (templates/email/*) and sends them asynchronously. */
@Slf4j
@Service
public class MailService {

    private final JavaMailSender sender;
    private final TemplateEngine templates;
    private final AppProperties props;

    public MailService(JavaMailSender sender, TemplateEngine templates, AppProperties props) {
        this.sender = sender;
        this.templates = templates;
        this.props = props;
    }

    @Async
    public void sendOtp(String to, String name, String code, OtpPurpose purpose, String tenantHost) {
        String subject = switch (purpose) {
            case EMAIL_VERIFICATION -> "Verify your e-mail address";
            case LOGIN -> "Your sign-in code";
            case PASSWORD_RESET -> "Reset your password";
            default -> "Your code";
        };
        send(to, subject, "email/otp", Map.of(
                "name", name, "code", code, "purpose", purpose.name(),
                "ttlMinutes", props.security().otp().ttl().toMinutes(),
                "tenantHost", tenantHost));
    }

    @Async
    public void sendWelcome(String to, String name, String tenantHost) {
        send(to, "Welcome to " + props.mail().productName(), "email/welcome",
                Map.of("name", name, "tenantHost", tenantHost));
    }

    @Async
    public void sendTenantInvite(String to, String tenantName, String tenantHost) {
        send(to, "Your workspace " + tenantName + " is ready", "email/tenant-invite",
                Map.of("tenantName", tenantName, "tenantHost", tenantHost));
    }

    private void send(String to, String subject, String template, Map<String, Object> model) {
        try {
            Context ctx = new Context(Locale.ENGLISH);
            ctx.setVariables(model);
            ctx.setVariable("productName", props.mail().productName());
            String html = templates.process(template, ctx);

            var message = sender.createMimeMessage();
            var helper = new MimeMessageHelper(message, "UTF-8");
            helper.setFrom(props.mail().from());
            helper.setTo(to);
            helper.setSubject(subject);
            helper.setText(html, true);
            sender.send(message);
        } catch (MessagingException | RuntimeException e) {
            log.error("Failed to send '{}' mail to {}", template, to, e);
        }
    }
}
