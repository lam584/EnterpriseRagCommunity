package com.example.EnterpriseRagCommunity.service.content.admin.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import com.example.EnterpriseRagCommunity.dto.content.admin.PostFileExtractionAdminDetailDTO;
import com.example.EnterpriseRagCommunity.dto.content.admin.PostFileExtractionAdminListItemDTO;
import com.example.EnterpriseRagCommunity.entity.monitor.FileAssetsEntity;
import com.example.EnterpriseRagCommunity.entity.monitor.FileAssetExtractionsEntity;
import com.example.EnterpriseRagCommunity.entity.monitor.enums.FileAssetExtractionStatus;
import com.example.EnterpriseRagCommunity.repository.content.PostAttachmentsRepository;
import com.example.EnterpriseRagCommunity.repository.monitor.FileAssetExtractionsRepository;
import com.example.EnterpriseRagCommunity.repository.monitor.FileAssetsRepository;
import com.example.EnterpriseRagCommunity.service.monitor.FileAssetExtractionAsyncService;
import com.fasterxml.jackson.databind.ObjectMapper;

class AdminPostFilesServiceImplTest {
    @Test
    void list_should_clamp_page_and_parse_meta() {
        PostAttachmentsRepository repo = mock(PostAttachmentsRepository.class);
        PostAttachmentsRepository.AdminPostFileRow row = mock(PostAttachmentsRepository.AdminPostFileRow.class);
        when(row.getAttachmentId()).thenReturn(1L);
        when(row.getPostId()).thenReturn(2L);
        when(row.getFileAssetId()).thenReturn(3L);
        when(row.getAttachmentUrl()).thenReturn(" ");
        when(row.getAssetUrl()).thenReturn("u");
        when(row.getFileName()).thenReturn("f.txt");
        when(row.getOriginalName()).thenReturn("O.TXT");
        when(row.getAssetMimeType()).thenReturn(null);
        when(row.getAttachmentMimeType()).thenReturn("text/plain");
        when(row.getAssetSizeBytes()).thenReturn(null);
        when(row.getAttachmentSizeBytes()).thenReturn(10L);
        when(row.getExtractStatus()).thenReturn(null);
        when(row.getExtractionUpdatedAt()).thenReturn(LocalDateTime.now());
        when(row.getExtractionErrorMessage()).thenReturn("e");
        when(row.getExtractedMetadataJson()).thenReturn("{\"parseDurationMs\":\"12\",\"pages\":3,\"textCharCount\":99,\"textTokenCount\":\"100\",\"tokenCountMode\":\"os\",\"imageCount\":1,\"ext\":\"pdf\",\"mimeType\":\"application/pdf\"}");

        when(repo.adminListPostFiles(any(), any(), any(), any(), any(PageRequest.class))).thenReturn(new PageImpl<>(java.util.List.of(row)));

        AdminPostFilesServiceImpl s = new AdminPostFilesServiceImpl(
                repo,
                mock(FileAssetsRepository.class),
                mock(FileAssetExtractionsRepository.class),
                mock(FileAssetExtractionAsyncService.class),
                new ObjectMapper()
        );

        var page = s.list(0, 999, null, null, null, null);
        PostFileExtractionAdminListItemDTO dto = page.getContent().get(0);
        assertEquals(1L, dto.getAttachmentId());
        assertEquals(2L, dto.getPostId());
        assertEquals(3L, dto.getFileAssetId());
        assertEquals("u", dto.getUrl());
        assertEquals("text/plain", dto.getMimeType());
        assertEquals(10L, dto.getSizeBytes());
        assertEquals("txt", dto.getExt());
        assertEquals("NONE", dto.getExtractStatus());
        assertEquals(12L, dto.getParseDurationMs());
        assertEquals(3, dto.getPages());
        assertEquals(99L, dto.getTextCharCount());
        assertEquals(100L, dto.getTextTokenCount());
        assertEquals("os", dto.getTokenCountMode());
        assertEquals(1, dto.getImageCount());
    }

    @Test
    void detail_should_validate_and_build_preview() {
        PostAttachmentsRepository repo = mock(PostAttachmentsRepository.class);
        assertThrows(IllegalArgumentException.class, () -> new AdminPostFilesServiceImpl(
                repo,
                mock(FileAssetsRepository.class),
                mock(FileAssetExtractionsRepository.class),
                mock(FileAssetExtractionAsyncService.class),
                new ObjectMapper()
        ).detail(null));

        when(repo.adminGetPostFileDetail(1L)).thenReturn(Optional.empty());
        AdminPostFilesServiceImpl s = new AdminPostFilesServiceImpl(
                repo,
                mock(FileAssetsRepository.class),
                mock(FileAssetExtractionsRepository.class),
                mock(FileAssetExtractionAsyncService.class),
                new ObjectMapper()
        );
        assertThrows(IllegalArgumentException.class, () -> s.detail(1L));
    }

    @Test
    void detail_should_include_image_reference_for_image_ext() {
        PostAttachmentsRepository repo = mock(PostAttachmentsRepository.class);
        PostAttachmentsRepository.AdminPostFileDetailRow row = mock(PostAttachmentsRepository.AdminPostFileDetailRow.class);
        when(row.getAttachmentId()).thenReturn(1L);
        when(row.getPostId()).thenReturn(2L);
        when(row.getFileAssetId()).thenReturn(3L);
        when(row.getAttachmentUrl()).thenReturn("u");
        when(row.getAssetUrl()).thenReturn(" ");
        when(row.getFileName()).thenReturn("a.jpg");
        when(row.getOriginalName()).thenReturn("a.jpg");
        when(row.getAssetMimeType()).thenReturn("image/jpeg");
        when(row.getAttachmentMimeType()).thenReturn(null);
        when(row.getAssetSizeBytes()).thenReturn(10L);
        when(row.getAttachmentSizeBytes()).thenReturn(null);
        when(row.getExtractStatus()).thenReturn("DONE");
        when(row.getExtractionUpdatedAt()).thenReturn(LocalDateTime.now());
        when(row.getExtractionErrorMessage()).thenReturn(null);
        when(row.getExtractedMetadataJson()).thenReturn(null);
        when(row.getExtractedText()).thenReturn("t");
        when(repo.adminGetPostFileDetail(1L)).thenReturn(Optional.of(row));

        AdminPostFilesServiceImpl s = new AdminPostFilesServiceImpl(
                repo,
                mock(FileAssetsRepository.class),
                mock(FileAssetExtractionsRepository.class),
                mock(FileAssetExtractionAsyncService.class),
                new ObjectMapper()
        );

        PostFileExtractionAdminDetailDTO dto = s.detail(1L);
        assertNotNull(dto.getLlmInputPreview());
        assertTrue(dto.getLlmInputPreview().contains("=== 图片引用 ==="));
        assertTrue(dto.getLlmInputPreview().contains("u"));
    }

    @Test
    void reextract_should_create_pending_extraction_and_call_async() {
        PostAttachmentsRepository repo = mock(PostAttachmentsRepository.class);
        PostAttachmentsRepository.AdminPostFileDetailRow row = mock(PostAttachmentsRepository.AdminPostFileDetailRow.class);
        when(row.getAttachmentId()).thenReturn(1L);
        when(row.getPostId()).thenReturn(2L);
        when(row.getFileAssetId()).thenReturn(3L);
        when(row.getAttachmentUrl()).thenReturn("u");
        when(row.getAssetUrl()).thenReturn(" ");
        when(row.getFileName()).thenReturn("a.txt");
        when(row.getOriginalName()).thenReturn("a.txt");
        when(row.getAssetMimeType()).thenReturn("text/plain");
        when(row.getAttachmentMimeType()).thenReturn(null);
        when(row.getAssetSizeBytes()).thenReturn(10L);
        when(row.getAttachmentSizeBytes()).thenReturn(null);
        when(row.getExtractStatus()).thenReturn("DONE");
        when(row.getExtractionUpdatedAt()).thenReturn(LocalDateTime.now());
        when(row.getExtractionErrorMessage()).thenReturn(null);
        when(row.getExtractedMetadataJson()).thenReturn(null);
        when(row.getExtractedText()).thenReturn("t");
        when(repo.adminGetPostFileDetail(1L)).thenReturn(Optional.of(row));

        FileAssetsRepository fileAssetsRepository = mock(FileAssetsRepository.class);
        when(fileAssetsRepository.findById(3L)).thenReturn(Optional.of(new FileAssetsEntity()));

        FileAssetExtractionsRepository extractionsRepository = mock(FileAssetExtractionsRepository.class);
        when(extractionsRepository.findById(3L)).thenReturn(Optional.empty());
        when(extractionsRepository.save(any(FileAssetExtractionsEntity.class))).thenAnswer(inv -> inv.getArgument(0));

        FileAssetExtractionAsyncService async = mock(FileAssetExtractionAsyncService.class);

        AdminPostFilesServiceImpl s = new AdminPostFilesServiceImpl(
                repo,
                fileAssetsRepository,
                extractionsRepository,
                async,
                new ObjectMapper()
        );

        PostFileExtractionAdminDetailDTO dto = s.reextract(1L);
        assertNotNull(dto);
        ArgumentCaptor<FileAssetExtractionsEntity> cap = ArgumentCaptor.forClass(FileAssetExtractionsEntity.class);
        verify(extractionsRepository).save(cap.capture());
        assertEquals(3L, cap.getValue().getFileAssetId());
        assertEquals(FileAssetExtractionStatus.PENDING, cap.getValue().getExtractStatus());
        verify(async).extractAsync(3L);
    }
}

