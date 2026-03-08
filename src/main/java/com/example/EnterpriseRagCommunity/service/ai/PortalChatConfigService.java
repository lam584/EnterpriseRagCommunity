package com.example.EnterpriseRagCommunity.service.ai;

import com.example.EnterpriseRagCommunity.dto.ai.PortalChatConfigDTO;
import com.example.EnterpriseRagCommunity.service.monitor.AppSettingsService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class PortalChatConfigService {
    public static final String KEY_PORTAL_CHAT_CONFIG_V1 = "portal_chat_config_v1";

    private static final int DEFAULT_ASSISTANT_HISTORY_LIMIT = 20;
    private static final int DEFAULT_RAG_TOP_K = 6;
    private static final int DEFAULT_POST_COMPOSE_HISTORY_LIMIT = 20;

    private static final String DEFAULT_ASSISTANT_SYSTEM_PROMPT_CODE = "PORTAL_CHAT_ASSISTANT";
    private static final String DEFAULT_ASSISTANT_DEEP_THINK_SYSTEM_PROMPT_CODE = "PORTAL_CHAT_ASSISTANT_DEEP_THINK";

    private static final String DEFAULT_POST_COMPOSE_SYSTEM_PROMPT_CODE = "PORTAL_POST_COMPOSE";
    private static final String DEFAULT_POST_COMPOSE_DEEP_THINK_SYSTEM_PROMPT_CODE = "PORTAL_POST_COMPOSE_DEEP_THINK";
    private static final String DEFAULT_POST_COMPOSE_COMPOSE_SYSTEM_PROMPT_CODE = "PORTAL_POST_COMPOSE_PROTOCOL";

    private final AppSettingsService appSettingsService;
    private final ObjectMapper objectMapper;

    @Transactional(readOnly = true)
    public PortalChatConfigDTO getAdminConfig() {
        return getConfigOrDefault();
    }

    @Transactional(readOnly = true)
    public PortalChatConfigDTO getConfigOrDefault() {
        String raw = appSettingsService.getString(KEY_PORTAL_CHAT_CONFIG_V1).orElse(null);
        PortalChatConfigDTO parsed = null;
        if (raw != null && !raw.isBlank()) {
            try {
                parsed = objectMapper.readValue(raw, PortalChatConfigDTO.class);
            } catch (Exception ignored) {
                parsed = null;
            }
        }
        return normalizeAndValidate(parsed, false);
    }

    @Transactional
    public PortalChatConfigDTO upsertAdminConfig(PortalChatConfigDTO payload) {
        PortalChatConfigDTO normalized = normalizeAndValidate(payload, true);
        try {
            String json = objectMapper.writeValueAsString(normalized);
            appSettingsService.upsertString(KEY_PORTAL_CHAT_CONFIG_V1, json);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("配置序列化失败");
        }
        return normalized;
    }

    private PortalChatConfigDTO normalizeAndValidate(PortalChatConfigDTO input, boolean strict) {
        PortalChatConfigDTO out = new PortalChatConfigDTO();

        PortalChatConfigDTO.AssistantChatConfigDTO a = new PortalChatConfigDTO.AssistantChatConfigDTO();
        PortalChatConfigDTO.AssistantChatConfigDTO ia = input == null ? null : input.getAssistantChat();
        a.setProviderId(normalizeOptionalString(ia == null ? null : ia.getProviderId()));
        a.setModel(normalizeOptionalString(ia == null ? null : ia.getModel()));
        a.setTemperature(normalizeOptionalNumber(ia == null ? null : ia.getTemperature(), 0.0, 2.0, "assistantChat.temperature", strict));
        a.setTopP(normalizeOptionalNumber(ia == null ? null : ia.getTopP(), 0.0, 1.0, "assistantChat.topP", strict));
        a.setHistoryLimit(normalizeOptionalInt(ia == null ? null : ia.getHistoryLimit(), 1, 200, DEFAULT_ASSISTANT_HISTORY_LIMIT));
        a.setDefaultDeepThink(ia == null || ia.getDefaultDeepThink() == null ? Boolean.FALSE : Boolean.TRUE.equals(ia.getDefaultDeepThink()));
        a.setDefaultUseRag(ia == null || ia.getDefaultUseRag() == null ? Boolean.TRUE : Boolean.TRUE.equals(ia.getDefaultUseRag()));
        a.setRagTopK(normalizeOptionalInt(ia == null ? null : ia.getRagTopK(), 1, 50, DEFAULT_RAG_TOP_K));
        a.setDefaultStream(ia == null || ia.getDefaultStream() == null ? Boolean.TRUE : Boolean.TRUE.equals(ia.getDefaultStream()));
        a.setSystemPromptCode(normalizeRequiredString(
                ia == null ? null : ia.getSystemPromptCode(),
                DEFAULT_ASSISTANT_SYSTEM_PROMPT_CODE,
                "assistantChat.systemPromptCode",
                strict
        ));
        a.setDeepThinkSystemPromptCode(normalizeRequiredString(
                ia == null ? null : ia.getDeepThinkSystemPromptCode(),
                DEFAULT_ASSISTANT_DEEP_THINK_SYSTEM_PROMPT_CODE,
                "assistantChat.deepThinkSystemPromptCode",
                strict
        ));
        out.setAssistantChat(a);

        PortalChatConfigDTO.PostComposeAssistantConfigDTO p = new PortalChatConfigDTO.PostComposeAssistantConfigDTO();
        PortalChatConfigDTO.PostComposeAssistantConfigDTO ip = input == null ? null : input.getPostComposeAssistant();
        p.setProviderId(normalizeOptionalString(ip == null ? null : ip.getProviderId()));
        p.setModel(normalizeOptionalString(ip == null ? null : ip.getModel()));
        p.setTemperature(normalizeOptionalNumber(ip == null ? null : ip.getTemperature(), 0.0, 2.0, "postComposeAssistant.temperature", strict));
        p.setTopP(normalizeOptionalNumber(ip == null ? null : ip.getTopP(), 0.0, 1.0, "postComposeAssistant.topP", strict));
        p.setChatHistoryLimit(normalizeOptionalInt(ip == null ? null : ip.getChatHistoryLimit(), 1, 200, DEFAULT_POST_COMPOSE_HISTORY_LIMIT));
        p.setDefaultDeepThink(ip == null || ip.getDefaultDeepThink() == null ? Boolean.FALSE : Boolean.TRUE.equals(ip.getDefaultDeepThink()));
        p.setSystemPromptCode(normalizeRequiredString(
                ip == null ? null : ip.getSystemPromptCode(),
                DEFAULT_POST_COMPOSE_SYSTEM_PROMPT_CODE,
                "postComposeAssistant.systemPromptCode",
                strict
        ));
        p.setDeepThinkSystemPromptCode(normalizeRequiredString(
                ip == null ? null : ip.getDeepThinkSystemPromptCode(),
                DEFAULT_POST_COMPOSE_DEEP_THINK_SYSTEM_PROMPT_CODE,
                "postComposeAssistant.deepThinkSystemPromptCode",
                strict
        ));
        p.setComposeSystemPromptCode(normalizeRequiredString(
                ip == null ? null : ip.getComposeSystemPromptCode(),
                DEFAULT_POST_COMPOSE_COMPOSE_SYSTEM_PROMPT_CODE,
                "postComposeAssistant.composeSystemPromptCode",
                strict
        ));
        out.setPostComposeAssistant(p);

        return out;
    }

    private static String normalizeOptionalString(String v) {
        if (v == null) return null;
        String t = v.trim();
        return t.isEmpty() ? null : t;
    }

    private static Double normalizeOptionalNumber(Double v, double min, double max, String field, boolean strict) {
        if (v == null) return null;
        if (!Double.isFinite(v)) {
            if (strict) throw new IllegalArgumentException(field + " 不是有效数字");
            return null;
        }
        if (v < min || v > max) {
            if (strict) throw new IllegalArgumentException(field + " 超出范围");
            return Math.max(min, Math.min(max, v));
        }
        return v;
    }

    private static Integer normalizeOptionalInt(Integer v, int min, int max, int defaultValue) {
        if (v == null) return defaultValue;
        if (v < min) return min;
        if (v > max) return max;
        return v;
    }

    private static String normalizeRequiredString(String v, String defaultValue, String field, boolean strict) {
        if (v == null) return defaultValue;
        String t = v.trim();
        if (t.isEmpty()) {
            if (strict) throw new IllegalArgumentException(field + " 不能为空");
            return defaultValue;
        }
        if (t.length() > 20000) {
            if (strict) throw new IllegalArgumentException(field + " 过长（>20000），请精简");
            return t.substring(0, 20000);
        }
        return t;
    }
}
