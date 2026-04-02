package com.example.EnterpriseRagCommunity.service.monitor;

import com.example.EnterpriseRagCommunity.entity.ai.LlmModelEntity;
import com.example.EnterpriseRagCommunity.entity.ai.LlmPriceConfigEntity;
import com.example.EnterpriseRagCommunity.repository.ai.LlmModelRepository;
import com.example.EnterpriseRagCommunity.repository.ai.LlmPriceConfigRepository;
import com.example.EnterpriseRagCommunity.repository.monitor.LlmLoadTestRunDetailRepository;
import com.example.EnterpriseRagCommunity.repository.monitor.LlmLoadTestRunHistoryRepository;
import com.example.EnterpriseRagCommunity.repository.semantic.PromptsRepository;
import com.example.EnterpriseRagCommunity.service.ai.LlmGateway;
import com.example.EnterpriseRagCommunity.service.ai.TokenCountService;
import com.example.EnterpriseRagCommunity.service.moderation.admin.AdminModerationLlmService;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.io.StringWriter;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Proxy;
import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeoutException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AdminLlmLoadTestServiceBranchHelperUnitTest {

    private static Object invokeStatic(String name, Class<?>[] paramTypes, Object[] args) throws Exception {
        Method m;
        try {
            m = AdminLlmLoadTestService.class.getDeclaredMethod(name, paramTypes);
        } catch (NoSuchMethodException ex) {
            m = findCompatibleStaticMethod(name, args);
        }
        m.setAccessible(true);
        return m.invoke(null, args);
    }

    @Test
    void instanceHelpers_should_cover_json_timeout_and_pricing_branches() throws Exception {
        LlmModelRepository modelRepo = mock(LlmModelRepository.class);
        LlmPriceConfigRepository priceRepo = mock(LlmPriceConfigRepository.class);
        when(modelRepo.findByEnvAndEnabledTrueAndModelNameInOrderByIsDefaultDescWeightDescIdAsc(anyString(), anyCollection())).thenReturn(List.of());
        when(priceRepo.findByIdIn(anyCollection())).thenReturn(List.of());
        when(priceRepo.findByNameIn(anyCollection())).thenReturn(List.of());

        AdminLlmLoadTestService svc = newService(modelRepo, priceRepo);

        Object nullPricing = invokeStatic("resolvePricing", new Class[]{LlmPriceConfigEntity.class}, new Object[]{null});
        assertNull(nullPricing);

        LlmPriceConfigEntity meta = new LlmPriceConfigEntity();
        meta.setMetadata(Map.of("pricing", Map.of("defaultInputCostPerUnit", BigDecimal.ONE)));
        assertNotNull(invokeStatic("resolvePricing", new Class[]{LlmPriceConfigEntity.class}, new Object[]{meta}));

        LlmPriceConfigEntity legacy = new LlmPriceConfigEntity();
        legacy.setInputCostPer1k(BigDecimal.ONE);
        legacy.setOutputCostPer1k(BigDecimal.ONE);
        assertNotNull(invokeStatic("resolvePricing", new Class[]{LlmPriceConfigEntity.class}, new Object[]{legacy}));

        Method tryRead = AdminLlmLoadTestService.class.getDeclaredMethod("tryReadJson", String.class);
        tryRead.setAccessible(true);
        assertNull(tryRead.invoke(svc, new Object[]{null}));
        assertNull(tryRead.invoke(svc, "   "));
        JsonNode node = (JsonNode) tryRead.invoke(svc, "{\"a\":1}");
        assertEquals(1, node.path("a").asInt());

        StringWriter writer = new StringWriter();
        JsonGenerator gen = new ObjectMapper().getFactory().createGenerator(writer);
        Method writeMaybe = AdminLlmLoadTestService.class.getDeclaredMethod("writeMaybeJson", JsonGenerator.class, String.class);
        writeMaybe.setAccessible(true);
        writeMaybe.invoke(svc, gen, (String) null);
        gen.flush();
        assertEquals("null", writer.toString());

        writer = new StringWriter();
        gen = new ObjectMapper().getFactory().createGenerator(writer);
        writeMaybe.invoke(svc, gen, "{\"x\":2}");
        gen.flush();
        assertTrue(writer.toString().contains("\"x\":2"));

        writer = new StringWriter();
        gen = new ObjectMapper().getFactory().createGenerator(writer);
        writeMaybe.invoke(svc, gen, "{bad}");
        gen.flush();
        assertTrue(writer.toString().contains("{bad}"));

        Method toJson = AdminLlmLoadTestService.class.getDeclaredMethod("toJson", Object.class);
        toJson.setAccessible(true);
        class SelfRef { public SelfRef self = this; @Override public String toString() { return "SELF"; } }
        assertEquals("SELF", toJson.invoke(svc, new SelfRef()));

        Method runWithTimeout = AdminLlmLoadTestService.class.getDeclaredMethod("runWithTimeout", checkedCallableClass(), int.class);
        runWithTimeout.setAccessible(true);
        Object okFn = checkedCallable(() -> "ok");
        assertEquals("ok", runWithTimeout.invoke(svc, okFn, 100));
        Object timeoutFn = checkedCallable(() -> { Thread.sleep(50); return "x"; });
        InvocationTargetException timeoutEx = assertThrows(InvocationTargetException.class, () -> runWithTimeout.invoke(svc, timeoutFn, 1));
        assertTrue(timeoutEx.getCause() instanceof TimeoutException);
        Object failFn = checkedCallable(() -> { throw new IllegalArgumentException("bad"); });
        InvocationTargetException failEx = assertThrows(InvocationTargetException.class, () -> runWithTimeout.invoke(svc, failFn, 100));
        assertTrue(failEx.getCause() instanceof IllegalArgumentException);
    }

    @Test
    void resolvePricesAndComputeCostInfo_should_cover_repository_fallback_paths() throws Exception {
        LlmModelRepository modelRepo = mock(LlmModelRepository.class);
        LlmPriceConfigRepository priceRepo = mock(LlmPriceConfigRepository.class);

        LlmModelEntity m1 = new LlmModelEntity();
        m1.setModelName("provider/a:modelA");
        m1.setPriceConfigId(1L);
        LlmModelEntity m2 = new LlmModelEntity();
        m2.setModelName("modelB");
        m2.setPriceConfigId(null);
        when(modelRepo.findByEnvAndEnabledTrueAndModelNameInOrderByIsDefaultDescWeightDescIdAsc(anyString(), anyCollection()))
                .thenReturn(Arrays.asList(null, m1, m2));

        LlmPriceConfigEntity p1 = new LlmPriceConfigEntity();
        p1.setId(1L);
        p1.setCurrency("usd");
        p1.setInputCostPer1k(BigDecimal.ONE);
        p1.setOutputCostPer1k(BigDecimal.ONE);
        LlmPriceConfigEntity p2 = new LlmPriceConfigEntity();
        p2.setId(2L);
        p2.setName("modelB");
        p2.setCurrency("cny");
        p2.setMetadata(Map.of("pricing", Map.of("defaultInputCostPerUnit", BigDecimal.ONE)));
        when(priceRepo.findByIdIn(anyCollection())).thenReturn(Arrays.asList(null, p1));
        when(priceRepo.findByNameIn(anyCollection())).thenReturn(List.of(p2));

        AdminLlmLoadTestService svc = newService(modelRepo, priceRepo);

        Method resolvePrices = AdminLlmLoadTestService.class.getDeclaredMethod("resolvePrices", java.util.Collection.class);
        resolvePrices.setAccessible(true);
        Map<?, ?> empty = (Map<?, ?>) resolvePrices.invoke(svc, List.of("  "));
        assertTrue(empty.isEmpty());
        Map<?, ?> prices = (Map<?, ?>) resolvePrices.invoke(svc, Arrays.asList("provider/a:modelA", "modelB", null));
        assertEquals(2, prices.size());

        Class<?> modelAggClass = Class.forName(AdminLlmLoadTestService.class.getName() + "$ModelAgg");
        Constructor<?> aggCtor = modelAggClass.getDeclaredConstructor();
        aggCtor.setAccessible(true);
        Object aggA = aggCtor.newInstance();
        Object aggB = aggCtor.newInstance();
        Method sumAdd = Class.forName("java.util.concurrent.atomic.LongAdder").getDeclaredMethod("add", long.class);
        var inField = modelAggClass.getDeclaredField("in");
        inField.setAccessible(true);
        var outField = modelAggClass.getDeclaredField("out");
        outField.setAccessible(true);
        Object inA = inField.get(aggA);
        Object outA = outField.get(aggA);
        Object inB = inField.get(aggB);
        Object outB = outField.get(aggB);
        sumAdd.invoke(inA, 1000L);
        sumAdd.invoke(outA, 500L);
        sumAdd.invoke(inB, 1000L);
        sumAdd.invoke(outB, 500L);

        Map<String, Object> byModel = new ConcurrentHashMap<>();
        byModel.put("provider/a:modelA", aggA);
        byModel.put("modelB", aggB);
        byModel.put("   ", aggB);

        Class<?> pricingModeClass = Class.forName("com.example.EnterpriseRagCommunity.service.monitor.LlmPricing$Mode");
        Object mode = Enum.valueOf((Class<Enum>) pricingModeClass.asSubclass(Enum.class), "DEFAULT");
        Method computeCostInfo = AdminLlmLoadTestService.class.getDeclaredMethod("computeCostInfo", Map.class, pricingModeClass);
        computeCostInfo.setAccessible(true);
        Object cost = computeCostInfo.invoke(svc, byModel, mode);
        assertNotNull(cost);
        Method currency = cost.getClass().getDeclaredMethod("currency");
        currency.setAccessible(true);
        assertEquals("MIXED", currency.invoke(cost));

        Object nullCost = computeCostInfo.invoke(svc, Map.of(), mode);
        assertNull(nullCost);
    }

    private static Method findCompatibleStaticMethod(String name, Object[] args) throws NoSuchMethodException {
        for (Method m : AdminLlmLoadTestService.class.getDeclaredMethods()) {
            if (!m.getName().equals(name) || !Modifier.isStatic(m.getModifiers())) {
                continue;
            }
            Class<?>[] pts = m.getParameterTypes();
            if (pts.length != (args == null ? 0 : args.length)) {
                continue;
            }
            boolean ok = true;
            for (int i = 0; i < pts.length; i++) {
                Object arg = args[i];
                if (arg == null) {
                    continue;
                }
                if (!wrap(pts[i]).isInstance(arg)) {
                    ok = false;
                    break;
                }
            }
            if (ok) {
                return m;
            }
        }
        throw new NoSuchMethodException(name);
    }

    private static Class<?> wrap(Class<?> c) {
        if (!c.isPrimitive()) {
            return c;
        }
        if (c == int.class) return Integer.class;
        if (c == long.class) return Long.class;
        if (c == double.class) return Double.class;
        if (c == float.class) return Float.class;
        if (c == boolean.class) return Boolean.class;
        if (c == byte.class) return Byte.class;
        if (c == short.class) return Short.class;
        if (c == char.class) return Character.class;
        return c;
    }

    @Test
    void staticHelpers_should_cover_main_branches() throws Exception {
        assertNull(invokeStatic("trimToNull", new Class[]{String.class}, new Object[]{null}));
        assertNull(invokeStatic("trimToNull", new Class[]{String.class}, new Object[]{"   "}));
        assertEquals("a", invokeStatic("trimToNull", new Class[]{String.class}, new Object[]{" a "}));

        assertNull(invokeStatic("toNonBlank", new Class[]{Object.class}, new Object[]{null}));
        assertNull(invokeStatic("toNonBlank", new Class[]{Object.class}, new Object[]{"   "}));
        assertEquals("42", invokeStatic("toNonBlank", new Class[]{Object.class}, new Object[]{42}));

        assertEquals(10, invokeStatic("clampInt", new Class[]{Integer.class, int.class, int.class, int.class}, new Object[]{null, 1, 20, 10}));
        assertEquals(1, invokeStatic("clampInt", new Class[]{Integer.class, int.class, int.class, int.class}, new Object[]{0, 1, 20, 10}));
        assertEquals(20, invokeStatic("clampInt", new Class[]{Integer.class, int.class, int.class, int.class}, new Object[]{21, 1, 20, 10}));
        assertEquals(7, invokeStatic("clampInt", new Class[]{Integer.class, int.class, int.class, int.class}, new Object[]{7, 1, 20, 10}));

        assertFalse((Boolean) invokeStatic("supportsThinkingDirectiveModel", new Class[]{String.class}, new Object[]{null}));
        assertFalse((Boolean) invokeStatic("supportsThinkingDirectiveModel", new Class[]{String.class}, new Object[]{"qwen3-thinking"}));
        assertTrue((Boolean) invokeStatic("supportsThinkingDirectiveModel", new Class[]{String.class}, new Object[]{"qwen3-8b"}));
        assertTrue((Boolean) invokeStatic("supportsThinkingDirectiveModel", new Class[]{String.class}, new Object[]{"provider/qwen-plus-2025-04-28"}));
        assertFalse((Boolean) invokeStatic("supportsThinkingDirectiveModel", new Class[]{String.class}, new Object[]{"gpt-4"}));

        assertEquals("a", invokeStatic("applyThinkingDirective", new Class[]{String.class, boolean.class, String.class}, new Object[]{"a", false, "gpt"}));
        assertEquals("x/think", invokeStatic("applyThinkingDirective", new Class[]{String.class, boolean.class, String.class}, new Object[]{"x/think", true, "qwen3-8b"}));
        assertEquals("x\n/think", invokeStatic("applyThinkingDirective", new Class[]{String.class, boolean.class, String.class}, new Object[]{"x", true, "qwen3-8b"}));
        assertEquals("x\n/no_think", invokeStatic("applyThinkingDirective", new Class[]{String.class, boolean.class, String.class}, new Object[]{"x", false, "qwen3-8b"}));
        assertEquals("x\n/no_think", invokeStatic("applyThinkingDirective", new Class[]{String.class, boolean.class, String.class}, new Object[]{"x\n", false, "qwen3-8b"}));

        assertEquals(-1, invokeStatic("indexOfIgnoreCase", new Class[]{String.class, String.class, int.class}, new Object[]{null, "a", 0}));
        assertEquals(0, invokeStatic("indexOfIgnoreCase", new Class[]{String.class, String.class, int.class}, new Object[]{"abc", "", -1}));
        assertEquals(-1, invokeStatic("indexOfIgnoreCase", new Class[]{String.class, String.class, int.class}, new Object[]{"ab", "abc", 0}));
        assertEquals(2, invokeStatic("indexOfIgnoreCase", new Class[]{String.class, String.class, int.class}, new Object[]{"AbCd", "cd", 0}));

        assertNull(invokeStatic("removeMarkerWordIgnoreCase", new Class[]{String.class, String.class}, new Object[]{null, "x"}));
        assertEquals("abc", invokeStatic("removeMarkerWordIgnoreCase", new Class[]{String.class, String.class}, new Object[]{"abc", " "} ));
        assertEquals("abc", invokeStatic("removeMarkerWordIgnoreCase", new Class[]{String.class, String.class}, new Object[]{"abc", "X"}));
        assertEquals("ab", invokeStatic("removeMarkerWordIgnoreCase", new Class[]{String.class, String.class}, new Object[]{"aXXb", "x"}));

        String stripped = (String) invokeStatic("stripReasoningArtifacts", new Class[]{String.class}, new Object[]{"<reasoning_content>abc</reasoning_content>"});
        assertFalse(stripped.toLowerCase().contains("reasoning_content"));

        assertNull(invokeStatic("extractDeltaStringField", new Class[]{String.class, String.class}, new Object[]{null, "content"}));
        assertNull(invokeStatic("extractDeltaStringField", new Class[]{String.class, String.class}, new Object[]{"{}", ""}));
        assertNull(invokeStatic("extractDeltaStringField", new Class[]{String.class, String.class}, new Object[]{"{\"x\":\"1\"}", "content"}));
        assertEquals("你\n好", invokeStatic("extractDeltaStringField", new Class[]{String.class, String.class}, new Object[]{"{\"content\":\"\\u4f60\\n\\u597d\"}", "content"}));

        assertNull(invokeStatic("extractDeltaText", new Class[]{String.class, boolean.class}, new Object[]{null, false}));
        assertEquals("abc", invokeStatic("extractDeltaText", new Class[]{String.class, boolean.class}, new Object[]{"{\"content\":\"abc\"}", false}));
        assertEquals("r", invokeStatic("extractDeltaText", new Class[]{String.class, boolean.class}, new Object[]{"{\"reasoning_content\":\"r\",\"text\":\"t\"}", true}));
        assertEquals("t", invokeStatic("extractDeltaText", new Class[]{String.class, boolean.class}, new Object[]{"{\"text\":\"t\"}", false}));
        assertEquals("abc", invokeStatic("extractDeltaText", new Class[]{String.class, boolean.class}, new Object[]{"{\"content\":\"abc\"}", false}));

        assertEquals("error", invokeStatic("safeMessage", new Class[]{Exception.class}, new Object[]{null}));
        assertEquals("IllegalStateException", invokeStatic("safeMessage", new Class[]{Exception.class}, new Object[]{new IllegalStateException(" ")}));
        assertEquals("boom", invokeStatic("safeMessage", new Class[]{Exception.class}, new Object[]{new RuntimeException("boom")}));

        Object cfgZero = newNormalizedConfig(1, 1, 0, 0, null, null, false, 1000, 0, 0, "a", "b");
        assertEquals("MODERATION_TEST", invokeStatic("weightedPick", new Class[]{cfgZero.getClass(), long.class}, new Object[]{cfgZero, 1L}).toString());
        Object cfgMixed = newNormalizedConfig(1, 1, 2, 1, null, null, false, 1000, 0, 0, "a", "b");
        assertEquals("CHAT_STREAM", invokeStatic("weightedPick", new Class[]{cfgMixed.getClass(), long.class}, new Object[]{cfgMixed, 0L}).toString());
        assertEquals("MODERATION_TEST", invokeStatic("weightedPick", new Class[]{cfgMixed.getClass(), long.class}, new Object[]{cfgMixed, 2L}).toString());

        assertNull(invokeStatic("normalizeModel", new Class[]{String.class}, new Object[]{null}));
        assertNull(invokeStatic("normalizeModel", new Class[]{String.class}, new Object[]{"   "}));
        assertEquals("m", invokeStatic("normalizeModel", new Class[]{String.class}, new Object[]{"x/y:m"}));

        assertNull(invokeStatic("normalizeCurrency", new Class[]{String.class}, new Object[]{null}));
        assertNull(invokeStatic("normalizeCurrency", new Class[]{String.class}, new Object[]{"  "}));
        assertEquals("USD", invokeStatic("normalizeCurrency", new Class[]{String.class}, new Object[]{" usd "}));

        assertNull(invokeStatic("percentile", new Class[]{long[].class, double.class}, new Object[]{null, 0.5}));
        assertNull(invokeStatic("percentile", new Class[]{long[].class, double.class}, new Object[]{new long[]{1, 2}, Double.NaN}));
        assertEquals(5.0, invokeStatic("percentile", new Class[]{long[].class, double.class}, new Object[]{new long[]{5}, 0.5}));
        assertEquals(15.0, invokeStatic("percentile", new Class[]{long[].class, double.class}, new Object[]{new long[]{10, 20}, 0.5}));
    }

    private static AdminLlmLoadTestService newService(LlmModelRepository modelRepo, LlmPriceConfigRepository priceRepo) {
        return new AdminLlmLoadTestService(
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
        return c.newInstance(concurrency, totalRequests, weightChatStream, weightModeration, providerId, model, enableThinking, timeoutMs, retries, retryDelayMs, chatMessage, moderationText);
    }

    private static Class<?> checkedCallableClass() throws Exception {
        return Class.forName(AdminLlmLoadTestService.class.getName() + "$CheckedCallable");
    }

    private static Object checkedCallable(CallableBody body) throws Exception {
        Class<?> itf = checkedCallableClass();
        return Proxy.newProxyInstance(
                itf.getClassLoader(),
                new Class[]{itf},
                (proxy, method, args) -> {
                    if ("call".equals(method.getName())) return body.call();
                    if ("toString".equals(method.getName())) return "checkedCallable";
                    return null;
                }
        );
    }

    @FunctionalInterface
    private interface CallableBody {
        Object call() throws Exception;
    }
}
