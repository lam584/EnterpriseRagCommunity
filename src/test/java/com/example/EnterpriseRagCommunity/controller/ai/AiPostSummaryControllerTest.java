package com.example.EnterpriseRagCommunity.controller.ai;

import com.example.EnterpriseRagCommunity.dto.ai.PostAiSummaryDTO;
import com.example.EnterpriseRagCommunity.dto.ai.PostSummaryGenPublicConfigDTO;
import com.example.EnterpriseRagCommunity.entity.ai.PostAiSummaryEntity;
import com.example.EnterpriseRagCommunity.repository.ai.PostAiSummaryRepository;
import com.example.EnterpriseRagCommunity.service.ai.PostSummaryGenConfigService;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class AiPostSummaryControllerTest {

    @Test
    void getPostSummary_disabled_returnsDisabled() {
        PostSummaryGenConfigService cfgSvc = mock(PostSummaryGenConfigService.class);
        PostAiSummaryRepository repo = mock(PostAiSummaryRepository.class);

        PostSummaryGenPublicConfigDTO cfg = new PostSummaryGenPublicConfigDTO();
        cfg.setEnabled(false);
        when(cfgSvc.getPublicConfig()).thenReturn(cfg);

        AiPostSummaryController c = new AiPostSummaryController(cfgSvc, repo);
        PostAiSummaryDTO dto = c.getPostSummary(10L);

        Assertions.assertEquals(10L, dto.getPostId());
        Assertions.assertFalse(dto.getEnabled());
        Assertions.assertEquals("DISABLED", dto.getStatus());
        verifyNoInteractions(repo);
    }

    @Test
    void getPostSummary_enabledWithoutSummary_returnsPending() {
        PostSummaryGenConfigService cfgSvc = mock(PostSummaryGenConfigService.class);
        PostAiSummaryRepository repo = mock(PostAiSummaryRepository.class);

        PostSummaryGenPublicConfigDTO cfg = new PostSummaryGenPublicConfigDTO();
        cfg.setEnabled(true);
        when(cfgSvc.getPublicConfig()).thenReturn(cfg);
        when(repo.findByPostId(10L)).thenReturn(Optional.empty());

        AiPostSummaryController c = new AiPostSummaryController(cfgSvc, repo);
        PostAiSummaryDTO dto = c.getPostSummary(10L);

        Assertions.assertTrue(dto.getEnabled());
        Assertions.assertEquals("PENDING", dto.getStatus());
    }

    @Test
    void getPostSummary_success_setsTitleAndText() {
        PostSummaryGenConfigService cfgSvc = mock(PostSummaryGenConfigService.class);
        PostAiSummaryRepository repo = mock(PostAiSummaryRepository.class);

        PostSummaryGenPublicConfigDTO cfg = new PostSummaryGenPublicConfigDTO();
        cfg.setEnabled(true);
        when(cfgSvc.getPublicConfig()).thenReturn(cfg);

        PostAiSummaryEntity s = new PostAiSummaryEntity();
        s.setPostId(10L);
        s.setStatus("success");
        s.setSummaryTitle("t");
        s.setSummaryText("x");
        s.setGeneratedAt(LocalDateTime.of(2026, 1, 2, 3, 4));
        s.setErrorMessage("  err1\nerr2  ");
        when(repo.findByPostId(10L)).thenReturn(Optional.of(s));

        AiPostSummaryController c = new AiPostSummaryController(cfgSvc, repo);
        PostAiSummaryDTO dto = c.getPostSummary(10L);

        Assertions.assertEquals("success", dto.getStatus());
        Assertions.assertEquals("t", dto.getSummaryTitle());
        Assertions.assertEquals("x", dto.getSummaryText());
        Assertions.assertEquals(LocalDateTime.of(2026, 1, 2, 3, 4), dto.getGeneratedAt());
        Assertions.assertEquals("err1", dto.getErrorMessage());
    }

    @Test
    void getPostSummary_nonSuccess_doesNotSetTitleAndText() {
        PostSummaryGenConfigService cfgSvc = mock(PostSummaryGenConfigService.class);
        PostAiSummaryRepository repo = mock(PostAiSummaryRepository.class);

        PostSummaryGenPublicConfigDTO cfg = new PostSummaryGenPublicConfigDTO();
        cfg.setEnabled(true);
        when(cfgSvc.getPublicConfig()).thenReturn(cfg);

        PostAiSummaryEntity s = new PostAiSummaryEntity();
        s.setPostId(10L);
        s.setStatus("FAILED");
        s.setSummaryTitle("t");
        s.setSummaryText("x");
        s.setErrorMessage("   ");
        when(repo.findByPostId(10L)).thenReturn(Optional.of(s));

        AiPostSummaryController c = new AiPostSummaryController(cfgSvc, repo);
        PostAiSummaryDTO dto = c.getPostSummary(10L);

        Assertions.assertEquals("FAILED", dto.getStatus());
        Assertions.assertNull(dto.getSummaryTitle());
        Assertions.assertNull(dto.getSummaryText());
        Assertions.assertNull(dto.getErrorMessage());
    }

    @Test
    void getPostSummary_normalizePublicError_truncatesTo500() {
        PostSummaryGenConfigService cfgSvc = mock(PostSummaryGenConfigService.class);
        PostAiSummaryRepository repo = mock(PostAiSummaryRepository.class);

        PostSummaryGenPublicConfigDTO cfg = new PostSummaryGenPublicConfigDTO();
        cfg.setEnabled(true);
        when(cfgSvc.getPublicConfig()).thenReturn(cfg);

        String raw = "a".repeat(600);
        PostAiSummaryEntity s = new PostAiSummaryEntity();
        s.setPostId(10L);
        s.setStatus("FAILED");
        s.setErrorMessage(raw);
        when(repo.findByPostId(10L)).thenReturn(Optional.of(s));

        AiPostSummaryController c = new AiPostSummaryController(cfgSvc, repo);
        PostAiSummaryDTO dto = c.getPostSummary(10L);

        Assertions.assertEquals(500, dto.getErrorMessage().length());
    }
}

