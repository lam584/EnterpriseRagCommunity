package com.example.EnterpriseRagCommunity.service;

import com.example.EnterpriseRagCommunity.dto.access.response.TotpStatusResponse;
import com.example.EnterpriseRagCommunity.config.TotpSecurityProperties;
import com.example.EnterpriseRagCommunity.entity.access.TotpSecretsEntity;
import com.example.EnterpriseRagCommunity.entity.access.UsersEntity;
import com.example.EnterpriseRagCommunity.repository.access.TotpSecretsRepository;
import com.example.EnterpriseRagCommunity.repository.access.UsersRepository;
import com.example.EnterpriseRagCommunity.service.access.TotpCryptoService;
import com.example.EnterpriseRagCommunity.service.access.TotpPolicyService;
import com.example.EnterpriseRagCommunity.service.access.TotpService;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.LocalDateTime;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AccountTotpServiceTest {

    @Test
    void getStatusByEmail_when_no_enabled_secret_should_include_masterKeyConfigured() {
        UsersRepository usersRepository = mock(UsersRepository.class);
        TotpSecretsRepository totpSecretsRepository = mock(TotpSecretsRepository.class);
        TotpService totpService = mock(TotpService.class);
        TotpCryptoService totpCryptoService = mock(TotpCryptoService.class);
        TotpPolicyService totpPolicyService = mock(TotpPolicyService.class);
        PasswordEncoder passwordEncoder = mock(PasswordEncoder.class);

        UsersEntity user = new UsersEntity();
        user.setId(1L);
        user.setEmail("u@example.com");
        user.setIsDeleted(false);
        when(usersRepository.findByEmailAndIsDeletedFalse("u@example.com")).thenReturn(Optional.of(user));
        when(totpSecretsRepository.findByUserIdAndEnabledTrue(1L)).thenReturn(List.of());
        when(totpCryptoService.isConfigured()).thenReturn(false);

        AccountTotpService svc = new AccountTotpService(
                usersRepository,
                totpSecretsRepository,
                totpService,
                totpCryptoService,
                totpPolicyService,
                passwordEncoder
        );

        TotpStatusResponse resp = svc.getStatusByEmail("u@example.com");
        assertThat(resp.getEnabled()).isFalse();
        assertThat(resp.getMasterKeyConfigured()).isFalse();
    }

    @Test
    void getStatusByEmail_when_enabled_secret_exists_should_include_masterKeyConfigured() {
        UsersRepository usersRepository = mock(UsersRepository.class);
        TotpSecretsRepository totpSecretsRepository = mock(TotpSecretsRepository.class);
        TotpService totpService = mock(TotpService.class);
        TotpCryptoService totpCryptoService = mock(TotpCryptoService.class);
        TotpPolicyService totpPolicyService = mock(TotpPolicyService.class);
        PasswordEncoder passwordEncoder = mock(PasswordEncoder.class);

        UsersEntity user = new UsersEntity();
        user.setId(1L);
        user.setEmail("u@example.com");
        user.setIsDeleted(false);
        when(usersRepository.findByEmailAndIsDeletedFalse("u@example.com")).thenReturn(Optional.of(user));
        when(totpCryptoService.isConfigured()).thenReturn(true);

        TotpSecretsEntity secret = new TotpSecretsEntity();
        secret.setEnabled(true);
        secret.setCreatedAt(LocalDateTime.now());
        when(totpSecretsRepository.findByUserIdAndEnabledTrue(1L)).thenReturn(List.of(secret));

        AccountTotpService svc = new AccountTotpService(
                usersRepository,
                totpSecretsRepository,
                totpService,
                totpCryptoService,
                totpPolicyService,
                passwordEncoder
        );

        TotpStatusResponse resp = svc.getStatusByEmail("u@example.com");
        assertThat(resp.getEnabled()).isTrue();
        assertThat(resp.getMasterKeyConfigured()).isTrue();
    }

    @Test
    void verifyByEmail_should_match_any_recent_pending_secret() {
        UsersRepository usersRepository = mock(UsersRepository.class);
        TotpSecretsRepository totpSecretsRepository = mock(TotpSecretsRepository.class);
        TotpService totpService = mock(TotpService.class);
        TotpCryptoService totpCryptoService = mock(TotpCryptoService.class);
        TotpPolicyService totpPolicyService = mock(TotpPolicyService.class);
        PasswordEncoder passwordEncoder = mock(PasswordEncoder.class);

        UsersEntity user = new UsersEntity();
        user.setId(1L);
        user.setEmail("u@example.com");
        user.setIsDeleted(false);
        user.setPasswordHash("hash");
        when(usersRepository.findByEmailAndIsDeletedFalse("u@example.com")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("p", "hash")).thenReturn(true);
        when(totpCryptoService.isConfigured()).thenReturn(true);

        byte[] enc1 = new byte[]{1};
        byte[] enc2 = new byte[]{2};
        byte[] secret1 = new byte[]{11};
        byte[] secret2 = new byte[]{22};

        TotpSecretsEntity older = new TotpSecretsEntity();
        older.setId(101L);
        older.setUserId(1L);
        older.setSecretEncrypted(enc1);
        older.setAlgorithm("SHA1");
        older.setDigits(6);
        older.setPeriodSeconds(30);
        older.setSkew(1);
        older.setEnabled(false);
        older.setCreatedAt(LocalDateTime.now().minusMinutes(5));

        TotpSecretsEntity latest = new TotpSecretsEntity();
        latest.setId(102L);
        latest.setUserId(1L);
        latest.setSecretEncrypted(enc2);
        latest.setAlgorithm("SHA1");
        latest.setDigits(6);
        latest.setPeriodSeconds(30);
        latest.setSkew(1);
        latest.setEnabled(false);
        latest.setCreatedAt(LocalDateTime.now());

        when(totpSecretsRepository.findTop5ByUserIdAndEnabledFalseOrderByCreatedAtDesc(1L)).thenReturn(List.of(latest, older));
        when(totpSecretsRepository.findByUserIdAndEnabledTrue(1L)).thenReturn(List.of());

        when(totpCryptoService.decrypt(enc1)).thenReturn(secret1);
        when(totpCryptoService.decrypt(enc2)).thenReturn(secret2);

        when(totpService.verifyCode(secret2, "123456", "SHA1", 6, 30, 1)).thenReturn(false);
        when(totpService.verifyCode(secret1, "123456", "SHA1", 6, 30, 1)).thenReturn(true);

        AccountTotpService svc = new AccountTotpService(
                usersRepository,
                totpSecretsRepository,
                totpService,
                totpCryptoService,
                totpPolicyService,
                passwordEncoder
        );

        TotpStatusResponse resp = svc.verifyByEmail("u@example.com", "p", "123456");
        assertThat(resp.getEnabled()).isTrue();
        assertThat(resp.getDigits()).isEqualTo(6);
        assertThat(resp.getVerifiedAt()).isNotNull();
        assertThat(older.getEnabled()).isTrue();
        assertThat(older.getVerifiedAt()).isNotNull();
        assertThat(latest.getEnabled()).isFalse();
    }

    @Test
    void verifyByEmail_should_reject_non_numeric_code() {
        UsersRepository usersRepository = mock(UsersRepository.class);
        TotpSecretsRepository totpSecretsRepository = mock(TotpSecretsRepository.class);
        TotpService totpService = mock(TotpService.class);
        TotpCryptoService totpCryptoService = mock(TotpCryptoService.class);
        TotpPolicyService totpPolicyService = mock(TotpPolicyService.class);
        PasswordEncoder passwordEncoder = mock(PasswordEncoder.class);

        UsersEntity user = new UsersEntity();
        user.setId(1L);
        user.setEmail("u@example.com");
        user.setIsDeleted(false);
        user.setPasswordHash("hash");
        when(usersRepository.findByEmailAndIsDeletedFalse("u@example.com")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("p", "hash")).thenReturn(true);
        when(totpCryptoService.isConfigured()).thenReturn(true);

        AccountTotpService svc = new AccountTotpService(
                usersRepository,
                totpSecretsRepository,
                totpService,
                totpCryptoService,
                totpPolicyService,
                passwordEncoder
        );

        assertThatThrownBy(() -> svc.verifyByEmail("u@example.com", "p", "12 456"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("验证码格式不正确");
    }

    @Test
    void verifyByEmail_when_code_mismatch_should_include_config_hint() {
        UsersRepository usersRepository = mock(UsersRepository.class);
        TotpSecretsRepository totpSecretsRepository = mock(TotpSecretsRepository.class);
        TotpService totpService = mock(TotpService.class);
        TotpCryptoService totpCryptoService = mock(TotpCryptoService.class);
        TotpPolicyService totpPolicyService = mock(TotpPolicyService.class);
        PasswordEncoder passwordEncoder = mock(PasswordEncoder.class);

        UsersEntity user = new UsersEntity();
        user.setId(1L);
        user.setEmail("u@example.com");
        user.setIsDeleted(false);
        user.setPasswordHash("hash");
        when(usersRepository.findByEmailAndIsDeletedFalse("u@example.com")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("p", "hash")).thenReturn(true);
        when(totpCryptoService.isConfigured()).thenReturn(true);

        byte[] enc = new byte[]{7};
        byte[] secret = new byte[]{77};

        TotpSecretsEntity latest = new TotpSecretsEntity();
        latest.setId(102L);
        latest.setUserId(1L);
        latest.setSecretEncrypted(enc);
        latest.setAlgorithm("SHA256");
        latest.setDigits(8);
        latest.setPeriodSeconds(60);
        latest.setSkew(0);
        latest.setEnabled(false);
        latest.setCreatedAt(LocalDateTime.now());

        when(totpSecretsRepository.findTop5ByUserIdAndEnabledFalseOrderByCreatedAtDesc(1L)).thenReturn(List.of(latest));
        when(totpCryptoService.decrypt(enc)).thenReturn(secret);
        when(totpService.verifyCode(secret, "12345678", "SHA256", 8, 60, 0)).thenReturn(false);

        AccountTotpService svc = new AccountTotpService(
                usersRepository,
                totpSecretsRepository,
                totpService,
                totpCryptoService,
                totpPolicyService,
                passwordEncoder
        );

        assertThatThrownBy(() -> svc.verifyByEmail("u@example.com", "p", "12345678"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("当前待启用密钥配置")
                .hasMessageContaining("SHA256")
                .hasMessageContaining("8 位")
                .hasMessageContaining("60 秒")
                .hasMessageContaining("skew=0");
    }

    @Test
    void verifyByEmail_when_code_matches_other_params_should_hint_detected_params() {
        UsersRepository usersRepository = mock(UsersRepository.class);
        TotpSecretsRepository totpSecretsRepository = mock(TotpSecretsRepository.class);
        TotpPolicyService totpPolicyService = mock(TotpPolicyService.class);
        PasswordEncoder passwordEncoder = mock(PasswordEncoder.class);

        TotpService totpService = new TotpService();
        TotpSecurityProperties props = new TotpSecurityProperties();
        props.setMasterKey(Base64.getEncoder().encodeToString(new byte[32]));
        TotpCryptoService totpCryptoService = new TotpCryptoService(props, null);

        UsersEntity user = new UsersEntity();
        user.setId(1L);
        user.setEmail("u@example.com");
        user.setIsDeleted(false);
        user.setPasswordHash("hash");
        when(usersRepository.findByEmailAndIsDeletedFalse("u@example.com")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("p", "hash")).thenReturn(true);

        byte[] secret = totpService.generateSecretBytes(20);
        byte[] encrypted = totpCryptoService.encrypt(secret);

        TotpSecretsEntity pending = new TotpSecretsEntity();
        pending.setId(201L);
        pending.setUserId(1L);
        pending.setSecretEncrypted(encrypted);
        pending.setAlgorithm("SHA512");
        pending.setDigits(8);
        pending.setPeriodSeconds(60);
        pending.setSkew(2);
        pending.setEnabled(false);
        pending.setCreatedAt(LocalDateTime.now());

        when(totpSecretsRepository.findTop5ByUserIdAndEnabledFalseOrderByCreatedAtDesc(1L)).thenReturn(List.of(pending));

        long now = Instant.now().getEpochSecond();
        String code = totpService.generateCode(secret, now, "SHA1", 6, 30);

        AccountTotpService svc = new AccountTotpService(
                usersRepository,
                totpSecretsRepository,
                totpService,
                totpCryptoService,
                totpPolicyService,
                passwordEncoder
        );

        assertThatThrownBy(() -> svc.verifyByEmail("u@example.com", "p", code))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("检测到你输入的验证码更符合")
                .hasMessageContaining("SHA1 / 6 位 / 30 秒");
    }

    @Test
    void verifyByEmail_should_work_with_real_totp_generation() {
        UsersRepository usersRepository = mock(UsersRepository.class);
        TotpSecretsRepository totpSecretsRepository = mock(TotpSecretsRepository.class);
        TotpPolicyService totpPolicyService = mock(TotpPolicyService.class);
        PasswordEncoder passwordEncoder = mock(PasswordEncoder.class);

        TotpService totpService = new TotpService();
        TotpSecurityProperties props = new TotpSecurityProperties();
        props.setMasterKey(Base64.getEncoder().encodeToString(new byte[32]));
        TotpCryptoService totpCryptoService = new TotpCryptoService(props, null);

        UsersEntity user = new UsersEntity();
        user.setId(1L);
        user.setEmail("u@example.com");
        user.setIsDeleted(false);
        user.setPasswordHash("hash");
        when(usersRepository.findByEmailAndIsDeletedFalse("u@example.com")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("p", "hash")).thenReturn(true);

        byte[] secret = totpService.generateSecretBytes(20);
        byte[] encrypted = totpCryptoService.encrypt(secret);

        TotpSecretsEntity pending = new TotpSecretsEntity();
        pending.setId(201L);
        pending.setUserId(1L);
        pending.setSecretEncrypted(encrypted);
        pending.setAlgorithm("SHA1");
        pending.setDigits(6);
        pending.setPeriodSeconds(30);
        pending.setSkew(1);
        pending.setEnabled(false);
        pending.setCreatedAt(LocalDateTime.now());

        when(totpSecretsRepository.findTop5ByUserIdAndEnabledFalseOrderByCreatedAtDesc(1L)).thenReturn(List.of(pending));
        when(totpSecretsRepository.findByUserIdAndEnabledTrue(1L)).thenReturn(List.of());

        long now = Instant.now().getEpochSecond();
        String code = totpService.generateCode(secret, now, "SHA1", 6, 30);

        AccountTotpService svc = new AccountTotpService(
                usersRepository,
                totpSecretsRepository,
                totpService,
                totpCryptoService,
                totpPolicyService,
                passwordEncoder
        );

        TotpStatusResponse resp = svc.verifyByEmail("u@example.com", "p", code);
        assertThat(resp.getEnabled()).isTrue();
        assertThat(resp.getDigits()).isEqualTo(6);
        assertThat(pending.getEnabled()).isTrue();
    }
}
