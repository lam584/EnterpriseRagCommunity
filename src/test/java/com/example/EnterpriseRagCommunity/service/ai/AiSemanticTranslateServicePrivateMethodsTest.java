package com.example.EnterpriseRagCommunity.service.ai;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Method;

import org.junit.jupiter.api.Test;

import com.example.EnterpriseRagCommunity.entity.ai.SemanticTranslateConfigEntity;
import com.example.EnterpriseRagCommunity.entity.semantic.PromptsEntity;

class AiSemanticTranslateServicePrivateMethodsTest {

    @Test
    void extractStreamChunkText_shouldHandleAllBranches() throws Exception {
        AiSemanticTranslateService svc = new AiSemanticTranslateService(null, null, null, null, null, null, null, null);

        Method m = AiSemanticTranslateService.class.getDeclaredMethod("extractStreamChunkText", String.class);
        m.setAccessible(true);

        assertThat(m.invoke(svc, (Object) null)).isNull();
        assertThat(m.invoke(svc, "hello")).isNull();
        assertThat(m.invoke(svc, "data: [DONE]")).isNull();
        assertThat(m.invoke(svc, "data: {bad")).isNull();
        assertThat(m.invoke(svc, "data: {\"choices\":[]}")).isNull();
        assertThat(m.invoke(svc, "data: {\"choices\":[{\"delta\":{}}]}")).isNull();
        assertThat(m.invoke(svc, "data: {\"choices\":[{\"delta\":{\"content\":\"X\"}}]}")).isEqualTo("X");
    }

    @Test
    void extractAssistantContent_shouldFallbackOnInvalidJson() throws Exception {
        AiSemanticTranslateService svc = new AiSemanticTranslateService(null, null, null, null, null, null, null, null);
        Method m = AiSemanticTranslateService.class.getDeclaredMethod("extractAssistantContent", String.class);
        m.setAccessible(true);

        String msg = "{\"choices\":[{\"message\":{\"content\":\"Hi\"}}]}";
        assertThat(m.invoke(svc, msg)).isEqualTo("Hi");

        String txt = "{\"choices\":[{\"text\":\"Hi2\"}]}";
        assertThat(m.invoke(svc, txt)).isEqualTo("Hi2");

        String noContent = "{\"choices\":[{\"message\":{\"role\":\"assistant\"}}]}";
        assertThat(m.invoke(svc, noContent)).isEqualTo(noContent);

        assertThat(m.invoke(svc, "not-json")).isEqualTo("not-json");
        assertThat(m.invoke(svc, (Object) null)).isNull();
    }

    @Test
    void parseTranslateFromAssistantText_shouldSupportWrappedJsonAndInvalidJson() throws Exception {
        AiSemanticTranslateService svc = new AiSemanticTranslateService(null, null, null, null, null, null, null, null);
        Method m = AiSemanticTranslateService.class.getDeclaredMethod("parseTranslateFromAssistantText", String.class);
        m.setAccessible(true);

        Object out1 = m.invoke(svc, (Object) null);
        assertParsed(out1, null, null);

        Object out2 = m.invoke(svc, "{\"title\":\"T\",\"markdown\":\"M\"}");
        assertParsed(out2, "T", "M");

        Object out3 = m.invoke(svc, "prefix {\"title\":\"T2\",\"markdown\":\"M2\"} suffix");
        assertParsed(out3, "T2", "M2");

        Object out4 = m.invoke(svc, "{\"title\":\"T3\"}");
        assertParsed(out4, "T3", null);

        Object out5 = m.invoke(svc, "{\"title\":123,\"markdown\":false}");
        assertParsed(out5, null, null);

        Object out6 = m.invoke(svc, "{bad");
        assertParsed(out6, null, null);
    }

    @Test
    void staticHelpers_shouldHandleNullBlankAndTruncation() throws Exception {
        Method render = AiSemanticTranslateService.class.getDeclaredMethod("renderPrompt", String.class, String.class, String.class, String.class);
        render.setAccessible(true);
        assertThat(render.invoke(null, null, " en ", " t ", " c ")).isEqualTo("");
        assertThat(render.invoke(null, "x {{targetLang}} {{title}} {{content}}", " en ", " t ", " c ")).isEqualTo("x en t c");

        Method excerpt = AiSemanticTranslateService.class.getDeclaredMethod("buildExcerpt", String.class);
        excerpt.setAccessible(true);
        assertThat(excerpt.invoke(null, (Object) null)).isNull();
        assertThat(excerpt.invoke(null, "   ")).isNull();
        assertThat(((String) excerpt.invoke(null, "x".repeat(241))).length()).isEqualTo(240);

        Method titleExcerpt = AiSemanticTranslateService.class.getDeclaredMethod("buildTitleExcerpt", String.class);
        titleExcerpt.setAccessible(true);
        assertThat(titleExcerpt.invoke(null, (Object) null)).isNull();
        assertThat(titleExcerpt.invoke(null, "   ")).isNull();
        assertThat(((String) titleExcerpt.invoke(null, "x".repeat(121))).length()).isEqualTo(120);

        Method blankToNull = AiSemanticTranslateService.class.getDeclaredMethod("blankToNull", String.class);
        blankToNull.setAccessible(true);
        assertThat(blankToNull.invoke(null, (Object) null)).isNull();
        assertThat(blankToNull.invoke(null, "   ")).isNull();
        assertThat(blankToNull.invoke(null, " x ")).isEqualTo("x");

        Method sha256 = AiSemanticTranslateService.class.getDeclaredMethod("sha256Hex", String.class);
        sha256.setAccessible(true);
        assertThat(sha256.invoke(null, (Object) null)).isNotNull();
        assertThat(((String) sha256.invoke(null, "a")).length()).isEqualTo(64);
    }

    @Test
    void buildConfigSignature_shouldCoverNullAndNonNullBranches() throws Exception {
        Method signature = AiSemanticTranslateService.class.getDeclaredMethod(
                "buildConfigSignature",
                SemanticTranslateConfigEntity.class,
                PromptsEntity.class,
                PromptLlmParams.class
        );
        signature.setAccessible(true);

        SemanticTranslateConfigEntity cfgNull = new SemanticTranslateConfigEntity();
        PromptsEntity promptNull = new PromptsEntity();
        PromptLlmParams paramsNull = new PromptLlmParams(null, null, null, null, null, null);
        String s1 = (String) signature.invoke(null, cfgNull, promptNull, paramsNull);
        assertThat(s1).contains("providerId=");
        assertThat(s1).contains("|model=");
        assertThat(s1).contains("|temp=");
        assertThat(s1).contains("|topP=");
        assertThat(s1).contains("|thinking=0");
        assertThat(s1).contains("|max=");
        assertThat(s1).contains("|sp=");
        assertThat(s1).contains("|pt=");

        SemanticTranslateConfigEntity cfg = new SemanticTranslateConfigEntity();
        cfg.setMaxContentChars(1234);
        PromptsEntity prompt = new PromptsEntity();
        prompt.setSystemPrompt("  SYS  ");
        prompt.setUserPromptTemplate("  USER  ");
        PromptLlmParams params = new PromptLlmParams("  P1  ", "  M1  ", 0.33, 0.44, 1024, true);
        String s2 = (String) signature.invoke(null, cfg, prompt, params);
        assertThat(s2).contains("providerId=P1");
        assertThat(s2).contains("|model=M1");
        assertThat(s2).contains("|temp=0.33");
        assertThat(s2).contains("|topP=0.44");
        assertThat(s2).contains("|thinking=1");
        assertThat(s2).contains("|max=1234");
        assertThat(s2).contains("|sp=SYS");
        assertThat(s2).contains("|pt=USER");
    }

    private static void assertParsed(Object parsed, String expectedTitle, String expectedMarkdown) throws Exception {
        Method title = parsed.getClass().getDeclaredMethod("title");
        Method markdown = parsed.getClass().getDeclaredMethod("markdown");
        title.setAccessible(true);
        markdown.setAccessible(true);
        assertThat(title.invoke(parsed)).isEqualTo(expectedTitle);
        assertThat(markdown.invoke(parsed)).isEqualTo(expectedMarkdown);
    }
}
