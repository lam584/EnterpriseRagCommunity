package com.example.EnterpriseRagCommunity.controller.ai;

import com.example.EnterpriseRagCommunity.dto.ai.AssistantPreferencesDTO;
import com.example.EnterpriseRagCommunity.dto.ai.PortalChatConfigDTO;
import com.example.EnterpriseRagCommunity.dto.ai.UpdateAssistantPreferencesRequest;
import com.example.EnterpriseRagCommunity.entity.access.enums.AuditResult;
import com.example.EnterpriseRagCommunity.entity.access.UsersEntity;
import com.example.EnterpriseRagCommunity.repository.access.UsersRepository;
import com.example.EnterpriseRagCommunity.service.access.AuditDiffBuilder;
import com.example.EnterpriseRagCommunity.service.access.AuditLogWriter;
import com.example.EnterpriseRagCommunity.service.ai.PortalChatConfigService;
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
    private final AuditLogWriter auditLogWriter;
    private final AuditDiffBuilder auditDiffBuilder;

    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private PortalChatConfigService portalChatConfigService;

    @GetMapping
    public ResponseEntity<?> getPreferences() {
        UsersEntity user = resolveCurrentUserOrNull();
        if (user == null) return unauthorizedResponse();
        AssistantPreferencesDTO dto = toAssistantPreferencesDto(user.getMetadata());
        if (!isAssistantManualModelSelectionEnabled()) {
            dto.setDefaultProviderId(null);
            dto.setDefaultModel(null);
        }
        return ResponseEntity.ok(dto);
    }

    @PutMapping
    public ResponseEntity<?> updatePreferences(@RequestBody @Valid UpdateAssistantPreferencesRequest req) {
        UsersEntity user = resolveCurrentUserOrNull();
        if (user == null) return unauthorizedResponse();
        String email = user.getEmail();
        Map<String, Object> beforeAudit = summarizeAssistantPrefsForAudit(user.getMetadata());

        Map<String, Object> metadata0 = user.getMetadata();
        Map<String, Object> metadata = (metadata0 == null) ? new LinkedHashMap<>() : new LinkedHashMap<>(metadata0);

        Map<String, Object> prefs = ensureObjectMap(metadata, "preferences");
        Map<String, Object> assistant = ensureObjectMap(prefs, "assistant");
        boolean allowManualModelSelection = isAssistantManualModelSelectionEnabled();

        if (allowManualModelSelection && req.isDefaultProviderIdPresent()) {
            assistant.put("defaultProviderId", normalizeOptionalString(req.getDefaultProviderId()));
        }

        if (allowManualModelSelection && req.isDefaultModelPresent()) {
            assistant.put("defaultModel", normalizeOptionalString(req.getDefaultModel()));
        }

        if (!allowManualModelSelection) {
            assistant.put("defaultProviderId", null);
            assistant.put("defaultModel", null);
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
            assistant.put("ragTopK", v == null ? null : Math.clamp(v, 1, 50));
        }

        if (req.isStreamPresent()) {
            assistant.put("stream", Boolean.TRUE.equals(req.getStream()));
        }

        if (req.isTemperaturePresent()) {
            Double v = req.getTemperature();
            assistant.put("temperature", v == null ? null : Math.clamp(v, 0.0, 2.0));
        }

        if (req.isTopPPresent()) {
            Double v = req.getTopP();
            assistant.put("topP", v == null ? null : Math.clamp(v, 0.0, 1.0));
        }

        if (req.isDefaultSystemPromptPresent()) {
            String v = req.getDefaultSystemPrompt();
            assistant.put("defaultSystemPrompt", normalizeOptionalString(v));
        }

        prefs.put("assistant", assistant);
        metadata.put("preferences", prefs);
        user.setMetadata(metadata);

        UsersEntity saved = usersRepository.save(user);
        auditLogWriter.write(
                saved.getId(),
                email,
                "ASSISTANT_PREFERENCES_UPDATE",
                "USER",
                saved.getId(),
                AuditResult.SUCCESS,
                "更新 AI 助手偏好",
                null,
                auditDiffBuilder.build(beforeAudit, summarizeAssistantPrefsForAudit(saved.getMetadata()))
        );
        AssistantPreferencesDTO dto = toAssistantPreferencesDto(saved.getMetadata());
        if (!allowManualModelSelection) {
            dto.setDefaultProviderId(null);
            dto.setDefaultModel(null);
        }
        return ResponseEntity.ok(dto);
    }

    private boolean isAssistantManualModelSelectionEnabled() {
        PortalChatConfigService svc = this.portalChatConfigService;
        if (svc == null) return true;
        try {
            PortalChatConfigDTO cfg = svc.getConfigOrDefault();
            if (cfg == null || cfg.getAssistantChat() == null) return true;
            Boolean enabled = cfg.getAssistantChat().getAllowManualModelSelection();
            return enabled == null || enabled;
        } catch (Exception ignored) {
            return true;
        }
    }

    private UsersEntity resolveCurrentUserOrNull() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || "anonymousUser".equals(auth.getPrincipal())) {
            return null;
        }
        String email = auth.getName();
        return usersRepository.findByEmailAndIsDeletedFalse(email)
                .orElseThrow(() -> new RuntimeException("User not found"));
    }

    private static ResponseEntity<Map<String, String>> unauthorizedResponse() {
        Map<String, String> response = new HashMap<>();
        response.put("message", "未登录或会话已过期");
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(response);
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
    private static Map<String, Object> copyObjectMap(Object value) {
        if (value instanceof Map) {
            return new LinkedHashMap<>((Map<String, Object>) value);
        }
        return null;
    }

    private static AssistantPreferencesDTO toAssistantPreferencesDto(Map<String, Object> metadata) {
        AssistantPreferencesDTO dto = new AssistantPreferencesDTO();
        Map<String, Object> prefs = metadata == null ? null : copyObjectMap(metadata.get("preferences"));
        Map<String, Object> assistant = prefs == null ? null : copyObjectMap(prefs.get("assistant"));

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
            if (k instanceof Number) ragTopK = Math.clamp(((Number) k).intValue(), 1, 50);
            Object st = assistant.get("stream");
            if (st instanceof Boolean) stream = (Boolean) st;

            Object tmp = assistant.get("temperature");
            if (tmp instanceof Number) temperature = Math.clamp(((Number) tmp).doubleValue(), 0.0, 2.0);
            Object tp = assistant.get("topP");
            if (tp instanceof Number) topP = Math.clamp(((Number) tp).doubleValue(), 0.0, 1.0);
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

    private static Map<String, Object> summarizeAssistantPrefsForAudit(Map<String, Object> metadata) {
        Map<String, Object> m = new LinkedHashMap<>();
        Map<String, Object> prefs = metadata == null ? null : copyObjectMap(metadata.get("preferences"));
        Map<String, Object> assistant = prefs == null ? null : copyObjectMap(prefs.get("assistant"));
        if (assistant == null) return m;
        m.put("defaultProviderId", assistant.get("defaultProviderId"));
        m.put("defaultModel", assistant.get("defaultModel"));
        m.put("defaultDeepThink", assistant.get("defaultDeepThink"));
        m.put("autoLoadLastSession", assistant.get("autoLoadLastSession"));
        m.put("defaultUseRag", assistant.get("defaultUseRag"));
        m.put("ragTopK", assistant.get("ragTopK"));
        m.put("stream", assistant.get("stream"));
        m.put("temperature", assistant.get("temperature"));
        m.put("topP", assistant.get("topP"));
        Object dsp = assistant.get("defaultSystemPrompt");
        m.put("defaultSystemPromptLen", dsp == null ? 0 : String.valueOf(dsp).length());
        return m;
    }
}
