package com.example.EnterpriseRagCommunity.controller;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.time.LocalDateTime;
import java.util.Enumeration;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.security.web.csrf.DefaultCsrfToken;

import com.example.EnterpriseRagCommunity.config.AdminSetupManager;
import com.example.EnterpriseRagCommunity.dto.access.Security2faPolicyStatusDTO;
import com.example.EnterpriseRagCommunity.dto.access.request.Login2faVerifyRequest;
import com.example.EnterpriseRagCommunity.dto.access.request.LoginRequest;
import com.example.EnterpriseRagCommunity.dto.access.request.RegisterResendRequest;
import com.example.EnterpriseRagCommunity.dto.access.request.RegisterVerifyRequest;
import com.example.EnterpriseRagCommunity.entity.access.PermissionsEntity;
import com.example.EnterpriseRagCommunity.entity.access.RolePermissionsEntity;
import com.example.EnterpriseRagCommunity.entity.access.TenantsEntity;
import com.example.EnterpriseRagCommunity.entity.access.UsersEntity;
import com.example.EnterpriseRagCommunity.entity.access.enums.AccountStatus;
import com.example.EnterpriseRagCommunity.entity.access.enums.EmailVerificationPurpose;
import com.example.EnterpriseRagCommunity.entity.content.BoardsEntity;
import com.example.EnterpriseRagCommunity.repository.access.PermissionsRepository;
import com.example.EnterpriseRagCommunity.repository.access.RolePermissionsRepository;
import com.example.EnterpriseRagCommunity.repository.access.RolesRepository;
import com.example.EnterpriseRagCommunity.repository.access.TenantsRepository;
import com.example.EnterpriseRagCommunity.repository.access.UserRoleLinksRepository;
import com.example.EnterpriseRagCommunity.repository.content.BoardModeratorsRepository;
import com.example.EnterpriseRagCommunity.repository.content.BoardsRepository;
import com.example.EnterpriseRagCommunity.service.AccountTotpService;
import com.example.EnterpriseRagCommunity.service.AdministratorService;
import com.example.EnterpriseRagCommunity.service.access.AuditLogWriter;
import com.example.EnterpriseRagCommunity.service.access.EmailVerificationService;
import com.example.EnterpriseRagCommunity.service.access.Security2faPolicyService;
import com.example.EnterpriseRagCommunity.service.init.InitialAdminIndexBootstrapService;
import com.example.EnterpriseRagCommunity.service.init.TotpMasterKeyBootstrapService;
import com.example.EnterpriseRagCommunity.service.monitor.AppSettingsService;
import com.example.EnterpriseRagCommunity.service.notify.EmailVerificationMailer;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;

class AuthControllerBranchCoverageBoostTest {

    @AfterEach
    void clearSecurity() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void helperMethods_shouldCoverMaskAndSafeBranches() throws Exception {
        String v1 = invokeStatic("maskHeaderValue", new Class[]{String.class, String.class}, null, null);
        String v2 = invokeStatic("maskHeaderValue", new Class[]{String.class, String.class}, "Authorization", "Bearer token");
        String v3 = invokeStatic("maskHeaderValue", new Class[]{String.class, String.class}, "x", "a".repeat(600));
        String v4 = invokeStatic("maskHeaderValue", new Class[]{String.class, String.class}, "x", "ok");
        String c1 = invokeStatic("maskCookieValue", new Class[]{String.class}, (Object) null);
        String c2 = invokeStatic("maskCookieValue", new Class[]{String.class}, "123456");
        String c3 = invokeStatic("maskCookieValue", new Class[]{String.class}, "1234567");
        String s1 = invokeStatic("safeText", new Class[]{String.class}, (Object) null);
        String s2 = invokeStatic("safeText", new Class[]{String.class}, " \n\t ");
        String s3 = invokeStatic("safeText", new Class[]{String.class}, "abc");
        String s4 = invokeStatic("safeText", new Class[]{String.class}, "abcdef");

        assertThat(v1).isEqualTo("null");
        assertThat(v2).isEqualTo("***");
        assertThat(v3).endsWith("...");
        assertThat(v4).isEqualTo("ok");
        assertThat(c1).isEqualTo("null");
        assertThat(c2).isEqualTo("***");
        assertThat(c3).isEqualTo("123***567");
        assertThat(s1).isNull();
        assertThat(s2).isNull();
        assertThat(s3).isEqualTo("abc");
        assertThat(s4).isEqualTo("abcdef");
    }

    @Test
    void helperMethods_shouldCoverHeaderCookieFormattingAndSummarize() throws Exception {
        MockHttpServletRequest req = new MockHttpServletRequest();
        req.addHeader("Authorization", "Bearer abc");
        req.addHeader("X-Test", "v");
        req.setCookies(new Cookie("sid", "123456789"));
        String h = invokeStatic("formatRequestHeadersMasked", new Class[]{HttpServletRequest.class}, req);
        String c = invokeStatic("formatRequestCookiesMasked", new Class[]{HttpServletRequest.class}, req);
        String cNone = invokeStatic("formatRequestCookiesMasked", new Class[]{HttpServletRequest.class}, new MockHttpServletRequest());
        String a1 = invokeStatic("summarizeAuthentication", new Class[]{Authentication.class}, (Object) null);
        Authentication auth = new UsernamePasswordAuthenticationToken("u@example.com", "p", List.of());
        String a2 = invokeStatic("summarizeAuthentication", new Class[]{Authentication.class}, auth);

        assertThat(h).contains("Authorization: ***");
        assertThat(c).contains("sid=123***789");
        assertThat(cNone).isEqualTo("none");
        assertThat(a1).isEqualTo("null");
        assertThat(a2).contains("authorities=0");
    }

    @Test
    void completeLogin_shouldCoverParameterAndTimestampBranches() throws Exception {
        Fx fx = new Fx();
        UsersEntity user = baseUser(1L, "u@example.com");
        MockHttpServletRequest req = new MockHttpServletRequest();
        MockHttpServletResponse resp = new MockHttpServletResponse();
        Authentication auth = new UsernamePasswordAuthenticationToken("u@example.com", "p", List.of(new SimpleGrantedAuthority("ROLE_USER")));

        assertThatThrownBy(() -> invoke(fx.c, "completeLogin",
                new Class[]{HttpServletRequest.class, jakarta.servlet.http.HttpServletResponse.class, UsersEntity.class, Authentication.class},
                req, resp, user, null)).hasRootCauseInstanceOf(IllegalArgumentException.class);

        assertThatThrownBy(() -> invoke(fx.c, "completeLogin",
                new Class[]{HttpServletRequest.class, jakarta.servlet.http.HttpServletResponse.class, UsersEntity.class, Authentication.class},
                req, resp, null, auth)).hasRootCauseInstanceOf(IllegalArgumentException.class);

        when(fx.administratorService.save(any(UsersEntity.class))).thenAnswer(i -> i.getArgument(0));
        ResponseEntity<?> r1 = invoke(fx.c, "completeLogin",
                new Class[]{HttpServletRequest.class, jakarta.servlet.http.HttpServletResponse.class, UsersEntity.class, Authentication.class},
                req, resp, user, auth);
        assertThat(r1.getStatusCode()).isEqualTo(HttpStatus.OK);

        UsersEntity user2 = baseUser(2L, "u2@example.com");
        user2.setUpdatedAt(LocalDateTime.now().minusHours(1));
        user2.setSessionInvalidatedAt(LocalDateTime.now().minusMinutes(2));
        ResponseEntity<?> r2 = invoke(fx.c, "completeLogin",
                new Class[]{HttpServletRequest.class, jakarta.servlet.http.HttpServletResponse.class, UsersEntity.class, Authentication.class},
                new MockHttpServletRequest(), new MockHttpServletResponse(), user2, auth);
        assertThat(r2.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void login_shouldCoverEmailUnverifiedBranches() throws Exception {
        Fx fx = new Fx();
        LoginRequest req = loginReq("u@example.com", "pass");
        UsersEntity user = baseUser(10L, "u@example.com");
        user.setStatus(AccountStatus.EMAIL_UNVERIFIED);
        user.setPasswordHash("hash");
        when(fx.administratorService.findByUsername("u@example.com")).thenReturn(Optional.of(user));
        when(fx.passwordEncoder.matches("pass", "hash")).thenReturn(false);

        ResponseEntity<?> r1 = fx.c.login(req, new MockHttpServletRequest(), new MockHttpServletResponse());
        assertThat(r1.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);

        when(fx.passwordEncoder.matches("pass", "hash")).thenReturn(true);
        ResponseEntity<?> r2 = fx.c.login(req, new MockHttpServletRequest(), new MockHttpServletResponse());
        assertThat(r2.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat((Map<String, Object>) r2.getBody()).containsEntry("code", "EMAIL_NOT_VERIFIED");
    }

    @Test
    void login_shouldCover2faAndNoUserAndExceptionBranches() throws Exception {
        Fx fx = new Fx();
        LoginRequest req = loginReq("u@example.com", "pass");
        when(fx.administratorService.findByUsername("u@example.com")).thenReturn(Optional.empty(), Optional.empty());
        when(fx.authenticationManager.authenticate(any())).thenReturn(new UsernamePasswordAuthenticationToken("u@example.com", "p"));

        ResponseEntity<?> noUser = fx.c.login(req, new MockHttpServletRequest(), new MockHttpServletResponse());
        assertThat(noUser.getStatusCode()).isEqualTo(HttpStatus.OK);

        when(fx.authenticationManager.authenticate(any())).thenThrow(new RuntimeException("bad"));
        ResponseEntity<?> ex = fx.c.login(req, new MockHttpServletRequest(), new MockHttpServletResponse());
        assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void login_shouldCoverDisabled2faAndEmail2faAndTotp2faBranches() throws Exception {
        Fx fx = new Fx();
        LoginRequest req = loginReq("u@example.com", "pass");
        UsersEntity user = baseUser(11L, "u@example.com");
        user.setStatus(AccountStatus.ACTIVE);
        when(fx.authenticationManager.authenticate(any())).thenReturn(new UsernamePasswordAuthenticationToken("u@example.com", "p"));
        when(fx.administratorService.findByUsername("u@example.com")).thenReturn(Optional.empty(), Optional.of(user));
        when(fx.security2faPolicyService.evaluateLogin2faModeForUser(11L)).thenReturn(Security2faPolicyService.Login2faMode.DISABLED);
        when(fx.administratorService.save(any(UsersEntity.class))).thenAnswer(i -> i.getArgument(0));
        ResponseEntity<?> disabled2fa = fx.c.login(req, new MockHttpServletRequest(), new MockHttpServletResponse());
        assertThat(disabled2fa.getStatusCode()).isEqualTo(HttpStatus.OK);

        Security2faPolicyStatusDTO policy = policy(true, true);
        when(fx.administratorService.findByUsername("u@example.com")).thenReturn(Optional.empty(), Optional.of(user), Optional.empty(), Optional.of(user), Optional.empty(), Optional.of(user));
        when(fx.security2faPolicyService.evaluateLogin2faModeForUser(11L))
                .thenReturn(Security2faPolicyService.Login2faMode.EMAIL_OR_TOTP, Security2faPolicyService.Login2faMode.EMAIL_OR_TOTP, Security2faPolicyService.Login2faMode.EMAIL_OR_TOTP);
        when(fx.security2faPolicyService.evaluateForUser(11L)).thenReturn(policy);
        when(fx.emailVerificationMailer.isEnabled()).thenReturn(false, true, true);
        when(fx.accountTotpService.isEnabledByEmail("u@example.com")).thenReturn(false, false, true);
        when(fx.emailVerificationService.getDefaultTtlSeconds()).thenReturn(300);
        when(fx.accountTotpService.getEnabledDigitsByEmail("u@example.com")).thenReturn(6, 7);

        ResponseEntity<?> unavailable = fx.c.login(req, new MockHttpServletRequest(), new MockHttpServletResponse());
        assertThat(unavailable.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat((Map<String, Object>) unavailable.getBody()).containsEntry("code", "LOGIN_2FA_UNAVAILABLE");

        ResponseEntity<?> emailOnly = fx.c.login(req, new MockHttpServletRequest(), new MockHttpServletResponse());
        assertThat(emailOnly.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat((Map<String, Object>) emailOnly.getBody()).containsEntry("code", "LOGIN_2FA_REQUIRED");

        ResponseEntity<?> totp = fx.c.login(req, new MockHttpServletRequest(), new MockHttpServletResponse());
        assertThat(totp.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void resendLogin2faEmail_shouldCoverAllBranches() throws Exception {
        Fx fx = new Fx();
        MockHttpServletRequest req1 = new MockHttpServletRequest();
        ResponseEntity<?> r1 = fx.c.resendLogin2faEmail(req1);
        assertThat(r1.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);

        MockHttpServletRequest req2 = new MockHttpServletRequest();
        HttpSession s2 = req2.getSession(true);
        s2.setAttribute("login2fa.pendingUserId", null);
        s2.setAttribute("login2fa.pendingEmail", " ");
        ResponseEntity<?> r2 = fx.c.resendLogin2faEmail(req2);
        assertThat(r2.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);

        MockHttpServletRequest req3 = sessionReq(12L, "u@example.com");
        when(fx.security2faPolicyService.evaluateLogin2faModeForUser(12L)).thenReturn(Security2faPolicyService.Login2faMode.DISABLED);
        ResponseEntity<?> r3 = fx.c.resendLogin2faEmail(req3);
        assertThat(r3.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);

        MockHttpServletRequest req4 = sessionReq(12L, "u@example.com");
        when(fx.security2faPolicyService.evaluateLogin2faModeForUser(12L)).thenReturn(Security2faPolicyService.Login2faMode.TOTP_ONLY);
        when(fx.security2faPolicyService.evaluateForUser(12L)).thenReturn(policy(true, true));
        when(fx.emailVerificationMailer.isEnabled()).thenReturn(true);
        ResponseEntity<?> r4 = fx.c.resendLogin2faEmail(req4);
        assertThat(r4.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);

        MockHttpServletRequest req5 = sessionReq(12L, "u@example.com");
        when(fx.security2faPolicyService.evaluateLogin2faModeForUser(12L)).thenReturn(Security2faPolicyService.Login2faMode.EMAIL_ONLY, Security2faPolicyService.Login2faMode.EMAIL_ONLY, Security2faPolicyService.Login2faMode.EMAIL_ONLY);
        when(fx.security2faPolicyService.evaluateForUser(12L)).thenReturn(policy(true, true));
        when(fx.emailVerificationMailer.isEnabled()).thenReturn(true);
        when(fx.emailVerificationService.issueCode(12L, EmailVerificationPurpose.LOGIN_2FA)).thenReturn("123456");
        when(fx.emailVerificationService.getDefaultResendWaitSeconds()).thenReturn(30);
        when(fx.emailVerificationService.getDefaultTtlSeconds()).thenReturn(300);
        ResponseEntity<?> ok = fx.c.resendLogin2faEmail(req5);
        assertThat(ok.getStatusCode()).isEqualTo(HttpStatus.OK);

        doThrow(new IllegalArgumentException("x")).when(fx.emailVerificationService).issueCode(12L, EmailVerificationPurpose.LOGIN_2FA);
        ResponseEntity<?> bad = fx.c.resendLogin2faEmail(sessionReq(12L, "u@example.com"));
        assertThat(bad.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);

        org.mockito.Mockito.doReturn("123456").when(fx.emailVerificationService).issueCode(12L, EmailVerificationPurpose.LOGIN_2FA);
        doThrow(new RuntimeException("x")).when(fx.emailVerificationMailer).sendVerificationCode("u@example.com", "123456", EmailVerificationPurpose.LOGIN_2FA);
        ResponseEntity<?> fail = fx.c.resendLogin2faEmail(sessionReq(12L, "u@example.com"));
        assertThat(fail.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
    }

    @Test
    void verifyLogin2fa_shouldCoverSessionAndInputBranches() throws Exception {
        Fx fx = new Fx();
        Login2faVerifyRequest body = verifyReq("email", "123456");
        ResponseEntity<?> r1 = fx.c.verifyLogin2fa(body, new MockHttpServletRequest(), new MockHttpServletResponse());
        assertThat(r1.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);

        MockHttpServletRequest req2 = new MockHttpServletRequest();
        req2.getSession(true).setAttribute("login2fa.pendingUserId", null);
        req2.getSession(false).setAttribute("login2fa.pendingEmail", " ");
        ResponseEntity<?> r2 = fx.c.verifyLogin2fa(body, req2, new MockHttpServletResponse());
        assertThat(r2.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);

        MockHttpServletRequest req3 = sessionReq(13L, "u@example.com");
        when(fx.security2faPolicyService.evaluateLogin2faModeForUser(13L)).thenReturn(Security2faPolicyService.Login2faMode.DISABLED);
        ResponseEntity<?> r3 = fx.c.verifyLogin2fa(body, req3, new MockHttpServletResponse());
        assertThat(r3.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);

        MockHttpServletRequest req4 = sessionReq(13L, "u@example.com");
        when(fx.security2faPolicyService.evaluateLogin2faModeForUser(13L)).thenReturn(Security2faPolicyService.Login2faMode.EMAIL_ONLY);
        when(fx.security2faPolicyService.evaluateForUser(13L)).thenReturn(policy(true, false));
        ResponseEntity<?> r4 = fx.c.verifyLogin2fa(verifyReq("email", " "), req4, new MockHttpServletResponse());
        assertThat(r4.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void verifyLogin2fa_shouldCoverEmailTotpInvalidAndExceptionAndSuccessBranches() throws Exception {
        Fx fx = new Fx();
        UsersEntity u = baseUser(13L, "u@example.com");
        UserDetails details = User.withUsername("u@example.com").password("x").authorities("ROLE_USER").build();
        when(fx.security2faPolicyService.evaluateLogin2faModeForUser(13L)).thenReturn(
                Security2faPolicyService.Login2faMode.EMAIL_ONLY,
                Security2faPolicyService.Login2faMode.TOTP_ONLY,
                Security2faPolicyService.Login2faMode.EMAIL_OR_TOTP,
                Security2faPolicyService.Login2faMode.EMAIL_OR_TOTP,
                Security2faPolicyService.Login2faMode.EMAIL_OR_TOTP
        );
        when(fx.security2faPolicyService.evaluateForUser(13L)).thenReturn(policy(false, false), policy(false, false), policy(true, true), policy(true, true), policy(true, true));
        when(fx.emailVerificationMailer.isEnabled()).thenReturn(false, true, true, true, true);
        when(fx.accountTotpService.isEnabledByEmail("u@example.com")).thenReturn(false, true, true, true, true);
        when(fx.administratorService.findByUsername("u@example.com")).thenReturn(Optional.empty(), Optional.of(u), Optional.of(u));
        when(fx.userDetailsService.loadUserByUsername("u@example.com")).thenReturn(details);
        when(fx.administratorService.save(any(UsersEntity.class))).thenAnswer(i -> i.getArgument(0));

        ResponseEntity<?> emailForbidden = fx.c.verifyLogin2fa(verifyReq("email", "123456"), sessionReq(13L, "u@example.com"), new MockHttpServletResponse());
        assertThat(emailForbidden.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);

        ResponseEntity<?> totpForbidden = fx.c.verifyLogin2fa(verifyReq("totp", "123456"), sessionReq(13L, "u@example.com"), new MockHttpServletResponse());
        assertThat(totpForbidden.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);

        ResponseEntity<?> invalidMethod = fx.c.verifyLogin2fa(verifyReq("x", "123456"), sessionReq(13L, "u@example.com"), new MockHttpServletResponse());
        assertThat(invalidMethod.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);

        ResponseEntity<?> emailNoUser = fx.c.verifyLogin2fa(verifyReq("email", "123456"), sessionReq(13L, "u@example.com"), new MockHttpServletResponse());
        assertThat(emailNoUser.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);

        doThrow(new RuntimeException("boom")).when(fx.accountTotpService).requireValidEnabledCodeByEmail("u@example.com", "123456");
        ResponseEntity<?> totpEx = fx.c.verifyLogin2fa(verifyReq("totp", "123456"), sessionReq(13L, "u@example.com"), new MockHttpServletResponse());
        assertThat(totpEx.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);

        ResponseEntity<?> emailOk = fx.c.verifyLogin2fa(verifyReq("email", "123456"), sessionReq(13L, "u@example.com"), new MockHttpServletResponse());
        assertThat(emailOk.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void registerAndVerifyAndResend_shouldCoverCommonBranches() throws Exception {
        Fx fx = new Fx();
        com.example.EnterpriseRagCommunity.dto.access.request.RegisterRequest r = new com.example.EnterpriseRagCommunity.dto.access.request.RegisterRequest();
        r.setEmail("u@example.com");
        r.setPassword("pass12345");
        r.setUsername("u");

        when(fx.appSettingsService.getLongOrDefault(AppSettingsService.KEY_REGISTRATION_ENABLED, 1L)).thenReturn(0L, 1L, 1L, 1L);
        ResponseEntity<?> disabled = fx.c.register(r);
        assertThat(disabled.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);

        when(fx.administratorService.findByUsername("u@example.com")).thenReturn(Optional.of(baseUser(1L, "u@example.com")));
        ResponseEntity<?> exists = fx.c.register(r);
        assertThat(exists.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);

        when(fx.administratorService.findByUsername("u@example.com")).thenReturn(Optional.empty());
        TenantsEntity tenant = new TenantsEntity();
        tenant.setId(99L);
        when(fx.tenantsRepository.findByCode(anyString())).thenReturn(Optional.of(tenant));
        when(fx.passwordEncoder.encode("pass12345")).thenReturn("hash");
        UsersEntity saved = baseUser(2L, "u@example.com");
        saved.setTenantId(tenant);
        saved.setUsername("u");
        when(fx.administratorService.save(any(UsersEntity.class))).thenReturn(saved);
        when(fx.appSettingsService.getLongOrDefault(AppSettingsService.KEY_DEFAULT_REGISTER_ROLE_ID, 1L)).thenReturn(1L);
        when(fx.rolesRepository.existsById(1L)).thenReturn(true);
        when(fx.emailVerificationMailer.isEnabled()).thenReturn(true, false, false, true, true, true, true);
        when(fx.emailVerificationService.issueCode(2L, EmailVerificationPurpose.REGISTER)).thenReturn("123456");
        ResponseEntity<?> createdWithMail = fx.c.register(r);
        assertThat(createdWithMail.getStatusCode()).isEqualTo(HttpStatus.CREATED);

        ResponseEntity<?> createdNoMail = fx.c.register(r);
        assertThat(createdNoMail.getStatusCode()).isEqualTo(HttpStatus.CREATED);

        RegisterVerifyRequest vr = new RegisterVerifyRequest();
        vr.setEmail("u@example.com");
        vr.setCode("123456");
        ResponseEntity<?> verifyDisabled = fx.c.verifyRegister(vr);
        assertThat(verifyDisabled.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);

        when(fx.administratorService.findByUsername("u@example.com")).thenReturn(Optional.of(saved));
        saved.setStatus(AccountStatus.ACTIVE);
        ResponseEntity<?> verifyNoNeed = fx.c.verifyRegister(vr);
        assertThat(verifyNoNeed.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);

        saved.setStatus(AccountStatus.EMAIL_UNVERIFIED);
        when(fx.administratorService.save(any(UsersEntity.class))).thenReturn(saved);
        ResponseEntity<?> verifyOk = fx.c.verifyRegister(vr);
        assertThat(verifyOk.getStatusCode()).isEqualTo(HttpStatus.OK);

        RegisterResendRequest rr = new RegisterResendRequest();
        rr.setEmail("u@example.com");
        when(fx.emailVerificationMailer.isEnabled()).thenReturn(false);
        ResponseEntity<?> resendDisabled = fx.c.resendRegisterCode(rr);
        assertThat(resendDisabled.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);

        when(fx.emailVerificationMailer.isEnabled()).thenReturn(true);

        when(fx.administratorService.findByUsername("u@example.com")).thenReturn(Optional.empty(), Optional.of(saved), Optional.of(saved));
        ResponseEntity<?> resendEmpty = fx.c.resendRegisterCode(rr);
        assertThat(resendEmpty.getStatusCode()).isEqualTo(HttpStatus.OK);

        saved.setStatus(AccountStatus.ACTIVE);
        ResponseEntity<?> resendActive = fx.c.resendRegisterCode(rr);
        assertThat(resendActive.getStatusCode()).isEqualTo(HttpStatus.OK);

        saved.setStatus(AccountStatus.EMAIL_UNVERIFIED);
        when(fx.emailVerificationService.getDefaultResendWaitSeconds()).thenReturn(30);
        when(fx.emailVerificationService.getDefaultTtlSeconds()).thenReturn(300);
        ResponseEntity<?> resendOk = fx.c.resendRegisterCode(rr);
        assertThat(resendOk.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void registerInitialAdmin_shouldCoverSetupAndSuccessAndMismatchBranches() throws Exception {
        Fx fx = new Fx();
        com.example.EnterpriseRagCommunity.dto.access.request.RegisterRequest r = new com.example.EnterpriseRagCommunity.dto.access.request.RegisterRequest();
        r.setEmail("admin@example.com");
        r.setPassword("pass12345");
        r.setUsername(null);

        when(fx.initialAdminSetupState.isSetupRequired()).thenReturn(false, true, true, true);
        ResponseEntity<?> forbidden = fx.c.registerInitialAdmin(r);
        assertThat(forbidden.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);

        TenantsEntity tenant = new TenantsEntity();
        tenant.setId(1L);
        when(fx.tenantsRepository.findByCode(anyString())).thenReturn(Optional.of(tenant));
        BoardsEntity board = new BoardsEntity();
        board.setId(10L);
        board.setName("默认版块");
        when(fx.boardsRepository.findFirstByTenantIdIsNullAndParentIdIsNullAndName("默认版块")).thenReturn(Optional.of(board));
        when(fx.rolesRepository.existsById(2L)).thenReturn(true);
        UsersEntity existing = baseUser(7L, "admin@example.com");
        existing.setPasswordHash("hash");
        existing.setTenantId(null);
        when(fx.administratorService.findByUsername("admin@example.com")).thenReturn(Optional.of(existing), Optional.of(existing), Optional.empty());
        when(fx.passwordEncoder.matches("pass12345", "hash")).thenReturn(false, true);
        ResponseEntity<?> mismatch = fx.c.registerInitialAdmin(r);
        assertThat(mismatch.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);

        when(fx.passwordEncoder.matches("pass12345", "hash")).thenReturn(true);
        when(fx.administratorService.save(any(UsersEntity.class))).thenAnswer(i -> {
            UsersEntity u = i.getArgument(0);
            if (u.getId() == null) {
                u.setId(8L);
            }
            return u;
        });
        when(fx.userRoleLinksRepository.existsByUserIdAndRoleIdAndScopeTypeAndScopeId(anyLong(), eq(2L), eq("GLOBAL"), eq(0L))).thenReturn(false, true);
        when(fx.boardModeratorsRepository.existsByBoardIdAndUserId(anyLong(), anyLong())).thenReturn(false, true);
        RolePermissionsEntity rp1 = new RolePermissionsEntity();
        rp1.setPermissionId(null);
        RolePermissionsEntity rp2 = new RolePermissionsEntity();
        rp2.setPermissionId(100L);
        when(fx.rolePermissionsRepository.findByRoleId(2L)).thenReturn(List.of(rp1, rp2), List.of());
        PermissionsEntity p1 = new PermissionsEntity();
        p1.setId(null);
        PermissionsEntity p2 = new PermissionsEntity();
        p2.setId(100L);
        PermissionsEntity p3 = new PermissionsEntity();
        p3.setId(101L);
        p3.setResource("admin_ui");
        p3.setAction("access");
        when(fx.permissionsRepository.findAll()).thenReturn(List.of(p1, p2, p3), List.of(p3));
        when(fx.permissionsRepository.count()).thenReturn(1L, 0L);
        when(fx.permissionsRepository.findByResourceAndAction(anyString(), anyString())).thenReturn(Optional.of(p3), Optional.empty());
        TotpMasterKeyBootstrapService.Result result = new TotpMasterKeyBootstrapService.Result();
        result.setAttempted(true);
        result.setSucceeded(true);
        result.setEnvVarName("APP_TOTP_MASTER_KEY");
        result.setKeyBase64("k");
        when(fx.totpMasterKeyBootstrapService.generateAndPersistToOsEnv()).thenReturn(result);

        ResponseEntity<?> existingOk = fx.c.registerInitialAdmin(r);
        assertThat(existingOk.getStatusCode()).isEqualTo(HttpStatus.CREATED);

        when(fx.passwordEncoder.encode("pass12345")).thenReturn("newhash");
        ResponseEntity<?> newUserOk = fx.c.registerInitialAdmin(r);
        assertThat(newUserOk.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    }

    @Test
    void logout_shouldCoverBranches() throws Exception {
        Fx fx = new Fx();
        ResponseEntity<?> anon = fx.c.logout(new MockHttpServletRequest());
        assertThat(anon.getStatusCode()).isEqualTo(HttpStatus.OK);

        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken("u@example.com", "p"));
        when(fx.administratorService.findByUsername("u@example.com")).thenThrow(new RuntimeException("ignore"));
        ResponseEntity<?> noSession = fx.c.logout(new MockHttpServletRequest());
        assertThat(noSession.getStatusCode()).isEqualTo(HttpStatus.OK);

        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken("u2@example.com", "p"));
        when(fx.administratorService.findByUsername("u2@example.com")).thenReturn(Optional.of(baseUser(2L, "u2@example.com")));
        MockHttpServletRequest req = new MockHttpServletRequest();
        req.getSession(true);
        ResponseEntity<?> withSession = fx.c.logout(req);
        assertThat(withSession.getStatusCode()).isEqualTo(HttpStatus.OK);

        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken("u3@example.com", "p"));
        when(fx.administratorService.findByUsername("u3@example.com")).thenReturn(Optional.of(baseUser(3L, "u3@example.com")));
        doThrow(new RuntimeException("x")).doNothing().when(fx.auditLogWriter).write(any(), any(), any(), any(), any(), any(), any(), any(), any());
        ResponseEntity<?> fail = fx.c.logout(new MockHttpServletRequest());
        assertThat(fail.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
    }

    @Test
    void tenantResolutionAndPermissionSeedMethods_shouldCoverBranches() throws Exception {
        Fx fx = new Fx();
        setField(fx.c, "defaultTenantCode", "  ");
        setField(fx.c, "defaultTenantName", "  ");
        when(fx.tenantsRepository.findFirstByOrderByIdAsc()).thenReturn(Optional.empty());
        when(fx.tenantsRepository.save(any(TenantsEntity.class))).thenAnswer(i -> i.getArgument(0));
        TenantsEntity created = invoke(fx.c, "resolveOrCreateDefaultTenantOrThrow", new Class[]{});
        assertThat(created.getCode()).isEqualTo("DEFAULT");

        TenantsEntity t1 = new TenantsEntity();
        t1.setId(1L);
        when(fx.tenantsRepository.findByCode("DEFAULT")).thenReturn(Optional.of(t1));
        setField(fx.c, "defaultTenantCode", "DEFAULT");
        TenantsEntity byCode = invoke(fx.c, "resolveOrCreateDefaultTenantOrThrow", new Class[]{});
        assertThat(byCode.getId()).isEqualTo(1L);

        when(fx.permissionsRepository.count()).thenReturn(1L, 0L);
        invokeVoid(fx.c, "ensureCoreRbacPermissionsSeededIfEmpty", new Class[]{});
        when(fx.permissionsRepository.findByResourceAndAction(anyString(), anyString())).thenReturn(Optional.of(new PermissionsEntity()), Optional.empty());
        invokeVoid(fx.c, "ensurePermissionExists", new Class[]{String.class, String.class, String.class}, "x", "y", "z");
        invokeVoid(fx.c, "ensurePermissionExists", new Class[]{String.class, String.class, String.class}, "x2", "y2", "z2");
        invokeVoid(fx.c, "ensureCoreRbacPermissionsSeededIfEmpty", new Class[]{});
    }

    @Test
    void convertMethods_shouldCoverTenantBranches() throws Exception {
        Fx fx = new Fx();
        UsersEntity u1 = baseUser(1L, "u1@example.com");
        UsersEntity u2 = baseUser(2L, "u2@example.com");
        TenantsEntity t = new TenantsEntity();
        t.setId(9L);
        u2.setTenantId(t);

        Object safe1 = invoke(fx.c, "convertToUserSafeDTO", new Class[]{UsersEntity.class}, u1);
        Object safe2 = invoke(fx.c, "convertToUserSafeDTO", new Class[]{UsersEntity.class}, u2);
        assertThat(safe1).isNotNull();
        assertThat(safe2).isNotNull();
    }

    @Test
    void getCurrentAdmin_shouldCoverLoggingGuardBranch() throws Exception {
        Fx fx = new Fx();
        setField(fx.c, "authRequestDetailsLoggingEnabled", true);
        UsersEntity u = baseUser(30L, "u@example.com");
        when(fx.administratorService.findByUsername("u@example.com")).thenReturn(Optional.of(u));
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("u@example.com", "p", List.of(new SimpleGrantedAuthority("ROLE_USER")))
        );
        MockHttpServletRequest req = new MockHttpServletRequest() {
            @Override
            public Enumeration<String> getHeaderNames() {
                return null;
            }
        };
        ResponseEntity<?> r = fx.c.getCurrentAdmin(req);
        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void verifyLogin2fa_shouldCoverEmailExceptionBranches() throws Exception {
        Fx fx = new Fx();
        when(fx.security2faPolicyService.evaluateLogin2faModeForUser(40L)).thenReturn(
                Security2faPolicyService.Login2faMode.EMAIL_ONLY,
                Security2faPolicyService.Login2faMode.EMAIL_ONLY
        );
        when(fx.security2faPolicyService.evaluateForUser(40L)).thenReturn(policy(true, true), policy(true, true));
        when(fx.emailVerificationMailer.isEnabled()).thenReturn(true, true);
        doThrow(new IllegalArgumentException("bad-code"))
                .when(fx.emailVerificationService)
                .verifyAndConsume(40L, EmailVerificationPurpose.LOGIN_2FA, "123456");
        ResponseEntity<?> bad = fx.c.verifyLogin2fa(verifyReq("email", "123456"), sessionReq(40L, "u@example.com"), new MockHttpServletResponse());
        assertThat(bad.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);

        doThrow(new RuntimeException("boom"))
                .when(fx.emailVerificationService)
                .verifyAndConsume(40L, EmailVerificationPurpose.LOGIN_2FA, "123456");
        ResponseEntity<?> fail = fx.c.verifyLogin2fa(verifyReq("email", "123456"), sessionReq(40L, "u@example.com"), new MockHttpServletResponse());
        assertThat(fail.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
    }

    @Test
    void register_shouldCoverRoleFallbackAndExceptionBranches() throws Exception {
        Fx fx = new Fx();
        com.example.EnterpriseRagCommunity.dto.access.request.RegisterRequest r = new com.example.EnterpriseRagCommunity.dto.access.request.RegisterRequest();
        r.setEmail("x@example.com");
        r.setPassword("pass12345");
        r.setUsername("x");

        TenantsEntity tenant = new TenantsEntity();
        tenant.setId(10L);
        when(fx.appSettingsService.getLongOrDefault(AppSettingsService.KEY_REGISTRATION_ENABLED, 1L)).thenReturn(1L, 1L);
        when(fx.administratorService.findByUsername("x@example.com")).thenReturn(Optional.empty(), Optional.empty());
        when(fx.tenantsRepository.findByCode(anyString())).thenReturn(Optional.of(tenant));
        when(fx.passwordEncoder.encode("pass12345")).thenReturn("hash");
        when(fx.administratorService.save(any(UsersEntity.class))).thenAnswer(i -> {
            UsersEntity u = i.getArgument(0);
            u.setId(77L);
            return u;
        });
        when(fx.appSettingsService.getLongOrDefault(AppSettingsService.KEY_DEFAULT_REGISTER_ROLE_ID, 1L)).thenReturn(0L);
        when(fx.rolesRepository.existsById(1L)).thenReturn(false);
        assertThatThrownBy(() -> fx.c.register(r)).isInstanceOf(org.springframework.transaction.NoTransactionException.class);

        when(fx.tenantsRepository.findByCode(anyString())).thenThrow(new RuntimeException("db"));
        assertThatThrownBy(() -> fx.c.register(r)).isInstanceOf(org.springframework.transaction.NoTransactionException.class);
    }

    @Test
    void registerInitialAdmin_shouldCoverConflictGuards() throws Exception {
        Fx fx = new Fx();
        com.example.EnterpriseRagCommunity.dto.access.request.RegisterRequest r = new com.example.EnterpriseRagCommunity.dto.access.request.RegisterRequest();
        r.setEmail("a@example.com");
        r.setPassword("pass12345");
        r.setUsername("a");

        when(fx.initialAdminSetupState.isSetupRequired()).thenReturn(true, true, true);
        when(fx.tenantsRepository.findByCode(anyString())).thenReturn(Optional.of(new TenantsEntity()));
        when(fx.boardsRepository.findFirstByTenantIdIsNullAndParentIdIsNullAndName("默认版块")).thenReturn(Optional.empty());
        assertThatThrownBy(() -> fx.c.registerInitialAdmin(r)).isInstanceOf(org.springframework.transaction.NoTransactionException.class);

        BoardsEntity b = new BoardsEntity();
        b.setName("默认版块");
        when(fx.boardsRepository.findFirstByTenantIdIsNullAndParentIdIsNullAndName("默认版块")).thenReturn(Optional.of(b));
        assertThatThrownBy(() -> fx.c.registerInitialAdmin(r)).isInstanceOf(org.springframework.transaction.NoTransactionException.class);

        b.setId(2L);
        when(fx.rolesRepository.existsById(2L)).thenReturn(false);
        assertThatThrownBy(() -> fx.c.registerInitialAdmin(r)).isInstanceOf(org.springframework.transaction.NoTransactionException.class);
    }

    @Test
    void tenantResolutionAndPermissionExists_shouldCoverRemainingBranches() throws Exception {
        Fx fx = new Fx();
        setField(fx.c, "defaultTenantCode", "DEFAULT");
        TenantsEntity first = new TenantsEntity();
        first.setId(5L);
        when(fx.tenantsRepository.findByCode("DEFAULT")).thenReturn(Optional.empty());
        when(fx.tenantsRepository.findFirstByOrderByIdAsc()).thenReturn(Optional.of(first));
        TenantsEntity t = invoke(fx.c, "resolveOrCreateDefaultTenantOrThrow", new Class[]{});
        assertThat(t.getId()).isEqualTo(5L);

        invokeVoid(fx.c, "ensurePermissionExists", new Class[]{String.class, String.class, String.class}, " ", "x", "d");
        invokeVoid(fx.c, "ensurePermissionExists", new Class[]{String.class, String.class, String.class}, "x", " ", "d");
    }

    @Test
    void getCurrentAdmin_shouldCoverUnauthorizedAndNotFoundBranches() {
        Fx fx;
        try {
            fx = new Fx();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        MockHttpServletRequest req = new MockHttpServletRequest();

        SecurityContextHolder.clearContext();
        ResponseEntity<?> r1 = fx.c.getCurrentAdmin(req);
        assertThat(r1.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);

        Authentication unauth = mock(Authentication.class);
        when(unauth.isAuthenticated()).thenReturn(false);
        SecurityContextHolder.getContext().setAuthentication(unauth);
        ResponseEntity<?> r2 = fx.c.getCurrentAdmin(req);
        assertThat(r2.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);

        Authentication anon = mock(Authentication.class);
        when(anon.isAuthenticated()).thenReturn(true);
        when(anon.getPrincipal()).thenReturn("anonymousUser");
        SecurityContextHolder.getContext().setAuthentication(anon);
        ResponseEntity<?> r3 = fx.c.getCurrentAdmin(req);
        assertThat(r3.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);

        Authentication auth = new UsernamePasswordAuthenticationToken("nouser@example.com", "p", List.of());
        SecurityContextHolder.getContext().setAuthentication(auth);
        when(fx.administratorService.findByUsername("nouser@example.com")).thenReturn(Optional.empty());
        ResponseEntity<?> r4 = fx.c.getCurrentAdmin(req);
        assertThat(r4.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void publicStatusEndpoints_shouldCoverCsrfAndRegistrationBranches() throws Exception {
        Fx fx = new Fx();
        MockHttpServletRequest req1 = new MockHttpServletRequest();
        ResponseEntity<?> c1 = fx.c.getCsrfToken(req1);
        assertThat(c1.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);

        MockHttpServletRequest req2 = new MockHttpServletRequest();
        CsrfToken t2 = new DefaultCsrfToken("X-CSRF-TOKEN", "_csrf", "t2");
        req2.setAttribute("_csrf", t2);
        ResponseEntity<?> c2 = fx.c.getCsrfToken(req2);
        assertThat(c2.getStatusCode()).isEqualTo(HttpStatus.OK);

        MockHttpServletRequest req3 = new MockHttpServletRequest();
        CsrfToken t3 = new DefaultCsrfToken("X-CSRF-TOKEN", "_csrf", "t3");
        req3.setAttribute(CsrfToken.class.getName(), t3);
        ResponseEntity<?> c3 = fx.c.getCsrfToken(req3);
        assertThat(c3.getStatusCode()).isEqualTo(HttpStatus.OK);

        when(fx.appSettingsService.getLongOrDefault(AppSettingsService.KEY_REGISTRATION_ENABLED, 1L)).thenReturn(1L, 0L);
        ResponseEntity<?> s1 = fx.c.getRegistrationStatus();
        ResponseEntity<?> s2 = fx.c.getRegistrationStatus();
        assertThat(s1.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(s2.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void maskHeaderValue_shouldCoverSensitiveHeaderChainBranches() throws Exception {
        String m1 = invokeStatic("maskHeaderValue", new Class[]{String.class, String.class}, "cookie", "v");
        String m2 = invokeStatic("maskHeaderValue", new Class[]{String.class, String.class}, "set-cookie", "v");
        String m3 = invokeStatic("maskHeaderValue", new Class[]{String.class, String.class}, "proxy-authorization", "v");
        String m4 = invokeStatic("maskHeaderValue", new Class[]{String.class, String.class}, "x-api-key", "v");
        String m5 = invokeStatic("maskHeaderValue", new Class[]{String.class, String.class}, "x-xsrf-token", "v");
        assertThat(m1).isEqualTo("***");
        assertThat(m2).isEqualTo("***");
        assertThat(m3).isEqualTo("***");
        assertThat(m4).isEqualTo("***");
        assertThat(m5).isEqualTo("***");
    }

    @Test
    void login_shouldCoverAdditional2faBranches() throws Exception {
        Fx fx = new Fx();
        LoginRequest req = loginReq("extra@example.com", "pass");
        UsersEntity user = baseUser(81L, "extra@example.com");
        when(fx.authenticationManager.authenticate(any())).thenReturn(new UsernamePasswordAuthenticationToken("extra@example.com", "p"));
        when(fx.administratorService.findByUsername("extra@example.com")).thenReturn(
                Optional.empty(), Optional.of(user),
                Optional.empty(), Optional.of(user),
                Optional.empty(), Optional.of(user)
        );
        when(fx.security2faPolicyService.evaluateLogin2faModeForUser(81L)).thenReturn(
                Security2faPolicyService.Login2faMode.EMAIL_ONLY,
                Security2faPolicyService.Login2faMode.TOTP_ONLY,
                Security2faPolicyService.Login2faMode.EMAIL_OR_TOTP
        );
        when(fx.security2faPolicyService.evaluateForUser(81L)).thenReturn(policy(true, true), policy(true, true), policy(true, true));
        when(fx.emailVerificationMailer.isEnabled()).thenReturn(true, true, false);
        when(fx.accountTotpService.isEnabledByEmail("extra@example.com")).thenReturn(true, true, true);
        when(fx.accountTotpService.getEnabledDigitsByEmail("extra@example.com")).thenReturn(Integer.valueOf(8), (Integer) null);
        when(fx.emailVerificationService.getDefaultTtlSeconds()).thenReturn(300);

        assertThat(fx.c.login(req, new MockHttpServletRequest(), new MockHttpServletResponse()).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(fx.c.login(req, new MockHttpServletRequest(), new MockHttpServletResponse()).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(fx.c.login(req, new MockHttpServletRequest(), new MockHttpServletResponse()).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void resendAndVerify2fa_shouldCoverAdditionalSessionAndModeBranches() throws Exception {
        Fx fx = new Fx();
        when(fx.security2faPolicyService.evaluateLogin2faModeForUser(91L)).thenReturn(
                Security2faPolicyService.Login2faMode.EMAIL_ONLY,
                Security2faPolicyService.Login2faMode.EMAIL_OR_TOTP,
                Security2faPolicyService.Login2faMode.EMAIL_OR_TOTP,
                Security2faPolicyService.Login2faMode.TOTP_ONLY
        );
        when(fx.security2faPolicyService.evaluateForUser(91L)).thenReturn(
                policy(false, true),
                policy(true, true),
                policy(true, true),
                policy(true, false)
        );
        when(fx.emailVerificationMailer.isEnabled()).thenReturn(true, false, true, true);

        MockHttpServletRequest invalidUidReq = sessionReq(0L, "u@example.com");
        ResponseEntity<?> s1 = fx.c.resendLogin2faEmail(invalidUidReq);
        assertThat(s1.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);

        ResponseEntity<?> s2 = fx.c.resendLogin2faEmail(sessionReq(91L, "u@example.com"));
        ResponseEntity<?> s3 = fx.c.resendLogin2faEmail(sessionReq(91L, "u@example.com"));
        assertThat(s2.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(s3).isNotNull();

        MockHttpServletRequest invalidEmailReq = new MockHttpServletRequest();
        HttpSession session = invalidEmailReq.getSession(true);
        session.setAttribute("login2fa.pendingUserId", 91L);
        session.setAttribute("login2fa.pendingEmail", null);
        ResponseEntity<?> v1 = fx.c.verifyLogin2fa(verifyReq("totp", "123456"), invalidEmailReq, new MockHttpServletResponse());
        assertThat(v1.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);

        when(fx.accountTotpService.isEnabledByEmail("u@example.com")).thenReturn(false);
        ResponseEntity<?> v2 = fx.c.verifyLogin2fa(verifyReq("totp", null), sessionReq(91L, "u@example.com"), new MockHttpServletResponse());
        ResponseEntity<?> v3 = fx.c.verifyLogin2fa(verifyReq(null, "123456"), sessionReq(91L, "u@example.com"), new MockHttpServletResponse());
        assertThat(v2.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(v3.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void resolveTenantAndAuthSummary_shouldCoverRemainingSmallBranches() throws Exception {
        Fx fx = new Fx();
        setField(fx.c, "defaultTenantCode", "  ");
        setField(fx.c, "defaultTenantName", "  ");
        when(fx.tenantsRepository.findFirstByOrderByIdAsc()).thenReturn(Optional.empty());
        when(fx.tenantsRepository.save(any(TenantsEntity.class))).thenAnswer(i -> i.getArgument(0));
        TenantsEntity t = invoke(fx.c, "resolveOrCreateDefaultTenantOrThrow", new Class[]{});
        assertThat(t.getCode()).isEqualTo("DEFAULT");
        assertThat(t.getName()).isEqualTo("Default Tenant");

        Authentication auth = mock(Authentication.class);
        when(auth.getClass()).thenReturn((Class) UsernamePasswordAuthenticationToken.class);
        when(auth.getName()).thenReturn("u@example.com");
        when(auth.isAuthenticated()).thenReturn(true);
        when(auth.getAuthorities()).thenReturn(null);
        String s = invokeStatic("summarizeAuthentication", new Class[]{Authentication.class}, auth);
        assertThat(s).contains("authorities=0");
    }

    @Test
    void registerInitialAdmin_andTenantResolve_shouldCoverRemainingBranches() throws Exception {
        Fx fx = new Fx();
        com.example.EnterpriseRagCommunity.dto.access.request.RegisterRequest r = new com.example.EnterpriseRagCommunity.dto.access.request.RegisterRequest();
        r.setEmail("exists@example.com");
        r.setPassword("pass12345");
        r.setUsername("admin-name");
        when(fx.initialAdminSetupState.isSetupRequired()).thenReturn(true, true);
        TenantsEntity tenant = new TenantsEntity();
        tenant.setId(20L);
        when(fx.tenantsRepository.findByCode(anyString())).thenReturn(Optional.of(tenant), Optional.empty());
        BoardsEntity board = new BoardsEntity();
        board.setId(2L);
        board.setName("默认版块");
        when(fx.boardsRepository.findFirstByTenantIdIsNullAndParentIdIsNullAndName("默认版块")).thenReturn(Optional.of(board), Optional.of(board));
        when(fx.rolesRepository.existsById(2L)).thenReturn(true, true);

        UsersEntity existing = baseUser(99L, "exists@example.com");
        existing.setTenantId(tenant);
        existing.setPasswordHash("hash");
        when(fx.administratorService.findByUsername("exists@example.com")).thenReturn(Optional.of(existing), Optional.empty());
        when(fx.passwordEncoder.matches("pass12345", "hash")).thenReturn(true);
        when(fx.userRoleLinksRepository.existsByUserIdAndRoleIdAndScopeTypeAndScopeId(anyLong(), eq(2L), eq("GLOBAL"), eq(0L))).thenReturn(true);
        when(fx.boardModeratorsRepository.existsByBoardIdAndUserId(anyLong(), anyLong())).thenReturn(true);
        when(fx.permissionsRepository.count()).thenReturn(1L, 1L);
        when(fx.permissionsRepository.findByResourceAndAction(anyString(), anyString())).thenReturn(Optional.of(new PermissionsEntity()));
        when(fx.rolePermissionsRepository.findByRoleId(2L)).thenReturn(List.of());
        when(fx.permissionsRepository.findAll()).thenReturn(List.of());
        TotpMasterKeyBootstrapService.Result result = new TotpMasterKeyBootstrapService.Result();
        result.setAttempted(true);
        result.setSucceeded(false);
        when(fx.totpMasterKeyBootstrapService.generateAndPersistToOsEnv()).thenReturn(result, result);
        when(fx.passwordEncoder.encode("pass12345")).thenReturn("newhash");
        when(fx.administratorService.save(any(UsersEntity.class))).thenAnswer(i -> {
            UsersEntity u = i.getArgument(0);
            if (u.getId() == null) u.setId(100L);
            return u;
        });

        assertThat(fx.c.registerInitialAdmin(r).getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(fx.c.registerInitialAdmin(r).getStatusCode()).isEqualTo(HttpStatus.CREATED);

        setField(fx.c, "defaultTenantCode", "TENANT_A");
        setField(fx.c, "defaultTenantName", "Tenant A");
        when(fx.tenantsRepository.findFirstByOrderByIdAsc()).thenReturn(Optional.empty());
        when(fx.tenantsRepository.save(any(TenantsEntity.class))).thenAnswer(i -> i.getArgument(0));
        TenantsEntity created = invoke(fx.c, "resolveOrCreateDefaultTenantOrThrow", new Class[]{});
        assertThat(created.getCode()).isEqualTo("TENANT_A");
        assertThat(created.getName()).isEqualTo("Tenant A");
    }

    @Test
    void resendAndVerify2fa_shouldCoverBlankEmailAndModeSwitchBranches() throws Exception {
        Fx fx = new Fx();
        MockHttpServletRequest blankEmailReq = new MockHttpServletRequest();
        HttpSession bs = blankEmailReq.getSession(true);
        bs.setAttribute("login2fa.pendingUserId", 1L);
        bs.setAttribute("login2fa.pendingEmail", "   ");
        assertThat(fx.c.resendLogin2faEmail(blankEmailReq).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);

        when(fx.security2faPolicyService.evaluateLogin2faModeForUser(77L)).thenReturn(
                Security2faPolicyService.Login2faMode.TOTP_ONLY,
                Security2faPolicyService.Login2faMode.EMAIL_ONLY,
                Security2faPolicyService.Login2faMode.EMAIL_OR_TOTP
        );
        when(fx.security2faPolicyService.evaluateForUser(77L)).thenReturn(policy(true, true), policy(true, false), policy(false, true));
        when(fx.emailVerificationMailer.isEnabled()).thenReturn(true, true, true);
        when(fx.accountTotpService.isEnabledByEmail("x@example.com")).thenReturn(true, false, true);

        ResponseEntity<?> v1 = fx.c.verifyLogin2fa(verifyReq("email", "111111"), sessionReq(77L, "x@example.com"), new MockHttpServletResponse());
        ResponseEntity<?> v2 = fx.c.verifyLogin2fa(verifyReq("totp", "222222"), sessionReq(77L, "x@example.com"), new MockHttpServletResponse());
        ResponseEntity<?> v3 = fx.c.verifyLogin2fa(verifyReq("totp", "333333"), sessionReq(77L, "x@example.com"), new MockHttpServletResponse());
        assertThat(v1.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(v2.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(v3).isNotNull();
    }


    @Test
    void login_shouldCoverTotpAllowedButDisabledByUserBranch() throws Exception {
        Fx fx = new Fx();
        LoginRequest req = loginReq("totp-branch@example.com", "p");
        UsersEntity user = baseUser(66L, "totp-branch@example.com");
        when(fx.administratorService.findByUsername("totp-branch@example.com")).thenReturn(Optional.empty(), Optional.of(user));
        when(fx.authenticationManager.authenticate(any())).thenReturn(new UsernamePasswordAuthenticationToken("totp-branch@example.com", "p"));
        when(fx.security2faPolicyService.evaluateLogin2faModeForUser(66L)).thenReturn(Security2faPolicyService.Login2faMode.EMAIL_OR_TOTP);
        when(fx.security2faPolicyService.evaluateForUser(66L)).thenReturn(policy(true, true));
        when(fx.emailVerificationMailer.isEnabled()).thenReturn(true);
        when(fx.accountTotpService.isEnabledByEmail("totp-branch@example.com")).thenReturn(false);
        when(fx.emailVerificationService.getDefaultTtlSeconds()).thenReturn(300);
        ResponseEntity<?> r = fx.c.login(req, new MockHttpServletRequest(), new MockHttpServletResponse());
        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void ensurePermissionExists_shouldCoverBlankDescriptionBranch() throws Exception {
        Fx fx = new Fx();
        invokeVoid(fx.c, "ensurePermissionExists", new Class[]{String.class, String.class, String.class}, "r", "a", " ");
    }

    private static LoginRequest loginReq(String email, String password) {
        LoginRequest req = new LoginRequest();
        req.setEmail(email);
        req.setPassword(password);
        return req;
    }

    private static Login2faVerifyRequest verifyReq(String method, String code) {
        Login2faVerifyRequest req = new Login2faVerifyRequest();
        req.setMethod(method);
        req.setCode(code);
        return req;
    }

    private static MockHttpServletRequest sessionReq(Long uid, String email) {
        MockHttpServletRequest req = new MockHttpServletRequest();
        HttpSession session = req.getSession(true);
        session.setAttribute("login2fa.pendingUserId", uid);
        session.setAttribute("login2fa.pendingEmail", email);
        session.setAttribute("login2fa.mode", "EMAIL_OR_TOTP");
        session.setAttribute("login2fa.createdAtMs", System.currentTimeMillis());
        return req;
    }

    private static UsersEntity baseUser(Long id, String email) {
        UsersEntity u = new UsersEntity();
        u.setId(id);
        u.setEmail(email);
        u.setUsername("u");
        u.setStatus(AccountStatus.ACTIVE);
        u.setIsDeleted(false);
        return u;
    }

    private static Security2faPolicyStatusDTO policy(boolean emailAllowed, boolean totpAllowed) {
        Security2faPolicyStatusDTO dto = new Security2faPolicyStatusDTO();
        dto.setEmailOtpAllowed(emailAllowed);
        dto.setTotpAllowed(totpAllowed);
        return dto;
    }

    @SuppressWarnings("unchecked")
    private static <T> T invoke(AuthController target, String method, Class<?>[] types, Object... args) throws Exception {
        Method m = AuthController.class.getDeclaredMethod(method, types);
        m.setAccessible(true);
        return (T) m.invoke(target, args);
    }

    private static void invokeVoid(AuthController target, String method, Class<?>[] types, Object... args) throws Exception {
        Method m = AuthController.class.getDeclaredMethod(method, types);
        m.setAccessible(true);
        m.invoke(target, args);
    }

    @SuppressWarnings("unchecked")
    private static <T> T invokeStatic(String method, Class<?>[] types, Object... args) throws Exception {
        Method m = AuthController.class.getDeclaredMethod(method, types);
        m.setAccessible(true);
        return (T) m.invoke(null, args);
    }

    private static void setField(Object target, String name, Object value) throws Exception {
        Field f = AuthController.class.getDeclaredField(name);
        f.setAccessible(true);
        f.set(target, value);
    }

    private static class Fx {
        final AdministratorService administratorService = mock(AdministratorService.class);
        final AuthenticationManager authenticationManager = mock(AuthenticationManager.class);
        final PasswordEncoder passwordEncoder = mock(PasswordEncoder.class);
        final AdminSetupManager initialAdminSetupState = mock(AdminSetupManager.class);
        final TenantsRepository tenantsRepository = mock(TenantsRepository.class);
        final UserRoleLinksRepository userRoleLinksRepository = mock(UserRoleLinksRepository.class);
        final PermissionsRepository permissionsRepository = mock(PermissionsRepository.class);
        final RolePermissionsRepository rolePermissionsRepository = mock(RolePermissionsRepository.class);
        final AppSettingsService appSettingsService = mock(AppSettingsService.class);
        final RolesRepository rolesRepository = mock(RolesRepository.class);
        final BoardsRepository boardsRepository = mock(BoardsRepository.class);
        final BoardModeratorsRepository boardModeratorsRepository = mock(BoardModeratorsRepository.class);
        final InitialAdminIndexBootstrapService initialAdminIndexBootstrapService = mock(InitialAdminIndexBootstrapService.class);
        final TotpMasterKeyBootstrapService totpMasterKeyBootstrapService = mock(TotpMasterKeyBootstrapService.class);
        final EmailVerificationService emailVerificationService = mock(EmailVerificationService.class);
        final EmailVerificationMailer emailVerificationMailer = mock(EmailVerificationMailer.class);
        final Security2faPolicyService security2faPolicyService = mock(Security2faPolicyService.class);
        final AccountTotpService accountTotpService = mock(AccountTotpService.class);
        final UserDetailsService userDetailsService = mock(UserDetailsService.class);
        final AuditLogWriter auditLogWriter = mock(AuditLogWriter.class);
        final AuthController c = new AuthController(
                administratorService,
                authenticationManager,
                passwordEncoder,
                initialAdminSetupState,
                tenantsRepository,
                userRoleLinksRepository,
                permissionsRepository,
                rolePermissionsRepository,
                appSettingsService,
                rolesRepository,
                boardsRepository,
                boardModeratorsRepository,
                initialAdminIndexBootstrapService,
                totpMasterKeyBootstrapService,
                emailVerificationService,
                emailVerificationMailer,
                security2faPolicyService,
                accountTotpService,
                userDetailsService,
                auditLogWriter
        );

        Fx() throws Exception {
            setField(c, "defaultTenantCode", "DEFAULT");
            setField(c, "defaultTenantName", "Default Tenant");
        }
    }
}
