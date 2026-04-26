package com.example.EnterpriseRagCommunity.controller.ai.admin;

import com.example.EnterpriseRagCommunity.entity.ai.ImageUploadLogEntity;
import com.example.EnterpriseRagCommunity.repository.ai.ImageUploadLogRepository;
import com.example.EnterpriseRagCommunity.service.ai.ImageStorageConfigService;
import com.example.EnterpriseRagCommunity.service.ai.LlmImageUploadService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.regex.Pattern;

@RestController
@RequestMapping("/api/admin/ai/image-storage")
@CrossOrigin(origins = {"http://localhost:5173", "http://127.0.0.1:5173"}, allowCredentials = "true")
@RequiredArgsConstructor
public class AdminImageStorageController {

    private static final Pattern SAFE_LOCAL_PATH = Pattern.compile("^[A-Za-z0-9._/\\\\-]+$");

    private final ImageStorageConfigService configService;
    private final ImageUploadLogRepository uploadLogRepository;
    private final LlmImageUploadService uploadService;

    @GetMapping("/config")
    @PreAuthorize("hasAuthority(T(com.example.EnterpriseRagCommunity.security.Permissions).perm('admin_ai_image_storage','access'))")
    public ImageStorageConfigService.ImageStorageConfig getConfig() {
        return configService.getConfig();
    }

    @PutMapping("/config")
    @PreAuthorize("hasAuthority(T(com.example.EnterpriseRagCommunity.security.Permissions).perm('admin_ai_image_storage','write'))")
    public ImageStorageConfigService.ImageStorageConfig updateConfig(
            @RequestBody ImageStorageConfigService.ImageStorageConfig payload) {
        configService.updateConfig(payload);
        return configService.getConfig();
    }

    @GetMapping("/upload-logs")
    @PreAuthorize("hasAuthority(T(com.example.EnterpriseRagCommunity.security.Permissions).perm('admin_ai_image_storage','access'))")
    public Page<ImageUploadLogEntity> getUploadLogs(
            @RequestParam(name = "page", defaultValue = "0") int page,
            @RequestParam(name = "size", defaultValue = "20") int size) {
        int safeSize = Math.clamp(size, 1, 100);
        return uploadLogRepository.findAllByOrderByUploadedAtDesc(PageRequest.of(page, safeSize));
    }

    @PostMapping("/test-upload")
    @PreAuthorize("hasAuthority(T(com.example.EnterpriseRagCommunity.security.Permissions).perm('admin_ai_image_storage','write'))")
    public Map<String, Object> testUpload(@RequestBody TestUploadRequest req) {
        LlmImageUploadService.ValidatedLocalPath localPath = sanitizeLocalPath(req.localPath());
        if (localPath == null) {
            return Map.of("success", false, "error", "localPath 不能为空");
        }
        try {
            long start = System.currentTimeMillis();
            String url = uploadService.resolveImageUrl(localPath, req.mimeType(), req.modelName());
            long elapsed = System.currentTimeMillis() - start;
            return Map.of(
                    "success", true,
                    "remoteUrl", url == null ? "" : url,
                    "elapsedMs", elapsed
            );
        } catch (Exception e) {
            return Map.of("success", false, "error", e.getMessage() == null ? "未知错误" : e.getMessage());
        }
    }

    @PostMapping("/test-compress")
    @PreAuthorize("hasAuthority(T(com.example.EnterpriseRagCommunity.security.Permissions).perm('admin_ai_image_storage','write'))")
    public Map<String, Object> testCompress(@RequestBody TestCompressRequest req) {
        String input = req == null ? null : req.localPath();
        if (input == null || input.trim().isBlank()) {
            return Map.of("success", false, "error", "localPath 不能为空");
        }
        try {
            ImageStorageConfigService.CompressionConfig compressionConfig = resolveCompressionConfig(req);
            Map<String, Object> data = uploadService.testCompress(input.trim(), compressionConfig);
            java.util.LinkedHashMap<String, Object> out = new java.util.LinkedHashMap<>();
            out.put("success", true);
            out.put("originalSize", data.get("originalSize"));
            out.put("compressedSize", data.get("compressedSize"));
            out.put("originalWidth", data.get("originalWidth"));
            out.put("originalHeight", data.get("originalHeight"));
            out.put("compressedWidth", data.get("compressedWidth"));
            out.put("compressedHeight", data.get("compressedHeight"));
            out.put("compressionRatio", data.get("compressionRatio"));
            out.put("format", data.get("format"));
            out.put("wasCompressed", data.get("wasCompressed"));
            out.put("originalPreview", data.get("originalBase64"));
            out.put("compressedPreview", data.get("compressedBase64"));
            out.put("originalFullImage", data.get("originalFullBase64"));
            out.put("compressedFullImage", data.get("compressedFullBase64"));
            return out;
        } catch (Exception e) {
            return Map.of("success", false, "error", e.getMessage() == null ? "测试压缩失败" : e.getMessage());
        }
    }

    @DeleteMapping("/expired-logs")
    @Transactional
    @PreAuthorize("hasAuthority(T(com.example.EnterpriseRagCommunity.security.Permissions).perm('admin_ai_image_storage','write'))")
    public Map<String, Object> deleteExpiredLogs() {
        int deleted = uploadLogRepository.deleteExpired(LocalDateTime.now());
        return Map.of("deleted", deleted);
    }

        public record TestUploadRequest(String localPath, String mimeType, String modelName) {}
        public record TestCompressRequest(
            String localPath,
            Boolean compressionEnabled,
            Integer compressionMaxWidth,
            Integer compressionMaxHeight,
            Double compressionQuality,
            Integer compressionMaxBytes
        ) {}

        private ImageStorageConfigService.CompressionConfig resolveCompressionConfig(TestCompressRequest req) {
        ImageStorageConfigService.CompressionConfig base = configService.getCompressionConfig();
        if (req == null) return base;

        Boolean enabledOverride = req.compressionEnabled();
        Integer maxWidthOverride = req.compressionMaxWidth();
        Integer maxHeightOverride = req.compressionMaxHeight();
        Double qualityOverride = req.compressionQuality();
        Integer maxBytesOverride = req.compressionMaxBytes();
        boolean hasOverride = enabledOverride != null
            || maxWidthOverride != null
            || maxHeightOverride != null
            || qualityOverride != null
            || maxBytesOverride != null;
        if (!hasOverride) return base;

        boolean enabled = enabledOverride != null ? enabledOverride : base.enabled();
        int maxWidth = maxWidthOverride != null ? Math.clamp(maxWidthOverride, 1, 16384) : base.maxWidth();
        int maxHeight = maxHeightOverride != null ? Math.clamp(maxHeightOverride, 1, 16384) : base.maxHeight();
        double quality = qualityOverride != null ? Math.clamp(qualityOverride, 0.1, 1.0) : base.quality();
        int maxBytes = maxBytesOverride != null ? Math.clamp(maxBytesOverride, 1024, 50_000_000) : base.maxBytes();
        return new ImageStorageConfigService.CompressionConfig(enabled, maxWidth, maxHeight, quality, maxBytes);
        }

    private static LlmImageUploadService.ValidatedLocalPath sanitizeLocalPath(String raw) {
        if (raw == null) return null;
        String p = raw.trim();
        if (p.isBlank()) return null;
        String normalized = p.replace('\\', '/');
        if (!SAFE_LOCAL_PATH.matcher(normalized).matches()) return null;
        if (normalized.contains("..") || normalized.contains("//")) return null;
        if (normalized.startsWith("/") || normalized.matches("^[A-Za-z]:/.*") || normalized.contains("://")) return null;
        if (!(normalized.startsWith("uploads/") || normalized.startsWith("_resumable/") || normalized.startsWith("202"))) return null;
        return new LlmImageUploadService.ValidatedLocalPath(normalized);
    }

}
