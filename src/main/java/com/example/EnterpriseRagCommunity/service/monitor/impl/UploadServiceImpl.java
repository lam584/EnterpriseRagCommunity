package com.example.EnterpriseRagCommunity.service.monitor.impl;

import com.example.EnterpriseRagCommunity.dto.monitor.UploadResultDTO;
import com.example.EnterpriseRagCommunity.entity.monitor.FileAssetsEntity;
import com.example.EnterpriseRagCommunity.entity.monitor.enums.FileAssetStatus;
import com.example.EnterpriseRagCommunity.repository.monitor.FileAssetsRepository;
import com.example.EnterpriseRagCommunity.service.AdministratorService;
import com.example.EnterpriseRagCommunity.service.monitor.UploadService;
import jakarta.transaction.Transactional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.nio.file.*;
import java.security.MessageDigest;
import java.time.LocalDate;

@Service
public class UploadServiceImpl implements UploadService {

    @Value("${app.upload.root:uploads}")
    private String uploadRoot;

    @Value("${app.upload.url-prefix:/uploads}")
    private String urlPrefix;

    @Autowired
    private FileAssetsRepository fileAssetsRepository;

    @Autowired
    private AdministratorService administratorService;

    private Long currentUserIdOrThrow() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || "anonymousUser".equals(auth.getPrincipal())) {
            throw new org.springframework.security.core.AuthenticationException("未登录或会话已过期") {};
        }
        String email = auth.getName();
        return administratorService.findByUsername(email)
                .orElseThrow(() -> new IllegalArgumentException("当前用户不存在"))
                .getId();
    }

    private static String sha256Hex(MultipartFile file) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            try (InputStream is = file.getInputStream()) {
                byte[] buf = new byte[8192];
                int n;
                while ((n = is.read(buf)) > 0) {
                    md.update(buf, 0, n);
                }
            }
            byte[] d = md.digest();
            StringBuilder sb = new StringBuilder(d.length * 2);
            for (byte b : d) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (Exception e) {
            throw new IllegalArgumentException("计算文件指纹失败");
        }
    }

    private static String safeFileName(String original) {
        String name = StringUtils.hasText(original) ? original : "file";
        name = Paths.get(name).getFileName().toString();
        name = name.replaceAll("[\\\\/]+", "_");
        name = name.replaceAll("\\s+", " ").trim();
        if (name.length() > 191) {
            name = name.substring(name.length() - 191);
        }
        return name;
    }

    private UploadResultDTO toResult(FileAssetsEntity fa, String fallbackFileName) {
        UploadResultDTO res = new UploadResultDTO();
        res.setId(fa.getId());
        res.setFileName(StringUtils.hasText(fallbackFileName) ? fallbackFileName : "file");
        res.setFileUrl(fa.getUrl());
        res.setFileSize(fa.getSizeBytes());
        res.setMimeType(fa.getMimeType());
        return res;
    }

    @Override
    @Transactional
    public UploadResultDTO upload(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("文件不能为空");
        }

        Long ownerId = currentUserIdOrThrow();

        String sha256 = sha256Hex(file);
        String originalName = safeFileName(file.getOriginalFilename());
        String mime = (file.getContentType() == null || file.getContentType().isBlank())
                ? "application/octet-stream"
                : file.getContentType();

        // Idempotency: if this file already exists (same content hash), return existing record.
        // This prevents 500 caused by UNIQUE KEY uk_file_sha (sha256).
        FileAssetsEntity existing = fileAssetsRepository.findBySha256(sha256).orElse(null);
        if (existing != null) {
            return toResult(existing, originalName);
        }

        LocalDate now = LocalDate.now();
        String subDir = String.format("%d/%02d", now.getYear(), now.getMonthValue());
        String storedName = sha256 + "_" + originalName;

        Path root = Paths.get(uploadRoot).toAbsolutePath().normalize();
        Path dir = root.resolve(subDir).normalize();
        Path target = dir.resolve(storedName).normalize();

        if (!target.startsWith(root)) {
            throw new IllegalArgumentException("非法文件路径");
        }

        try {
            Files.createDirectories(dir);
            Files.copy(file.getInputStream(), target, StandardCopyOption.REPLACE_EXISTING);
        } catch (Exception e) {
            throw new IllegalArgumentException("保存文件失败");
        }

        String normalizedPrefix = urlPrefix.endsWith("/") ? urlPrefix.substring(0, urlPrefix.length() - 1) : urlPrefix;
        String url = normalizedPrefix + "/" + subDir + "/" + storedName;

        FileAssetsEntity fa = new FileAssetsEntity();
        fa.setOwner(administratorService.findById(ownerId).orElse(null));
        fa.setPath(target.toString());
        fa.setUrl(url);
        fa.setSizeBytes(file.getSize());
        fa.setMimeType(mime);
        fa.setSha256(sha256);
        fa.setStatus(FileAssetStatus.READY);

        try {
            fa = fileAssetsRepository.save(fa);
        } catch (DataIntegrityViolationException ex) {
            // Concurrent upload of the same file may hit unique constraint; return the existing one.
            return fileAssetsRepository.findBySha256(sha256)
                    .map(found -> toResult(found, originalName))
                    .orElseThrow(() -> ex);
        }

        return toResult(fa, originalName);
    }
}
