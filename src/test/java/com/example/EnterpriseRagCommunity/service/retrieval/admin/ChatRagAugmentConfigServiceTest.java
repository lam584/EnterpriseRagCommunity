package com.example.EnterpriseRagCommunity.service.retrieval.admin;

import java.lang.reflect.Method;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import org.junit.jupiter.api.Test;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.example.EnterpriseRagCommunity.dto.retrieval.ChatRagAugmentConfigDTO;
import com.example.EnterpriseRagCommunity.service.monitor.AppSettingsService;
import com.fasterxml.jackson.databind.ObjectMapper;

class ChatRagAugmentConfigServiceTest {

    @Test
    void getConfig_shouldFallbackToDefaultWhenMissingOrBrokenJson() {
        AppSettingsService appSettingsService = mock(AppSettingsService.class);
        ChatRagAugmentConfigService service = new ChatRagAugmentConfigService(appSettingsService, new ObjectMapper());

        when(appSettingsService.getString(ChatRagAugmentConfigService.KEY_CONFIG_JSON)).thenReturn(Optional.empty(), Optional.of("{bad"));

        ChatRagAugmentConfigDTO a = service.getConfig();
        ChatRagAugmentConfigDTO b = service.getConfigOrDefault();

        assertThat(a.getIncludePostContentPolicy()).isEqualTo("ON_COMMENT_HIT");
        assertThat(b.getCommentTopK()).isEqualTo(20);
    }

    @Test
    void updateConfig_shouldNormalizeAndThrowWhenPersistFails() {
        AppSettingsService appSettingsService = mock(AppSettingsService.class);
        ObjectMapper objectMapper = new ObjectMapper();
        ChatRagAugmentConfigService service = new ChatRagAugmentConfigService(appSettingsService, objectMapper);

        assertThatThrownBy(() -> service.updateConfig(null)).isInstanceOf(IllegalArgumentException.class);

        ChatRagAugmentConfigDTO payload = new ChatRagAugmentConfigDTO();
        payload.setEnabled(null);
        payload.setCommentsEnabled(null);
        payload.setCommentTopK(999);
        payload.setMaxPosts(-1);
        payload.setPerPostMaxCommentChunks(1000);
        payload.setIncludePostContentPolicy(" x ");
        payload.setPostContentMaxTokens(1);
        payload.setCommentChunkMaxTokens(500000);
        payload.setDebugEnabled(null);
        payload.setDebugMaxChars(-1);

        ChatRagAugmentConfigDTO out = service.updateConfig(payload);
        assertThat(out.getEnabled()).isTrue();
        assertThat(out.getCommentsEnabled()).isTrue();
        assertThat(out.getCommentTopK()).isEqualTo(200);
        assertThat(out.getMaxPosts()).isEqualTo(1);
        assertThat(out.getPerPostMaxCommentChunks()).isEqualTo(50);
        assertThat(out.getIncludePostContentPolicy()).isEqualTo("ON_COMMENT_HIT");
        assertThat(out.getPostContentMaxTokens()).isEqualTo(50);
        assertThat(out.getCommentChunkMaxTokens()).isEqualTo(200000);
        assertThat(out.getDebugEnabled()).isFalse();
        assertThat(out.getDebugMaxChars()).isEqualTo(0);

        doThrow(new RuntimeException("db")).when(appSettingsService).upsertString(anyString(), anyString());
        assertThatThrownBy(() -> service.updateConfig(new ChatRagAugmentConfigDTO())).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void updateConfig_shouldAcceptValidPoliciesAndFalseFlags() {
        AppSettingsService appSettingsService = mock(AppSettingsService.class);
        ChatRagAugmentConfigService service = new ChatRagAugmentConfigService(appSettingsService, new ObjectMapper());

        ChatRagAugmentConfigDTO always = new ChatRagAugmentConfigDTO();
        always.setEnabled(false);
        always.setCommentsEnabled(false);
        always.setIncludePostContentPolicy(" always ");
        always.setDebugEnabled(true);
        ChatRagAugmentConfigDTO a = service.updateConfig(always);
        assertThat(a.getEnabled()).isFalse();
        assertThat(a.getCommentsEnabled()).isFalse();
        assertThat(a.getIncludePostContentPolicy()).isEqualTo("ALWAYS");
        assertThat(a.getDebugEnabled()).isTrue();

        ChatRagAugmentConfigDTO never = new ChatRagAugmentConfigDTO();
        never.setIncludePostContentPolicy("never");
        ChatRagAugmentConfigDTO n = service.updateConfig(never);
        assertThat(n.getIncludePostContentPolicy()).isEqualTo("NEVER");
    }

    @Test
    void getConfig_shouldParseValidJsonBranch() {
        AppSettingsService appSettingsService = mock(AppSettingsService.class);
        ChatRagAugmentConfigService service = new ChatRagAugmentConfigService(appSettingsService, new ObjectMapper());
        when(appSettingsService.getString(ChatRagAugmentConfigService.KEY_CONFIG_JSON))
                .thenReturn(Optional.of("{\"includePostContentPolicy\":\" always \",\"enabled\":false,\"commentsEnabled\":false}"));

        ChatRagAugmentConfigDTO out = service.getConfig();
        assertThat(out.getIncludePostContentPolicy()).isEqualTo("ALWAYS");
        assertThat(out.getEnabled()).isFalse();
        assertThat(out.getCommentsEnabled()).isFalse();
    }

    @Test
    void trimOrNull_shouldCoverBranches() throws Exception {
        Method m = ChatRagAugmentConfigService.class.getDeclaredMethod("trimOrNull", String.class);
        m.setAccessible(true);
        assertThat(m.invoke(null, (Object) null)).isNull();
        assertThat(m.invoke(null, "   ")).isNull();
        assertThat(m.invoke(null, " x ")).isEqualTo("x");
    }

    @Test
    void normalizeAndGetConfig_shouldCoverRemainingBranches() throws Exception {
        AppSettingsService appSettingsService = mock(AppSettingsService.class);
        ChatRagAugmentConfigService service = new ChatRagAugmentConfigService(appSettingsService, new ObjectMapper());

        Method normalize = ChatRagAugmentConfigService.class.getDeclaredMethod("normalize", ChatRagAugmentConfigDTO.class);
        normalize.setAccessible(true);

        ChatRagAugmentConfigDTO nullIn = (ChatRagAugmentConfigDTO) normalize.invoke(service, new Object[]{null});
        assertThat(nullIn.getIncludePostContentPolicy()).isEqualTo("ON_COMMENT_HIT");

        ChatRagAugmentConfigDTO in = new ChatRagAugmentConfigDTO();
        in.setIncludePostContentPolicy(null);
        in.setCommentTopK(null);
        in.setMaxPosts(null);
        in.setPerPostMaxCommentChunks(null);
        ChatRagAugmentConfigDTO out = (ChatRagAugmentConfigDTO) normalize.invoke(service, in);
        assertThat(out.getIncludePostContentPolicy()).isEqualTo("ON_COMMENT_HIT");
        assertThat(out.getCommentTopK()).isEqualTo(20);

        when(appSettingsService.getString(ChatRagAugmentConfigService.KEY_CONFIG_JSON)).thenReturn(Optional.of("null"));
        ChatRagAugmentConfigDTO cfg = service.getConfig();
        assertThat(cfg.getIncludePostContentPolicy()).isEqualTo("ON_COMMENT_HIT");
    }

    @Test
    void getConfig_shouldReturnDefaultForBlankJson() {
        AppSettingsService appSettingsService = mock(AppSettingsService.class);
        ChatRagAugmentConfigService service = new ChatRagAugmentConfigService(appSettingsService, new ObjectMapper());
        when(appSettingsService.getString(ChatRagAugmentConfigService.KEY_CONFIG_JSON)).thenReturn(Optional.of(" "));

        ChatRagAugmentConfigDTO cfg = service.getConfig();
        assertThat(cfg.getIncludePostContentPolicy()).isEqualTo("ON_COMMENT_HIT");
        assertThat(cfg.getCommentTopK()).isEqualTo(20);
    }

    @Test
    void normalize_shouldKeepOnCommentHitAndInRangeValues() throws Exception {
        AppSettingsService appSettingsService = mock(AppSettingsService.class);
        ChatRagAugmentConfigService service = new ChatRagAugmentConfigService(appSettingsService, new ObjectMapper());

        Method normalize = ChatRagAugmentConfigService.class.getDeclaredMethod("normalize", ChatRagAugmentConfigDTO.class);
        normalize.setAccessible(true);

        ChatRagAugmentConfigDTO in = new ChatRagAugmentConfigDTO();
        in.setEnabled(true);
        in.setCommentsEnabled(true);
        in.setCommentTopK(20);
        in.setMaxPosts(6);
        in.setPerPostMaxCommentChunks(2);
        in.setIncludePostContentPolicy(" on_comment_hit ");
        in.setPostContentMaxTokens(1200);
        in.setCommentChunkMaxTokens(400);
        in.setDebugEnabled(false);
        in.setDebugMaxChars(4000);

        ChatRagAugmentConfigDTO out = (ChatRagAugmentConfigDTO) normalize.invoke(service, in);
        assertThat(out.getEnabled()).isTrue();
        assertThat(out.getCommentsEnabled()).isTrue();
        assertThat(out.getIncludePostContentPolicy()).isEqualTo("ON_COMMENT_HIT");
        assertThat(out.getDebugEnabled()).isFalse();
    }
}
