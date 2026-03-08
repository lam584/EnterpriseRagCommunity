package com.example.EnterpriseRagCommunity.service.notify;

import com.example.EnterpriseRagCommunity.config.AppMailProperties;
import com.example.EnterpriseRagCommunity.dto.access.EmailInboxMessageDTO;
import com.example.EnterpriseRagCommunity.dto.access.EmailInboxSettingsDTO;
import com.example.EnterpriseRagCommunity.service.config.SystemConfigurationService;
import jakarta.mail.Address;
import jakarta.mail.Folder;
import jakarta.mail.Message;
import jakarta.mail.Session;
import jakarta.mail.Store;
import jakarta.mail.UIDFolder;
import jakarta.mail.internet.InternetAddress;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.util.Date;
import java.util.List;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.withSettings;

class EmailInboxServiceTest {

    @Test
    void listInbox_emptyFolder_returnsEmpty_andClosesResources() throws Exception {
        AppMailProperties appMailProperties = new AppMailProperties();
        appMailProperties.setUsername("u@example.com");
        appMailProperties.setPassword("pw");

        SystemConfigurationService systemConfigurationService = mock(SystemConfigurationService.class);
        EmailInboxService svc = new EmailInboxService(appMailProperties, systemConfigurationService);

        EmailInboxSettingsDTO cfg = new EmailInboxSettingsDTO();
        cfg.setHost("imap.example.com");
        cfg.setEncryption("SSL");
        cfg.setPortEncrypted(993);
        cfg.setFolder("INBOX");

        Session session = mock(Session.class);
        Store store = mock(Store.class);
        Folder folder = mock(Folder.class, withSettings().extraInterfaces(UIDFolder.class));

        when(session.getStore("imaps")).thenReturn(store);
        when(store.getFolder("INBOX")).thenReturn(folder);
        when(folder.getMessageCount()).thenReturn(0);
        when(folder.isOpen()).thenReturn(true);
        when(store.isConnected()).thenReturn(true);

        try (MockedStatic<Session> ms = org.mockito.Mockito.mockStatic(Session.class)) {
            ms.when(() -> Session.getInstance(any(Properties.class))).thenReturn(session);
            List<EmailInboxMessageDTO> out = svc.listInbox(cfg, 10);
            assertEquals(List.of(), out);
        }

        verify(folder).close(false);
        verify(store).close();
    }

    @Test
    void listInbox_success_readsMessages_buildsDisplayFields() throws Exception {
        AppMailProperties appMailProperties = new AppMailProperties();
        appMailProperties.setUsername("u@example.com");
        appMailProperties.setPassword("pw");

        SystemConfigurationService systemConfigurationService = mock(SystemConfigurationService.class);
        EmailInboxService svc = new EmailInboxService(appMailProperties, systemConfigurationService);

        EmailInboxSettingsDTO cfg = new EmailInboxSettingsDTO();
        cfg.setHost("imap.example.com");
        cfg.setEncryption("SSL");
        cfg.setPortEncrypted(993);
        cfg.setFolder("INBOX");

        Session session = mock(Session.class);
        Store store = mock(Store.class);
        Folder folder = mock(Folder.class, withSettings().extraInterfaces(UIDFolder.class));
        UIDFolder uidFolder = (UIDFolder) folder;

        Message msg = mock(Message.class);
        when(msg.getSubject()).thenReturn("subject");
        when(msg.getFrom()).thenReturn(new Address[]{new InternetAddress("a@example.com", "Alice")});
        when(msg.getRecipients(Message.RecipientType.TO)).thenReturn(null);
        when(msg.getAllRecipients()).thenReturn(new Address[]{new InternetAddress("to@example.com")});
        when(msg.getSentDate()).thenReturn(new Date(1000));
        when(msg.isMimeType("text/plain")).thenReturn(true);
        when(msg.getContent()).thenReturn("body");
        when(msg.getHeader("Message-ID")).thenReturn(null);
        when(msg.getMessageNumber()).thenReturn(7);

        when(session.getStore("imaps")).thenReturn(store);
        when(store.getFolder("INBOX")).thenReturn(folder);
        when(folder.getMessageCount()).thenReturn(1);
        when(folder.getMessages(1, 1)).thenReturn(new Message[]{msg});
        when(folder.isOpen()).thenReturn(true);
        when(store.isConnected()).thenReturn(true);
        when(uidFolder.getUID(msg)).thenReturn(123L);

        try (MockedStatic<Session> ms = org.mockito.Mockito.mockStatic(Session.class)) {
            ms.when(() -> Session.getInstance(any(Properties.class))).thenReturn(session);
            List<EmailInboxMessageDTO> out = svc.listInbox(cfg, 10);

            assertNotNull(out);
            assertEquals(1, out.size());
            EmailInboxMessageDTO dto = out.get(0);
            assertEquals("123", dto.getId());
            assertEquals("subject", dto.getSubject());
            assertEquals("Alice <a@example.com>", dto.getFrom());
            assertEquals("to@example.com", dto.getTo());
            assertEquals(1000L, dto.getSentAt());
            assertEquals("body", dto.getContent());
        }

        verify(folder).close(false);
        verify(store).close();
    }

    @Test
    void listInbox_connectFails_wrapsException() throws Exception {
        AppMailProperties appMailProperties = new AppMailProperties();
        appMailProperties.setUsername("u@example.com");
        appMailProperties.setPassword("pw");

        SystemConfigurationService systemConfigurationService = mock(SystemConfigurationService.class);
        EmailInboxService svc = new EmailInboxService(appMailProperties, systemConfigurationService);

        EmailInboxSettingsDTO cfg = new EmailInboxSettingsDTO();
        cfg.setHost("imap.example.com");
        cfg.setEncryption("SSL");
        cfg.setPortEncrypted(993);

        Session session = mock(Session.class);
        Store store = mock(Store.class);
        when(session.getStore("imaps")).thenReturn(store);
        doThrow(new RuntimeException("boom")).when(store).connect(anyString(), anyInt(), anyString(), anyString());

        try (MockedStatic<Session> ms = org.mockito.Mockito.mockStatic(Session.class)) {
            ms.when(() -> Session.getInstance(any(Properties.class))).thenReturn(session);
            assertThrows(IllegalStateException.class, () -> svc.listInbox(cfg, 10));
        }
    }
}
