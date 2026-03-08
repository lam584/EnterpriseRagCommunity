package com.example.EnterpriseRagCommunity.security;

import java.time.Instant;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.test.context.support.WithMockUser;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import org.springframework.test.web.servlet.MockMvc;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.EnterpriseRagCommunity.entity.access.TotpSecretsEntity;
import com.example.EnterpriseRagCommunity.entity.access.UsersEntity;
import com.example.EnterpriseRagCommunity.entity.access.enums.AccountStatus;
import com.example.EnterpriseRagCommunity.repository.access.TotpSecretsRepository;
import com.example.EnterpriseRagCommunity.repository.access.UsersRepository;
import com.example.EnterpriseRagCommunity.service.access.TotpCryptoService;
import com.example.EnterpriseRagCommunity.service.access.TotpService;
import com.example.EnterpriseRagCommunity.service.notify.EmailVerificationMailer;

import jakarta.annotation.Resource;
import jakarta.transaction.Transactional;

@SpringBootTest(properties = {
        "app.security.totp.master-key=AAAAAAAAAAAAAAAAAAAAAA=="
})
@AutoConfigureMockMvc
@Transactional
class TotpPasswordFlowsTest {

    @Resource
    private MockMvc mockMvc;

    @Resource
    private UsersRepository usersRepository;

    @Resource
    private TotpSecretsRepository totpSecretsRepository;

    @Resource
    private TotpCryptoService totpCryptoService;

    @Resource
    private TotpService totpService;

    @Resource
    private PasswordEncoder passwordEncoder;

    @MockitoBean
    private EmailVerificationMailer emailVerificationMailer;

    private UsersEntity createUser(String email, String rawPassword) {
        UsersEntity u = new UsersEntity();
        u.setEmail(email);
        u.setUsername("u");
        u.setPasswordHash(passwordEncoder.encode(rawPassword));
        u.setStatus(AccountStatus.ACTIVE);
        return usersRepository.save(u);
    }

    private TotpSecretsEntity createTotpSecret(long userId, byte[] secretBytes, boolean enabled, int digits) {
        TotpSecretsEntity e = new TotpSecretsEntity();
        e.setUserId(userId);
        e.setSecretEncrypted(totpCryptoService.encrypt(secretBytes));
        e.setAlgorithm("SHA1");
        e.setDigits(digits);
        e.setPeriodSeconds(30);
        e.setSkew(1);
        e.setEnabled(enabled);
        e.setCreatedAt(LocalDateTime.now());
        e.setVerifiedAt(enabled ? LocalDateTime.now() : null);
        return totpSecretsRepository.save(e);
    }

    @Test
    void passwordReset_status_shouldDisallow_whenTotpNotEnabled() throws Exception {
        createUser("zz_test_pr_a@example.invalid", "pass1234");

        mockMvc.perform(post("/api/auth/password-reset/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"zz_test_pr_a@example.invalid\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.allowed").value(false))
                .andExpect(jsonPath("$.totpEnabled").value(false));
    }

    @Test
    void passwordReset_reset_shouldReset_whenTotpEnabledAndCodeOk() throws Exception {
        UsersEntity user = createUser("zz_test_pr_b@example.invalid", "oldpass123");
        byte[] secret = new byte[]{1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18, 19, 20};
        TotpSecretsEntity totp = createTotpSecret(user.getId(), secret, true, 6);
        String code = totpService.generateCode(secret, Instant.now().getEpochSecond(), totp.getAlgorithm(), totp.getDigits(), totp.getPeriodSeconds());

        mockMvc.perform(post("/api/auth/password-reset/reset")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"zz_test_pr_b@example.invalid\",\"totpCode\":\"" + code + "\",\"newPassword\":\"newpass123\"}"))
                .andExpect(status().isOk());

        UsersEntity updated = usersRepository.findByEmailAndIsDeletedFalse("zz_test_pr_b@example.invalid").orElseThrow();
        assertThat(passwordEncoder.matches("newpass123", updated.getPasswordHash())).isTrue();
    }

    @Test
    @WithMockUser(username = "zz_test_cp_c@example.invalid")
    void changePassword_shouldRequireTotp_whenEnabled() throws Exception {
        UsersEntity user = createUser("zz_test_cp_c@example.invalid", "oldpass123");
        byte[] secret = new byte[]{9, 9, 9, 9, 9, 9, 9, 9, 9, 9, 9, 9, 9, 9, 9, 9, 9, 9, 9, 9};
        createTotpSecret(user.getId(), secret, true, 6);

        mockMvc.perform(post("/api/account/password")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"currentPassword\":\"oldpass123\",\"newPassword\":\"newpass123\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("请输入动态验证码"));
    }

    @Test
    @WithMockUser(username = "zz_test_cp_d@example.invalid")
    void changePassword_shouldSucceed_whenEnabledAndTotpOk() throws Exception {
        UsersEntity user = createUser("zz_test_cp_d@example.invalid", "oldpass123");
        byte[] secret = new byte[]{7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7};
        TotpSecretsEntity totp = createTotpSecret(user.getId(), secret, true, 6);
        String code = totpService.generateCode(secret, Instant.now().getEpochSecond(), totp.getAlgorithm(), totp.getDigits(), totp.getPeriodSeconds());

        mockMvc.perform(post("/api/account/password")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"currentPassword\":\"oldpass123\",\"newPassword\":\"newpass123\",\"totpCode\":\"" + code + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("密码修改成功"));
    }

    @Test
    @WithMockUser(username = "zz_test_ev_e@example.invalid")
    void enrollVerify_shouldRequirePassword() throws Exception {
        UsersEntity user = createUser("zz_test_ev_e@example.invalid", "oldpass123");
        byte[] secret = new byte[]{3, 1, 4, 1, 5, 9, 2, 6, 5, 3, 5, 8, 9, 7, 9, 3, 2, 3, 8, 4};
        TotpSecretsEntity pending = createTotpSecret(user.getId(), secret, false, 6);
        String code = totpService.generateCode(secret, Instant.now().getEpochSecond(), pending.getAlgorithm(), pending.getDigits(), pending.getPeriodSeconds());

        mockMvc.perform(post("/api/account/totp/verify")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"" + code + "\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser(username = "zz_test_ev_f@example.invalid")
    void enrollVerify_shouldEnable_whenPasswordAndCodeOk() throws Exception {
        UsersEntity user = createUser("zz_test_ev_f@example.invalid", "oldpass123");
        byte[] secret = new byte[]{8, 6, 7, 5, 3, 0, 9, 1, 2, 3, 4, 5, 6, 7, 8, 9, 0, 1, 2, 3};
        TotpSecretsEntity pending = createTotpSecret(user.getId(), secret, false, 6);
        String code = totpService.generateCode(secret, Instant.now().getEpochSecond(), pending.getAlgorithm(), pending.getDigits(), pending.getPeriodSeconds());

        mockMvc.perform(post("/api/account/totp/verify")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"" + code + "\",\"password\":\"oldpass123\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.enabled").value(true));
    }
}
