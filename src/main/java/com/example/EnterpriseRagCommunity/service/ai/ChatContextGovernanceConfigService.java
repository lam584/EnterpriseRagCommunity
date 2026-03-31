package com.example.EnterpriseRagCommunity.service.ai;

import com.example.EnterpriseRagCommunity.dto.ai.ChatContextGovernanceConfigDTO;
import com.example.EnterpriseRagCommunity.service.monitor.AppSettingsService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ChatContextGovernanceConfigService {
    public static final String KEY_CONFIG_JSON = "ai.chat.context.governance.config.json";

    private final AppSettingsService appSettingsService;
    private final ObjectMapper objectMapper;

    @Transactional(readOnly = true)
    public ChatContextGovernanceConfigDTO getConfig() {
        String json = appSettingsService.getString(KEY_CONFIG_JSON).orElse(null);
        if (json == null || json.isBlank()) return defaultConfig();
        try {
            ChatContextGovernanceConfigDTO cfg = objectMapper.readValue(json, ChatContextGovernanceConfigDTO.class);
            return normalize(cfg);
        } catch (Exception e) {
            return defaultConfig();
        }
    }

    @Transactional(readOnly = true)
    public ChatContextGovernanceConfigDTO getConfigOrDefault() {
        return getConfig();
    }

    @Transactional
    public ChatContextGovernanceConfigDTO updateConfig(ChatContextGovernanceConfigDTO payload) {
        if (payload == null) throw new IllegalArgumentException("payload is required");
        ChatContextGovernanceConfigDTO cfg = normalize(payload);
        try {
            String json = objectMapper.writeValueAsString(cfg);
            appSettingsService.upsertString(KEY_CONFIG_JSON, json);
        } catch (Exception e) {
            throw new IllegalStateException("保存配置失败: " + e.getMessage(), e);
        }
        return cfg;
    }

    public ChatContextGovernanceConfigDTO defaultConfig() {
        ChatContextGovernanceConfigDTO dto = new ChatContextGovernanceConfigDTO();
        dto.setEnabled(true);

        dto.setMaxPromptTokens(24000);
        dto.setReserveAnswerTokens(2000);
        dto.setMaxPromptChars(200000);
        dto.setPerMessageMaxTokens(6000);
        dto.setKeepLastMessages(20);
        dto.setAllowDropRagContext(true);

        dto.setCompressionEnabled(true);
        dto.setCompressionTriggerTokens(18000);
        dto.setCompressionKeepLastMessages(8);
        dto.setCompressionPerMessageSnippetChars(300);
        dto.setCompressionMaxChars(12000);

        dto.setMaxFiles(10);
        dto.setPerFileMaxChars(6000);
        dto.setTotalFilesMaxChars(24000);

        dto.setLogEnabled(true);
        dto.setLogSampleRate(1.0);
        dto.setLogMaxDays(30);
        return dto;
    }

    private ChatContextGovernanceConfigDTO normalize(ChatContextGovernanceConfigDTO in) {
        ChatContextGovernanceConfigDTO dto = in == null ? new ChatContextGovernanceConfigDTO() : in;
        ChatContextGovernanceConfigDTO def = defaultConfig();

        dto.setEnabled(dto.getEnabled() == null || dto.getEnabled());

        dto.setMaxPromptTokens(clampInt(dto.getMaxPromptTokens(), 1000, 1_000_000, def.getMaxPromptTokens()));
        dto.setReserveAnswerTokens(clampInt(dto.getReserveAnswerTokens(), 0, 1_000_000, def.getReserveAnswerTokens()));
        dto.setMaxPromptChars(clampInt(dto.getMaxPromptChars(), 1000, 2_000_000, def.getMaxPromptChars()));
        dto.setPerMessageMaxTokens(clampInt(dto.getPerMessageMaxTokens(), 100, 200_000, def.getPerMessageMaxTokens()));
        dto.setKeepLastMessages(clampInt(dto.getKeepLastMessages(), 0, 200, def.getKeepLastMessages()));
        dto.setAllowDropRagContext(dto.getAllowDropRagContext() == null || dto.getAllowDropRagContext());

        dto.setCompressionEnabled(dto.getCompressionEnabled() == null || dto.getCompressionEnabled());
        dto.setCompressionTriggerTokens(clampInt(dto.getCompressionTriggerTokens(), 500, 1_000_000, def.getCompressionTriggerTokens()));
        dto.setCompressionKeepLastMessages(clampInt(dto.getCompressionKeepLastMessages(), 0, 200, def.getCompressionKeepLastMessages()));
        dto.setCompressionPerMessageSnippetChars(clampInt(dto.getCompressionPerMessageSnippetChars(), 10, 10000, def.getCompressionPerMessageSnippetChars()));
        dto.setCompressionMaxChars(clampInt(dto.getCompressionMaxChars(), 200, 200000, def.getCompressionMaxChars()));

        dto.setMaxFiles(clampInt(dto.getMaxFiles(), 0, 50, def.getMaxFiles()));
        dto.setPerFileMaxChars(clampInt(dto.getPerFileMaxChars(), 100, 200000, def.getPerFileMaxChars()));
        dto.setTotalFilesMaxChars(clampInt(dto.getTotalFilesMaxChars(), 100, 2_000_000, def.getTotalFilesMaxChars()));

        dto.setLogEnabled(dto.getLogEnabled() == null || dto.getLogEnabled());
        dto.setLogSampleRate(clampDouble(dto.getLogSampleRate(), 0.0, 1.0, def.getLogSampleRate()));
        dto.setLogMaxDays(clampInt(dto.getLogMaxDays(), 1, 3650, def.getLogMaxDays()));
        return dto;
    }

    private static int clampInt(Integer v, int min, int max, int def) {
        int x = v == null ? def : v;
        if (x < min) x = min;
        if (x > max) x = max;
        return x;
    }

    private static double clampDouble(Double v, double min, double max, double def) {
        double x = v == null ? def : v;
        if (Double.isNaN(x) || Double.isInfinite(x)) x = def;
        if (x < min) x = min;
        if (x > max) x = max;
        return x;
    }
}
