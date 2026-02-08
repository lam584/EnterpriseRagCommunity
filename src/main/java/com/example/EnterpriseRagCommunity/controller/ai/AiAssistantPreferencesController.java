package com.example.EnterpriseRagCommunity.controller.ai;

import com.example.EnterpriseRagCommunity.dto.ai.AssistantPreferencesDTO;
import com.example.EnterpriseRagCommunity.dto.ai.UpdateAssistantPreferencesRequest;
import com.example.EnterpriseRagCommunity.entity.access.UsersEntity;
import com.example.EnterpriseRagCommunity.repository.access.UsersRepository;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/ai/assistant/preferences")
@RequiredArgsConstructor
public class AiAssistantPreferencesController {

    private static final int DEFAULT_RAG_TOP_K = 6;

    private final UsersRepository usersRepository;

    @GetMapping
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

        return ResponseEntity.ok(toAssistantPreferencesDto(user.getMetadata()));
    }

    @PutMapping
    public ResponseEntity<?> updatePreferences(@RequestBody @Valid UpdateAssistantPreferencesRequest req) {
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

        Map<String, Object> prefs = ensureObjectMap(metadata, "preferences");
        Map<String, Object> assistant = ensureObjectMap(prefs, "assistant");

        if (req.isDefaultProviderIdPresent()) {
            assistant.put("defaultProviderId", normalizeOptionalString(req.getDefaultProviderId()));
        }

        if (req.isDefaultModelPresent()) {
            assistant.put("defaultModel", normalizeOptionalString(req.getDefaultModel()));
        }

        if (req.isDefaultDeepThinkPresent()) {
            assistant.put("defaultDeepThink", Boolean.TRUE.equals(req.getDefaultDeepThink()));
        }

        if (req.isAutoLoadLastSessionPresent()) {
            assistant.put("autoLoadLastSession", Boolean.TRUE.equals(req.getAutoLoadLastSession()));
        }

        if (req.isDefaultUseRagPresent()) {
            assistant.put("defaultUseRag", Boolean.TRUE.equals(req.getDefaultUseRag()));
        }

        if (req.isRagTopKPresent()) {
            Integer v = req.getRagTopK();
            assistant.put("ragTopK", v == null ? null : Math.max(1, Math.min(50, v)));
        }

        if (req.isStreamPresent()) {
            assistant.put("stream", Boolean.TRUE.equals(req.getStream()));
        }

        if (req.isTemperaturePresent()) {
            Double v = req.getTemperature();
            assistant.put("temperature", v == null ? null : Math.max(0.0, Math.min(2.0, v)));
        }

        if (req.isTopPPresent()) {
            Double v = req.getTopP();
            assistant.put("topP", v == null ? null : Math.max(0.0, Math.min(1.0, v)));
        }

        if (req.isDefaultSystemPromptPresent()) {
            String v = req.getDefaultSystemPrompt();
            assistant.put("defaultSystemPrompt", normalizeOptionalString(v));
        }

        prefs.put("assistant", assistant);
        metadata.put("preferences", prefs);
        user.setMetadata(metadata);

        UsersEntity saved = usersRepository.save(user);
        return ResponseEntity.ok(toAssistantPreferencesDto(saved.getMetadata()));
    }

    private static String normalizeOptionalString(String s) {
        if (s == null) return null;
        String t = s.trim();
        return t.isEmpty() ? null : t;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> ensureObjectMap(Map<String, Object> root, String key) {
        Object v = root.get(key);
        if (v instanceof Map) {
            return new LinkedHashMap<>((Map<String, Object>) v);
        }
        return new LinkedHashMap<>();
    }

    @SuppressWarnings("unchecked")
    private static AssistantPreferencesDTO toAssistantPreferencesDto(Map<String, Object> metadata) {
        AssistantPreferencesDTO dto = new AssistantPreferencesDTO();

        Map<String, Object> prefs = null;
        if (metadata != null) {
            Object p = metadata.get("preferences");
            if (p instanceof Map) prefs = (Map<String, Object>) p;
        }

        Map<String, Object> assistant = null;
        if (prefs != null) {
            Object a = prefs.get("assistant");
            if (a instanceof Map) assistant = (Map<String, Object>) a;
        }

        String defaultProviderId = null;
        String defaultModel = null;
        boolean defaultDeepThink = false;
        boolean autoLoadLastSession = false;
        boolean defaultUseRag = true;
        int ragTopK = DEFAULT_RAG_TOP_K;
        boolean stream = true;
        Double temperature = null;
        Double topP = null;
        String defaultSystemPrompt = null;

        if (assistant != null) {
            Object pid = assistant.get("defaultProviderId");
            if (pid != null) {
                String s = String.valueOf(pid).trim();
                if (StringUtils.hasText(s)) defaultProviderId = s;
            }
            Object mdl = assistant.get("defaultModel");
            if (mdl != null) {
                String s = String.valueOf(mdl).trim();
                if (StringUtils.hasText(s)) defaultModel = s;
            }
            Object dt = assistant.get("defaultDeepThink");
            if (dt instanceof Boolean) defaultDeepThink = (Boolean) dt;
            Object al = assistant.get("autoLoadLastSession");
            if (al instanceof Boolean) autoLoadLastSession = (Boolean) al;
            Object ur = assistant.get("defaultUseRag");
            if (ur instanceof Boolean) defaultUseRag = (Boolean) ur;
            Object k = assistant.get("ragTopK");
            if (k instanceof Number) ragTopK = Math.max(1, Math.min(50, ((Number) k).intValue()));
            Object st = assistant.get("stream");
            if (st instanceof Boolean) stream = (Boolean) st;

            Object tmp = assistant.get("temperature");
            if (tmp instanceof Number) temperature = Math.max(0.0, Math.min(2.0, ((Number) tmp).doubleValue()));
            Object tp = assistant.get("topP");
            if (tp instanceof Number) topP = Math.max(0.0, Math.min(1.0, ((Number) tp).doubleValue()));
            Object dsp = assistant.get("defaultSystemPrompt");
            if (dsp != null) {
                String s = String.valueOf(dsp).trim();
                if (StringUtils.hasText(s)) defaultSystemPrompt = s;
            }
        }

        dto.setDefaultProviderId(defaultProviderId);
        dto.setDefaultModel(defaultModel);
        dto.setDefaultDeepThink(defaultDeepThink);
        dto.setAutoLoadLastSession(autoLoadLastSession);
        dto.setDefaultUseRag(defaultUseRag);
        dto.setRagTopK(ragTopK);
        dto.setStream(stream);
        dto.setTemperature(temperature);
        dto.setTopP(topP);
        dto.setDefaultSystemPrompt(defaultSystemPrompt);
        return dto;
    }
}
