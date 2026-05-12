package com.example.EnterpriseRagCommunity.service.config;

import com.example.EnterpriseRagCommunity.entity.config.SystemConfigurationEntity;
import com.example.EnterpriseRagCommunity.repository.config.SystemConfigurationRepository;
import com.example.EnterpriseRagCommunity.utils.AesGcmUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SystemConfigurationServiceTest {

    private static final String MASTER_KEY = "test-master-key";

    @Mock
    private SystemConfigurationRepository repository;

    private final AesGcmUtils aesGcmUtils = new AesGcmUtils();

    private SystemConfigurationService service;

    @BeforeEach
    void setUp() {
        service = new SystemConfigurationService(repository, aesGcmUtils);
        ReflectionTestUtils.setField(service, "masterKey", MASTER_KEY);
    }

    @Test
    void refreshCache_shouldLoadBlankLegacyPlaintextAndEncryptedValues() throws Exception {
        String encryptedSecret = aesGcmUtils.encrypt("secret-123", MASTER_KEY);
        when(repository.findAll()).thenReturn(List.of(
                entity("image.storage.oss.endpoint", "", true),
                entity("image.storage.oss.access_key_id", "legacy-access-key", true),
                entity("image.storage.oss.access_key_secret", encryptedSecret, true),
                entity("image.storage.oss.bucket", "bucket-a", false)
        ));

        service.refreshCache();

        assertEquals("", service.getConfig("image.storage.oss.endpoint"));
        assertEquals("legacy-access-key", service.getConfig("image.storage.oss.access_key_id"));
        assertEquals("secret-123", service.getConfig("image.storage.oss.access_key_secret"));
        assertEquals("bucket-a", service.getConfig("image.storage.oss.bucket"));
    }

    @Test
    void refreshCache_shouldSkipCiphertextThatFailsAuthentication() throws Exception {
        String ciphertextWithOtherKey = aesGcmUtils.encrypt("secret-123", "other-master-key");
        when(repository.findAll()).thenReturn(List.of(
                entity("image.storage.oss.access_key_secret", ciphertextWithOtherKey, true)
        ));

        service.refreshCache();

        assertNull(service.getConfig("image.storage.oss.access_key_secret"));
    }

    private static SystemConfigurationEntity entity(String key, String value, boolean encrypted) {
        SystemConfigurationEntity entity = new SystemConfigurationEntity();
        entity.setConfigKey(key);
        entity.setConfigValue(value);
        entity.setEncrypted(encrypted);
        return entity;
    }
}