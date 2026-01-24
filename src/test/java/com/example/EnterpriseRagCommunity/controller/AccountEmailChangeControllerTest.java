package com.example.EnterpriseRagCommunity.controller;

import com.example.EnterpriseRagCommunity.entity.access.TotpSecretsEntity;
import com.example.EnterpriseRagCommunity.entity.access.UsersEntity;
import com.example.EnterpriseRagCommunity.entity.access.enums.AccountStatus;
import com.example.EnterpriseRagCommunity.entity.access.enums.EmailVerificationPurpose;
import com.example.EnterpriseRagCommunity.repository.access.TotpSecretsRepository;
import com.example.EnterpriseRagCommunity.repository.access.UsersRepository;
import com.example.EnterpriseRagCommunity.service.access.EmailVerificationService;
import com.example.EnterpriseRagCommunity.service.access.TotpCryptoService;
import com.example.EnterpriseRagCommunity.service.access.TotpService;
import com.example.EnterpriseRagCommunity.service.notify.EmailVerificationMailer;
import jakarta.annotation.Resource;
import jakarta.transaction.Transactional;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "app.security.totp.master-key=AAAAAAAAAAAAAAAAAAAAAA=="
})
@AutoConfigureMockMvc
@Transactional
class AccountEmailChangeControllerTest {

    @Resource
    private MockMvc mockMvc;

    @Resource
    private UsersRepository usersRepository;

    @Resource
    private EmailVerificationService emailVerificationService;

    @Resource
    private TotpSecretsRepository totpSecretsRepository;

    @Resource
    private TotpCryptoService totpCryptoService;

    @Resource
    private TotpService totpService;

    @Resource
    private PasswordEncoder passwordEncoder;

    @MockBean
    private EmailVerificationMailer emailVerificationMailer;

    private UsersEntity createUser(String email) {
        UsersEntity u = new UsersEntity();
        u.setEmail(email);
        u.setUsername("u");
        u.setPasswordHash(passwordEncoder.encode("pass1234"));
        u.setStatus(AccountStatus.ACTIVE);
        return usersRepository.save(u);
    }

    private TotpSecretsEntity createEnabledTotpSecret(long userId, byte[] secretBytes, int digits) {
        TotpSecretsEntity e = new TotpSecretsEntity();
        e.setUserId(userId);
        e.setSecretEncrypted(totpCryptoService.encrypt(secretBytes));
        e.setAlgorithm("SHA1");
        e.setDigits(digits);
        e.setPeriodSeconds(30);
        e.setSkew(1);
        e.setEnabled(true);
        e.setCreatedAt(LocalDateTime.now());
        e.setVerifiedAt(LocalDateTime.now());
        return totpSecretsRepository.save(e);
    }

    @Test
    @WithMockUser(username = "zz_test_ec_pw_req@example.invalid")
    void oldSendCode_shouldRequirePasswordVerification() throws Exception {
        createUser("zz_test_ec_pw_req@example.invalid");
        when(emailVerificationMailer.isEnabled()).thenReturn(true);

        mockMvc.perform(post("/api/account/email-change/old/send-code")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("请先验证密码"));
    }

    @Test
    @WithMockUser(username = "zz_test_ec_1@example.invalid")
    void changeEmail_shouldReject_whenNewEmailBoundToOtherAccount() throws Exception {
        UsersEntity user = createUser("zz_test_ec_1@example.invalid");
        createUser("zz_test_ec_taken@example.invalid");

        when(emailVerificationMailer.isEnabled()).thenReturn(true);

        MockHttpSession session = new MockHttpSession();
        mockMvc.perform(post("/api/account/email-change/verify-password")
                        .with(csrf())
                        .session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"password\":\"pass1234\"}"))
                .andExpect(status().isOk());

        String oldCode = emailVerificationService.issueCode(user.getId(), EmailVerificationPurpose.CHANGE_EMAIL_OLD, "zz_test_ec_1@example.invalid");
        mockMvc.perform(post("/api/account/email-change/old/verify")
                        .with(csrf())
                        .session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"method\":\"email\",\"emailCode\":\"" + oldCode + "\"}"))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/account/email-change")
                        .with(csrf())
                        .session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"newEmail\":\"zz_test_ec_taken@example.invalid\",\"newEmailCode\":\"123456\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("该邮箱已绑定其他账号"));
    }

    @Test
    @WithMockUser(username = "zz_test_ec_2@example.invalid")
    void sendNewCode_shouldRequireOldVerification() throws Exception {
        createUser("zz_test_ec_2@example.invalid");
        when(emailVerificationMailer.isEnabled()).thenReturn(true);

        MockHttpSession session = new MockHttpSession();
        mockMvc.perform(post("/api/account/email-change/verify-password")
                        .with(csrf())
                        .session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"password\":\"pass1234\"}"))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/account/email-change/send-code")
                        .with(csrf())
                        .session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"newEmail\":\"zz_test_ec_new2@example.invalid\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("请先验证旧邮箱或动态验证码"));
    }

    @Test
    @WithMockUser(username = "zz_test_ec_3@example.invalid")
    void changeEmail_shouldSucceed_whenOldEmailVerifiedAndNewEmailVerified() throws Exception {
        UsersEntity user = createUser("zz_test_ec_3@example.invalid");
        when(emailVerificationMailer.isEnabled()).thenReturn(true);

        MockHttpSession session = new MockHttpSession();
        mockMvc.perform(post("/api/account/email-change/verify-password")
                        .with(csrf())
                        .session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"password\":\"pass1234\"}"))
                .andExpect(status().isOk());

        String oldCode = emailVerificationService.issueCode(user.getId(), EmailVerificationPurpose.CHANGE_EMAIL_OLD, "zz_test_ec_3@example.invalid");
        mockMvc.perform(post("/api/account/email-change/old/verify")
                        .with(csrf())
                        .session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"method\":\"email\",\"emailCode\":\"" + oldCode + "\"}"))
                .andExpect(status().isOk());

        String newCode = emailVerificationService.issueCode(user.getId(), EmailVerificationPurpose.CHANGE_EMAIL, "zz_test_ec_new3@example.invalid");

        mockMvc.perform(post("/api/account/email-change")
                        .with(csrf())
                        .session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"newEmail\":\"zz_test_ec_new3@example.invalid\",\"newEmailCode\":\"" + newCode + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("邮箱更换成功，请重新登录"));

        assertThat(usersRepository.findByEmailAndIsDeletedFalse("zz_test_ec_new3@example.invalid")).isPresent();
        assertThat(usersRepository.findByEmailAndIsDeletedFalse("zz_test_ec_3@example.invalid")).isEmpty();
    }

    @Test
    @WithMockUser(username = "zz_test_ec_4@example.invalid")
    void changeEmail_shouldSucceed_whenOldVerifiedByTotp() throws Exception {
        UsersEntity user = createUser("zz_test_ec_4@example.invalid");
        when(emailVerificationMailer.isEnabled()).thenReturn(true);

        byte[] secret = new byte[]{7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7};
        TotpSecretsEntity totp = createEnabledTotpSecret(user.getId(), secret, 6);
        String code = totpService.generateCode(secret, Instant.now().getEpochSecond(), totp.getAlgorithm(), totp.getDigits(), totp.getPeriodSeconds());

        MockHttpSession session = new MockHttpSession();
        mockMvc.perform(post("/api/account/email-change/verify-password")
                        .with(csrf())
                        .session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"password\":\"pass1234\"}"))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/account/email-change/old/verify")
                        .with(csrf())
                        .session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"method\":\"totp\",\"totpCode\":\"" + code + "\"}"))
                .andExpect(status().isOk());

        String newCode = emailVerificationService.issueCode(user.getId(), EmailVerificationPurpose.CHANGE_EMAIL, "zz_test_ec_new4@example.invalid");

        mockMvc.perform(post("/api/account/email-change")
                        .with(csrf())
                        .session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"newEmail\":\"zz_test_ec_new4@example.invalid\",\"newEmailCode\":\"" + newCode + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("邮箱更换成功，请重新登录"));

        assertThat(usersRepository.findByEmailAndIsDeletedFalse("zz_test_ec_new4@example.invalid")).isPresent();
    }
}
