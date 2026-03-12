package com.example.EnterpriseRagCommunity.service.monitor;

import com.example.EnterpriseRagCommunity.dto.moderation.LlmModerationTestResponse;
import com.example.EnterpriseRagCommunity.dto.monitor.AdminLlmLoadTestResultDTO;
import com.example.EnterpriseRagCommunity.entity.monitor.LlmLoadTestRunDetailEntity;
import com.example.EnterpriseRagCommunity.repository.ai.LlmModelRepository;
import com.example.EnterpriseRagCommunity.repository.ai.LlmPriceConfigRepository;
import com.example.EnterpriseRagCommunity.repository.monitor.LlmLoadTestRunDetailRepository;
import com.example.EnterpriseRagCommunity.repository.monitor.LlmLoadTestRunHistoryRepository;
import com.example.EnterpriseRagCommunity.repository.semantic.PromptsRepository;
import com.example.EnterpriseRagCommunity.service.ai.LlmGateway;
import com.example.EnterpriseRagCommunity.service.ai.TokenCountService;
import com.example.EnterpriseRagCommunity.service.ai.dto.ChatMessage;
import com.example.EnterpriseRagCommunity.service.moderation.admin.AdminModerationLlmService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.io.ByteArrayOutputStream;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AdminLlmLoadTestServiceDeepBranchCoverageTest {

    @Test
    void deepBranches_should_cover_extract_normalize_and_callModeration_paths() throws Exception {
        LlmGateway llmGateway = mock(LlmGateway.class);
        AdminModerationLlmService moderationSvc = mock(AdminModerationLlmService.class);
        LlmQueueMonitorService queueSvc = mock(LlmQueueMonitorService.class);
        TokenCountService tokenCountService = mock(TokenCountService.class);
        LlmModelRepository modelRepo = mock(LlmModelRepository.class);
        LlmPriceConfigRepository priceRepo = mock(LlmPriceConfigRepository.class);
        LlmLoadTestRunDetailRepository detailRepo = mock(LlmLoadTestRunDetailRepository.class);
        LlmLoadTestRunHistoryRepository historyRepo = mock(LlmLoadTestRunHistoryRepository.class);
        PromptsRepository promptsRepo = mock(PromptsRepository.class);

        when(modelRepo.findByEnvAndEnabledTrueAndModelNameInOrderByIsDefaultDescWeightDescIdAsc(anyString(), anyCollection())).thenReturn(List.of());
        when(priceRepo.findByIdIn(anyCollection())).thenReturn(List.of());
        when(priceRepo.findByNameIn(anyCollection())).thenReturn(List.of());
        when(detailRepo.saveAll(any())).thenAnswer(inv -> inv.getArgument(0));
        when(detailRepo.findByRunIdOrderByReqIndexAsc(anyString(), any(PageRequest.class))).thenReturn(Page.empty());
        when(historyRepo.findById(anyString())).thenReturn(Optional.empty());

        when(tokenCountService.normalizeOutputText(anyString(), anyBoolean())).thenReturn(
                new TokenCountService.NormalizedOutput("raw", "display", "tokenText", true, true)
        );
        when(tokenCountService.countTextTokens(anyString())).thenReturn(7);
        when(tokenCountService.countChatMessagesTokens(any())).thenReturn(11);

        LlmModerationTestResponse resp = new LlmModerationTestResponse();
        resp.setLatencyMs(null);
        resp.setModel("   ");
        resp.setRawModelOutput("{\"choices\":[{\"text\":\"hello\"}]}");
        LlmModerationTestResponse.Usage usage = new LlmModerationTestResponse.Usage();
        usage.setPromptTokens(null);
        usage.setCompletionTokens(null);
        usage.setTotalTokens(null);
        resp.setUsage(usage);
        List<LlmModerationTestResponse.Message> promptMessages = new ArrayList<>();
        promptMessages.add(null);
        LlmModerationTestResponse.Message m1 = new LlmModerationTestResponse.Message();
        m1.setRole("   ");
        m1.setContent("ignored");
        promptMessages.add(m1);
        LlmModerationTestResponse.Message m2 = new LlmModerationTestResponse.Message();
        m2.setRole("system");
        m2.setContent("sys");
        promptMessages.add(m2);
        resp.setPromptMessages(promptMessages);
        when(moderationSvc.test(any())).thenReturn(resp);

        AdminLlmLoadTestService svc = new AdminLlmLoadTestService(
                llmGateway, moderationSvc, queueSvc, tokenCountService, new ObjectMapper(),
                modelRepo, priceRepo, detailRepo, historyRepo, promptsRepo
        );

        try {
            Method extractRaw = AdminLlmLoadTestService.class.getDeclaredMethod("extractAssistantContentFromRawModelOutput", String.class);
            extractRaw.setAccessible(true);
            assertEquals("hello", extractRaw.invoke(svc, "{\"choices\":[{\"text\":\"hello\"}]}"));
            assertEquals("delta", extractRaw.invoke(svc, "{\"choices\":[{\"delta\":{\"content\":\"delta\"}}]}"));
            assertEquals("ot", extractRaw.invoke(svc, "{\"output_text\":\"ot\"}"));
            assertEquals("bad json", extractRaw.invoke(svc, "bad json"));

            assertEquals("\n\n", invokeStatic("stripReasoningArtifacts", new Class[]{String.class}, new Object[]{"\n\n"}));
            assertNotNull(invokeStatic("extractDeltaStringField", new Class[]{String.class, String.class}, new Object[]{"{\"content\":\"\\\"\\\\\\/\\b\\f\\n\\r\\t\\u4f60z\"}", "content"}));

            Object normalizedCfg = newNormalizedConfig(1, 1, 0, 1, "pid", "fallback-model", false, 2000, 0, 0, "c", "m");
            Object st = newRunState(svc, "run-x", normalizedCfg);

            Class<?> requestKindClass = Class.forName(AdminLlmLoadTestService.class.getName() + "$RequestKind");
            Object moderationKind = Enum.valueOf((Class<Enum>) requestKindClass.asSubclass(Enum.class), "MODERATION_TEST");
            Object prepared = newPreparedRequest(
                    moderationKind,
                    "{\"kind\":\"MODERATION_TEST\"}",
                    null,
                    "mod text"
            );
            Method callModeration = AdminLlmLoadTestService.class.getDeclaredMethod(
                    "callModeration",
                    st.getClass(),
                    prepared.getClass()
            );
            callModeration.setAccessible(true);
            Object result = callModeration.invoke(svc, st, prepared);
            assertNotNull(result);

            when(llmGateway.resolve(anyString())).thenThrow(new RuntimeException("resolve fail"));
            Method resolveModelName = AdminLlmLoadTestService.class.getDeclaredMethod("resolveModelNameForThinkDirective", String.class, String.class);
            resolveModelName.setAccessible(true);
            assertEquals(null, resolveModelName.invoke(svc, null, null));
            assertEquals(null, resolveModelName.invoke(svc, "pid", null));

            Method normalize = AdminLlmLoadTestService.class.getDeclaredMethod("normalize", com.example.EnterpriseRagCommunity.dto.monitor.AdminLlmLoadTestRunRequestDTO.class);
            normalize.setAccessible(true);
            Object n1 = normalize.invoke(null, new Object[]{null});
            assertNotNull(n1);
        } finally {
            svc.shutdown();
        }
    }

    @Test
    void deepBranches_should_cover_export_recompute_and_long_text_paths() throws Exception {
        LlmGateway llmGateway = mock(LlmGateway.class);
        AdminModerationLlmService moderationSvc = mock(AdminModerationLlmService.class);
        LlmQueueMonitorService queueSvc = mock(LlmQueueMonitorService.class);
        TokenCountService tokenCountService = mock(TokenCountService.class);
        LlmModelRepository modelRepo = mock(LlmModelRepository.class);
        LlmPriceConfigRepository priceRepo = mock(LlmPriceConfigRepository.class);
        LlmLoadTestRunDetailRepository detailRepo = mock(LlmLoadTestRunDetailRepository.class);
        LlmLoadTestRunHistoryRepository historyRepo = mock(LlmLoadTestRunHistoryRepository.class);
        PromptsRepository promptsRepo = mock(PromptsRepository.class);

        when(modelRepo.findByEnvAndEnabledTrueAndModelNameInOrderByIsDefaultDescWeightDescIdAsc(anyString(), anyCollection())).thenReturn(List.of());
        when(priceRepo.findByIdIn(anyCollection())).thenReturn(List.of());
        when(priceRepo.findByNameIn(anyCollection())).thenReturn(List.of());
        when(tokenCountService.countChatMessagesTokens(any())).thenReturn(13);
        when(tokenCountService.countTextTokens(anyString())).thenReturn(17);

        LlmLoadTestRunDetailEntity e1 = new LlmLoadTestRunDetailEntity();
        e1.setRunId("run-z");
        e1.setReqIndex(null);
        e1.setKind("CHAT_STREAM");
        e1.setOk(false);
        e1.setStartedAt(null);
        e1.setFinishedAt(null);
        e1.setLatencyMs(null);
        e1.setProviderId(null);
        e1.setModel(null);
        e1.setTokensIn(null);
        e1.setTokensOut(null);
        e1.setTotalTokens(null);
        e1.setError("err");
        e1.setRequestTruncated(true);
        e1.setResponseTruncated(true);
        e1.setRequestJson("{\"a\":1}");
        e1.setResponseJson("{bad}");
        LlmLoadTestRunDetailEntity e2 = new LlmLoadTestRunDetailEntity();
        e2.setRunId("run-z");
        e2.setReqIndex(2);
        e2.setKind("MODERATION_TEST");
        e2.setOk(true);
        e2.setStartedAt(LocalDateTime.now());
        e2.setFinishedAt(LocalDateTime.now());
        e2.setLatencyMs(1L);
        e2.setProviderId("pid");
        e2.setModel("m");
        e2.setTokensIn(1);
        e2.setTokensOut(2);
        e2.setTotalTokens(3);
        e2.setError(null);
        e2.setRequestTruncated(false);
        e2.setResponseTruncated(false);
        e2.setRequestJson("{}");
        e2.setResponseJson("{}");
        Page<LlmLoadTestRunDetailEntity> p1 = new PageImpl<>(java.util.Arrays.asList(null, e1), PageRequest.of(0, 2000), 3000);
        Page<LlmLoadTestRunDetailEntity> p2 = new PageImpl<>(List.of(e2), PageRequest.of(1, 2000), 3000);
        when(detailRepo.findByRunIdOrderByReqIndexAsc(anyString(), any(PageRequest.class)))
                .thenReturn(p1)
                .thenReturn(p2)
                .thenReturn(p2)
                .thenReturn(p2);
        when(detailRepo.existsByRunId(anyString())).thenReturn(true);
        when(detailRepo.saveAll(any())).thenAnswer(inv -> inv.getArgument(0));
        when(historyRepo.existsById(anyString())).thenReturn(true);
        when(historyRepo.findById(anyString())).thenThrow(new RuntimeException("history down"));

        AdminLlmLoadTestService svc = new AdminLlmLoadTestService(
                llmGateway, moderationSvc, queueSvc, tokenCountService, new ObjectMapper(),
                modelRepo, priceRepo, detailRepo, historyRepo, promptsRepo
        );

        try {
            Object normalizedCfg = newNormalizedConfig(1, 1, 1, 0, null, "qwen3-8b", false, 1000, 0, 0, "chat", "mod");
            Object st = newRunState(svc, "run-z", normalizedCfg);
            Field runsField = AdminLlmLoadTestService.class.getDeclaredField("runs");
            runsField.setAccessible(true);
            Map<String, Object> runs = (Map<String, Object>) runsField.get(svc);
            runs.put("run-z", st);

            Method writeJsonExport = AdminLlmLoadTestService.class.getDeclaredMethod("writeJsonExport", String.class, java.io.OutputStream.class);
            writeJsonExport.setAccessible(true);
            ByteArrayOutputStream jsonOut = new ByteArrayOutputStream();
            writeJsonExport.invoke(svc, "run-z", jsonOut);
            String json = jsonOut.toString(StandardCharsets.UTF_8);
            assertTrue(json.contains("\"summary\""));
            assertTrue(json.contains("\"details\""));

            Method writeCsvExport = AdminLlmLoadTestService.class.getDeclaredMethod("writeCsvExport", String.class, java.io.OutputStream.class);
            writeCsvExport.setAccessible(true);
            ByteArrayOutputStream csvOut = new ByteArrayOutputStream();
            writeCsvExport.invoke(svc, "run-z", csvOut);
            String csv = csvOut.toString(StandardCharsets.UTF_8);
            assertTrue(csv.contains("runId,index,kind"));

            Method truncateText = AdminLlmLoadTestService.class.getDeclaredMethod("truncateText", String.class);
            truncateText.setAccessible(true);
            String longText = "x".repeat(25000);
            Object truncated = truncateText.invoke(svc, longText);
            assertNotNull(truncated);

            AdminLlmLoadTestResultDTO dto = new AdminLlmLoadTestResultDTO();
            dto.setKind("MODERATION_TEST");
            dto.setTokensIn(null);
            dto.setTokensOut(null);
            dto.setTokens(9);
            dto.setModel(null);

            Object result = newResult(
                    1L,
                    null,
                    null,
                    null,
                    null,
                    List.of(ChatMessage.system("s"), ChatMessage.user("u")),
                    "out text",
                    "{}",
                    null
            );
            Method maybeRecompute = AdminLlmLoadTestService.class.getDeclaredMethod(
                    "maybeRecomputeTokensAsync",
                    st.getClass(),
                    AdminLlmLoadTestResultDTO.class,
                    result.getClass()
            );
            maybeRecompute.setAccessible(true);
            maybeRecompute.invoke(svc, st, dto, result);
            TimeUnit.MILLISECONDS.sleep(150);
            assertEquals(13, dto.getTokensIn());
            assertEquals(17, dto.getTokensOut());
            assertEquals(30, dto.getTokens());
        } finally {
            svc.shutdown();
        }
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

    private static Object newPreparedRequest(Object kind, String requestJson, List<ChatMessage> chatMessages, String moderationText) throws Exception {
        Class<?> cls = Class.forName(AdminLlmLoadTestService.class.getName() + "$PreparedRequest");
        Constructor<?> c = cls.getDeclaredConstructor(
                kind.getClass(),
                String.class,
                List.class,
                String.class
        );
        c.setAccessible(true);
        return c.newInstance(kind, requestJson, chatMessages, moderationText);
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
        Class<?> cls = Class.forName(AdminLlmLoadTestService.class.getName() + "$Result");
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
