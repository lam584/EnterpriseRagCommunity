package com.example.EnterpriseRagCommunity.service.notify;

import com.example.EnterpriseRagCommunity.config.AppMailProperties;
import com.example.EnterpriseRagCommunity.entity.access.enums.AuditResult;
import com.example.EnterpriseRagCommunity.service.access.AuditLogWriter;
import com.example.EnterpriseRagCommunity.service.config.SystemConfigurationService;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Properties;

@Service
@RequiredArgsConstructor
public class EmailSenderService {
    private static final Logger log = LoggerFactory.getLogger(EmailSenderService.class);

    private final AppMailProperties appMailProperties;
    private final SystemConfigurationService systemConfigurationService;
    private final AuditLogWriter auditLogWriter;

    private static String enumName(Enum<?> value) {
        return value == null ? null : value.name();
    }

    private JavaMailSenderImpl buildSender(EmailTransportConfig cfg, String username, String password, String fromAddress) {
        if (cfg.host() == null || cfg.host().isBlank()) throw new IllegalArgumentException("host is required");
        if (cfg.port() <= 0) throw new IllegalArgumentException("port is invalid");

        JavaMailSenderImpl sender = new JavaMailSenderImpl();
        sender.setHost(cfg.host().trim());
        sender.setPort(cfg.port());

        if (username != null && !username.isBlank()) {
            sender.setUsername(username.trim());
        }
        if (password != null && !password.isBlank()) {
            sender.setPassword(password);
        }

        Properties props = new Properties();
        String protocol = (cfg.protocol() == null || cfg.protocol().isBlank()) ? "smtp" : cfg.protocol().trim();
        props.put("mail.transport.protocol", protocol);
        props.put("mail.smtp.localhost", resolveSmtpLocalhost(fromAddress));

        EmailEncryption enc = cfg.encryption() == null ? EmailEncryption.NONE : cfg.encryption();
        if (enc == EmailEncryption.SSL) {
            props.put("mail.smtp.ssl.enable", "true");
        } else if (enc == EmailEncryption.STARTTLS) {
            props.put("mail.smtp.starttls.enable", "true");
            props.put("mail.smtp.starttls.required", "true");
        }

        boolean auth = username != null && !username.isBlank();
        props.put("mail.smtp.auth", Boolean.toString(auth));

        if (cfg.connectTimeoutMs() > 0) props.put("mail.smtp.connectiontimeout", String.valueOf(cfg.connectTimeoutMs()));
        if (cfg.timeoutMs() > 0) props.put("mail.smtp.timeout", String.valueOf(cfg.timeoutMs()));
        if (cfg.writeTimeoutMs() > 0) props.put("mail.smtp.writetimeout", String.valueOf(cfg.writeTimeoutMs()));

        if (cfg.sslTrust() != null && !cfg.sslTrust().isBlank()) {
            props.put("mail.smtp.ssl.trust", cfg.sslTrust().trim());
        }
        props.put("mail.debug", Boolean.toString(cfg.debug()));

        sender.setJavaMailProperties(props);
        return sender;
    }

    private String resolveSmtpLocalhost(String fromAddress) {
        if (fromAddress == null) return "localhost";
        String t = fromAddress.trim();
        int at = t.lastIndexOf('@');
        if (at <= 0 || at >= t.length() - 1) return "localhost";
        String domain = t.substring(at + 1).trim();
        if (domain.isEmpty()) return "localhost";
        if (!domain.matches("[A-Za-z0-9.-]+")) return "localhost";
        return domain;
    }

    private String getConfig(String key, String defaultValue) {
        String val = systemConfigurationService.getConfig(key);
        if (val != null && !val.isBlank()) return val;
        return defaultValue;
    }

    private static String safe(String v) {
        if (v == null) return null;
        String t = v.trim();
        return t.isEmpty() ? null : t;
    }

    private static String mask(String v) {
        String t = safe(v);
        if (t == null) return null;
        if (t.length() <= 2) return "**";
        int keepStart = 2;
        int keepEnd = Math.min(2, t.length() - keepStart);
        return t.substring(0, keepStart) + "****" + t.substring(t.length() - keepEnd);
    }

    public void sendPlainText(EmailTransportConfig cfg, String to, String subject, String text, String purpose) {
        if (cfg == null) throw new IllegalArgumentException("mail transport config is required");
        if (to == null || to.isBlank()) throw new IllegalArgumentException("to is required");
        if (subject == null) subject = "";
        if (text == null) text = "";

        String username = getConfig("APP_MAIL_USERNAME", appMailProperties.getUsername());
        String password = getConfig("APP_MAIL_PASSWORD", appMailProperties.getPassword());
        String fromAddress = getConfig("APP_MAIL_FROM_ADDRESS", appMailProperties.getFromAddress());
        String fromName = getConfig("APP_MAIL_FROM_NAME", appMailProperties.getFromName());

        if (username == null || username.isBlank() || password == null || password.isBlank()) {
            throw new IllegalStateException("app.mail.username 和 app.mail.password 必须配置");
        }
        if (fromAddress == null || fromAddress.isBlank()) {
            throw new IllegalStateException("app.mail.from-address is required");
        }

        JavaMailSenderImpl sender = buildSender(cfg, username, password, fromAddress);

        String safeUser = mask(username);
        long startNs = System.nanoTime();
        log.info("Email send start purpose={} to={} via {}:{} enc={} user={}",
                safe(purpose), to.trim(), safe(cfg.host()), cfg.port(), cfg.encryption(), safeUser);

        Map<String, Object> details = new LinkedHashMap<>();
        details.put("purpose", safe(purpose));
        details.put("to", to.trim());
        details.put("host", safe(cfg.host()));
        details.put("port", cfg.port());
        details.put("encryption", enumName(cfg.encryption()));

        try {
            var msg = sender.createMimeMessage();
            var helper = new MimeMessageHelper(msg, false, StandardCharsets.UTF_8.name());

            if (fromName != null && !fromName.isBlank()) {
                helper.setFrom(fromAddress.trim(), fromName.trim());
            } else {
                helper.setFrom(fromAddress.trim());
            }

            helper.setTo(to.trim());
            helper.setSubject(subject);
            helper.setText(text, false);

            sender.send(msg);

            long costMs = (System.nanoTime() - startNs) / 1_000_000;
            details.put("costMs", costMs);
            auditLogWriter.writeSystem("EMAIL_SEND", "EMAIL", null, AuditResult.SUCCESS, "邮件发送成功", null, details);
            log.info("Email send success purpose={} to={} costMs={}", safe(purpose), to.trim(), costMs);
        } catch (Exception e) {
            long costMs = (System.nanoTime() - startNs) / 1_000_000;
            details.put("costMs", costMs);
            details.put("error", safe(e.getMessage()));
            auditLogWriter.writeSystem("EMAIL_SEND", "EMAIL", null, AuditResult.FAIL, "邮件发送失败", null, details);
            log.warn("Email send fail purpose={} to={} costMs={} err={}", safe(purpose), to.trim(), costMs, safe(e.getMessage()));
            throw new IllegalStateException("邮件发送失败: " + e.getMessage(), e);
        }
    }
}
