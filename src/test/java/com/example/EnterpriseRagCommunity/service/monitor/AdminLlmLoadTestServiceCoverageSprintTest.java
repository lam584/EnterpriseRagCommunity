package com.example.EnterpriseRagCommunity.service.monitor;

import com.example.EnterpriseRagCommunity.dto.monitor.AdminLlmLoadTestResultDTO;
import com.example.EnterpriseRagCommunity.dto.monitor.AdminLlmQueueSampleDTO;
import com.example.EnterpriseRagCommunity.dto.monitor.AdminLlmQueueStatusDTO;
import com.example.EnterpriseRagCommunity.entity.ai.LlmModelEntity;
import com.example.EnterpriseRagCommunity.entity.ai.LlmPriceConfigEntity;
import com.example.EnterpriseRagCommunity.repository.ai.LlmModelRepository;
import com.example.EnterpriseRagCommunity.repository.ai.LlmPriceConfigRepository;
import com.example.EnterpriseRagCommunity.repository.monitor.LlmLoadTestRunDetailRepository;
import com.example.EnterpriseRagCommunity.repository.monitor.LlmLoadTestRunHistoryRepository;
import com.example.EnterpriseRagCommunity.repository.semantic.PromptsRepository;
import com.example.EnterpriseRagCommunity.service.ai.LlmGateway;
import com.example.EnterpriseRagCommunity.service.ai.AiProvidersConfigService;
import com.example.EnterpriseRagCommunity.service.ai.TokenCountService;
import com.example.EnterpriseRagCommunity.service.ai.dto.ChatMessage;
import com.example.EnterpriseRagCommunity.service.moderation.admin.AdminModerationLlmService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.math.BigDecimal;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.LongAdder;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AdminLlmLoadTestServiceCoverageSprintTest {

    @Test
    void helperPaths_should_cover_append_extract_and_supports_thinking_branches() throws Exception {
        StringBuilder out = new StringBuilder();
        invokeStatic("appendStreamDelta", new Class[]{String.class, StringBuilder.class, boolean.class}, new Object[]{null, out, false});
        invokeStatic("appendStreamDelta", new Class[]{String.class, StringBuilder.class, boolean.class}, new Object[]{"   ", out, false});
        invokeStatic("appendStreamDelta", new Class[]{String.class, StringBuilder.class, boolean.class}, new Object[]{"event: ping", out, false});
        invokeStatic("appendStreamDelta", new Class[]{String.class, StringBuilder.class, boolean.class}, new Object[]{"data: [DONE]", out, false});
        invokeStatic("appendStreamDelta", new Class[]{String.class, StringBuilder.class, boolean.class}, new Object[]{"data: {}", out, false});
        invokeStatic("appendStreamDelta", new Class[]{String.class, StringBuilder.class, boolean.class}, new Object[]{"data: {\"reasoning_content\":\"r\",\"content\":\"c\"}", out, true});
        invokeStatic("appendStreamDelta", new Class[]{String.class, StringBuilder.class, boolean.class}, new Object[]{"data: {\"reasoning_content\":\"x\",\"text\":\"t\"}", out, false});
        assertEquals("rct", out.toString());

        assertTrue((Boolean) invokeStatic("supportsThinkingDirectiveModel", new Class[]{String.class}, new Object[]{"provider/qwen3-14b"}));
        assertTrue((Boolean) invokeStatic("supportsThinkingDirectiveModel", new Class[]{String.class}, new Object[]{"x:y/qwen-plus-2025-04-28"}));
        assertTrue((Boolean) invokeStatic("supportsThinkingDirectiveModel", new Class[]{String.class}, new Object[]{"qwen-turbo-2025-04-28"}));
        assertFalse((Boolean) invokeStatic("supportsThinkingDirectiveModel", new Class[]{String.class}, new Object[]{"provider/qwen3-thinking"}));
        assertFalse((Boolean) invokeStatic("supportsThinkingDirectiveModel", new Class[]{String.class}, new Object[]{"provider:x:"}));

        AdminLlmLoadTestService svc = newService(mock(TokenCountService.class), mock(LlmQueueMonitorService.class));
        Method extractAssistant = AdminLlmLoadTestService.class.getDeclaredMethod("extractAssistantContentFromRawModelOutput", String.class);
        extractAssistant.setAccessible(true);
        assertEquals("m", extractAssistant.invoke(svc, "{\"choices\":[{\"message\":{\"content\":\"m\"}}]}"));
        assertEquals("t", extractAssistant.invoke(svc, "{\"choices\":[{\"text\":\"t\"}]}"));
        assertEquals("d", extractAssistant.invoke(svc, "{\"choices\":[{\"delta\":{\"content\":\"d\"}}]}"));
        assertEquals("o", extractAssistant.invoke(svc, "{\"output_text\":\"o\"}"));
        assertEquals("f", extractAssistant.invoke(svc, "{\"content\":\"f\"}"));
        assertEquals("bad", extractAssistant.invoke(svc, "bad"));
        svc.shutdown();
    }

    @Test
    void maybeRecomputePaths_should_cover_force_recompute_delta_and_model_fallback_branches() throws Exception {
        TokenCountService tokenCountService = mock(TokenCountService.class);
        when(tokenCountService.countChatMessagesTokens(any())).thenReturn(5, (Integer) null);
        when(tokenCountService.countTextTokens(anyString())).thenReturn(7);

        AdminLlmLoadTestService svc = newService(tokenCountService, mock(LlmQueueMonitorService.class));
        Object cfg = newNormalizedConfig(1, 1, 0, 1, null, "fallback-model", false, 1000, 0, 0, "c", "m");
        Object st = newRunState(svc, "run-sprint", cfg);

        Method maybeRecompute = AdminLlmLoadTestService.class.getDeclaredMethod(
                "maybeRecomputeTokensAsync",
                st.getClass(),
                AdminLlmLoadTestResultDTO.class,
                newResultClass()
        );
        maybeRecompute.setAccessible(true);

        AdminLlmLoadTestResultDTO dto = new AdminLlmLoadTestResultDTO();
        dto.setKind("MODERATION_TEST");
        dto.setTokensIn(1);
        dto.setTokensOut(2);
        dto.setTokens(3);
        dto.setModel(null);
        Object result = newResult(
                1L,
                null,
                null,
                null,
                null,
                List.of(ChatMessage.system("s"), ChatMessage.user("u")),
                "out-text",
                "{}",
                null
        );
        Field cancelledField = st.getClass().getDeclaredField("cancelled");
        cancelledField.setAccessible(true);
        ((java.util.concurrent.atomic.AtomicBoolean) cancelledField.get(st)).set(true);
        maybeRecompute.invoke(svc, st, new AdminLlmLoadTestResultDTO(), result);
        ((java.util.concurrent.atomic.AtomicBoolean) cancelledField.get(st)).set(false);

        maybeRecompute.invoke(svc, st, dto, result);
        TimeUnit.MILLISECONDS.sleep(150);
        assertEquals(5, dto.getTokensIn());
        assertEquals(2, dto.getTokensOut());
        assertEquals(7, dto.getTokens());

        Field tokensByModelField = st.getClass().getDeclaredField("tokensByModel");
        tokensByModelField.setAccessible(true);
        Map<?, ?> tokensByModel = (Map<?, ?>) tokensByModelField.get(st);
        assertTrue(tokensByModel.containsKey("fallback-model"));

        Field inField = st.getClass().getDeclaredField("totalTokensIn");
        inField.setAccessible(true);
        assertEquals(4L, ((LongAdder) inField.get(st)).sum());

        AdminLlmLoadTestResultDTO unchanged = new AdminLlmLoadTestResultDTO();
        unchanged.setKind("CHAT_STREAM");
        unchanged.setTokensIn(5);
        unchanged.setTokensOut(2);
        unchanged.setTokens(7);
        unchanged.setModel("fallback-model");
        maybeRecompute.invoke(svc, st, unchanged, result);
        TimeUnit.MILLISECONDS.sleep(120);
        assertEquals(5, unchanged.getTokensIn());
        assertEquals(2, unchanged.getTokensOut());
        assertEquals(7, unchanged.getTokens());

        AdminLlmLoadTestResultDTO outNull = new AdminLlmLoadTestResultDTO();
        outNull.setKind("CHAT_STREAM");
        outNull.setTokensIn(2);
        outNull.setTokensOut(null);
        outNull.setTokens(2);
        outNull.setModel("p/a:model-z");
        maybeRecompute.invoke(svc, st, outNull, result);
        TimeUnit.MILLISECONDS.sleep(120);
        assertEquals(2, outNull.getTokensIn());
        assertEquals(7, outNull.getTokensOut());
        assertEquals(9, outNull.getTokens());

        Object cfgNoModel = newNormalizedConfig(1, 1, 0, 1, null, " ", false, 1000, 0, 0, "c", "m");
        Object stNoModel = newRunState(svc, "run-nomodel", cfgNoModel);
        AdminLlmLoadTestResultDTO dtoNoModel = new AdminLlmLoadTestResultDTO();
        dtoNoModel.setKind("MODERATION_TEST");
        dtoNoModel.setTokensIn(null);
        dtoNoModel.setTokensOut(null);
        dtoNoModel.setTokens(null);
        dtoNoModel.setModel(" ");
        maybeRecompute.invoke(svc, stNoModel, dtoNoModel, result);
        TimeUnit.MILLISECONDS.sleep(150);
        Field mapNoModelField = stNoModel.getClass().getDeclaredField("tokensByModel");
        mapNoModelField.setAccessible(true);
        assertTrue(((Map<?, ?>) mapNoModelField.get(stNoModel)).isEmpty());
        svc.shutdown();
    }

    @Test
    void pricing_and_cost_paths_should_cover_resolve_prices_and_cost_info_branches() throws Exception {
        LlmModelRepository modelRepo = mock(LlmModelRepository.class);
        LlmPriceConfigRepository priceRepo = mock(LlmPriceConfigRepository.class);

        LlmModelEntity me1 = new LlmModelEntity();
        me1.setModelName("provider/a:modelA");
        me1.setPriceConfigId(1L);
        LlmModelEntity me2 = new LlmModelEntity();
        me2.setModelName("modelB");
        me2.setPriceConfigId(0L);
        LlmModelEntity me3 = new LlmModelEntity();
        me3.setModelName(" ");
        me3.setPriceConfigId(2L);
        when(modelRepo.findByEnvAndEnabledTrueAndModelNameInOrderByIsDefaultDescWeightDescIdAsc(anyString(), any()))
                .thenReturn(null)
                .thenReturn(Arrays.asList(null, me1, me2, me3));

        LlmPriceConfigEntity p1 = new LlmPriceConfigEntity();
        p1.setId(1L);
        p1.setCurrency("usd");
        p1.setInputCostPer1k(BigDecimal.ONE);
        p1.setOutputCostPer1k(BigDecimal.ONE);
        LlmPriceConfigEntity p2 = new LlmPriceConfigEntity();
        p2.setId(9L);
        p2.setName("modelB");
        p2.setCurrency("usd");
        p2.setInputCostPer1k(BigDecimal.ONE);
        p2.setOutputCostPer1k(BigDecimal.ONE);
        when(priceRepo.findByIdIn(any())).thenReturn(Arrays.asList(null, p1));
        when(priceRepo.findByNameIn(any())).thenReturn(Arrays.asList(null, p2));

        AdminLlmLoadTestService svc = new AdminLlmLoadTestService(
                mock(LlmGateway.class),
                mock(AdminModerationLlmService.class),
                mock(LlmQueueMonitorService.class),
                mock(TokenCountService.class),
                new ObjectMapper(),
                modelRepo,
                priceRepo,
                mock(LlmLoadTestRunDetailRepository.class),
                mock(LlmLoadTestRunHistoryRepository.class),
                mock(PromptsRepository.class)
        );

        Method resolvePrices = AdminLlmLoadTestService.class.getDeclaredMethod("resolvePrices", java.util.Collection.class);
        resolvePrices.setAccessible(true);
        Map<?, ?> r1 = (Map<?, ?>) resolvePrices.invoke(svc, List.of("modelA"));
        assertTrue(r1.isEmpty());
        Map<?, ?> r2 = (Map<?, ?>) resolvePrices.invoke(svc, Arrays.asList("provider/a:modelA", "modelB", null, " "));
        assertEquals(2, r2.size());

        Class<?> modelAggClass = Class.forName(AdminLlmLoadTestService.class.getName() + "$ModelAgg");
        Constructor<?> aggCtor = modelAggClass.getDeclaredConstructor();
        aggCtor.setAccessible(true);
        Object agg = aggCtor.newInstance();
        Field inField = modelAggClass.getDeclaredField("in");
        inField.setAccessible(true);
        Field outField = modelAggClass.getDeclaredField("out");
        outField.setAccessible(true);
        ((LongAdder) inField.get(agg)).add(1000L);
        ((LongAdder) outField.get(agg)).add(2000L);

        Map<String, Object> byModel = new HashMap<>();
        byModel.put("provider/a:modelA", agg);
        byModel.put("modelB", agg);
        byModel.put(" ", agg);
        byModel.put("unknown", null);

        Class<?> pricingModeClass = Class.forName("com.example.EnterpriseRagCommunity.service.monitor.LlmPricing$Mode");
        Object mode = Enum.valueOf((Class<Enum>) pricingModeClass.asSubclass(Enum.class), "DEFAULT");
        Method computeCostInfo = AdminLlmLoadTestService.class.getDeclaredMethod("computeCostInfo", Map.class, pricingModeClass);
        computeCostInfo.setAccessible(true);
        Object cost = computeCostInfo.invoke(svc, byModel, mode);
        assertNotNull(cost);
        Method currency = cost.getClass().getDeclaredMethod("currency");
        currency.setAccessible(true);
        assertEquals("USD", currency.invoke(cost));
        assertTrue(computeCostInfo.invoke(svc, new HashMap<>(), mode) == null);
        svc.shutdown();
    }

    @Test
    void detail_and_csv_helpers_should_cover_null_blank_long_and_quote_branches() throws Exception {
        AdminLlmLoadTestService svc = newService(mock(TokenCountService.class), mock(LlmQueueMonitorService.class));
        Object cfg = newNormalizedConfig(1, 1, 0, 1, "cfg-pid", "cfg-model", false, 1000, 0, 0, "c", "m");
        Object st = newRunState(svc, "run-detail", cfg);

        Method buildDetail = AdminLlmLoadTestService.class.getDeclaredMethod(
                "buildDetailEntity",
                st.getClass(),
                AdminLlmLoadTestResultDTO.class,
                String.class,
                String.class,
                String.class
        );
        buildDetail.setAccessible(true);
        assertNull(buildDetail.invoke(svc, new Object[]{null, new AdminLlmLoadTestResultDTO(), null, null, null}));
        assertNull(buildDetail.invoke(svc, st, null, null, null, null));

        AdminLlmLoadTestResultDTO dto = new AdminLlmLoadTestResultDTO();
        dto.setIndex(1);
        dto.setKind("K");
        dto.setOk(true);
        dto.setStartedAtMs(1L);
        dto.setFinishedAtMs(2L);
        dto.setLatencyMs(3L);
        dto.setModel(" ");
        dto.setTokensIn(1);
        dto.setTokensOut(2);
        dto.setTokens(3);
        dto.setError(" ");
        String longReq = "a".repeat(25000);
        Object entity = buildDetail.invoke(svc, st, dto, " ", longReq, null);
        assertNotNull(entity);
        Method getProviderId = entity.getClass().getMethod("getProviderId");
        Method getError = entity.getClass().getMethod("getError");
        Method getRequestTruncated = entity.getClass().getMethod("getRequestTruncated");
        Method getResponseTruncated = entity.getClass().getMethod("getResponseTruncated");
        assertEquals("cfg-pid", getProviderId.invoke(entity));
        assertNull(getError.invoke(entity));
        assertTrue((Boolean) getRequestTruncated.invoke(entity));
        assertFalse((Boolean) getResponseTruncated.invoke(entity));

        Method truncateError = AdminLlmLoadTestService.class.getDeclaredMethod("truncateError", String.class);
        truncateError.setAccessible(true);
        assertNull(truncateError.invoke(svc, new Object[]{null}));
        assertNull(truncateError.invoke(svc, " "));
        assertEquals("x", truncateError.invoke(svc, "x"));
        assertEquals(1024, String.valueOf(truncateError.invoke(svc, "z".repeat(3000))).length());

        assertEquals("", invokeStatic("csvEscape", new Class[]{String.class}, new Object[]{null}));
        assertEquals("abc", invokeStatic("csvEscape", new Class[]{String.class}, new Object[]{"abc"}));
        assertEquals("\"a,b\"", invokeStatic("csvEscape", new Class[]{String.class}, new Object[]{"a,b"}));
        assertEquals("\"a\"\"b\"", invokeStatic("csvEscape", new Class[]{String.class}, new Object[]{"a\"b"}));
        assertEquals("\"a\nb\"", invokeStatic("csvEscape", new Class[]{String.class}, new Object[]{"a\nb"}));
        assertEquals("\"a\rb\"", invokeStatic("csvEscape", new Class[]{String.class}, new Object[]{"a\rb"}));
        svc.shutdown();
    }

    @Test
    void extract_field_and_delta_should_cover_malformed_and_escape_branches() throws Exception {
        assertNull(invokeStatic("extractDeltaStringField", new Class[]{String.class, String.class}, new Object[]{null, "content"}));
        assertNull(invokeStatic("extractDeltaStringField", new Class[]{String.class, String.class}, new Object[]{"{}", " "}));
        assertNull(invokeStatic("extractDeltaStringField", new Class[]{String.class, String.class}, new Object[]{"{\"x\":\"1\"}", "content"}));
        assertNull(invokeStatic("extractDeltaStringField", new Class[]{String.class, String.class}, new Object[]{"{\"content\" \"x\"}", "content"}));
        assertNull(invokeStatic("extractDeltaStringField", new Class[]{String.class, String.class}, new Object[]{"{\"content\":1}", "content"}));
        assertEquals("abc", invokeStatic("extractDeltaStringField", new Class[]{String.class, String.class}, new Object[]{"{\"content\":\"abc\"}", "content"}));
        assertEquals("\"\\/\b\f\n\r\t你", invokeStatic(
                "extractDeltaStringField",
                new Class[]{String.class, String.class},
                new Object[]{"{\"content\":\"\\\"\\\\\\/\\b\\f\\n\\r\\t\\u4f60\\u12zx\"}", "content"}
        ));
        assertEquals("abc}", invokeStatic("extractDeltaStringField", new Class[]{String.class, String.class}, new Object[]{"{\"content\":\"abc}", "content"}));

        assertNull(invokeStatic("extractDeltaText", new Class[]{String.class, boolean.class}, new Object[]{"{}", false}));
        assertEquals("r", invokeStatic("extractDeltaText", new Class[]{String.class, boolean.class}, new Object[]{"{\"reasoning_content\":\"r\",\"text\":\"t\"}", true}));
        assertEquals("t", invokeStatic("extractDeltaText", new Class[]{String.class, boolean.class}, new Object[]{"{\"reasoning_content\":\"r\",\"text\":\"t\"}", false}));
    }

    @Test
    void sampleQueue_and_start_overflow_should_cover_remaining_fast_branches() throws Exception {
        LlmQueueMonitorService queueSvc = mock(LlmQueueMonitorService.class);
        AdminLlmQueueStatusDTO s1 = new AdminLlmQueueStatusDTO();
        s1.setPendingCount(-1);
        s1.setRunningCount(null);
        AdminLlmQueueSampleDTO n1 = new AdminLlmQueueSampleDTO();
        n1.setTokensPerSec(-2.0);
        s1.setSamples(List.of(n1));
        AdminLlmQueueStatusDTO s2 = new AdminLlmQueueStatusDTO();
        s2.setPendingCount(2);
        s2.setRunningCount(3);
        AdminLlmQueueSampleDTO n2 = new AdminLlmQueueSampleDTO();
        n2.setTokensPerSec(4.0);
        s2.setSamples(Arrays.asList(null, n2));
        when(queueSvc.query(any(), any(), any(), any())).thenReturn(null, s1, s2);

        TokenCountService tokenCountService = mock(TokenCountService.class);
        when(tokenCountService.normalizeOutputText(anyString(), anyBoolean())).thenReturn(
                new TokenCountService.NormalizedOutput("raw", "disp", "token", false, false)
        );

        AdminModerationLlmService moderationSvc = mock(AdminModerationLlmService.class);
        when(moderationSvc.test(any())).thenReturn(new com.example.EnterpriseRagCommunity.dto.moderation.LlmModerationTestResponse());

        AdminLlmLoadTestService svc = new AdminLlmLoadTestService(
                mock(LlmGateway.class),
                moderationSvc,
                queueSvc,
                tokenCountService,
                new ObjectMapper(),
                mock(LlmModelRepository.class),
                mock(LlmPriceConfigRepository.class),
                mock(LlmLoadTestRunDetailRepository.class),
                mock(LlmLoadTestRunHistoryRepository.class),
                mock(PromptsRepository.class)
        );

        Object cfg = newNormalizedConfig(1, 1, 0, 1, null, "qwen3-8b", false, 1000, 0, 0, "c", "m");
        Object st = newRunState(svc, "run-queue", cfg);
        Method sampleQueue = AdminLlmLoadTestService.class.getDeclaredMethod("sampleQueue", st.getClass());
        sampleQueue.setAccessible(true);
        sampleQueue.invoke(svc, st);
        sampleQueue.invoke(svc, st);
        sampleQueue.invoke(svc, st);

        Field maxPendingField = st.getClass().getDeclaredField("queueMaxPending");
        maxPendingField.setAccessible(true);
        Field maxRunningField = st.getClass().getDeclaredField("queueMaxRunning");
        maxRunningField.setAccessible(true);
        Field maxTotalField = st.getClass().getDeclaredField("queueMaxTotal");
        maxTotalField.setAccessible(true);
        assertEquals(2, ((AtomicInteger) maxPendingField.get(st)).get());
        assertEquals(3, ((AtomicInteger) maxRunningField.get(st)).get());
        assertEquals(5, ((AtomicInteger) maxTotalField.get(st)).get());

        Field countField = st.getClass().getDeclaredField("queueTokensPerSecCount");
        countField.setAccessible(true);
        assertEquals(1L, ((LongAdder) countField.get(st)).sum());

        com.example.EnterpriseRagCommunity.dto.monitor.AdminLlmLoadTestRunRequestDTO req = new com.example.EnterpriseRagCommunity.dto.monitor.AdminLlmLoadTestRunRequestDTO();
        req.setConcurrency(1);
        req.setTotalRequests(1);
        req.setStream(false);
        req.setRatioChatStream(0);
        req.setRatioModerationTest(100);
        req.setRetries(0);
        req.setTimeoutMs(1000);
        req.setModel("qwen3-8b");
        req.setModerationText("ok");
        String oldest = null;
        for (int i = 0; i < 21; i++) {
            String runId = svc.start(req);
            assertNotNull(runId);
            if (i == 0) oldest = runId;
        }
        assertNotNull(oldest);
        assertFalse(svc.stop(oldest));
        svc.shutdown();
    }

    @Test
    void think_and_model_helpers_should_cover_remaining_support_and_normalize_branches() throws Exception {
        assertFalse((Boolean) invokeStatic("supportsThinkingDirectiveModel", new Class[]{String.class}, new Object[]{"   "}));
        assertFalse((Boolean) invokeStatic("supportsThinkingDirectiveModel", new Class[]{String.class}, new Object[]{"a/"}));
        assertFalse((Boolean) invokeStatic("supportsThinkingDirectiveModel", new Class[]{String.class}, new Object[]{"a:b:"}));
        assertTrue((Boolean) invokeStatic("supportsThinkingDirectiveModel", new Class[]{String.class}, new Object[]{"x/qwen-turbo-2025-04-28"}));
        assertTrue((Boolean) invokeStatic("supportsThinkingDirectiveModel", new Class[]{String.class}, new Object[]{"qwen-plus-2025-04-28"}));
        assertFalse((Boolean) invokeStatic("supportsThinkingDirectiveModel", new Class[]{String.class}, new Object[]{"qwen3-32b-thinking"}));

        assertEquals("\n/think", invokeStatic("applyThinkingDirective", new Class[]{String.class, boolean.class, String.class}, new Object[]{null, true, "qwen3-8b"}));
        assertEquals("x\r/no_think", invokeStatic("applyThinkingDirective", new Class[]{String.class, boolean.class, String.class}, new Object[]{"x\r", false, "qwen3-8b"}));
        assertEquals("x/no_think", invokeStatic("applyThinkingDirective", new Class[]{String.class, boolean.class, String.class}, new Object[]{"x/no_think", true, "qwen3-8b"}));
        assertEquals("x/think", invokeStatic("applyThinkingDirective", new Class[]{String.class, boolean.class, String.class}, new Object[]{"x/think", false, "qwen3-8b"}));

        assertNull(invokeStatic("normalizeModel", new Class[]{String.class}, new Object[]{null}));
        assertNull(invokeStatic("normalizeModel", new Class[]{String.class}, new Object[]{" "}));
        assertEquals("a/", invokeStatic("normalizeModel", new Class[]{String.class}, new Object[]{"a/"}));
        assertEquals("a:b:", invokeStatic("normalizeModel", new Class[]{String.class}, new Object[]{"a:b:"}));
        assertEquals("m", invokeStatic("normalizeModel", new Class[]{String.class}, new Object[]{"/m"}));
        assertEquals("m", invokeStatic("normalizeModel", new Class[]{String.class}, new Object[]{":m"}));

        LlmGateway gateway = mock(LlmGateway.class);
        when(gateway.resolve("pid-ok")).thenReturn(new AiProvidersConfigService.ResolvedProvider(
                "pid-ok", "openai_compat", "u", "k", "qwen3-8b", null, Map.of(), Map.of(), 1000, 1000
        ));
        when(gateway.resolve("pid-blank")).thenReturn(new AiProvidersConfigService.ResolvedProvider(
                "pid-blank", "openai_compat", "u", "k", "  ", null, Map.of(), Map.of(), 1000, 1000
        ));
        AdminLlmLoadTestService svc = new AdminLlmLoadTestService(
                gateway,
                mock(AdminModerationLlmService.class),
                mock(LlmQueueMonitorService.class),
                mock(TokenCountService.class),
                new ObjectMapper(),
                mock(LlmModelRepository.class),
                mock(LlmPriceConfigRepository.class),
                mock(LlmLoadTestRunDetailRepository.class),
                mock(LlmLoadTestRunHistoryRepository.class),
                mock(PromptsRepository.class)
        );
        Method resolveModelName = AdminLlmLoadTestService.class.getDeclaredMethod("resolveModelNameForThinkDirective", String.class, String.class);
        resolveModelName.setAccessible(true);
        assertEquals("override", resolveModelName.invoke(svc, "pid-ok", "override"));
        assertEquals("qwen3-8b", resolveModelName.invoke(svc, "pid-ok", null));
        assertNull(resolveModelName.invoke(svc, "pid-blank", null));
        assertNull(resolveModelName.invoke(svc, " ", null));
        svc.shutdown();
    }

    @Test
    void sample_queue_and_cost_should_cover_remaining_mix_and_missing_branches() throws Exception {
        LlmQueueMonitorService queueSvc = mock(LlmQueueMonitorService.class);
        AdminLlmQueueStatusDTO s1 = new AdminLlmQueueStatusDTO();
        s1.setPendingCount(null);
        s1.setRunningCount(-9);
        s1.setSamples(null);
        AdminLlmQueueStatusDTO s2 = new AdminLlmQueueStatusDTO();
        s2.setPendingCount(1);
        s2.setRunningCount(1);
        s2.setSamples(List.of());
        AdminLlmQueueStatusDTO s3 = new AdminLlmQueueStatusDTO();
        s3.setPendingCount(1);
        s3.setRunningCount(1);
        s3.setSamples(Arrays.asList(new AdminLlmQueueSampleDTO(), null));
        AdminLlmQueueStatusDTO s4 = new AdminLlmQueueStatusDTO();
        s4.setPendingCount(3);
        s4.setRunningCount(4);
        AdminLlmQueueSampleDTO n4 = new AdminLlmQueueSampleDTO();
        n4.setTokensPerSec(null);
        s4.setSamples(List.of(n4));
        when(queueSvc.query(any(), any(), any(), any())).thenReturn(s1, s2, s3, s4);

        AdminLlmLoadTestService svc = newService(mock(TokenCountService.class), queueSvc);
        Object cfg = newNormalizedConfig(1, 1, 0, 1, "cfg", "m", false, 1000, 0, 0, "c", "m");
        Object st = newRunState(svc, "run-sample", cfg);
        Method sampleQueue = AdminLlmLoadTestService.class.getDeclaredMethod("sampleQueue", st.getClass());
        sampleQueue.setAccessible(true);
        sampleQueue.invoke(svc, st);
        sampleQueue.invoke(svc, st);
        sampleQueue.invoke(svc, st);
        sampleQueue.invoke(svc, st);
        Field maxTotalField = st.getClass().getDeclaredField("queueMaxTotal");
        maxTotalField.setAccessible(true);
        assertEquals(7, ((AtomicInteger) maxTotalField.get(st)).get());
        svc.shutdown();

        LlmModelRepository modelRepo = mock(LlmModelRepository.class);
        LlmPriceConfigRepository priceRepo = mock(LlmPriceConfigRepository.class);
        LlmModelEntity ma = new LlmModelEntity();
        ma.setModelName("modelA");
        ma.setPriceConfigId(1L);
        LlmModelEntity mb = new LlmModelEntity();
        mb.setModelName("modelB");
        mb.setPriceConfigId(2L);
        when(modelRepo.findByEnvAndEnabledTrueAndModelNameInOrderByIsDefaultDescWeightDescIdAsc(anyString(), any())).thenReturn(List.of(ma, mb));
        LlmPriceConfigEntity pa = new LlmPriceConfigEntity();
        pa.setId(1L);
        pa.setCurrency("usd");
        pa.setInputCostPer1k(BigDecimal.ONE);
        pa.setOutputCostPer1k(BigDecimal.ONE);
        LlmPriceConfigEntity pb = new LlmPriceConfigEntity();
        pb.setId(2L);
        pb.setCurrency("cny");
        pb.setInputCostPer1k(BigDecimal.ONE);
        pb.setOutputCostPer1k(BigDecimal.ONE);
        when(priceRepo.findByIdIn(any())).thenReturn(List.of(pa, pb));
        when(priceRepo.findByNameIn(any())).thenReturn(List.of());
        AdminLlmLoadTestService svc2 = new AdminLlmLoadTestService(
                mock(LlmGateway.class),
                mock(AdminModerationLlmService.class),
                mock(LlmQueueMonitorService.class),
                mock(TokenCountService.class),
                new ObjectMapper(),
                modelRepo,
                priceRepo,
                mock(LlmLoadTestRunDetailRepository.class),
                mock(LlmLoadTestRunHistoryRepository.class),
                mock(PromptsRepository.class)
        );
        Class<?> modelAggClass = Class.forName(AdminLlmLoadTestService.class.getName() + "$ModelAgg");
        Constructor<?> aggCtor = modelAggClass.getDeclaredConstructor();
        aggCtor.setAccessible(true);
        Object agg = aggCtor.newInstance();
        Field inField = modelAggClass.getDeclaredField("in");
        inField.setAccessible(true);
        Field outField = modelAggClass.getDeclaredField("out");
        outField.setAccessible(true);
        ((LongAdder) inField.get(agg)).add(100L);
        ((LongAdder) outField.get(agg)).add(200L);
        Map<String, Object> byModel = new HashMap<>();
        byModel.put("modelA", agg);
        byModel.put("modelB", agg);
        byModel.put("missingModel", agg);
        Class<?> pricingModeClass = Class.forName("com.example.EnterpriseRagCommunity.service.monitor.LlmPricing$Mode");
        Object mode = Enum.valueOf((Class<Enum>) pricingModeClass.asSubclass(Enum.class), "DEFAULT");
        Method computeCostInfo = AdminLlmLoadTestService.class.getDeclaredMethod("computeCostInfo", Map.class, pricingModeClass);
        computeCostInfo.setAccessible(true);
        Object cost = computeCostInfo.invoke(svc2, byModel, mode);
        Method currency = cost.getClass().getDeclaredMethod("currency");
        currency.setAccessible(true);
        Method priceMissing = cost.getClass().getDeclaredMethod("priceMissing");
        priceMissing.setAccessible(true);
        assertEquals("MIXED", currency.invoke(cost));
        assertTrue((Boolean) priceMissing.invoke(cost));
        svc2.shutdown();
    }

    @Test
    void hotspot_methods_should_cover_lambda_resolve_extract_and_cost_remaining_branches() throws Exception {
        TokenCountService tokenCountService = mock(TokenCountService.class);
        when(tokenCountService.countChatMessagesTokens(any())).thenAnswer(inv -> {
            List<?> msgs = inv.getArgument(0);
            if (msgs == null || msgs.isEmpty()) return null;
            Object first = msgs.get(0);
            if (first instanceof ChatMessage cm && "NULL_IN".equals(cm.content())) return null;
            return 9;
        });
        when(tokenCountService.countTextTokens(anyString())).thenAnswer(inv -> {
            String v = inv.getArgument(0);
            if ("NULL_OUT".equals(v)) return null;
            return 7;
        });

        AdminLlmLoadTestService svc = newService(tokenCountService, mock(LlmQueueMonitorService.class));
        Object cfg = newNormalizedConfig(1, 1, 0, 1, null, "fallback-model", false, 1000, 0, 0, "c", "m");
        Object st = newRunState(svc, "run-hot", cfg);
        Method lambda = AdminLlmLoadTestService.class.getDeclaredMethod(
                "lambda$maybeRecomputeTokensAsync$10",
                st.getClass(),
                AdminLlmLoadTestResultDTO.class,
                List.class,
                newResultClass()
        );
        lambda.setAccessible(true);

        Field cancelledField = st.getClass().getDeclaredField("cancelled");
        cancelledField.setAccessible(true);
        ((java.util.concurrent.atomic.AtomicBoolean) cancelledField.get(st)).set(true);
        lambda.invoke(svc, st, new AdminLlmLoadTestResultDTO(), List.of(ChatMessage.user("x")), newResult(1L, null, null, null, null, List.of(ChatMessage.user("x")), "x", "{}", null));
        ((java.util.concurrent.atomic.AtomicBoolean) cancelledField.get(st)).set(false);

        AdminLlmLoadTestResultDTO dto1 = new AdminLlmLoadTestResultDTO();
        dto1.setKind("MODERATION_TEST");
        dto1.setTokensIn(5);
        dto1.setTokensOut(null);
        dto1.setTokens(1);
        dto1.setModel(" ");
        Object result1 = newResult(1L, null, null, null, null, List.of(ChatMessage.user("NULL_IN")), "X", "{}", null);
        lambda.invoke(svc, st, dto1, List.of(ChatMessage.user("NULL_IN")), result1);
        assertEquals(5, dto1.getTokensIn());
        assertEquals(7, dto1.getTokensOut());
        assertEquals(12, dto1.getTokens());

        AdminLlmLoadTestResultDTO dto2 = new AdminLlmLoadTestResultDTO();
        dto2.setKind("CHAT_STREAM");
        dto2.setTokensIn(2);
        dto2.setTokensOut(3);
        dto2.setTokens(5);
        dto2.setModel("model-z");
        Object result2 = newResult(1L, null, null, null, null, List.of(ChatMessage.user("KEEP")), "NULL_OUT", "{}", null);
        lambda.invoke(svc, st, dto2, List.of(ChatMessage.user("KEEP")), result2);
        assertEquals(2, dto2.getTokensIn());
        assertEquals(3, dto2.getTokensOut());
        assertEquals(5, dto2.getTokens());

        AdminLlmLoadTestResultDTO dto3 = new AdminLlmLoadTestResultDTO();
        dto3.setKind("CHAT_STREAM");
        dto3.setTokensIn(null);
        dto3.setTokensOut(4);
        dto3.setTokens(11);
        dto3.setModel(" ");
        Object result3 = newResult(1L, null, null, null, null, List.of(ChatMessage.user("NULL_IN")), "NULL_OUT", "{}", null);
        lambda.invoke(svc, st, dto3, List.of(ChatMessage.user("NULL_IN")), result3);
        assertEquals(11, dto3.getTokens());

        AdminLlmLoadTestResultDTO dto4 = new AdminLlmLoadTestResultDTO();
        dto4.setKind("CHAT_STREAM");
        dto4.setTokensIn(null);
        dto4.setTokensOut(null);
        dto4.setTokens(null);
        dto4.setModel(null);
        Object result4 = newResult(1L, null, null, null, null, null, "NULL_OUT", "{}", null);
        lambda.invoke(svc, st, dto4, null, result4);
        lambda.invoke(svc, st, dto4, List.of(), result4);

        AdminLlmLoadTestResultDTO dto5 = new AdminLlmLoadTestResultDTO();
        dto5.setKind("CHAT_STREAM");
        dto5.setTokensIn(null);
        dto5.setTokensOut(null);
        dto5.setTokens(5);
        dto5.setModel(" ");
        Object result5 = newResult(1L, null, null, null, null, List.of(ChatMessage.user("KEEP")), "NULL_OUT", "{}", null);
        lambda.invoke(svc, st, dto5, List.of(ChatMessage.user("KEEP")), result5);
        assertEquals(9, dto5.getTokensIn());
        assertNull(dto5.getTokensOut());
        assertEquals(5, dto5.getTokens());

        AdminLlmLoadTestResultDTO dto6 = new AdminLlmLoadTestResultDTO();
        dto6.setKind("MODERATION_TEST");
        dto6.setTokensIn(1);
        dto6.setTokensOut(1);
        dto6.setTokens(2);
        dto6.setModel("model-agg");
        Object result6 = newResult(1L, null, null, null, null, List.of(ChatMessage.user("KEEP")), "X", "{}", null);
        lambda.invoke(svc, st, dto6, List.of(ChatMessage.user("KEEP")), result6);
        assertEquals(9, dto6.getTokensIn());
        assertEquals(1, dto6.getTokensOut());
        assertEquals(10, dto6.getTokens());

        AdminLlmLoadTestResultDTO dto7 = new AdminLlmLoadTestResultDTO();
        dto7.setKind("CHAT_STREAM");
        dto7.setTokensIn(null);
        dto7.setTokensOut(null);
        dto7.setTokens(null);
        dto7.setModel(" ");
        Object result7 = newResult(1L, null, null, null, null, List.of(ChatMessage.user("NULL_IN")), "NULL_OUT", "{}", null);
        lambda.invoke(svc, st, dto7, List.of(ChatMessage.user("NULL_IN")), result7);
        assertNull(dto7.getTokens());

        Method extractAssistant = AdminLlmLoadTestService.class.getDeclaredMethod("extractAssistantContentFromRawModelOutput", String.class);
        extractAssistant.setAccessible(true);
        assertEquals("{\"choices\":[]}", extractAssistant.invoke(svc, "{\"choices\":[]}"));
        assertEquals("{\"choices\":[{}]}", extractAssistant.invoke(svc, "{\"choices\":[{}]}"));
        assertEquals("{\"choices\":[{\"message\":{\"content\":null}}]}", extractAssistant.invoke(svc, "{\"choices\":[{\"message\":{\"content\":null}}]}"));
        assertEquals("raw", extractAssistant.invoke(svc, "raw"));

        assertEquals("rc", invokeStatic("extractDeltaText", new Class[]{String.class, boolean.class}, new Object[]{"{\"reasoning_content\":\"r\",\"content\":\"c\"}", true}));
        assertEquals("c", invokeStatic("extractDeltaText", new Class[]{String.class, boolean.class}, new Object[]{"{\"reasoning_content\":\"r\",\"content\":\"c\"}", false}));
        assertEquals("x", invokeStatic("extractDeltaText", new Class[]{String.class, boolean.class}, new Object[]{"{\"content\":\"x\",\"text\":\"t\"}", false}));
        assertEquals("t", invokeStatic("extractDeltaText", new Class[]{String.class, boolean.class}, new Object[]{"{\"text\":\"t\"}", true}));
        assertNull(invokeStatic("extractDeltaText", new Class[]{String.class, boolean.class}, new Object[]{"{\"reasoning_content\":\"r\"}", false}));
        assertEquals("z", invokeStatic("extractDeltaText", new Class[]{String.class, boolean.class}, new Object[]{"{\"content\":\"reasoning_contentz\"}", false}));

        assertEquals("", invokeStatic("extractDeltaStringField", new Class[]{String.class, String.class}, new Object[]{"{\"content\":\"\"}", "content"}));
        assertEquals("x", invokeStatic("extractDeltaStringField", new Class[]{String.class, String.class}, new Object[]{"{\"content\":\"\\x\"}", "content"}));
        assertNotNull(invokeStatic("extractDeltaStringField", new Class[]{String.class, String.class}, new Object[]{"{\"content\":\"\\u12u\"}", "content"}));

        assertFalse((Boolean) invokeStatic("supportsThinkingDirectiveModel", new Class[]{String.class}, new Object[]{"qwen-plus-2025-04-28-thinking"}));
        assertFalse((Boolean) invokeStatic("supportsThinkingDirectiveModel", new Class[]{String.class}, new Object[]{"x/qwen-turbo-2025-04-28-thinking"}));
        assertTrue((Boolean) invokeStatic("supportsThinkingDirectiveModel", new Class[]{String.class}, new Object[]{"x:y/qwen3-72b"}));
        assertTrue((Boolean) invokeStatic("supportsThinkingDirectiveModel", new Class[]{String.class}, new Object[]{"x:y:qwen-plus-2025-04-28"}));
        assertFalse((Boolean) invokeStatic("supportsThinkingDirectiveModel", new Class[]{String.class}, new Object[]{"x:y:qwen-plus-2025-04-28-thinking"}));

        LlmModelRepository modelRepo = mock(LlmModelRepository.class);
        LlmPriceConfigRepository priceRepo = mock(LlmPriceConfigRepository.class);
        LlmModelEntity mA = new LlmModelEntity();
        mA.setModelName("modelA");
        mA.setPriceConfigId(1L);
        LlmModelEntity mB = new LlmModelEntity();
        mB.setModelName("modelB");
        mB.setPriceConfigId(null);
        LlmModelEntity mC = new LlmModelEntity();
        mC.setModelName("modelC");
        mC.setPriceConfigId(3L);
        when(modelRepo.findByEnvAndEnabledTrueAndModelNameInOrderByIsDefaultDescWeightDescIdAsc(anyString(), any()))
                .thenReturn(List.of(mA, mB, mC));
        LlmPriceConfigEntity pA = new LlmPriceConfigEntity();
        pA.setId(1L);
        pA.setName("modelA");
        pA.setCurrency("usd");
        pA.setInputCostPer1k(BigDecimal.ONE);
        pA.setOutputCostPer1k(BigDecimal.ONE);
        LlmPriceConfigEntity pB = new LlmPriceConfigEntity();
        pB.setId(2L);
        pB.setName("modelB");
        pB.setCurrency("usd");
        pB.setInputCostPer1k(BigDecimal.ONE);
        pB.setOutputCostPer1k(BigDecimal.ONE);
        when(priceRepo.findByIdIn(any())).thenReturn(List.of(pA));
        when(priceRepo.findByNameIn(any())).thenReturn(List.of(pB, new LlmPriceConfigEntity()));
        AdminLlmLoadTestService svcPrice = new AdminLlmLoadTestService(
                mock(LlmGateway.class),
                mock(AdminModerationLlmService.class),
                mock(LlmQueueMonitorService.class),
                mock(TokenCountService.class),
                new ObjectMapper(),
                modelRepo,
                priceRepo,
                mock(LlmLoadTestRunDetailRepository.class),
                mock(LlmLoadTestRunHistoryRepository.class),
                mock(PromptsRepository.class)
        );
        Method resolvePrices = AdminLlmLoadTestService.class.getDeclaredMethod("resolvePrices", java.util.Collection.class);
        resolvePrices.setAccessible(true);
        Map<?, ?> prices = (Map<?, ?>) resolvePrices.invoke(svcPrice, Arrays.asList("modelA", "modelB", "modelC"));
        assertTrue(prices.containsKey("modelA"));
        assertTrue(prices.containsKey("modelB"));
        assertFalse(prices.containsKey("modelC"));

        Class<?> modelAggClass = Class.forName(AdminLlmLoadTestService.class.getName() + "$ModelAgg");
        Constructor<?> aggCtor = modelAggClass.getDeclaredConstructor();
        aggCtor.setAccessible(true);
        Object agg = aggCtor.newInstance();
        Field inField = modelAggClass.getDeclaredField("in");
        inField.setAccessible(true);
        Field outField = modelAggClass.getDeclaredField("out");
        outField.setAccessible(true);
        ((LongAdder) inField.get(agg)).add(500L);
        ((LongAdder) outField.get(agg)).add(800L);
        Map<String, Object> byModel = new HashMap<>();
        byModel.put("modelA", agg);
        byModel.put("modelB", agg);
        byModel.put("modelC", agg);
        Class<?> pricingModeClass = Class.forName("com.example.EnterpriseRagCommunity.service.monitor.LlmPricing$Mode");
        Object mode = Enum.valueOf((Class<Enum>) pricingModeClass.asSubclass(Enum.class), "DEFAULT");
        Method computeCostInfo = AdminLlmLoadTestService.class.getDeclaredMethod("computeCostInfo", Map.class, pricingModeClass);
        computeCostInfo.setAccessible(true);
        Object cost = computeCostInfo.invoke(svcPrice, byModel, mode);
        Method priceMissing = cost.getClass().getDeclaredMethod("priceMissing");
        priceMissing.setAccessible(true);
        assertTrue((Boolean) priceMissing.invoke(cost));

        LlmModelRepository modelRepo2 = mock(LlmModelRepository.class);
        LlmPriceConfigRepository priceRepo2 = mock(LlmPriceConfigRepository.class);
        LlmModelEntity mOnly = new LlmModelEntity();
        mOnly.setModelName("modelOnly");
        mOnly.setPriceConfigId(11L);
        when(modelRepo2.findByEnvAndEnabledTrueAndModelNameInOrderByIsDefaultDescWeightDescIdAsc(anyString(), any())).thenReturn(List.of(mOnly));
        LlmPriceConfigEntity pOnly = new LlmPriceConfigEntity();
        pOnly.setId(11L);
        pOnly.setName("modelOnly");
        pOnly.setCurrency("usd");
        pOnly.setInputCostPer1k(BigDecimal.ONE);
        pOnly.setOutputCostPer1k(BigDecimal.ONE);
        when(priceRepo2.findByIdIn(any())).thenReturn(List.of(pOnly));
        when(priceRepo2.findByNameIn(any())).thenReturn(null);
        AdminLlmLoadTestService svcPrice2 = new AdminLlmLoadTestService(
                mock(LlmGateway.class),
                mock(AdminModerationLlmService.class),
                mock(LlmQueueMonitorService.class),
                mock(TokenCountService.class),
                new ObjectMapper(),
                modelRepo2,
                priceRepo2,
                mock(LlmLoadTestRunDetailRepository.class),
                mock(LlmLoadTestRunHistoryRepository.class),
                mock(PromptsRepository.class)
        );
        Map<String, Object> byModel2 = new HashMap<>();
        byModel2.put("modelOnly", agg);
        Object cost2 = computeCostInfo.invoke(svcPrice2, byModel2, mode);
        Method currency = cost2.getClass().getDeclaredMethod("currency");
        currency.setAccessible(true);
        assertEquals("USD", currency.invoke(cost2));
        assertFalse((Boolean) priceMissing.invoke(cost2));
        svcPrice2.shutdown();
        svcPrice.shutdown();
        svc.shutdown();
    }

    @Test
    void pricing_resolution_branches_should_cover_null_lists_duplicates_and_missing_paths() throws Exception {
        LlmModelRepository modelRepo = mock(LlmModelRepository.class);
        LlmPriceConfigRepository priceRepo = mock(LlmPriceConfigRepository.class);
        when(modelRepo.findByEnvAndEnabledTrueAndModelNameInOrderByIsDefaultDescWeightDescIdAsc(anyString(), any()))
                .thenReturn(null)
                .thenReturn(List.of())
                .thenReturn(List.of(new LlmModelEntity(), new LlmModelEntity()))
                .thenReturn(List.of());
        when(priceRepo.findByIdIn(any())).thenReturn(null, List.of());
        when(priceRepo.findByNameIn(any())).thenReturn(null, List.of());

        AdminLlmLoadTestService svc = new AdminLlmLoadTestService(
                mock(LlmGateway.class),
                mock(AdminModerationLlmService.class),
                mock(LlmQueueMonitorService.class),
                mock(TokenCountService.class),
                new ObjectMapper(),
                modelRepo,
                priceRepo,
                mock(LlmLoadTestRunDetailRepository.class),
                mock(LlmLoadTestRunHistoryRepository.class),
                mock(PromptsRepository.class)
        );
        Method resolvePrices = AdminLlmLoadTestService.class.getDeclaredMethod("resolvePrices", java.util.Collection.class);
        resolvePrices.setAccessible(true);
        assertTrue(((Map<?, ?>) resolvePrices.invoke(svc, new Object[]{null})).isEmpty());
        assertTrue(((Map<?, ?>) resolvePrices.invoke(svc, List.of("x"))).isEmpty());
        assertTrue(((Map<?, ?>) resolvePrices.invoke(svc, List.of("x", " "))).isEmpty());
        assertTrue(((Map<?, ?>) resolvePrices.invoke(svc, List.of("x"))).isEmpty());
        svc.shutdown();

        LlmModelRepository modelRepo2 = mock(LlmModelRepository.class);
        LlmPriceConfigRepository priceRepo2 = mock(LlmPriceConfigRepository.class);
        LlmModelEntity m1 = new LlmModelEntity();
        m1.setModelName("modelA");
        m1.setPriceConfigId(1L);
        LlmModelEntity m2 = new LlmModelEntity();
        m2.setModelName("modelB");
        m2.setPriceConfigId(null);
        when(modelRepo2.findByEnvAndEnabledTrueAndModelNameInOrderByIsDefaultDescWeightDescIdAsc(anyString(), any())).thenReturn(List.of(m1, m2));
        LlmPriceConfigEntity p1 = new LlmPriceConfigEntity();
        p1.setId(1L);
        p1.setName("modelA");
        p1.setCurrency("usd");
        p1.setInputCostPer1k(BigDecimal.ONE);
        p1.setOutputCostPer1k(BigDecimal.ONE);
        LlmPriceConfigEntity p2 = new LlmPriceConfigEntity();
        p2.setId(2L);
        p2.setName("modelB");
        p2.setCurrency("usd");
        p2.setInputCostPer1k(BigDecimal.ONE);
        p2.setOutputCostPer1k(BigDecimal.ONE);
        when(priceRepo2.findByIdIn(any())).thenReturn(List.of(p1));
        when(priceRepo2.findByNameIn(any())).thenReturn(List.of(p2, p2));
        AdminLlmLoadTestService svc2 = new AdminLlmLoadTestService(
                mock(LlmGateway.class),
                mock(AdminModerationLlmService.class),
                mock(LlmQueueMonitorService.class),
                mock(TokenCountService.class),
                new ObjectMapper(),
                modelRepo2,
                priceRepo2,
                mock(LlmLoadTestRunDetailRepository.class),
                mock(LlmLoadTestRunHistoryRepository.class),
                mock(PromptsRepository.class)
        );
        Map<?, ?> out = (Map<?, ?>) resolvePrices.invoke(svc2, List.of("modelA", "modelB"));
        assertEquals(2, out.size());
        svc2.shutdown();
    }

    @Test
    void remaining_branch_matrix_should_cover_supports_extract_normalize_and_export_paths() throws Exception {
        assertTrue((Boolean) invokeStatic("supportsThinkingDirectiveModel", new Class[]{String.class}, new Object[]{"qwen3-8b"}));
        assertTrue((Boolean) invokeStatic("supportsThinkingDirectiveModel", new Class[]{String.class}, new Object[]{"p/qwen3-8b"}));
        assertTrue((Boolean) invokeStatic("supportsThinkingDirectiveModel", new Class[]{String.class}, new Object[]{"qwen-plus-2025-04-28"}));
        assertTrue((Boolean) invokeStatic("supportsThinkingDirectiveModel", new Class[]{String.class}, new Object[]{"p/qwen-plus-2025-04-28"}));
        assertTrue((Boolean) invokeStatic("supportsThinkingDirectiveModel", new Class[]{String.class}, new Object[]{"qwen-turbo-2025-04-28"}));
        assertTrue((Boolean) invokeStatic("supportsThinkingDirectiveModel", new Class[]{String.class}, new Object[]{"p/qwen-turbo-2025-04-28"}));
        assertFalse((Boolean) invokeStatic("supportsThinkingDirectiveModel", new Class[]{String.class}, new Object[]{"abc-thinking/qwen3-8b"}));
        assertFalse((Boolean) invokeStatic("supportsThinkingDirectiveModel", new Class[]{String.class}, new Object[]{"p/qwen3-thinking"}));

        assertNull(invokeStatic("extractDeltaText", new Class[]{String.class, boolean.class}, new Object[]{"{\"reasoning_content\":\"\",\"content\":\"\",\"text\":\"\"}", true}));
        assertEquals("r", invokeStatic("extractDeltaText", new Class[]{String.class, boolean.class}, new Object[]{"{\"reasoning_content\":\"r\"}", true}));
        assertEquals("c", invokeStatic("extractDeltaText", new Class[]{String.class, boolean.class}, new Object[]{"{\"content\":\"c\"}", true}));
        assertEquals("rc", invokeStatic("extractDeltaText", new Class[]{String.class, boolean.class}, new Object[]{"{\"reasoning_content\":\"r\",\"content\":\"c\"}", true}));
        assertEquals("c", invokeStatic("extractDeltaText", new Class[]{String.class, boolean.class}, new Object[]{"{\"reasoning_content\":\"<reasoning_content>r</reasoning_content>\",\"content\":\"c\"}", false}));

        com.example.EnterpriseRagCommunity.dto.monitor.AdminLlmLoadTestRunRequestDTO req = new com.example.EnterpriseRagCommunity.dto.monitor.AdminLlmLoadTestRunRequestDTO();
        req.setConcurrency(9999);
        req.setTotalRequests(0);
        req.setRatioChatStream(0);
        req.setRatioModerationTest(0);
        req.setStream(false);
        req.setTimeoutMs(1);
        req.setRetries(99);
        req.setRetryDelayMs(-1);
        Method normalize = AdminLlmLoadTestService.class.getDeclaredMethod("normalize", com.example.EnterpriseRagCommunity.dto.monitor.AdminLlmLoadTestRunRequestDTO.class);
        normalize.setAccessible(true);
        Object cfg = normalize.invoke(null, req);
        Method weightModeration = cfg.getClass().getDeclaredMethod("weightModeration");
        weightModeration.setAccessible(true);
        assertEquals(100, weightModeration.invoke(cfg));

        LlmLoadTestRunDetailRepository detailRepo = mock(LlmLoadTestRunDetailRepository.class);
        LlmLoadTestRunHistoryRepository historyRepo = mock(LlmLoadTestRunHistoryRepository.class);
        when(detailRepo.existsByRunId("rid")).thenReturn(true);
        when(historyRepo.existsById("rid")).thenReturn(false);
        when(detailRepo.findByRunIdOrderByReqIndexAsc(anyString(), any())).thenReturn(new org.springframework.data.domain.PageImpl<>(List.of(), org.springframework.data.domain.PageRequest.of(0, 2000), 0));
        AdminLlmLoadTestService svc = new AdminLlmLoadTestService(
                mock(LlmGateway.class),
                mock(AdminModerationLlmService.class),
                mock(LlmQueueMonitorService.class),
                mock(TokenCountService.class),
                new ObjectMapper(),
                mock(LlmModelRepository.class),
                mock(LlmPriceConfigRepository.class),
                detailRepo,
                historyRepo,
                mock(PromptsRepository.class)
        );
        assertNull(svc.status("missing"));
        assertEquals(200, svc.export("rid", null).getStatusCode().value());
        assertEquals(404, svc.export("none", null).getStatusCode().value());
        svc.shutdown();
    }

    @Test
    void tiny_helper_sweep_should_cover_index_remove_append_safe_and_timeout_branches() throws Exception {
        assertEquals(-1, invokeStatic("indexOfIgnoreCase", new Class[]{String.class, String.class, int.class}, new Object[]{null, "a", 0}));
        assertEquals(-1, invokeStatic("indexOfIgnoreCase", new Class[]{String.class, String.class, int.class}, new Object[]{"abc", null, 0}));
        assertEquals(0, invokeStatic("indexOfIgnoreCase", new Class[]{String.class, String.class, int.class}, new Object[]{"abc", "", -1}));
        assertEquals(-1, invokeStatic("indexOfIgnoreCase", new Class[]{String.class, String.class, int.class}, new Object[]{"abc", "", 9}));
        assertEquals(-1, invokeStatic("indexOfIgnoreCase", new Class[]{String.class, String.class, int.class}, new Object[]{"a", "abcd", 0}));
        assertEquals(1, invokeStatic("indexOfIgnoreCase", new Class[]{String.class, String.class, int.class}, new Object[]{"aBcD", "bc", 0}));
        assertEquals(-1, invokeStatic("indexOfIgnoreCase", new Class[]{String.class, String.class, int.class}, new Object[]{"abcd", "ef", 0}));

        assertEquals(" ", invokeStatic("removeMarkerWordIgnoreCase", new Class[]{String.class, String.class}, new Object[]{" ", "x"}));
        assertEquals("abc", invokeStatic("removeMarkerWordIgnoreCase", new Class[]{String.class, String.class}, new Object[]{"abc", " "}));
        assertEquals("abc", invokeStatic("removeMarkerWordIgnoreCase", new Class[]{String.class, String.class}, new Object[]{"abc", "x"}));
        assertEquals("ab", invokeStatic("removeMarkerWordIgnoreCase", new Class[]{String.class, String.class}, new Object[]{"aXbX", "x"}));
        String stripped = String.valueOf(invokeStatic("stripReasoningArtifacts", new Class[]{String.class}, new Object[]{"<reasoning_content>r</reasoning_content>z"}));
        assertTrue(stripped.contains("r"));
        assertTrue(stripped.endsWith("z"));

        StringBuilder out = new StringBuilder();
        invokeStatic("appendStreamDelta", new Class[]{String.class, StringBuilder.class, boolean.class}, new Object[]{"", out, false});
        invokeStatic("appendStreamDelta", new Class[]{String.class, StringBuilder.class, boolean.class}, new Object[]{"data: {\"reasoning_content\":\"r\"}", out, false});
        invokeStatic("appendStreamDelta", new Class[]{String.class, StringBuilder.class, boolean.class}, new Object[]{"data: {\"content\":\"x\"}", out, false});
        assertEquals("x", out.toString());

        AdminLlmLoadTestService svc = newService(mock(TokenCountService.class), mock(LlmQueueMonitorService.class));
        Method extractAssistant = AdminLlmLoadTestService.class.getDeclaredMethod("extractAssistantContentFromRawModelOutput", String.class);
        extractAssistant.setAccessible(true);
        assertNull(extractAssistant.invoke(svc, new Object[]{null}));
        assertEquals(" ", extractAssistant.invoke(svc, " "));

        Method safeMessage = AdminLlmLoadTestService.class.getDeclaredMethod("safeMessage", Exception.class);
        safeMessage.setAccessible(true);
        assertEquals(Exception.class.getSimpleName(), safeMessage.invoke(null, new Exception((String) null)));

        Class<?> checkedCallableClass = Class.forName(AdminLlmLoadTestService.class.getName() + "$CheckedCallable");
        Object timeoutCallable = Proxy.newProxyInstance(
                checkedCallableClass.getClassLoader(),
                new Class[]{checkedCallableClass},
                (proxy, method, args) -> {
                    Thread.sleep(30);
                    return "ok";
                }
        );
        Method runWithTimeout = AdminLlmLoadTestService.class.getDeclaredMethod("runWithTimeout", checkedCallableClass, int.class);
        runWithTimeout.setAccessible(true);
        assertThrows(InvocationTargetException.class, () -> runWithTimeout.invoke(svc, timeoutCallable, 1));
        svc.shutdown();
    }

    @Test
    void final_gap_closing_should_cover_supports_and_compute_cost_remaining_branches() throws Exception {
        assertFalse((Boolean) invokeStatic("supportsThinkingDirectiveModel", new Class[]{String.class}, new Object[]{"a/b:"}));
        assertFalse((Boolean) invokeStatic("supportsThinkingDirectiveModel", new Class[]{String.class}, new Object[]{"qwen3-thinking/x"}));
        assertFalse((Boolean) invokeStatic("supportsThinkingDirectiveModel", new Class[]{String.class}, new Object[]{"x/qwen3-thinking"}));
        assertTrue((Boolean) invokeStatic("supportsThinkingDirectiveModel", new Class[]{String.class}, new Object[]{"x/qwen3-8b"}));
        assertTrue((Boolean) invokeStatic("supportsThinkingDirectiveModel", new Class[]{String.class}, new Object[]{"qwen3-8b/x"}));
        assertTrue((Boolean) invokeStatic("supportsThinkingDirectiveModel", new Class[]{String.class}, new Object[]{"x/qwen-plus-2025-04-28"}));
        assertTrue((Boolean) invokeStatic("supportsThinkingDirectiveModel", new Class[]{String.class}, new Object[]{"qwen-plus-2025-04-28/x"}));
        assertTrue((Boolean) invokeStatic("supportsThinkingDirectiveModel", new Class[]{String.class}, new Object[]{"x/qwen-turbo-2025-04-28"}));
        assertTrue((Boolean) invokeStatic("supportsThinkingDirectiveModel", new Class[]{String.class}, new Object[]{"qwen-turbo-2025-04-28/x"}));
        assertFalse((Boolean) invokeStatic("supportsThinkingDirectiveModel", new Class[]{String.class}, new Object[]{"model-a"}));

        LlmModelRepository modelRepo = mock(LlmModelRepository.class);
        LlmPriceConfigRepository priceRepo = mock(LlmPriceConfigRepository.class);
        LlmModelEntity ma = new LlmModelEntity();
        ma.setModelName("modelA");
        ma.setPriceConfigId(1L);
        LlmModelEntity mb = new LlmModelEntity();
        mb.setModelName("modelB");
        mb.setPriceConfigId(2L);
        when(modelRepo.findByEnvAndEnabledTrueAndModelNameInOrderByIsDefaultDescWeightDescIdAsc(anyString(), any()))
                .thenReturn(List.of(ma, mb))
                .thenReturn(List.of(ma, mb))
                .thenReturn(List.of(ma, mb));
        LlmPriceConfigEntity pa = new LlmPriceConfigEntity();
        pa.setId(1L);
        pa.setName("modelA");
        pa.setCurrency(null);
        pa.setInputCostPer1k(BigDecimal.ONE);
        pa.setOutputCostPer1k(BigDecimal.ONE);
        LlmPriceConfigEntity pb = new LlmPriceConfigEntity();
        pb.setId(2L);
        pb.setName("modelB");
        pb.setCurrency("usd");
        pb.setInputCostPer1k(BigDecimal.ONE);
        pb.setOutputCostPer1k(BigDecimal.ONE);
        when(priceRepo.findByIdIn(any())).thenReturn(List.of(pa, pb), List.of(pa, pb), List.of(pa, pb));
        when(priceRepo.findByNameIn(any())).thenReturn(List.of(), List.of(), List.of());

        AdminLlmLoadTestService svc = new AdminLlmLoadTestService(
                mock(LlmGateway.class),
                mock(AdminModerationLlmService.class),
                mock(LlmQueueMonitorService.class),
                mock(TokenCountService.class),
                new ObjectMapper(),
                modelRepo,
                priceRepo,
                mock(LlmLoadTestRunDetailRepository.class),
                mock(LlmLoadTestRunHistoryRepository.class),
                mock(PromptsRepository.class)
        );
        Class<?> modelAggClass = Class.forName(AdminLlmLoadTestService.class.getName() + "$ModelAgg");
        Constructor<?> aggCtor = modelAggClass.getDeclaredConstructor();
        aggCtor.setAccessible(true);
        Object agg = aggCtor.newInstance();
        Field inField = modelAggClass.getDeclaredField("in");
        inField.setAccessible(true);
        Field outField = modelAggClass.getDeclaredField("out");
        outField.setAccessible(true);
        ((LongAdder) inField.get(agg)).add(10L);
        ((LongAdder) outField.get(agg)).add(20L);
        Method computeCostInfo = AdminLlmLoadTestService.class.getDeclaredMethod(
                "computeCostInfo",
                Map.class,
                Class.forName("com.example.EnterpriseRagCommunity.service.monitor.LlmPricing$Mode")
        );
        computeCostInfo.setAccessible(true);
        Class<?> modeClass = Class.forName("com.example.EnterpriseRagCommunity.service.monitor.LlmPricing$Mode");
        Object nonThinking = Enum.valueOf((Class<Enum>) modeClass.asSubclass(Enum.class), "NON_THINKING");
        Object thinking = Enum.valueOf((Class<Enum>) modeClass.asSubclass(Enum.class), "THINKING");
        assertNull(computeCostInfo.invoke(svc, null, nonThinking));
        Map<String, Object> blankOnly = new HashMap<>();
        blankOnly.put(" ", agg);
        assertNull(computeCostInfo.invoke(svc, blankOnly, nonThinking));
        Map<String, Object> byModel = new HashMap<>();
        byModel.put("modelA", agg);
        byModel.put("modelB", agg);
        Object c1 = computeCostInfo.invoke(svc, byModel, nonThinking);
        Method currency = c1.getClass().getDeclaredMethod("currency");
        currency.setAccessible(true);
        Method priceMissing = c1.getClass().getDeclaredMethod("priceMissing");
        priceMissing.setAccessible(true);
        assertEquals("USD", currency.invoke(c1));
        assertFalse((Boolean) priceMissing.invoke(c1));
        Object c2 = computeCostInfo.invoke(svc, byModel, thinking);
        assertNotNull(c2);
        svc.shutdown();
    }

    private static AdminLlmLoadTestService newService(TokenCountService tokenCountService, LlmQueueMonitorService queueSvc) {
        return new AdminLlmLoadTestService(
                mock(LlmGateway.class),
                mock(AdminModerationLlmService.class),
                queueSvc,
                tokenCountService,
                new ObjectMapper(),
                mock(LlmModelRepository.class),
                mock(LlmPriceConfigRepository.class),
                mock(LlmLoadTestRunDetailRepository.class),
                mock(LlmLoadTestRunHistoryRepository.class),
                mock(PromptsRepository.class)
        );
    }

    private static Object invokeStatic(String name, Class<?>[] paramTypes, Object[] args) throws Exception {
        Method m = AdminLlmLoadTestService.class.getDeclaredMethod(name, paramTypes);
        m.setAccessible(true);
        return m.invoke(null, args);
    }

    private static Object newNormalizedConfig(
            int concurrency,
            int totalRequests,
            int weightChatStream,
            int weightModeration,
            String providerId,
            String model,
            boolean enableThinking,
            int timeoutMs,
            int retries,
            int retryDelayMs,
            String chatMessage,
            String moderationText
    ) throws Exception {
        Class<?> cls = Class.forName(AdminLlmLoadTestService.class.getName() + "$NormalizedConfig");
        Constructor<?> c = cls.getDeclaredConstructor(
                int.class, int.class, int.class, int.class, String.class, String.class, boolean.class,
                int.class, int.class, int.class, String.class, String.class
        );
        c.setAccessible(true);
        return c.newInstance(
                concurrency, totalRequests, weightChatStream, weightModeration,
                providerId, model, enableThinking, timeoutMs, retries, retryDelayMs, chatMessage, moderationText
        );
    }

    private static Object newRunState(AdminLlmLoadTestService svc, String runId, Object normalizedConfig) throws Exception {
        Class<?> cls = Class.forName(AdminLlmLoadTestService.class.getName() + "$RunState");
        Constructor<?> c = cls.getDeclaredConstructor(AdminLlmLoadTestService.class, String.class, long.class, normalizedConfig.getClass());
        c.setAccessible(true);
        return c.newInstance(svc, runId, System.currentTimeMillis(), normalizedConfig);
    }

    private static Class<?> newResultClass() throws Exception {
        return Class.forName(AdminLlmLoadTestService.class.getName() + "$Result");
    }

    private static Object newResult(
            Long latencyMs,
            Integer tokens,
            Integer tokensIn,
            Integer tokensOut,
            String model,
            List<ChatMessage> tokenCountMessages,
            String tokenCountText,
            String responseJson,
            String providerId
    ) throws Exception {
        Class<?> cls = newResultClass();
        Constructor<?> c = cls.getDeclaredConstructor(
                Long.class,
                Integer.class,
                Integer.class,
                Integer.class,
                String.class,
                List.class,
                String.class,
                String.class,
                String.class
        );
        c.setAccessible(true);
        return c.newInstance(
                latencyMs, tokens, tokensIn, tokensOut, model, tokenCountMessages, tokenCountText, responseJson, providerId
        );
    }
}
