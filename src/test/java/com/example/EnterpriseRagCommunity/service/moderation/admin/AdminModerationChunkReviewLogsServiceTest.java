package com.example.EnterpriseRagCommunity.service.moderation.admin;

import com.example.EnterpriseRagCommunity.dto.moderation.AdminModerationChunkContentPreviewDTO;
import com.example.EnterpriseRagCommunity.dto.moderation.AdminModerationChunkLogDetailDTO;
import com.example.EnterpriseRagCommunity.dto.moderation.AdminModerationChunkLogItemDTO;
import com.example.EnterpriseRagCommunity.entity.content.PostAttachmentsEntity;
import com.example.EnterpriseRagCommunity.entity.moderation.ModerationChunkEntity;
import com.example.EnterpriseRagCommunity.entity.moderation.ModerationChunkSetEntity;
import com.example.EnterpriseRagCommunity.entity.moderation.enums.ChunkSetStatus;
import com.example.EnterpriseRagCommunity.entity.moderation.enums.ChunkSourceType;
import com.example.EnterpriseRagCommunity.entity.moderation.enums.ChunkStatus;
import com.example.EnterpriseRagCommunity.entity.moderation.enums.ContentType;
import com.example.EnterpriseRagCommunity.entity.moderation.enums.ModerationCaseType;
import com.example.EnterpriseRagCommunity.entity.moderation.enums.Verdict;
import com.example.EnterpriseRagCommunity.entity.monitor.FileAssetExtractionsEntity;
import com.example.EnterpriseRagCommunity.entity.monitor.FileAssetsEntity;
import com.example.EnterpriseRagCommunity.exception.ResourceNotFoundException;
import com.example.EnterpriseRagCommunity.repository.content.PostAttachmentsRepository;
import com.example.EnterpriseRagCommunity.repository.moderation.ModerationChunkRepository;
import com.example.EnterpriseRagCommunity.repository.moderation.ModerationChunkSetRepository;
import com.example.EnterpriseRagCommunity.repository.monitor.FileAssetExtractionsRepository;
import com.example.EnterpriseRagCommunity.service.moderation.ModerationChunkReviewService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AdminModerationChunkReviewLogsServiceTest {

    @Test
    void listRecent_shouldClampMinLimit_trimBlankKeyword_andSkipNullChunk() {
        ModerationChunkRepository chunkRepository = mock(ModerationChunkRepository.class);
        ModerationChunkSetRepository chunkSetRepository = mock(ModerationChunkSetRepository.class);
        ModerationChunkReviewService chunkReviewService = mock(ModerationChunkReviewService.class);
        FileAssetExtractionsRepository fileAssetExtractionsRepository = mock(FileAssetExtractionsRepository.class);
        PostAttachmentsRepository postAttachmentsRepository = mock(PostAttachmentsRepository.class);

        ModerationChunkEntity c = baseChunk(1L, null);
        c.setConfidence(null);
        c.setStatus(null);
        c.setVerdict(null);
        c.setSourceType(null);

        when(chunkRepository.findRecentForAdmin(eq(11L), eq(ChunkStatus.SUCCESS), eq(Verdict.APPROVE),
                eq(ChunkSourceType.FILE_TEXT), eq(99L), eq(null), any(PageRequest.class)))
                .thenReturn(Arrays.asList(null, c));

        AdminModerationChunkReviewLogsService service = new AdminModerationChunkReviewLogsService(
                chunkRepository, chunkSetRepository, chunkReviewService, fileAssetExtractionsRepository, postAttachmentsRepository, new ObjectMapper()
        );

        List<AdminModerationChunkLogItemDTO> out = service.listRecent(
                -8, 11L, ChunkStatus.SUCCESS, Verdict.APPROVE, ChunkSourceType.FILE_TEXT, 99L, "   "
        );

        assertEquals(1, out.size());
        AdminModerationChunkLogItemDTO dto = out.getFirst();
        assertEquals(1L, dto.getId());
        assertNull(dto.getQueueId());
        assertNull(dto.getStatus());
        assertNull(dto.getVerdict());
        assertNull(dto.getSourceType());
        assertNull(dto.getConfidence());

        ArgumentCaptor<PageRequest> pageCaptor = ArgumentCaptor.forClass(PageRequest.class);
        verify(chunkRepository).findRecentForAdmin(eq(11L), eq(ChunkStatus.SUCCESS), eq(Verdict.APPROVE),
                eq(ChunkSourceType.FILE_TEXT), eq(99L), eq(null), pageCaptor.capture());
        assertEquals(1, pageCaptor.getValue().getPageSize());
    }

    @Test
    void listRecent_shouldClampMaxLimit_mapChunkSet_andExtractBudgetLog() {
        ModerationChunkRepository chunkRepository = mock(ModerationChunkRepository.class);
        ModerationChunkSetRepository chunkSetRepository = mock(ModerationChunkSetRepository.class);
        ModerationChunkReviewService chunkReviewService = mock(ModerationChunkReviewService.class);
        FileAssetExtractionsRepository fileAssetExtractionsRepository = mock(FileAssetExtractionsRepository.class);
        PostAttachmentsRepository postAttachmentsRepository = mock(PostAttachmentsRepository.class);

        ModerationChunkEntity c = baseChunk(2L, 10L);
        c.setStatus(ChunkStatus.FAILED);
        c.setVerdict(Verdict.REJECT);
        c.setSourceType(ChunkSourceType.FILE_TEXT);
        c.setConfidence(BigDecimal.valueOf(0.55));
        c.setSourceKey("src-key");
        c.setLastError("err");
        c.setModel("m");
        c.setTokensIn(2);
        c.setTokensOut(3);

        ModerationChunkSetEntity set = baseSet(10L);
        Map<String, Object> cfg = new HashMap<>();
        Map<String, Object> bcl = new LinkedHashMap<>();
        bcl.put(null, 1);
        bcl.put("k", "v");
        cfg.put("budgetConvergenceLog", bcl);
        set.setConfigJson(cfg);

        when(chunkRepository.findRecentForAdmin(eq(null), eq(null), eq(null),
                eq(null), eq(null), eq("kw"), any(PageRequest.class)))
                .thenReturn(List.of(c));
        when(chunkSetRepository.findAllById(Set.of(10L))).thenReturn(Arrays.asList(null, set));

        AdminModerationChunkReviewLogsService service = new AdminModerationChunkReviewLogsService(
                chunkRepository, chunkSetRepository, chunkReviewService, fileAssetExtractionsRepository, postAttachmentsRepository, new ObjectMapper()
        );

        List<AdminModerationChunkLogItemDTO> out = service.listRecent(
                999, null, null, null, null, null, "  kw  "
        );

        assertEquals(1, out.size());
        AdminModerationChunkLogItemDTO dto = out.getFirst();
        assertEquals(22L, dto.getQueueId());
        assertEquals("FAILED", dto.getStatus());
        assertEquals("REJECT", dto.getVerdict());
        assertEquals(0.55d, dto.getConfidence());
        assertEquals(Map.of("k", "v"), dto.getBudgetConvergenceLog());

        ArgumentCaptor<PageRequest> pageCaptor = ArgumentCaptor.forClass(PageRequest.class);
        verify(chunkRepository).findRecentForAdmin(eq(null), eq(null), eq(null),
                eq(null), eq(null), eq("kw"), pageCaptor.capture());
        assertEquals(200, pageCaptor.getValue().getPageSize());
    }

    @Test
    void getDetail_shouldThrowWhenChunkMissing() {
        ModerationChunkRepository chunkRepository = mock(ModerationChunkRepository.class);
        when(chunkRepository.findById(9L)).thenReturn(Optional.empty());

        AdminModerationChunkReviewLogsService service = new AdminModerationChunkReviewLogsService(
                chunkRepository,
                mock(ModerationChunkSetRepository.class),
                mock(ModerationChunkReviewService.class),
                mock(FileAssetExtractionsRepository.class),
                mock(PostAttachmentsRepository.class),
                new ObjectMapper()
        );

        assertThrows(ResourceNotFoundException.class, () -> service.getDetail(9L));
    }

    @Test
    void getDetail_shouldThrowWhenSetMissing() {
        ModerationChunkRepository chunkRepository = mock(ModerationChunkRepository.class);
        ModerationChunkSetRepository chunkSetRepository = mock(ModerationChunkSetRepository.class);

        ModerationChunkEntity chunk = baseChunk(10L, 99L);
        when(chunkRepository.findById(10L)).thenReturn(Optional.of(chunk));
        when(chunkSetRepository.findById(99L)).thenReturn(Optional.empty());

        AdminModerationChunkReviewLogsService service = new AdminModerationChunkReviewLogsService(
                chunkRepository,
                chunkSetRepository,
                mock(ModerationChunkReviewService.class),
                mock(FileAssetExtractionsRepository.class),
                mock(PostAttachmentsRepository.class),
                new ObjectMapper()
        );

        assertThrows(ResourceNotFoundException.class, () -> service.getDetail(10L));
    }

    @Test
    void getDetail_shouldReturnChunkAndSetDto() {
        ModerationChunkRepository chunkRepository = mock(ModerationChunkRepository.class);
        ModerationChunkSetRepository chunkSetRepository = mock(ModerationChunkSetRepository.class);

        ModerationChunkEntity chunk = baseChunk(11L, 77L);
        chunk.setLabels(Map.of("risk", "low"));
        chunk.setConfidence(BigDecimal.valueOf(0.1));
        chunk.setVerdict(Verdict.REVIEW);
        chunk.setStatus(ChunkStatus.RUNNING);
        chunk.setSourceType(ChunkSourceType.POST_TEXT);

        ModerationChunkSetEntity set = baseSet(77L);
        set.setConfigJson(Map.of("a", 1));
        set.setMemoryJson(Map.of("m", 2));

        when(chunkRepository.findById(11L)).thenReturn(Optional.of(chunk));
        when(chunkSetRepository.findById(77L)).thenReturn(Optional.of(set));

        AdminModerationChunkReviewLogsService service = new AdminModerationChunkReviewLogsService(
                chunkRepository,
                chunkSetRepository,
                mock(ModerationChunkReviewService.class),
                mock(FileAssetExtractionsRepository.class),
                mock(PostAttachmentsRepository.class),
                new ObjectMapper()
        );

        AdminModerationChunkLogDetailDTO out = service.getDetail(11L);
        assertEquals(11L, out.getChunk().getId());
        assertEquals("POST_TEXT", out.getChunk().getSourceType());
        assertEquals("RUNNING", out.getChunk().getStatus());
        assertEquals("REVIEW", out.getChunk().getVerdict());
        assertEquals(77L, out.getChunkSet().getId());
        assertEquals("CONTENT", out.getChunkSet().getCaseType());
        assertEquals("POST", out.getChunkSet().getContentType());
    }

    @Test
    void getContentPreview_shouldThrowWhenChunkMissing() {
        ModerationChunkRepository chunkRepository = mock(ModerationChunkRepository.class);
        when(chunkRepository.findById(1L)).thenReturn(Optional.empty());

        AdminModerationChunkReviewLogsService service = new AdminModerationChunkReviewLogsService(
                chunkRepository,
                mock(ModerationChunkSetRepository.class),
                mock(ModerationChunkReviewService.class),
                mock(FileAssetExtractionsRepository.class),
                mock(PostAttachmentsRepository.class),
                new ObjectMapper()
        );

        assertThrows(ResourceNotFoundException.class, () -> service.getContentPreview(1L));
    }

    @Test
    void getContentPreview_shouldThrowWhenSetMissing() {
        ModerationChunkRepository chunkRepository = mock(ModerationChunkRepository.class);
        ModerationChunkSetRepository chunkSetRepository = mock(ModerationChunkSetRepository.class);
        ModerationChunkEntity chunk = baseChunk(2L, 20L);

        when(chunkRepository.findById(2L)).thenReturn(Optional.of(chunk));
        when(chunkSetRepository.findById(20L)).thenReturn(Optional.empty());

        AdminModerationChunkReviewLogsService service = new AdminModerationChunkReviewLogsService(
                chunkRepository,
                chunkSetRepository,
                mock(ModerationChunkReviewService.class),
                mock(FileAssetExtractionsRepository.class),
                mock(PostAttachmentsRepository.class),
                new ObjectMapper()
        );

        assertThrows(ResourceNotFoundException.class, () -> service.getContentPreview(2L));
    }

    @Test
    void getContentPreview_shouldReturnReasonWhenSourceTypeMissing() {
        ModerationChunkRepository chunkRepository = mock(ModerationChunkRepository.class);
        ModerationChunkSetRepository chunkSetRepository = mock(ModerationChunkSetRepository.class);
        ModerationChunkEntity chunk = baseChunk(3L, 30L);
        chunk.setSourceType(null);
        ModerationChunkSetEntity set = baseSet(30L);

        when(chunkRepository.findById(3L)).thenReturn(Optional.of(chunk));
        when(chunkSetRepository.findById(30L)).thenReturn(Optional.of(set));

        AdminModerationChunkReviewLogsService service = new AdminModerationChunkReviewLogsService(
                chunkRepository,
                chunkSetRepository,
                mock(ModerationChunkReviewService.class),
                mock(FileAssetExtractionsRepository.class),
                mock(PostAttachmentsRepository.class),
                new ObjectMapper()
        );

        AdminModerationChunkContentPreviewDTO out = service.getContentPreview(3L);
        assertEquals("缺少 sourceType", out.getReason());
        assertEquals("", out.getText());
    }

    @Test
    void getContentPreview_shouldHandleFileTextSwapOffsets_andResolveImages() {
        ModerationChunkRepository chunkRepository = mock(ModerationChunkRepository.class);
        ModerationChunkSetRepository chunkSetRepository = mock(ModerationChunkSetRepository.class);
        ModerationChunkReviewService chunkReviewService = mock(ModerationChunkReviewService.class);
        FileAssetExtractionsRepository fileAssetExtractionsRepository = mock(FileAssetExtractionsRepository.class);

        ModerationChunkEntity chunk = baseChunk(4L, 40L);
        chunk.setSourceType(ChunkSourceType.FILE_TEXT);
        chunk.setFileAssetId(100L);
        chunk.setStartOffset(20);
        chunk.setEndOffset(-3);
        ModerationChunkSetEntity set = baseSet(40L);

        FileAssetExtractionsEntity ex = new FileAssetExtractionsEntity();
        ex.setFileAssetId(100L);
        ex.setExtractedMetadataJson("""
                {"extractedImages":[
                  {"placeholder":"[[IMAGE_2]]","url":"u2","mimeType":"image/jpeg","fileName":"2.jpg","sizeBytes":"12"},
                  {"index":1,"placeholder":"[[IMAGE_1]]","url":"u1","mimeType":"image/png","fileName":"1.png","sizeBytes":11},
                  {"index":9,"placeholder":"[[IMAGE_9]]"},
                  "not-map"
                ]}
                """);

        when(chunkRepository.findById(4L)).thenReturn(Optional.of(chunk));
        when(chunkSetRepository.findById(40L)).thenReturn(Optional.of(set));
        when(chunkReviewService.loadChunkText(22L, ChunkSourceType.FILE_TEXT, 100L, 0, 20))
                .thenReturn(Optional.of("x [[IMAGE_2]] y [[IMAGE_1]]"));
        when(fileAssetExtractionsRepository.findById(100L)).thenReturn(Optional.of(ex));

        AdminModerationChunkReviewLogsService service = new AdminModerationChunkReviewLogsService(
                chunkRepository,
                chunkSetRepository,
                chunkReviewService,
                fileAssetExtractionsRepository,
                mock(PostAttachmentsRepository.class),
                new ObjectMapper()
        );

        AdminModerationChunkContentPreviewDTO out = service.getContentPreview(4L);
        assertEquals("x [[IMAGE_2]] y [[IMAGE_1]]", out.getText());
        assertNull(out.getReason());
        assertEquals(2, out.getImages().size());
        assertEquals(1, out.getImages().get(0).getIndex());
        assertEquals(2, out.getImages().get(1).getIndex());
    }

    @Test
    void getContentPreview_shouldSetMissingFileAssetReasonWhenTextFound() {
        ModerationChunkRepository chunkRepository = mock(ModerationChunkRepository.class);
        ModerationChunkSetRepository chunkSetRepository = mock(ModerationChunkSetRepository.class);
        ModerationChunkReviewService chunkReviewService = mock(ModerationChunkReviewService.class);

        ModerationChunkEntity chunk = baseChunk(5L, 50L);
        chunk.setSourceType(ChunkSourceType.FILE_TEXT);
        chunk.setFileAssetId(null);
        ModerationChunkSetEntity set = baseSet(50L);

        when(chunkRepository.findById(5L)).thenReturn(Optional.of(chunk));
        when(chunkSetRepository.findById(50L)).thenReturn(Optional.of(set));
        when(chunkReviewService.loadChunkText(eq(22L), eq(ChunkSourceType.FILE_TEXT), eq(null), eq(4), eq(9)))
                .thenReturn(Optional.of("ok"));

        AdminModerationChunkReviewLogsService service = new AdminModerationChunkReviewLogsService(
                chunkRepository,
                chunkSetRepository,
                chunkReviewService,
                mock(FileAssetExtractionsRepository.class),
                mock(PostAttachmentsRepository.class),
                new ObjectMapper()
        );

        AdminModerationChunkContentPreviewDTO out = service.getContentPreview(5L);
        assertEquals("缺少 fileAssetId", out.getReason());
        assertTrue(out.getImages().isEmpty());
    }

    @Test
    void getContentPreview_shouldKeepTextNotFoundReasonWhenNoText() {
        ModerationChunkRepository chunkRepository = mock(ModerationChunkRepository.class);
        ModerationChunkSetRepository chunkSetRepository = mock(ModerationChunkSetRepository.class);
        ModerationChunkReviewService chunkReviewService = mock(ModerationChunkReviewService.class);

        ModerationChunkEntity chunk = baseChunk(6L, 60L);
        chunk.setSourceType(ChunkSourceType.FILE_TEXT);
        chunk.setFileAssetId(null);
        ModerationChunkSetEntity set = baseSet(60L);

        when(chunkRepository.findById(6L)).thenReturn(Optional.of(chunk));
        when(chunkSetRepository.findById(60L)).thenReturn(Optional.of(set));
        when(chunkReviewService.loadChunkText(eq(22L), eq(ChunkSourceType.FILE_TEXT), eq(null), eq(4), eq(9)))
                .thenReturn(Optional.empty());

        AdminModerationChunkReviewLogsService service = new AdminModerationChunkReviewLogsService(
                chunkRepository,
                chunkSetRepository,
                chunkReviewService,
                mock(FileAssetExtractionsRepository.class),
                mock(PostAttachmentsRepository.class),
                new ObjectMapper()
        );

        AdminModerationChunkContentPreviewDTO out = service.getContentPreview(6L);
        assertEquals("无法定位来源文本", out.getReason());
        assertEquals("", out.getText());
    }

    @Test
    void getContentPreview_shouldResolvePostTextImages() {
        ModerationChunkRepository chunkRepository = mock(ModerationChunkRepository.class);
        ModerationChunkSetRepository chunkSetRepository = mock(ModerationChunkSetRepository.class);
        ModerationChunkReviewService chunkReviewService = mock(ModerationChunkReviewService.class);
        PostAttachmentsRepository postAttachmentsRepository = mock(PostAttachmentsRepository.class);

        ModerationChunkEntity chunk = baseChunk(7L, 70L);
        chunk.setSourceType(ChunkSourceType.POST_TEXT);
        ModerationChunkSetEntity set = baseSet(70L);
        set.setContentType(ContentType.POST);
        set.setContentId(333L);

        List<PostAttachmentsEntity> items = new ArrayList<>();
        items.add(null);
        PostAttachmentsEntity noAsset = new PostAttachmentsEntity();
        noAsset.setId(1L);
        items.add(noAsset);
        PostAttachmentsEntity notImage = new PostAttachmentsEntity();
        notImage.setId(2L);
        FileAssetsEntity txt = new FileAssetsEntity();
        txt.setMimeType(" text/plain ");
        notImage.setFileAsset(txt);
        items.add(notImage);
        PostAttachmentsEntity image = new PostAttachmentsEntity();
        image.setId(3L);
        image.setFileAssetId(78L);
        image.setWidth(100);
        image.setHeight(50);
        FileAssetsEntity fa = new FileAssetsEntity();
        fa.setUrl("http://img");
        fa.setMimeType(" IMAGE/PNG ");
        fa.setOriginalName("a.png");
        fa.setSizeBytes(99L);
        image.setFileAsset(fa);
        items.add(image);

        when(chunkRepository.findById(7L)).thenReturn(Optional.of(chunk));
        when(chunkSetRepository.findById(70L)).thenReturn(Optional.of(set));
        when(chunkReviewService.loadChunkText(eq(22L), eq(ChunkSourceType.POST_TEXT), eq(8L), eq(4), eq(9)))
                .thenReturn(Optional.of("post text"));
        when(postAttachmentsRepository.findByPostId(333L, PageRequest.of(0, 200))).thenReturn(new PageImpl<>(items));

        AdminModerationChunkReviewLogsService service = new AdminModerationChunkReviewLogsService(
                chunkRepository,
                chunkSetRepository,
                chunkReviewService,
                mock(FileAssetExtractionsRepository.class),
                postAttachmentsRepository,
                new ObjectMapper()
        );

        AdminModerationChunkContentPreviewDTO out = service.getContentPreview(7L);
        assertEquals(1, out.getImages().size());
        AdminModerationChunkContentPreviewDTO.Image img = out.getImages().getFirst();
        assertEquals(1, img.getIndex());
        assertEquals("[[IMAGE_1]]", img.getPlaceholder());
        assertEquals(78L, img.getFileAssetId());
        assertEquals(100, img.getWidth());
        assertEquals(50, img.getHeight());
    }

    @Test
    void privateResolveFileTextImages_shouldCoverEarlyReturnBranches() throws Exception {
        FileAssetExtractionsRepository extractionRepo = mock(FileAssetExtractionsRepository.class);
        AdminModerationChunkReviewLogsService service = new AdminModerationChunkReviewLogsService(
                mock(ModerationChunkRepository.class),
                mock(ModerationChunkSetRepository.class),
                mock(ModerationChunkReviewService.class),
                extractionRepo,
                mock(PostAttachmentsRepository.class),
                new ObjectMapper()
        );

        assertTrue(((List<?>) invokePrivateInstance(service, "resolveFileTextImages", new Class[]{String.class, Long.class}, "x", null)).isEmpty());

        when(extractionRepo.findById(1L)).thenReturn(Optional.empty());
        assertTrue(((List<?>) invokePrivateInstance(service, "resolveFileTextImages", new Class[]{String.class, Long.class}, "x", 1L)).isEmpty());

        FileAssetExtractionsEntity blank = new FileAssetExtractionsEntity();
        blank.setExtractedMetadataJson(" ");
        when(extractionRepo.findById(2L)).thenReturn(Optional.of(blank));
        assertTrue(((List<?>) invokePrivateInstance(service, "resolveFileTextImages", new Class[]{String.class, Long.class}, "x", 2L)).isEmpty());

        FileAssetExtractionsEntity bad = new FileAssetExtractionsEntity();
        bad.setExtractedMetadataJson("{bad");
        when(extractionRepo.findById(3L)).thenReturn(Optional.of(bad));
        assertTrue(((List<?>) invokePrivateInstance(service, "resolveFileTextImages", new Class[]{String.class, Long.class}, "x", 3L)).isEmpty());

        FileAssetExtractionsEntity noList = new FileAssetExtractionsEntity();
        noList.setExtractedMetadataJson("{\"a\":1}");
        when(extractionRepo.findById(4L)).thenReturn(Optional.of(noList));
        assertTrue(((List<?>) invokePrivateInstance(service, "resolveFileTextImages", new Class[]{String.class, Long.class}, "x", 4L)).isEmpty());

        FileAssetExtractionsEntity emptyList = new FileAssetExtractionsEntity();
        emptyList.setExtractedMetadataJson("{\"extractedImages\":[]}");
        when(extractionRepo.findById(5L)).thenReturn(Optional.of(emptyList));
        assertTrue(((List<?>) invokePrivateInstance(service, "resolveFileTextImages", new Class[]{String.class, Long.class}, "x", 5L)).isEmpty());

        FileAssetExtractionsEntity noUsed = new FileAssetExtractionsEntity();
        noUsed.setExtractedMetadataJson("{\"extractedImages\":[{\"index\":1}]}");
        when(extractionRepo.findById(6L)).thenReturn(Optional.of(noUsed));
        assertTrue(((List<?>) invokePrivateInstance(service, "resolveFileTextImages", new Class[]{String.class, Long.class}, "no-image", 6L)).isEmpty());
    }

    @Test
    void privateResolvePostTextImages_shouldCoverGuardBranches() throws Exception {
        PostAttachmentsRepository postAttachmentsRepository = mock(PostAttachmentsRepository.class);
        AdminModerationChunkReviewLogsService service = new AdminModerationChunkReviewLogsService(
                mock(ModerationChunkRepository.class),
                mock(ModerationChunkSetRepository.class),
                mock(ModerationChunkReviewService.class),
                mock(FileAssetExtractionsRepository.class),
                postAttachmentsRepository,
                new ObjectMapper()
        );

        assertTrue(((List<?>) invokePrivateInstance(service, "resolvePostTextImages", new Class[]{ModerationChunkSetEntity.class}, (Object) null)).isEmpty());

        ModerationChunkSetEntity wrongType = baseSet(1L);
        wrongType.setContentType(ContentType.COMMENT);
        assertTrue(((List<?>) invokePrivateInstance(service, "resolvePostTextImages", new Class[]{ModerationChunkSetEntity.class}, wrongType)).isEmpty());

        ModerationChunkSetEntity nullContentId = baseSet(2L);
        nullContentId.setContentType(ContentType.POST);
        nullContentId.setContentId(null);
        assertTrue(((List<?>) invokePrivateInstance(service, "resolvePostTextImages", new Class[]{ModerationChunkSetEntity.class}, nullContentId)).isEmpty());

        ModerationChunkSetEntity withPost = baseSet(3L);
        withPost.setContentType(ContentType.POST);
        withPost.setContentId(55L);


        when(postAttachmentsRepository.findByPostId(55L, PageRequest.of(0, 200))).thenReturn(new PageImpl<>(List.of()));
        assertTrue(((List<?>) invokePrivateInstance(service, "resolvePostTextImages", new Class[]{ModerationChunkSetEntity.class}, withPost)).isEmpty());
    }

    @Test
    void privateStaticHelpers_shouldCoverConversionAndParsingBranches() throws Exception {
        @SuppressWarnings("unchecked")
        Set<Integer> used1 = (Set<Integer>) invokePrivateStatic(
                AdminModerationChunkReviewLogsService.class,
                "parseUsedImageIndices",
                new Class[]{String.class},
                (Object) null
        );
        assertTrue(used1.isEmpty());
        @SuppressWarnings("unchecked")
        Set<Integer> used2 = (Set<Integer>) invokePrivateStatic(
                AdminModerationChunkReviewLogsService.class,
                "parseUsedImageIndices",
                new Class[]{String.class},
                "[[IMAGE_1]]x[[IMAGE_2]]x[[IMAGE_a]]"
        );
        assertEquals(Set.of(1, 2), used2);

        assertNull(invokePrivateStatic(
                AdminModerationChunkReviewLogsService.class,
                "parseImageIndexFromPlaceholder",
                new Class[]{String.class},
                (Object) null
        ));
        assertNull(invokePrivateStatic(
                AdminModerationChunkReviewLogsService.class,
                "parseImageIndexFromPlaceholder",
                new Class[]{String.class},
                "x"
        ));
        assertEquals(7, invokePrivateStatic(
                AdminModerationChunkReviewLogsService.class,
                "parseImageIndexFromPlaceholder",
                new Class[]{String.class},
                "[[IMAGE_7]]"
        ));

        assertEquals(0, invokePrivateStatic(
                AdminModerationChunkReviewLogsService.class,
                "safeOffset",
                new Class[]{Integer.class},
                (Object) null
        ));
        assertEquals(0, invokePrivateStatic(
                AdminModerationChunkReviewLogsService.class,
                "safeOffset",
                new Class[]{Integer.class},
                -1
        ));
        assertEquals(9, invokePrivateStatic(
                AdminModerationChunkReviewLogsService.class,
                "safeOffset",
                new Class[]{Integer.class},
                9
        ));

        assertNull(invokePrivateStatic(AdminModerationChunkReviewLogsService.class, "toInt", new Class[]{Object.class}, new Object[]{null}));
        assertEquals(3, invokePrivateStatic(AdminModerationChunkReviewLogsService.class, "toInt", new Class[]{Object.class}, 3));
        assertEquals(Integer.MAX_VALUE, invokePrivateStatic(AdminModerationChunkReviewLogsService.class, "toInt", new Class[]{Object.class}, Long.MAX_VALUE));
        assertEquals(8, invokePrivateStatic(AdminModerationChunkReviewLogsService.class, "toInt", new Class[]{Object.class}, 8.8d));
        assertEquals(15, invokePrivateStatic(AdminModerationChunkReviewLogsService.class, "toInt", new Class[]{Object.class}, "15"));
        assertNull(invokePrivateStatic(AdminModerationChunkReviewLogsService.class, "toInt", new Class[]{Object.class}, ""));
        assertNull(invokePrivateStatic(AdminModerationChunkReviewLogsService.class, "toInt", new Class[]{Object.class}, "x"));

        assertNull(invokePrivateStatic(AdminModerationChunkReviewLogsService.class, "toLong", new Class[]{Object.class}, new Object[]{null}));
        assertEquals(2L, invokePrivateStatic(AdminModerationChunkReviewLogsService.class, "toLong", new Class[]{Object.class}, 2));
        assertEquals(3L, invokePrivateStatic(AdminModerationChunkReviewLogsService.class, "toLong", new Class[]{Object.class}, 3L));
        assertEquals(5L, invokePrivateStatic(AdminModerationChunkReviewLogsService.class, "toLong", new Class[]{Object.class}, 5.9d));
        assertEquals(88L, invokePrivateStatic(AdminModerationChunkReviewLogsService.class, "toLong", new Class[]{Object.class}, "88"));
        assertNull(invokePrivateStatic(AdminModerationChunkReviewLogsService.class, "toLong", new Class[]{Object.class}, ""));
        assertNull(invokePrivateStatic(AdminModerationChunkReviewLogsService.class, "toLong", new Class[]{Object.class}, "x"));

        assertNull(invokePrivateStatic(AdminModerationChunkReviewLogsService.class, "toStr", new Class[]{Object.class}, new Object[]{null}));
        assertNull(invokePrivateStatic(AdminModerationChunkReviewLogsService.class, "toStr", new Class[]{Object.class}, "   "));
        assertEquals("a", invokePrivateStatic(AdminModerationChunkReviewLogsService.class, "toStr", new Class[]{Object.class}, "a"));

        assertNull(invokePrivateStatic(
                AdminModerationChunkReviewLogsService.class,
                "extractBudgetConvergenceLog",
                new Class[]{Map.class},
                new Object[]{null}
        ));
        assertNull(invokePrivateStatic(
                AdminModerationChunkReviewLogsService.class,
                "extractBudgetConvergenceLog",
                new Class[]{Map.class},
                Map.of()
        ));
        assertNull(invokePrivateStatic(
                AdminModerationChunkReviewLogsService.class,
                "extractBudgetConvergenceLog",
                new Class[]{Map.class},
                Map.of("budgetConvergenceLog", "x")
        ));

        Map<String, Object> onlyNullKey = new HashMap<>();
        Map<Object, Object> badMap = new HashMap<>();
        badMap.put(null, "x");
        onlyNullKey.put("budgetConvergenceLog", badMap);
        assertNull(invokePrivateStatic(
                AdminModerationChunkReviewLogsService.class,
                "extractBudgetConvergenceLog",
                new Class[]{Map.class},
                onlyNullKey
        ));

        Map<String, Object> mixedKeys = new HashMap<>();
        Map<Object, Object> goodMap = new LinkedHashMap<>();
        goodMap.put(null, "x");
        goodMap.put("a", 1);
        mixedKeys.put("budgetConvergenceLog", goodMap);
        @SuppressWarnings("unchecked")
        Map<String, Object> out = (Map<String, Object>) invokePrivateStatic(
                AdminModerationChunkReviewLogsService.class,
                "extractBudgetConvergenceLog",
                new Class[]{Map.class},
                mixedKeys
        );
        assertEquals(Map.of("a", 1), out);

        assertNull(invokePrivateStatic(
                AdminModerationChunkReviewLogsService.class,
                "toDoubleOrNull",
                new Class[]{BigDecimal.class},
                new Object[]{null}
        ));
        assertEquals(1.2d, invokePrivateStatic(
                AdminModerationChunkReviewLogsService.class,
                "toDoubleOrNull",
                new Class[]{BigDecimal.class},
                BigDecimal.valueOf(1.2)
        ));
    }

    private static Object invokePrivateStatic(Class<?> clazz, String name, Class<?>[] paramTypes, Object... args) throws Exception {
        Method m = clazz.getDeclaredMethod(name, paramTypes);
        m.setAccessible(true);
        return m.invoke(null, args);
    }

    private static Object invokePrivateInstance(Object target, String name, Class<?>[] paramTypes, Object... args) throws Exception {
        Method m = target.getClass().getDeclaredMethod(name, paramTypes);
        m.setAccessible(true);
        return m.invoke(target, args);
    }

    private static ModerationChunkEntity baseChunk(Long id, Long setId) {
        ModerationChunkEntity c = new ModerationChunkEntity();
        c.setId(id);
        c.setChunkSetId(setId);
        c.setSourceType(ChunkSourceType.POST_TEXT);
        c.setSourceKey("k");
        c.setFileAssetId(8L);
        c.setFileName("f");
        c.setChunkIndex(1);
        c.setStartOffset(4);
        c.setEndOffset(9);
        c.setStatus(ChunkStatus.SUCCESS);
        c.setAttempts(1);
        c.setLastError("e");
        c.setModel("m");
        c.setVerdict(Verdict.APPROVE);
        c.setConfidence(BigDecimal.valueOf(0.8));
        c.setTokensIn(10);
        c.setTokensOut(20);
        c.setDecidedAt(LocalDateTime.now());
        c.setCreatedAt(LocalDateTime.now());
        c.setUpdatedAt(LocalDateTime.now());
        return c;
    }

    private static ModerationChunkSetEntity baseSet(Long id) {
        ModerationChunkSetEntity s = new ModerationChunkSetEntity();
        s.setId(id);
        s.setQueueId(22L);
        s.setCaseType(ModerationCaseType.CONTENT);
        s.setContentType(ContentType.POST);
        s.setContentId(333L);
        s.setStatus(ChunkSetStatus.DONE);
        s.setChunkThresholdChars(1);
        s.setChunkSizeChars(2);
        s.setOverlapChars(3);
        s.setTotalChunks(4);
        s.setCompletedChunks(5);
        s.setFailedChunks(6);
        s.setCreatedAt(LocalDateTime.now());
        s.setUpdatedAt(LocalDateTime.now());
        return s;
    }
}
