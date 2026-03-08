package com.example.EnterpriseRagCommunity.service.notify;

import com.example.EnterpriseRagCommunity.config.AppMailProperties;
import com.example.EnterpriseRagCommunity.entity.access.enums.AuditResult;
import com.example.EnterpriseRagCommunity.service.access.AuditLogWriter;
import com.example.EnterpriseRagCommunity.service.config.SystemConfigurationService;
import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedConstruction;
import org.springframework.mail.javamail.JavaMailSenderImpl;

import java.util.Map;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.withSettings;

class EmailSenderServiceTest {

    @Test
    void sendPlainText_success_writesAuditLog() {
        AppMailProperties props = new AppMailProperties();
        props.setUsername("u@example.com");
        props.setPassword("pw");
        props.setFromAddress("from@example.com");
        props.setFromName("Sender");

        SystemConfigurationService systemConfigurationService = mock(SystemConfigurationService.class);
        AuditLogWriter auditLogWriter = mock(AuditLogWriter.class);
        EmailSenderService svc = new EmailSenderService(props, systemConfigurationService, auditLogWriter);

        EmailTransportConfig cfg = new EmailTransportConfig(
                "smtp",
                "smtp.example.com",
                465,
                EmailEncryption.SSL,
                1000,
                1000,
                1000,
                false,
                null
        );

        try (MockedConstruction<JavaMailSenderImpl> mocked = org.mockito.Mockito.mockConstruction(JavaMailSenderImpl.class, (sender, ctx) -> {
            MimeMessage msg = new MimeMessage(Session.getInstance(new Properties()));
            when(sender.createMimeMessage()).thenReturn(msg);
        })) {
            svc.sendPlainText(cfg, "to@example.com", "s", "t", "PURPOSE");
        }

        ArgumentCaptor<Map<String, Object>> cap = ArgumentCaptor.forClass(Map.class);
        verify(auditLogWriter).writeSystem(
                eq("EMAIL_SEND"),
                eq("EMAIL"),
                eq(null),
                eq(AuditResult.SUCCESS),
                eq("邮件发送成功"),
                eq(null),
                cap.capture()
        );
        Map<String, Object> details = cap.getValue();
        assertEquals("to@example.com", details.get("to"));
        assertEquals("smtp.example.com", details.get("host"));
        assertEquals(465, details.get("port"));
    }

    @Test
    void sendPlainText_senderThrows_writesFailAudit_andRethrows() {
        AppMailProperties props = new AppMailProperties();
        props.setUsername("u@example.com");
        props.setPassword("pw");
        props.setFromAddress("from@example.com");
        props.setFromName("");

        SystemConfigurationService systemConfigurationService = mock(SystemConfigurationService.class);
        AuditLogWriter auditLogWriter = mock(AuditLogWriter.class);
        EmailSenderService svc = new EmailSenderService(props, systemConfigurationService, auditLogWriter);

        EmailTransportConfig cfg = new EmailTransportConfig(
                "smtp",
                "smtp.example.com",
                465,
                EmailEncryption.SSL,
                0,
                0,
                0,
                false,
                null
        );

        try (MockedConstruction<JavaMailSenderImpl> mocked = org.mockito.Mockito.mockConstruction(JavaMailSenderImpl.class, (sender, ctx) -> {
            MimeMessage msg = new MimeMessage(Session.getInstance(new Properties()));
            when(sender.createMimeMessage()).thenReturn(msg);
            doThrow(new RuntimeException("boom")).when(sender).send(any(MimeMessage.class));
        })) {
            assertThrows(IllegalStateException.class, () -> svc.sendPlainText(cfg, "to@example.com", "s", "t", "PURPOSE"));
        }

        verify(auditLogWriter).writeSystem(
                eq("EMAIL_SEND"),
                eq("EMAIL"),
                eq(null),
                eq(AuditResult.FAIL),
                eq("邮件发送失败"),
                eq(null),
                any()
        );
    }

    @Test
    void sendPlainText_missingCredentials_throws() {
        AppMailProperties props = new AppMailProperties();
        props.setUsername(" ");
        props.setPassword("");
        props.setFromAddress("from@example.com");

        SystemConfigurationService systemConfigurationService = mock(SystemConfigurationService.class, withSettings().lenient());
        AuditLogWriter auditLogWriter = mock(AuditLogWriter.class, withSettings().lenient());
        EmailSenderService svc = new EmailSenderService(props, systemConfigurationService, auditLogWriter);

        EmailTransportConfig cfg = new EmailTransportConfig(
                "smtp",
                "smtp.example.com",
                465,
                EmailEncryption.SSL,
                0,
                0,
                0,
                false,
                null
        );

        assertThrows(IllegalStateException.class, () -> svc.sendPlainText(cfg, "to@example.com", "s", "t", "PURPOSE"));
    }
}

