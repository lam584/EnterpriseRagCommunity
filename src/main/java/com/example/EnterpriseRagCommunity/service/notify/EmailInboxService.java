package com.example.EnterpriseRagCommunity.service.notify;

import com.example.EnterpriseRagCommunity.config.AppMailProperties;
import com.example.EnterpriseRagCommunity.dto.access.EmailInboxMessageDTO;
import com.example.EnterpriseRagCommunity.dto.access.EmailInboxSettingsDTO;
import com.example.EnterpriseRagCommunity.service.config.SystemConfigurationService;
import jakarta.mail.Address;
import jakarta.mail.BodyPart;
import jakarta.mail.Folder;
import jakarta.mail.Message;
import jakarta.mail.Multipart;
import jakarta.mail.Part;
import jakarta.mail.Session;
import jakarta.mail.Store;
import jakarta.mail.UIDFolder;
import jakarta.mail.internet.InternetAddress;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Properties;

@Service
@RequiredArgsConstructor
@ConditionalOnClass(name = "jakarta.mail.Part")
public class EmailInboxService {
    private final AppMailProperties appMailProperties;
    private final SystemConfigurationService systemConfigurationService;

    public List<EmailInboxMessageDTO> listInbox(EmailInboxSettingsDTO cfg, int limit) {
        if (cfg == null) throw new IllegalArgumentException("inbox config is required");
        if (limit <= 0) limit = 20;
        if (limit > 50) limit = 50;

        String username = getConfig("APP_MAIL_USERNAME", appMailProperties.getUsername());
        String password = getConfig("APP_MAIL_PASSWORD", appMailProperties.getPassword());
        
        if (username == null || username.isBlank()) throw new IllegalStateException("app.mail.username is required");
        if (password == null || password.isBlank()) throw new IllegalStateException("app.mail.password is required");

        String host = cfg.getHost() == null ? "" : cfg.getHost().trim();
        if (host.isEmpty()) throw new IllegalArgumentException("host 不能为空");

        String folderName = cfg.getFolder() == null || cfg.getFolder().isBlank() ? "INBOX" : cfg.getFolder().trim();
        String encryption = cfg.getEncryption() == null ? "SSL" : cfg.getEncryption().trim().toUpperCase();
        boolean debug = cfg.getDebug() != null && cfg.getDebug();

        int connectTimeoutMs = cfg.getConnectTimeoutMs() == null ? 10_000 : Math.max(0, cfg.getConnectTimeoutMs());
        int timeoutMs = cfg.getTimeoutMs() == null ? 10_000 : Math.max(0, cfg.getTimeoutMs());
        int writeTimeoutMs = cfg.getWriteTimeoutMs() == null ? 10_000 : Math.max(0, cfg.getWriteTimeoutMs());

        EmailEncryption enc;
        if (encryption.equals("STARTTLS")) enc = EmailEncryption.STARTTLS;
        else if (encryption.equals("NONE")) enc = EmailEncryption.NONE;
        else enc = EmailEncryption.SSL;

        int port = (enc == EmailEncryption.SSL)
                ? (cfg.getPortEncrypted() == null ? 993 : cfg.getPortEncrypted())
                : (cfg.getPortPlain() == null ? 143 : cfg.getPortPlain());
        if (port <= 0 || port > 65535) throw new IllegalArgumentException("port 不合法");

        String protocol = enc == EmailEncryption.SSL ? "imaps" : "imap";

        Properties props = new Properties();
        props.put("mail.store.protocol", protocol);
        props.put("mail.debug", debug ? "true" : "false");
        if (connectTimeoutMs > 0) props.put("mail." + protocol + ".connectiontimeout", String.valueOf(connectTimeoutMs));
        if (timeoutMs > 0) props.put("mail." + protocol + ".timeout", String.valueOf(timeoutMs));
        if (writeTimeoutMs > 0) props.put("mail." + protocol + ".writetimeout", String.valueOf(writeTimeoutMs));

        if (enc == EmailEncryption.STARTTLS) {
            props.put("mail.imap.starttls.enable", "true");
            props.put("mail.imap.starttls.required", "true");
        }

        String sslTrust = cfg.getSslTrust();
        if (sslTrust != null && !sslTrust.isBlank()) {
            props.put("mail." + protocol + ".ssl.trust", sslTrust.trim());
        }

        Session session = Session.getInstance(props);
        session.setDebug(debug);

        Store store = null;
        Folder folder = null;
        try {
            store = session.getStore(protocol);
            store.connect(host, port, username.trim(), password);

            folder = store.getFolder(folderName);
            folder.open(Folder.READ_ONLY);

            int count = folder.getMessageCount();
            if (count <= 0) return List.of();

            int start = Math.max(1, count - limit + 1);
            Message[] msgs = folder.getMessages(start, count);

            if (folder instanceof UIDFolder uidFolder) {
                var fp = new jakarta.mail.FetchProfile();
                fp.add(jakarta.mail.FetchProfile.Item.ENVELOPE);
                fp.add(UIDFolder.FetchProfileItem.UID);
                folder.fetch(msgs, fp);
            } else {
                var fp = new jakarta.mail.FetchProfile();
                fp.add(jakarta.mail.FetchProfile.Item.ENVELOPE);
                folder.fetch(msgs, fp);
            }

            List<EmailInboxMessageDTO> out = new ArrayList<>();
            for (int i = msgs.length - 1; i >= 0; i--) {
                Message m = msgs[i];
                EmailInboxMessageDTO dto = new EmailInboxMessageDTO();
                dto.setId(getMessageId(folder, m));
                dto.setSubject(safeString(m.getSubject(), 200));
                dto.setFrom(formatFrom(m.getFrom()));
                Address[] to = m.getRecipients(Message.RecipientType.TO);
                if (to == null || to.length == 0) to = m.getAllRecipients();
                dto.setTo(safeString(formatRecipients(to), 500));
                Date sent = m.getSentDate();
                dto.setSentAt(sent == null ? null : sent.getTime());
                dto.setContent(safeString(extractText(m), 20_000));
                out.add(dto);
            }
            return out;
        } catch (Exception e) {
            throw new IllegalStateException("收件箱读取失败: " + e.getMessage(), e);
        } finally {
            try {
                if (folder != null && folder.isOpen()) folder.close(false);
            } catch (Exception ignored) {
            }
            try {
                if (store != null && store.isConnected()) store.close();
            } catch (Exception ignored) {
            }
        }
    }

    private String getConfig(String key, String defaultValue) {
        String val = systemConfigurationService.getConfig(key);
        if (val != null && !val.isBlank()) return val;
        return defaultValue;
    }

    private static String getMessageId(Folder folder, Message m) {
        try {
            if (folder instanceof UIDFolder uidFolder) {
                long uid = uidFolder.getUID(m);
                if (uid > 0) return String.valueOf(uid);
            }
        } catch (Exception ignored) {
        }
        try {
            String[] h = m.getHeader("Message-ID");
            if (h != null && h.length > 0 && h[0] != null && !h[0].isBlank()) return h[0].trim();
        } catch (Exception ignored) {
        }
        try {
            return String.valueOf(m.getMessageNumber());
        } catch (Exception e) {
            return "";
        }
    }

    private static String formatFrom(Object[] from) {
        if (from == null || from.length == 0 || from[0] == null) return "";
        try {
            if (from[0] instanceof InternetAddress ia) {
                String personal = ia.getPersonal();
                String addr = ia.getAddress();
                if (personal != null && !personal.isBlank()) return personal.trim() + " <" + (addr == null ? "" : addr.trim()) + ">";
                return addr == null ? "" : addr.trim();
            }
            return from[0].toString();
        } catch (Exception e) {
            return "";
        }
    }

    private static String formatRecipients(Address[] recipients) {
        if (recipients == null || recipients.length == 0) return "";
        List<String> out = new ArrayList<>();
        for (Address a : recipients) {
            if (a == null) continue;
            try {
                if (a instanceof InternetAddress ia) {
                    String personal = ia.getPersonal();
                    String addr = ia.getAddress();
                    if (personal != null && !personal.isBlank()) {
                        out.add(personal.trim() + " <" + (addr == null ? "" : addr.trim()) + ">");
                    } else {
                        out.add(addr == null ? "" : addr.trim());
                    }
                } else {
                    out.add(a.toString());
                }
            } catch (Exception ignored) {
            }
        }
        return String.join(", ", out);
    }

    private static String extractText(Part p) throws Exception {
        if (p.isMimeType("text/plain")) {
            Object c = p.getContent();
            if (c == null) return "";
            if (c instanceof String s) return s;
            return new String(c.toString().getBytes(StandardCharsets.UTF_8), StandardCharsets.UTF_8);
        }
        if (p.isMimeType("text/html")) {
            Object c = p.getContent();
            return c == null ? "" : c.toString();
        }
        if (p.isMimeType("multipart/*")) {
            Multipart mp = (Multipart) p.getContent();
            String html = null;
            for (int i = 0; i < mp.getCount(); i++) {
                BodyPart bp = mp.getBodyPart(i);
                String t = extractText(bp);
                if (t == null || t.isBlank()) continue;
                if (bp.isMimeType("text/plain")) return t;
                if (html == null && bp.isMimeType("text/html")) html = t;
            }
            return html == null ? "" : html;
        }
        if (p.isMimeType("message/rfc822")) {
            Object c = p.getContent();
            if (c instanceof Part nested) return extractText(nested);
        }
        return "";
    }

    private static String safeString(String v, int max) {
        if (v == null) return "";
        String t = v.trim();
        if (t.length() <= max) return t;
        return t.substring(0, max);
    }
}
