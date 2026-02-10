package com.example.EnterpriseRagCommunity.service.config;

import com.example.EnterpriseRagCommunity.entity.config.SystemConfigurationEntity;
import com.example.EnterpriseRagCommunity.repository.config.SystemConfigurationRepository;
import com.example.EnterpriseRagCommunity.utils.AesGcmUtils;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class SystemConfigurationService {

    private static final Logger logger = LoggerFactory.getLogger(SystemConfigurationService.class);

    @Value("${APP_MASTER_KEY}")
    private String masterKey;

    private final SystemConfigurationRepository repository;
    private final AesGcmUtils aesUtils;
    private final Map<String, String> configCache = new ConcurrentHashMap<>();

    public SystemConfigurationService(SystemConfigurationRepository repository, AesGcmUtils aesUtils) {
        this.repository = repository;
        this.aesUtils = aesUtils;
    }

    @PostConstruct
    public void init() {
        if (masterKey == null || masterKey.isBlank()) {
            throw new IllegalStateException("Critical Error: APP_MASTER_KEY environment variable is not set! The application cannot start without the master encryption key.");
        }
        refreshCache();
    }

    public void refreshCache() {
        try {
            configCache.clear();
            repository.findAll().forEach(entity -> {
                try {
                    String value = entity.getConfigValue();
                    if (entity.isEncrypted()) {
                        value = aesUtils.decrypt(value, masterKey);
                    }
                    if (value != null) {
                        configCache.put(entity.getConfigKey(), value);
                    }
                } catch (Exception e) {
                    logger.error("Failed to decrypt config: {}", entity.getConfigKey(), e);
                }
            });
            logger.info("Loaded {} configurations from database", configCache.size());
        } catch (Exception e) {
            logger.error("Failed to load configurations from database", e);
        }
    }

    public String getConfig(String key) {
        return configCache.get(key);
    }

    public Map<String, String> getAllConfigs() {
        return configCache;
    }

    @Transactional
    public void saveConfig(String key, String value, boolean encrypt, String description) {
        SystemConfigurationEntity entity = new SystemConfigurationEntity();
        entity.setConfigKey(key);
        entity.setDescription(description);
        entity.setEncrypted(encrypt);

        try {
            if (encrypt && value != null) {
                entity.setConfigValue(aesUtils.encrypt(value, masterKey));
            } else {
                entity.setConfigValue(value);
            }
            repository.save(entity);
            if (value != null) {
                configCache.put(key, value);
            } else {
                configCache.remove(key);
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to save config: " + key, e);
        }
    }
}
