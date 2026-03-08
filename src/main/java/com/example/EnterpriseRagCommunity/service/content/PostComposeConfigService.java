package com.example.EnterpriseRagCommunity.service.content;

import com.example.EnterpriseRagCommunity.dto.content.PostComposeConfigDTO;
import com.example.EnterpriseRagCommunity.service.monitor.AppSettingsService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class PostComposeConfigService {

    public static final String KEY_CONFIG_JSON = "posts.compose.config.json";

    private final AppSettingsService appSettingsService;
    private final ObjectMapper objectMapper;

    @Transactional(readOnly = true)
    public PostComposeConfigDTO getConfig() {
        String json = appSettingsService.getString(KEY_CONFIG_JSON).orElse(null);
        if (json == null || json.isBlank()) return defaultConfig();
        try {
            PostComposeConfigDTO cfg = objectMapper.readValue(json, PostComposeConfigDTO.class);
            return normalize(cfg);
        } catch (Exception e) {
            return defaultConfig();
        }
    }

    @Transactional
    public PostComposeConfigDTO updateConfig(PostComposeConfigDTO payload) {
        if (payload == null) throw new IllegalArgumentException("payload is required");
        PostComposeConfigDTO cfg = normalize(payload);
        try {
            String json = objectMapper.writeValueAsString(cfg);
            appSettingsService.upsertString(KEY_CONFIG_JSON, json);
        } catch (Exception e) {
            throw new IllegalStateException("保存配置失败: " + e.getMessage(), e);
        }
        return cfg;
    }

    public PostComposeConfigDTO defaultConfig() {
        PostComposeConfigDTO dto = new PostComposeConfigDTO();
        dto.setRequireTitle(false);
        dto.setRequireTags(false);
        dto.setMaxAttachments(10);
        dto.setMaxContentChars(500_000);
        dto.setChunkThresholdChars(20_000);
        dto.setBypassAttachmentLimitWhenChunked(true);
        return dto;
    }

    private static PostComposeConfigDTO normalize(PostComposeConfigDTO in) {
        PostComposeConfigDTO dto = in == null ? new PostComposeConfigDTO() : in;

        dto.setRequireTitle(Boolean.TRUE.equals(dto.getRequireTitle()));
        dto.setRequireTags(Boolean.TRUE.equals(dto.getRequireTags()));
        dto.setBypassAttachmentLimitWhenChunked(dto.getBypassAttachmentLimitWhenChunked() == null || dto.getBypassAttachmentLimitWhenChunked());

        Integer maxAttachments = dto.getMaxAttachments();
        if (maxAttachments == null) maxAttachments = 10;
        if (maxAttachments < 0) maxAttachments = 0;
        if (maxAttachments > 500) maxAttachments = 500;
        dto.setMaxAttachments(maxAttachments);

        Integer maxContentChars = dto.getMaxContentChars();
        if (maxContentChars == null) maxContentChars = 500_000;
        if (maxContentChars < 0) maxContentChars = 0;
        if (maxContentChars > 5_000_000) maxContentChars = 5_000_000;
        dto.setMaxContentChars(maxContentChars);

        Integer chunkThresholdChars = dto.getChunkThresholdChars();
        if (chunkThresholdChars == null) chunkThresholdChars = 20_000;
        if (chunkThresholdChars <= 0) {
            dto.setChunkThresholdChars(null);
        } else {
            if (chunkThresholdChars > 5_000_000) chunkThresholdChars = 5_000_000;
            dto.setChunkThresholdChars(chunkThresholdChars);
        }
        return dto;
    }
}

