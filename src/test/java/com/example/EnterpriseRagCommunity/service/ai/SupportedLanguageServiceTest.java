package com.example.EnterpriseRagCommunity.service.ai;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import com.example.EnterpriseRagCommunity.dto.ai.SupportedLanguageDTO;
import com.example.EnterpriseRagCommunity.entity.ai.SupportedLanguageEntity;
import com.example.EnterpriseRagCommunity.repository.ai.SupportedLanguageRepository;

class SupportedLanguageServiceTest {
    @Test
    void listActive_should_map_entities_to_dtos() {
        SupportedLanguageRepository repo = mock(SupportedLanguageRepository.class);
        SupportedLanguageEntity e = new SupportedLanguageEntity();
        e.setLanguageCode("en");
        e.setDisplayName("English");
        e.setNativeName("English");
        e.setSortOrder(2);
        when(repo.findByIsActiveTrueOrderBySortOrderAscIdAsc()).thenReturn(List.of(e));

        SupportedLanguageService s = new SupportedLanguageService(repo);
        List<SupportedLanguageDTO> out = s.listActive();
        assertEquals(1, out.size());
        assertEquals("en", out.get(0).getLanguageCode());
        assertEquals("English", out.get(0).getDisplayName());
        assertEquals("English", out.get(0).getNativeName());
        assertEquals(2, out.get(0).getSortOrder());
    }

    @Test
    void normalizeToLanguageCode_should_handle_zh_variants_and_repo_lookup() {
        SupportedLanguageRepository repo = mock(SupportedLanguageRepository.class);
        SupportedLanguageService s = new SupportedLanguageService(repo);

        assertEquals("zh-CN", s.normalizeToLanguageCode("zh"));
        assertEquals("zh-CN", s.normalizeToLanguageCode("ZH-CN"));
        assertEquals("zh-CN", s.normalizeToLanguageCode("zh_cn"));
        assertEquals("zh-CN", s.normalizeToLanguageCode("zh-Hans"));

        SupportedLanguageEntity en = new SupportedLanguageEntity();
        en.setLanguageCode("en");
        when(repo.findByLanguageCode("en")).thenReturn(Optional.of(en));
        assertEquals("en", s.normalizeToLanguageCode("en"));

        SupportedLanguageEntity english = new SupportedLanguageEntity();
        english.setLanguageCode("en");
        when(repo.findByLanguageCode("English")).thenReturn(Optional.empty());
        when(repo.findByDisplayName("English")).thenReturn(Optional.of(english));
        assertEquals("en", s.normalizeToLanguageCode("English"));

        when(repo.findByLanguageCode("it")).thenReturn(Optional.empty());
        when(repo.findByDisplayName("it")).thenReturn(Optional.empty());
        assertEquals("it", s.normalizeToLanguageCode("it"));
    }

    @Test
    void normalizeToLanguageCode_should_return_default_for_blank_and_trim_to_blank_control_char() {
        SupportedLanguageRepository repo = mock(SupportedLanguageRepository.class);
        SupportedLanguageService s = new SupportedLanguageService(repo);
        assertEquals("zh-CN", s.normalizeToLanguageCode(null));
        assertEquals("zh-CN", s.normalizeToLanguageCode("   "));
        assertEquals("zh-CN", s.normalizeToLanguageCode("\u0000"));
    }

    @Test
    void listActiveLanguageCodes_should_filter_blanks() {
        SupportedLanguageRepository repo = mock(SupportedLanguageRepository.class);
        SupportedLanguageEntity e1 = new SupportedLanguageEntity();
        e1.setLanguageCode(" en ");
        SupportedLanguageEntity e2 = new SupportedLanguageEntity();
        e2.setLanguageCode(" ");
        SupportedLanguageEntity e3 = new SupportedLanguageEntity();
        e3.setLanguageCode(null);
        SupportedLanguageEntity e4 = new SupportedLanguageEntity();
        e4.setLanguageCode("zh-CN");
        when(repo.findByIsActiveTrueOrderBySortOrderAscIdAsc()).thenReturn(List.of(e1, e2, e3, e4));
        SupportedLanguageService s = new SupportedLanguageService(repo);
        assertEquals(List.of("en", "zh-CN"), s.listActiveLanguageCodes());
    }

    @Test
    void adminUpsert_should_create_new_when_missing_and_set_sort_order() {
        SupportedLanguageRepository repo = mock(SupportedLanguageRepository.class);
        when(repo.findByLanguageCode("en")).thenReturn(Optional.empty());
        when(repo.findTopByIsActiveTrueOrderBySortOrderDescIdDesc()).thenReturn(Optional.empty());
        when(repo.save(any(SupportedLanguageEntity.class))).thenAnswer(inv -> inv.getArgument(0));
        SupportedLanguageService s = new SupportedLanguageService(repo);

        SupportedLanguageDTO payload = new SupportedLanguageDTO();
        payload.setLanguageCode(" en ");
        payload.setDisplayName(" English ");
        payload.setNativeName(" ");

        SupportedLanguageDTO out = s.adminUpsert(payload);
        assertEquals("en", out.getLanguageCode());
        assertEquals("English", out.getDisplayName());
        assertEquals(null, out.getNativeName());
        assertEquals(1, out.getSortOrder());
    }

    @Test
    void adminUpsert_should_use_existing_entity_and_override_sort_order_when_provided() {
        SupportedLanguageRepository repo = mock(SupportedLanguageRepository.class);
        SupportedLanguageEntity existing = new SupportedLanguageEntity();
        existing.setId(3L);
        existing.setLanguageCode("en");
        existing.setSortOrder(4);
        when(repo.findByLanguageCode("en")).thenReturn(Optional.of(existing));
        when(repo.save(any(SupportedLanguageEntity.class))).thenAnswer(inv -> inv.getArgument(0));
        SupportedLanguageService s = new SupportedLanguageService(repo);

        SupportedLanguageDTO payload = new SupportedLanguageDTO();
        payload.setLanguageCode("en");
        payload.setDisplayName("English");
        payload.setNativeName("英语");
        payload.setSortOrder(8);
        SupportedLanguageDTO out = s.adminUpsert(payload);

        assertEquals("en", out.getLanguageCode());
        assertEquals("English", out.getDisplayName());
        assertEquals("英语", out.getNativeName());
        assertEquals(8, out.getSortOrder());
        verify(repo, never()).findTopByIsActiveTrueOrderBySortOrderDescIdDesc();
    }

    @Test
    void adminUpsert_should_set_next_sort_order_when_top_exists_with_null_sort() {
        SupportedLanguageRepository repo = mock(SupportedLanguageRepository.class);
        when(repo.findByLanguageCode("de")).thenReturn(Optional.empty());
        SupportedLanguageEntity top = new SupportedLanguageEntity();
        top.setSortOrder(null);
        when(repo.findTopByIsActiveTrueOrderBySortOrderDescIdDesc()).thenReturn(Optional.of(top));
        when(repo.save(any(SupportedLanguageEntity.class))).thenAnswer(inv -> inv.getArgument(0));
        SupportedLanguageService s = new SupportedLanguageService(repo);

        SupportedLanguageDTO payload = new SupportedLanguageDTO();
        payload.setLanguageCode("de");
        payload.setDisplayName("Deutsch");
        payload.setNativeName(" ");
        SupportedLanguageDTO out = s.adminUpsert(payload);
        assertEquals(1, out.getSortOrder());
        assertNull(out.getNativeName());
    }

    @Test
    void adminUpsert_should_validate_payload_and_required_fields() {
        SupportedLanguageRepository repo = mock(SupportedLanguageRepository.class);
        SupportedLanguageService s = new SupportedLanguageService(repo);
        assertThrows(IllegalArgumentException.class, () -> s.adminUpsert(null));

        SupportedLanguageDTO missingCode = new SupportedLanguageDTO();
        missingCode.setDisplayName("English");
        assertThrows(IllegalArgumentException.class, () -> s.adminUpsert(missingCode));

        SupportedLanguageDTO missingDisplay = new SupportedLanguageDTO();
        missingDisplay.setLanguageCode("en");
        assertThrows(IllegalArgumentException.class, () -> s.adminUpsert(missingDisplay));

        SupportedLanguageDTO controlCharCode = new SupportedLanguageDTO();
        controlCharCode.setLanguageCode("\u0000");
        controlCharCode.setDisplayName("English");
        assertThrows(IllegalArgumentException.class, () -> s.adminUpsert(controlCharCode));
    }

    @Test
    void adminUpdate_should_throw_when_conflict_code_exists() {
        SupportedLanguageRepository repo = mock(SupportedLanguageRepository.class);
        SupportedLanguageEntity old = new SupportedLanguageEntity();
        old.setId(1L);
        old.setLanguageCode("en");
        when(repo.findByLanguageCode("en")).thenReturn(Optional.of(old));

        SupportedLanguageEntity other = new SupportedLanguageEntity();
        other.setId(2L);
        other.setLanguageCode("fr");
        when(repo.findByLanguageCode("fr")).thenReturn(Optional.of(other));

        SupportedLanguageService s = new SupportedLanguageService(repo);
        SupportedLanguageDTO payload = new SupportedLanguageDTO();
        payload.setLanguageCode("fr");
        payload.setDisplayName("French");

        assertThrows(IllegalArgumentException.class, () -> s.adminUpdate("en", payload));
        verify(repo, never()).save(any());
    }

    @Test
    void adminUpdate_should_keep_code_when_not_changed_and_keep_existing_sort_order_when_null() {
        SupportedLanguageRepository repo = mock(SupportedLanguageRepository.class);
        SupportedLanguageEntity old = new SupportedLanguageEntity();
        old.setId(1L);
        old.setLanguageCode("en");
        old.setSortOrder(5);
        when(repo.findByLanguageCode("en")).thenReturn(Optional.of(old));
        when(repo.save(any(SupportedLanguageEntity.class))).thenAnswer(inv -> inv.getArgument(0));
        SupportedLanguageService s = new SupportedLanguageService(repo);

        SupportedLanguageDTO payload = new SupportedLanguageDTO();
        payload.setLanguageCode("en");
        payload.setDisplayName("English");
        payload.setNativeName(" ");
        payload.setSortOrder(null);
        SupportedLanguageDTO out = s.adminUpdate(" en ", payload);

        assertEquals("en", out.getLanguageCode());
        assertEquals("English", out.getDisplayName());
        assertNull(out.getNativeName());
        assertEquals(5, out.getSortOrder());
        verify(repo, never()).findByLanguageCode("fr");
    }

    @Test
    void adminUpdate_should_allow_code_change_when_target_missing_or_same_id_or_target_id_null() {
        SupportedLanguageRepository repo = mock(SupportedLanguageRepository.class);
        SupportedLanguageEntity old = new SupportedLanguageEntity();
        old.setId(1L);
        old.setLanguageCode("en");
        when(repo.findByLanguageCode("en")).thenReturn(Optional.of(old));

        when(repo.findByLanguageCode("fr")).thenReturn(Optional.empty());
        when(repo.save(any(SupportedLanguageEntity.class))).thenAnswer(inv -> inv.getArgument(0));
        SupportedLanguageService s = new SupportedLanguageService(repo);

        SupportedLanguageDTO payload = new SupportedLanguageDTO();
        payload.setLanguageCode("fr");
        payload.setDisplayName("French");
        payload.setSortOrder(7);
        SupportedLanguageDTO outMissing = s.adminUpdate("en", payload);
        assertEquals("fr", outMissing.getLanguageCode());
        assertEquals(7, outMissing.getSortOrder());

        SupportedLanguageEntity sameId = new SupportedLanguageEntity();
        sameId.setId(1L);
        when(repo.findByLanguageCode("fr")).thenReturn(Optional.of(sameId));
        SupportedLanguageDTO outSameId = s.adminUpdate("en", payload);
        assertEquals("fr", outSameId.getLanguageCode());

        SupportedLanguageEntity targetNullId = new SupportedLanguageEntity();
        targetNullId.setId(null);
        when(repo.findByLanguageCode("fr")).thenReturn(Optional.of(targetNullId));
        SupportedLanguageDTO outNullId = s.adminUpdate("en", payload);
        assertEquals("fr", outNullId.getLanguageCode());
    }

    @Test
    void adminUpdate_should_validate_inputs_and_throw_when_original_missing_or_payload_null_or_old_not_found() {
        SupportedLanguageRepository repo = mock(SupportedLanguageRepository.class);
        SupportedLanguageService s = new SupportedLanguageService(repo);

        SupportedLanguageDTO payload = new SupportedLanguageDTO();
        payload.setLanguageCode("en");
        payload.setDisplayName("English");
        assertThrows(IllegalArgumentException.class, () -> s.adminUpdate(" ", payload));
        assertThrows(IllegalArgumentException.class, () -> s.adminUpdate("\u0000", payload));
        assertThrows(IllegalArgumentException.class, () -> s.adminUpdate("en", null));

        when(repo.findByLanguageCode("en")).thenReturn(Optional.empty());
        assertThrows(IllegalArgumentException.class, () -> s.adminUpdate("en", payload));
    }

    @Test
    void findByCode_and_findByDisplayName_should_handle_blank_and_trimmed_input() {
        SupportedLanguageRepository repo = mock(SupportedLanguageRepository.class);
        SupportedLanguageService s = new SupportedLanguageService(repo);

        assertTrue(s.findByCode(" ").isEmpty());
        assertTrue(s.findByDisplayName(null).isEmpty());

        SupportedLanguageEntity byCode = new SupportedLanguageEntity();
        byCode.setLanguageCode("en");
        when(repo.findByLanguageCode("en")).thenReturn(Optional.of(byCode));
        Optional<SupportedLanguageEntity> foundByCode = s.findByCode(" en ");
        assertTrue(foundByCode.isPresent());
        assertSame(byCode, foundByCode.get());

        SupportedLanguageEntity byName = new SupportedLanguageEntity();
        byName.setLanguageCode("zh-CN");
        when(repo.findByDisplayName("中文")).thenReturn(Optional.of(byName));
        Optional<SupportedLanguageEntity> foundByName = s.findByDisplayName(" 中文 ");
        assertTrue(foundByName.isPresent());
        assertSame(byName, foundByName.get());

        verify(repo, never()).findByLanguageCode(" ");
    }

    @Test
    void adminDeactivate_should_mark_inactive_and_validate_inputs() {
        SupportedLanguageRepository repo = mock(SupportedLanguageRepository.class);
        SupportedLanguageService s = new SupportedLanguageService(repo);
        assertThrows(IllegalArgumentException.class, () -> s.adminDeactivate(" "));

        SupportedLanguageEntity e = new SupportedLanguageEntity();
        e.setLanguageCode("en");
        e.setIsActive(true);
        when(repo.findByLanguageCode("en")).thenReturn(Optional.of(e));
        when(repo.save(any(SupportedLanguageEntity.class))).thenAnswer(inv -> inv.getArgument(0));
        s.adminDeactivate(" en ");
        assertFalse(Boolean.TRUE.equals(e.getIsActive()));

        when(repo.findByLanguageCode("fr")).thenReturn(Optional.empty());
        assertThrows(IllegalArgumentException.class, () -> s.adminDeactivate("fr"));
    }
}
