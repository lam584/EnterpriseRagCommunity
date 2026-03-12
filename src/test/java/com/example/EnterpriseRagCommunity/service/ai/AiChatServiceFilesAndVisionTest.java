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
