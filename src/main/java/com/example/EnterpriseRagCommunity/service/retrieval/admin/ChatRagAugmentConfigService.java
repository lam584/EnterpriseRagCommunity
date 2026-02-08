package com.example.EnterpriseRagCommunity.service.retrieval.admin;

import com.example.EnterpriseRagCommunity.dto.retrieval.ChatRagAugmentConfigDTO;
import com.example.EnterpriseRagCommunity.service.monitor.AppSettingsService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;

@Service
@RequiredArgsConstructor
public class ChatRagAugmentConfigService {

    public static final String KEY_CONFIG_JSON = "retrieval.chat.rag.config.json";

    private final AppSettingsService appSettingsService;
    private final ObjectMapper objectMapper;

    @Transactional(readOnly = true)
    public ChatRagAugmentConfigDTO getConfig() {
        String json = appSettingsService.getString(KEY_CONFIG_JSON).orElse(null);
        if (json == null || json.isBlank()) return defaultConfig();
        try {
            ChatRagAugmentConfigDTO cfg = objectMapper.readValue(json, ChatRagAugmentConfigDTO.class);
            return normalize(cfg);
        } catch (Exception e) {
            return defaultConfig();
        }
    }

    @Transactional(readOnly = true)
    public ChatRagAugmentConfigDTO getConfigOrDefault() {
        return getConfig();
    }

    @Transactional
    public ChatRagAugmentConfigDTO updateConfig(ChatRagAugmentConfigDTO payload) {
        if (payload == null) throw new IllegalArgumentException("payload is required");
        ChatRagAugmentConfigDTO cfg = normalize(payload);
        try {
            String json = objectMapper.writeValueAsString(cfg);
            appSettingsService.upsertString(KEY_CONFIG_JSON, json);
        } catch (Exception e) {
            throw new IllegalStateException("保存配置失败: " + e.getMessage(), e);
        }
        return cfg;
    }

    public ChatRagAugmentConfigDTO defaultConfig() {
        ChatRagAugmentConfigDTO dto = new ChatRagAugmentConfigDTO();
        dto.setEnabled(true);
        dto.setCommentsEnabled(true);
        dto.setCommentTopK(20);

        dto.setMaxPosts(6);
        dto.setPerPostMaxCommentChunks(2);

        dto.setIncludePostContentPolicy("ON_COMMENT_HIT");
        dto.setPostContentMaxTokens(1200);
        dto.setCommentChunkMaxTokens(400);

        dto.setDebugEnabled(false);
        dto.setDebugMaxChars(4000);
        return dto;
    }

    private ChatRagAugmentConfigDTO normalize(ChatRagAugmentConfigDTO in) {
        ChatRagAugmentConfigDTO dto = (in == null) ? new ChatRagAugmentConfigDTO() : in;
        ChatRagAugmentConfigDTO def = defaultConfig();

        dto.setEnabled(dto.getEnabled() == null || Boolean.TRUE.equals(dto.getEnabled()));
        dto.setCommentsEnabled(dto.getCommentsEnabled() == null || Boolean.TRUE.equals(dto.getCommentsEnabled()));
        dto.setCommentTopK(clampInt(dto.getCommentTopK(), 1, 200, def.getCommentTopK()));

        dto.setMaxPosts(clampInt(dto.getMaxPosts(), 1, 100, def.getMaxPosts()));
        dto.setPerPostMaxCommentChunks(clampInt(dto.getPerPostMaxCommentChunks(), 0, 50, def.getPerPostMaxCommentChunks()));

        String p = trimOrNull(dto.getIncludePostContentPolicy());
        if (p == null) p = def.getIncludePostContentPolicy();
        p = p.trim().toUpperCase(Locale.ROOT);
        if (!p.equals("ALWAYS") && !p.equals("ON_COMMENT_HIT") && !p.equals("NEVER")) {
            p = def.getIncludePostContentPolicy();
        }
        dto.setIncludePostContentPolicy(p);

        dto.setPostContentMaxTokens(clampInt(dto.getPostContentMaxTokens(), 50, 200_000, def.getPostContentMaxTokens()));
        dto.setCommentChunkMaxTokens(clampInt(dto.getCommentChunkMaxTokens(), 20, 200_000, def.getCommentChunkMaxTokens()));

        dto.setDebugEnabled(Boolean.TRUE.equals(dto.getDebugEnabled()));
        dto.setDebugMaxChars(clampInt(dto.getDebugMaxChars(), 0, 200_000, def.getDebugMaxChars()));

        return dto;
    }

    private static int clampInt(Integer v, int min, int max, int def) {
        int x = v == null ? def : v;
        if (x < min) x = min;
        if (x > max) x = max;
        return x;
    }

    private static String trimOrNull(String s) {
        if (s == null) return null;
        String t = s.trim();
        return t.isBlank() ? null : t;
    }
}

