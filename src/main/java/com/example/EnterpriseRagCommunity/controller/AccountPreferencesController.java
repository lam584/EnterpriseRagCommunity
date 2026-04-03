package com.example.EnterpriseRagCommunity.controller;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.EnterpriseRagCommunity.dto.access.TranslatePreferencesDTO;
import com.example.EnterpriseRagCommunity.dto.access.UpdateTranslatePreferencesRequest;
import com.example.EnterpriseRagCommunity.entity.access.UsersEntity;
import com.example.EnterpriseRagCommunity.repository.access.UsersRepository;
import com.example.EnterpriseRagCommunity.service.ai.SupportedLanguageService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/account")
@RequiredArgsConstructor
@CrossOrigin(origins = {"http://localhost:5173", "http://127.0.0.1:5173"}, allowCredentials = "true")
public class AccountPreferencesController {

    private final UsersRepository usersRepository;
    private final SupportedLanguageService supportedLanguageService;

    @GetMapping("/preferences")
    public ResponseEntity<?> getPreferences() {
        String email = currentEmailOrNull();
        if (email == null) return unauthorized();

        UsersEntity user = requireActiveUserByEmail(email);

        return ResponseEntity.ok(toTranslatePreferencesDto(user.getMetadata()));
    }

    @PutMapping("/preferences")
    public ResponseEntity<?> updatePreferences(@RequestBody @Valid UpdateTranslatePreferencesRequest req) {
        String email = currentEmailOrNull();
        if (email == null) return unauthorized();

        UsersEntity user = requireActiveUserByEmail(email);

        Map<String, Object> metadata0 = user.getMetadata();
        Map<String, Object> metadata = (metadata0 == null) ? new LinkedHashMap<>() : new LinkedHashMap<>(metadata0);

        Map<String, Object> prefs = mutableChildMap(metadata, "preferences");
        Map<String, Object> translate = mutableChildMap(prefs, "translate");
        Map<String, Object> postsCompose = mutableChildMap(prefs, "postsCompose");

        if (req.isTargetLanguagePresent()) {
            String tl = req.getTargetLanguage() == null ? null : req.getTargetLanguage().trim();
            if (tl != null && tl.isEmpty()) tl = null;
            translate.put("targetLanguage", tl == null ? null : supportedLanguageService.normalizeToLanguageCode(tl));
        }

        if (req.isAutoTranslatePostsPresent()) {
            Boolean v = req.getAutoTranslatePosts();
            translate.put("autoTranslatePosts", v);
        }

        if (req.isAutoTranslateCommentsPresent()) {
            Boolean v = req.getAutoTranslateComments();
            translate.put("autoTranslateComments", v);
        }

        if (req.isTitleGenCountPresent()) {
            postsCompose.put("titleGenCount", req.getTitleGenCount());
        }

        if (req.isTagGenCountPresent()) {
            postsCompose.put("tagGenCount", req.getTagGenCount());
        }

        prefs.put("translate", translate);
        prefs.put("postsCompose", postsCompose);
        metadata.put("preferences", prefs);
        user.setMetadata(metadata);

        UsersEntity saved = usersRepository.save(user);
        return ResponseEntity.ok(toTranslatePreferencesDto(saved.getMetadata()));
    }

    private TranslatePreferencesDTO toTranslatePreferencesDto(Map<String, Object> metadata) {
        TranslatePreferencesDTO dto = new TranslatePreferencesDTO();
        Map<String, Object> prefs = childMapOrNull(metadata, "preferences");
        Map<String, Object> translate = childMapOrNull(prefs, "translate");
        Map<String, Object> postsCompose = childMapOrNull(prefs, "postsCompose");

        String targetLanguage = null;
        Boolean autoTranslatePosts = null;
        Boolean autoTranslateComments = null;
        if (translate != null) {
            Object tl = translate.get("targetLanguage");
            if (tl != null) {
                String s = String.valueOf(tl).trim();
                if (StringUtils.hasText(s)) targetLanguage = s;
            }
            Object ap = translate.get("autoTranslatePosts");
            if (ap instanceof Boolean) autoTranslatePosts = (Boolean) ap;
            Object ac = translate.get("autoTranslateComments");
            if (ac instanceof Boolean) autoTranslateComments = (Boolean) ac;
        }

        Integer titleGenCount = null;
        Integer tagGenCount = null;
        if (postsCompose != null) {
            Object tc = postsCompose.get("titleGenCount");
            if (tc instanceof Number) {
                int n = ((Number) tc).intValue();
                if (n >= 1 && n <= 50) titleGenCount = n;
            }
            Object kc = postsCompose.get("tagGenCount");
            if (kc instanceof Number) {
                int n = ((Number) kc).intValue();
                if (n >= 1 && n <= 50) tagGenCount = n;
            }
        }

        dto.setTargetLanguage(supportedLanguageService.normalizeToLanguageCode(targetLanguage));
        dto.setAutoTranslatePosts(autoTranslatePosts != null && autoTranslatePosts);
        dto.setAutoTranslateComments(autoTranslateComments != null && autoTranslateComments);
        dto.setTitleGenCount(titleGenCount);
        dto.setTagGenCount(tagGenCount);
        return dto;
    }

    private static String currentEmailOrNull() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || "anonymousUser".equals(auth.getPrincipal())) {
            return null;
        }
        return auth.getName();
    }

    private static ResponseEntity<Map<String, String>> unauthorized() {
        Map<String, String> response = new HashMap<>();
        response.put("message", "未登录或会话已过期");
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(response);
    }

    private UsersEntity requireActiveUserByEmail(String email) {
        return usersRepository.findByEmailAndIsDeletedFalse(email)
                .orElseThrow(() -> new RuntimeException("User not found"));
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> childMapOrNull(Map<String, Object> parent, String key) {
        if (parent == null) return null;
        Object value = parent.get(key);
        if (value instanceof Map) return (Map<String, Object>) value;
        return null;
    }

    private static Map<String, Object> mutableChildMap(Map<String, Object> parent, String key) {
        Map<String, Object> found = childMapOrNull(parent, key);
        return found == null ? new LinkedHashMap<>() : new LinkedHashMap<>(found);
    }
}
