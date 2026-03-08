package com.example.EnterpriseRagCommunity.service.ai;

import com.example.EnterpriseRagCommunity.dto.ai.PromptBatchResponse;
import com.example.EnterpriseRagCommunity.dto.ai.PromptContentDTO;
import com.example.EnterpriseRagCommunity.dto.ai.PromptContentUpdateRequest;
import com.example.EnterpriseRagCommunity.entity.semantic.PromptsEntity;
import com.example.EnterpriseRagCommunity.repository.semantic.PromptsRepository;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class PromptsAdminServiceTest {

    @Test
    void batchGetByCodes_returnsMissingCodes() {
        PromptsRepository repo = mock(PromptsRepository.class);
        PromptsAdminService svc = new PromptsAdminService(repo);

        PromptsEntity p = new PromptsEntity();
        p.setPromptCode("TITLE_GEN");
        p.setName("标题生成");
        p.setSystemPrompt("S");
        p.setUserPromptTemplate("U");
        p.setVersion(1);
        p.setUpdatedBy(10L);

        when(repo.findByPromptCodeIn(eq(List.of("TITLE_GEN", "TRANSLATE_GEN")))).thenReturn(List.of(p));

        PromptBatchResponse resp = svc.batchGetByCodes(List.of(" title_gen ", "TRANSLATE_GEN", "title_gen"));

        assertNotNull(resp);
        assertEquals(List.of("TRANSLATE_GEN"), resp.getMissingCodes());
        assertEquals(1, resp.getPrompts().size());
        assertEquals("TITLE_GEN", resp.getPrompts().get(0).getPromptCode());
    }

    @Test
    void batchGetByCodes_emptyNormalizedInput_shouldSkipRepository() {
        PromptsRepository repo = mock(PromptsRepository.class);
        PromptsAdminService svc = new PromptsAdminService(repo);

        PromptBatchResponse nullResp = svc.batchGetByCodes(null);
        assertNotNull(nullResp);
        assertEquals(List.of(), nullResp.getPrompts());
        assertEquals(List.of(), nullResp.getMissingCodes());

        PromptBatchResponse blankResp = svc.batchGetByCodes(List.of(" ", "\t", "\n"));
        assertNotNull(blankResp);
        assertEquals(List.of(), blankResp.getPrompts());
        assertEquals(List.of(), blankResp.getMissingCodes());

        verify(repo, never()).findByPromptCodeIn(any());
    }

    @Test
    void batchGetByCodes_filtersInvalidRepoCodes_andKeepsFirstWhenDuplicate() {
        PromptsRepository repo = mock(PromptsRepository.class);
        PromptsAdminService svc = new PromptsAdminService(repo);

        PromptsEntity first = new PromptsEntity();
        first.setPromptCode("TITLE_GEN");
        first.setName("first");

        PromptsEntity duplicate = new PromptsEntity();
        duplicate.setPromptCode(" TITLE_GEN ");
        duplicate.setName("duplicate");

        PromptsEntity blank = new PromptsEntity();
        blank.setPromptCode(" ");
        blank.setName("blank");

        PromptsEntity nullCode = new PromptsEntity();
        nullCode.setPromptCode(null);
        nullCode.setName("null");

        when(repo.findByPromptCodeIn(eq(List.of("TITLE_GEN", "MISSING_CODE"))))
                .thenReturn(List.of(first, duplicate, blank, nullCode));

        PromptBatchResponse resp = svc.batchGetByCodes(List.of("title_gen", "missing_code"));

        assertNotNull(resp);
        assertEquals(1, resp.getPrompts().size());
        assertEquals("first", resp.getPrompts().get(0).getName());
        assertEquals(List.of("MISSING_CODE"), resp.getMissingCodes());
    }

    @Test
    void updateContent_incrementsVersion_andSetsUpdatedBy() {
        PromptsRepository repo = mock(PromptsRepository.class);
        PromptsAdminService svc = new PromptsAdminService(repo);

        PromptsEntity p = new PromptsEntity();
        p.setPromptCode("TITLE_GEN");
        p.setName("标题生成");
        p.setSystemPrompt("S");
        p.setUserPromptTemplate("U");
        p.setVersion(5);
        p.setUpdatedBy(null);

        when(repo.findByPromptCode("TITLE_GEN")).thenReturn(Optional.of(p));
        when(repo.save(any(PromptsEntity.class))).thenAnswer(inv -> inv.getArgument(0));

        PromptContentUpdateRequest req = new PromptContentUpdateRequest();
        req.setSystemPrompt("S2");

        PromptContentDTO out = svc.updateContent("title_gen", req, 99L);

        assertEquals("TITLE_GEN", out.getPromptCode());
        assertEquals("S2", out.getSystemPrompt());
        assertEquals(6, out.getVersion());
        assertEquals(99L, out.getUpdatedBy());
    }

    @Test
    void updateContent_nullRequestAndNullUpdatedBy_shouldKeepExistingContent_andSetVersionToOne() {
        PromptsRepository repo = mock(PromptsRepository.class);
        PromptsAdminService svc = new PromptsAdminService(repo);

        PromptsEntity p = new PromptsEntity();
        p.setPromptCode("TITLE_GEN");
        p.setName("标题生成");
        p.setSystemPrompt("S");
        p.setUserPromptTemplate("U");
        p.setVersion(null);
        p.setUpdatedBy(5L);

        when(repo.findByPromptCode("TITLE_GEN")).thenReturn(Optional.of(p));
        when(repo.save(any(PromptsEntity.class))).thenAnswer(inv -> inv.getArgument(0));

        PromptContentDTO out = svc.updateContent(" title_gen ", null, null);

        assertEquals("TITLE_GEN", out.getPromptCode());
        assertEquals("标题生成", out.getName());
        assertEquals("S", out.getSystemPrompt());
        assertEquals("U", out.getUserPromptTemplate());
        assertEquals(1, out.getVersion());
        assertEquals(5L, out.getUpdatedBy());
    }

    @Test
    void updateContent_shouldUpdateNameAndTemplate_andClampVersionAtLeastOne() {
        PromptsRepository repo = mock(PromptsRepository.class);
        PromptsAdminService svc = new PromptsAdminService(repo);

        PromptsEntity p = new PromptsEntity();
        p.setPromptCode("TITLE_GEN");
        p.setName("old");
        p.setSystemPrompt("S");
        p.setUserPromptTemplate("old-template");
        p.setVersion(0);

        when(repo.findByPromptCode("TITLE_GEN")).thenReturn(Optional.of(p));
        when(repo.save(any(PromptsEntity.class))).thenAnswer(inv -> inv.getArgument(0));

        PromptContentUpdateRequest req = new PromptContentUpdateRequest();
        req.setName("new");
        req.setUserPromptTemplate("new-template");

        PromptContentDTO out = svc.updateContent("title_gen", req, null);

        assertEquals("new", out.getName());
        assertEquals("S", out.getSystemPrompt());
        assertEquals("new-template", out.getUserPromptTemplate());
        assertEquals(1, out.getVersion());
    }

    @Test
    void updateContent_nullPromptCode_shouldNormalizeToEmptyAndThrow() {
        PromptsRepository repo = mock(PromptsRepository.class);
        PromptsAdminService svc = new PromptsAdminService(repo);

        when(repo.findByPromptCode("")).thenReturn(Optional.empty());

        assertThrows(java.util.NoSuchElementException.class,
                () -> svc.updateContent(null, new PromptContentUpdateRequest(), 1L));
    }

    @Test
    void updateContent_promptNotFound_throws() {
        PromptsRepository repo = mock(PromptsRepository.class);
        PromptsAdminService svc = new PromptsAdminService(repo);

        when(repo.findByPromptCode("MISSING")).thenReturn(Optional.empty());

        assertThrows(java.util.NoSuchElementException.class, () -> svc.updateContent("missing", new PromptContentUpdateRequest(), 1L));
    }
}
