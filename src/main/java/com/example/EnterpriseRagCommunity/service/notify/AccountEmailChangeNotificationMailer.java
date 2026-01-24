package com.example.EnterpriseRagCommunity.service.notify;

import com.example.EnterpriseRagCommunity.service.monitor.AppSettingsService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

@Service
@RequiredArgsConstructor
public class AccountEmailChangeNotificationMailer {
    private final AppSettingsService appSettingsService;
    private final EmailSenderService emailSenderService;
    private final EmailVerificationMailer emailVerificationMailer;

    public void sendChangeEmailSuccessNotifications(String oldEmail, String newEmail) {
        if (!emailVerificationMailer.isEnabled()) throw new IllegalStateException("邮箱服务未启用");
        EmailTransportConfig transport = emailVerificationMailer.loadTransportConfig();

        String subjectOld = buildSubject("邮箱变更通知");
        String subjectNew = buildSubject("邮箱绑定通知");

        String now = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));

        if (oldEmail != null && !oldEmail.isBlank()) {
            String textOld = "您的账号邮箱已更换。\n\n旧邮箱：" + oldEmail.trim() + "\n新邮箱：" + (newEmail == null ? "" : newEmail.trim()) + "\n时间：" + now + "\n\n若非本人操作，请立即修改密码并联系管理员。";
            emailSenderService.sendPlainText(transport, oldEmail.trim(), subjectOld, textOld, "CHANGE_EMAIL_NOTIFY_OLD");
        }

        if (newEmail != null && !newEmail.isBlank()) {
            String textNew = "您的账号已绑定此邮箱。\n\n邮箱：" + newEmail.trim() + "\n时间：" + now + "\n\n若非本人操作，请立即修改密码并联系管理员。";
            emailSenderService.sendPlainText(transport, newEmail.trim(), subjectNew, textNew, "CHANGE_EMAIL_NOTIFY_NEW");
        }
    }

    private String buildSubject(String base) {
        String prefix = appSettingsService.getString("email_subject_prefix").orElse("").trim();
        if (prefix.isEmpty()) return base;
        return prefix + " " + base;
    }
}

