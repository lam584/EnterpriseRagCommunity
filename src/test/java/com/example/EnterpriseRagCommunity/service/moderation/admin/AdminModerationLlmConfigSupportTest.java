package com.example.EnterpriseRagCommunity.service.moderation.admin;

import java.lang.reflect.Field;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.EnterpriseRagCommunity.dto.moderation.LlmModerationConfigDTO;
import com.example.EnterpriseRagCommunity.dto.moderation.LlmModerationTestRequest;
import com.example.EnterpriseRagCommunity.entity.moderation.ModerationLlmConfigEntity;
import com.example.EnterpriseRagCommunity.repository.moderation.ModerationLlmConfigRepository;

class AdminModerationLlmConfigSupportTest {

    @Test
    void loadBaseConfigCached_shouldHitCache_whenCacheFresh() throws Exception {
        ModerationLlmConfigRepository repository = mock(ModerationLlmConfigRepository.class);
        AdminModerationLlmConfigSupport support = new AdminModerationLlmConfigSupport(repository);
        ModerationLlmConfigEntity cached = validEntity("TEXT_A", "VISION_A", "JUDGE_A", true);

        setField(support, "cachedBaseConfig", cached);
        setField(support, "cachedBaseConfigAtMs", System.currentTimeMillis() - 1_000L);

        ModerationLlmConfigEntity actual = support.loadBaseConfigCached();

        assertSame(cached, actual);
        verify(repository, never()).findTopByOrderByUpdatedAtDescIdDesc();
    }

    @Test
    void loadBaseConfigCached_shouldReload_whenCacheExpired() throws Exception {
        ModerationLlmConfigRepository repository = mock(ModerationLlmConfigRepository.class);
        AdminModerationLlmConfigSupport support = new AdminModerationLlmConfigSupport(repository);

        setField(support, "cachedBaseConfig", validEntity("TEXT_OLD", "VISION_OLD", "JUDGE_OLD", true));
        setField(support, "cachedBaseConfigAtMs", System.currentTimeMillis() - 6_000L);

        ModerationLlmConfigEntity fromRepository = validEntity("TEXT_NEW", "VISION_NEW", "JUDGE_NEW", false);
        when(repository.findTopByOrderByUpdatedAtDescIdDesc()).thenReturn(Optional.of(fromRepository));

        ModerationLlmConfigEntity actual = support.loadBaseConfigCached();

        assertSame(fromRepository, actual);
        verify(repository).findTopByOrderByUpdatedAtDescIdDesc();
    }

    @Test
    void loadBaseConfigCached_shouldReload_whenCacheAgeNegative() throws Exception {
        ModerationLlmConfigRepository repository = mock(ModerationLlmConfigRepository.class);
        AdminModerationLlmConfigSupport support = new AdminModerationLlmConfigSupport(repository);

        setField(support, "cachedBaseConfig", validEntity("TEXT_OLD", "VISION_OLD", "JUDGE_OLD", true));
        setField(support, "cachedBaseConfigAtMs", System.currentTimeMillis() + 1_000L);

        ModerationLlmConfigEntity fromRepository = validEntity("TEXT_NEW", "VISION_NEW", "JUDGE_NEW", false);
        when(repository.findTopByOrderByUpdatedAtDescIdDesc()).thenReturn(Optional.of(fromRepository));

        ModerationLlmConfigEntity actual = support.loadBaseConfigCached();

        assertSame(fromRepository, actual);
        verify(repository).findTopByOrderByUpdatedAtDescIdDesc();
    }

    @Test
    void loadBaseConfigCached_shouldThrow_whenRepositoryEmpty() {
        ModerationLlmConfigRepository repository = mock(ModerationLlmConfigRepository.class);
        AdminModerationLlmConfigSupport support = new AdminModerationLlmConfigSupport(repository);
        when(repository.findTopByOrderByUpdatedAtDescIdDesc()).thenReturn(Optional.empty());

        IllegalStateException ex = assertThrows(IllegalStateException.class, support::loadBaseConfigCached);

        assertTrue(ex.getMessage().contains("not initialized"));
    }

    @Test
    void upsertConfigEntity_shouldThrow_whenPayloadNull() {
        AdminModerationLlmConfigSupport support = new AdminModerationLlmConfigSupport(mock(ModerationLlmConfigRepository.class));

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> support.upsertConfigEntity(null, 1L));

        assertEquals("payload cannot be null", ex.getMessage());
    }

    @Test
    void upsertConfigEntity_shouldThrow_whenTextPromptCodeInvalid() {
        AdminModerationLlmConfigSupport support = new AdminModerationLlmConfigSupport(mock(ModerationLlmConfigRepository.class));
        LlmModerationConfigDTO payload = validPayload();
        payload.setTextPromptCode("  ");

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> support.upsertConfigEntity(payload, 1L));

        assertTrue(ex.getMessage().contains("textPromptCode"));
    }

    @Test
    void upsertConfigEntity_shouldThrow_whenVisionPromptCodeInvalid() {
        AdminModerationLlmConfigSupport support = new AdminModerationLlmConfigSupport(mock(ModerationLlmConfigRepository.class));
        LlmModerationConfigDTO payload = validPayload();
        payload.setVisionPromptCode(null);

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> support.upsertConfigEntity(payload, 1L));

        assertTrue(ex.getMessage().contains("visionPromptCode"));
    }

    @Test
    void upsertConfigEntity_shouldThrow_whenJudgePromptCodeInvalid() {
        AdminModerationLlmConfigSupport support = new AdminModerationLlmConfigSupport(mock(ModerationLlmConfigRepository.class));
        LlmModerationConfigDTO payload = validPayload();
        payload.setJudgePromptCode("   ");

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> support.upsertConfigEntity(payload, 1L));

        assertTrue(ex.getMessage().contains("judgePromptCode"));
    }

    @Test
    void upsertConfigEntity_shouldCreateNewConfigAndDefaultAutoRunTrue() {
        ModerationLlmConfigRepository repository = mock(ModerationLlmConfigRepository.class);
        AdminModerationLlmConfigSupport support = new AdminModerationLlmConfigSupport(repository);
        when(repository.findTopByOrderByUpdatedAtDescIdDesc()).thenReturn(Optional.empty());
        when(repository.save(any(ModerationLlmConfigEntity.class))).thenAnswer(inv -> inv.getArgument(0));

        LlmModerationConfigDTO payload = validPayload();
        payload.setAutoRun(null);
        ConfigUpsertResult result = support.upsertConfigEntity(payload, 100L);

        assertEquals("TEXT_NEW", result.saved().getTextPromptCode());
        assertEquals("VISION_NEW", result.saved().getVisionPromptCode());
        assertEquals("JUDGE_NEW", result.saved().getJudgePromptCode());
        assertEquals(Boolean.TRUE, result.saved().getAutoRun());
        assertEquals(100L, result.saved().getUpdatedBy());
        assertEquals(null, result.beforeSummary().get("textPromptCode"));
        assertEquals(null, result.beforeSummary().get("visionPromptCode"));
        assertEquals(null, result.beforeSummary().get("judgePromptCode"));
        assertEquals(null, result.beforeSummary().get("autoRun"));
        assertEquals("TEXT_NEW", result.afterSummary().get("textPromptCode"));
        assertEquals(Boolean.TRUE, result.afterSummary().get("autoRun"));
    }

    @Test
    void upsertConfigEntity_shouldUpdateExistingAndInvalidateCache_whenAutoRunProvided() throws Exception {
        ModerationLlmConfigRepository repository = mock(ModerationLlmConfigRepository.class);
        AdminModerationLlmConfigSupport support = new AdminModerationLlmConfigSupport(repository);
        ModerationLlmConfigEntity existing = validEntity("TEXT_OLD", "VISION_OLD", "JUDGE_OLD", true);
        existing.setId(9L);
        when(repository.findTopByOrderByUpdatedAtDescIdDesc()).thenReturn(Optional.of(existing));
        when(repository.save(any(ModerationLlmConfigEntity.class))).thenAnswer(inv -> inv.getArgument(0));

        setField(support, "cachedBaseConfig", validEntity("CACHE", "CACHE", "CACHE", true));
        setField(support, "cachedBaseConfigAtMs", System.currentTimeMillis());

        LlmModerationConfigDTO payload = validPayload();
        payload.setAutoRun(Boolean.FALSE);
        ConfigUpsertResult result = support.upsertConfigEntity(payload, 200L);

        assertSame(existing, result.saved());
        assertEquals(Boolean.FALSE, result.saved().getAutoRun());
        assertEquals("TEXT_OLD", result.beforeSummary().get("textPromptCode"));
        assertEquals(Boolean.TRUE, result.beforeSummary().get("autoRun"));
        assertEquals("TEXT_NEW", result.afterSummary().get("textPromptCode"));
        assertEquals(Boolean.FALSE, result.afterSummary().get("autoRun"));
        assertNull(getField(support, "cachedBaseConfig"));
        assertEquals(0L, (Long) getField(support, "cachedBaseConfigAtMs"));
    }

    @Test
    void merge_shouldKeepAutoRun_whenOverrideNull() {
        AdminModerationLlmConfigSupport support = new AdminModerationLlmConfigSupport(mock(ModerationLlmConfigRepository.class));
        ModerationLlmConfigEntity base = validEntity("TEXT_BASE", "VISION_BASE", "JUDGE_BASE", true);
        base.setId(1L);
        base.setVersion(7);
        base.setUpdatedBy(10L);

        ModerationLlmConfigEntity merged = support.merge(base, null);

        assertEquals(1L, merged.getId());
        assertEquals(7, merged.getVersion());
        assertEquals("TEXT_BASE", merged.getTextPromptCode());
        assertEquals("VISION_BASE", merged.getVisionPromptCode());
        assertEquals("JUDGE_BASE", merged.getJudgePromptCode());
        assertEquals(Boolean.TRUE, merged.getAutoRun());
        assertEquals(10L, merged.getUpdatedBy());
    }

    @Test
    void merge_shouldOverrideAutoRun_whenOverrideAutoRunNotNull() {
        AdminModerationLlmConfigSupport support = new AdminModerationLlmConfigSupport(mock(ModerationLlmConfigRepository.class));
        ModerationLlmConfigEntity base = validEntity("TEXT_BASE", "VISION_BASE", "JUDGE_BASE", true);
        LlmModerationTestRequest.LlmModerationConfigOverrideDTO override = new LlmModerationTestRequest.LlmModerationConfigOverrideDTO();
        override.setAutoRun(Boolean.FALSE);

        ModerationLlmConfigEntity merged = support.merge(base, override);

        assertEquals(Boolean.FALSE, merged.getAutoRun());
    }

    @Test
    void merge_shouldKeepAutoRun_whenOverrideAutoRunNull() {
        AdminModerationLlmConfigSupport support = new AdminModerationLlmConfigSupport(mock(ModerationLlmConfigRepository.class));
        ModerationLlmConfigEntity base = validEntity("TEXT_BASE", "VISION_BASE", "JUDGE_BASE", true);
        LlmModerationTestRequest.LlmModerationConfigOverrideDTO override = new LlmModerationTestRequest.LlmModerationConfigOverrideDTO();
        override.setAutoRun(null);

        ModerationLlmConfigEntity merged = support.merge(base, override);

        assertEquals(Boolean.TRUE, merged.getAutoRun());
    }

    @Test
    void normalizeBaseConfig_shouldThrow_whenBaseNull() {
        AdminModerationLlmConfigSupport support = new AdminModerationLlmConfigSupport(mock(ModerationLlmConfigRepository.class));

        IllegalStateException ex = assertThrows(IllegalStateException.class, () -> support.normalizeBaseConfig(null));

        assertTrue(ex.getMessage().contains("not initialized"));
    }

    @Test
    void normalizeBaseConfig_shouldThrow_whenTextPromptCodeInvalid() {
        AdminModerationLlmConfigSupport support = new AdminModerationLlmConfigSupport(mock(ModerationLlmConfigRepository.class));
        ModerationLlmConfigEntity base = validEntity("TEXT_A", "VISION_A", "JUDGE_A", true);
        base.setTextPromptCode(" ");

        IllegalStateException ex = assertThrows(IllegalStateException.class, () -> support.normalizeBaseConfig(base));

        assertTrue(ex.getMessage().contains("text_prompt_code"));
    }

    @Test
    void normalizeBaseConfig_shouldThrow_whenVisionPromptCodeInvalid() {
        AdminModerationLlmConfigSupport support = new AdminModerationLlmConfigSupport(mock(ModerationLlmConfigRepository.class));
        ModerationLlmConfigEntity base = validEntity("TEXT_A", "VISION_A", "JUDGE_A", true);
        base.setVisionPromptCode(null);

        IllegalStateException ex = assertThrows(IllegalStateException.class, () -> support.normalizeBaseConfig(base));

        assertTrue(ex.getMessage().contains("vision_prompt_code"));
    }

    @Test
    void normalizeBaseConfig_shouldThrow_whenJudgePromptCodeInvalid() {
        AdminModerationLlmConfigSupport support = new AdminModerationLlmConfigSupport(mock(ModerationLlmConfigRepository.class));
        ModerationLlmConfigEntity base = validEntity("TEXT_A", "VISION_A", "JUDGE_A", true);
        base.setJudgePromptCode(" ");

        IllegalStateException ex = assertThrows(IllegalStateException.class, () -> support.normalizeBaseConfig(base));

        assertTrue(ex.getMessage().contains("judge_prompt_code"));
    }

    @Test
    void summarizeConfig_shouldSupportNullAndNonNull() {
        Map<String, Object> empty = AdminModerationLlmConfigSupport.summarizeConfig(null);
        assertTrue(empty.isEmpty());

        ModerationLlmConfigEntity cfg = validEntity("TEXT_SUM", "VISION_SUM", "JUDGE_SUM", false);
        Map<String, Object> mapped = AdminModerationLlmConfigSupport.summarizeConfig(cfg);
        assertEquals("TEXT_SUM", mapped.get("textPromptCode"));
        assertEquals("VISION_SUM", mapped.get("visionPromptCode"));
        assertEquals("JUDGE_SUM", mapped.get("judgePromptCode"));
        assertEquals(Boolean.FALSE, mapped.get("autoRun"));
    }

    @Test
    void blankToNull_shouldCoverNullBlankAndText() {
        assertNull(AdminModerationLlmConfigSupport.blankToNull(null));
        assertNull(AdminModerationLlmConfigSupport.blankToNull("   "));
        assertEquals("abc", AdminModerationLlmConfigSupport.blankToNull("  abc  "));
    }

    @Test
    void toDto_shouldMapAllFields() {
        AdminModerationLlmConfigSupport support = new AdminModerationLlmConfigSupport(mock(ModerationLlmConfigRepository.class));
        ModerationLlmConfigEntity entity = validEntity("TEXT_DTO", "VISION_DTO", "JUDGE_DTO", false);
        entity.setId(99L);
        entity.setVersion(3);
        entity.setUpdatedAt(LocalDateTime.of(2026, 3, 6, 10, 30));

        LlmModerationConfigDTO dto = support.toDto(entity, "tester");

        assertEquals(99L, dto.getId());
        assertEquals(3, dto.getVersion());
        assertEquals("TEXT_DTO", dto.getTextPromptCode());
        assertEquals("VISION_DTO", dto.getVisionPromptCode());
        assertEquals("JUDGE_DTO", dto.getJudgePromptCode());
        assertEquals(Boolean.FALSE, dto.getAutoRun());
        assertEquals(LocalDateTime.of(2026, 3, 6, 10, 30), dto.getUpdatedAt());
        assertEquals("tester", dto.getUpdatedBy());
    }

    private static ModerationLlmConfigEntity validEntity(String text, String vision, String judge, Boolean autoRun) {
        ModerationLlmConfigEntity e = new ModerationLlmConfigEntity();
        e.setTextPromptCode(text);
        e.setVisionPromptCode(vision);
        e.setJudgePromptCode(judge);
        e.setAutoRun(autoRun);
        e.setVersion(1);
        e.setUpdatedAt(LocalDateTime.now());
        e.setUpdatedBy(1L);
        return e;
    }

    private static LlmModerationConfigDTO validPayload() {
        LlmModerationConfigDTO dto = new LlmModerationConfigDTO();
        dto.setTextPromptCode("TEXT_NEW");
        dto.setVisionPromptCode("VISION_NEW");
        dto.setJudgePromptCode("JUDGE_NEW");
        dto.setAutoRun(Boolean.TRUE);
        return dto;
    }

    private static void setField(Object target, String fieldName, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(target, value);
    }

    private static Object getField(Object target, String fieldName) throws Exception {
        Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        return field.get(target);
    }
}
