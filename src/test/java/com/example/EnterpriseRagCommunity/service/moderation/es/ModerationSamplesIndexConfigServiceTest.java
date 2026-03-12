package com.example.EnterpriseRagCommunity.service.moderation.es;

import java.util.List;
import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.EnterpriseRagCommunity.entity.moderation.ModerationSamplesIndexConfigEntity;
import com.example.EnterpriseRagCommunity.repository.moderation.ModerationSamplesIndexConfigRepository;

class ModerationSamplesIndexConfigServiceTest {

    private ModerationSamplesIndexConfigRepository repository;
    private ModerationSamplesIndexConfigService service;

    @BeforeEach
    void setUp() {
        repository = mock(ModerationSamplesIndexConfigRepository.class);
        service = new ModerationSamplesIndexConfigService(repository);
    }

    @Test
    void getOrSeedDefault_shouldReturnExistingWhenPresent() {
        ModerationSamplesIndexConfigEntity existing = new ModerationSamplesIndexConfigEntity();
        existing.setIndexName("exists");
        when(repository.findAll()).thenReturn(List.of(existing));

        ModerationSamplesIndexConfigEntity out = service.getOrSeedDefault(1L);

        assertThat(out).isSameAs(existing);
    }

    @Test
    void getOrSeedDefault_shouldSeedWhenMissing() {
        when(repository.findAll()).thenReturn(List.of());
        when(repository.save(org.mockito.ArgumentMatchers.any(ModerationSamplesIndexConfigEntity.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        ModerationSamplesIndexConfigEntity out = service.getOrSeedDefault(7L);

        assertThat(out.getIndexName()).isEqualTo(ModerationSamplesIndexConfigService.DEFAULT_INDEX_NAME);
        assertThat(out.getIkEnabled()).isEqualTo(ModerationSamplesIndexConfigService.DEFAULT_IK_ENABLED);
        assertThat(out.getEmbeddingDims()).isEqualTo(ModerationSamplesIndexConfigService.DEFAULT_EMBEDDING_DIMS);
        assertThat(out.getDefaultTopK()).isEqualTo(ModerationSamplesIndexConfigService.DEFAULT_TOP_K);
        assertThat(out.getDefaultThreshold()).isEqualTo(ModerationSamplesIndexConfigService.DEFAULT_THRESHOLD);
        assertThat(out.getUpdatedBy()).isEqualTo(7L);
        assertThat(out.getUpdatedAt()).isNotNull();
    }

    @Test
    void getConfigOrNull_shouldReturnNullOnRepositoryError() {
        when(repository.findAll()).thenThrow(new RuntimeException("db down"));

        ModerationSamplesIndexConfigEntity out = service.getConfigOrNull();

        assertThat(out).isNull();
    }

    @Test
    void getIndexNameOrDefault_shouldFallbackOnBlank() {
        ModerationSamplesIndexConfigEntity cfg = new ModerationSamplesIndexConfigEntity();
        cfg.setIndexName("   ");
        when(repository.findAll()).thenReturn(List.of(cfg));

        String out = service.getIndexNameOrDefault();

        assertThat(out).isEqualTo(ModerationSamplesIndexConfigService.DEFAULT_INDEX_NAME);
    }

    @Test
    void getIndexNameOrDefault_shouldReturnTrimmedValue() {
        ModerationSamplesIndexConfigEntity cfg = new ModerationSamplesIndexConfigEntity();
        cfg.setIndexName(" my_idx ");
        when(repository.findAll()).thenReturn(List.of(cfg));

        String out = service.getIndexNameOrDefault();

        assertThat(out).isEqualTo("my_idx");
    }

    @Test
    void isIkEnabledOrDefault_shouldUseDefaultWhenNull() {
        ModerationSamplesIndexConfigEntity cfg = new ModerationSamplesIndexConfigEntity();
        cfg.setIkEnabled(null);
        when(repository.findAll()).thenReturn(List.of(cfg));

        boolean out = service.isIkEnabledOrDefault();

        assertThat(out).isTrue();
    }

    @Test
    void isIkEnabledOrDefault_shouldReadConfiguredValue() {
        ModerationSamplesIndexConfigEntity cfg = new ModerationSamplesIndexConfigEntity();
        cfg.setIkEnabled(false);
        when(repository.findAll()).thenReturn(List.of(cfg));

        boolean out = service.isIkEnabledOrDefault();

        assertThat(out).isFalse();
    }

    @Test
    void getEmbeddingModelOrDefault_shouldFallbackOnBlank() {
        ModerationSamplesIndexConfigEntity cfg = new ModerationSamplesIndexConfigEntity();
        cfg.setEmbeddingModel(" ");
        when(repository.findAll()).thenReturn(List.of(cfg));

        String out = service.getEmbeddingModelOrDefault();

        assertThat(out).isEqualTo(ModerationSamplesIndexConfigService.DEFAULT_EMBEDDING_MODEL);
    }

    @Test
    void getEmbeddingDimsOrDefault_shouldClampToNonNegative() {
        ModerationSamplesIndexConfigEntity cfg = new ModerationSamplesIndexConfigEntity();
        cfg.setEmbeddingDims(-5);
        when(repository.findAll()).thenReturn(List.of(cfg));

        int out = service.getEmbeddingDimsOrDefault();

        assertThat(out).isZero();
    }

    @Test
    void getDefaultTopKOrDefault_shouldClampToRange() {
        ModerationSamplesIndexConfigEntity low = new ModerationSamplesIndexConfigEntity();
        low.setDefaultTopK(0);
        when(repository.findAll()).thenReturn(List.of(low));
        assertThat(service.getDefaultTopKOrDefault()).isEqualTo(1);

        ModerationSamplesIndexConfigEntity high = new ModerationSamplesIndexConfigEntity();
        high.setDefaultTopK(88);
        when(repository.findAll()).thenReturn(List.of(high));
        assertThat(service.getDefaultTopKOrDefault()).isEqualTo(50);
    }

    @Test
    void getDefaultThresholdOrDefault_shouldClampAndKeepNormalValue() {
        ModerationSamplesIndexConfigEntity low = new ModerationSamplesIndexConfigEntity();
        low.setDefaultThreshold(-0.1);
        when(repository.findAll()).thenReturn(List.of(low));
        assertThat(service.getDefaultThresholdOrDefault()).isZero();

        ModerationSamplesIndexConfigEntity high = new ModerationSamplesIndexConfigEntity();
        high.setDefaultThreshold(1.5);
        when(repository.findAll()).thenReturn(List.of(high));
        assertThat(service.getDefaultThresholdOrDefault()).isEqualTo(1.0);

        ModerationSamplesIndexConfigEntity normal = new ModerationSamplesIndexConfigEntity();
        normal.setDefaultThreshold(0.3);
        when(repository.findAll()).thenReturn(List.of(normal));
        assertThat(service.getDefaultThresholdOrDefault()).isEqualTo(0.3);
    }

    @Test
    void getters_shouldUseDefaultsWhenConfigMissing() {
        when(repository.findAll()).thenReturn(List.of());
        assertThat(service.getIndexNameOrDefault()).isEqualTo(ModerationSamplesIndexConfigService.DEFAULT_INDEX_NAME);
        assertThat(service.isIkEnabledOrDefault()).isEqualTo(ModerationSamplesIndexConfigService.DEFAULT_IK_ENABLED);
        assertThat(service.getEmbeddingModelOrDefault()).isEqualTo(ModerationSamplesIndexConfigService.DEFAULT_EMBEDDING_MODEL);
        assertThat(service.getEmbeddingDimsOrDefault()).isEqualTo(ModerationSamplesIndexConfigService.DEFAULT_EMBEDDING_DIMS);
        assertThat(service.getDefaultTopKOrDefault()).isEqualTo(ModerationSamplesIndexConfigService.DEFAULT_TOP_K);
        assertThat(service.getDefaultThresholdOrDefault()).isEqualTo(ModerationSamplesIndexConfigService.DEFAULT_THRESHOLD);
    }

    @Test
    void toNonBlank_shouldReturnNullForNull() throws Exception {
        Method m = ModerationSamplesIndexConfigService.class.getDeclaredMethod("toNonBlank", String.class);
        m.setAccessible(true);
        assertThat(m.invoke(null, new Object[]{null})).isNull();
    }

    @Test
    void getters_shouldReturnConfiguredValuesWhenValid() {
        ModerationSamplesIndexConfigEntity cfg = new ModerationSamplesIndexConfigEntity();
        cfg.setIndexName(" idx_a ");
        cfg.setIkEnabled(false);
        cfg.setEmbeddingModel(" model-a ");
        cfg.setEmbeddingDims(6);
        cfg.setDefaultTopK(9);
        cfg.setDefaultThreshold(0.6);
        when(repository.findAll()).thenReturn(List.of(cfg));

        assertThat(service.getIndexNameOrDefault()).isEqualTo("idx_a");
        assertThat(service.isIkEnabledOrDefault()).isFalse();
        assertThat(service.getEmbeddingModelOrDefault()).isEqualTo("model-a");
        assertThat(service.getEmbeddingDimsOrDefault()).isEqualTo(6);
        assertThat(service.getDefaultTopKOrDefault()).isEqualTo(9);
        assertThat(service.getDefaultThresholdOrDefault()).isEqualTo(0.6);
    }

    @Test
    void getOrSeedDefault_shouldCallSaveOnSeedPath() {
        when(repository.findAll()).thenReturn(List.of());
        when(repository.save(org.mockito.ArgumentMatchers.any(ModerationSamplesIndexConfigEntity.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        service.getOrSeedDefault(2L);

        verify(repository).save(org.mockito.ArgumentMatchers.any(ModerationSamplesIndexConfigEntity.class));
    }
}
