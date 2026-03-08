package com.example.EnterpriseRagCommunity.service.monitor;

import com.example.EnterpriseRagCommunity.entity.monitor.AppSettingsEntity;
import com.example.EnterpriseRagCommunity.repository.monitor.AppSettingsRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Service
@RequiredArgsConstructor
public class AppSettingsService {
    public static final String KEY_DEFAULT_REGISTER_ROLE_ID = "default_register_role_id";
    public static final String KEY_REGISTRATION_ENABLED = "registration_enabled";

    private final AppSettingsRepository appSettingsRepository;

    @Transactional(readOnly = true)
    public Optional<String> getString(String key) {
        if (key == null || key.isBlank()) return Optional.empty();
        return appSettingsRepository.findById(key).map(AppSettingsEntity::getV);
    }

    @Transactional(readOnly = true)
    public long getLongOrDefault(String key, long defaultValue) {
        String v = getString(key).orElse(null);
        if (v == null || v.isBlank()) return defaultValue;
        try {
            return Long.parseLong(v.trim());
        } catch (NumberFormatException ignored) {
            return defaultValue;
        }
    }

    @Transactional
    public void upsertString(String key, String value) {
        if (key == null || key.isBlank()) throw new IllegalArgumentException("key is required");
        if (value == null) throw new IllegalArgumentException("value is required");

        AppSettingsEntity entity = new AppSettingsEntity();
        entity.setK(key.trim());
        entity.setV(value.trim());
        appSettingsRepository.save(entity);
    }
}
