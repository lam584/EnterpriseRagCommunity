package com.example.EnterpriseRagCommunity.service.notify;

import com.example.EnterpriseRagCommunity.entity.access.enums.EmailVerificationPurpose;
import com.example.EnterpriseRagCommunity.service.monitor.AppSettingsService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.core.env.Environment;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class EmailVerificationMailerTest {

    @Test
    void sendVerificationCode_disabled_throws() {
        AppSettingsService appSettingsService = mock(AppSettingsService.class);
        EmailSenderService emailSenderService = mock(EmailSenderService.class);
        Environment environment = mock(Environment.class);
        when(appSettingsService.getLongOrDefault("email_enabled", 1L)).thenReturn(0L);

        EmailVerificationMailer mailer = new EmailVerificationMailer(appSettingsService, emailSenderService, environment);

        assertThrows(IllegalStateException.class, () -> mailer.sendVerificationCode("a@b.com", "123456", EmailVerificationPurpose.REGISTER));
    }

    @Test
    void sendVerificationCode_enabled_buildsTransportAndCallsSender() {
        AppSettingsService appSettingsService = mock(AppSettingsService.class);
        EmailSenderService emailSenderService = mock(EmailSenderService.class);
        Environment environment = mock(Environment.class);

        when(appSettingsService.getLongOrDefault("email_enabled", 1L)).thenReturn(1L);

        when(appSettingsService.getString("email_host")).thenReturn(Optional.of(" smtp.test "));
        when(appSettingsService.getString("email_encryption")).thenReturn(Optional.of("none"));
        when(appSettingsService.getString("email_ssl_trust")).thenReturn(Optional.of("  "));
        when(appSettingsService.getString("email_subject_prefix")).thenReturn(Optional.of("[E]"));

        when(environment.getProperty("app.mail.port.plain", "25")).thenReturn("25");
        when(environment.getProperty("app.mail.port", "465")).thenReturn("465");

        when(appSettingsService.getLongOrDefault(eq("email_port_plain"), anyLong())).thenReturn(25L);
        when(appSettingsService.getLongOrDefault(eq("email_port_encrypted"), anyLong())).thenReturn(465L);
        when(appSettingsService.getLongOrDefault(eq("email_connect_timeout_ms"), anyLong())).thenReturn(1000L);
        when(appSettingsService.getLongOrDefault(eq("email_timeout_ms"), anyLong())).thenReturn(1000L);
        when(appSettingsService.getLongOrDefault(eq("email_write_timeout_ms"), anyLong())).thenReturn(1000L);
        when(appSettingsService.getLongOrDefault(eq("email_debug"), anyLong())).thenReturn(0L);
        when(appSettingsService.getLongOrDefault(eq("email_otp_ttl_seconds"), anyLong())).thenReturn(600L);

        EmailVerificationMailer mailer = new EmailVerificationMailer(appSettingsService, emailSenderService, environment);
        mailer.sendVerificationCode("to@example.com", "778899", EmailVerificationPurpose.REGISTER);

        ArgumentCaptor<EmailTransportConfig> cfgCap = ArgumentCaptor.forClass(EmailTransportConfig.class);
        verify(emailSenderService).sendPlainText(
                cfgCap.capture(),
                eq("to@example.com"),
                any(),
                any(),
                eq(EmailVerificationPurpose.REGISTER.name())
        );

        EmailTransportConfig transport = cfgCap.getValue();
        assertNotNull(transport);
        assertEquals("smtp", transport.protocol());
        assertEquals("smtp.test", transport.host());
        assertEquals(25, transport.port());
        assertEquals(EmailEncryption.NONE, transport.encryption());
    }

    @Test
    void loadTransportConfig_invalidPort_throws() {
        AppSettingsService appSettingsService = mock(AppSettingsService.class);
        EmailSenderService emailSenderService = mock(EmailSenderService.class);
        Environment environment = mock(Environment.class);

        when(appSettingsService.getLongOrDefault("email_enabled", 1L)).thenReturn(1L);
        when(appSettingsService.getString("email_host")).thenReturn(Optional.of("smtp.test"));
        when(appSettingsService.getString("email_encryption")).thenReturn(Optional.of("SSL"));
        when(environment.getProperty("app.mail.port.plain", "25")).thenReturn("25");
        when(environment.getProperty("app.mail.port", "465")).thenReturn("465");
        when(appSettingsService.getLongOrDefault(eq("email_port_plain"), anyLong())).thenReturn(25L);
        when(appSettingsService.getLongOrDefault(eq("email_port_encrypted"), anyLong())).thenReturn(70000L);

        EmailVerificationMailer mailer = new EmailVerificationMailer(appSettingsService, emailSenderService, environment);

        assertThrows(IllegalStateException.class, mailer::loadTransportConfig);
    }
}
