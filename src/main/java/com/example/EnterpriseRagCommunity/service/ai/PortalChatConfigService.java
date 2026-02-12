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

    private static final String DEFAULT_ASSISTANT_SYSTEM_PROMPT = "你是一个严谨、专业的中文助手。";
    private static final String DEFAULT_ASSISTANT_DEEP_THINK_SYSTEM_PROMPT =
            "你是一个严谨、专业的中文助手。请在回答前进行更充分的推理与自检，输出更可靠、结构化的结论；不确定时说明不确定并给出验证建议。";

    private static final String DEFAULT_POST_COMPOSE_SYSTEM_PROMPT = "你是一个严谨、专业的中文助手。";
    private static final String DEFAULT_POST_COMPOSE_DEEP_THINK_SYSTEM_PROMPT =
            "你是一个严谨、专业的中文助手。请在回答前进行更充分的推理与自检，输出更可靠、结构化的结论；不确定时说明不确定并给出验证建议。";

    private static final String DEFAULT_POST_COMPOSE_COMPOSE_SYSTEM_PROMPT =
            "你是一名发帖编辑助手。你要帮助用户完成“可发布的 Markdown 正文”，并在必要时与用户沟通确认细节。\n" +
            "你必须严格遵守以下输出协议（非常重要）：\n" +
            "1) 你只允许输出两类内容块，并且所有输出必须被包裹在其中之一：\n" +
            "   - <chat>...</chat>：与用户沟通（提问、确认、解释、澄清）。这部分只会显示在聊天窗口，不会写入正文。\n" +
            "   - <post>...</post>：帖子最终 Markdown 正文。这部分只会写入正文编辑框，不会显示在聊天窗口。\n" +
            "2) 当信息不足、需要用户确认/补充时：只输出 <chat>，不要输出 <post>。\n" +
            "3) 当你输出 <post> 时：内容必须是完整、可发布的最终 Markdown 正文；不要解释你的思考过程；不要使用```包裹正文。\n" +
            "4) 不要杜撰事实；缺少信息时在 <chat> 提问，或在 <post> 中用占位符明确标记缺失信息。\n" +
            "5) 若用户明确要求“直接写入正文/直接改写/不要提问/给出最终稿”，你必须直接输出 <post>，不要继续在 <chat> 中拉扯确认。\n" +
            "6) 标签必须使用半角尖括号：<post>/<chat>，不要转义为 &lt;post&gt;，也不要使用全角括号。\n" +
            "7) 除 <chat> 或 <post> 之外不要输出任何其他文本。\n";

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
        a.setSystemPrompt(normalizeRequiredString(
                ia == null ? null : ia.getSystemPrompt(),
                DEFAULT_ASSISTANT_SYSTEM_PROMPT,
                "assistantChat.systemPrompt",
                strict
        ));
        a.setDeepThinkSystemPrompt(normalizeRequiredString(
                ia == null ? null : ia.getDeepThinkSystemPrompt(),
                DEFAULT_ASSISTANT_DEEP_THINK_SYSTEM_PROMPT,
                "assistantChat.deepThinkSystemPrompt",
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
        p.setSystemPrompt(normalizeRequiredString(
                ip == null ? null : ip.getSystemPrompt(),
                DEFAULT_POST_COMPOSE_SYSTEM_PROMPT,
                "postComposeAssistant.systemPrompt",
                strict
        ));
        p.setDeepThinkSystemPrompt(normalizeRequiredString(
                ip == null ? null : ip.getDeepThinkSystemPrompt(),
                DEFAULT_POST_COMPOSE_DEEP_THINK_SYSTEM_PROMPT,
                "postComposeAssistant.deepThinkSystemPrompt",
                strict
        ));
        p.setComposeSystemPrompt(normalizeRequiredString(
                ip == null ? null : ip.getComposeSystemPrompt(),
                DEFAULT_POST_COMPOSE_COMPOSE_SYSTEM_PROMPT,
                "postComposeAssistant.composeSystemPrompt",
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
