package com.example.EnterpriseRagCommunity.service.ai;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.example.EnterpriseRagCommunity.dto.ai.AiChatStreamRequest;
import com.example.EnterpriseRagCommunity.dto.ai.ChatContextGovernanceConfigDTO;
import com.example.EnterpriseRagCommunity.entity.access.UsersEntity;
import com.example.EnterpriseRagCommunity.entity.ai.LlmModelEntity;
import com.example.EnterpriseRagCommunity.entity.monitor.FileAssetExtractionsEntity;
import com.example.EnterpriseRagCommunity.entity.monitor.FileAssetsEntity;
import com.example.EnterpriseRagCommunity.entity.monitor.enums.FileAssetExtractionStatus;
import com.example.EnterpriseRagCommunity.repository.access.UsersRepository;
import com.example.EnterpriseRagCommunity.repository.ai.LlmModelRepository;
import com.example.EnterpriseRagCommunity.repository.content.PostsRepository;
import com.example.EnterpriseRagCommunity.repository.monitor.FileAssetExtractionsRepository;
import com.example.EnterpriseRagCommunity.repository.monitor.FileAssetsRepository;
import com.example.EnterpriseRagCommunity.repository.rag.QaMessageSourcesRepository;
import com.example.EnterpriseRagCommunity.repository.rag.QaMessagesRepository;
import com.example.EnterpriseRagCommunity.repository.rag.QaSessionsRepository;
import com.example.EnterpriseRagCommunity.repository.rag.QaTurnsRepository;
import com.example.EnterpriseRagCommunity.repository.semantic.ContextWindowsRepository;
import com.example.EnterpriseRagCommunity.repository.semantic.PromptsRepository;
import com.example.EnterpriseRagCommunity.repository.semantic.RetrievalEventsRepository;
import com.example.EnterpriseRagCommunity.repository.semantic.RetrievalHitsRepository;
import com.example.EnterpriseRagCommunity.service.retrieval.HybridRagRetrievalService;
import com.example.EnterpriseRagCommunity.service.retrieval.RagChatPostCommentAggregationService;
import com.example.EnterpriseRagCommunity.service.retrieval.RagCommentChatRetrievalService;
import com.example.EnterpriseRagCommunity.service.retrieval.RagPostChatRetrievalService;
import com.example.EnterpriseRagCommunity.service.retrieval.admin.ChatRagAugmentConfigService;
import com.example.EnterpriseRagCommunity.service.retrieval.admin.CitationConfigService;
import com.example.EnterpriseRagCommunity.service.retrieval.admin.ContextClipConfigService;
import com.example.EnterpriseRagCommunity.service.retrieval.admin.HybridRetrievalConfigService;

class AiChatServiceFilesAndVisionTest {
    @TempDir
    Path tempDir;

    @Test
    void buildFilesBlockForModel_should_return_null_when_owner_mismatch() throws Exception {
        FileAssetsRepository fileAssetsRepository = mock(FileAssetsRepository.class);
        FileAssetExtractionsRepository fileAssetExtractionsRepository = mock(FileAssetExtractionsRepository.class);

        FileAssetsEntity asset = new FileAssetsEntity();
        asset.setId(10L);
        UsersEntity other = new UsersEntity();
        other.setId(2L);
        asset.setOwner(other);
        asset.setOriginalName("a.txt");
        asset.setMimeType("text/plain");
        asset.setUrl("https://x/a.txt");
        when(fileAssetsRepository.findAllById(any())).thenReturn(List.of(asset));

        AiChatService service = buildService(mock(LlmGateway.class), mock(LlmModelRepository.class), fileAssetsRepository, fileAssetExtractionsRepository);

        AiChatStreamRequest.FileInput fi = new AiChatStreamRequest.FileInput();
        fi.setFileAssetId(10L);
        ChatContextGovernanceConfigDTO cfg = new ChatContextGovernanceConfigDTO();
        cfg.setMaxFiles(10);
        String out = invokeBuildFilesBlock(service, List.of(fi), 1L, cfg);
        assertNull(out);
    }

    @Test
    void buildFilesBlockForModel_should_include_error_message_when_extraction_failed() throws Exception {
        FileAssetsRepository fileAssetsRepository = mock(FileAssetsRepository.class);
        FileAssetExtractionsRepository fileAssetExtractionsRepository = mock(FileAssetExtractionsRepository.class);

        FileAssetsEntity asset = new FileAssetsEntity();
        asset.setId(10L);
        UsersEntity owner = new UsersEntity();
        owner.setId(1L);
        asset.setOwner(owner);
        asset.setOriginalName("a.txt");
        asset.setMimeType("text/plain");
        asset.setUrl("https://x/a.txt");
        when(fileAssetsRepository.findAllById(any())).thenReturn(List.of(asset));

        FileAssetExtractionsEntity ex = new FileAssetExtractionsEntity();
        ex.setFileAssetId(10L);
        ex.setExtractStatus(FileAssetExtractionStatus.FAILED);
        ex.setErrorMessage("e".repeat(400));
        when(fileAssetExtractionsRepository.findAllById(any())).thenReturn(List.of(ex));

        AiChatService service = buildService(mock(LlmGateway.class), mock(LlmModelRepository.class), fileAssetsRepository, fileAssetExtractionsRepository);

        AiChatStreamRequest.FileInput fi = new AiChatStreamRequest.FileInput();
        fi.setFileAssetId(10L);
        ChatContextGovernanceConfigDTO cfg = new ChatContextGovernanceConfigDTO();
        cfg.setMaxFiles(10);
        String out = invokeBuildFilesBlock(service, List.of(fi), 1L, cfg);
        assertNotNull(out);
        assertEquals(true, out.contains("解析失败"));
        assertEquals(true, out.contains("file_asset_id=10"));
    }

    @Test
    void buildFilesBlockForModel_should_truncate_per_file_and_total_budget() throws Exception {
        FileAssetsRepository fileAssetsRepository = mock(FileAssetsRepository.class);
        FileAssetExtractionsRepository fileAssetExtractionsRepository = mock(FileAssetExtractionsRepository.class);

        FileAssetsEntity asset = new FileAssetsEntity();
        asset.setId(10L);
        UsersEntity owner = new UsersEntity();
        owner.setId(1L);
        asset.setOwner(owner);
        asset.setOriginalName("a.txt");
        asset.setMimeType("text/plain");
        asset.setUrl("https://x/a.txt");
        when(fileAssetsRepository.findAllById(any())).thenReturn(List.of(asset));

        FileAssetExtractionsEntity ex = new FileAssetExtractionsEntity();
        ex.setFileAssetId(10L);
        ex.setExtractStatus(FileAssetExtractionStatus.READY);
        ex.setExtractedText("x".repeat(200));
        when(fileAssetExtractionsRepository.findAllById(any())).thenReturn(List.of(ex));

        AiChatService service = buildService(mock(LlmGateway.class), mock(LlmModelRepository.class), fileAssetsRepository, fileAssetExtractionsRepository);

        AiChatStreamRequest.FileInput fi = new AiChatStreamRequest.FileInput();
        fi.setFileAssetId(10L);
        ChatContextGovernanceConfigDTO cfg = new ChatContextGovernanceConfigDTO();
        cfg.setMaxFiles(10);
        cfg.setPerFileMaxChars(50);
        cfg.setTotalFilesMaxChars(80);
        String out = invokeBuildFilesBlock(service, List.of(fi), 1L, cfg);
        assertNotNull(out);
        assertEquals(true, out.contains("[FILES]"));
        assertEquals(true, out.contains("file_asset_id=10"));
        assertEquals(true, out.length() <= 200);
    }

    @Test
    void buildFilesBlockForModel_should_return_null_when_max_files_zero_or_file_ids_missing() throws Exception {
        FileAssetsRepository fileAssetsRepository = mock(FileAssetsRepository.class);
        FileAssetExtractionsRepository fileAssetExtractionsRepository = mock(FileAssetExtractionsRepository.class);
        AiChatService service = buildService(mock(LlmGateway.class), mock(LlmModelRepository.class), fileAssetsRepository, fileAssetExtractionsRepository);

        AiChatStreamRequest.FileInput noId = new AiChatStreamRequest.FileInput();
        noId.setUrl("https://x/no-id.txt");

        ChatContextGovernanceConfigDTO zeroCfg = new ChatContextGovernanceConfigDTO();
        zeroCfg.setMaxFiles(0);
        assertNull(invokeBuildFilesBlock(service, List.of(noId), 1L, zeroCfg));

        ChatContextGovernanceConfigDTO normalCfg = new ChatContextGovernanceConfigDTO();
        normalCfg.setMaxFiles(10);
        assertNull(invokeBuildFilesBlock(service, List.of(noId), 1L, normalCfg));
    }

    @Test
    void ensureVisionModelForRequest_should_throw_when_pool_empty_and_auto_selected() throws Exception {
        LlmGateway llmGateway = mock(LlmGateway.class);
        LlmModelRepository llmModelRepository = mock(LlmModelRepository.class);
        when(llmModelRepository.findByEnvAndPurposeAndEnabledTrueOrderBySortIndexAscPriorityDescWeightDescIsDefaultDescIdAsc(eq("default"), eq("MULTIMODAL_CHAT")))
                .thenReturn(List.of());
        AiChatService service = buildService(llmGateway, llmModelRepository, mock(FileAssetsRepository.class), mock(FileAssetExtractionsRepository.class));

        Method m = AiChatService.class.getDeclaredMethod("ensureMultimodalModelForRequest", LlmQueueTaskType.class, String.class, String.class);
        m.setAccessible(true);
        Exception ex = assertThrows(Exception.class, () -> m.invoke(service, LlmQueueTaskType.MULTIMODAL_CHAT, null, null));
        assertEquals(true, String.valueOf(ex.getCause().getMessage()).contains("MULTIMODAL_CHAT"));
    }

    @Test
    void ensureVisionModelForRequest_should_accept_enabled_model_override() throws Exception {
        LlmGateway llmGateway = mock(LlmGateway.class);
        when(llmGateway.resolve(null)).thenReturn(new AiProvidersConfigService.ResolvedProvider(
                "p1",
                "OPENAI_COMPAT",
                "http://127.0.0.1:1",
                null,
                "m1",
                "e1",
                Map.of(),
                Map.of(),
                1000,
                1000
        ));

        LlmModelRepository llmModelRepository = mock(LlmModelRepository.class);
        LlmModelEntity e = new LlmModelEntity();
        e.setEnabled(true);
        when(llmModelRepository.findByEnvAndProviderIdAndPurposeAndModelName(eq("default"), eq("p1"), eq("MULTIMODAL_CHAT"), eq("vision-1")))
                .thenReturn(Optional.of(e));

        AiChatService service = buildService(llmGateway, llmModelRepository, mock(FileAssetsRepository.class), mock(FileAssetExtractionsRepository.class));

        Method m = AiChatService.class.getDeclaredMethod("ensureMultimodalModelForRequest", LlmQueueTaskType.class, String.class, String.class);
        m.setAccessible(true);
        m.invoke(service, LlmQueueTaskType.MULTIMODAL_CHAT, null, "vision-1");
    }

    @Test
    void ensureVisionModelForRequest_should_cover_non_multimodal_and_provider_default_not_enabled() throws Exception {
        LlmGateway llmGateway = mock(LlmGateway.class);
        when(llmGateway.resolve("p1")).thenReturn(new AiProvidersConfigService.ResolvedProvider(
                "p1",
                "OPENAI_COMPAT",
                "http://127.0.0.1:1",
                null,
                "vision-default",
                "e1",
                Map.of(),
                Map.of(),
                1000,
                1000
        ));

        LlmModelRepository llmModelRepository = mock(LlmModelRepository.class);
        LlmModelEntity disabled = new LlmModelEntity();
        disabled.setEnabled(false);
        when(llmModelRepository.findByEnvAndProviderIdAndPurposeAndModelName(eq("default"), eq("p1"), eq("MULTIMODAL_CHAT"), eq("vision-default")))
                .thenReturn(Optional.of(disabled));

        AiChatService service = buildService(llmGateway, llmModelRepository, mock(FileAssetsRepository.class), mock(FileAssetExtractionsRepository.class));
        Method m = AiChatService.class.getDeclaredMethod("ensureMultimodalModelForRequest", LlmQueueTaskType.class, String.class, String.class);
        m.setAccessible(true);

        m.invoke(service, LlmQueueTaskType.CHAT, "p1", null);
        Exception ex = assertThrows(Exception.class, () -> m.invoke(service, LlmQueueTaskType.MULTIMODAL_CHAT, "p1", null));
        assertEquals(true, String.valueOf(ex.getCause().getMessage()).contains("默认模型未加入多模态聊天模型池"));
    }

    @Test
    void encodeImageUrlForUpstream_should_encode_local_upload_to_data_url() throws Exception {
        FileAssetsRepository fileAssetsRepository = mock(FileAssetsRepository.class);
        AiChatService service = buildService(mock(LlmGateway.class), mock(LlmModelRepository.class), fileAssetsRepository, mock(FileAssetExtractionsRepository.class));

        Path root = tempDir.resolve("uploads");
        Files.createDirectories(root);
        Path img = root.resolve("a.png");
        Files.write(img, new byte[] {1, 2, 3});

        setField(service, "uploadRoot", root.toString());
        setField(service, "urlPrefix", "/uploads");

        AiChatStreamRequest.ImageInput in = new AiChatStreamRequest.ImageInput();
        in.setUrl("/uploads/a.png?x=1");
        in.setMimeType("image/png");

        Method m = AiChatService.class.getDeclaredMethod("encodeImageUrlForUpstream", AiChatStreamRequest.ImageInput.class);
        m.setAccessible(true);
        String out = (String) m.invoke(service, in);
        assertEquals(true, out.startsWith("data:image/png;base64,"));
    }

    @Test
    void encodeImageUrlForUpstream_should_cover_http_data_large_file_and_default_mime_paths() throws Exception {
        FileAssetsRepository fileAssetsRepository = mock(FileAssetsRepository.class);
        AiChatService service = buildService(mock(LlmGateway.class), mock(LlmModelRepository.class), fileAssetsRepository, mock(FileAssetExtractionsRepository.class));

        Method m = AiChatService.class.getDeclaredMethod("encodeImageUrlForUpstream", AiChatStreamRequest.ImageInput.class);
        m.setAccessible(true);

        AiChatStreamRequest.ImageInput httpIn = new AiChatStreamRequest.ImageInput();
        httpIn.setUrl("https://x/a.png");
        assertEquals("https://x/a.png", m.invoke(service, httpIn));

        AiChatStreamRequest.ImageInput dataIn = new AiChatStreamRequest.ImageInput();
        dataIn.setUrl("data:image/png;base64,AAA");
        assertEquals("data:image/png;base64,AAA", m.invoke(service, dataIn));

        Path root = tempDir.resolve("uploads");
        Files.createDirectories(root);
        Path small = root.resolve("small.bin");
        Files.write(small, new byte[] {1, 2, 3, 4});
        setField(service, "uploadRoot", root.toString());
        setField(service, "urlPrefix", "/uploads");

        AiChatStreamRequest.ImageInput localNoMime = new AiChatStreamRequest.ImageInput();
        localNoMime.setUrl("/uploads/small.bin");
        localNoMime.setMimeType("   ");
        String encoded = (String) m.invoke(service, localNoMime);
        assertEquals(true, encoded.startsWith("data:application/octet-stream;base64,"));

        Path big = root.resolve("big.bin");
        Files.write(big, new byte[4_000_001]);
        AiChatStreamRequest.ImageInput largeIn = new AiChatStreamRequest.ImageInput();
        largeIn.setUrl("/uploads/big.bin");
        largeIn.setMimeType("image/png");
        assertEquals("/uploads/big.bin", m.invoke(service, largeIn));
    }

    @Test
    void encodeImageUrlForUpstream_should_use_file_asset_mime_when_request_mime_blank() throws Exception {
        FileAssetsRepository fileAssetsRepository = mock(FileAssetsRepository.class);
        AiChatService service = buildService(mock(LlmGateway.class), mock(LlmModelRepository.class), fileAssetsRepository, mock(FileAssetExtractionsRepository.class));

        Path root = tempDir.resolve("uploads");
        Files.createDirectories(root);
        Path small = root.resolve("asset.bin");
        Files.write(small, new byte[] {5, 6, 7});
        setField(service, "uploadRoot", root.toString());
        setField(service, "urlPrefix", "/uploads");

        FileAssetsEntity fa = new FileAssetsEntity();
        fa.setId(501L);
        fa.setMimeType("image/webp");
        when(fileAssetsRepository.findById(501L)).thenReturn(Optional.of(fa));

        AiChatStreamRequest.ImageInput in = new AiChatStreamRequest.ImageInput();
        in.setFileAssetId(501L);
        in.setMimeType(" ");
        in.setUrl("/uploads/asset.bin");

        Method m = AiChatService.class.getDeclaredMethod("encodeImageUrlForUpstream", AiChatStreamRequest.ImageInput.class);
        m.setAccessible(true);
        String encoded = (String) m.invoke(service, in);
        assertEquals(true, encoded.startsWith("data:image/webp;base64,"));
    }

    @Test
    void providerSupportsVision_should_cover_boolean_string_and_exception_paths() throws Exception {
        LlmGateway llmGateway = mock(LlmGateway.class);
        when(llmGateway.resolve("p_bool")).thenReturn(new AiProvidersConfigService.ResolvedProvider(
                "p_bool",
                "OPENAI_COMPAT",
                "http://127.0.0.1:1",
                null,
                "m1",
                "e1",
                Map.of("supportsVision", true),
                Map.of(),
                1000,
                1000
        ));
        when(llmGateway.resolve("p_str")).thenReturn(new AiProvidersConfigService.ResolvedProvider(
                "p_str",
                "OPENAI_COMPAT",
                "http://127.0.0.1:1",
                null,
                "m1",
                "e1",
                Map.of("supportsVision", "TRUE"),
                Map.of(),
                1000,
                1000
        ));
        when(llmGateway.resolve("p_none")).thenReturn(new AiProvidersConfigService.ResolvedProvider(
                "p_none",
                "OPENAI_COMPAT",
                "http://127.0.0.1:1",
                null,
                "m1",
                "e1",
                Map.of("other", 1),
                Map.of(),
                1000,
                1000
        ));
        when(llmGateway.resolve("p_err")).thenThrow(new RuntimeException("boom"));

        AiChatService service = buildService(llmGateway, mock(LlmModelRepository.class), mock(FileAssetsRepository.class), mock(FileAssetExtractionsRepository.class));
        Method m = AiChatService.class.getDeclaredMethod("providerSupportsVision", String.class);
        m.setAccessible(true);

        assertEquals(true, (Boolean) m.invoke(service, "p_bool"));
        assertEquals(true, (Boolean) m.invoke(service, "p_str"));
        assertEquals(false, (Boolean) m.invoke(service, "p_none"));
        assertEquals(false, (Boolean) m.invoke(service, "p_err"));
    }

    @Test
    void readLocalUploadBytes_should_read_prefixed_file_and_block_path_traversal() throws Exception {
        FileAssetsRepository fileAssetsRepository = mock(FileAssetsRepository.class);
        AiChatService service = buildService(mock(LlmGateway.class), mock(LlmModelRepository.class), fileAssetsRepository, mock(FileAssetExtractionsRepository.class));

        Path root = tempDir.resolve("uploads");
        Files.createDirectories(root);
        Path ok = root.resolve("ok.txt");
        Files.write(ok, "abc".getBytes());
        Path outside = tempDir.resolve("outside.txt");
        Files.write(outside, "secret".getBytes());

        setField(service, "uploadRoot", root.toString());
        setField(service, "urlPrefix", "/uploads");

        Method m = AiChatService.class.getDeclaredMethod("readLocalUploadBytes", Long.class, String.class);
        m.setAccessible(true);
        byte[] okBytes = (byte[]) m.invoke(service, null, "/uploads/ok.txt?x=1");
        byte[] blocked = (byte[]) m.invoke(service, null, "/uploads/../outside.txt");

        assertNotNull(okBytes);
        assertEquals(3, okBytes.length);
        assertNull(blocked);
    }

    @Test
    void readLocalUploadBytes_should_fallback_to_file_asset_path_when_url_not_local_prefix() throws Exception {
        FileAssetsRepository fileAssetsRepository = mock(FileAssetsRepository.class);
        AiChatService service = buildService(mock(LlmGateway.class), mock(LlmModelRepository.class), fileAssetsRepository, mock(FileAssetExtractionsRepository.class));

        Path p = tempDir.resolve("fa.txt");
        Files.write(p, "from-asset".getBytes());
        FileAssetsEntity fa = new FileAssetsEntity();
        fa.setId(10L);
        fa.setPath(p.toString());
        when(fileAssetsRepository.findById(10L)).thenReturn(Optional.of(fa));

        setField(service, "uploadRoot", tempDir.resolve("uploads").toString());
        setField(service, "urlPrefix", "/uploads");

        Method m = AiChatService.class.getDeclaredMethod("readLocalUploadBytes", Long.class, String.class);
        m.setAccessible(true);
        byte[] bytes = (byte[]) m.invoke(service, 10L, "/not-local-prefix/x.txt");
        assertNotNull(bytes);
        assertEquals(10, bytes.length);
    }

    @Test
    void readLocalUploadBytes_should_cover_missing_paths_and_repository_miss() throws Exception {
        FileAssetsRepository fileAssetsRepository = mock(FileAssetsRepository.class);
        AiChatService service = buildService(mock(LlmGateway.class), mock(LlmModelRepository.class), fileAssetsRepository, mock(FileAssetExtractionsRepository.class));

        setField(service, "uploadRoot", tempDir.resolve("uploads").toString());
        setField(service, "urlPrefix", "/uploads");

        Method m = AiChatService.class.getDeclaredMethod("readLocalUploadBytes", Long.class, String.class);
        m.setAccessible(true);

        assertNull(m.invoke(service, null, "/uploads/not-exists.txt"));
        when(fileAssetsRepository.findById(99L)).thenReturn(Optional.empty());
        assertNull(m.invoke(service, 99L, "/not-local-prefix/miss.txt"));

        FileAssetsEntity blankPath = new FileAssetsEntity();
        blankPath.setId(100L);
        blankPath.setPath("   ");
        when(fileAssetsRepository.findById(100L)).thenReturn(Optional.of(blankPath));
        assertNull(m.invoke(service, 100L, "/not-local-prefix/miss.txt"));
    }

    @Test
    void helper_methods_should_cover_file_resolution_and_image_url_heuristics() throws Exception {
        AiChatStreamRequest req = new AiChatStreamRequest();
        java.util.ArrayList<AiChatStreamRequest.FileInput> files = new java.util.ArrayList<>();
        for (int i = 0; i < 25; i++) {
            AiChatStreamRequest.FileInput f = new AiChatStreamRequest.FileInput();
            if (i % 3 == 0) {
                f.setFileAssetId(100L + i);
            } else if (i % 3 == 1) {
                f.setUrl("https://x/" + (i % 2) + ".txt");
            }
            files.add(f);
        }
        files.add(null);
        req.setFiles(files);

        Method resolveFiles = AiChatService.class.getDeclaredMethod("resolveFiles", AiChatStreamRequest.class);
        resolveFiles.setAccessible(true);
        @SuppressWarnings("unchecked")
        List<AiChatStreamRequest.FileInput> out = (List<AiChatStreamRequest.FileInput>) resolveFiles.invoke(null, req);
        assertEquals(true, out.size() > 0);
        assertEquals(true, out.size() <= 20);

        Method likely = AiChatService.class.getDeclaredMethod("isLikelyImageUrl", String.class);
        likely.setAccessible(true);
        assertEquals(false, (Boolean) likely.invoke(null, (String) null));
        assertEquals(true, (Boolean) likely.invoke(null, "/uploads/a.bin"));
        assertEquals(true, (Boolean) likely.invoke(null, "https://x/a.JPEG"));
        assertEquals(false, (Boolean) likely.invoke(null, "https://x/a.txt"));
    }

    @Test
    void loadUserDefaultSystemPrompt_should_cover_nested_metadata_shapes() throws Exception {
        UsersRepository usersRepository = mock(UsersRepository.class);
        FileAssetsRepository fileAssetsRepository = mock(FileAssetsRepository.class);
        AiChatService service = buildService(mock(LlmGateway.class), mock(LlmModelRepository.class), fileAssetsRepository, mock(FileAssetExtractionsRepository.class));

        Field ur = AiChatService.class.getDeclaredField("usersRepository");
        ur.setAccessible(true);
        ur.set(service, usersRepository);

        Method m = AiChatService.class.getDeclaredMethod("loadUserDefaultSystemPrompt", Long.class);
        m.setAccessible(true);

        assertNull(m.invoke(service, new Object[] {null}));

        when(usersRepository.findById(1L)).thenReturn(Optional.empty());
        assertNull(m.invoke(service, 1L));

        UsersEntity uNoMeta = new UsersEntity();
        when(usersRepository.findById(2L)).thenReturn(Optional.of(uNoMeta));
        assertNull(m.invoke(service, 2L));

        UsersEntity uBadPrefs = new UsersEntity();
        uBadPrefs.setMetadata(Map.of("preferences", "x"));
        when(usersRepository.findById(3L)).thenReturn(Optional.of(uBadPrefs));
        assertNull(m.invoke(service, 3L));

        UsersEntity uBadAssistant = new UsersEntity();
        uBadAssistant.setMetadata(Map.of("preferences", Map.of("assistant", "x")));
        when(usersRepository.findById(4L)).thenReturn(Optional.of(uBadAssistant));
        assertNull(m.invoke(service, 4L));

        UsersEntity uBlankPrompt = new UsersEntity();
        uBlankPrompt.setMetadata(Map.of("preferences", Map.of("assistant", Map.of("defaultSystemPrompt", "   "))));
        when(usersRepository.findById(5L)).thenReturn(Optional.of(uBlankPrompt));
        assertNull(m.invoke(service, 5L));

        UsersEntity uOk = new UsersEntity();
        uOk.setMetadata(Map.of("preferences", Map.of("assistant", Map.of("defaultSystemPrompt", "  SYS  "))));
        when(usersRepository.findById(6L)).thenReturn(Optional.of(uOk));
        assertEquals("SYS", m.invoke(service, 6L));
    }

    @Test
    void ensureVisionModelForRequest_should_cover_provider_resolution_failure_paths() throws Exception {
        LlmGateway llmGateway = mock(LlmGateway.class);
        when(llmGateway.resolve(null)).thenThrow(new RuntimeException("boom"));
        when(llmGateway.resolve("p_bad")).thenReturn(new AiProvidersConfigService.ResolvedProvider(
                "p_bad",
                "OPENAI_COMPAT",
                "http://127.0.0.1:1",
                null,
                null,
                "e1",
                Map.of(),
                Map.of(),
                1000,
                1000
        ));

        LlmModelRepository llmModelRepository = mock(LlmModelRepository.class);
        AiChatService service = buildService(llmGateway, llmModelRepository, mock(FileAssetsRepository.class), mock(FileAssetExtractionsRepository.class));

        Method m = AiChatService.class.getDeclaredMethod("ensureMultimodalModelForRequest", LlmQueueTaskType.class, String.class, String.class);
        m.setAccessible(true);

        Exception ex1 = assertThrows(Exception.class, () -> m.invoke(service, LlmQueueTaskType.MULTIMODAL_CHAT, null, "vision-x"));
        assertEquals(true, String.valueOf(ex1.getCause().getMessage()).contains("providerId"));

        Exception ex2 = assertThrows(Exception.class, () -> m.invoke(service, LlmQueueTaskType.MULTIMODAL_CHAT, "p_bad", null));
        assertEquals(true, String.valueOf(ex2.getCause().getMessage()).contains("默认模型"));
    }

    private static void setField(Object target, String name, String value) throws Exception {
        Field f = AiChatService.class.getDeclaredField(name);
        f.setAccessible(true);
        f.set(target, value);
    }

    private static String invokeBuildFilesBlock(AiChatService service, List<AiChatStreamRequest.FileInput> files, Long userId, ChatContextGovernanceConfigDTO cfg) throws Exception {
        Method m = AiChatService.class.getDeclaredMethod("buildFilesBlockForModel", List.class, Long.class, ChatContextGovernanceConfigDTO.class);
        m.setAccessible(true);
        return (String) m.invoke(service, files, userId, cfg);
    }

    private static AiChatService buildService(
            LlmGateway llmGateway,
            LlmModelRepository llmModelRepository,
            FileAssetsRepository fileAssetsRepository,
            FileAssetExtractionsRepository fileAssetExtractionsRepository
    ) {
        QaSessionsRepository qaSessionsRepository = mock(QaSessionsRepository.class);
        QaMessagesRepository qaMessagesRepository = mock(QaMessagesRepository.class);
        QaTurnsRepository qaTurnsRepository = mock(QaTurnsRepository.class);
        RagPostChatRetrievalService ragRetrievalService = mock(RagPostChatRetrievalService.class);
        RagCommentChatRetrievalService ragCommentChatRetrievalService = mock(RagCommentChatRetrievalService.class);
        RagChatPostCommentAggregationService ragChatPostCommentAggregationService = mock(RagChatPostCommentAggregationService.class);
        HybridRetrievalConfigService hybridRetrievalConfigService = mock(HybridRetrievalConfigService.class);
        HybridRagRetrievalService hybridRagRetrievalService = mock(HybridRagRetrievalService.class);
        ContextClipConfigService contextClipConfigService = mock(ContextClipConfigService.class);
        CitationConfigService citationConfigService = mock(CitationConfigService.class);
        ChatRagAugmentConfigService chatRagAugmentConfigService = mock(ChatRagAugmentConfigService.class);
        RagContextPromptService ragContextPromptService = mock(RagContextPromptService.class);
        RetrievalEventsRepository retrievalEventsRepository = mock(RetrievalEventsRepository.class);
        RetrievalHitsRepository retrievalHitsRepository = mock(RetrievalHitsRepository.class);
        ContextWindowsRepository contextWindowsRepository = mock(ContextWindowsRepository.class);
        PostsRepository postsRepository = mock(PostsRepository.class);
        QaMessageSourcesRepository qaMessageSourcesRepository = mock(QaMessageSourcesRepository.class);
        OpenSearchTokenizeService openSearchTokenizeService = mock(OpenSearchTokenizeService.class);
        TokenCountService tokenCountService = new TokenCountService(openSearchTokenizeService);
        UsersRepository usersRepository = mock(UsersRepository.class);
        PortalChatConfigService portalChatConfigService = mock(PortalChatConfigService.class);
        PromptsRepository promptsRepository = mock(PromptsRepository.class);
        ChatContextGovernanceConfigService chatContextGovernanceConfigService = mock(ChatContextGovernanceConfigService.class);
        ChatContextGovernanceService chatContextGovernanceService = mock(ChatContextGovernanceService.class);

        return new AiChatService(
                llmGateway,
                llmModelRepository,
                qaSessionsRepository,
                qaMessagesRepository,
                qaTurnsRepository,
                ragRetrievalService,
                ragCommentChatRetrievalService,
                ragChatPostCommentAggregationService,
                hybridRetrievalConfigService,
                hybridRagRetrievalService,
                contextClipConfigService,
                citationConfigService,
                chatRagAugmentConfigService,
                ragContextPromptService,
                retrievalEventsRepository,
                retrievalHitsRepository,
                contextWindowsRepository,
                postsRepository,
                qaMessageSourcesRepository,
                tokenCountService,
                usersRepository,
                fileAssetsRepository,
                fileAssetExtractionsRepository,
                portalChatConfigService,
                promptsRepository,
                chatContextGovernanceConfigService,
                chatContextGovernanceService
        );
    }
}
