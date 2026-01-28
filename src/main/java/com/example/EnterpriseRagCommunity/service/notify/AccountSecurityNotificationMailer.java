package com.example.EnterpriseRagCommunity.service.notify;

import com.example.EnterpriseRagCommunity.service.monitor.AppSettingsService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

@Service
@RequiredArgsConstructor
public class AccountSecurityNotificationMailer {
    private final AppSettingsService appSettingsService;
    private final EmailSenderService emailSenderService;
    private final EmailVerificationMailer emailVerificationMailer;

    public void sendPasswordChanged(String email) {
        send(email, "密码已修改通知", "你的账号密码已修改成功。");
    }

    public void sendTotpEnabled(String email) {
        send(email, "TOTP 已启用通知", "你的账号已启用 TOTP。");
    }

    public void sendTotpDisabled(String email) {
        send(email, "TOTP 已关闭通知", "你的账号已关闭 TOTP。");
    }

    private void send(String email, String subjectBase, String bodyBase) {
        if (email == null || email.isBlank()) return;
        if (!emailVerificationMailer.isEnabled()) throw new IllegalStateException("邮箱服务未启用");
        EmailTransportConfig transport = emailVerificationMailer.loadTransportConfig();
        String subject = buildSubject(subjectBase);
        String now = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        String text = bodyBase + "\n\n时间：" + now + "\n\n若非本人操作，请立即修改密码并联系管理员。";
        emailSenderService.sendPlainText(transport, email.trim(), subject, text, "ACCOUNT_SECURITY");
    }

    private String buildSubject(String base) {
        String prefix = appSettingsService.getString("email_subject_prefix").orElse("").trim();
        if (prefix.isEmpty()) return base;
        return prefix + " " + base;
    }
}

