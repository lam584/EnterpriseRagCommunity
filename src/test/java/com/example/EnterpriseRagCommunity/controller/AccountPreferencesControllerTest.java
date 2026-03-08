package com.example.EnterpriseRagCommunity.controller;

import com.example.EnterpriseRagCommunity.entity.access.UsersEntity;
import com.example.EnterpriseRagCommunity.repository.access.UsersRepository;
import com.example.EnterpriseRagCommunity.service.access.AccessControlService;
import com.example.EnterpriseRagCommunity.service.ai.SupportedLanguageService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = AccountPreferencesController.class)
@AutoConfigureMockMvc(addFilters = false)
class AccountPreferencesControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private UsersRepository usersRepository;

    @MockitoBean
    private SupportedLanguageService supportedLanguageService;

    @MockitoBean
    private AccessControlService accessControlService;

    @AfterEach
    void cleanup() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void updatePreferences_shouldStoreTitleAndTagGenCounts() throws Exception {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("u@example.com", "x", Collections.emptyList())
        );

        UsersEntity user = new UsersEntity();
        user.setEmail("u@example.com");
        user.setIsDeleted(false);
        user.setMetadata(new LinkedHashMap<>());

        when(usersRepository.findByEmailAndIsDeletedFalse("u@example.com")).thenReturn(Optional.of(user));
        when(usersRepository.save(any(UsersEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(supportedLanguageService.normalizeToLanguageCode(any())).thenAnswer(invocation -> {
            String raw = invocation.getArgument(0, String.class);
            if (raw == null) return "zh-CN";
            String t = raw.trim();
            if (t.isEmpty()) return "zh-CN";
            return t;
        });

        mockMvc.perform(
                        put("/api/account/preferences")
                                .contentType("application/json")
                                .content("{\"titleGenCount\":7,\"tagGenCount\":9}")
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.targetLanguage").value("zh-CN"))
                .andExpect(jsonPath("$.titleGenCount").value(7))
                .andExpect(jsonPath("$.tagGenCount").value(9));

        ArgumentCaptor<UsersEntity> captor = ArgumentCaptor.forClass(UsersEntity.class);
        verify(usersRepository).save(captor.capture());
        Map<String, Object> metadata = captor.getValue().getMetadata();

        assertThat(metadata).isNotNull();
        assertThat(metadata).containsKey("preferences");
        Object prefsObj = metadata.get("preferences");
        assertThat(prefsObj).isInstanceOf(Map.class);
        @SuppressWarnings("unchecked")
        Map<String, Object> prefs = (Map<String, Object>) prefsObj;

        assertThat(prefs).containsKey("postsCompose");
        Object pcObj = prefs.get("postsCompose");
        assertThat(pcObj).isInstanceOf(Map.class);
        @SuppressWarnings("unchecked")
        Map<String, Object> postsCompose = (Map<String, Object>) pcObj;

        assertThat(postsCompose.get("titleGenCount")).isEqualTo(7);
        assertThat(postsCompose.get("tagGenCount")).isEqualTo(9);
    }

    @Test
    void updatePreferences_shouldReturn401_whenAuthIsNull() throws Exception {
        SecurityContextHolder.clearContext();

        mockMvc.perform(
                        put("/api/account/preferences")
                                .contentType("application/json")
                                .content("{}")
                )
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("未登录或会话已过期"));
    }

    @Test
    void updatePreferences_shouldReturn401_whenNotAuthenticated() throws Exception {
        TestingAuthenticationToken unauth = new TestingAuthenticationToken("u@example.com", "n/a");
        unauth.setAuthenticated(false);
        SecurityContextHolder.getContext().setAuthentication(unauth);

        mockMvc.perform(
                        put("/api/account/preferences")
                                .contentType("application/json")
                                .content("{}")
                )
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("未登录或会话已过期"));
    }

    @Test
    void updatePreferences_shouldReturn401_whenAnonymousUser() throws Exception {
        TestingAuthenticationToken anon = new TestingAuthenticationToken("anonymousUser", "n/a");
        anon.setAuthenticated(true);
        SecurityContextHolder.getContext().setAuthentication(anon);

        mockMvc.perform(
                        put("/api/account/preferences")
                                .contentType("application/json")
                                .content("{}")
                )
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("未登录或会话已过期"));
    }

    @Test
    void updatePreferences_shouldThrowServerError_whenUserNotFound() throws Exception {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("u@example.com", "x", Collections.emptyList())
        );
        when(usersRepository.findByEmailAndIsDeletedFalse("u@example.com")).thenReturn(Optional.empty());

        mockMvc.perform(
                        put("/api/account/preferences")
                                .contentType("application/json")
                                .content("{}")
                )
                .andExpect(status().is5xxServerError());
    }

    @Test
    void updatePreferences_shouldHandleExistingMaps_andTranslateFlags() throws Exception {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("u@example.com", "x", Collections.emptyList())
        );

        Map<String, Object> translate0 = new LinkedHashMap<>();
        translate0.put("targetLanguage", "de");
        Map<String, Object> postsCompose0 = new LinkedHashMap<>();
        postsCompose0.put("titleGenCount", 3);
        postsCompose0.put("tagGenCount", 4);
        Map<String, Object> prefs0 = new LinkedHashMap<>();
        prefs0.put("translate", translate0);
        prefs0.put("postsCompose", postsCompose0);
        Map<String, Object> metadata0 = new LinkedHashMap<>();
        metadata0.put("preferences", prefs0);

        UsersEntity user = new UsersEntity();
        user.setEmail("u@example.com");
        user.setIsDeleted(false);
        user.setMetadata(metadata0);

        when(usersRepository.findByEmailAndIsDeletedFalse("u@example.com")).thenReturn(Optional.of(user));
        when(usersRepository.save(any(UsersEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(supportedLanguageService.normalizeToLanguageCode(any())).thenAnswer(invocation -> {
            String raw = invocation.getArgument(0, String.class);
            if (raw == null) return "zh-CN";
            String t = raw.trim();
            if (t.isEmpty()) return "zh-CN";
            return t;
        });

        mockMvc.perform(
                        put("/api/account/preferences")
                                .contentType("application/json")
                                .content("{\"targetLanguage\":\" en \",\"autoTranslatePosts\":true,\"autoTranslateComments\":false}")
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.targetLanguage").value("en"))
                .andExpect(jsonPath("$.autoTranslatePosts").value(true))
                .andExpect(jsonPath("$.autoTranslateComments").value(false));

        ArgumentCaptor<UsersEntity> captor = ArgumentCaptor.forClass(UsersEntity.class);
        verify(usersRepository).save(captor.capture());
        Map<String, Object> savedMetadata = captor.getValue().getMetadata();

        assertThat(savedMetadata).isNotNull();
        Object prefsObj = savedMetadata.get("preferences");
        assertThat(prefsObj).isInstanceOf(Map.class);
        @SuppressWarnings("unchecked")
        Map<String, Object> prefs = (Map<String, Object>) prefsObj;

        Object translateObj = prefs.get("translate");
        assertThat(translateObj).isInstanceOf(Map.class);
        @SuppressWarnings("unchecked")
        Map<String, Object> translate = (Map<String, Object>) translateObj;

        assertThat(translate.get("targetLanguage")).isEqualTo("en");
        assertThat(translate.get("autoTranslatePosts")).isEqualTo(true);
        assertThat(translate.get("autoTranslateComments")).isEqualTo(false);
    }

    @Test
    void updatePreferences_shouldStoreNullTargetLanguage_whenPresentButNull() throws Exception {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("u@example.com", "x", Collections.emptyList())
        );

        UsersEntity user = new UsersEntity();
        user.setEmail("u@example.com");
        user.setIsDeleted(false);
        user.setMetadata(null);

        when(usersRepository.findByEmailAndIsDeletedFalse("u@example.com")).thenReturn(Optional.of(user));
        when(usersRepository.save(any(UsersEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(supportedLanguageService.normalizeToLanguageCode(any())).thenReturn("zh-CN");

        mockMvc.perform(
                        put("/api/account/preferences")
                                .contentType("application/json")
                                .content("{\"targetLanguage\":null}")
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.targetLanguage").value("zh-CN"));

        ArgumentCaptor<UsersEntity> captor = ArgumentCaptor.forClass(UsersEntity.class);
        verify(usersRepository).save(captor.capture());
        Map<String, Object> savedMetadata = captor.getValue().getMetadata();

        Object prefsObj = savedMetadata.get("preferences");
        assertThat(prefsObj).isInstanceOf(Map.class);
        @SuppressWarnings("unchecked")
        Map<String, Object> prefs = (Map<String, Object>) prefsObj;

        Object translateObj = prefs.get("translate");
        assertThat(translateObj).isInstanceOf(Map.class);
        @SuppressWarnings("unchecked")
        Map<String, Object> translate = (Map<String, Object>) translateObj;

        assertThat(translate).containsKey("targetLanguage");
        assertThat(translate.get("targetLanguage")).isNull();
    }

    @Test
    void getPreferences_shouldReturn401_whenAuthIsNull() throws Exception {
        SecurityContextHolder.clearContext();

        mockMvc.perform(get("/api/account/preferences"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("未登录或会话已过期"));
    }

    @Test
    void getPreferences_shouldReturn401_whenNotAuthenticated() throws Exception {
        TestingAuthenticationToken unauth = new TestingAuthenticationToken("u@example.com", "n/a");
        unauth.setAuthenticated(false);
        SecurityContextHolder.getContext().setAuthentication(unauth);

        mockMvc.perform(get("/api/account/preferences"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("未登录或会话已过期"));
    }

    @Test
    void getPreferences_shouldReturn401_whenAnonymousUser() throws Exception {
        TestingAuthenticationToken anon = new TestingAuthenticationToken("anonymousUser", "n/a");
        anon.setAuthenticated(true);
        SecurityContextHolder.getContext().setAuthentication(anon);

        mockMvc.perform(get("/api/account/preferences"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("未登录或会话已过期"));
    }

    @Test
    void getPreferences_shouldThrowServerError_whenUserNotFound() throws Exception {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("u@example.com", "x", Collections.emptyList())
        );
        when(usersRepository.findByEmailAndIsDeletedFalse("u@example.com")).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/account/preferences"))
                .andExpect(status().is5xxServerError());
    }

    @Test
    void getPreferences_shouldMapAllFields_whenMetadataValid() throws Exception {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("u@example.com", "x", Collections.emptyList())
        );

        Map<String, Object> translate = new LinkedHashMap<>();
        translate.put("targetLanguage", "en");
        translate.put("autoTranslatePosts", true);
        translate.put("autoTranslateComments", false);
        Map<String, Object> postsCompose = new LinkedHashMap<>();
        postsCompose.put("titleGenCount", 1);
        postsCompose.put("tagGenCount", 50);
        Map<String, Object> prefs = new LinkedHashMap<>();
        prefs.put("translate", translate);
        prefs.put("postsCompose", postsCompose);
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("preferences", prefs);

        UsersEntity user = new UsersEntity();
        user.setEmail("u@example.com");
        user.setIsDeleted(false);
        user.setMetadata(metadata);

        when(usersRepository.findByEmailAndIsDeletedFalse("u@example.com")).thenReturn(Optional.of(user));
        when(supportedLanguageService.normalizeToLanguageCode(any())).thenAnswer(invocation -> invocation.getArgument(0));

        mockMvc.perform(get("/api/account/preferences"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.targetLanguage").value("en"))
                .andExpect(jsonPath("$.autoTranslatePosts").value(true))
                .andExpect(jsonPath("$.autoTranslateComments").value(false))
                .andExpect(jsonPath("$.titleGenCount").value(1))
                .andExpect(jsonPath("$.tagGenCount").value(50));
    }

    @Test
    void getPreferences_shouldHandlePreferencesNotMap() throws Exception {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("u@example.com", "x", Collections.emptyList())
        );

        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("preferences", "oops");

        UsersEntity user = new UsersEntity();
        user.setEmail("u@example.com");
        user.setIsDeleted(false);
        user.setMetadata(metadata);

        when(usersRepository.findByEmailAndIsDeletedFalse("u@example.com")).thenReturn(Optional.of(user));
        when(supportedLanguageService.normalizeToLanguageCode(any())).thenReturn("zh-CN");

        mockMvc.perform(get("/api/account/preferences"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.targetLanguage").value("zh-CN"))
                .andExpect(jsonPath("$.autoTranslatePosts").value(false))
                .andExpect(jsonPath("$.autoTranslateComments").value(false));
    }

    @Test
    void getPreferences_shouldHandleTranslateAndPostsComposeNotMap() throws Exception {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("u@example.com", "x", Collections.emptyList())
        );

        Map<String, Object> prefs = new LinkedHashMap<>();
        prefs.put("translate", "oops");
        prefs.put("postsCompose", 123);
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("preferences", prefs);

        UsersEntity user = new UsersEntity();
        user.setEmail("u@example.com");
        user.setIsDeleted(false);
        user.setMetadata(metadata);

        when(usersRepository.findByEmailAndIsDeletedFalse("u@example.com")).thenReturn(Optional.of(user));
        when(supportedLanguageService.normalizeToLanguageCode(any())).thenReturn("zh-CN");

        mockMvc.perform(get("/api/account/preferences"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.targetLanguage").value("zh-CN"))
                .andExpect(jsonPath("$.autoTranslatePosts").value(false))
                .andExpect(jsonPath("$.autoTranslateComments").value(false));
    }

    @Test
    void getPreferences_shouldIgnoreInvalidValuesInsideMaps() throws Exception {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("u@example.com", "x", Collections.emptyList())
        );

        Map<String, Object> translate = new LinkedHashMap<>();
        translate.put("targetLanguage", "   ");
        translate.put("autoTranslatePosts", "true");
        translate.put("autoTranslateComments", 1);
        Map<String, Object> postsCompose = new LinkedHashMap<>();
        postsCompose.put("titleGenCount", 0);
        postsCompose.put("tagGenCount", 51);
        Map<String, Object> prefs = new LinkedHashMap<>();
        prefs.put("translate", translate);
        prefs.put("postsCompose", postsCompose);
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("preferences", prefs);

        UsersEntity user = new UsersEntity();
        user.setEmail("u@example.com");
        user.setIsDeleted(false);
        user.setMetadata(metadata);

        when(usersRepository.findByEmailAndIsDeletedFalse("u@example.com")).thenReturn(Optional.of(user));
        when(supportedLanguageService.normalizeToLanguageCode(any())).thenReturn("zh-CN");

        mockMvc.perform(get("/api/account/preferences"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.targetLanguage").value("zh-CN"))
                .andExpect(jsonPath("$.autoTranslatePosts").value(false))
                .andExpect(jsonPath("$.autoTranslateComments").value(false));
    }

    @Test
    void getPreferences_shouldIgnoreNonNumberCounts() throws Exception {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("u@example.com", "x", Collections.emptyList())
        );

        Map<String, Object> translate = new LinkedHashMap<>();
        Map<String, Object> postsCompose = new LinkedHashMap<>();
        postsCompose.put("titleGenCount", "x");
        postsCompose.put("tagGenCount", "y");
        Map<String, Object> prefs = new LinkedHashMap<>();
        prefs.put("translate", translate);
        prefs.put("postsCompose", postsCompose);
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("preferences", prefs);

        UsersEntity user = new UsersEntity();
        user.setEmail("u@example.com");
        user.setIsDeleted(false);
        user.setMetadata(metadata);

        when(usersRepository.findByEmailAndIsDeletedFalse("u@example.com")).thenReturn(Optional.of(user));
        when(supportedLanguageService.normalizeToLanguageCode(any())).thenReturn("zh-CN");

        mockMvc.perform(get("/api/account/preferences"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.targetLanguage").value("zh-CN"))
                .andExpect(jsonPath("$.autoTranslatePosts").value(false))
                .andExpect(jsonPath("$.autoTranslateComments").value(false));
    }

    @Test
    void getPreferences_shouldHandleNullMetadata() throws Exception {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("u@example.com", "x", Collections.emptyList())
        );

        UsersEntity user = new UsersEntity();
        user.setEmail("u@example.com");
        user.setIsDeleted(false);
        user.setMetadata(null);

        when(usersRepository.findByEmailAndIsDeletedFalse("u@example.com")).thenReturn(Optional.of(user));
        when(supportedLanguageService.normalizeToLanguageCode(any())).thenReturn("zh-CN");

        mockMvc.perform(get("/api/account/preferences"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.targetLanguage").value("zh-CN"))
                .andExpect(jsonPath("$.autoTranslatePosts").value(false))
                .andExpect(jsonPath("$.autoTranslateComments").value(false));
    }

    @Test
    void getPreferences_shouldCoverOtherOutOfRangeCountsCases() throws Exception {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("u@example.com", "x", Collections.emptyList())
        );

        Map<String, Object> translate = new LinkedHashMap<>();
        translate.put("targetLanguage", "en");
        Map<String, Object> postsCompose = new LinkedHashMap<>();
        postsCompose.put("titleGenCount", 51);
        postsCompose.put("tagGenCount", 0);
        Map<String, Object> prefs = new LinkedHashMap<>();
        prefs.put("translate", translate);
        prefs.put("postsCompose", postsCompose);
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("preferences", prefs);

        UsersEntity user = new UsersEntity();
        user.setEmail("u@example.com");
        user.setIsDeleted(false);
        user.setMetadata(metadata);

        when(usersRepository.findByEmailAndIsDeletedFalse("u@example.com")).thenReturn(Optional.of(user));
        when(supportedLanguageService.normalizeToLanguageCode(any())).thenAnswer(invocation -> invocation.getArgument(0));

        mockMvc.perform(get("/api/account/preferences"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.targetLanguage").value("en"))
                .andExpect(jsonPath("$.autoTranslatePosts").value(false))
                .andExpect(jsonPath("$.autoTranslateComments").value(false));
    }

    @Test
    void getPreferences_shouldCoverAutoTranslateTrueAndFalseCombinations() throws Exception {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("u@example.com", "x", Collections.emptyList())
        );

        Map<String, Object> translate = new LinkedHashMap<>();
        translate.put("targetLanguage", "en");
        translate.put("autoTranslatePosts", false);
        translate.put("autoTranslateComments", true);
        Map<String, Object> prefs = new LinkedHashMap<>();
        prefs.put("translate", translate);
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("preferences", prefs);

        UsersEntity user = new UsersEntity();
        user.setEmail("u@example.com");
        user.setIsDeleted(false);
        user.setMetadata(metadata);

        when(usersRepository.findByEmailAndIsDeletedFalse("u@example.com")).thenReturn(Optional.of(user));
        when(supportedLanguageService.normalizeToLanguageCode(any())).thenAnswer(invocation -> invocation.getArgument(0));

        mockMvc.perform(get("/api/account/preferences"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.targetLanguage").value("en"))
                .andExpect(jsonPath("$.autoTranslatePosts").value(false))
                .andExpect(jsonPath("$.autoTranslateComments").value(true));
    }

    @Test
    void updatePreferences_shouldTrimWhitespaceTargetLanguageToNull() throws Exception {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("u@example.com", "x", Collections.emptyList())
        );

        UsersEntity user = new UsersEntity();
        user.setEmail("u@example.com");
        user.setIsDeleted(false);
        user.setMetadata(new LinkedHashMap<>());

        when(usersRepository.findByEmailAndIsDeletedFalse("u@example.com")).thenReturn(Optional.of(user));
        when(usersRepository.save(any(UsersEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(supportedLanguageService.normalizeToLanguageCode(any())).thenReturn("zh-CN");

        mockMvc.perform(
                        put("/api/account/preferences")
                                .contentType("application/json")
                                .content("{\"targetLanguage\":\"   \"}")
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.targetLanguage").value("zh-CN"));

        ArgumentCaptor<UsersEntity> captor = ArgumentCaptor.forClass(UsersEntity.class);
        verify(usersRepository).save(captor.capture());
        Map<String, Object> savedMetadata = captor.getValue().getMetadata();

        Object prefsObj = savedMetadata.get("preferences");
        assertThat(prefsObj).isInstanceOf(Map.class);
        @SuppressWarnings("unchecked")
        Map<String, Object> prefs = (Map<String, Object>) prefsObj;

        Object translateObj = prefs.get("translate");
        assertThat(translateObj).isInstanceOf(Map.class);
        @SuppressWarnings("unchecked")
        Map<String, Object> translate = (Map<String, Object>) translateObj;

        assertThat(translate).containsKey("targetLanguage");
        assertThat(translate.get("targetLanguage")).isNull();
    }
}
