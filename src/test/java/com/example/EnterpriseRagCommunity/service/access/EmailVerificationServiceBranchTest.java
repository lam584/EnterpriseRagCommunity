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

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class EmailVerificationServiceBranchTest {

    @Test
    void defaults_should_clamp_ttl_and_resend_wait() {
        EmailVerificationsRepository repo = mock(EmailVerificationsRepository.class);
        AppSettingsService settings = mock(AppSettingsService.class);
        when(settings.getLongOrDefault(eq("email_otp_ttl_seconds"), eq(600L))).thenReturn(30L).thenReturn(5000L);
        when(settings.getLongOrDefault(eq("email_otp_resend_wait_seconds"), eq(120L))).thenReturn(-10L).thenReturn(7200L);

        EmailVerificationService service = new EmailVerificationService(repo, settings);

        assertEquals(60, service.getDefaultTtlSeconds());
        assertEquals(3600, service.getDefaultTtlSeconds());
        assertEquals(0, service.getDefaultResendWaitSeconds());
        assertEquals(3600, service.getDefaultResendWaitSeconds());
    }

    @Test
    void issueCode_should_validate_arguments() {
        EmailVerificationsRepository repo = mock(EmailVerificationsRepository.class);
        AppSettingsService settings = mock(AppSettingsService.class);
        EmailVerificationService service = new EmailVerificationService(repo, settings);

        assertThrows(IllegalArgumentException.class, () -> service.issueCode(null, EmailVerificationPurpose.REGISTER, Duration.ofMinutes(1), Duration.ZERO));
        assertThrows(IllegalArgumentException.class, () -> service.issueCode(0L, EmailVerificationPurpose.REGISTER, Duration.ofMinutes(1), Duration.ZERO));
        assertThrows(IllegalArgumentException.class, () -> service.issueCode(1L, null, Duration.ofMinutes(1), Duration.ZERO));
        assertThrows(IllegalArgumentException.class, () -> service.issueCode(1L, EmailVerificationPurpose.REGISTER, null, Duration.ZERO));
        assertThrows(IllegalArgumentException.class, () -> service.issueCode(1L, EmailVerificationPurpose.REGISTER, Duration.ZERO, Duration.ZERO));
        assertThrows(IllegalArgumentException.class, () -> service.issueCode(1L, EmailVerificationPurpose.REGISTER, Duration.ofSeconds(-1), Duration.ZERO));
        assertThrows(IllegalArgumentException.class, () -> service.issueCode(1L, EmailVerificationPurpose.REGISTER, Duration.ofMinutes(1), null));
        assertThrows(IllegalArgumentException.class, () -> service.issueCode(1L, EmailVerificationPurpose.REGISTER, Duration.ofMinutes(1), Duration.ofSeconds(-1)));
    }

    @Test
    void issueCode_should_skip_rate_limit_when_min_interval_zero_and_trim_blank_target() {
        EmailVerificationsRepository repo = mock(EmailVerificationsRepository.class);
        AppSettingsService settings = mock(AppSettingsService.class);
        when(repo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        EmailVerificationService service = new EmailVerificationService(repo, settings);
        service.issueCode(1L, EmailVerificationPurpose.CHANGE_EMAIL, "   ", Duration.ofMinutes(10), Duration.ZERO);

        verify(repo, never()).findFirstByUserIdAndPurposeOrderByCreatedAtDesc(anyLong(), any());
        ArgumentCaptor<EmailVerificationsEntity> captor = ArgumentCaptor.forClass(EmailVerificationsEntity.class);
        verify(repo).save(captor.capture());
        assertNull(captor.getValue().getTargetEmail());
    }

    @Test
    void issueCode_should_ignore_last_record_without_created_at() {
        EmailVerificationsRepository repo = mock(EmailVerificationsRepository.class);
        AppSettingsService settings = mock(AppSettingsService.class);
        EmailVerificationsEntity last = new EmailVerificationsEntity();
        last.setCreatedAt(null);
        when(repo.findFirstByUserIdAndPurposeOrderByCreatedAtDesc(eq(1L), eq(EmailVerificationPurpose.LOGIN_2FA)))
                .thenReturn(Optional.of(last));
        when(repo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        EmailVerificationService service = new EmailVerificationService(repo, settings);
        assertDoesNotThrow(() -> service.issueCode(1L, EmailVerificationPurpose.LOGIN_2FA, Duration.ofMinutes(10), Duration.ofSeconds(30)));
        verify(repo).save(any());
    }

    @Test
    void issueCode_should_enforce_rate_limit_after_consumed_when_reduction_is_small() {
        EmailVerificationsRepository repo = mock(EmailVerificationsRepository.class);
        AppSettingsService settings = mock(AppSettingsService.class);
        EmailVerificationsEntity last = new EmailVerificationsEntity();
        last.setCreatedAt(LocalDateTime.now());
        last.setConsumedAt(LocalDateTime.now());
        when(repo.findFirstByUserIdAndPurposeOrderByCreatedAtDesc(eq(1L), eq(EmailVerificationPurpose.CHANGE_PASSWORD)))
                .thenReturn(Optional.of(last));
        when(settings.getLongOrDefault(eq("email_otp_resend_wait_reduction_after_verified_seconds"), eq(0L))).thenReturn(5L);

        EmailVerificationService service = new EmailVerificationService(repo, settings);
        assertThrows(IllegalArgumentException.class, () ->
                service.issueCode(1L, EmailVerificationPurpose.CHANGE_PASSWORD, Duration.ofMinutes(10), Duration.ofSeconds(30))
        );
        verify(repo, never()).save(any());
    }

    @Test
    void verifyAndConsume_should_validate_and_cover_not_found_and_trimmed_paths() {
        EmailVerificationsRepository repo = mock(EmailVerificationsRepository.class);
        AppSettingsService settings = mock(AppSettingsService.class);
        EmailVerificationService service = new EmailVerificationService(repo, settings);

        assertThrows(IllegalArgumentException.class, () -> service.verifyAndConsume(null, EmailVerificationPurpose.TOTP_ENABLE, "123456"));
        assertThrows(IllegalArgumentException.class, () -> service.verifyAndConsume(0L, EmailVerificationPurpose.TOTP_ENABLE, "123456"));
        assertThrows(IllegalArgumentException.class, () -> service.verifyAndConsume(1L, null, "123456"));
        assertThrows(IllegalArgumentException.class, () -> service.verifyAndConsume(1L, EmailVerificationPurpose.TOTP_ENABLE, "   "));

        when(repo.findFirstByUserIdAndPurposeAndConsumedAtIsNullAndExpiresAtAfterOrderByCreatedAtDesc(eq(1L), eq(EmailVerificationPurpose.REGISTER), any()))
                .thenReturn(Optional.empty());
        assertThrows(IllegalArgumentException.class, () -> service.verifyAndConsume(1L, EmailVerificationPurpose.REGISTER, "123456"));

        EmailVerificationsEntity entity = new EmailVerificationsEntity();
        entity.setCode("999999");
        entity.setCreatedAt(LocalDateTime.now().minusMinutes(1));
        entity.setExpiresAt(LocalDateTime.now().plusMinutes(9));
        when(repo.findFirstByUserIdAndPurposeAndTargetEmailAndConsumedAtIsNullAndExpiresAtAfterOrderByCreatedAtDesc(
                eq(1L), eq(EmailVerificationPurpose.CHANGE_EMAIL), eq("abc@example.invalid"), any()))
                .thenReturn(Optional.of(entity));
        when(repo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        assertDoesNotThrow(() -> service.verifyAndConsume(
                1L, EmailVerificationPurpose.CHANGE_EMAIL, "  abc@example.invalid  ", " 999999 "));
        verify(repo).save(any());
    }
}
