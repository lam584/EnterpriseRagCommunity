package com.example.EnterpriseRagCommunity.service.retrieval.admin;

import com.example.EnterpriseRagCommunity.dto.retrieval.CitationConfigDTO;
import com.example.EnterpriseRagCommunity.dto.retrieval.CitationTestRequest;
import com.example.EnterpriseRagCommunity.dto.retrieval.CitationTestResponse;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CitationTestServiceTest {

    @Test
    void test_shouldUseSavedConfigWhenRequestNull() {
        CitationConfigService citationConfigService = mock(CitationConfigService.class);
        CitationConfigDTO cfg = new CitationConfigDTO();
        cfg.setEnabled(true);
        cfg.setCitationMode("BOTH");
        cfg.setInstructionTemplate("ins");
        cfg.setMaxSources(3);
        cfg.setSourcesTitle("来源");
        when(citationConfigService.getConfigOrDefault()).thenReturn(cfg);

        CitationTestService service = new CitationTestService(citationConfigService);
        CitationTestResponse out = service.test(null);

        assertThat(out.getConfig()).isSameAs(cfg);
        assertThat(out.getInstructionPreview()).isEqualTo("ins");
        assertThat(out.getSources()).isEmpty();
        assertThat(out.getSourcesPreview()).isEmpty();
    }

    @Test
    void test_shouldUseNormalizedConfigAndRenderAllFields() {
        CitationConfigService citationConfigService = mock(CitationConfigService.class);
        CitationConfigDTO cfg = new CitationConfigDTO();
        cfg.setInstructionTemplate("i2");
        cfg.setSourcesTitle("  来源  ");
        cfg.setMaxSources(2);
        cfg.setPostUrlTemplate("/p/{postId}");
        cfg.setIncludeTitle(true);
        cfg.setIncludeUrl(true);
        cfg.setIncludeScore(true);
        cfg.setIncludePostId(true);
        cfg.setIncludeChunkIndex(true);
        CitationConfigDTO normalized = new CitationConfigDTO();
        normalized.setEnabled(true);
        normalized.setCitationMode("BOTH");
        normalized.setInstructionTemplate("i2");
        normalized.setSourcesTitle("来源");
        normalized.setMaxSources(2);
        normalized.setPostUrlTemplate("/p/{postId}");
        normalized.setIncludeTitle(true);
        normalized.setIncludeUrl(true);
        normalized.setIncludeScore(true);
        normalized.setIncludePostId(true);
        normalized.setIncludeChunkIndex(true);

        CitationTestRequest req = new CitationTestRequest();
        req.setUseSavedConfig(false);
        req.setConfig(cfg);
        CitationTestRequest.CitationTestItem i1 = new CitationTestRequest.CitationTestItem();
        i1.setPostId(11L);
        i1.setChunkIndex(2);
        i1.setScore(0.12345);
        i1.setTitle(" t1 ");
        CitationTestRequest.CitationTestItem i2 = new CitationTestRequest.CitationTestItem();
        i2.setPostId(null);
        i2.setChunkIndex(null);
        i2.setScore(null);
        i2.setTitle(" ");
        req.setItems(Arrays.asList(i1, null, i2));
        when(citationConfigService.normalizeConfig(cfg)).thenReturn(normalized);

        CitationTestService service = new CitationTestService(citationConfigService);
        CitationTestResponse out = service.test(req);

        verify(citationConfigService).normalizeConfig(cfg);
        assertThat(out.getSources()).hasSize(1);
        assertThat(out.getSources().get(0).getUrl()).isEqualTo("/p/11");
        assertThat(out.getSourcesPreview()).contains("来源");
        assertThat(out.getSourcesPreview()).contains("[1]");
        assertThat(out.getSourcesPreview()).contains("t1");
        assertThat(out.getSourcesPreview()).contains("score=0.1235");
        assertThat(out.getSourcesPreview()).contains("post_id=11");
        assertThat(out.getSourcesPreview()).contains("chunk=2");
    }

    @Test
    void test_shouldHandleBlankTitleAndNoUrlTemplateBranches() {
        CitationConfigService citationConfigService = mock(CitationConfigService.class);
        CitationConfigDTO cfg = new CitationConfigDTO();
        cfg.setEnabled(true);
        cfg.setCitationMode("SOURCES_SECTION");
        cfg.setMaxSources(5);
        cfg.setSourcesTitle(" ");
        cfg.setPostUrlTemplate(" ");
        cfg.setIncludeTitle(false);
        cfg.setIncludeUrl(true);
        cfg.setIncludeScore(false);
        cfg.setIncludePostId(false);
        cfg.setIncludeChunkIndex(false);
        when(citationConfigService.getConfigOrDefault()).thenReturn(cfg);

        CitationTestRequest req = new CitationTestRequest();
        req.setUseSavedConfig(true);
        CitationTestRequest.CitationTestItem i1 = new CitationTestRequest.CitationTestItem();
        i1.setPostId(8L);
        req.setItems(List.of(i1));

        CitationTestService service = new CitationTestService(citationConfigService);
        CitationTestResponse out = service.test(req);
        assertThat(out.getSources()).hasSize(1);
        assertThat(out.getSources().get(0).getUrl()).isNull();
        assertThat(out.getSourcesPreview()).isEmpty();
    }

    @Test
    void test_shouldCoverNullConfigAndEmptySourceBranches() {
        CitationConfigService citationConfigService = mock(CitationConfigService.class);
        when(citationConfigService.getConfigOrDefault()).thenReturn(null);

        CitationTestService service = new CitationTestService(citationConfigService);
        CitationTestResponse out = service.test(new CitationTestRequest());
        assertThat(out.getInstructionPreview()).isNull();
        assertThat(out.getSources()).isEmpty();
        assertThat(out.getSourcesPreview()).isEmpty();
    }

    @Test
    void test_shouldRenderWithoutOptionalFields() {
        CitationConfigService citationConfigService = mock(CitationConfigService.class);
        CitationConfigDTO cfg = new CitationConfigDTO();
        cfg.setEnabled(true);
        cfg.setCitationMode("SOURCES_SECTION");
        cfg.setMaxSources(2);
        cfg.setSourcesTitle("来源");
        cfg.setPostUrlTemplate("/p/{postId}");
        cfg.setIncludeTitle(false);
        cfg.setIncludeUrl(false);
        cfg.setIncludeScore(false);
        cfg.setIncludePostId(false);
        cfg.setIncludeChunkIndex(false);
        when(citationConfigService.getConfigOrDefault()).thenReturn(cfg);

        CitationTestRequest req = new CitationTestRequest();
        CitationTestRequest.CitationTestItem i1 = new CitationTestRequest.CitationTestItem();
        i1.setPostId(null);
        i1.setChunkIndex(null);
        i1.setScore(null);
        i1.setTitle(" ");
        req.setItems(List.of(i1));

        CitationTestService service = new CitationTestService(citationConfigService);
        CitationTestResponse out = service.test(req);
        assertThat(out.getSources()).hasSize(1);
        assertThat(out.getSourcesPreview()).contains("[1]");
        assertThat(out.getSourcesPreview()).doesNotContain("score=");
        assertThat(out.getSourcesPreview()).doesNotContain("post_id=");
        assertThat(out.getSourcesPreview()).doesNotContain("chunk=");
    }

    @Test
    void privateHelpers_shouldCoverRemainingBranches() throws Exception {
        Method build = CitationTestService.class.getDeclaredMethod("buildPostUrl", CitationConfigDTO.class, Long.class);
        build.setAccessible(true);
        CitationConfigDTO cfg = new CitationConfigDTO();
        cfg.setPostUrlTemplate("/p/{postId}");
        assertThat(build.invoke(null, null, 1L)).isNull();
        cfg.setPostUrlTemplate(" ");
        assertThat(build.invoke(null, cfg, 1L)).isNull();
        cfg.setPostUrlTemplate("/p/{postId}");
        assertThat(build.invoke(null, cfg, null)).isEqualTo("/p/");

        Method render = CitationTestService.class.getDeclaredMethod("renderSourcesText", CitationConfigDTO.class, List.class);
        render.setAccessible(true);
        assertThat(render.invoke(null, null, List.of())).isEqualTo("");
        CitationConfigDTO noTitle = new CitationConfigDTO();
        noTitle.setEnabled(true);
        noTitle.setCitationMode("SOURCES_SECTION");
        noTitle.setSourcesTitle(" ");
        assertThat(render.invoke(null, noTitle, List.of(new CitationTestResponse.Source()))).isEqualTo("");
        CitationConfigDTO full = new CitationConfigDTO();
        full.setEnabled(true);
        full.setCitationMode("BOTH");
        full.setSourcesTitle("来源");
        full.setIncludeTitle(true);
        full.setIncludeUrl(true);
        full.setIncludeScore(true);
        full.setIncludePostId(true);
        full.setIncludeChunkIndex(true);
        CitationTestResponse.Source s = new CitationTestResponse.Source();
        s.setIndex(1);
        s.setTitle("t");
        s.setUrl("/p/1");
        s.setScore(0.12);
        s.setPostId(1L);
        s.setChunkIndex(2);
        String text = (String) render.invoke(null, full, Arrays.asList(s, null));
        assertThat(text).contains("来源");
        assertThat(text).contains("score=0.1200");
        assertThat(text).contains("post_id=1");
        assertThat(text).contains("chunk=2");
    }

    @Test
    void privateHelpers_shouldCoverNullValueFormattingBranches() throws Exception {
        Method render = CitationTestService.class.getDeclaredMethod("renderSourcesText", CitationConfigDTO.class, List.class);
        render.setAccessible(true);
        CitationConfigDTO full = new CitationConfigDTO();
        full.setEnabled(true);
        full.setCitationMode("BOTH");
        full.setSourcesTitle("来源");
        full.setIncludeTitle(true);
        full.setIncludeUrl(true);
        full.setIncludeScore(true);
        full.setIncludePostId(true);
        full.setIncludeChunkIndex(true);

        CitationTestResponse.Source s = new CitationTestResponse.Source();
        s.setIndex(null);
        s.setTitle(" ");
        s.setUrl(" ");
        s.setScore(null);
        s.setPostId(null);
        s.setChunkIndex(null);
        String text = (String) render.invoke(null, full, List.of(s));
        assertThat(text).contains("[]");
        assertThat(text).doesNotContain("score=");
        assertThat(text).doesNotContain("post_id=");
        assertThat(text).doesNotContain("chunk=");
    }

    @Test
    void test_shouldHideInstructionPreview_whenModeIsSourcesSection() {
        CitationConfigService citationConfigService = mock(CitationConfigService.class);
        CitationConfigDTO cfg = new CitationConfigDTO();
        cfg.setEnabled(true);
        cfg.setCitationMode("SOURCES_SECTION");
        cfg.setInstructionTemplate("ins");
        cfg.setMaxSources(1);
        cfg.setSourcesTitle("来源");
        when(citationConfigService.getConfigOrDefault()).thenReturn(cfg);

        CitationTestService service = new CitationTestService(citationConfigService);
        CitationTestResponse out = service.test(new CitationTestRequest());

        assertThat(out.getInstructionPreview()).isEmpty();
    }
}
