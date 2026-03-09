package com.example.EnterpriseRagCommunity.service.access;

import com.example.EnterpriseRagCommunity.dto.access.Security2faPolicySettingsDTO;
import com.example.EnterpriseRagCommunity.dto.access.Security2faPolicyStatusDTO;
import com.example.EnterpriseRagCommunity.entity.access.UserRoleLinksEntity;
import com.example.EnterpriseRagCommunity.entity.access.UsersEntity;
import com.example.EnterpriseRagCommunity.repository.access.UserRoleLinksRepository;
import com.example.EnterpriseRagCommunity.repository.access.UsersRepository;
import com.example.EnterpriseRagCommunity.service.monitor.AppSettingsService;
import com.example.EnterpriseRagCommunity.service.notify.EmailVerificationMailer;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.anyString;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class Security2faPolicyServiceBranchTest {

    @Test
    void evaluate_and_mode_should_cover_main_branches() {
        AppSettingsService appSettingsService = mock(AppSettingsService.class);
        when(appSettingsService.getString(Security2faPolicyService.KEY_TOTP_POLICY)).thenReturn(Optional.of("require_roles"));
        when(appSettingsService.getString(Security2faPolicyService.KEY_TOTP_ROLE_IDS)).thenReturn(Optional.of("1,2,abc,-1"));
        when(appSettingsService.getString(Security2faPolicyService.KEY_TOTP_USER_IDS)).thenReturn(Optional.of("3"));
        when(appSettingsService.getString(Security2faPolicyService.KEY_EMAIL_OTP_POLICY)).thenReturn(Optional.of("allow_users"));
        when(appSettingsService.getString(Security2faPolicyService.KEY_EMAIL_OTP_ROLE_IDS)).thenReturn(Optional.of("9"));
        when(appSettingsService.getString(Security2faPolicyService.KEY_EMAIL_OTP_USER_IDS)).thenReturn(Optional.of("100,200"));
        when(appSettingsService.getString(Security2faPolicyService.KEY_LOGIN2FA_MODE)).thenReturn(Optional.of("email_or_totp"));
        when(appSettingsService.getString(Security2faPolicyService.KEY_LOGIN2FA_SCOPE_POLICY)).thenReturn(Optional.of("allow_users"));
        when(appSettingsService.getString(Security2faPolicyService.KEY_LOGIN2FA_ROLE_IDS)).thenReturn(Optional.of("5"));
        when(appSettingsService.getString(Security2faPolicyService.KEY_LOGIN2FA_USER_IDS)).thenReturn(Optional.of("100"));

        UserRoleLinksRepository userRoleLinksRepository = mock(UserRoleLinksRepository.class);
        UserRoleLinksEntity l1 = new UserRoleLinksEntity();
        l1.setRoleId(1L);
        UserRoleLinksEntity l2 = new UserRoleLinksEntity();
        l2.setRoleId(null);
        when(userRoleLinksRepository.findByUserId(100L)).thenReturn(List.of(l1, l2));
        when(userRoleLinksRepository.findByUserId(101L)).thenReturn(List.of());

        UsersRepository usersRepository = mock(UsersRepository.class);
        UsersEntity u = new UsersEntity();
        Map<String, Object> sec = new HashMap<>();
        sec.put("login2faEnabled", true);
        Map<String, Object> pref = new HashMap<>();
        pref.put("security", sec);
        u.setMetadata(Map.of("preferences", pref));
        when(usersRepository.findById(100L)).thenReturn(Optional.of(u));
        when(usersRepository.findById(101L)).thenReturn(Optional.empty());

        EmailVerificationMailer emailVerificationMailer = mock(EmailVerificationMailer.class);
        when(emailVerificationMailer.isEnabled()).thenReturn(true);

        Security2faPolicyService svc = new Security2faPolicyService(
                appSettingsService, userRoleLinksRepository, usersRepository, emailVerificationMailer
        );

        Security2faPolicyStatusDTO s1 = svc.evaluateForUser(100L);
        assertTrue(s1.isTotpRequired());
        assertTrue(s1.isTotpAllowed());
        assertTrue(s1.isEmailOtpAllowed());
        assertTrue(s1.isLogin2faAllowed());
        assertTrue(s1.isLogin2faCanEnable());
        assertTrue(s1.isLogin2faEnabled());

        Security2faPolicyStatusDTO s2 = svc.evaluateForUser(101L);
        assertFalse(s2.isTotpAllowed());
        assertFalse(s2.isTotpRequired());
        assertFalse(s2.isEmailOtpAllowed());
        assertFalse(s2.isLogin2faEnabled());

        assertEquals(Security2faPolicyService.Login2faMode.EMAIL_OR_TOTP, svc.evaluateLogin2faModeForUser(100L));
        assertEquals(Security2faPolicyService.Login2faMode.DISABLED, svc.evaluateLogin2faModeForUser(101L));
        assertThrows(IllegalArgumentException.class, () -> svc.evaluateForUser(null));
        assertThrows(IllegalArgumentException.class, () -> svc.evaluateLogin2faModeForUser(null));
    }

    @Test
    void save_and_get_should_cover_parse_and_join_branches() {
        AppSettingsService appSettingsService = mock(AppSettingsService.class);
        when(appSettingsService.getString(anyString())).thenReturn(Optional.empty());
        EmailVerificationMailer emailVerificationMailer = mock(EmailVerificationMailer.class);
        when(emailVerificationMailer.isEnabled()).thenReturn(false);
        Security2faPolicyService svc = new Security2faPolicyService(
                appSettingsService, mock(UserRoleLinksRepository.class), mock(UsersRepository.class), emailVerificationMailer
        );

        Security2faPolicySettingsDTO in = new Security2faPolicySettingsDTO();
        in.setTotpPolicy("FORBID_ALL");
        in.setTotpRoleIds(List.of(9L));
        in.setTotpUserIds(List.of(7L));
        in.setEmailOtpPolicy("ALLOW_ALL");
        in.setEmailOtpRoleIds(List.of(2L));
        in.setEmailOtpUserIds(List.of(4L));
        in.setLogin2faMode("DISABLED");
        in.setLogin2faScopePolicy("ALLOW_ALL");
        in.setLogin2faRoleIds(List.of(1L));
        in.setLogin2faUserIds(List.of(3L));

        Security2faPolicySettingsDTO out = svc.saveAdminSettings(in);
        assertEquals("FORBID_ALL", out.getTotpPolicy());
        assertTrue(out.getTotpRoleIds().isEmpty());
        assertTrue(out.getTotpUserIds().isEmpty());

        ArgumentCaptor<String> keyCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> valueCaptor = ArgumentCaptor.forClass(String.class);
        verify(appSettingsService, atLeastOnce()).upsertString(keyCaptor.capture(), valueCaptor.capture());
        assertNotNull(keyCaptor.getAllValues());

        Security2faPolicySettingsDTO def = svc.getAdminSettingsOrDefault();
        assertEquals("ALLOW_ALL", def.getTotpPolicy());
        assertEquals("ALLOW_ALL", def.getEmailOtpPolicy());
    }

    @Test
    void normalize_and_preference_should_cover_validation_and_metadata_branches() throws Exception {
        Security2faPolicyService svc = new Security2faPolicyService(
                mock(AppSettingsService.class), mock(UserRoleLinksRepository.class), mock(UsersRepository.class), mock(EmailVerificationMailer.class)
        );

        Security2faPolicySettingsDTO raw = new Security2faPolicySettingsDTO();
        raw.setTotpPolicy("allow_roles");
        raw.setTotpRoleIds(List.of(2L, 2L, 1L, -1L));
        raw.setEmailOtpPolicy("require_users");
        raw.setEmailOtpUserIds(List.of(7L, 7L));
        raw.setLogin2faMode("totp_only");
        raw.setLogin2faScopePolicy("require_roles");
        raw.setLogin2faRoleIds(List.of(6L, 6L, 1L));
        Security2faPolicySettingsDTO out = svc.normalizeAdminSettings(raw);
        assertEquals(List.of(1L, 2L), out.getTotpRoleIds());
        assertEquals(List.of(7L), out.getEmailOtpUserIds());
        assertEquals(List.of(1L, 6L), out.getLogin2faRoleIds());

        Security2faPolicySettingsDTO bad1 = new Security2faPolicySettingsDTO();
        bad1.setTotpPolicy("REQUIRE_USERS");
        assertThrows(IllegalArgumentException.class, () -> svc.normalizeAdminSettings(bad1));
        Security2faPolicySettingsDTO bad2 = new Security2faPolicySettingsDTO();
        bad2.setTotpPolicy("ALLOW_ALL");
        bad2.setEmailOtpPolicy("REQUIRE_ROLES");
        assertThrows(IllegalArgumentException.class, () -> svc.normalizeAdminSettings(bad2));
        Security2faPolicySettingsDTO bad3 = new Security2faPolicySettingsDTO();
        bad3.setTotpPolicy("ALLOW_ALL");
        bad3.setEmailOtpPolicy("ALLOW_ALL");
        bad3.setLogin2faScopePolicy("REQUIRE_USERS");
        assertThrows(IllegalArgumentException.class, () -> svc.normalizeAdminSettings(bad3));

        assertFalse(svc.readLogin2faEnabledFromMetadata(null));
        assertFalse(svc.readLogin2faEnabledFromMetadata(Map.of("preferences", "x")));
        assertFalse(svc.readLogin2faEnabledFromMetadata(Map.of("preferences", Map.of("security", "x"))));
        assertFalse(svc.readLogin2faEnabledFromMetadata(Map.of("preferences", Map.of("security", Map.of("login2faEnabled", "true")))));
        assertTrue(svc.readLogin2faEnabledFromMetadata(Map.of("preferences", Map.of("security", Map.of("login2faEnabled", true)))));

        Method matches = Security2faPolicyService.class.getDeclaredMethod(
                "matches",
                Security2faPolicyService.EnablePolicy.class,
                Long.class,
                Set.class,
                List.class,
                List.class
        );
        matches.setAccessible(true);
        assertEquals(false, matches.invoke(null, Security2faPolicyService.EnablePolicy.ALLOW_ROLES, 1L, Set.of(), List.of(1L), List.of()));
        assertEquals(false, matches.invoke(null, Security2faPolicyService.EnablePolicy.ALLOW_USERS, 0L, Set.of(1L), List.of(), List.of(1L)));
        assertEquals(true, matches.invoke(null, Security2faPolicyService.EnablePolicy.ALLOW_ALL, 1L, Set.of(), List.of(), List.of()));
    }

    @Test
    void enum_parse_should_cover_fallback_branches() {
        assertEquals(Security2faPolicyService.EnablePolicy.ALLOW_ALL,
                Security2faPolicyService.EnablePolicy.parseOrDefault(null, Security2faPolicyService.EnablePolicy.ALLOW_ALL));
        assertEquals(Security2faPolicyService.EnablePolicy.ALLOW_ALL,
                Security2faPolicyService.EnablePolicy.parseOrDefault(" ", Security2faPolicyService.EnablePolicy.ALLOW_ALL));
        assertEquals(Security2faPolicyService.EnablePolicy.ALLOW_ALL,
                Security2faPolicyService.EnablePolicy.parseOrDefault("x", Security2faPolicyService.EnablePolicy.ALLOW_ALL));
        assertEquals(Security2faPolicyService.EnablePolicy.REQUIRE_ALL,
                Security2faPolicyService.EnablePolicy.parseOrDefault("require_all", Security2faPolicyService.EnablePolicy.ALLOW_ALL));

        assertEquals(Security2faPolicyService.Login2faMode.DISABLED,
                Security2faPolicyService.Login2faMode.parseOrDefault("bad", Security2faPolicyService.Login2faMode.DISABLED));
        assertEquals(Security2faPolicyService.Login2faMode.EMAIL_ONLY,
                Security2faPolicyService.Login2faMode.parseOrDefault("email_only", Security2faPolicyService.Login2faMode.DISABLED));
    }

    @Test
    void login2fa_mode_branches_should_cover_disabled_required_and_preference_paths() {
        AppSettingsService appSettingsService = mock(AppSettingsService.class);
        when(appSettingsService.getString(Security2faPolicyService.KEY_TOTP_POLICY)).thenReturn(Optional.of("ALLOW_ALL"));
        when(appSettingsService.getString(Security2faPolicyService.KEY_TOTP_ROLE_IDS)).thenReturn(Optional.empty());
        when(appSettingsService.getString(Security2faPolicyService.KEY_TOTP_USER_IDS)).thenReturn(Optional.empty());
        when(appSettingsService.getString(Security2faPolicyService.KEY_EMAIL_OTP_POLICY)).thenReturn(Optional.of("FORBID_ALL"));
        when(appSettingsService.getString(Security2faPolicyService.KEY_EMAIL_OTP_ROLE_IDS)).thenReturn(Optional.empty());
        when(appSettingsService.getString(Security2faPolicyService.KEY_EMAIL_OTP_USER_IDS)).thenReturn(Optional.empty());
        when(appSettingsService.getString(Security2faPolicyService.KEY_LOGIN2FA_MODE)).thenReturn(Optional.of("TOTP_ONLY"), Optional.of("DISABLED"));
        when(appSettingsService.getString(Security2faPolicyService.KEY_LOGIN2FA_SCOPE_POLICY)).thenReturn(Optional.of("REQUIRE_ALL"));
        when(appSettingsService.getString(Security2faPolicyService.KEY_LOGIN2FA_ROLE_IDS)).thenReturn(Optional.empty());
        when(appSettingsService.getString(Security2faPolicyService.KEY_LOGIN2FA_USER_IDS)).thenReturn(Optional.empty());

        UsersRepository usersRepository = mock(UsersRepository.class);
        UsersEntity user = new UsersEntity();
        user.setMetadata(Map.of());
        when(usersRepository.findById(9L)).thenReturn(Optional.of(user));

        Security2faPolicyService svc = new Security2faPolicyService(
                appSettingsService,
                mock(UserRoleLinksRepository.class),
                usersRepository,
                mock(EmailVerificationMailer.class)
        );

        assertEquals(Security2faPolicyService.Login2faMode.TOTP_ONLY, svc.evaluateLogin2faModeForUser(9L));
        assertEquals(Security2faPolicyService.Login2faMode.DISABLED, svc.evaluateLogin2faModeForUser(9L));
        assertFalse(svc.isLogin2faEnabledByUserPreference(null));
        assertFalse(svc.isLogin2faEnabledByUserPreference(0L));
    }

    @Test
    void evaluate_should_cover_forbid_all_and_disabled_email_service_branches() {
        AppSettingsService appSettingsService = mock(AppSettingsService.class);
        when(appSettingsService.getString(Security2faPolicyService.KEY_TOTP_POLICY)).thenReturn(Optional.of("FORBID_ALL"));
        when(appSettingsService.getString(Security2faPolicyService.KEY_TOTP_ROLE_IDS)).thenReturn(Optional.empty());
        when(appSettingsService.getString(Security2faPolicyService.KEY_TOTP_USER_IDS)).thenReturn(Optional.empty());
        when(appSettingsService.getString(Security2faPolicyService.KEY_EMAIL_OTP_POLICY)).thenReturn(Optional.of("REQUIRE_ALL"));
        when(appSettingsService.getString(Security2faPolicyService.KEY_EMAIL_OTP_ROLE_IDS)).thenReturn(Optional.empty());
        when(appSettingsService.getString(Security2faPolicyService.KEY_EMAIL_OTP_USER_IDS)).thenReturn(Optional.empty());
        when(appSettingsService.getString(Security2faPolicyService.KEY_LOGIN2FA_MODE)).thenReturn(Optional.of("EMAIL_ONLY"));
        when(appSettingsService.getString(Security2faPolicyService.KEY_LOGIN2FA_SCOPE_POLICY)).thenReturn(Optional.of("FORBID_ALL"));
        when(appSettingsService.getString(Security2faPolicyService.KEY_LOGIN2FA_ROLE_IDS)).thenReturn(Optional.empty());
        when(appSettingsService.getString(Security2faPolicyService.KEY_LOGIN2FA_USER_IDS)).thenReturn(Optional.empty());

        EmailVerificationMailer mailer = mock(EmailVerificationMailer.class);
        when(mailer.isEnabled()).thenReturn(false);

        Security2faPolicyService svc = new Security2faPolicyService(
                appSettingsService,
                mock(UserRoleLinksRepository.class),
                mock(UsersRepository.class),
                mailer
        );

        Security2faPolicyStatusDTO status = svc.evaluateForUser(88L);
        assertFalse(status.isTotpAllowed());
        assertFalse(status.isTotpRequired());
        assertFalse(status.isEmailOtpAllowed());
        assertFalse(status.isEmailOtpRequired());
        assertFalse(status.isLogin2faAllowed());
        assertFalse(status.isLogin2faRequired());
        assertFalse(status.isLogin2faCanEnable());
        assertFalse(status.isLogin2faEnabled());
    }
}
