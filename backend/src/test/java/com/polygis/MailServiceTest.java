package com.polygis;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.thymeleaf.TemplateEngine;

import com.polygis.auth.OtpPurpose;
import com.polygis.config.AppProperties;
import com.polygis.mail.MailService;

import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;

class MailServiceTest {

    JavaMailSender sender = mock(JavaMailSender.class);
    MailService mail;

    @BeforeEach
    void setUp() {
        TemplateEngine templates = mock(TemplateEngine.class);
        when(templates.process(any(String.class), any())).thenReturn("<p>hi</p>");
        when(sender.createMimeMessage()).thenAnswer(i -> new MimeMessage((Session) null));
        var security = new AppProperties.Security(null, null, null,
                new AppProperties.Security.Otp(6, Duration.ofMinutes(10), 5, Duration.ZERO), null);
        var props = new AppProperties(null, security, null, new AppProperties.Mail("no-reply@test", "Test"),
                new AppProperties.Cors(List.of()));
        mail = new MailService(sender, templates, props, Runnable::run);
    }

    @AfterEach
    void tearDown() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
        TransactionSynchronizationManager.setActualTransactionActive(false);
    }

    @Test
    void sendsImmediatelyOutsideATransaction() {
        mail.sendOtp("a@b.c", "A", "123456", OtpPurpose.LOGIN, "acme.example.com");
        verify(sender, times(1)).send(any(MimeMessage.class));
    }

    @Test
    void insideATransactionTheMailWaitsForCommit() {
        TransactionSynchronizationManager.initSynchronization();
        TransactionSynchronizationManager.setActualTransactionActive(true);

        mail.sendOtp("a@b.c", "A", "123456", OtpPurpose.LOGIN, "acme.example.com");
        verify(sender, never()).send(any(MimeMessage.class)); // a rollback here would drop the mail

        TransactionSynchronizationManager.getSynchronizations().forEach(TransactionSynchronization::afterCommit);
        verify(sender, times(1)).send(any(MimeMessage.class));
    }
}
