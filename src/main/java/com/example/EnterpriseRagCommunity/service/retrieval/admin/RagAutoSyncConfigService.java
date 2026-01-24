package com.example.EnterpriseRagCommunity.service.retrieval.admin;

import com.example.EnterpriseRagCommunity.dto.retrieval.RagAutoSyncConfigDTO;
import com.example.EnterpriseRagCommunity.service.monitor.AppSettingsService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class RagAutoSyncConfigService {

    public static final String KEY_ENABLED = "retrieval.rag.autoSync.enabled";
    public static final String KEY_INTERVAL_SECONDS = "retrieval.rag.autoSync.intervalSeconds";

    private final AppSettingsService appSettingsService;

    @Transactional(readOnly = true)
    public RagAutoSyncConfigDTO getConfig() {
        RagAutoSyncConfigDTO dto = new RagAutoSyncConfigDTO();
        String enabled = appSettingsService.getString(KEY_ENABLED).orElse("true");
        dto.setEnabled(Boolean.parseBoolean(enabled));
        dto.setIntervalSeconds(appSettingsService.getLongOrDefault(KEY_INTERVAL_SECONDS, 30));
        return dto;
    }

    @Transactional
    public RagAutoSyncConfigDTO updateConfig(RagAutoSyncConfigDTO payload) {
        if (payload == null) throw new IllegalArgumentException("payload is required");
        if (payload.getEnabled() == null) throw new IllegalArgumentException("enabled 不能为空");
        long interval = payload.getIntervalSeconds() == null ? 30 : payload.getIntervalSeconds();
        interval = Math.max(5, Math.min(3600, interval));

        appSettingsService.upsertString(KEY_ENABLED, payload.getEnabled() ? "true" : "false");
        appSettingsService.upsertString(KEY_INTERVAL_SECONDS, Long.toString(interval));

        RagAutoSyncConfigDTO out = new RagAutoSyncConfigDTO();
        out.setEnabled(payload.getEnabled());
        out.setIntervalSeconds(interval);
        return out;
    }
}
