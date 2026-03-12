package com.example.EnterpriseRagCommunity.service.content.admin.impl;

import com.example.EnterpriseRagCommunity.dto.content.admin.PostFileExtractionAdminDetailDTO;
import com.example.EnterpriseRagCommunity.entity.monitor.FileAssetsEntity;
import com.example.EnterpriseRagCommunity.entity.monitor.FileAssetExtractionsEntity;
import com.example.EnterpriseRagCommunity.entity.monitor.enums.FileAssetExtractionStatus;
import com.example.EnterpriseRagCommunity.repository.content.PostAttachmentsRepository;
import com.example.EnterpriseRagCommunity.repository.monitor.FileAssetExtractionsRepository;
import com.example.EnterpriseRagCommunity.repository.monitor.FileAssetsRepository;
import com.example.EnterpriseRagCommunity.service.monitor.FileAssetExtractionAsyncService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AdminPostFilesServiceImplCoverageTest {

    @Test
    void reextract_should_cover_validation_and_update_existing_record_branch() {
        PostAttachmentsRepository repo = mock(PostAttachmentsRepository.class);
        FileAssetsRepository fileAssetsRepository = mock(FileAssetsRepository.class);
        FileAssetExtractionsRepository extractionsRepository = mock(FileAssetExtractionsRepository.class);
        FileAssetExtractionAsyncService async = mock(FileAssetExtractionAsyncService.class);
        AdminPostFilesServiceImpl svc = new AdminPostFilesServiceImpl(
                repo, fileAssetsRepository, extractionsRepository, async, new ObjectMapper()
        );

        assertThrows(IllegalArgumentException.class, () -> svc.reextract(null));
        when(repo.adminGetPostFileDetail(1L)).thenReturn(Optional.empty());
        assertThrows(IllegalArgumentException.class, () -> svc.reextract(1L));

        PostAttachmentsRepository.AdminPostFileDetailRow noAssetId = mock(PostAttachmentsRepository.AdminPostFileDetailRow.class);
        when(noAssetId.getFileAssetId()).thenReturn(null);
        when(repo.adminGetPostFileDetail(2L)).thenReturn(Optional.of(noAssetId));
        assertThrows(IllegalArgumentException.class, () -> svc.reextract(2L));

        PostAttachmentsRepository.AdminPostFileDetailRow missingAsset = mock(PostAttachmentsRepository.AdminPostFileDetailRow.class);
        when(missingAsset.getFileAssetId()).thenReturn(10L);
        when(repo.adminGetPostFileDetail(3L)).thenReturn(Optional.of(missingAsset));
        when(fileAssetsRepository.findById(10L)).thenReturn(Optional.empty());
        assertThrows(IllegalArgumentException.class, () -> svc.reextract(3L));

        PostAttachmentsRepository.AdminPostFileDetailRow ok = mock(PostAttachmentsRepository.AdminPostFileDetailRow.class);
        mockDetail(ok, 4L, 20L, "a.bad", "a.bad", "{\"extractedImages\":true}", "text");
        when(repo.adminGetPostFileDetail(4L)).thenReturn(Optional.of(ok));
        when(fileAssetsRepository.findById(20L)).thenReturn(Optional.of(new FileAssetsEntity()));
        FileAssetExtractionsEntity exists = new FileAssetExtractionsEntity();
        exists.setFileAssetId(20L);
        exists.setExtractStatus(FileAssetExtractionStatus.READY);
        exists.setExtractedText("old");
        exists.setExtractedMetadataJson("{\"a\":1}");
        exists.setErrorMessage("err");
        when(extractionsRepository.findById(20L)).thenReturn(Optional.of(exists));
        when(extractionsRepository.save(any(FileAssetExtractionsEntity.class))).thenAnswer(inv -> inv.getArgument(0));

        PostFileExtractionAdminDetailDTO dto = svc.reextract(4L);
        assertNotNull(dto);
        assertTrue(dto.getLlmInputPreview().contains("=== 抽取文本 ==="));
        verify(async).extractAsync(20L);

        ArgumentCaptor<FileAssetExtractionsEntity> cap = ArgumentCaptor.forClass(FileAssetExtractionsEntity.class);
        verify(extractionsRepository).save(cap.capture());
        assertEquals(FileAssetExtractionStatus.PENDING, cap.getValue().getExtractStatus());
        assertNull(cap.getValue().getExtractedText());
        assertNull(cap.getValue().getExtractedMetadataJson());
        assertNull(cap.getValue().getErrorMessage());
    }

    @Test
    void detail_should_cover_preview_branches_for_non_image_and_images_list() {
        PostAttachmentsRepository repo = mock(PostAttachmentsRepository.class);
        AdminPostFilesServiceImpl svc = new AdminPostFilesServiceImpl(
                repo,
                mock(FileAssetsRepository.class),
                mock(FileAssetExtractionsRepository.class),
                mock(FileAssetExtractionAsyncService.class),
                new ObjectMapper()
        );

        PostAttachmentsRepository.AdminPostFileDetailRow row = mock(PostAttachmentsRepository.AdminPostFileDetailRow.class);
        mockDetail(
                row,
                7L,
                8L,
                "x.docx",
                "x.docx",
                "{\"parseDurationMs\":\"  \",\"pages\":\"not-int\",\"textCharCount\":\"123\",\"textTokenCount\":\"not-long\",\"tokenCountMode\":\" \",\"imageCount\":1,\"extractedImages\":[null,{\"placeholder\":\"[1]\",\"url\":\"https://img\"}],\"ext\":\" \",\"mimeType\":\" \"}",
                "body"
        );
        when(repo.adminGetPostFileDetail(7L)).thenReturn(Optional.of(row));

        PostFileExtractionAdminDetailDTO dto = svc.detail(7L);
        assertNotNull(dto.getLlmInputPreview());
        assertTrue(dto.getLlmInputPreview().contains("=== 图片引用 ==="));
        assertTrue(dto.getLlmInputPreview().contains("https://img"));
        assertNull(dto.getPages());
        assertEquals(123L, dto.getTextCharCount());
        assertNull(dto.getTextTokenCount());
    }

    @Test
    void private_static_helpers_should_cover_edge_branches() {
        assertNull(ReflectionTestUtils.invokeMethod(AdminPostFilesServiceImpl.class, "extLowerOrNull", (Object) null));
        assertNull(ReflectionTestUtils.invokeMethod(AdminPostFilesServiceImpl.class, "extLowerOrNull", " "));
        assertNull(ReflectionTestUtils.invokeMethod(AdminPostFilesServiceImpl.class, "extLowerOrNull", "a."));
        assertNull(ReflectionTestUtils.invokeMethod(AdminPostFilesServiceImpl.class, "extLowerOrNull", "a.中文"));
        assertNull(ReflectionTestUtils.invokeMethod(AdminPostFilesServiceImpl.class, "extLowerOrNull", "a.abcdefghijklmnopq"));
        assertEquals("jpg", ReflectionTestUtils.invokeMethod(AdminPostFilesServiceImpl.class, "extLowerOrNull", "C:\\\\a\\\\b.JPG"));

        assertEquals(20, (Integer) ReflectionTestUtils.invokeMethod(AdminPostFilesServiceImpl.class, "clamp", null, 1, 200, 20));
        assertEquals(1, (Integer) ReflectionTestUtils.invokeMethod(AdminPostFilesServiceImpl.class, "clamp", -1, 1, 200, 20));
        assertEquals(200, (Integer) ReflectionTestUtils.invokeMethod(AdminPostFilesServiceImpl.class, "clamp", 999, 1, 200, 20));

        assertNull(ReflectionTestUtils.invokeMethod(AdminPostFilesServiceImpl.class, "getLong", null, "x"));
        assertNull(ReflectionTestUtils.invokeMethod(AdminPostFilesServiceImpl.class, "getLong", Map.of("x", " "), "x"));
        assertNull(ReflectionTestUtils.invokeMethod(AdminPostFilesServiceImpl.class, "getLong", Map.of("x", "bad"), "x"));
        assertEquals(9L, (Long) ReflectionTestUtils.invokeMethod(AdminPostFilesServiceImpl.class, "getLong", Map.of("x", 9), "x"));
        assertEquals(11L, (Long) ReflectionTestUtils.invokeMethod(AdminPostFilesServiceImpl.class, "getLong", Map.of("x", "11"), "x"));

        assertNull(ReflectionTestUtils.invokeMethod(AdminPostFilesServiceImpl.class, "getInt", null, "x"));
        assertNull(ReflectionTestUtils.invokeMethod(AdminPostFilesServiceImpl.class, "getInt", Map.of("x", " "), "x"));
        assertNull(ReflectionTestUtils.invokeMethod(AdminPostFilesServiceImpl.class, "getInt", Map.of("x", "bad"), "x"));
        assertEquals(7, (Integer) ReflectionTestUtils.invokeMethod(AdminPostFilesServiceImpl.class, "getInt", Map.of("x", 7), "x"));
        assertEquals(8, (Integer) ReflectionTestUtils.invokeMethod(AdminPostFilesServiceImpl.class, "getInt", Map.of("x", "8"), "x"));

        assertNull(ReflectionTestUtils.invokeMethod(AdminPostFilesServiceImpl.class, "getString", null, "x"));
        assertNull(ReflectionTestUtils.invokeMethod(AdminPostFilesServiceImpl.class, "getString", Map.of("x", " "), "x"));
        assertEquals("v", ReflectionTestUtils.invokeMethod(AdminPostFilesServiceImpl.class, "getString", Map.of("x", "v"), "x"));

        assertNull(ReflectionTestUtils.invokeMethod(AdminPostFilesServiceImpl.class, "tryGetImages", (Object) null));
        assertNull(ReflectionTestUtils.invokeMethod(AdminPostFilesServiceImpl.class, "tryGetImages", Map.of("extractedImages", "x")));
        assertNotNull(ReflectionTestUtils.invokeMethod(AdminPostFilesServiceImpl.class, "tryGetImages", Map.of("extractedImages", java.util.List.of("x"))));
    }

    @Test
    void private_helpers_should_cover_remaining_post_file_branches() {
        PostAttachmentsRepository repo = mock(PostAttachmentsRepository.class);
        AdminPostFilesServiceImpl svc = new AdminPostFilesServiceImpl(
                repo,
                mock(FileAssetsRepository.class),
                mock(FileAssetExtractionsRepository.class),
                mock(FileAssetExtractionAsyncService.class),
                new ObjectMapper()
        );

        assertEquals(Boolean.FALSE, ReflectionTestUtils.invokeMethod(AdminPostFilesServiceImpl.class, "isImageExt", (Object) null));
        assertEquals(Boolean.FALSE, ReflectionTestUtils.invokeMethod(AdminPostFilesServiceImpl.class, "isImageExt", " "));
        assertEquals(Boolean.TRUE, ReflectionTestUtils.invokeMethod(AdminPostFilesServiceImpl.class, "isImageExt", "bmp"));
        assertEquals(Boolean.TRUE, ReflectionTestUtils.invokeMethod(AdminPostFilesServiceImpl.class, "isImageExt", "png"));
        assertEquals(Boolean.TRUE, ReflectionTestUtils.invokeMethod(AdminPostFilesServiceImpl.class, "isImageExt", "jpg"));
        assertEquals(Boolean.TRUE, ReflectionTestUtils.invokeMethod(AdminPostFilesServiceImpl.class, "isImageExt", "jpeg"));
        assertEquals(Boolean.TRUE, ReflectionTestUtils.invokeMethod(AdminPostFilesServiceImpl.class, "isImageExt", "gif"));
        assertEquals(Boolean.TRUE, ReflectionTestUtils.invokeMethod(AdminPostFilesServiceImpl.class, "isImageExt", "webp"));
        assertEquals(Boolean.FALSE, ReflectionTestUtils.invokeMethod(AdminPostFilesServiceImpl.class, "isImageExt", "pdf"));

        assertNull(ReflectionTestUtils.invokeMethod(AdminPostFilesServiceImpl.class, "firstNonBlank", (Object) null));
        assertNull(ReflectionTestUtils.invokeMethod(AdminPostFilesServiceImpl.class, "firstNonBlank", (Object) new String[]{" ", "\t"}));
        assertEquals("x", ReflectionTestUtils.invokeMethod(AdminPostFilesServiceImpl.class, "firstNonBlank", (Object) new String[]{" ", "x", "y"}));

        assertNull(ReflectionTestUtils.invokeMethod(AdminPostFilesServiceImpl.class, "extLowerOrNull", "/a/b/"));
        assertNull(ReflectionTestUtils.invokeMethod(AdminPostFilesServiceImpl.class, "extLowerOrNull", "noext"));
        assertNull(ReflectionTestUtils.invokeMethod(AdminPostFilesServiceImpl.class, "extLowerOrNull", "a. "));
        assertNull(ReflectionTestUtils.invokeMethod(AdminPostFilesServiceImpl.class, "extLowerOrNull", "a. \t"));
        assertNull(ReflectionTestUtils.invokeMethod(AdminPostFilesServiceImpl.class, "extLowerOrNull", "a.\u2003"));

        Object weird = new Object() {
            @Override
            public String toString() {
                return null;
            }
        };
        Map<String, Object> weirdMap = new HashMap<>();
        weirdMap.put("x", weird);
        assertNull(ReflectionTestUtils.invokeMethod(AdminPostFilesServiceImpl.class, "getString", weirdMap, "x"));

        assertNull(ReflectionTestUtils.invokeMethod(svc, "tryParseJsonMap", " "));
        assertNull(ReflectionTestUtils.invokeMethod(svc, "tryParseJsonMap", "{bad}"));
        Object parsed = ReflectionTestUtils.invokeMethod(svc, "tryParseJsonMap", "{\"k\":1}");
        assertNotNull(parsed);

        PostAttachmentsRepository.AdminPostFileRow row = mock(PostAttachmentsRepository.AdminPostFileRow.class);
        when(row.getAttachmentId()).thenReturn(11L);
        when(row.getPostId()).thenReturn(12L);
        when(row.getFileAssetId()).thenReturn(13L);
        when(row.getAttachmentUrl()).thenReturn(null);
        when(row.getAssetUrl()).thenReturn("asset-url");
        when(row.getFileName()).thenReturn("name");
        when(row.getOriginalName()).thenReturn(null);
        when(row.getAssetMimeType()).thenReturn(null);
        when(row.getAttachmentMimeType()).thenReturn(null);
        when(row.getAssetSizeBytes()).thenReturn(null);
        when(row.getAttachmentSizeBytes()).thenReturn(3L);
        when(row.getExtractStatus()).thenReturn("DONE");
        when(row.getExtractionUpdatedAt()).thenReturn(LocalDateTime.now());
        when(row.getExtractionErrorMessage()).thenReturn("e");
        when(row.getExtractedMetadataJson()).thenReturn("{\"ext\":\"pdf\",\"mimeType\":\"application/pdf\",\"parseDurationMs\":1,\"pages\":\"2\",\"textCharCount\":\"5\",\"textTokenCount\":6,\"tokenCountMode\":\"mode\",\"imageCount\":\"4\"}");
        Object dto = ReflectionTestUtils.invokeMethod(svc, "toListItem", row);
        assertEquals("pdf", ReflectionTestUtils.getField(dto, "ext"));
        assertEquals("application/pdf", ReflectionTestUtils.getField(dto, "mimeType"));

        PostFileExtractionAdminDetailDTO d = new PostFileExtractionAdminDetailDTO();
        d.setOriginalName("x.txt");
        d.setFileName("x.txt");
        d.setExt("txt");
        d.setExtractedText(null);
        d.setExtractedImages(new ArrayList<>(Arrays.asList(
                null,
                Map.of("placeholder", "[p]"),
                Map.of("url", "https://u"),
                Map.of()
        )));
        String preview = (String) ReflectionTestUtils.invokeMethod(svc, "buildLlmInputPreview", d);
        assertTrue(preview.contains("[p]"));
        assertTrue(preview.contains("https://u"));

        PostFileExtractionAdminDetailDTO img = new PostFileExtractionAdminDetailDTO();
        img.setOriginalName("p.jpg");
        img.setFileName("p.jpg");
        img.setExt("jpg");
        img.setUrl(null);
        img.setSizeBytes(1L);
        img.setPages(2);
        img.setImageCount(3);
        img.setTextTokenCount(4L);
        img.setExtractedText("img-text");
        String previewImg = (String) ReflectionTestUtils.invokeMethod(svc, "buildLlmInputPreview", img);
        assertTrue(previewImg.contains("=== 图片引用 ==="));
        assertTrue(previewImg.contains("文本 tokens: 4"));

        PostFileExtractionAdminDetailDTO noImageList = new PostFileExtractionAdminDetailDTO();
        noImageList.setOriginalName("n.txt");
        noImageList.setFileName("n.txt");
        noImageList.setExt("txt");
        noImageList.setExtractedImages(List.of());
        String previewNoImageList = (String) ReflectionTestUtils.invokeMethod(svc, "buildLlmInputPreview", noImageList);
        assertTrue(previewNoImageList.contains("=== 抽取文本 ==="));

        PostFileExtractionAdminDetailDTO nullExt = new PostFileExtractionAdminDetailDTO();
        nullExt.setOriginalName("m");
        nullExt.setFileName("m");
        nullExt.setExt(null);
        String previewNullExt = (String) ReflectionTestUtils.invokeMethod(svc, "buildLlmInputPreview", nullExt);
        assertTrue(previewNullExt.contains("格式: -"));
    }

    private static void mockDetail(
            PostAttachmentsRepository.AdminPostFileDetailRow row,
            Long attachmentId,
            Long fileAssetId,
            String fileName,
            String originalName,
            String metaJson,
            String extractedText
    ) {
        when(row.getAttachmentId()).thenReturn(attachmentId);
        when(row.getPostId()).thenReturn(2L);
        when(row.getFileAssetId()).thenReturn(fileAssetId);
        when(row.getAttachmentUrl()).thenReturn("u");
        when(row.getAssetUrl()).thenReturn(" ");
        when(row.getFileName()).thenReturn(fileName);
        when(row.getOriginalName()).thenReturn(originalName);
        when(row.getAssetMimeType()).thenReturn(null);
        when(row.getAttachmentMimeType()).thenReturn("text/plain");
        when(row.getAssetSizeBytes()).thenReturn(100L);
        when(row.getAttachmentSizeBytes()).thenReturn(null);
        when(row.getExtractStatus()).thenReturn("DONE");
        when(row.getExtractionUpdatedAt()).thenReturn(LocalDateTime.now());
        when(row.getExtractionErrorMessage()).thenReturn(null);
        when(row.getExtractedMetadataJson()).thenReturn(metaJson);
        when(row.getExtractedText()).thenReturn(extractedText);
    }
}
