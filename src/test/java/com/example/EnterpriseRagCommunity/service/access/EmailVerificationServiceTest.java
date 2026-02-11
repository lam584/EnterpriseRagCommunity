package com.example.EnterpriseRagCommunity.service.access;

import com.example.EnterpriseRagCommunity.entity.access.EmailVerificationsEntity;
import com.example.EnterpriseRagCommunity.entity.access.enums.EmailVerificationPurpose;
import com.example.EnterpriseRagCommunity.repository.access.EmailVerificationsRepository;
import com.example.EnterpriseRagCommunity.service.monitor.AppSettingsService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class EmailVerificationServiceTest {

    @Test
    void issueCode_savesEntity_andReturns6Digits() {
        EmailVerificationsRepository repo = mock(EmailVerificationsRepository.class);
        AppSettingsService settings = mock(AppSettingsService.class);
        when(repo.findFirstByUserIdAndPurposeOrderByCreatedAtDesc(anyLong(), any())).thenReturn(Optional.empty());
        when(repo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        EmailVerificationService svc = new EmailVerificationService(repo, settings);
        String code = svc.issueCode(1L, EmailVerificationPurpose.REGISTER, Duration.ofMinutes(10), Duration.ofSeconds(0));

        assertNotNull(code);
        assertTrue(code.matches("^\\d{6}$"));

        ArgumentCaptor<EmailVerificationsEntity> captor = ArgumentCaptor.forClass(EmailVerificationsEntity.class);
        verify(repo).save(captor.capture());
        EmailVerificationsEntity saved = captor.getValue();
        assertEquals(1L, saved.getUserId());
        assertEquals(EmailVerificationPurpose.REGISTER, saved.getPurpose());
        assertEquals(code, saved.getCode());
        assertNotNull(saved.getCreatedAt());
        assertNotNull(saved.getExpiresAt());
        assertNull(saved.getConsumedAt());
        assertTrue(saved.getExpiresAt().isAfter(saved.getCreatedAt()));
    }

    @Test
    void issueCode_withTargetEmail_savesTargetEmail() {
        EmailVerificationsRepository repo = mock(EmailVerificationsRepository.class);
        AppSettingsService settings = mock(AppSettingsService.class);
        when(repo.findFirstByUserIdAndPurposeOrderByCreatedAtDesc(anyLong(), any())).thenReturn(Optional.empty());
        when(repo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        EmailVerificationService svc = new EmailVerificationService(repo, settings);
        String code = svc.issueCode(1L, EmailVerificationPurpose.CHANGE_EMAIL, "a@example.invalid", Duration.ofMinutes(10), Duration.ofSeconds(0));

        assertNotNull(code);

        ArgumentCaptor<EmailVerificationsEntity> captor = ArgumentCaptor.forClass(EmailVerificationsEntity.class);
        verify(repo).save(captor.capture());
        assertEquals("a@example.invalid", captor.getValue().getTargetEmail());
    }

    @Test
    void issueCode_rateLimited_throws() {
        EmailVerificationsRepository repo = mock(EmailVerificationsRepository.class);
        AppSettingsService settings = mock(AppSettingsService.class);
        EmailVerificationsEntity last = new EmailVerificationsEntity();
        last.setCreatedAt(LocalDateTime.now());
        when(repo.findFirstByUserIdAndPurposeOrderByCreatedAtDesc(eq(1L), eq(EmailVerificationPurpose.CHANGE_PASSWORD)))
                .thenReturn(Optional.of(last));

        EmailVerificationService svc = new EmailVerificationService(repo, settings);
        assertThrows(IllegalArgumentException.class, () ->
                svc.issueCode(1L, EmailVerificationPurpose.CHANGE_PASSWORD, Duration.ofMinutes(10), Duration.ofSeconds(30))
        );
        verify(repo, never()).save(any());
    }

    @Test
    void issueCode_rateLimited_butLastConsumedAndReductionAllows_respectsReduction() {
        EmailVerificationsRepository repo = mock(EmailVerificationsRepository.class);
        AppSettingsService settings = mock(AppSettingsService.class);
        EmailVerificationsEntity last = new EmailVerificationsEntity();
        last.setCreatedAt(LocalDateTime.now());
        last.setConsumedAt(LocalDateTime.now());
        when(repo.findFirstByUserIdAndPurposeOrderByCreatedAtDesc(eq(1L), eq(EmailVerificationPurpose.LOGIN_2FA)))
                .thenReturn(Optional.of(last));
        when(settings.getLongOrDefault(eq("email_otp_resend_wait_reduction_after_verified_seconds"), eq(0L))).thenReturn(30L);
        when(repo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        EmailVerificationService svc = new EmailVerificationService(repo, settings);
        assertDoesNotThrow(() ->
                svc.issueCode(1L, EmailVerificationPurpose.LOGIN_2FA, Duration.ofMinutes(10), Duration.ofSeconds(30))
        );
        verify(repo).save(any());
    }

    @Test
    void verifyAndConsume_wrongCode_throws() {
        EmailVerificationsRepository repo = mock(EmailVerificationsRepository.class);
        AppSettingsService settings = mock(AppSettingsService.class);
        EmailVerificationsEntity e = new EmailVerificationsEntity();
        e.setId(10L);
        e.setUserId(1L);
        e.setPurpose(EmailVerificationPurpose.TOTP_ENABLE);
        e.setCode("123456");
        e.setCreatedAt(LocalDateTime.now().minusMinutes(1));
        e.setExpiresAt(LocalDateTime.now().plusMinutes(9));

        when(repo.findFirstByUserIdAndPurposeAndConsumedAtIsNullAndExpiresAtAfterOrderByCreatedAtDesc(eq(1L), eq(EmailVerificationPurpose.TOTP_ENABLE), any()))
                .thenReturn(Optional.of(e));

        EmailVerificationService svc = new EmailVerificationService(repo, settings);
        assertThrows(IllegalArgumentException.class, () -> svc.verifyAndConsume(1L, EmailVerificationPurpose.TOTP_ENABLE, "000000"));
        verify(repo, never()).save(any());
    }

    @Test
    void verifyAndConsume_ok_consumes() {
        EmailVerificationsRepository repo = mock(EmailVerificationsRepository.class);
        AppSettingsService settings = mock(AppSettingsService.class);
        EmailVerificationsEntity e = new EmailVerificationsEntity();
        e.setId(10L);
        e.setUserId(1L);
        e.setPurpose(EmailVerificationPurpose.PASSWORD_RESET);
        e.setCode("123456");
        e.setCreatedAt(LocalDateTime.now().minusMinutes(1));
        e.setExpiresAt(LocalDateTime.now().plusMinutes(9));

        when(repo.findFirstByUserIdAndPurposeAndConsumedAtIsNullAndExpiresAtAfterOrderByCreatedAtDesc(eq(1L), eq(EmailVerificationPurpose.PASSWORD_RESET), any()))
                .thenReturn(Optional.of(e));
        when(repo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        EmailVerificationService svc = new EmailVerificationService(repo, settings);
        svc.verifyAndConsume(1L, EmailVerificationPurpose.PASSWORD_RESET, "123456");

        ArgumentCaptor<EmailVerificationsEntity> captor = ArgumentCaptor.forClass(EmailVerificationsEntity.class);
        verify(repo).save(captor.capture());
        assertNotNull(captor.getValue().getConsumedAt());
    }

    @Test
    void verifyAndConsume_withTargetEmail_usesTargetQuery() {
        EmailVerificationsRepository repo = mock(EmailVerificationsRepository.class);
        AppSettingsService settings = mock(AppSettingsService.class);
        EmailVerificationsEntity e = new EmailVerificationsEntity();
        e.setId(10L);
        e.setUserId(1L);
        e.setPurpose(EmailVerificationPurpose.CHANGE_EMAIL);
        e.setTargetEmail("a@example.invalid");
        e.setCode("123456");
        e.setCreatedAt(LocalDateTime.now().minusMinutes(1));
        e.setExpiresAt(LocalDateTime.now().plusMinutes(9));

        when(repo.findFirstByUserIdAndPurposeAndTargetEmailAndConsumedAtIsNullAndExpiresAtAfterOrderByCreatedAtDesc(eq(1L), eq(EmailVerificationPurpose.CHANGE_EMAIL), eq("a@example.invalid"), any()))
                .thenReturn(Optional.of(e));
        when(repo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        EmailVerificationService svc = new EmailVerificationService(repo, settings);
        svc.verifyAndConsume(1L, EmailVerificationPurpose.CHANGE_EMAIL, "a@example.invalid", "123456");

        verify(repo, never()).findFirstByUserIdAndPurposeAndConsumedAtIsNullAndExpiresAtAfterOrderByCreatedAtDesc(anyLong(), any(), any());
        verify(repo).save(any());
    }
}
