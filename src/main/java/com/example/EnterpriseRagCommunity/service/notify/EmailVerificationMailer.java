package com.example.EnterpriseRagCommunity.service.notify;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

import org.springframework.stereotype.Service;

import com.example.EnterpriseRagCommunity.entity.access.enums.EmailVerificationPurpose;
import com.example.EnterpriseRagCommunity.service.monitor.AppSettingsService;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class EmailVerificationMailer {
    private final AppSettingsService appSettingsService;
    private final EmailSenderService emailSenderService;

    public boolean isEnabled() {
        return appSettingsService.getLongOrDefault("email_enabled", 1L) == 1L;
    }

    public void sendVerificationCode(String to, String code, EmailVerificationPurpose purpose) {
        if (!isEnabled()) throw new IllegalStateException("邮箱服务未启用");
        EmailTransportConfig transport = loadTransportConfig();
        String subject = buildSubject(purpose);
        String text = buildBody(code, purpose);
        emailSenderService.sendPlainText(transport, to, subject, text, purpose == null ? null : purpose.name());
    }

    public EmailTransportConfig loadTransportConfig() {
        String host = appSettingsService.getString("email_host").orElse("smtp.qiye.aliyun.com").trim();
        if (host.isEmpty()) throw new IllegalStateException("邮箱服务器 host 未配置");

        String encryptionRaw = appSettingsService.getString("email_encryption").orElse("SSL").trim().toUpperCase(Locale.ROOT);
        EmailEncryption enc;
        if (encryptionRaw.equals("NONE")) enc = EmailEncryption.NONE;
        else if (encryptionRaw.equals("STARTTLS")) enc = EmailEncryption.STARTTLS;
        else enc = EmailEncryption.SSL;

        int portPlain = (int) appSettingsService.getLongOrDefault("email_port_plain", 25L);
        int portEncrypted = (int) appSettingsService.getLongOrDefault("email_port_encrypted", 465L);
        int port = (enc == EmailEncryption.SSL) ? portEncrypted : portPlain;
        if (port <= 0 || port > 65535) throw new IllegalStateException("邮箱端口不合法");

        int connectTimeoutMs = (int) appSettingsService.getLongOrDefault("email_connect_timeout_ms", 10_000L);
        int timeoutMs = (int) appSettingsService.getLongOrDefault("email_timeout_ms", 10_000L);
        int writeTimeoutMs = (int) appSettingsService.getLongOrDefault("email_write_timeout_ms", 10_000L);
        boolean debug = appSettingsService.getLongOrDefault("email_debug", 0L) == 1L;

        String sslTrust = appSettingsService.getString("email_ssl_trust").orElse(null);
        if (sslTrust != null && sslTrust.isBlank()) sslTrust = null;

        return new EmailTransportConfig(
                "smtp",
                host,
                port,
                enc,
                Math.max(0, connectTimeoutMs),
                Math.max(0, timeoutMs),
                Math.max(0, writeTimeoutMs),
                debug,
                sslTrust
        );
    }

    private String buildSubject(EmailVerificationPurpose purpose) {
        String prefix = appSettingsService.getString("email_subject_prefix").orElse("").trim();
        String base = "验证码";
        if (purpose == EmailVerificationPurpose.REGISTER) base = "注册验证码";
        else if (purpose == EmailVerificationPurpose.PASSWORD_RESET) base = "重置密码验证码";
        else if (purpose == EmailVerificationPurpose.CHANGE_PASSWORD) base = "修改密码验证码";
        else if (purpose == EmailVerificationPurpose.CHANGE_EMAIL) base = "更换邮箱验证码";
        else if (purpose == EmailVerificationPurpose.CHANGE_EMAIL_OLD) base = "验证旧邮箱验证码";
        else if (purpose == EmailVerificationPurpose.TOTP_ENABLE) base = "启用二次验证验证码";
        if (prefix.isEmpty()) return base;
        return prefix + " " + base;
    }

    private String buildBody(String code, EmailVerificationPurpose purpose) {
        String now = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        return "您的验证码是：" + (code == null ? "" : code) + "\n\n用途：" + (purpose == null ? "" : purpose.name()) + "\n时间：" + now + "\n\n该验证码 10 分钟内有效，请勿泄露给他人。";
    }
}
