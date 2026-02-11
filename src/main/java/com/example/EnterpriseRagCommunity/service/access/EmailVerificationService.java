package com.example.EnterpriseRagCommunity.service.access;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.EnterpriseRagCommunity.entity.access.EmailVerificationsEntity;
import com.example.EnterpriseRagCommunity.entity.access.enums.EmailVerificationPurpose;
import com.example.EnterpriseRagCommunity.repository.access.EmailVerificationsRepository;
import com.example.EnterpriseRagCommunity.service.monitor.AppSettingsService;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class EmailVerificationService {
    private static final SecureRandom random = new SecureRandom();
    private static final String KEY_EMAIL_OTP_TTL_SECONDS = "email_otp_ttl_seconds";
    private static final String KEY_EMAIL_OTP_RESEND_WAIT_SECONDS = "email_otp_resend_wait_seconds";
    private static final String KEY_EMAIL_OTP_RESEND_WAIT_REDUCTION_AFTER_VERIFIED_SECONDS = "email_otp_resend_wait_reduction_after_verified_seconds";

    private final EmailVerificationsRepository emailVerificationsRepository;
    private final AppSettingsService appSettingsService;

    private Duration getDefaultTtl() {
        long seconds = appSettingsService.getLongOrDefault(KEY_EMAIL_OTP_TTL_SECONDS, 600L);
        if (seconds < 60L) seconds = 60L;
        if (seconds > 3600L) seconds = 3600L;
        return Duration.ofSeconds(seconds);
    }

    private Duration getDefaultMinInterval() {
        long seconds = appSettingsService.getLongOrDefault(KEY_EMAIL_OTP_RESEND_WAIT_SECONDS, 120L);
        if (seconds < 0L) seconds = 0L;
        if (seconds > 3600L) seconds = 3600L;
        return Duration.ofSeconds(seconds);
    }

    private Duration getDefaultMinIntervalReductionAfterVerified() {
        long seconds = appSettingsService.getLongOrDefault(KEY_EMAIL_OTP_RESEND_WAIT_REDUCTION_AFTER_VERIFIED_SECONDS, 0L);
        if (seconds < 0L) seconds = 0L;
        if (seconds > 3600L) seconds = 3600L;
        return Duration.ofSeconds(seconds);
    }

    public int getDefaultTtlSeconds() {
        return (int) getDefaultTtl().getSeconds();
    }

    public int getDefaultResendWaitSeconds() {
        return (int) getDefaultMinInterval().getSeconds();
    }

    @Transactional
    public String issueCode(Long userId, EmailVerificationPurpose purpose) {
        return issueCode(userId, purpose, getDefaultTtl(), getDefaultMinInterval());
    }

    @Transactional
    public String issueCode(Long userId, EmailVerificationPurpose purpose, Duration ttl, Duration minInterval) {
        return issueCode(userId, purpose, null, ttl, minInterval);
    }

    @Transactional
    public String issueCode(Long userId, EmailVerificationPurpose purpose, String targetEmail) {
        return issueCode(userId, purpose, targetEmail, getDefaultTtl(), getDefaultMinInterval());
    }

    @Transactional
    public String issueCode(Long userId, EmailVerificationPurpose purpose, String targetEmail, Duration ttl, Duration minInterval) {
        if (userId == null || userId <= 0) throw new IllegalArgumentException("用户ID不能为空");
        if (purpose == null) throw new IllegalArgumentException("用途不能为空");
        if (ttl == null || ttl.isNegative() || ttl.isZero()) throw new IllegalArgumentException("验证码有效期不合法");
        if (minInterval == null || minInterval.isNegative()) throw new IllegalArgumentException("发送间隔不合法");

        LocalDateTime now = LocalDateTime.now();
        if (!minInterval.isZero()) {
            emailVerificationsRepository.findFirstByUserIdAndPurposeOrderByCreatedAtDesc(userId, purpose).ifPresent(last -> {
                LocalDateTime createdAt = last.getCreatedAt();
                if (createdAt == null) return;

                Duration effectiveMinInterval = minInterval;
                if (last.getConsumedAt() != null) {
                    Duration reduction = getDefaultMinIntervalReductionAfterVerified();
                    if (!reduction.isZero()) {
                        effectiveMinInterval = reduction.compareTo(effectiveMinInterval) >= 0 ? Duration.ZERO : effectiveMinInterval.minus(reduction);
                    }
                }

                if (!effectiveMinInterval.isZero() && createdAt.isAfter(now.minus(effectiveMinInterval))) {
                    throw new IllegalArgumentException("发送过于频繁，请稍后重试");
                }
            });
        }

        String code = String.format("%06d", random.nextInt(1_000_000));

        EmailVerificationsEntity e = new EmailVerificationsEntity();
        e.setUserId(userId);
        e.setPurpose(purpose);
        e.setCode(code);
        e.setTargetEmail(targetEmail == null || targetEmail.isBlank() ? null : targetEmail.trim());
        e.setCreatedAt(now);
        e.setExpiresAt(now.plus(ttl));
        e.setConsumedAt(null);
        emailVerificationsRepository.save(e);

        return code;
    }

    @Transactional
    public void verifyAndConsume(Long userId, EmailVerificationPurpose purpose, String code) {
        verifyAndConsume(userId, purpose, null, code);
    }

    @Transactional
    public void verifyAndConsume(Long userId, EmailVerificationPurpose purpose, String targetEmail, String code) {
        if (userId == null || userId <= 0) throw new IllegalArgumentException("用户ID不能为空");
        if (purpose == null) throw new IllegalArgumentException("用途不能为空");
        if (code == null || code.isBlank()) throw new IllegalArgumentException("验证码不能为空");

        LocalDateTime now = LocalDateTime.now();
        Optional<EmailVerificationsEntity> found;
        if (targetEmail == null || targetEmail.isBlank()) {
            found = emailVerificationsRepository
                    .findFirstByUserIdAndPurposeAndConsumedAtIsNullAndExpiresAtAfterOrderByCreatedAtDesc(userId, purpose, now);
        } else {
            found = emailVerificationsRepository
                    .findFirstByUserIdAndPurposeAndTargetEmailAndConsumedAtIsNullAndExpiresAtAfterOrderByCreatedAtDesc(userId, purpose, targetEmail.trim(), now);
        }
        EmailVerificationsEntity e = found.orElseThrow(() -> new IllegalArgumentException("验证码不存在或已过期"));

        if (!code.trim().equals(e.getCode())) {
            throw new IllegalArgumentException("验证码错误");
        }

        e.setConsumedAt(now);
        emailVerificationsRepository.save(e);
    }
}
