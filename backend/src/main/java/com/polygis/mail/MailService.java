package com.polygis.mail;

import java.util.Locale;
import java.util.Map;
import java.util.concurrent.Executor;

import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.stereotype.Service;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

import com.polygis.auth.OtpPurpose;
import com.polygis.config.AppProperties;

import jakarta.mail.MessagingException;
import lombok.extern.slf4j.Slf4j;

/**
 * Renders Thymeleaf e-mail templates (templates/email/*) and sends them asynchronously.
 * <p>When called inside a transaction the mail is only handed to the executor <b>after commit</b>, so a
 * rolled-back registration never sends a code. Transient SMTP failures are retried with backoff.
 */
@Slf4j
@Service
public class MailService {

    private final JavaMailSender sender;
    private final TemplateEngine templates;
    private final AppProperties props;
    private final Executor executor;

    private static final int MAX_ATTEMPTS = 3;

    public MailService(JavaMailSender sender, TemplateEngine templates, AppProperties props,
                       @Qualifier("taskExecutor") Executor executor) {
        this.sender = sender;
        this.templates = templates;
        this.props = props;
        this.executor = executor;
    }

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

    public void sendWelcome(String to, String name, String tenantHost) {
        send(to, "Welcome to " + props.mail().productName(), "email/welcome",
                Map.of("name", name, "tenantHost", tenantHost));
    }

    public void sendUserInvite(String to, String name, String tenantHost) {
        send(to, "You have been invited to " + props.mail().productName(), "email/user-invite",
                Map.of("name", name, "email", to, "tenantHost", tenantHost));
    }

    public void sendTenantInvite(String to, String tenantName, String tenantHost) {
        send(to, "Your workspace " + tenantName + " is ready", "email/tenant-invite",
                Map.of("tenantName", tenantName, "tenantHost", tenantHost));
    }

    private void send(String to, String subject, String template, Map<String, Object> model) {
        Runnable task = () -> executor.execute(() -> sendWithRetry(to, subject, template, model));
        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    task.run();
                }
            });
        } else {
            task.run();
        }
    }

    private void sendWithRetry(String to, String subject, String template, Map<String, Object> model) {
        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            try {
                doSend(to, subject, template, model);
                return;
            } catch (MessagingException | RuntimeException e) {
                if (attempt == MAX_ATTEMPTS) {
                    log.error("Giving up on '{}' mail to {} after {} attempts", template, to, attempt, e);
                    return;
                }
                log.warn("Sending '{}' mail to {} failed (attempt {}): {}", template, to, attempt, e.getMessage());
                try {
                    Thread.sleep(1000L * attempt * attempt);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    return;
                }
            }
        }
    }

    private void doSend(String to, String subject, String template, Map<String, Object> model)
            throws MessagingException {
        {
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
        }
    }
}
