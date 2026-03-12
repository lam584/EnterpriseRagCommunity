package com.example.EnterpriseRagCommunity.service.retrieval.admin;

import com.example.EnterpriseRagCommunity.dto.retrieval.CitationConfigDTO;
import com.example.EnterpriseRagCommunity.service.monitor.AppSettingsService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class CitationConfigServiceTest {

    @Test
    void getConfig_shouldReadAndNormalizeOrFallbackToDefault() {
        AppSettingsService appSettingsService = mock(AppSettingsService.class);
        CitationConfigService service = new CitationConfigService(appSettingsService, new ObjectMapper());

        when(appSettingsService.getString(CitationConfigService.KEY_CONFIG_JSON)).thenReturn(
                Optional.of("{\"citationMode\":\"bad\",\"instructionTemplate\":\"   \",\"sourcesTitle\":\"\",\"maxSources\":999}"),
                Optional.of("{bad"),
                Optional.empty()
        );

        CitationConfigDTO a = service.getConfig();
        CitationConfigDTO b = service.getConfig();
        CitationConfigDTO c = service.getConfigOrDefault();

        assertThat(a.getCitationMode()).isEqualTo("MODEL_INLINE");
        assertThat(a.getInstructionTemplate()).isNotBlank();
        assertThat(a.getSourcesTitle()).isEqualTo("来源");
        assertThat(a.getMaxSources()).isEqualTo(200);
        assertThat(b.getMaxSources()).isEqualTo(6);
        assertThat(c.getPostUrlTemplate()).isEqualTo("/portal/posts/detail/{postId}");
    }

    @Test
    void updateAndNormalize_shouldCoverBranchesAndErrorPath() {
        AppSettingsService appSettingsService = mock(AppSettingsService.class);
        CitationConfigService service = new CitationConfigService(appSettingsService, new ObjectMapper());

        assertThatThrownBy(() -> service.updateConfig(null)).isInstanceOf(IllegalArgumentException.class);

        CitationConfigDTO payload = new CitationConfigDTO();
        payload.setEnabled(null);
        payload.setCitationMode(" both ");
        payload.setInstructionTemplate("  hi  ");
        payload.setSourcesTitle("  t  ");
        payload.setMaxSources(-1);
        payload.setIncludeUrl(null);
        payload.setIncludeScore(true);
        payload.setIncludeTitle(null);
        payload.setIncludePostId(true);
        payload.setIncludeChunkIndex(true);
        payload.setPostUrlTemplate(" ");
        CitationConfigDTO out = service.updateConfig(payload);

        assertThat(out.getEnabled()).isFalse();
        assertThat(out.getCitationMode()).isEqualTo("BOTH");
        assertThat(out.getInstructionTemplate()).isEqualTo("hi");
        assertThat(out.getSourcesTitle()).isEqualTo("t");
        assertThat(out.getMaxSources()).isEqualTo(0);
        assertThat(out.getIncludeUrl()).isFalse();
        assertThat(out.getIncludeScore()).isTrue();
        assertThat(out.getIncludeTitle()).isFalse();
        assertThat(out.getIncludePostId()).isTrue();
        assertThat(out.getIncludeChunkIndex()).isTrue();
        assertThat(out.getPostUrlTemplate()).isEqualTo("/portal/posts/detail/{postId}");

        doThrow(new RuntimeException("db")).when(appSettingsService).upsertString(anyString(), anyString());
        assertThatThrownBy(() -> service.updateConfig(new CitationConfigDTO())).isInstanceOf(IllegalStateException.class);

        CitationConfigDTO normalizedDefault = service.normalizeConfig(null);
        assertThat(normalizedDefault.getCitationMode()).isEqualTo("MODEL_INLINE");
    }

    @Test
    void normalizeConfig_shouldKeepValidInputBranches() {
        AppSettingsService appSettingsService = mock(AppSettingsService.class);
        CitationConfigService service = new CitationConfigService(appSettingsService, new ObjectMapper());

        CitationConfigDTO in = new CitationConfigDTO();
        in.setCitationMode("sources_section");
        in.setInstructionTemplate(" ok ");
        in.setSourcesTitle(" src ");
        in.setMaxSources(9);
        in.setIncludeUrl(true);
        in.setIncludeScore(true);
        in.setIncludeTitle(true);
        in.setIncludePostId(true);
        in.setIncludeChunkIndex(true);
        in.setPostUrlTemplate("/x/{postId}");
        CitationConfigDTO out = service.normalizeConfig(in);

        assertThat(out.getCitationMode()).isEqualTo("SOURCES_SECTION");
        assertThat(out.getInstructionTemplate()).isEqualTo("ok");
        assertThat(out.getSourcesTitle()).isEqualTo("src");
        assertThat(out.getMaxSources()).isEqualTo(9);
        assertThat(out.getIncludeUrl()).isTrue();
        assertThat(out.getIncludeScore()).isTrue();
        assertThat(out.getIncludeTitle()).isTrue();
        assertThat(out.getIncludePostId()).isTrue();
        assertThat(out.getIncludeChunkIndex()).isTrue();
        assertThat(out.getPostUrlTemplate()).isEqualTo("/x/{postId}");
    }

    @Test
    void getConfig_shouldParseValidJsonBranch() {
        AppSettingsService appSettingsService = mock(AppSettingsService.class);
        CitationConfigService service = new CitationConfigService(appSettingsService, new ObjectMapper());
        when(appSettingsService.getString(CitationConfigService.KEY_CONFIG_JSON))
                .thenReturn(Optional.of("{\"citationMode\":\"both\",\"maxSources\":10,\"instructionTemplate\":\" i \"}"));

        CitationConfigDTO out = service.getConfig();
        assertThat(out.getCitationMode()).isEqualTo("BOTH");
        assertThat(out.getMaxSources()).isEqualTo(10);
        assertThat(out.getInstructionTemplate()).isEqualTo("i");
    }

    @Test
    void getConfig_shouldReturnDefaultForBlankJson() {
        AppSettingsService appSettingsService = mock(AppSettingsService.class);
        CitationConfigService service = new CitationConfigService(appSettingsService, new ObjectMapper());
        when(appSettingsService.getString(CitationConfigService.KEY_CONFIG_JSON)).thenReturn(Optional.of(" "));

        CitationConfigDTO out = service.getConfig();
        assertThat(out.getCitationMode()).isEqualTo("MODEL_INLINE");
        assertThat(out.getMaxSources()).isEqualTo(6);
    }
}
