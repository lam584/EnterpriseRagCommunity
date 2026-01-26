package com.example.EnterpriseRagCommunity.controller;

import com.example.EnterpriseRagCommunity.dto.access.TranslatePreferencesDTO;
import com.example.EnterpriseRagCommunity.dto.access.UpdateTranslatePreferencesRequest;
import com.example.EnterpriseRagCommunity.entity.access.UsersEntity;
import com.example.EnterpriseRagCommunity.repository.access.UsersRepository;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/account")
@RequiredArgsConstructor
@CrossOrigin(origins = {"http://localhost:5173", "http://127.0.0.1:5173"}, allowCredentials = "true")
public class AccountPreferencesController {

    private static final String DEFAULT_TARGET_LANGUAGE = "zh";

    private final UsersRepository usersRepository;

    @GetMapping("/preferences")
    public ResponseEntity<?> getPreferences() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || "anonymousUser".equals(auth.getPrincipal())) {
            Map<String, String> response = new HashMap<>();
            response.put("message", "未登录或会话已过期");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(response);
        }

        String email = auth.getName();
        UsersEntity user = usersRepository.findByEmailAndIsDeletedFalse(email)
                .orElseThrow(() -> new RuntimeException("User not found"));

        return ResponseEntity.ok(toTranslatePreferencesDto(user.getMetadata()));
    }

    @PutMapping("/preferences")
    public ResponseEntity<?> updatePreferences(@RequestBody @Valid UpdateTranslatePreferencesRequest req) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || "anonymousUser".equals(auth.getPrincipal())) {
            Map<String, String> response = new HashMap<>();
            response.put("message", "未登录或会话已过期");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(response);
        }

        String email = auth.getName();
        UsersEntity user = usersRepository.findByEmailAndIsDeletedFalse(email)
                .orElseThrow(() -> new RuntimeException("User not found"));

        Map<String, Object> metadata0 = user.getMetadata();
        Map<String, Object> metadata = (metadata0 == null) ? new LinkedHashMap<>() : new LinkedHashMap<>(metadata0);

        Object prefsObj = metadata.get("preferences");
        Map<String, Object> prefs0;
        if (prefsObj instanceof Map) {
            //noinspection unchecked
            prefs0 = (Map<String, Object>) prefsObj;
        } else {
            prefs0 = null;
        }
        Map<String, Object> prefs = (prefs0 == null) ? new LinkedHashMap<>() : new LinkedHashMap<>(prefs0);

        Object translateObj = prefs.get("translate");
        Map<String, Object> translate0;
        if (translateObj instanceof Map) {
            //noinspection unchecked
            translate0 = (Map<String, Object>) translateObj;
        } else {
            translate0 = null;
        }
        Map<String, Object> translate = (translate0 == null) ? new LinkedHashMap<>() : new LinkedHashMap<>(translate0);

        if (req.isTargetLanguagePresent()) {
            String tl = req.getTargetLanguage() == null ? null : req.getTargetLanguage().trim();
            if (tl != null && tl.isEmpty()) tl = null;
            translate.put("targetLanguage", tl);
        }

        if (req.isAutoTranslatePostsPresent()) {
            Boolean v = req.getAutoTranslatePosts();
            translate.put("autoTranslatePosts", v);
        }

        if (req.isAutoTranslateCommentsPresent()) {
            Boolean v = req.getAutoTranslateComments();
            translate.put("autoTranslateComments", v);
        }

        prefs.put("translate", translate);
        metadata.put("preferences", prefs);
        user.setMetadata(metadata);

        UsersEntity saved = usersRepository.save(user);
        return ResponseEntity.ok(toTranslatePreferencesDto(saved.getMetadata()));
    }

    @SuppressWarnings("unchecked")
    private static TranslatePreferencesDTO toTranslatePreferencesDto(Map<String, Object> metadata) {
        TranslatePreferencesDTO dto = new TranslatePreferencesDTO();
        Map<String, Object> prefs = null;
        if (metadata != null) {
            Object p = metadata.get("preferences");
            if (p instanceof Map) prefs = (Map<String, Object>) p;
        }

        Map<String, Object> translate = null;
        if (prefs != null) {
            Object t = prefs.get("translate");
            if (t instanceof Map) translate = (Map<String, Object>) t;
        }

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

        dto.setTargetLanguage(targetLanguage == null ? DEFAULT_TARGET_LANGUAGE : targetLanguage);
        dto.setAutoTranslatePosts(autoTranslatePosts != null && autoTranslatePosts);
        dto.setAutoTranslateComments(autoTranslateComments != null && autoTranslateComments);
        return dto;
    }
}

