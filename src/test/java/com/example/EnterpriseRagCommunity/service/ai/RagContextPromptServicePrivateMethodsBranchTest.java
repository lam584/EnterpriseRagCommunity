package com.example.EnterpriseRagCommunity.service.ai;

import com.example.EnterpriseRagCommunity.dto.retrieval.CitationConfigDTO;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;

class RagContextPromptServicePrivateMethodsBranchTest {

    @Test
    void renderHeader_templateBranch_should_outputEmpty_when_flagsOff_or_fieldsNull() throws Exception {
        RagContextPromptService.Item it = new RagContextPromptService.Item();
        it.setPostId(null);
        it.setChunkIndex(null);
        it.setScore(null);
        it.setTitle(null);

        String out = (String) invokePrivateStatic(
                RagContextPromptService.class,
                "renderHeader",
                new Class<?>[]{String.class, int.class, RagContextPromptService.Item.class, boolean.class, boolean.class, boolean.class, boolean.class},
                "i={i}|post={postId}|chunk={chunkIndex}|score={score}|title={title}",
                7,
                it,
                false,
                false,
                false,
                false
        );

        assertEquals("i=7|post=|chunk=|score=|title=", out);
        assertFalse(out.contains("null"));
    }

    @Test
    void buildSources_should_skipNullItem_and_postIdNull_should_renderEmptyInUrl() throws Exception {
        CitationConfigDTO cfg = new CitationConfigDTO();
        cfg.setEnabled(true);
        cfg.setMaxSources(2);
        cfg.setPostUrlTemplate("https://p/{postId}");

        RagContextPromptService.Item it1 = new RagContextPromptService.Item();
        it1.setPostId(null);

        @SuppressWarnings("unchecked")
        List<RagContextPromptService.CitationSource> out = (List<RagContextPromptService.CitationSource>) invokePrivateStatic(
                RagContextPromptService.class,
                "buildSources",
                new Class<?>[]{CitationConfigDTO.class, List.class},
                cfg,
                Arrays.asList(null, it1)
        );

        assertEquals(1, out.size());
        assertEquals(2, out.get(0).getIndex());
        assertEquals("https://p/", out.get(0).getUrl());
    }

    @Test
    void renderSourcesText_should_cover_all_earlyReturns_and_loopNullBranches() {
        assertEquals("", RagContextPromptService.renderSourcesText(null, List.of()));

        CitationConfigDTO cfg = new CitationConfigDTO();
        cfg.setEnabled(false);
        assertEquals("", RagContextPromptService.renderSourcesText(cfg, List.of()));

        cfg.setEnabled(true);
        cfg.setCitationMode(null);
        cfg.setSourcesTitle("来源");
        assertEquals("", RagContextPromptService.renderSourcesText(cfg, List.of(new RagContextPromptService.CitationSource())));

        cfg.setCitationMode("BOTH");
        assertEquals("", RagContextPromptService.renderSourcesText(cfg, null));
        assertEquals("", RagContextPromptService.renderSourcesText(cfg, List.of()));

        cfg.setSourcesTitle("   ");
        assertEquals("", RagContextPromptService.renderSourcesText(cfg, List.of(new RagContextPromptService.CitationSource())));

        cfg.setSourcesTitle("来源");
        cfg.setIncludeTitle(true);
        cfg.setIncludeUrl(true);
        cfg.setIncludeScore(true);
        cfg.setIncludePostId(true);
        cfg.setIncludeChunkIndex(true);

        RagContextPromptService.CitationSource s = new RagContextPromptService.CitationSource();
        s.setIndex(null);
        s.setTitle(null);
        s.setUrl("   ");
        s.setScore(null);
        s.setPostId(null);
        s.setChunkIndex(null);

        String txt = RagContextPromptService.renderSourcesText(cfg, Arrays.asList(null, s));
        assertEquals("来源：\n[]", txt);
    }

    @Test
    void buildPostUrl_should_cover_cfgNull_tplBlank_and_postIdNull() throws Exception {
        assertNull(invokePrivateStatic(
                RagContextPromptService.class,
                "buildPostUrl",
                new Class<?>[]{CitationConfigDTO.class, Long.class},
                null,
                1L
        ));

        CitationConfigDTO cfg = new CitationConfigDTO();
        cfg.setPostUrlTemplate("   ");
        assertNull(invokePrivateStatic(
                RagContextPromptService.class,
                "buildPostUrl",
                new Class<?>[]{CitationConfigDTO.class, Long.class},
                cfg,
                1L
        ));

        cfg.setPostUrlTemplate("x/{postId}");
        assertEquals("x/", invokePrivateStatic(
                RagContextPromptService.class,
                "buildPostUrl",
                new Class<?>[]{CitationConfigDTO.class, Long.class},
                cfg,
                null
        ));
    }

    @Test
    void normalizeTitle_should_cover_null_and_whitespaceCollapse() throws Exception {
        assertEquals("", invokePrivateStatic(
                RagContextPromptService.class,
                "normalizeTitle",
                new Class<?>[]{String.class},
                new Object[]{null}
        ));

        assertEquals("hello world", invokePrivateStatic(
                RagContextPromptService.class,
                "normalizeTitle",
                new Class<?>[]{String.class},
                "  HeLLo \n\t  WORLD   "
        ));
    }

    @Test
    void clampInt_should_cover_null_belowMin_aboveMax_and_inRange() throws Exception {
        assertEquals(5, invokePrivateStatic(
                RagContextPromptService.class,
                "clampInt",
                new Class<?>[]{Integer.class, int.class, int.class, int.class},
                null,
                1,
                9,
                5
        ));

        assertEquals(1, invokePrivateStatic(
                RagContextPromptService.class,
                "clampInt",
                new Class<?>[]{Integer.class, int.class, int.class, int.class},
                0,
                1,
                9,
                5
        ));

        assertEquals(9, invokePrivateStatic(
                RagContextPromptService.class,
                "clampInt",
                new Class<?>[]{Integer.class, int.class, int.class, int.class},
                10,
                1,
                9,
                5
        ));

        assertEquals(7, invokePrivateStatic(
                RagContextPromptService.class,
                "clampInt",
                new Class<?>[]{Integer.class, int.class, int.class, int.class},
                7,
                1,
                9,
                5
        ));
    }

    @Test
    void truncateByApproxTokens_should_cover_null_string() {
        assertEquals("", RagContextPromptService.truncateByApproxTokens(null, 10));
    }

    private static Object invokePrivateStatic(Class<?> clazz, String name, Class<?>[] paramTypes, Object... args) throws Exception {
        Method m = clazz.getDeclaredMethod(name, paramTypes);
        m.setAccessible(true);
        return m.invoke(null, args);
    }
}
