package com.example.EnterpriseRagCommunity.service.moderation.admin;

import com.example.EnterpriseRagCommunity.dto.moderation.AdminModerationChunkContentPreviewDTO;
import com.example.EnterpriseRagCommunity.dto.moderation.AdminModerationChunkLogDetailDTO;
import com.example.EnterpriseRagCommunity.dto.moderation.AdminModerationChunkLogItemDTO;
import com.example.EnterpriseRagCommunity.entity.moderation.ModerationChunkEntity;
import com.example.EnterpriseRagCommunity.entity.moderation.ModerationChunkSetEntity;
import com.example.EnterpriseRagCommunity.entity.moderation.enums.ChunkSourceType;
import com.example.EnterpriseRagCommunity.entity.moderation.enums.ChunkStatus;
import com.example.EnterpriseRagCommunity.entity.moderation.enums.ContentType;
import com.example.EnterpriseRagCommunity.entity.moderation.enums.Verdict;
import com.example.EnterpriseRagCommunity.entity.monitor.FileAssetExtractionsEntity;
import com.example.EnterpriseRagCommunity.entity.content.PostAttachmentsEntity;
import com.example.EnterpriseRagCommunity.exception.ResourceNotFoundException;
import com.example.EnterpriseRagCommunity.repository.content.PostAttachmentsRepository;
import com.example.EnterpriseRagCommunity.repository.moderation.ModerationChunkRepository;
import com.example.EnterpriseRagCommunity.repository.moderation.ModerationChunkSetRepository;
import com.example.EnterpriseRagCommunity.repository.monitor.FileAssetExtractionsRepository;
import com.example.EnterpriseRagCommunity.service.moderation.ModerationChunkReviewService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
public class AdminModerationChunkReviewLogsService {
    private final ModerationChunkRepository chunkRepository;
    private final ModerationChunkSetRepository chunkSetRepository;
    private final ModerationChunkReviewService chunkReviewService;
    private final FileAssetExtractionsRepository fileAssetExtractionsRepository;
    private final PostAttachmentsRepository postAttachmentsRepository;
    private final ObjectMapper objectMapper;

    private static final Pattern IMAGE_PLACEHOLDER = Pattern.compile("\\[\\[IMAGE_(\\d+)]]");

    private static AdminModerationChunkLogItemDTO toItemDTO(ModerationChunkEntity c, ModerationChunkSetEntity s) {
        AdminModerationChunkLogItemDTO dto = new AdminModerationChunkLogItemDTO();
        dto.setId(c.getId());
        dto.setChunkSetId(c.getChunkSetId());
        if (s != null) {
            dto.setQueueId(s.getQueueId());
            dto.setCaseType(enumName(s.getCaseType()));
            dto.setContentType(enumName(s.getContentType()));
            dto.setContentId(s.getContentId());
        }

        dto.setSourceType(enumName(c.getSourceType()));
        dto.setSourceKey(c.getSourceKey());
        dto.setFileAssetId(c.getFileAssetId());
        dto.setFileName(c.getFileName());
        dto.setChunkIndex(c.getChunkIndex());
        dto.setStartOffset(c.getStartOffset());
        dto.setEndOffset(c.getEndOffset());

        dto.setStatus(enumName(c.getStatus()));
        dto.setVerdict(enumName(c.getVerdict()));
        dto.setConfidence(toDoubleOrNull(c.getConfidence()));
        dto.setAttempts(c.getAttempts());
        dto.setLastError(c.getLastError());
        dto.setModel(c.getModel());
        dto.setTokensIn(c.getTokensIn());
        dto.setTokensOut(c.getTokensOut());
        if (s != null) {
            dto.setBudgetConvergenceLog(extractBudgetConvergenceLog(s.getConfigJson()));
        }

        dto.setDecidedAt(c.getDecidedAt());
        dto.setCreatedAt(c.getCreatedAt());
        dto.setUpdatedAt(c.getUpdatedAt());
        return dto;
    }

    public AdminModerationChunkLogDetailDTO getDetail(long chunkId) {
        ModerationChunkEntity c = chunkRepository.findById(chunkId)
                .orElseThrow(() -> new ResourceNotFoundException("分片记录不存在"));
        ModerationChunkSetEntity s = c.getChunkSetId() == null ? null : chunkSetRepository.findById(c.getChunkSetId()).orElse(null);
        if (s == null) {
            throw new ResourceNotFoundException("分片集合不存在");
        }

        AdminModerationChunkLogDetailDTO dto = new AdminModerationChunkLogDetailDTO();
        dto.setChunk(toChunkDetailDTO(c, s));
        dto.setChunkSet(toChunkSetDetailDTO(s));
        return dto;
    }

    private static AdminModerationChunkLogDetailDTO.Chunk toChunkDetailDTO(ModerationChunkEntity c, ModerationChunkSetEntity s) {
        AdminModerationChunkLogDetailDTO.Chunk dto = new AdminModerationChunkLogDetailDTO.Chunk();
        dto.setId(c.getId());
        dto.setChunkSetId(c.getChunkSetId());
        dto.setQueueId(s.getQueueId());
        dto.setCaseType(enumName(s.getCaseType()));
        dto.setContentType(enumName(s.getContentType()));
        dto.setContentId(s.getContentId());

        dto.setSourceType(enumName(c.getSourceType()));
        dto.setSourceKey(c.getSourceKey());
        dto.setFileAssetId(c.getFileAssetId());
        dto.setFileName(c.getFileName());
        dto.setChunkIndex(c.getChunkIndex());
        dto.setStartOffset(c.getStartOffset());
        dto.setEndOffset(c.getEndOffset());

        dto.setStatus(enumName(c.getStatus()));
        dto.setAttempts(c.getAttempts());
        dto.setLastError(c.getLastError());
        dto.setModel(c.getModel());
        dto.setVerdict(enumName(c.getVerdict()));
        dto.setConfidence(toDoubleOrNull(c.getConfidence()));
        dto.setLabels(c.getLabels());
        dto.setTokensIn(c.getTokensIn());
        dto.setTokensOut(c.getTokensOut());

        dto.setDecidedAt(c.getDecidedAt());
        dto.setCreatedAt(c.getCreatedAt());
        dto.setUpdatedAt(c.getUpdatedAt());
        return dto;
    }

    private List<AdminModerationChunkContentPreviewDTO.Image> resolveFileTextImages(String text, Long fileAssetId) {
        if (fileAssetId == null) return List.of();
        FileAssetExtractionsEntity ex = fileAssetExtractionsRepository.findById(fileAssetId).orElse(null);
        if (ex == null) return List.of();
        String metaJson = ex.getExtractedMetadataJson();
        if (metaJson == null || metaJson.isBlank()) return List.of();

        Map meta;
        try {
            meta = objectMapper.readValue(metaJson, Map.class);
        } catch (Exception ignore) {
            return List.of();
        }
        Object listObj = meta.get("extractedImages");
        if (!(listObj instanceof List<?> list) || list.isEmpty()) return List.of();

        Set<Integer> used = parseUsedImageIndices(text);
        if (used.isEmpty()) return List.of();

        List<AdminModerationChunkContentPreviewDTO.Image> out = new ArrayList<>();
        for (Object it : list) {
            if (!(it instanceof Map<?, ?> m)) continue;
            AdminModerationChunkContentPreviewDTO.Image img = new AdminModerationChunkContentPreviewDTO.Image();
            Integer idx = toInt(m.get("index"));
            String placeholder = toStr(m.get("placeholder"));
            if (idx == null && placeholder != null) idx = parseImageIndexFromPlaceholder(placeholder);
            if (idx == null || !used.contains(idx)) continue;
            img.setIndex(idx);
            img.setPlaceholder(placeholder);
            img.setUrl(toStr(m.get("url")));
            img.setMimeType(toStr(m.get("mimeType")));
            img.setFileName(toStr(m.get("fileName")));
            img.setSizeBytes(toLong(m.get("sizeBytes")));
            out.add(img);
        }
        out.sort(Comparator.comparing(AdminModerationChunkContentPreviewDTO.Image::getIndex, Comparator.nullsLast(Integer::compareTo)));
        return out;
    }

    private static AdminModerationChunkLogDetailDTO.ChunkSet toChunkSetDetailDTO(ModerationChunkSetEntity s) {
        AdminModerationChunkLogDetailDTO.ChunkSet dto = new AdminModerationChunkLogDetailDTO.ChunkSet();
        dto.setId(s.getId());
        dto.setQueueId(s.getQueueId());
        dto.setCaseType(enumName(s.getCaseType()));
        dto.setContentType(enumName(s.getContentType()));
        dto.setContentId(s.getContentId());
        dto.setStatus(enumName(s.getStatus()));

        dto.setChunkThresholdChars(s.getChunkThresholdChars());
        dto.setChunkSizeChars(s.getChunkSizeChars());
        dto.setOverlapChars(s.getOverlapChars());

        dto.setTotalChunks(s.getTotalChunks());
        dto.setCompletedChunks(s.getCompletedChunks());
        dto.setFailedChunks(s.getFailedChunks());

        dto.setConfigJson(s.getConfigJson());
        dto.setMemoryJson(s.getMemoryJson());

        dto.setCreatedAt(s.getCreatedAt());
        dto.setUpdatedAt(s.getUpdatedAt());
        return dto;
    }

    private static Set<Integer> parseUsedImageIndices(String text) {
        String t = text == null ? "" : text;
        Matcher m = IMAGE_PLACEHOLDER.matcher(t);
        Set<Integer> out = new LinkedHashSet<>();
        while (m.find()) {
            Integer idx = toInt(m.group(1));
            if (idx != null) out.add(idx);
        }
        return out;
    }

    private static Integer parseImageIndexFromPlaceholder(String placeholder) {
        if (placeholder == null) return null;
        Matcher m = IMAGE_PLACEHOLDER.matcher(placeholder);
        if (!m.find()) return null;
        return toInt(m.group(1));
    }

    private static int safeOffset(Integer v) {
        if (v == null) return 0;
        int n = v;
        return Math.max(0, n);
    }

    private static Integer toInt(Object v) {
        if (v == null) return null;
        if (v instanceof Integer i) return i;
        if (v instanceof Long l) return (int) Math.min(Integer.MAX_VALUE, Math.max(Integer.MIN_VALUE, l));
        if (v instanceof Number n) return n.intValue();
        try {
            String s = String.valueOf(v).trim();
            if (s.isEmpty()) return null;
            return Integer.parseInt(s);
        } catch (Exception ignore) {
            return null;
        }
    }

    private static Long toLong(Object v) {
        if (v == null) return null;
        if (v instanceof Long l) return l;
        if (v instanceof Integer i) return i.longValue();
        if (v instanceof Number n) return n.longValue();
        try {
            String s = String.valueOf(v).trim();
            if (s.isEmpty()) return null;
            return Long.parseLong(s);
        } catch (Exception ignore) {
            return null;
        }
    }

    private static String toStr(Object v) {
        if (v == null) return null;
        String s = String.valueOf(v);
        return s.isBlank() ? null : s;
    }

    private static String enumName(Enum<?> value) {
        return value == null ? null : value.name();
    }

    public List<AdminModerationChunkLogItemDTO> listRecent(int limit,
                                                           Long queueId,
                                                           ChunkStatus status,
                                                           Verdict verdict,
                                                           ChunkSourceType sourceType,
                                                           Long fileAssetId,
                                                           String keyword) {
        int lim = Math.clamp(limit, 1, 200);
        String kw = keyword == null ? null : keyword.trim();
        if (kw != null && kw.isBlank()) kw = null;

        List<ModerationChunkEntity> chunks = chunkRepository.findRecentForAdmin(
                queueId,
                status,
                verdict,
                sourceType,
                fileAssetId,
                kw,
                PageRequest.of(0, lim)
        );

        Set<Long> setIds = new LinkedHashSet<>();
        for (ModerationChunkEntity c : chunks) {
            if (c == null || c.getChunkSetId() == null) continue;
            setIds.add(c.getChunkSetId());
        }

        Map<Long, ModerationChunkSetEntity> setMap = new HashMap<>();
        if (!setIds.isEmpty()) {
            for (ModerationChunkSetEntity s : chunkSetRepository.findAllById(setIds)) {
                if (s == null || s.getId() == null) continue;
                setMap.put(s.getId(), s);
            }
        }

        List<AdminModerationChunkLogItemDTO> out = new ArrayList<>();
        for (ModerationChunkEntity c : chunks) {
            if (c == null) continue;
            ModerationChunkSetEntity s = c.getChunkSetId() == null ? null : setMap.get(c.getChunkSetId());
            out.add(toItemDTO(c, s));
        }
        return out;
    }

    public AdminModerationChunkContentPreviewDTO getContentPreview(long chunkId) {
        ModerationChunkEntity c = chunkRepository.findById(chunkId)
                .orElseThrow(() -> new ResourceNotFoundException("分片记录不存在"));
        ModerationChunkSetEntity s = c.getChunkSetId() == null ? null : chunkSetRepository.findById(c.getChunkSetId()).orElse(null);
        if (s == null) {
            throw new ResourceNotFoundException("分片集合不存在");
        }

        AdminModerationChunkContentPreviewDTO dto = new AdminModerationChunkContentPreviewDTO();
        AdminModerationChunkContentPreviewDTO.Source src = new AdminModerationChunkContentPreviewDTO.Source();
        src.setChunkId(c.getId());
        src.setQueueId(s.getQueueId());
        src.setContentType(enumName(s.getContentType()));
        src.setContentId(s.getContentId());
        src.setSourceType(enumName(c.getSourceType()));
        src.setFileAssetId(c.getFileAssetId());
        src.setStartOffset(c.getStartOffset());
        src.setEndOffset(c.getEndOffset());
        dto.setSource(src);
        dto.setText("");

        if (c.getSourceType() == null) {
            dto.setReason("缺少 sourceType");
            return dto;
        }

        int start = safeOffset(c.getStartOffset());
        int end = safeOffset(c.getEndOffset());
        if (end < start) {
            int tmp = start;
            start = end;
            end = tmp;
        }

        Optional<String> text = chunkReviewService.loadChunkText(s.getQueueId(), c.getSourceType(), c.getFileAssetId(), start, end);
        if (text.isPresent()) dto.setText(text.get());
        else dto.setReason("无法定位来源文本");

        if (c.getSourceType() == ChunkSourceType.FILE_TEXT) {
            dto.setImages(resolveFileTextImages(dto.getText(), c.getFileAssetId()));
            if ((dto.getReason() == null || dto.getReason().isBlank()) && c.getFileAssetId() == null)
                dto.setReason("缺少 fileAssetId");
        } else if (c.getSourceType() == ChunkSourceType.POST_TEXT) {
            dto.setImages(resolvePostTextImages(s));
        }

        return dto;
    }

    private static Double toDoubleOrNull(BigDecimal v) {
        if (v == null) return null;
        return v.doubleValue();
    }

    private List<AdminModerationChunkContentPreviewDTO.Image> resolvePostTextImages(ModerationChunkSetEntity s) {
        if (s == null || s.getContentType() != ContentType.POST || s.getContentId() == null) return List.of();
        List<PostAttachmentsEntity> atts = postAttachmentsRepository.findByPostId(s.getContentId(), PageRequest.of(0, 200)).getContent();
        if (atts.isEmpty()) return List.of();
        atts = new ArrayList<>(atts);
        List<AdminModerationChunkContentPreviewDTO.Image> out = new ArrayList<>();
        atts.sort(Comparator.nullsLast(Comparator.comparing(PostAttachmentsEntity::getId, Comparator.nullsLast(Long::compareTo))));
        int idx = 0;
        for (PostAttachmentsEntity a : atts) {
            if (a == null || a.getFileAsset() == null) continue;
            String mt = a.getFileAsset().getMimeType() == null ? "" : a.getFileAsset().getMimeType().trim().toLowerCase(Locale.ROOT);
            if (!mt.startsWith("image/")) continue;
            idx += 1;
            out.add(toPostImageDto(a, idx));
        }
        return out;
    }

    private static AdminModerationChunkContentPreviewDTO.Image toPostImageDto(PostAttachmentsEntity a, int idx) {
        AdminModerationChunkContentPreviewDTO.Image img = new AdminModerationChunkContentPreviewDTO.Image();
        img.setIndex(idx);
        img.setPlaceholder("[[IMAGE_" + idx + "]]");
        img.setUrl(a.getFileAsset().getUrl());
        img.setMimeType(a.getFileAsset().getMimeType());
        img.setFileName(a.getFileAsset().getOriginalName());
        img.setSizeBytes(a.getFileAsset().getSizeBytes());
        img.setFileAssetId(a.getFileAssetId());
        img.setWidth(a.getWidth());
        img.setHeight(a.getHeight());
        return img;
    }

    private static Map<String, Object> extractBudgetConvergenceLog(Map<String, Object> configJson) {
        if (configJson == null || configJson.isEmpty()) return null;
        Object v = configJson.get("budgetConvergenceLog");
        if (v instanceof Map<?, ?> m) {
            Map<String, Object> out = new LinkedHashMap<>();
            for (Map.Entry<?, ?> en : m.entrySet()) {
                if (en == null || en.getKey() == null) continue;
                out.put(String.valueOf(en.getKey()), en.getValue());
            }
            return out.isEmpty() ? null : out;
        }
        return null;
    }
}
