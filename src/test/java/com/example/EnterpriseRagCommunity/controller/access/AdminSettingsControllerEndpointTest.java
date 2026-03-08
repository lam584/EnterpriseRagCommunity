package com.example.EnterpriseRagCommunity.controller.access;

import com.example.EnterpriseRagCommunity.dto.access.EmailInboxMessageDTO;
import com.example.EnterpriseRagCommunity.dto.access.EmailInboxSettingsDTO;
import com.example.EnterpriseRagCommunity.dto.access.EmailTestSendDTO;
import com.example.EnterpriseRagCommunity.repository.access.RolesRepository;
import com.example.EnterpriseRagCommunity.service.access.AuditDiffBuilder;
import com.example.EnterpriseRagCommunity.service.access.AuditLogWriter;
import com.example.EnterpriseRagCommunity.service.access.Security2faPolicyService;
import com.example.EnterpriseRagCommunity.service.config.SystemConfigurationService;
import com.example.EnterpriseRagCommunity.service.monitor.AppSettingsService;
import com.example.EnterpriseRagCommunity.service.notify.EmailInboxService;
import com.example.EnterpriseRagCommunity.service.notify.EmailSenderService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.ObjectProvider;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AdminSettingsControllerEndpointTest {

    @Test
    void listEmailInbox_should_throw_when_inbox_service_unavailable() {
        AppSettingsService appSettingsService = mock(AppSettingsService.class);
        RolesRepository rolesRepository = mock(RolesRepository.class);
        SystemConfigurationService systemConfigurationService = mock(SystemConfigurationService.class);
        EmailSenderService emailSenderService = mock(EmailSenderService.class);
        @SuppressWarnings("unchecked")
        ObjectProvider<EmailInboxService> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(null);
        Security2faPolicyService security2faPolicyService = mock(Security2faPolicyService.class);
        AuditLogWriter auditLogWriter = mock(AuditLogWriter.class);
        AuditDiffBuilder auditDiffBuilder = mock(AuditDiffBuilder.class);

        AdminSettingsController controller = new AdminSettingsController(
                appSettingsService,
                rolesRepository,
                systemConfigurationService,
                emailSenderService,
                provider,
                security2faPolicyService,
                auditLogWriter,
                auditDiffBuilder
        );

        assertThatThrownBy(() -> controller.listEmailInbox(null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("收件箱功能不可用");
    }

    @Test
    void listEmailSent_should_default_to_sent_folder_when_blank() {
        AppSettingsService appSettingsService = mock(AppSettingsService.class);
        when(appSettingsService.getLongOrDefault(anyString(), anyLong())).thenAnswer(inv -> inv.getArgument(1));
        when(appSettingsService.getString(anyString())).thenReturn(Optional.empty());
        when(appSettingsService.getString(eq("email_sent_folder"))).thenReturn(Optional.of(" "));

        RolesRepository rolesRepository = mock(RolesRepository.class);
        SystemConfigurationService systemConfigurationService = mock(SystemConfigurationService.class);
        EmailSenderService emailSenderService = mock(EmailSenderService.class);

        EmailInboxService inbox = mock(EmailInboxService.class);
        when(inbox.listInbox(any(EmailInboxSettingsDTO.class), anyInt())).thenReturn(List.<EmailInboxMessageDTO>of());

        @SuppressWarnings("unchecked")
        ObjectProvider<EmailInboxService> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(inbox);

        Security2faPolicyService security2faPolicyService = mock(Security2faPolicyService.class);
        AuditLogWriter auditLogWriter = mock(AuditLogWriter.class);
        AuditDiffBuilder auditDiffBuilder = mock(AuditDiffBuilder.class);

        AdminSettingsController controller = new AdminSettingsController(
                appSettingsService,
                rolesRepository,
                systemConfigurationService,
                emailSenderService,
                provider,
                security2faPolicyService,
                auditLogWriter,
                auditDiffBuilder
        );

        controller.listEmailSent(null);

        ArgumentCaptor<EmailInboxSettingsDTO> cfg = ArgumentCaptor.forClass(EmailInboxSettingsDTO.class);
        verify(inbox).listInbox(cfg.capture(), eq(20));
        assertThat(cfg.getValue().getFolder()).isEqualTo("Sent");
    }

    @Test
    void testEmail_should_throw_when_email_disabled() {
        AppSettingsService appSettingsService = mock(AppSettingsService.class);
        when(appSettingsService.getLongOrDefault(anyString(), anyLong())).thenAnswer(inv -> inv.getArgument(1));
        when(appSettingsService.getLongOrDefault(eq("email_enabled"), anyLong())).thenReturn(0L);
        when(appSettingsService.getString(anyString())).thenReturn(Optional.empty());

        RolesRepository rolesRepository = mock(RolesRepository.class);
        SystemConfigurationService systemConfigurationService = mock(SystemConfigurationService.class);
        when(systemConfigurationService.getConfig(anyString())).thenReturn("");

        EmailSenderService emailSenderService = mock(EmailSenderService.class);
        @SuppressWarnings("unchecked")
        ObjectProvider<EmailInboxService> provider = mock(ObjectProvider.class);
        Security2faPolicyService security2faPolicyService = mock(Security2faPolicyService.class);
        AuditLogWriter auditLogWriter = mock(AuditLogWriter.class);
        AuditDiffBuilder auditDiffBuilder = mock(AuditDiffBuilder.class);

        AdminSettingsController controller = new AdminSettingsController(
                appSettingsService,
                rolesRepository,
                systemConfigurationService,
                emailSenderService,
                provider,
                security2faPolicyService,
                auditLogWriter,
                auditDiffBuilder
        );

        EmailTestSendDTO dto = new EmailTestSendDTO();
        dto.setTo("to@example.com");

        assertThatThrownBy(() -> controller.testEmail(dto))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("邮箱服务未启用");
    }

    @Test
    void testEmail_should_prefix_subject_when_prefix_present() {
        AppSettingsService appSettingsService = mock(AppSettingsService.class);
        when(appSettingsService.getLongOrDefault(anyString(), anyLong())).thenAnswer(inv -> inv.getArgument(1));
        when(appSettingsService.getLongOrDefault(eq("email_enabled"), anyLong())).thenReturn(1L);
        when(appSettingsService.getString(anyString())).thenReturn(Optional.empty());
        when(appSettingsService.getString(eq("email_subject_prefix"))).thenReturn(Optional.of("Pre"));

        RolesRepository rolesRepository = mock(RolesRepository.class);
        SystemConfigurationService systemConfigurationService = mock(SystemConfigurationService.class);
        when(systemConfigurationService.getConfig(anyString())).thenReturn("x");

        EmailSenderService emailSenderService = mock(EmailSenderService.class);
        @SuppressWarnings("unchecked")
        ObjectProvider<EmailInboxService> provider = mock(ObjectProvider.class);
        Security2faPolicyService security2faPolicyService = mock(Security2faPolicyService.class);
        AuditLogWriter auditLogWriter = mock(AuditLogWriter.class);
        AuditDiffBuilder auditDiffBuilder = mock(AuditDiffBuilder.class);

        AdminSettingsController controller = new AdminSettingsController(
                appSettingsService,
                rolesRepository,
                systemConfigurationService,
                emailSenderService,
                provider,
                security2faPolicyService,
                auditLogWriter,
                auditDiffBuilder
        );

        EmailTestSendDTO dto = new EmailTestSendDTO();
        dto.setTo("to@example.com");

        controller.testEmail(dto);

        ArgumentCaptor<String> subject = ArgumentCaptor.forClass(String.class);
        verify(emailSenderService).sendPlainText(any(), eq("to@example.com"), subject.capture(), any(), eq("ADMIN_TEST"));
        assertThat(subject.getValue()).isEqualTo("Pre 邮件配置测试");
    }
}
