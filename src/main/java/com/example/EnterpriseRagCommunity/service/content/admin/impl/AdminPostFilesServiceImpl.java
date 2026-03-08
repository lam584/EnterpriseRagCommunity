package com.example.EnterpriseRagCommunity.service.content.admin.impl;

import com.example.EnterpriseRagCommunity.dto.content.admin.PostFileExtractionAdminDetailDTO;
import com.example.EnterpriseRagCommunity.dto.content.admin.PostFileExtractionAdminListItemDTO;
import com.example.EnterpriseRagCommunity.entity.monitor.FileAssetExtractionsEntity;
import com.example.EnterpriseRagCommunity.entity.monitor.enums.FileAssetExtractionStatus;
import com.example.EnterpriseRagCommunity.repository.content.PostAttachmentsRepository;
import com.example.EnterpriseRagCommunity.repository.monitor.FileAssetExtractionsRepository;
import com.example.EnterpriseRagCommunity.repository.monitor.FileAssetsRepository;
import com.example.EnterpriseRagCommunity.service.content.admin.AdminPostFilesService;
import com.example.EnterpriseRagCommunity.service.monitor.FileAssetExtractionAsyncService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class AdminPostFilesServiceImpl implements AdminPostFilesService {

    private final PostAttachmentsRepository postAttachmentsRepository;
    private final FileAssetsRepository fileAssetsRepository;
    private final FileAssetExtractionsRepository fileAssetExtractionsRepository;
    private final FileAssetExtractionAsyncService fileAssetExtractionAsyncService;
    private final ObjectMapper objectMapper;

    @Override
    @Transactional(readOnly = true)
    public Page<PostFileExtractionAdminListItemDTO> list(int page, int pageSize, Long postId, Long fileAssetId, String keyword, String extractStatus) {
        int p = Math.max(1, page);
        int ps = clamp(pageSize, 1, 200, 20);
        Pageable pageable = PageRequest.of(p - 1, ps);
        Page<PostAttachmentsRepository.AdminPostFileRow> rows = postAttachmentsRepository.adminListPostFiles(postId, fileAssetId, keyword, extractStatus, pageable);
        return rows.map(this::toListItem);
    }

    @Override
    @Transactional(readOnly = true)
    public PostFileExtractionAdminDetailDTO detail(Long attachmentId) {
        if (attachmentId == null) throw new IllegalArgumentException("attachmentId is required");
        var row = postAttachmentsRepository.adminGetPostFileDetail(attachmentId).orElse(null);
        if (row == null) throw new IllegalArgumentException("附件不存在");
        PostFileExtractionAdminDetailDTO dto = toDetail(row);
        dto.setLlmInputPreview(buildLlmInputPreview(dto));
        return dto;
    }

    @Override
    @Transactional
    public PostFileExtractionAdminDetailDTO reextract(Long attachmentId) {
        if (attachmentId == null) throw new IllegalArgumentException("attachmentId is required");
        var row = postAttachmentsRepository.adminGetPostFileDetail(attachmentId).orElse(null);
        if (row == null) throw new IllegalArgumentException("附件不存在");
        Long fileAssetId = row.getFileAssetId();
        if (fileAssetId == null) throw new IllegalArgumentException("附件未关联 fileAssetId");
        if (fileAssetsRepository.findById(fileAssetId).orElse(null) == null) throw new IllegalArgumentException("文件资产不存在");

        FileAssetExtractionsEntity e = fileAssetExtractionsRepository.findById(fileAssetId).orElse(null);
        if (e == null) {
            e = new FileAssetExtractionsEntity();
            e.setFileAssetId(fileAssetId);
        }
        e.setExtractStatus(FileAssetExtractionStatus.PENDING);
        e.setExtractedText(null);
        e.setExtractedMetadataJson(null);
        e.setErrorMessage(null);
        fileAssetExtractionsRepository.save(e);
        fileAssetExtractionAsyncService.extractAsync(fileAssetId);

        return detail(attachmentId);
    }

    private PostFileExtractionAdminListItemDTO toListItem(PostAttachmentsRepository.AdminPostFileRow r) {
        PostFileExtractionAdminListItemDTO dto = new PostFileExtractionAdminListItemDTO();
        dto.setAttachmentId(r.getAttachmentId());
        dto.setPostId(r.getPostId());
        dto.setFileAssetId(r.getFileAssetId());
        dto.setUrl(firstNonBlank(r.getAttachmentUrl(), r.getAssetUrl()));
        dto.setFileName(r.getFileName());
        dto.setOriginalName(r.getOriginalName());
        dto.setMimeType(firstNonBlank(r.getAssetMimeType(), r.getAttachmentMimeType()));
        dto.setSizeBytes(firstNonNull(r.getAssetSizeBytes(), r.getAttachmentSizeBytes()));
        dto.setExt(extLowerOrNull(firstNonBlank(r.getOriginalName(), r.getFileName())));

        dto.setExtractStatus(r.getExtractStatus() == null ? "NONE" : r.getExtractStatus());
        dto.setExtractionUpdatedAt(r.getExtractionUpdatedAt());
        dto.setExtractionErrorMessage(r.getExtractionErrorMessage());

        String metaJson = r.getExtractedMetadataJson();
        Map<String, Object> meta = tryParseJsonMap(metaJson);
        if (meta != null) {
            dto.setParseDurationMs(getLong(meta, "parseDurationMs"));
            dto.setPages(getInt(meta, "pages"));
            dto.setTextCharCount(getLong(meta, "textCharCount"));
            dto.setTextTokenCount(getLong(meta, "textTokenCount"));
            dto.setTokenCountMode(getString(meta, "tokenCountMode"));
            dto.setImageCount(getInt(meta, "imageCount"));
            if (dto.getExt() == null) dto.setExt(getString(meta, "ext"));
            if (dto.getMimeType() == null) dto.setMimeType(getString(meta, "mimeType"));
        }
        return dto;
    }

    private PostFileExtractionAdminDetailDTO toDetail(PostAttachmentsRepository.AdminPostFileDetailRow r) {
        PostFileExtractionAdminDetailDTO dto = new PostFileExtractionAdminDetailDTO();
        PostFileExtractionAdminListItemDTO base = toListItem(r);
        dto.setAttachmentId(base.getAttachmentId());
        dto.setPostId(base.getPostId());
        dto.setFileAssetId(base.getFileAssetId());
        dto.setUrl(base.getUrl());
        dto.setFileName(base.getFileName());
        dto.setOriginalName(base.getOriginalName());
        dto.setMimeType(base.getMimeType());
        dto.setSizeBytes(base.getSizeBytes());
        dto.setExt(base.getExt());
        dto.setExtractStatus(base.getExtractStatus());
        dto.setExtractionUpdatedAt(base.getExtractionUpdatedAt());
        dto.setExtractionErrorMessage(base.getExtractionErrorMessage());
        dto.setParseDurationMs(base.getParseDurationMs());
        dto.setPages(base.getPages());
        dto.setTextCharCount(base.getTextCharCount());
        dto.setTextTokenCount(base.getTextTokenCount());
        dto.setTokenCountMode(base.getTokenCountMode());
        dto.setImageCount(base.getImageCount());

        dto.setExtractedText(r.getExtractedText());
        dto.setExtractedMetadataJson(r.getExtractedMetadataJson());
        Map<String, Object> meta = tryParseJsonMap(r.getExtractedMetadataJson());
        dto.setExtractedMetadata(meta);
        dto.setExtractedImages(tryGetImages(meta));
        return dto;
    }

    private String buildLlmInputPreview(PostFileExtractionAdminDetailDTO dto) {
        StringBuilder sb = new StringBuilder();
        sb.append("文件名: ").append(firstNonBlank(dto.getOriginalName(), dto.getFileName(), "-")).append("\n");
        sb.append("格式: ").append(firstNonBlank(dto.getExt(), "-")).append("\n");
        if (dto.getSizeBytes() != null) sb.append("大小: ").append(dto.getSizeBytes()).append(" bytes\n");
        if (dto.getPages() != null) sb.append("页数: ").append(dto.getPages()).append("\n");
        if (dto.getImageCount() != null) sb.append("图片数: ").append(dto.getImageCount()).append("\n");
        if (dto.getTextTokenCount() != null) sb.append("文本 tokens: ").append(dto.getTextTokenCount()).append("\n");
        sb.append("\n");
        sb.append("=== 抽取文本 ===\n");
        sb.append(dto.getExtractedText() == null ? "" : dto.getExtractedText());
        sb.append("\n");

        String ext = dto.getExt() == null ? "" : dto.getExt().toLowerCase(Locale.ROOT);
        if (isImageExt(ext)) {
            sb.append("\n=== 图片引用 ===\n");
            sb.append(dto.getUrl() == null ? "" : dto.getUrl());
            sb.append("\n");
            return sb.toString();
        }

        List<Map<String, Object>> images = dto.getExtractedImages();
        if (images != null && !images.isEmpty()) {
            sb.append("\n=== 图片引用 ===\n");
            for (Map<String, Object> it : images) {
                if (it == null) continue;
                String p = getString(it, "placeholder");
                String u = getString(it, "url");
                if (p != null) sb.append(p).append(' ');
                if (u != null) sb.append(u);
                sb.append("\n");
            }
        }
        return sb.toString();
    }

    private static boolean isImageExt(String ext) {
        if (ext == null || ext.isBlank()) return false;
        return ext.equals("bmp") || ext.equals("png") || ext.equals("jpg") || ext.equals("jpeg") || ext.equals("gif") || ext.equals("webp");
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> tryGetImages(Map<String, Object> meta) {
        if (meta == null) return null;
        Object v = meta.get("extractedImages");
        if (v instanceof List<?> list) {
            try {
                return (List<Map<String, Object>>) v;
            } catch (Exception ignored) {
                return null;
            }
        }
        return null;
    }

    private Map<String, Object> tryParseJsonMap(String json) {
        if (json == null || json.isBlank()) return null;
        try {
            return objectMapper.readValue(json, new TypeReference<Map<String, Object>>() {
            });
        } catch (Exception ignored) {
            return null;
        }
    }

    private static Long getLong(Map<String, Object> m, String k) {
        Object v = m == null ? null : m.get(k);
        if (v == null) return null;
        if (v instanceof Number n) return n.longValue();
        try {
            String s = v.toString().trim();
            if (s.isBlank()) return null;
            return Long.parseLong(s);
        } catch (Exception ignored) {
            return null;
        }
    }

    private static Integer getInt(Map<String, Object> m, String k) {
        Object v = m == null ? null : m.get(k);
        if (v == null) return null;
        if (v instanceof Number n) return n.intValue();
        try {
            String s = v.toString().trim();
            if (s.isBlank()) return null;
            return Integer.parseInt(s);
        } catch (Exception ignored) {
            return null;
        }
    }

    private static String getString(Map<String, Object> m, String k) {
        Object v = m == null ? null : m.get(k);
        if (v == null) return null;
        String s = v.toString();
        return s == null || s.isBlank() ? null : s;
    }

    private static <T> T firstNonNull(T a, T b) {
        return a != null ? a : b;
    }

    private static String firstNonBlank(String... xs) {
        if (xs == null) return null;
        for (String s : xs) {
            if (s != null && !s.isBlank()) return s;
        }
        return null;
    }

    private static int clamp(Integer v, int min, int max, int def) {
        int x = v == null ? def : v;
        if (x < min) x = min;
        if (x > max) x = max;
        return x;
    }

    private static String extLowerOrNull(String fileName) {
        if (fileName == null || fileName.isBlank()) return null;
        String name = fileName.trim();
        int slash = Math.max(name.lastIndexOf('/'), name.lastIndexOf('\\'));
        if (slash >= 0 && slash < name.length() - 1) name = name.substring(slash + 1);
        int idx = name.lastIndexOf('.');
        if (idx < 0 || idx == name.length() - 1) return null;
        String ext = name.substring(idx + 1).trim().toLowerCase(Locale.ROOT);
        if (ext.isBlank()) return null;
        if (!ext.matches("[a-z0-9]+")) return null;
        if (ext.length() > 16) return null;
        return ext;
    }
}
