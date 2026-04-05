package com.example.EnterpriseRagCommunity.service.moderation.admin;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.IIOImage;
import javax.imageio.stream.MemoryCacheImageOutputStream;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Component;

import com.example.EnterpriseRagCommunity.dto.moderation.LlmModerationTestRequest;
import com.example.EnterpriseRagCommunity.entity.moderation.ModerationQueueEntity;
import com.example.EnterpriseRagCommunity.entity.moderation.enums.ContentType;
import com.example.EnterpriseRagCommunity.repository.content.PostAttachmentsRepository;
import com.example.EnterpriseRagCommunity.repository.moderation.ModerationQueueRepository;
import com.example.EnterpriseRagCommunity.repository.monitor.FileAssetExtractionsRepository;
import com.example.EnterpriseRagCommunity.repository.monitor.FileAssetsRepository;
import com.example.EnterpriseRagCommunity.service.ai.ImageResizeSupport;
import com.example.EnterpriseRagCommunity.service.ai.LocalUploadFileSupport;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
class AdminModerationLlmImageSupport {

    private static final int MAX_UPSTREAM_DATA_URL_CHARS = 250_000;
    private static final int MAX_UPSTREAM_IMAGE_BYTES = 180_000;

    private final ModerationQueueRepository queueRepository;
    private final PostAttachmentsRepository postAttachmentsRepository;
    private final FileAssetsRepository fileAssetsRepository;
    private final FileAssetExtractionsRepository fileAssetExtractionsRepository;
    private final com.example.EnterpriseRagCommunity.service.ai.LlmImageUploadService llmImageUploadService;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Value("${app.upload.root:uploads}")
    private String uploadRoot;

    @Value("${app.upload.url-prefix:/uploads}")
    private String urlPrefix;

    int clampVisionMaxImages(Integer v) {
        int x = v == null ? 10 : v;
        if (x < 1) x = 1;
        if (x > 50) x = 50;
        return x;
    }

    List<ImageRef> resolveImages(LlmModerationTestRequest req, int maxImages) {
        if (req == null) return List.of();
        int max = Math.clamp(maxImages, 1, 50);
        List<ImageRef> out = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        if (req.getImages() != null) {
            for (LlmModerationTestRequest.ImageInput in : req.getImages()) {
                if (in == null) continue;
                String u = blankToNull(in.getUrl());
                if (u == null) continue;
                String mt = blankToNull(in.getMimeType());
                boolean isImg = mt != null && mt.toLowerCase(Locale.ROOT).startsWith("image/");
                if (!isImg && !isLikelyImageUrl(u)) continue;
                if (seen.contains(u)) continue;
                seen.add(u);
                out.add(new ImageRef(in.getFileAssetId(), u, mt));
                if (out.size() >= max) break;
            }
        }
        if (!out.isEmpty()) return out;

        if (req.getQueueId() == null) return List.of();
        ModerationQueueEntity q = queueRepository.findById(req.getQueueId()).orElse(null);
        if (q == null) return List.of();
        if (q.getContentType() != ContentType.POST) return List.of();

        try {
            var page = postAttachmentsRepository.findByPostId(
                    q.getContentId(),
                    PageRequest.of(0, 50, Sort.by(Sort.Order.asc("createdAt"), Sort.Order.asc("id")))
            );
            if (page.getContent().isEmpty()) return List.of();
            LinkedHashSet<Long> fileAssetIds = new LinkedHashSet<>();
            for (var a : page.getContent()) {
                if (a == null) continue;
                if (a.getFileAssetId() != null) fileAssetIds.add(a.getFileAssetId());
                if (a.getFileAsset() == null) continue;
                String u = blankToNull(a.getFileAsset().getUrl());
                if (u == null) continue;
                String mt = a.getFileAsset().getMimeType() == null ? "" : a.getFileAsset().getMimeType().trim().toLowerCase(Locale.ROOT);
                if (!mt.startsWith("image/")) continue;
                if (seen.contains(u)) continue;
                seen.add(u);
                out.add(new ImageRef(a.getFileAssetId(), u, mt));
                if (out.size() >= max) break;
            }
            if (out.size() >= max) return out;

            if (!fileAssetIds.isEmpty()) {
                for (var ex : fileAssetExtractionsRepository.findAllById(fileAssetIds)) {
                    if (ex == null) continue;
                    List<ImageRef> derived = tryExtractDerivedImages(ex.getFileAssetId(), ex.getExtractedMetadataJson(), max);
                    if (derived == null || derived.isEmpty()) continue;
                    for (ImageRef img : derived) {
                        if (img == null) continue;
                        String u = blankToNull(img.url());
                        if (u == null) continue;
                        if (seen.contains(u)) continue;
                        seen.add(u);
                        out.add(img);
                        if (out.size() >= max) break;
                    }
                    if (out.size() >= max) break;
                }
            }

            return out.isEmpty() ? List.of() : out;
        } catch (Exception ignore) {
            return List.of();
        }
    }

    static boolean isLikelyImageUrl(String url) {
        if (url == null) return false;
        String lower = url.trim().toLowerCase(Locale.ROOT);
        if (lower.startsWith("/uploads/")) return true;
        return lower.endsWith(".png")
                || lower.endsWith(".jpg")
                || lower.endsWith(".jpeg")
                || lower.endsWith(".gif")
                || lower.endsWith(".webp")
                || lower.endsWith(".bmp")
                || lower.endsWith(".svg");
    }

    String encodeImageUrlForUpstream(ImageRef img) {
        return encodeImageUrlForUpstream(img, null);
    }

    String encodeImageUrlForUpstream(ImageRef img, String modelName) {
        if (img == null) return null;
        String url = blankToNull(img.url());
        if (url == null) return null;

        if (url.startsWith("data:")) {
            if (url.length() <= MAX_UPSTREAM_DATA_URL_CHARS) return url;
            return null;
        }
        if (url.startsWith("http://") || url.startsWith("https://")) {
            return url;
        }
        if (url.startsWith("oss://")) {
            return url;
        }

        // Try configurable storage modes (LOCAL public URL / DashScope temp / Aliyun OSS)
        String mimeType = blankToNull(img.mimeType());
        if ((mimeType == null || mimeType.isBlank()) && img.fileAssetId() != null) {
            var fa = fileAssetsRepository.findById(img.fileAssetId()).orElse(null);
            mimeType = fa == null ? null : blankToNull(fa.getMimeType());
        }
        try {
            String uploaded = llmImageUploadService.resolveImageUrl(url, mimeType, modelName);
            if (uploaded != null && !uploaded.isBlank()) return uploaded;
        } catch (Exception e) {
            // fall through to base64 fallback
        }

        // Fallback: base64 data URL (original behavior)
        byte[] bytes = readLocalUploadBytes(img.fileAssetId(), url);
        if (bytes == null || bytes.length == 0) return url;
        if (bytes.length > 4_000_000) return url;

        if (mimeType == null || mimeType.isBlank()) mimeType = "application/octet-stream";
        if (!mimeType.toLowerCase(Locale.ROOT).startsWith("image/")) return url;

        String direct = toDataUrl(bytes, mimeType);
        if (direct != null && direct.length() <= MAX_UPSTREAM_DATA_URL_CHARS) return direct;

        byte[] resized = tryResizeAndCompressToJpeg(bytes, MAX_UPSTREAM_IMAGE_BYTES);
        if (resized != null && resized.length > 0) {
            String compressed = toDataUrl(resized, "image/jpeg");
            if (compressed != null && compressed.length() <= MAX_UPSTREAM_DATA_URL_CHARS) return compressed;
        }

        byte[] shrunk = bytes.length > MAX_UPSTREAM_IMAGE_BYTES ? trimBytes(bytes, MAX_UPSTREAM_IMAGE_BYTES) : bytes;
        String fallback = toDataUrl(shrunk, mimeType);
        if (fallback != null && fallback.length() <= MAX_UPSTREAM_DATA_URL_CHARS) return fallback;
        return url;
    }

    private static String toDataUrl(byte[] bytes, String mimeType) {
        if (bytes == null || bytes.length == 0) return null;
        String mt = blankToNull(mimeType);
        if (mt == null) mt = "application/octet-stream";
        return "data:" + mt + ";base64," + Base64.getEncoder().encodeToString(bytes);
    }

    private static byte[] trimBytes(byte[] src, int maxBytes) {
        if (src == null || src.length == 0) return src;
        int limit = Math.max(1, maxBytes);
        if (src.length <= limit) return src;
        byte[] out = new byte[limit];
        System.arraycopy(src, 0, out, 0, limit);
        return out;
    }

    private static byte[] tryResizeAndCompressToJpeg(byte[] src, int maxBytes) {
        if (src == null || src.length == 0) return null;
        int target = Math.max(32_000, maxBytes);
        try {
            BufferedImage image = ImageIO.read(new ByteArrayInputStream(src));
            if (image == null) return null;

            BufferedImage working = toRgb(image);
            float quality = 0.86f;
            int maxSide = Math.max(720, Math.max(working.getWidth(), working.getHeight()));

            for (int i = 0; i < 9; i++) {
                BufferedImage scaled = resizeToMaxSide(working, maxSide);
                byte[] out = writeJpeg(scaled, quality);
                if (out != null && out.length <= target) return out;
                maxSide = Math.max(640, (int) Math.floor(maxSide * 0.85));
                quality = Math.max(0.35f, quality - 0.08f);
            }
        } catch (Exception ignore) {
            return null;
        }
        return null;
    }

    private static BufferedImage toRgb(BufferedImage src) {
        if (src == null) return null;
        if (src.getType() == BufferedImage.TYPE_INT_RGB) return src;
        BufferedImage out = new BufferedImage(src.getWidth(), src.getHeight(), BufferedImage.TYPE_INT_RGB);
        out.getGraphics().drawImage(src, 0, 0, null);
        return out;
    }

    private static BufferedImage resizeToMaxSide(BufferedImage src, int maxSide) {
        if (src == null) return null;
        int w = src.getWidth();
        int h = src.getHeight();
        int max = Math.max(w, h);
        if (max <= maxSide) return src;
        double ratio = maxSide / (double) max;
        int nw = Math.max(1, (int) Math.round(w * ratio));
        int nh = Math.max(1, (int) Math.round(h * ratio));
        return ImageResizeSupport.drawResized(src, nw, nh);
    }

    private static byte[] writeJpeg(BufferedImage image, float quality) {
        if (image == null) return null;
        Iterator<ImageWriter> it = ImageIO.getImageWritersByFormatName("jpeg");
        if (it == null || !it.hasNext()) return null;
        ImageWriter writer = it.next();
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream();
             MemoryCacheImageOutputStream ios = new MemoryCacheImageOutputStream(baos)) {
            writer.setOutput(ios);
            ImageWriteParam param = writer.getDefaultWriteParam();
            if (param.canWriteCompressed()) {
                param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
                param.setCompressionQuality(Math.clamp(quality, 0.1f, 1.0f));
            }
            writer.write(null, new IIOImage(image, null, null), param);
            writer.dispose();
            return baos.toByteArray();
        } catch (Exception ignore) {
            try {
                writer.dispose();
            } catch (Exception ignored) {
            }
            return null;
        }
    }

    int estimateVisionImageTokens(ImageRef img, String model, Boolean highResolutionImages, Integer maxPixels) {
        int side = tokenSideForModel(model);
        long tokenPixels = (long) side * (long) side;

        long maxPixelsEffective;
        if (Boolean.TRUE.equals(highResolutionImages)) {
            maxPixelsEffective = 16384L * tokenPixels;
        } else if (maxPixels != null && maxPixels > 0) {
            maxPixelsEffective = maxPixels.longValue();
        } else {
            maxPixelsEffective = 16384L * tokenPixels;
        }

        long minPixels = 4L * tokenPixels;
        long tokenUpperBound = Math.max(4L, (maxPixelsEffective / tokenPixels) + 2L);
        tokenUpperBound = Math.min(tokenUpperBound, 16386L);

        ImageSize size = tryResolveImageSize(img);
        if (size == null || size.width() <= 0 || size.height() <= 0) {
            return (int) tokenUpperBound;
        }

        int width = size.width();
        int height = size.height();

        long hBar = Math.round(height / (double) side) * side;
        long wBar = Math.round(width / (double) side) * side;
        if (hBar <= 0) hBar = side;
        if (wBar <= 0) wBar = side;

        long hwBar = hBar * wBar;
        double hw = (double) height * (double) width;
        if (hwBar > maxPixelsEffective) {
            double beta = Math.sqrt(hw / (double) maxPixelsEffective);
            hBar = (long) Math.floor(height / beta / side) * side;
            wBar = (long) Math.floor(width / beta / side) * side;
            if (hBar <= 0) hBar = side;
            if (wBar <= 0) wBar = side;
        } else if (hwBar < minPixels) {
            double beta = Math.sqrt((double) minPixels / hw);
            hBar = (long) Math.ceil(height * beta / side) * side;
            wBar = (long) Math.ceil(width * beta / side) * side;
        }

        long tokens = (hBar * wBar) / tokenPixels + 2L;
        if (tokens < 4L) tokens = 4L;
        if (tokens > 16386L) tokens = 16386L;
        return (int) tokens;
    }

    static int tokenSideForModel(String model) {
        if (model == null || model.isBlank()) return 32;
        String m = model.trim().toLowerCase(Locale.ROOT);
        if (m.contains("qvq")) return 28;
        if (m.contains("qwen2.5-vl")) return 28;
        return 32;
    }

    ImageSize tryResolveImageSize(ImageRef img) {
        if (img == null) return null;
        String url = blankToNull(img.url());
        if (url == null) return null;
        try {
            byte[] bytes = null;
            if (url.startsWith("data:")) {
                int base64Idx = url.indexOf("base64,");
                if (base64Idx >= 0) {
                    String b64 = url.substring(base64Idx + "base64,".length());
                    if (!b64.isBlank()) {
                        bytes = Base64.getDecoder().decode(b64.trim());
                    }
                }
            } else if (!url.startsWith("http://") && !url.startsWith("https://")) {
                bytes = readLocalUploadBytes(img.fileAssetId(), url);
            }
            if (bytes == null || bytes.length == 0) return null;
            BufferedImage bi = ImageIO.read(new ByteArrayInputStream(bytes));
            if (bi == null) return null;
            return new ImageSize(bi.getWidth(), bi.getHeight());
        } catch (Exception ignore) {
            return null;
        }
    }

    List<ImageRef> tryExtractDerivedImages(Long fileAssetId, String extractedMetadataJson, int maxImages) {
        if (extractedMetadataJson == null || extractedMetadataJson.isBlank()) return List.of();
        try {
            JsonNode root = objectMapper.readTree(extractedMetadataJson);
            if (root == null || root.isMissingNode() || root.isNull()) return List.of();
            JsonNode arr = root.get("extractedImages");
            if (arr == null || !arr.isArray() || arr.isEmpty()) return List.of();
            int max = Math.clamp(maxImages, 1, 50);
            List<ImageRef> out = new ArrayList<>();
            for (JsonNode it : arr) {
                if (it == null || it.isNull() || it.isMissingNode()) continue;
                String url = textOrNull(it.get("url"));
                if (url == null || url.isBlank()) continue;
                String mt = textOrNull(it.get("mimeType"));
                if (mt == null) mt = textOrNull(it.get("mime"));
                if (mt != null && !mt.isBlank()) {
                    String m = mt.trim().toLowerCase(Locale.ROOT);
                    if (!m.startsWith("image/") && !isLikelyImageUrl(url)) continue;
                    mt = m;
                } else {
                    if (!isLikelyImageUrl(url)) continue;
                }
                out.add(new ImageRef(fileAssetId, url.trim(), mt));
                if (out.size() >= max) break;
            }
            return out;
        } catch (Exception ignore) {
            return List.of();
        }
    }

    private static String textOrNull(JsonNode n) {
        if (n == null || n.isMissingNode() || n.isNull()) return null;
        if (n.isTextual()) return n.asText();
        return String.valueOf(n);
    }

    private byte[] readLocalUploadBytes(Long fileAssetId, String url) {
        return LocalUploadFileSupport.readLocalUploadBytes(fileAssetsRepository, uploadRoot, urlPrefix, fileAssetId, url);
    }

    private static String blankToNull(String s) {
        return AdminModerationLlmConfigSupport.blankToNull(s);
    }
}
