package com.example.EnterpriseRagCommunity.service.ai;

import com.example.EnterpriseRagCommunity.dto.ai.AiPostComposeStreamRequest;
import com.example.EnterpriseRagCommunity.dto.ai.PortalChatConfigDTO;
import com.example.EnterpriseRagCommunity.entity.access.UsersEntity;
import com.example.EnterpriseRagCommunity.entity.ai.LlmModelEntity;
import com.example.EnterpriseRagCommunity.entity.content.PostComposeAiSnapshotsEntity;
import com.example.EnterpriseRagCommunity.entity.content.enums.PostComposeAiSnapshotStatus;
import com.example.EnterpriseRagCommunity.entity.semantic.PromptsEntity;
import com.example.EnterpriseRagCommunity.repository.access.UsersRepository;
import com.example.EnterpriseRagCommunity.repository.ai.LlmModelRepository;
import com.example.EnterpriseRagCommunity.repository.content.PostComposeAiSnapshotsRepository;
import com.example.EnterpriseRagCommunity.repository.monitor.FileAssetsRepository;
import com.example.EnterpriseRagCommunity.repository.semantic.PromptsRepository;
import com.example.EnterpriseRagCommunity.service.ai.client.OpenAiCompatClient;
import com.example.EnterpriseRagCommunity.service.ai.dto.ChatMessage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockHttpServletResponse;

import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AiPostComposeAssistantServiceTest {

    @TempDir
    Path tempDir;

    private AiPostComposeAssistantService newService(
            PostComposeAiSnapshotsRepository snapshotsRepository,
            UsersRepository usersRepository,
            LlmGateway llmGateway,
            LlmModelRepository llmModelRepository,
            FileAssetsRepository fileAssetsRepository,
            PortalChatConfigService portalChatConfigService,
            PromptsRepository promptsRepository,
            String uploadRoot,
            String urlPrefix
    ) {
        AiPostComposeAssistantService s = new AiPostComposeAssistantService(
                snapshotsRepository,
                usersRepository,
                llmGateway,
                llmModelRepository,
                fileAssetsRepository,
                portalChatConfigService,
                promptsRepository
        );
        setField(s, "uploadRoot", uploadRoot);
        setField(s, "urlPrefix", urlPrefix);
        return s;
    }

    private static void setField(Object target, String name, Object value) {
        try {
            Field f = target.getClass().getDeclaredField(name);
            f.setAccessible(true);
            f.set(target, value);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private static PostComposeAiSnapshotsEntity snapPending(long snapId, long userId) {
        PostComposeAiSnapshotsEntity snap = new PostComposeAiSnapshotsEntity();
        snap.setId(snapId);
        snap.setUserId(userId);
        snap.setStatus(PostComposeAiSnapshotStatus.PENDING);
        snap.setBeforeTitle("t");
        snap.setBeforeContent("c");
        snap.setInstruction("i");
        snap.setExpiresAt(null);
        return snap;
    }

    private static PortalChatConfigService portalCfg(
            String providerId,
            String model,
            Double temperature,
            Double topP,
            Integer chatHistoryLimit,
            Boolean defaultDeepThink,
            String systemPromptCode,
            String deepThinkSystemPromptCode,
            String composeSystemPromptCode
    ) {
        PortalChatConfigDTO cfg = new PortalChatConfigDTO();
        PortalChatConfigDTO.PostComposeAssistantConfigDTO p = new PortalChatConfigDTO.PostComposeAssistantConfigDTO();
        p.setProviderId(providerId);
        p.setModel(model);
        p.setTemperature(temperature);
        p.setTopP(topP);
        p.setChatHistoryLimit(chatHistoryLimit);
        p.setDefaultDeepThink(defaultDeepThink);
        p.setSystemPromptCode(systemPromptCode);
        p.setDeepThinkSystemPromptCode(deepThinkSystemPromptCode);
        p.setComposeSystemPromptCode(composeSystemPromptCode);
        cfg.setPostComposeAssistant(p);
        PortalChatConfigService svc = mock(PortalChatConfigService.class);
        when(svc.getConfigOrDefault()).thenReturn(cfg);
        return svc;
    }

    private static PromptsRepository promptsRepo(String... codes) {
        PromptsRepository repo = mock(PromptsRepository.class);
        when(repo.findByPromptCode(anyString())).thenReturn(Optional.empty());
        for (String code : codes) {
            PromptsEntity e = new PromptsEntity();
            e.setSystemPrompt("prompt:" + code);
            when(repo.findByPromptCode(code)).thenReturn(Optional.of(e));
        }
        return repo;
    }

    private static UsersRepository usersRepoWithDefaultPrompt(long userId, String defaultSystemPrompt) {
        UsersRepository repo = mock(UsersRepository.class);
        UsersEntity u = new UsersEntity();
        u.setId(userId);
        if (defaultSystemPrompt != null) {
            u.setMetadata(Map.of(
                    "preferences", Map.of(
                            "assistant", Map.of(
                                    "defaultSystemPrompt", defaultSystemPrompt
                            )
                    )
            ));
        }
        when(repo.findById(userId)).thenReturn(Optional.of(u));
        return repo;
    }

    private static AiPostComposeStreamRequest req(long snapshotId) {
        AiPostComposeStreamRequest r = new AiPostComposeStreamRequest();
        r.setSnapshotId(snapshotId);
        r.setDeepThink(false);
        return r;
    }

    private static AiPostComposeStreamRequest.ImageInput img(Long fileAssetId, String url, String mimeType) {
        AiPostComposeStreamRequest.ImageInput i = new AiPostComposeStreamRequest.ImageInput();
        i.setFileAssetId(fileAssetId);
        i.setUrl(url);
        i.setMimeType(mimeType);
        return i;
    }

    private static MockHttpServletResponse newSseResponse() {
        MockHttpServletResponse resp = new MockHttpServletResponse();
        resp.setCharacterEncoding(StandardCharsets.UTF_8.name());
        return resp;
    }

    private Path writeTempFile(String relative, byte[] bytes) throws Exception {
        Path p = tempDir.resolve(relative);
        Files.createDirectories(p.getParent());
        Files.write(p, bytes);
        return p;
    }

    private static String encodeImageUrlForUpstream(AiPostComposeAssistantService s, AiPostComposeStreamRequest.ImageInput img) {
        try {
            Method m = AiPostComposeAssistantService.class.getDeclaredMethod(
                    "encodeImageUrlForUpstream",
                    AiPostComposeStreamRequest.ImageInput.class
            );
            m.setAccessible(true);
            return (String) m.invoke(s, img);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private static Object invokeStatic(String name, Class<?>[] paramTypes, Object[] args) {
        try {
            Method m = AiPostComposeAssistantService.class.getDeclaredMethod(name, paramTypes);
            m.setAccessible(true);
            return m.invoke(null, args);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private static Object invokeInstance(Object target, String name, Class<?>[] paramTypes, Object[] args) {
        try {
            Method m = target.getClass().getDeclaredMethod(name, paramTypes);
            m.setAccessible(true);
            return m.invoke(target, args);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private static AiProvidersConfigService.ResolvedProvider provider(String id, String defaultChatModel) {
        return new AiProvidersConfigService.ResolvedProvider(
                id,
                "OPENAI_COMPAT",
                "http://localhost",
                "k",
                defaultChatModel,
                null,
                null,
                null,
                null,
                null
        );
    }

    @SuppressWarnings("unchecked")
    private static <T extends Throwable> void sneakyThrow(Throwable t) throws T {
        throw (T) t;
    }

    private static Throwable unwrapInvoke(RuntimeException ex) {
        Throwable c = ex.getCause();
        if (c instanceof java.lang.reflect.InvocationTargetException ite) {
            return ite.getTargetException();
        }
        return c;
    }

    @Test
    void streamComposeEdit_should_throw_when_snapshot_missing() {
        PostComposeAiSnapshotsRepository snapshotsRepository = mock(PostComposeAiSnapshotsRepository.class);
        when(snapshotsRepository.findByIdAndUserId(eq(1L), eq(10L))).thenReturn(Optional.empty());

        AiPostComposeAssistantService s = newService(
                snapshotsRepository,
                mock(UsersRepository.class),
                mock(LlmGateway.class),
                mock(LlmModelRepository.class),
                mock(FileAssetsRepository.class),
                portalCfg("p", "m", 0.6, 0.9, 20, false, "base", "deep", "compose"),
                promptsRepo("base", "deep", "compose"),
                tempDir.toString(),
                "/uploads"
        );

        IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class,
                () -> s.streamComposeEdit(req(1L), 10L, newSseResponse())
        );
        assertTrue(ex.getMessage().contains("快照不存在"));
    }

    @Test
    void streamComposeEdit_should_throw_when_snapshot_not_pending() {
        PostComposeAiSnapshotsEntity snap = snapPending(1L, 10L);
        snap.setStatus(PostComposeAiSnapshotStatus.APPLIED);

        PostComposeAiSnapshotsRepository snapshotsRepository = mock(PostComposeAiSnapshotsRepository.class);
        when(snapshotsRepository.findByIdAndUserId(eq(1L), eq(10L))).thenReturn(Optional.of(snap));

        AiPostComposeAssistantService s = newService(
                snapshotsRepository,
                mock(UsersRepository.class),
                mock(LlmGateway.class),
                mock(LlmModelRepository.class),
                mock(FileAssetsRepository.class),
                portalCfg("p", "m", 0.6, 0.9, 20, false, "base", "deep", "compose"),
                promptsRepo("base", "deep", "compose"),
                tempDir.toString(),
                "/uploads"
        );

        IllegalStateException ex = assertThrows(
                IllegalStateException.class,
                () -> s.streamComposeEdit(req(1L), 10L, newSseResponse())
        );
        assertTrue(ex.getMessage().contains("快照已处理"));
    }

    @Test
    void streamComposeEdit_should_throw_when_snapshot_expired() {
        PostComposeAiSnapshotsEntity snap = snapPending(1L, 10L);
        snap.setExpiresAt(LocalDateTime.now().minusSeconds(1));

        PostComposeAiSnapshotsRepository snapshotsRepository = mock(PostComposeAiSnapshotsRepository.class);
        when(snapshotsRepository.findByIdAndUserId(eq(1L), eq(10L))).thenReturn(Optional.of(snap));

        AiPostComposeAssistantService s = newService(
                snapshotsRepository,
                mock(UsersRepository.class),
                mock(LlmGateway.class),
                mock(LlmModelRepository.class),
                mock(FileAssetsRepository.class),
                portalCfg("p", "m", 0.6, 0.9, 20, false, "base", "deep", "compose"),
                promptsRepo("base", "deep", "compose"),
                tempDir.toString(),
                "/uploads"
        );

        IllegalStateException ex = assertThrows(
                IllegalStateException.class,
                () -> s.streamComposeEdit(req(1L), 10L, newSseResponse())
        );
        assertTrue(ex.getMessage().contains("快照已过期"));
    }

    @Test
    void streamComposeEdit_should_write_meta_delta_and_done_when_upstream_returns_data_lines() throws Exception {
        long userId = 10L;
        PostComposeAiSnapshotsEntity snap = snapPending(1L, userId);
        snap.setInstruction(" ");

        PostComposeAiSnapshotsRepository snapshotsRepository = mock(PostComposeAiSnapshotsRepository.class);
        when(snapshotsRepository.findByIdAndUserId(eq(1L), eq(userId))).thenReturn(Optional.of(snap));

        LlmGateway llmGateway = mock(LlmGateway.class);
        doAnswer(invocation -> {
            OpenAiCompatClient.SseLineConsumer consumer = invocation.getArgument(8);
            consumer.onLine("");
            consumer.onLine("event: ignore");
            consumer.onLine("data: {\"choices\":[{\"delta\":{\"content\":\"hi\\n\\\"x\\\"\"}}]}");
            consumer.onLine("data: {\"choices\":[{\"text\":\"t2\"}]}");
            consumer.onLine("data: {}");
            consumer.onLine("data: [DONE]");
            return new LlmGateway.RoutedChatStreamResult("p", "m", null);
        }).when(llmGateway).chatStreamRouted(
                any(LlmQueueTaskType.class),
                anyString(),
                anyString(),
                any(List.class),
                any(),
                any(),
                any(),
                any(),
                any(OpenAiCompatClient.SseLineConsumer.class)
        );

        AiPostComposeAssistantService s = newService(
                snapshotsRepository,
                usersRepoWithDefaultPrompt(userId, "u-prompt"),
                llmGateway,
                mock(LlmModelRepository.class),
                mock(FileAssetsRepository.class),
                portalCfg("p", "m", 0.6, 0.9, 20, false, "base", "deep", "compose"),
                promptsRepo("base", "deep", "compose"),
                tempDir.toString(),
                "/uploads"
        );

        AiPostComposeStreamRequest r = req(1L);
        r.setDeepThink(false);
        r.setInstruction(null);

        MockHttpServletResponse resp = newSseResponse();
        s.streamComposeEdit(r, userId, resp);

        String body = resp.getContentAsString(StandardCharsets.UTF_8);
        assertTrue(body.contains("event: meta\n"));
        assertTrue(body.contains("event: delta\n"));
        assertTrue(body.contains("event: done\n"));
        assertTrue(body.contains("\\\\n"));
        assertTrue(body.contains("\\\\\"x\\\\\""));
    }

    @Test
    void streamComposeEdit_should_default_temperature_to_0_2_when_deepThink_and_temperature_null() throws Exception {
        long userId = 10L;
        PostComposeAiSnapshotsEntity snap = snapPending(1L, userId);
        snap.setTemperature(null);

        PostComposeAiSnapshotsRepository snapshotsRepository = mock(PostComposeAiSnapshotsRepository.class);
        when(snapshotsRepository.findByIdAndUserId(eq(1L), eq(userId))).thenReturn(Optional.of(snap));

        LlmGateway llmGateway = mock(LlmGateway.class);
        final Double[] capturedTemp = new Double[] { null };
        doAnswer(invocation -> {
            capturedTemp[0] = invocation.getArgument(4);
            return new LlmGateway.RoutedChatStreamResult("p", "m", null);
        }).when(llmGateway).chatStreamRouted(
                any(LlmQueueTaskType.class),
                anyString(),
                anyString(),
                any(List.class),
                any(),
                any(),
                any(),
                any(),
                any(OpenAiCompatClient.SseLineConsumer.class)
        );

        AiPostComposeAssistantService s = newService(
                snapshotsRepository,
                usersRepoWithDefaultPrompt(userId, null),
                llmGateway,
                mock(LlmModelRepository.class),
                mock(FileAssetsRepository.class),
                portalCfg("p", "m", null, 0.9, 20, true, "base", "deep", "compose"),
                promptsRepo("base", "deep", "compose"),
                tempDir.toString(),
                "/uploads"
        );

        AiPostComposeStreamRequest r = req(1L);
        r.setDeepThink(null);
        r.setTemperature(null);

        s.streamComposeEdit(r, userId, newSseResponse());

        assertEquals(0.2, capturedTemp[0]);
    }

    @Test
    void streamComposeEdit_should_map_history_roles_and_apply_limit() throws Exception {
        long userId = 10L;
        PostComposeAiSnapshotsEntity snap = snapPending(1L, userId);

        PostComposeAiSnapshotsRepository snapshotsRepository = mock(PostComposeAiSnapshotsRepository.class);
        when(snapshotsRepository.findByIdAndUserId(eq(1L), eq(userId))).thenReturn(Optional.of(snap));

        LlmGateway llmGateway = mock(LlmGateway.class);
        final List<ChatMessage>[] capturedMessages = new List[] { null };
        doAnswer(invocation -> {
            capturedMessages[0] = invocation.getArgument(3);
            return new LlmGateway.RoutedChatStreamResult("p", "m", null);
        }).when(llmGateway).chatStreamRouted(
                any(LlmQueueTaskType.class),
                anyString(),
                anyString(),
                any(List.class),
                any(),
                any(),
                any(),
                any(),
                any(OpenAiCompatClient.SseLineConsumer.class)
        );

        AiPostComposeAssistantService s = newService(
                snapshotsRepository,
                usersRepoWithDefaultPrompt(userId, null),
                llmGateway,
                mock(LlmModelRepository.class),
                mock(FileAssetsRepository.class),
                portalCfg("p", "m", 0.6, 0.9, 1, false, "base", "deep", "compose"),
                promptsRepo("base", "deep", "compose"),
                tempDir.toString(),
                "/uploads"
        );

        AiPostComposeStreamRequest r = req(1L);
        AiPostComposeStreamRequest.ChatHistoryMessage m1 = new AiPostComposeStreamRequest.ChatHistoryMessage();
        m1.setRole("assistant");
        m1.setContent("a1");
        AiPostComposeStreamRequest.ChatHistoryMessage m2 = new AiPostComposeStreamRequest.ChatHistoryMessage();
        m2.setRole("USER");
        m2.setContent("u2");
        r.setChatHistory(List.of(m1, m2));

        s.streamComposeEdit(r, userId, newSseResponse());

        assertNotNull(capturedMessages[0]);
        assertTrue(capturedMessages[0].size() >= 2);
        ChatMessage lastHistory = capturedMessages[0].get(capturedMessages[0].size() - 2);
        assertEquals("user", lastHistory.role());
        assertEquals("u2", lastHistory.content());
    }

    @Test
    void streamComposeEdit_should_write_error_and_done_when_upstream_throws() throws Exception {
        long userId = 10L;
        PostComposeAiSnapshotsEntity snap = snapPending(1L, userId);

        PostComposeAiSnapshotsRepository snapshotsRepository = mock(PostComposeAiSnapshotsRepository.class);
        when(snapshotsRepository.findByIdAndUserId(eq(1L), eq(userId))).thenReturn(Optional.of(snap));

        LlmGateway llmGateway = mock(LlmGateway.class);
        when(llmGateway.chatStreamRouted(
                any(LlmQueueTaskType.class),
                anyString(),
                anyString(),
                any(List.class),
                any(),
                any(),
                any(),
                any(),
                any(OpenAiCompatClient.SseLineConsumer.class)
        )).thenThrow(new RuntimeException("boom"));

        AiPostComposeAssistantService s = newService(
                snapshotsRepository,
                usersRepoWithDefaultPrompt(userId, null),
                llmGateway,
                mock(LlmModelRepository.class),
                mock(FileAssetsRepository.class),
                portalCfg("p", "m", 0.6, 0.9, 20, false, "base", "deep", "compose"),
                promptsRepo("base", "deep", "compose"),
                tempDir.toString(),
                "/uploads"
        );

        MockHttpServletResponse resp = newSseResponse();
        s.streamComposeEdit(req(1L), userId, resp);

        String body = resp.getContentAsString(StandardCharsets.UTF_8);
        assertTrue(body.contains("event: error\n"));
        assertTrue(body.contains("上游AI调用失败"));
        assertTrue(body.contains("event: done\n"));
        assertFalse(body.contains("event: delta\n"));
        verify(llmGateway).chatStreamRouted(
                any(LlmQueueTaskType.class),
                anyString(),
                anyString(),
                any(List.class),
                any(),
                any(),
                any(),
                any(),
                any(OpenAiCompatClient.SseLineConsumer.class)
        );
    }

    @Test
    void resolveImages_should_merge_dedupe_limit_and_filter() throws Exception {
        PostComposeAiSnapshotsEntity snap = snapPending(1L, 10L);
        snap.setBeforeMetadata(Map.of(
                "attachments", List.of(
                        Map.of("fileUrl", "/uploads/a.png", "mimeType", "image/png", "fileAssetId", 1),
                        Map.of("url", "/uploads/a.png", "mimeType", "image/png", "fileAssetId", 2),
                        Map.of("fileUrl", "https://ex/b.jpg", "id", "bad"),
                        Map.of("fileUrl", "https://ex/c.txt", "mimeType", "text/plain"),
                        Map.of("fileUrl", "https://ex/d.webp")
                )
        ));

        AiPostComposeStreamRequest r = req(1L);
        r.setImages(List.of(
                img(10L, "https://ex/e.gif", null),
                img(11L, "https://ex/f.jpeg", null),
                img(12L, "https://ex/g.png", null),
                img(13L, "https://ex/a.png", null)
        ));

        Method m = AiPostComposeAssistantService.class.getDeclaredMethod(
                "resolveImages",
                AiPostComposeStreamRequest.class,
                PostComposeAiSnapshotsEntity.class
        );
        m.setAccessible(true);
        @SuppressWarnings("unchecked")
        List<AiPostComposeStreamRequest.ImageInput> out = (List<AiPostComposeStreamRequest.ImageInput>) m.invoke(null, r, snap);

        assertNotNull(out);
        assertEquals(5, out.size());
        assertTrue(out.stream().anyMatch(x -> "/uploads/a.png".equals(x.getUrl())));
        assertTrue(out.stream().anyMatch(x -> "https://ex/b.jpg".equals(x.getUrl())));
        assertFalse(out.stream().anyMatch(x -> "https://ex/c.txt".equals(x.getUrl())));
    }

    @Test
    void streamComposeEdit_should_write_error_done_when_images_without_provider() throws Exception {
        long userId = 10L;
        PostComposeAiSnapshotsEntity snap = snapPending(1L, userId);

        PostComposeAiSnapshotsRepository snapshotsRepository = mock(PostComposeAiSnapshotsRepository.class);
        when(snapshotsRepository.findByIdAndUserId(eq(1L), eq(userId))).thenReturn(Optional.of(snap));

        LlmGateway llmGateway = mock(LlmGateway.class);
        when(llmGateway.resolve((String) null)).thenReturn(null);

        AiPostComposeAssistantService s = newService(
                snapshotsRepository,
                usersRepoWithDefaultPrompt(userId, null),
                llmGateway,
                mock(LlmModelRepository.class),
                mock(FileAssetsRepository.class),
                portalCfg(null, "m-vision", 0.6, 0.9, 20, false, "base", "deep", "compose"),
                promptsRepo("base", "deep", "compose"),
                tempDir.toString(),
                "/uploads"
        );

        AiPostComposeStreamRequest r = req(1L);
        r.setImages(List.of(img(1L, "https://ex/a.png", null)));

        MockHttpServletResponse resp = newSseResponse();
        s.streamComposeEdit(r, userId, resp);

        String body = resp.getContentAsString(StandardCharsets.UTF_8);
        assertTrue(body.contains("event: error\n"));
        assertTrue(body.contains("未指定模型提供商"));
        assertTrue(body.contains("event: done\n"));
        verify(llmGateway, never()).chatStreamRouted(
                any(LlmQueueTaskType.class),
                anyString(),
                anyString(),
                any(List.class),
                any(),
                any(),
                any(),
                any(),
                any(OpenAiCompatClient.SseLineConsumer.class)
        );
    }

    @Test
    void streamComposeEdit_should_write_error_done_when_image_model_not_enabled() throws Exception {
        long userId = 10L;
        PostComposeAiSnapshotsEntity snap = snapPending(1L, userId);

        PostComposeAiSnapshotsRepository snapshotsRepository = mock(PostComposeAiSnapshotsRepository.class);
        when(snapshotsRepository.findByIdAndUserId(eq(1L), eq(userId))).thenReturn(Optional.of(snap));

        LlmModelRepository llmModelRepository = mock(LlmModelRepository.class);
        when(llmModelRepository.findByEnvAndProviderIdAndPurposeAndModelName(eq("default"), eq("p1"), eq("IMAGE_CHAT"), eq("m1")))
                .thenReturn(Optional.empty());

        LlmGateway llmGateway = mock(LlmGateway.class);

        AiPostComposeAssistantService s = newService(
                snapshotsRepository,
                usersRepoWithDefaultPrompt(userId, null),
                llmGateway,
                llmModelRepository,
                mock(FileAssetsRepository.class),
                portalCfg("p1", "m1", 0.6, 0.9, 20, false, "base", "deep", "compose"),
                promptsRepo("base", "deep", "compose"),
                tempDir.toString(),
                "/uploads"
        );

        AiPostComposeStreamRequest r = req(1L);
        r.setImages(List.of(img(1L, "https://ex/a.png", null)));

        MockHttpServletResponse resp = newSseResponse();
        s.streamComposeEdit(r, userId, resp);

        String body = resp.getContentAsString(StandardCharsets.UTF_8);
        assertTrue(body.contains("event: error\n"));
        assertTrue(body.contains("不支持图片"));
        assertTrue(body.contains("event: done\n"));
        verify(llmGateway, never()).chatStreamRouted(
                any(LlmQueueTaskType.class),
                anyString(),
                anyString(),
                any(List.class),
                any(),
                any(),
                any(),
                any(),
                any(OpenAiCompatClient.SseLineConsumer.class)
        );
    }

    @Test
    void streamComposeEdit_should_write_error_done_when_image_pool_empty_and_no_override() throws Exception {
        long userId = 10L;
        PostComposeAiSnapshotsEntity snap = snapPending(1L, userId);
        snap.setProviderId(null);
        snap.setModel(null);

        PostComposeAiSnapshotsRepository snapshotsRepository = mock(PostComposeAiSnapshotsRepository.class);
        when(snapshotsRepository.findByIdAndUserId(eq(1L), eq(userId))).thenReturn(Optional.of(snap));

        LlmModelRepository llmModelRepository = mock(LlmModelRepository.class);
        when(llmModelRepository.findByEnvAndPurposeAndEnabledTrueOrderBySortIndexAscPriorityDescWeightDescIsDefaultDescIdAsc(eq("default"), eq("IMAGE_CHAT")))
                .thenReturn(List.of());

        LlmGateway llmGateway = mock(LlmGateway.class);

        AiPostComposeAssistantService s = newService(
                snapshotsRepository,
                usersRepoWithDefaultPrompt(userId, null),
                llmGateway,
                llmModelRepository,
                mock(FileAssetsRepository.class),
                portalCfg(null, null, 0.6, 0.9, 20, false, "base", "deep", "compose"),
                promptsRepo("base", "deep", "compose"),
                tempDir.toString(),
                "/uploads"
        );

        AiPostComposeStreamRequest r = req(1L);
        r.setProviderId(null);
        r.setModel(null);
        r.setImages(List.of(img(1L, "https://ex/a.png", null)));

        MockHttpServletResponse resp = newSseResponse();
        s.streamComposeEdit(r, userId, resp);

        String body = resp.getContentAsString(StandardCharsets.UTF_8);
        assertTrue(body.contains("event: error\n"));
        assertTrue(body.contains("IMAGE_CHAT"));
        assertTrue(body.contains("event: done\n"));
        verify(llmGateway, never()).chatStreamRouted(
                any(LlmQueueTaskType.class),
                anyString(),
                anyString(),
                any(List.class),
                any(),
                any(),
                any(),
                any(),
                any(OpenAiCompatClient.SseLineConsumer.class)
        );
    }

    @Test
    void streamComposeEdit_should_build_multimodal_parts_when_image_model_enabled() throws Exception {
        long userId = 10L;
        PostComposeAiSnapshotsEntity snap = snapPending(1L, userId);
        snap.setBeforeMetadata(Map.of(
                "attachments", List.of(
                        Map.of("fileUrl", "/uploads/a.png", "mimeType", "image/png", "fileAssetId", 1),
                        Map.of("fileUrl", "/uploads/a.png", "mimeType", "image/png", "fileAssetId", 1)
                )
        ));

        PostComposeAiSnapshotsRepository snapshotsRepository = mock(PostComposeAiSnapshotsRepository.class);
        when(snapshotsRepository.findByIdAndUserId(eq(1L), eq(userId))).thenReturn(Optional.of(snap));

        LlmModelEntity enabled = new LlmModelEntity();
        enabled.setEnabled(true);
        LlmModelRepository llmModelRepository = mock(LlmModelRepository.class);
        when(llmModelRepository.findByEnvAndProviderIdAndPurposeAndModelName(eq("default"), eq("p1"), eq("IMAGE_CHAT"), eq("m1")))
                .thenReturn(Optional.of(enabled));

        LlmGateway llmGateway = mock(LlmGateway.class);
        final List<ChatMessage>[] capturedMessages = new List[] { null };
        final LlmQueueTaskType[] capturedTaskType = new LlmQueueTaskType[] { null };
        doAnswer(invocation -> {
            capturedTaskType[0] = invocation.getArgument(0);
            capturedMessages[0] = invocation.getArgument(3);
            return new LlmGateway.RoutedChatStreamResult("p1", "m1", null);
        }).when(llmGateway).chatStreamRouted(
                any(LlmQueueTaskType.class),
                anyString(),
                anyString(),
                any(List.class),
                any(),
                any(),
                any(),
                any(),
                any(OpenAiCompatClient.SseLineConsumer.class)
        );

        AiPostComposeAssistantService s = newService(
                snapshotsRepository,
                usersRepoWithDefaultPrompt(userId, null),
                llmGateway,
                llmModelRepository,
                mock(FileAssetsRepository.class),
                portalCfg("p1", "m1", 0.6, 0.9, 20, false, "base", "deep", "compose"),
                promptsRepo("base", "deep", "compose"),
                tempDir.toString(),
                "/uploads"
        );

        AiPostComposeStreamRequest r = req(1L);
        r.setImages(List.of(
                img(10L, "https://ex/e.gif", null),
                img(11L, "https://ex/f.jpeg", null),
                img(12L, "https://ex/g.png", null),
                img(13L, "https://ex/h.png", null),
                img(14L, "https://ex/i.png", null),
                img(15L, "https://ex/j.png", null)
        ));

        s.streamComposeEdit(r, userId, newSseResponse());

        assertEquals(LlmQueueTaskType.IMAGE_CHAT, capturedTaskType[0]);
        assertNotNull(capturedMessages[0]);
        ChatMessage last = capturedMessages[0].get(capturedMessages[0].size() - 1);
        assertEquals("user", last.role());
        assertTrue(last.content() instanceof List<?>);
        List<?> parts = (List<?>) last.content();
        assertTrue(parts.size() >= 2);
        assertTrue(parts.get(0) instanceof Map<?, ?>);
        Map<?, ?> first = (Map<?, ?>) parts.get(0);
        assertEquals("text", first.get("type"));
        assertTrue(String.valueOf(first.get("text")).contains("输出协议"));
    }

    @Test
    void encodeImageUrlForUpstream_should_pass_through_data_and_http_urls() {
        AiPostComposeAssistantService s = newService(
                mock(PostComposeAiSnapshotsRepository.class),
                mock(UsersRepository.class),
                mock(LlmGateway.class),
                mock(LlmModelRepository.class),
                mock(FileAssetsRepository.class),
                portalCfg("p", "m", 0.6, 0.9, 20, false, "base", "deep", "compose"),
                promptsRepo("base", "deep", "compose"),
                tempDir.toString(),
                "/uploads"
        );

        assertEquals(
                "data:image/png;base64,xxx",
                encodeImageUrlForUpstream(s, img(null, "data:image/png;base64,xxx", null))
        );
        assertEquals(
                "https://ex/a.png",
                encodeImageUrlForUpstream(s, img(null, "https://ex/a.png", null))
        );
        assertEquals(
                "http://ex/a.png",
                encodeImageUrlForUpstream(s, img(null, "http://ex/a.png", null))
        );
    }

    @Test
    void encodeImageUrlForUpstream_should_encode_local_upload_file_and_strip_query() throws Exception {
        writeTempFile("a.bin", "abc".getBytes(StandardCharsets.UTF_8));

        FileAssetsRepository fileAssetsRepository = mock(FileAssetsRepository.class);
        AiPostComposeAssistantService s = newService(
                mock(PostComposeAiSnapshotsRepository.class),
                mock(UsersRepository.class),
                mock(LlmGateway.class),
                mock(LlmModelRepository.class),
                fileAssetsRepository,
                portalCfg("p", "m", 0.6, 0.9, 20, false, "base", "deep", "compose"),
                promptsRepo("base", "deep", "compose"),
                tempDir.toString(),
                "/uploads"
        );

        String url = encodeImageUrlForUpstream(s, img(null, "/uploads/a.bin?x=1", "image/png"));
        assertTrue(url.startsWith("data:image/png;base64,"));
        assertTrue(url.endsWith("YWJj"));
        verify(fileAssetsRepository, never()).findById(any());
    }

    @Test
    void encodeImageUrlForUpstream_should_use_fileAsset_mimeType_when_missing() throws Exception {
        writeTempFile("a2.bin", "abc".getBytes(StandardCharsets.UTF_8));

        com.example.EnterpriseRagCommunity.entity.monitor.FileAssetsEntity fa = new com.example.EnterpriseRagCommunity.entity.monitor.FileAssetsEntity();
        fa.setId(1L);
        fa.setMimeType("image/gif");
        fa.setPath(tempDir.resolve("a2.bin").toString());

        FileAssetsRepository fileAssetsRepository = mock(FileAssetsRepository.class);
        when(fileAssetsRepository.findById(1L)).thenReturn(Optional.of(fa));

        AiPostComposeAssistantService s = newService(
                mock(PostComposeAiSnapshotsRepository.class),
                mock(UsersRepository.class),
                mock(LlmGateway.class),
                mock(LlmModelRepository.class),
                fileAssetsRepository,
                portalCfg("p", "m", 0.6, 0.9, 20, false, "base", "deep", "compose"),
                promptsRepo("base", "deep", "compose"),
                tempDir.toString(),
                "/uploads"
        );

        String url = encodeImageUrlForUpstream(s, img(1L, "/uploads/a2.bin", null));
        assertTrue(url.startsWith("data:image/gif;base64,"));
        assertTrue(url.endsWith("YWJj"));
    }

    @Test
    void encodeImageUrlForUpstream_should_return_original_url_when_missing_or_too_large_or_path_escape() throws Exception {
        byte[] big = new byte[4_000_001];
        Arrays.fill(big, (byte) 'a');
        writeTempFile("big.bin", big);
        writeTempFile("evil.txt", "abc".getBytes(StandardCharsets.UTF_8));

        AiPostComposeAssistantService s = newService(
                mock(PostComposeAiSnapshotsRepository.class),
                mock(UsersRepository.class),
                mock(LlmGateway.class),
                mock(LlmModelRepository.class),
                mock(FileAssetsRepository.class),
                portalCfg("p", "m", 0.6, 0.9, 20, false, "base", "deep", "compose"),
                promptsRepo("base", "deep", "compose"),
                tempDir.toString(),
                "/uploads"
        );

        assertEquals("/uploads/missing.bin", encodeImageUrlForUpstream(s, img(null, "/uploads/missing.bin", "image/png")));
        assertEquals("/uploads/big.bin", encodeImageUrlForUpstream(s, img(null, "/uploads/big.bin", "image/png")));
        assertEquals("/uploads/../evil.txt", encodeImageUrlForUpstream(s, img(null, "/uploads/../evil.txt", "image/png")));
    }

    @Test
    void encodeImageUrlForUpstream_should_read_by_fileAsset_path_when_url_not_under_prefix() throws Exception {
        Path p = writeTempFile("asset.bin", "abc".getBytes(StandardCharsets.UTF_8));

        com.example.EnterpriseRagCommunity.entity.monitor.FileAssetsEntity fa = new com.example.EnterpriseRagCommunity.entity.monitor.FileAssetsEntity();
        fa.setId(2L);
        fa.setMimeType("image/png");
        fa.setPath(p.toString());

        FileAssetsRepository fileAssetsRepository = mock(FileAssetsRepository.class);
        when(fileAssetsRepository.findById(2L)).thenReturn(Optional.of(fa));

        AiPostComposeAssistantService s = newService(
                mock(PostComposeAiSnapshotsRepository.class),
                mock(UsersRepository.class),
                mock(LlmGateway.class),
                mock(LlmModelRepository.class),
                fileAssetsRepository,
                portalCfg("p", "m", 0.6, 0.9, 20, false, "base", "deep", "compose"),
                promptsRepo("base", "deep", "compose"),
                tempDir.toString(),
                "/uploads"
        );

        String url = encodeImageUrlForUpstream(s, img(2L, "/other/path.png", "image/png"));
        assertTrue(url.startsWith("data:image/png;base64,"));
        assertTrue(url.endsWith("YWJj"));
    }

    @Test
    void jsonEscape_should_escape_quotes_backslash_and_control_chars() {
        String in = "\"\\\\\b\f\n\r\t" + ((char) 0x01);
        String out = (String) invokeStatic("jsonEscape", new Class<?>[] { String.class }, new Object[] { in });
        assertTrue(out.contains("\\\\\""));
        assertTrue(out.contains("\\\\\\\\"));
        assertTrue(out.contains("\\\\b"));
        assertTrue(out.contains("\\\\f"));
        assertTrue(out.contains("\\\\n"));
        assertTrue(out.contains("\\\\r"));
        assertTrue(out.contains("\\\\t"));
        assertTrue(out.contains("\\\\u0001"));
    }

    @Test
    void extractDeltaContent_should_handle_variants_and_failures() {
        AiPostComposeAssistantService s = newService(
                mock(PostComposeAiSnapshotsRepository.class),
                mock(UsersRepository.class),
                mock(LlmGateway.class),
                mock(LlmModelRepository.class),
                mock(FileAssetsRepository.class),
                portalCfg("p", "m", 0.6, 0.9, 20, false, "base", "deep", "compose"),
                promptsRepo("base", "deep", "compose"),
                tempDir.toString(),
                "/uploads"
        );

        String c1 = (String) invokeInstance(
                s,
                "extractDeltaContent",
                new Class<?>[] { String.class },
                new Object[] { "{\"choices\":[{\"delta\":{\"content\":\"x\"}}]}" }
        );
        assertEquals("x", c1);

        String c2 = (String) invokeInstance(
                s,
                "extractDeltaContent",
                new Class<?>[] { String.class },
                new Object[] { "{\"choices\":[{\"text\":\"y\"}]}" }
        );
        assertEquals("y", c2);

        Object c3 = invokeInstance(
                s,
                "extractDeltaContent",
                new Class<?>[] { String.class },
                new Object[] { "{\"choices\":[]}" }
        );
        assertNull(c3);

        Object c4 = invokeInstance(
                s,
                "extractDeltaContent",
                new Class<?>[] { String.class },
                new Object[] { "not-json" }
        );
        assertNull(c4);
    }

    @Test
    void joinSystemPrompts_and_firstNonBlank_should_handle_blanks() {
        assertEquals(
                "a\n\nb",
                invokeStatic(
                        "joinSystemPrompts",
                        new Class<?>[] { String[].class },
                        new Object[] { new String[] { " a ", null, " ", "b" } }
                )
        );
        assertEquals("", invokeStatic("joinSystemPrompts", new Class<?>[] { String[].class }, new Object[] { null }));

        assertEquals("x", invokeStatic("firstNonBlank", new Class<?>[] { String.class, String.class }, new Object[] { " x ", "y" }));
        assertEquals("y", invokeStatic("firstNonBlank", new Class<?>[] { String.class, String.class }, new Object[] { " ", " y " }));
        assertNull(invokeStatic("firstNonBlank", new Class<?>[] { String.class, String.class }, new Object[] { " ", " " }));

        assertEquals("z", invokeStatic("firstNonBlank", new Class<?>[] { String.class, String.class, String.class }, new Object[] { " ", null, " z " }));
        assertNull(invokeStatic("firstNonBlank", new Class<?>[] { String.class, String.class, String.class }, new Object[] { null, " ", " " }));
    }

    @Test
    void isLikelyImageUrl_toNonBlank_and_appendImagesAsText_should_handle_edges() throws Exception {
        assertTrue((Boolean) invokeStatic("isLikelyImageUrl", new Class<?>[] { String.class }, new Object[] { "/uploads/x" }));
        assertTrue((Boolean) invokeStatic("isLikelyImageUrl", new Class<?>[] { String.class }, new Object[] { "A.PNG" }));
        assertFalse((Boolean) invokeStatic("isLikelyImageUrl", new Class<?>[] { String.class }, new Object[] { "a.txt" }));
        assertFalse((Boolean) invokeStatic("isLikelyImageUrl", new Class<?>[] { String.class }, new Object[] { null }));
        assertTrue((Boolean) invokeStatic("isLikelyImageUrl", new Class<?>[] { String.class }, new Object[] { "a.bmp" }));
        assertTrue((Boolean) invokeStatic("isLikelyImageUrl", new Class<?>[] { String.class }, new Object[] { "a.svg" }));

        assertNull(invokeStatic("toNonBlank", new Class<?>[] { String.class }, new Object[] { "  " }));
        assertEquals("x", invokeStatic("toNonBlank", new Class<?>[] { String.class }, new Object[] { " x " }));

        Method m = AiPostComposeAssistantService.class.getDeclaredMethod("appendImagesAsText", String.class, List.class);
        m.setAccessible(true);
        String out = (String) m.invoke(
                null,
                "base",
                Arrays.asList(
                        img(null, "https://ex/1.png", null),
                        img(null, " ", null),
                        null,
                        img(null, "https://ex/2.png", null),
                        img(null, "https://ex/3.png", null),
                        img(null, "https://ex/4.png", null),
                        img(null, "https://ex/5.png", null),
                        img(null, "https://ex/6.png", null)
                )
        );
        assertTrue(out.contains("[IMAGES]"));
        assertTrue(out.contains("https://ex/1.png"));
        assertTrue(out.contains("https://ex/5.png"));
        assertFalse(out.contains("https://ex/6.png"));

        String out2 = (String) m.invoke(null, null, List.of());
        assertEquals("\n\n[IMAGES]\n", out2);
    }

    @Test
    void streamComposeEdit_should_include_assistant_history_message_when_within_limit() throws Exception {
        long userId = 10L;
        PostComposeAiSnapshotsEntity snap = snapPending(1L, userId);

        PostComposeAiSnapshotsRepository snapshotsRepository = mock(PostComposeAiSnapshotsRepository.class);
        when(snapshotsRepository.findByIdAndUserId(eq(1L), eq(userId))).thenReturn(Optional.of(snap));

        LlmGateway llmGateway = mock(LlmGateway.class);
        final List<ChatMessage>[] capturedMessages = new List[] { null };
        doAnswer(invocation -> {
            capturedMessages[0] = invocation.getArgument(3);
            return new LlmGateway.RoutedChatStreamResult("p", "m", null);
        }).when(llmGateway).chatStreamRouted(
                any(LlmQueueTaskType.class),
                anyString(),
                anyString(),
                any(List.class),
                any(),
                any(),
                any(),
                any(),
                any(OpenAiCompatClient.SseLineConsumer.class)
        );

        AiPostComposeAssistantService s = newService(
                snapshotsRepository,
                usersRepoWithDefaultPrompt(userId, null),
                llmGateway,
                mock(LlmModelRepository.class),
                mock(FileAssetsRepository.class),
                portalCfg("p", "m", 0.6, 0.9, 2, false, "base", "deep", "compose"),
                promptsRepo("base", "deep", "compose"),
                tempDir.toString(),
                "/uploads"
        );

        AiPostComposeStreamRequest r = req(1L);
        AiPostComposeStreamRequest.ChatHistoryMessage m1 = new AiPostComposeStreamRequest.ChatHistoryMessage();
        m1.setRole("assistant");
        m1.setContent("a1");
        AiPostComposeStreamRequest.ChatHistoryMessage m2 = new AiPostComposeStreamRequest.ChatHistoryMessage();
        m2.setRole("USER");
        m2.setContent("u2");
        r.setChatHistory(List.of(m1, m2));

        s.streamComposeEdit(r, userId, newSseResponse());

        assertNotNull(capturedMessages[0]);
        assertTrue(capturedMessages[0].stream().anyMatch(x -> "assistant".equals(x.role()) && "a1".equals(x.content())));
        assertTrue(capturedMessages[0].stream().anyMatch(x -> "user".equals(x.role()) && "u2".equals(x.content())));
    }

    @Test
    void ensureVisionModelForRequest_should_return_early_for_non_image_requests() {
        LlmGateway llmGateway = mock(LlmGateway.class);
        LlmModelRepository llmModelRepository = mock(LlmModelRepository.class);
        AiPostComposeAssistantService s = newService(
                mock(PostComposeAiSnapshotsRepository.class),
                mock(UsersRepository.class),
                llmGateway,
                llmModelRepository,
                mock(FileAssetsRepository.class),
                portalCfg("p", "m", 0.6, 0.9, 20, false, "base", "deep", "compose"),
                promptsRepo("base", "deep", "compose"),
                tempDir.toString(),
                "/uploads"
        );

        invokeInstance(
                s,
                "ensureVisionModelForRequest",
                new Class<?>[] { LlmQueueTaskType.class, String.class, String.class, boolean.class },
                new Object[] { LlmQueueTaskType.IMAGE_CHAT, "p1", "m1", false }
        );
        invokeInstance(
                s,
                "ensureVisionModelForRequest",
                new Class<?>[] { LlmQueueTaskType.class, String.class, String.class, boolean.class },
                new Object[] { LlmQueueTaskType.TEXT_CHAT, "p1", "m1", true }
        );

        verify(llmGateway, never()).resolve(any());
        verify(llmModelRepository, never()).findByEnvAndProviderIdAndPurposeAndModelName(any(), any(), any(), any());
    }

    @Test
    void ensureVisionModelForRequest_should_resolve_active_provider_when_model_override_set_but_provider_missing() {
        LlmGateway llmGateway = mock(LlmGateway.class);
        when(llmGateway.resolve((String) null)).thenReturn(provider("p1", "mX"));

        LlmModelEntity enabled = new LlmModelEntity();
        enabled.setEnabled(true);
        LlmModelRepository llmModelRepository = mock(LlmModelRepository.class);
        when(llmModelRepository.findByEnvAndProviderIdAndPurposeAndModelName(eq("default"), eq("p1"), eq("IMAGE_CHAT"), eq("m1")))
                .thenReturn(Optional.of(enabled));

        AiPostComposeAssistantService s = newService(
                mock(PostComposeAiSnapshotsRepository.class),
                mock(UsersRepository.class),
                llmGateway,
                llmModelRepository,
                mock(FileAssetsRepository.class),
                portalCfg(null, "m1", 0.6, 0.9, 20, false, "base", "deep", "compose"),
                promptsRepo("base", "deep", "compose"),
                tempDir.toString(),
                "/uploads"
        );

        invokeInstance(
                s,
                "ensureVisionModelForRequest",
                new Class<?>[] { LlmQueueTaskType.class, String.class, String.class, boolean.class },
                new Object[] { LlmQueueTaskType.IMAGE_CHAT, null, "m1", true }
        );
    }

    @Test
    void ensureVisionModelForRequest_should_throw_when_active_provider_resolution_throws() {
        LlmGateway llmGateway = mock(LlmGateway.class);
        doAnswer(invocation -> {
            sneakyThrow(new IOException("io"));
            return null;
        }).when(llmGateway).resolve((String) null);

        AiPostComposeAssistantService s = newService(
                mock(PostComposeAiSnapshotsRepository.class),
                mock(UsersRepository.class),
                llmGateway,
                mock(LlmModelRepository.class),
                mock(FileAssetsRepository.class),
                portalCfg(null, "m1", 0.6, 0.9, 20, false, "base", "deep", "compose"),
                promptsRepo("base", "deep", "compose"),
                tempDir.toString(),
                "/uploads"
        );

        RuntimeException ex = assertThrows(
                RuntimeException.class,
                () -> invokeInstance(
                        s,
                        "ensureVisionModelForRequest",
                        new Class<?>[] { LlmQueueTaskType.class, String.class, String.class, boolean.class },
                        new Object[] { LlmQueueTaskType.IMAGE_CHAT, null, "m1", true }
                )
        );
        Throwable t = unwrapInvoke(ex);
        assertTrue(t instanceof IllegalArgumentException);
        assertTrue(t.getMessage().contains("未指定模型提供商"));
    }

    @Test
    void ensureVisionModelForRequest_should_use_provider_default_model_when_providerId_provided() {
        LlmGateway llmGateway = mock(LlmGateway.class);
        when(llmGateway.resolve("p1")).thenReturn(provider("p1", "m1"));

        LlmModelEntity enabled = new LlmModelEntity();
        enabled.setEnabled(true);
        LlmModelRepository llmModelRepository = mock(LlmModelRepository.class);
        when(llmModelRepository.findByEnvAndProviderIdAndPurposeAndModelName(eq("default"), eq("p1"), eq("IMAGE_CHAT"), eq("m1")))
                .thenReturn(Optional.of(enabled));

        AiPostComposeAssistantService s = newService(
                mock(PostComposeAiSnapshotsRepository.class),
                mock(UsersRepository.class),
                llmGateway,
                llmModelRepository,
                mock(FileAssetsRepository.class),
                portalCfg("p1", null, 0.6, 0.9, 20, false, "base", "deep", "compose"),
                promptsRepo("base", "deep", "compose"),
                tempDir.toString(),
                "/uploads"
        );

        invokeInstance(
                s,
                "ensureVisionModelForRequest",
                new Class<?>[] { LlmQueueTaskType.class, String.class, String.class, boolean.class },
                new Object[] { LlmQueueTaskType.IMAGE_CHAT, "p1", null, true }
        );
    }

    @Test
    void ensureVisionModelForRequest_should_throw_when_default_model_missing_or_disabled() {
        LlmGateway llmGateway = mock(LlmGateway.class);

        AiPostComposeAssistantService s = newService(
                mock(PostComposeAiSnapshotsRepository.class),
                mock(UsersRepository.class),
                llmGateway,
                mock(LlmModelRepository.class),
                mock(FileAssetsRepository.class),
                portalCfg("p1", null, 0.6, 0.9, 20, false, "base", "deep", "compose"),
                promptsRepo("base", "deep", "compose"),
                tempDir.toString(),
                "/uploads"
        );

        when(llmGateway.resolve("p1")).thenReturn(null);
        RuntimeException ex1 = assertThrows(
                RuntimeException.class,
                () -> invokeInstance(
                        s,
                        "ensureVisionModelForRequest",
                        new Class<?>[] { LlmQueueTaskType.class, String.class, String.class, boolean.class },
                        new Object[] { LlmQueueTaskType.IMAGE_CHAT, "p1", null, true }
                )
        );
        Throwable t1 = unwrapInvoke(ex1);
        assertTrue(t1 instanceof IllegalArgumentException);
        assertTrue(t1.getMessage().contains("默认模型"));

        when(llmGateway.resolve("p1")).thenReturn(provider(null, "m1"));
        RuntimeException ex2 = assertThrows(
                RuntimeException.class,
                () -> invokeInstance(
                        s,
                        "ensureVisionModelForRequest",
                        new Class<?>[] { LlmQueueTaskType.class, String.class, String.class, boolean.class },
                        new Object[] { LlmQueueTaskType.IMAGE_CHAT, "p1", null, true }
                )
        );
        Throwable t2 = unwrapInvoke(ex2);
        assertTrue(t2 instanceof IllegalArgumentException);
        assertTrue(t2.getMessage().contains("默认模型"));

        when(llmGateway.resolve("p1")).thenReturn(provider("p1", null));
        RuntimeException ex3 = assertThrows(
                RuntimeException.class,
                () -> invokeInstance(
                        s,
                        "ensureVisionModelForRequest",
                        new Class<?>[] { LlmQueueTaskType.class, String.class, String.class, boolean.class },
                        new Object[] { LlmQueueTaskType.IMAGE_CHAT, "p1", null, true }
                )
        );
        Throwable t3 = unwrapInvoke(ex3);
        assertTrue(t3 instanceof IllegalArgumentException);
        assertTrue(t3.getMessage().contains("默认模型"));

        LlmModelRepository llmModelRepository2 = mock(LlmModelRepository.class);
        when(llmModelRepository2.findByEnvAndProviderIdAndPurposeAndModelName(eq("default"), eq("p1"), eq("IMAGE_CHAT"), eq("m1")))
                .thenReturn(Optional.empty());
        AiPostComposeAssistantService s2 = newService(
                mock(PostComposeAiSnapshotsRepository.class),
                mock(UsersRepository.class),
                llmGateway,
                llmModelRepository2,
                mock(FileAssetsRepository.class),
                portalCfg("p1", null, 0.6, 0.9, 20, false, "base", "deep", "compose"),
                promptsRepo("base", "deep", "compose"),
                tempDir.toString(),
                "/uploads"
        );
        when(llmGateway.resolve("p1")).thenReturn(provider("p1", "m1"));
        RuntimeException ex4 = assertThrows(
                RuntimeException.class,
                () -> invokeInstance(
                        s2,
                        "ensureVisionModelForRequest",
                        new Class<?>[] { LlmQueueTaskType.class, String.class, String.class, boolean.class },
                        new Object[] { LlmQueueTaskType.IMAGE_CHAT, "p1", null, true }
                )
        );
        Throwable t4 = unwrapInvoke(ex4);
        assertTrue(t4 instanceof IllegalArgumentException);
        assertTrue(t4.getMessage().contains("不支持图片"));
    }

    @Test
    void ensureVisionModelForRequest_should_rethrow_runtime_exceptions_from_provider_resolve() {
        LlmGateway llmGateway = mock(LlmGateway.class);
        when(llmGateway.resolve("p1")).thenThrow(new IllegalStateException("boom"));

        AiPostComposeAssistantService s = newService(
                mock(PostComposeAiSnapshotsRepository.class),
                mock(UsersRepository.class),
                llmGateway,
                mock(LlmModelRepository.class),
                mock(FileAssetsRepository.class),
                portalCfg("p1", null, 0.6, 0.9, 20, false, "base", "deep", "compose"),
                promptsRepo("base", "deep", "compose"),
                tempDir.toString(),
                "/uploads"
        );

        RuntimeException ex = assertThrows(
                RuntimeException.class,
                () -> invokeInstance(
                        s,
                        "ensureVisionModelForRequest",
                        new Class<?>[] { LlmQueueTaskType.class, String.class, String.class, boolean.class },
                        new Object[] { LlmQueueTaskType.IMAGE_CHAT, "p1", null, true }
                )
        );
        Throwable t = unwrapInvoke(ex);
        assertTrue(t instanceof IllegalStateException);
        assertTrue(t.getMessage().contains("boom"));
    }

    @Test
    void ensureVisionModelForRequest_should_wrap_checked_exceptions_from_provider_resolve() {
        LlmGateway llmGateway = mock(LlmGateway.class);
        doAnswer(invocation -> {
            sneakyThrow(new IOException("io"));
            return null;
        }).when(llmGateway).resolve("p1");

        AiPostComposeAssistantService s = newService(
                mock(PostComposeAiSnapshotsRepository.class),
                mock(UsersRepository.class),
                llmGateway,
                mock(LlmModelRepository.class),
                mock(FileAssetsRepository.class),
                portalCfg("p1", null, 0.6, 0.9, 20, false, "base", "deep", "compose"),
                promptsRepo("base", "deep", "compose"),
                tempDir.toString(),
                "/uploads"
        );

        RuntimeException ex = assertThrows(
                RuntimeException.class,
                () -> invokeInstance(
                        s,
                        "ensureVisionModelForRequest",
                        new Class<?>[] { LlmQueueTaskType.class, String.class, String.class, boolean.class },
                        new Object[] { LlmQueueTaskType.IMAGE_CHAT, "p1", null, true }
                )
        );
        Throwable t = unwrapInvoke(ex);
        assertTrue(t instanceof IllegalArgumentException);
        assertTrue(t.getMessage().contains("模型提供商解析失败"));
    }

    @Test
    void ensureVisionModelForRequest_should_pass_when_image_pool_not_empty_and_no_provider_or_model() {
        LlmModelRepository llmModelRepository = mock(LlmModelRepository.class);
        when(llmModelRepository.findByEnvAndPurposeAndEnabledTrueOrderBySortIndexAscPriorityDescWeightDescIsDefaultDescIdAsc(eq("default"), eq("IMAGE_CHAT")))
                .thenReturn(List.of(new LlmModelEntity()));

        AiPostComposeAssistantService s = newService(
                mock(PostComposeAiSnapshotsRepository.class),
                mock(UsersRepository.class),
                mock(LlmGateway.class),
                llmModelRepository,
                mock(FileAssetsRepository.class),
                portalCfg(null, null, 0.6, 0.9, 20, false, "base", "deep", "compose"),
                promptsRepo("base", "deep", "compose"),
                tempDir.toString(),
                "/uploads"
        );

        invokeInstance(
                s,
                "ensureVisionModelForRequest",
                new Class<?>[] { LlmQueueTaskType.class, String.class, String.class, boolean.class },
                new Object[] { LlmQueueTaskType.IMAGE_CHAT, null, null, true }
        );
    }

    @Test
    void ensureVisionModelForRequest_should_throw_when_image_pool_empty_and_no_provider_or_model() {
        LlmModelRepository llmModelRepository = mock(LlmModelRepository.class);
        when(llmModelRepository.findByEnvAndPurposeAndEnabledTrueOrderBySortIndexAscPriorityDescWeightDescIsDefaultDescIdAsc(eq("default"), eq("IMAGE_CHAT")))
                .thenReturn(List.of());

        AiPostComposeAssistantService s = newService(
                mock(PostComposeAiSnapshotsRepository.class),
                mock(UsersRepository.class),
                mock(LlmGateway.class),
                llmModelRepository,
                mock(FileAssetsRepository.class),
                portalCfg(null, null, 0.6, 0.9, 20, false, "base", "deep", "compose"),
                promptsRepo("base", "deep", "compose"),
                tempDir.toString(),
                "/uploads"
        );

        RuntimeException ex = assertThrows(
                RuntimeException.class,
                () -> invokeInstance(
                        s,
                        "ensureVisionModelForRequest",
                        new Class<?>[] { LlmQueueTaskType.class, String.class, String.class, boolean.class },
                        new Object[] { LlmQueueTaskType.IMAGE_CHAT, null, null, true }
                )
        );
        Throwable t = unwrapInvoke(ex);
        assertTrue(t instanceof IllegalArgumentException);
        assertTrue(t.getMessage().contains("IMAGE_CHAT"));
    }

    @Test
    void isEnabledImageChatModel_should_return_false_when_disabled_or_missing_args() {
        LlmModelEntity disabled = new LlmModelEntity();
        disabled.setEnabled(false);
        LlmModelRepository llmModelRepository = mock(LlmModelRepository.class);
        when(llmModelRepository.findByEnvAndProviderIdAndPurposeAndModelName(eq("default"), eq("p1"), eq("IMAGE_CHAT"), eq("m1")))
                .thenReturn(Optional.of(disabled));

        AiPostComposeAssistantService s = newService(
                mock(PostComposeAiSnapshotsRepository.class),
                mock(UsersRepository.class),
                mock(LlmGateway.class),
                llmModelRepository,
                mock(FileAssetsRepository.class),
                portalCfg("p1", "m1", 0.6, 0.9, 20, false, "base", "deep", "compose"),
                promptsRepo("base", "deep", "compose"),
                tempDir.toString(),
                "/uploads"
        );

        assertFalse((Boolean) invokeInstance(
                s,
                "isEnabledImageChatModel",
                new Class<?>[] { String.class, String.class },
                new Object[] { "p1", "m1" }
        ));
        assertFalse((Boolean) invokeInstance(
                s,
                "isEnabledImageChatModel",
                new Class<?>[] { String.class, String.class },
                new Object[] { " ", "m1" }
        ));
        assertFalse((Boolean) invokeInstance(
                s,
                "isEnabledImageChatModel",
                new Class<?>[] { String.class, String.class },
                new Object[] { "p1", " " }
        ));
    }

    @Test
    void isEnabledImageChatModel_should_return_true_when_model_enabled() {
        LlmModelEntity enabled = new LlmModelEntity();
        enabled.setEnabled(true);
        LlmModelRepository llmModelRepository = mock(LlmModelRepository.class);
        when(llmModelRepository.findByEnvAndProviderIdAndPurposeAndModelName(eq("default"), eq("p1"), eq("IMAGE_CHAT"), eq("m1")))
                .thenReturn(Optional.of(enabled));

        AiPostComposeAssistantService s = newService(
                mock(PostComposeAiSnapshotsRepository.class),
                mock(UsersRepository.class),
                mock(LlmGateway.class),
                llmModelRepository,
                mock(FileAssetsRepository.class),
                portalCfg("p1", "m1", 0.6, 0.9, 20, false, "base", "deep", "compose"),
                promptsRepo("base", "deep", "compose"),
                tempDir.toString(),
                "/uploads"
        );

        assertTrue((Boolean) invokeInstance(
                s,
                "isEnabledImageChatModel",
                new Class<?>[] { String.class, String.class },
                new Object[] { "p1", "m1" }
        ));
    }

    @Test
    void resolvePromptText_and_loadUserDefaultSystemPrompt_should_handle_edge_cases() {
        PromptsRepository promptsRepository = promptsRepo("p1");
        UsersRepository usersRepository = mock(UsersRepository.class);
        UsersEntity u1 = new UsersEntity();
        u1.setId(1L);
        u1.setMetadata(null);
        UsersEntity u2 = new UsersEntity();
        u2.setId(2L);
        u2.setMetadata(Map.of("preferences", "bad"));
        UsersEntity u3 = new UsersEntity();
        u3.setId(3L);
        u3.setMetadata(Map.of("preferences", Map.of("assistant", "bad")));
        UsersEntity u4 = new UsersEntity();
        u4.setId(4L);
        u4.setMetadata(Map.of("preferences", Map.of("assistant", Map.of("defaultSystemPrompt", "  "))));
        UsersEntity u5 = new UsersEntity();
        u5.setId(5L);
        u5.setMetadata(Map.of("preferences", Map.of("assistant", Map.of("defaultSystemPrompt", List.of("x")))));

        when(usersRepository.findById(1L)).thenReturn(Optional.of(u1));
        when(usersRepository.findById(2L)).thenReturn(Optional.of(u2));
        when(usersRepository.findById(3L)).thenReturn(Optional.of(u3));
        when(usersRepository.findById(4L)).thenReturn(Optional.of(u4));
        when(usersRepository.findById(5L)).thenReturn(Optional.of(u5));
        when(usersRepository.findById(6L)).thenReturn(Optional.empty());

        AiPostComposeAssistantService s = newService(
                mock(PostComposeAiSnapshotsRepository.class),
                usersRepository,
                mock(LlmGateway.class),
                mock(LlmModelRepository.class),
                mock(FileAssetsRepository.class),
                portalCfg("p1", "m1", 0.6, 0.9, 20, false, "base", "deep", "compose"),
                promptsRepository,
                tempDir.toString(),
                "/uploads"
        );

        assertEquals("", invokeInstance(s, "resolvePromptText", new Class<?>[] { String.class }, new Object[] { " " }));
        assertEquals("", invokeInstance(s, "resolvePromptText", new Class<?>[] { String.class }, new Object[] { "missing" }));
        assertEquals("prompt:p1", invokeInstance(s, "resolvePromptText", new Class<?>[] { String.class }, new Object[] { "p1" }));

        assertNull(invokeInstance(s, "loadUserDefaultSystemPrompt", new Class<?>[] { Long.class }, new Object[] { 1L }));
        assertNull(invokeInstance(s, "loadUserDefaultSystemPrompt", new Class<?>[] { Long.class }, new Object[] { 2L }));
        assertNull(invokeInstance(s, "loadUserDefaultSystemPrompt", new Class<?>[] { Long.class }, new Object[] { 3L }));
        assertNull(invokeInstance(s, "loadUserDefaultSystemPrompt", new Class<?>[] { Long.class }, new Object[] { 4L }));
        assertEquals("[x]", invokeInstance(s, "loadUserDefaultSystemPrompt", new Class<?>[] { Long.class }, new Object[] { 5L }));
        assertNull(invokeInstance(s, "loadUserDefaultSystemPrompt", new Class<?>[] { Long.class }, new Object[] { 6L }));
    }

    @Test
    void resolveImages_should_handle_invalid_metadata_shapes_and_image_extensions() throws Exception {
        PostComposeAiSnapshotsEntity snap = snapPending(1L, 10L);
        snap.setBeforeMetadata(Map.of(
                "attachments", List.of(
                        "bad",
                        Map.of("fileUrl", "  ", "mimeType", "image/png"),
                        Map.of("url", "https://ex/a.bmp", "mimeType", "text/plain", "fileAssetId", "x"),
                        Map.of("fileUrl", "https://ex/b.svg", "id", 123)
                )
        ));

        AiPostComposeStreamRequest r = req(1L);
        r.setImages(Arrays.asList(
                img(null, "https://ex/a.bmp", "text/plain"),
                img(null, "https://ex/c.png", null),
                null,
                img(null, "  ", "image/png")
        ));

        Method m = AiPostComposeAssistantService.class.getDeclaredMethod(
                "resolveImages",
                AiPostComposeStreamRequest.class,
                PostComposeAiSnapshotsEntity.class
        );
        m.setAccessible(true);
        @SuppressWarnings("unchecked")
        List<AiPostComposeStreamRequest.ImageInput> out = (List<AiPostComposeStreamRequest.ImageInput>) m.invoke(null, r, snap);

        assertNotNull(out);
        assertTrue(out.stream().anyMatch(x -> "https://ex/a.bmp".equals(x.getUrl())));
        assertTrue(out.stream().anyMatch(x -> "https://ex/b.svg".equals(x.getUrl())));
        assertTrue(out.stream().anyMatch(x -> "https://ex/c.png".equals(x.getUrl())));
    }

    @Test
    void encodeImageUrlForUpstream_should_default_mimeType_and_handle_zero_bytes_and_read_errors() throws Exception {
        writeTempFile("oct.bin", "abc".getBytes(StandardCharsets.UTF_8));
        writeTempFile("empty.bin", new byte[0]);

        AiPostComposeAssistantService s1 = newService(
                mock(PostComposeAiSnapshotsRepository.class),
                mock(UsersRepository.class),
                mock(LlmGateway.class),
                mock(LlmModelRepository.class),
                mock(FileAssetsRepository.class),
                portalCfg("p", "m", 0.6, 0.9, 20, false, "base", "deep", "compose"),
                promptsRepo("base", "deep", "compose"),
                tempDir.toString(),
                "/uploads"
        );

        String url1 = encodeImageUrlForUpstream(s1, img(null, "/uploads/oct.bin", " "));
        assertTrue(url1.startsWith("data:application/octet-stream;base64,"));
        assertTrue(url1.endsWith("YWJj"));

        assertEquals("/uploads/empty.bin", encodeImageUrlForUpstream(s1, img(null, "/uploads/empty.bin", "image/png")));

        AiPostComposeAssistantService s2 = newService(
                mock(PostComposeAiSnapshotsRepository.class),
                mock(UsersRepository.class),
                mock(LlmGateway.class),
                mock(LlmModelRepository.class),
                mock(FileAssetsRepository.class),
                portalCfg("p", "m", 0.6, 0.9, 20, false, "base", "deep", "compose"),
                promptsRepo("base", "deep", "compose"),
                "up\u0000loads",
                "/uploads"
        );
        assertEquals("/uploads/oct.bin", encodeImageUrlForUpstream(s2, img(null, "/uploads/oct.bin", "image/png")));
    }

    @Test
    void streamComposeEdit_should_fallback_to_default_error_message_when_vision_check_exception_has_no_message() throws Exception {
        long userId = 10L;
        PostComposeAiSnapshotsEntity snap = snapPending(1L, userId);

        PostComposeAiSnapshotsRepository snapshotsRepository = mock(PostComposeAiSnapshotsRepository.class);
        when(snapshotsRepository.findByIdAndUserId(eq(1L), eq(userId))).thenReturn(Optional.of(snap));

        LlmModelRepository llmModelRepository = mock(LlmModelRepository.class);
        when(llmModelRepository.findByEnvAndProviderIdAndPurposeAndModelName(eq("default"), eq("p1"), eq("IMAGE_CHAT"), eq("m1")))
                .thenThrow(new IllegalArgumentException());

        AiPostComposeAssistantService s = newService(
                snapshotsRepository,
                usersRepoWithDefaultPrompt(userId, null),
                mock(LlmGateway.class),
                llmModelRepository,
                mock(FileAssetsRepository.class),
                portalCfg("p1", "m1", 0.6, 0.9, 20, false, "base", "deep", "compose"),
                promptsRepo("base", "deep", "compose"),
                tempDir.toString(),
                "/uploads"
        );

        AiPostComposeStreamRequest r = req(1L);
        r.setProviderId("p1");
        r.setModel("m1");
        r.setImages(List.of(img(1L, "https://ex/a.png", null)));

        MockHttpServletResponse resp = newSseResponse();
        s.streamComposeEdit(r, userId, resp);

        String body = resp.getContentAsString(StandardCharsets.UTF_8);
        assertTrue(body.contains("event: error\n"));
        assertTrue(body.contains("请求失败"));
        assertTrue(body.contains("event: done\n"));
    }

    @Test
    void encodeImageUrlForUpstream_should_return_null_when_image_or_url_is_blank() {
        AiPostComposeAssistantService s = newService(
                mock(PostComposeAiSnapshotsRepository.class),
                mock(UsersRepository.class),
                mock(LlmGateway.class),
                mock(LlmModelRepository.class),
                mock(FileAssetsRepository.class),
                portalCfg("p", "m", 0.6, 0.9, 20, false, "base", "deep", "compose"),
                promptsRepo("base", "deep", "compose"),
                tempDir.toString(),
                "/uploads"
        );

        assertNull(encodeImageUrlForUpstream(s, null));
        assertNull(encodeImageUrlForUpstream(s, img(null, "  ", "image/png")));
    }

    @Test
    void resolveImages_should_return_empty_list_when_request_and_snapshot_are_null() throws Exception {
        Method m = AiPostComposeAssistantService.class.getDeclaredMethod(
                "resolveImages",
                AiPostComposeStreamRequest.class,
                PostComposeAiSnapshotsEntity.class
        );
        m.setAccessible(true);
        @SuppressWarnings("unchecked")
        List<AiPostComposeStreamRequest.ImageInput> out = (List<AiPostComposeStreamRequest.ImageInput>) m.invoke(null, null, null);

        assertNotNull(out);
        assertTrue(out.isEmpty());
    }

    @Test
    void loadUserDefaultSystemPrompt_should_return_null_when_default_prompt_key_missing() {
        UsersRepository usersRepository = mock(UsersRepository.class);
        UsersEntity u = new UsersEntity();
        u.setId(9L);
        u.setMetadata(Map.of("preferences", Map.of("assistant", Map.of())));
        when(usersRepository.findById(9L)).thenReturn(Optional.of(u));

        AiPostComposeAssistantService s = newService(
                mock(PostComposeAiSnapshotsRepository.class),
                usersRepository,
                mock(LlmGateway.class),
                mock(LlmModelRepository.class),
                mock(FileAssetsRepository.class),
                portalCfg("p", "m", 0.6, 0.9, 20, false, "base", "deep", "compose"),
                promptsRepo("base", "deep", "compose"),
                tempDir.toString(),
                "/uploads"
        );

        assertNull(invokeInstance(s, "loadUserDefaultSystemPrompt", new Class<?>[] { Long.class }, new Object[] { 9L }));
    }

    @Test
    void streamComposeEdit_should_clamp_history_limit_and_use_snapshot_provider_model_params() throws Exception {
        long userId = 10L;
        PostComposeAiSnapshotsEntity snap = snapPending(1L, userId);
        snap.setProviderId("snap-provider");
        snap.setModel("snap-model");
        snap.setTemperature(0.77);
        snap.setTopP(0.66);

        PostComposeAiSnapshotsRepository snapshotsRepository = mock(PostComposeAiSnapshotsRepository.class);
        when(snapshotsRepository.findByIdAndUserId(eq(1L), eq(userId))).thenReturn(Optional.of(snap));

        LlmGateway llmGateway = mock(LlmGateway.class);
        final String[] capturedProvider = new String[] { null };
        final String[] capturedModel = new String[] { null };
        final Double[] capturedTemperature = new Double[] { null };
        final Double[] capturedTopP = new Double[] { null };
        final List<ChatMessage>[] capturedMessages = new List[] { null };
        doAnswer(invocation -> {
            capturedProvider[0] = invocation.getArgument(1);
            capturedModel[0] = invocation.getArgument(2);
            capturedMessages[0] = invocation.getArgument(3);
            capturedTemperature[0] = invocation.getArgument(4);
            capturedTopP[0] = invocation.getArgument(5);
            return new LlmGateway.RoutedChatStreamResult("p", "m", null);
        }).when(llmGateway).chatStreamRouted(
                any(LlmQueueTaskType.class),
                anyString(),
                anyString(),
                any(List.class),
                any(),
                any(),
                any(),
                any(),
                any(OpenAiCompatClient.SseLineConsumer.class)
        );

        AiPostComposeAssistantService s = newService(
                snapshotsRepository,
                usersRepoWithDefaultPrompt(userId, null),
                llmGateway,
                mock(LlmModelRepository.class),
                mock(FileAssetsRepository.class),
                portalCfg("cfg-provider", "cfg-model", 0.22, 0.33, 500, false, "base", "deep", "compose"),
                promptsRepo("base", "deep", "compose"),
                tempDir.toString(),
                "/uploads"
        );

        AiPostComposeStreamRequest r = req(1L);
        r.setProviderId(" ");
        r.setModel(" ");
        r.setTemperature(null);
        r.setTopP(null);
        List<AiPostComposeStreamRequest.ChatHistoryMessage> history = new ArrayList<>();
        for (int i = 0; i < 205; i++) {
            AiPostComposeStreamRequest.ChatHistoryMessage m = new AiPostComposeStreamRequest.ChatHistoryMessage();
            m.setRole(i % 2 == 0 ? "assistant" : null);
            m.setContent(i == 204 ? "u-last" : "h-" + i);
            history.add(m);
        }
        history.set(203, null);
        history.get(202).setContent(" ");
        r.setChatHistory(history);

        s.streamComposeEdit(r, userId, newSseResponse());

        assertEquals("snap-provider", capturedProvider[0]);
        assertEquals("snap-model", capturedModel[0]);
        assertEquals(0.77, capturedTemperature[0]);
        assertEquals(0.66, capturedTopP[0]);
        assertNotNull(capturedMessages[0]);
        assertTrue(capturedMessages[0].stream().anyMatch(x -> "assistant".equals(x.role()) && "h-6".equals(x.content())));
        assertTrue(capturedMessages[0].stream().anyMatch(x -> "assistant".equals(x.role()) && "u-last".equals(x.content())));
    }

    @Test
    void streamComposeEdit_should_use_req_content_when_history_limit_null_and_ignore_non_delta_chunks() throws Exception {
        long userId = 10L;
        PostComposeAiSnapshotsEntity snap = snapPending(1L, userId);
        snap.setBeforeContent("snap-content");

        PostComposeAiSnapshotsRepository snapshotsRepository = mock(PostComposeAiSnapshotsRepository.class);
        when(snapshotsRepository.findByIdAndUserId(eq(1L), eq(userId))).thenReturn(Optional.of(snap));

        LlmGateway llmGateway = mock(LlmGateway.class);
        final List<ChatMessage>[] capturedMessages = new List[] { null };
        doAnswer(invocation -> {
            capturedMessages[0] = invocation.getArgument(3);
            OpenAiCompatClient.SseLineConsumer consumer = invocation.getArgument(8);
            consumer.onLine(null);
            consumer.onLine("   ");
            consumer.onLine("event: ping");
            consumer.onLine("data: {}");
            consumer.onLine("data: [DONE]");
            return new LlmGateway.RoutedChatStreamResult("p", "m", null);
        }).when(llmGateway).chatStreamRouted(
                any(LlmQueueTaskType.class),
                anyString(),
                anyString(),
                any(List.class),
                any(),
                any(),
                any(),
                any(),
                any(OpenAiCompatClient.SseLineConsumer.class)
        );

        AiPostComposeAssistantService s = newService(
                snapshotsRepository,
                usersRepoWithDefaultPrompt(userId, null),
                llmGateway,
                mock(LlmModelRepository.class),
                mock(FileAssetsRepository.class),
                portalCfg("p", "m", 0.6, 0.9, null, false, "base", "deep", "compose"),
                promptsRepo("base", "deep", "compose"),
                tempDir.toString(),
                "/uploads"
        );

        AiPostComposeStreamRequest r = req(1L);
        r.setCurrentTitle("req-title");
        r.setCurrentContent("req-content");
        r.setInstruction("keep");

        MockHttpServletResponse resp = newSseResponse();
        s.streamComposeEdit(r, userId, resp);

        String body = resp.getContentAsString(StandardCharsets.UTF_8);
        assertTrue(body.contains("event: meta\n"));
        assertTrue(body.contains("event: done\n"));
        assertFalse(body.contains("event: delta\n"));
        assertNotNull(capturedMessages[0]);
        ChatMessage last = capturedMessages[0].get(capturedMessages[0].size() - 1);
        assertEquals("user", last.role());
        assertTrue(String.valueOf(last.content()).contains("req-content"));
    }

    @Test
    void encodeImageUrlForUpstream_should_return_original_url_when_url_prefix_blank_and_file_asset_path_blank() {
        com.example.EnterpriseRagCommunity.entity.monitor.FileAssetsEntity fa = new com.example.EnterpriseRagCommunity.entity.monitor.FileAssetsEntity();
        fa.setId(77L);
        fa.setPath("   ");

        FileAssetsRepository fileAssetsRepository = mock(FileAssetsRepository.class);
        when(fileAssetsRepository.findById(77L)).thenReturn(Optional.of(fa));

        AiPostComposeAssistantService s = newService(
                mock(PostComposeAiSnapshotsRepository.class),
                mock(UsersRepository.class),
                mock(LlmGateway.class),
                mock(LlmModelRepository.class),
                fileAssetsRepository,
                portalCfg("p", "m", 0.6, 0.9, 20, false, "base", "deep", "compose"),
                promptsRepo("base", "deep", "compose"),
                tempDir.toString(),
                " "
        );

        String out = encodeImageUrlForUpstream(s, img(77L, "/uploads/a.png", "image/png"));
        assertEquals("/uploads/a.png", out);
        verify(fileAssetsRepository).findById(77L);
    }

    @Test
    void buildUserMessage_and_jsonEscape_should_handle_title_fallback_and_null_input() {
        String out = (String) invokeStatic(
                "buildUserMessage",
                new Class<?>[] { String.class, String.class, String.class },
                new Object[] { "   ", null, "请改写" }
        );
        assertTrue(out.contains("标题：（无标题）"));
        assertTrue(out.contains("当前正文（Markdown）："));
        assertTrue(out.contains("用户要求：\n请改写"));
        assertEquals("", invokeStatic("jsonEscape", new Class<?>[] { String.class }, new Object[] { null }));
    }

    @Test
    void extractDeltaContent_should_return_null_when_choices_not_array_or_text_not_textual() {
        AiPostComposeAssistantService s = newService(
                mock(PostComposeAiSnapshotsRepository.class),
                mock(UsersRepository.class),
                mock(LlmGateway.class),
                mock(LlmModelRepository.class),
                mock(FileAssetsRepository.class),
                portalCfg("p", "m", 0.6, 0.9, 20, false, "base", "deep", "compose"),
                promptsRepo("base", "deep", "compose"),
                tempDir.toString(),
                "/uploads"
        );

        assertNull(invokeInstance(
                s,
                "extractDeltaContent",
                new Class<?>[] { String.class },
                new Object[] { "{\"choices\":{}}" }
        ));
        assertNull(invokeInstance(
                s,
                "extractDeltaContent",
                new Class<?>[] { String.class },
                new Object[] { "{\"choices\":[{\"delta\":{\"content\":123},\"text\":456}]}" }
        ));
    }
}
