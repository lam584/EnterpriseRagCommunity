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
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        int safeSize = Math.clamp(size, 1, 100);
        return uploadLogRepository.findAllByOrderByUploadedAtDesc(PageRequest.of(page, safeSize));
    }

    @PostMapping("/test-upload")
    @PreAuthorize("hasAuthority(T(com.example.EnterpriseRagCommunity.security.Permissions).perm('admin_ai_image_storage','write'))")
    public Map<String, Object> testUpload(@RequestBody TestUploadRequest req) {
        String localPath = sanitizeLocalPath(req.localPath());
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

    @DeleteMapping("/expired-logs")
    @Transactional
    @PreAuthorize("hasAuthority(T(com.example.EnterpriseRagCommunity.security.Permissions).perm('admin_ai_image_storage','write'))")
    public Map<String, Object> deleteExpiredLogs() {
        int deleted = uploadLogRepository.deleteExpired(LocalDateTime.now());
        return Map.of("deleted", deleted);
    }

    public record TestUploadRequest(String localPath, String mimeType, String modelName) {}

    private static String sanitizeLocalPath(String raw) {
        if (raw == null) return null;
        String p = raw.trim();
        if (p.isBlank()) return null;
        String normalized = p.replace('\\', '/');
        if (!SAFE_LOCAL_PATH.matcher(normalized).matches()) return null;
        if (normalized.contains("..") || normalized.contains("//")) return null;
        if (normalized.startsWith("/") || normalized.matches("^[A-Za-z]:/.*") || normalized.contains("://")) return null;
        if (!(normalized.startsWith("uploads/") || normalized.startsWith("_resumable/") || normalized.startsWith("202"))) return null;
        return normalized;
    }

}
