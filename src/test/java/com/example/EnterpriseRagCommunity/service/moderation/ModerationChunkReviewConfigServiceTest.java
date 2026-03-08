package com.example.EnterpriseRagCommunity.service.moderation;

import com.example.EnterpriseRagCommunity.repository.monitor.AppSettingsRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(properties = {
        "security.access-refresh.enabled=false"
})
@Transactional
class ModerationChunkReviewConfigServiceTest {

    @Autowired
    ModerationChunkReviewConfigService configService;

    @Autowired
    AppSettingsRepository appSettingsRepository;

    @Test
    void getConfig_shouldThrowWhenMissing() throws Exception {
        String key = ModerationChunkReviewConfigService.KEY_CONFIG_JSON;
        appSettingsRepository.deleteById(key);

        var cachedField = ModerationChunkReviewConfigService.class.getDeclaredField("cached");
        cachedField.setAccessible(true);
        cachedField.set(configService, null);

        var cachedAtField = ModerationChunkReviewConfigService.class.getDeclaredField("cachedAtMs");
        cachedAtField.setAccessible(true);
        cachedAtField.setLong(configService, 0L);

        assertThatThrownBy(() -> configService.getConfig())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("missing config");
        assertThat(appSettingsRepository.findById(key)).isNotPresent();
    }
}
