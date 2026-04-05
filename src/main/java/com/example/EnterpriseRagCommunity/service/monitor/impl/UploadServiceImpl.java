package com.example.EnterpriseRagCommunity.service.monitor.impl;

import com.example.EnterpriseRagCommunity.dto.monitor.ResumableUploadInitRequestDTO;
import com.example.EnterpriseRagCommunity.dto.monitor.ResumableUploadInitResponseDTO;
import com.example.EnterpriseRagCommunity.dto.monitor.ResumableUploadStatusDTO;
import com.example.EnterpriseRagCommunity.dto.monitor.UploadResultDTO;
import com.example.EnterpriseRagCommunity.dto.monitor.UploadFormatsConfigDTO;
import com.example.EnterpriseRagCommunity.entity.monitor.FileAssetsEntity;
import com.example.EnterpriseRagCommunity.entity.monitor.enums.FileAssetStatus;
import com.example.EnterpriseRagCommunity.exception.ResourceNotFoundException;
import com.example.EnterpriseRagCommunity.repository.monitor.FileAssetsRepository;
import com.example.EnterpriseRagCommunity.service.AdministratorService;
import com.example.EnterpriseRagCommunity.service.monitor.FileAssetExtractionService;
import com.example.EnterpriseRagCommunity.service.monitor.UploadFormatsConfigService;
import com.example.EnterpriseRagCommunity.service.monitor.UploadService;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.io.OutputStream;
import java.io.RandomAccessFile;
import java.nio.file.*;
import java.security.MessageDigest;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.function.BooleanSupplier;
import java.util.function.LongConsumer;

@Service
@RequiredArgsConstructor
public class UploadServiceImpl implements UploadService {
    private static final int RESUMABLE_VERIFY_BUFFER_BYTES = 1024 * 1024;


    @Value("${app.upload.root:uploads}")
    private String uploadRoot;

    @Value("${app.upload.url-prefix:/uploads}")
    private String urlPrefix;
    private final FileAssetsRepository fileAssetsRepository;
    private final AdministratorService administratorService;
    private final UploadFormatsConfigService uploadFormatsConfigService;
    private final FileAssetExtractionService fileAssetExtractionService;
    private final ObjectMapper objectMapper;

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

    private static String sha256Hex(Path file, LongConsumer onBytesRead, BooleanSupplier shouldAbort) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            try (InputStream is = Files.newInputStream(file)) {
                byte[] buf = new byte[RESUMABLE_VERIFY_BUFFER_BYTES];
                int n;
                long read = 0L;
                while ((n = is.read(buf)) > 0) {
                    if (shouldAbort != null && shouldAbort.getAsBoolean()) {
                        throw new IllegalArgumentException("上传任务已取消");
                    }
                    md.update(buf, 0, n);
                    read += n;
                    if (onBytesRead != null) {
                        onBytesRead.accept(read);
                    }
                }
            }
            byte[] d = md.digest();
            StringBuilder sb = new StringBuilder(d.length * 2);
            for (byte b : d) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalArgumentException("计算文件指纹失败");
        }
    }

    private static String safeFileName(String original) {
        String name = StringUtils.hasText(original) ? original : "file";
        // Keep only the tail segment without invoking path parsers on user input.
        int slash = Math.max(name.lastIndexOf('/'), name.lastIndexOf('\\'));
        if (slash >= 0) {
            name = name.substring(slash + 1);
        }
        name = name.replaceAll("[\\\\/:*?\"<>|]+", "_");
        name = name.replaceAll("[\u0000-\u001F]+", "");
        name = name.replaceAll("\\s+", " ").trim();
        if (name.equals(".") || name.equals("..") || name.isBlank()) {
            name = "file";
        }
        if (name.length() > 191) {
            name = name.substring(name.length() - 191);
        }
        return name;
    }

    private static String storedFileName() {
        return UUID.randomUUID().toString().replace("-", "");
    }

    private record SavedFile(Path target, String url) {
    }

    private SavedFile saveBlobOrThrow(MultipartFile file) {
        LocalDate now = LocalDate.now();
        String subDir = String.format("%d/%02d", now.getYear(), now.getMonthValue());
        String storedName = storedFileName();

        Path root = uploadRootPathOrThrow();
        Path dir = root.resolve(subDir).normalize();
        Path target = dir.resolve(storedName).normalize();

        if (!target.startsWith(root)) {
            throw new IllegalArgumentException("非法文件路径");
        }

        try {
            Files.createDirectories(dir);
            try (InputStream in = file.getInputStream();
                 OutputStream out = Files.newOutputStream(target,
                         StandardOpenOption.CREATE,
                         StandardOpenOption.TRUNCATE_EXISTING,
                         StandardOpenOption.WRITE)) {
                in.transferTo(out);
            }
        } catch (Exception e) {
            throw new IllegalArgumentException("保存文件失败");
        }

        String normalizedPrefix = urlPrefix.endsWith("/") ? urlPrefix.substring(0, urlPrefix.length() - 1) : urlPrefix;
        String url = normalizedPrefix + "/" + subDir + "/" + storedName;
        return new SavedFile(target, url);
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

    private static String extLowerOrNull(String fileName) {
        if (!StringUtils.hasText(fileName)) return null;
        String name = fileName.trim();
        int idx = name.lastIndexOf('.');
        if (idx < 0 || idx == name.length() - 1) return null;
        String ext = name.substring(idx + 1).trim().toLowerCase(Locale.ROOT);
        if (ext.isBlank()) return null;
        if (!ext.matches("[a-z0-9]+")) return null;
        if (ext.length() > 16) return null;
        return ext;
    }

    private static long safeSize(MultipartFile f) {
        if (f == null) return 0L;
        long s = f.getSize();
        return s < 0 ? 0 : s;
    }

    private void validateUploadsOrThrow(List<MultipartFile> files) {
        UploadFormatsConfigDTO cfg = uploadFormatsConfigService.getConfig();
        if (!Boolean.TRUE.equals(cfg.getEnabled())) {
            throw new IllegalArgumentException("上传功能已被管理员关闭");
        }
        if (files == null || files.isEmpty()) {
            throw new IllegalArgumentException("文件不能为空");
        }
        int maxFiles = cfg.getMaxFilesPerRequest() == null ? 10 : cfg.getMaxFilesPerRequest();
        if (files.size() > maxFiles) {
            throw new IllegalArgumentException("文件数量超过限制: " + maxFiles);
        }
        long total = 0L;
        for (MultipartFile f : files) {
            if (f == null || f.isEmpty()) {
                throw new IllegalArgumentException("存在空文件");
            }
            total += safeSize(f);
        }
        long maxTotal = cfg.getMaxTotalSizeBytes() == null ? (200L * 1024 * 1024) : cfg.getMaxTotalSizeBytes();
        if (total > maxTotal) {
            throw new IllegalArgumentException("总大小超过限制: " + maxTotal);
        }

        Map<String, UploadFormatsConfigDTO.UploadFormatRuleDTO> extToRule = uploadFormatsConfigService.enabledExtensionToRule();
        if (extToRule.isEmpty()) {
            throw new IllegalArgumentException("未配置允许上传的文件类型");
        }
        long globalMax = cfg.getMaxFileSizeBytes() == null ? (50L * 1024 * 1024) : cfg.getMaxFileSizeBytes();
        for (MultipartFile f : files) {
            String originalName = safeFileName(f.getOriginalFilename());
            String ext = extLowerOrNull(originalName);
            if (ext == null) {
                throw new IllegalArgumentException("文件缺少扩展名: " + originalName);
            }
            UploadFormatsConfigDTO.UploadFormatRuleDTO rule = extToRule.get(ext);
            if (rule == null) {
                throw new IllegalArgumentException("不支持的文件类型: " + originalName);
            }
            long perMax = (rule.getMaxFileSizeBytes() != null && rule.getMaxFileSizeBytes() > 0) ? rule.getMaxFileSizeBytes() : globalMax;
            long size = safeSize(f);
            if (size > perMax) {
                throw new IllegalArgumentException("文件大小超过限制: " + originalName);
            }
        }
    }

    private static final int RESUMABLE_CHUNK_SIZE_BYTES = 32 * 1024 * 1024;

    private static class ResumableMeta {
        public String uploadId;
        public Long ownerId;
        public String originalName;
        public String mimeType;
        public long totalBytes;
        public long uploadedBytes;
        public int chunkSizeBytes;
        public long createdAtEpochMs;
        public String phase;
        public Long verifyBytes;
        public Long updatedAtEpochMs;
        public String errorMessage;
    }

    private void validateResumableOrThrow(String originalName, long sizeBytes) {
        UploadFormatsConfigDTO cfg = uploadFormatsConfigService.getConfig();
        if (!Boolean.TRUE.equals(cfg.getEnabled())) {
            throw new IllegalArgumentException("上传功能已被管理员关闭");
        }
        if (!StringUtils.hasText(originalName)) {
            throw new IllegalArgumentException("文件名不能为空");
        }
        if (sizeBytes <= 0) {
            throw new IllegalArgumentException("文件大小不合法");
        }

        Map<String, UploadFormatsConfigDTO.UploadFormatRuleDTO> extToRule = uploadFormatsConfigService.enabledExtensionToRule();
        if (extToRule.isEmpty()) {
            throw new IllegalArgumentException("未配置允许上传的文件类型");
        }

        String safeName = safeFileName(originalName);
        String ext = extLowerOrNull(safeName);
        if (ext == null) {
            throw new IllegalArgumentException("文件缺少扩展名: " + safeName);
        }
        UploadFormatsConfigDTO.UploadFormatRuleDTO rule = extToRule.get(ext);
        if (rule == null) {
            throw new IllegalArgumentException("不支持的文件类型: " + safeName);
        }

        long globalMax = cfg.getMaxFileSizeBytes() == null ? (50L * 1024 * 1024) : cfg.getMaxFileSizeBytes();
        long perMax = (rule.getMaxFileSizeBytes() != null && rule.getMaxFileSizeBytes() > 0) ? rule.getMaxFileSizeBytes() : globalMax;
        if (sizeBytes > perMax) {
            throw new IllegalArgumentException("文件大小超过限制: " + safeName);
        }
    }

    private static void requireValidUploadId(String uploadId) {
        if (!StringUtils.hasText(uploadId)) throw new IllegalArgumentException("uploadId 不能为空");
        String id = uploadId.trim();
        if (id.length() > 64) throw new IllegalArgumentException("uploadId 不合法");
        if (!id.matches("[a-zA-Z0-9]+")) throw new IllegalArgumentException("uploadId 不合法");
    }

    private static String normalizeSha256OrThrow(String sha256) {
        if (!StringUtils.hasText(sha256)) throw new IllegalArgumentException("sha256 不能为空");
        String s = sha256.trim().toLowerCase(Locale.ROOT);
        if (!s.matches("[0-9a-f]{64}")) throw new IllegalArgumentException("sha256 不合法");
        return s;
    }

    private Path resumableDirOrThrow() {
        Path root = uploadRootPathOrThrow();
        Path dir = root.resolve("_resumable").normalize();
        if (!dir.startsWith(root)) throw new IllegalArgumentException("非法文件路径");
        try {
            Files.createDirectories(dir);
        } catch (Exception e) {
            throw new IllegalArgumentException("创建上传目录失败");
        }
        return dir;
    }

    private Path uploadRootPathOrThrow() {
        String rootRaw = StringUtils.hasText(uploadRoot) ? uploadRoot.trim() : "uploads";
        if (!rootRaw.matches("[A-Za-z0-9_./\\\\:-]+")) {
            throw new IllegalArgumentException("非法上传根目录");
        }
        return Paths.get(rootRaw).toAbsolutePath().normalize();
    }

    private Path resumableMetaPath(Path dir, String uploadId) {
        return dir.resolve(uploadId + ".json").normalize();
    }

    private Path resumablePartPath(Path dir, String uploadId) {
        return dir.resolve(uploadId + ".part").normalize();
    }

    private ResumableMeta loadResumableMetaOrThrow(String uploadId) {
        requireValidUploadId(uploadId);
        Path dir = resumableDirOrThrow();
        Path metaPath = resumableMetaPath(dir, uploadId);
        if (!metaPath.startsWith(dir)) throw new IllegalArgumentException("非法文件路径");
        if (!Files.exists(metaPath)) throw new IllegalArgumentException("上传任务不存在或已被删除");
        try {
            return objectMapper.readValue(metaPath.toFile(), ResumableMeta.class);
        } catch (Exception e) {
            throw new IllegalArgumentException("读取上传任务失败");
        }
    }

    private void saveResumableMetaOrThrow(ResumableMeta meta) {
        if (meta == null || !StringUtils.hasText(meta.uploadId)) throw new IllegalArgumentException("上传任务不合法");
        Path dir = resumableDirOrThrow();
        Path metaPath = resumableMetaPath(dir, meta.uploadId);
        Path tmp = dir.resolve(meta.uploadId + ".json.tmp").normalize();
        if (!metaPath.startsWith(dir) || !tmp.startsWith(dir)) throw new IllegalArgumentException("非法文件路径");
        try {
            objectMapper.writeValue(tmp.toFile(), meta);
            moveResumableMetaWithRetry(tmp, metaPath);
        } catch (Exception e) {
            try {
                Files.deleteIfExists(tmp);
            } catch (Exception ignored) {
            }
            throw new IllegalArgumentException("保存上传任务失败");
        }
    }

    private void moveResumableMetaWithRetry(Path tmp, Path metaPath) throws Exception {
        for (int i = 0; i < 20; i++) {
            try {
                Files.move(tmp, metaPath, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
                return;
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(tmp, metaPath, StandardCopyOption.REPLACE_EXISTING);
                return;
            } catch (AccessDeniedException e) {
                if (i == 19) throw e;
                try {
                    Thread.sleep(5L);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    throw e;
                }
            }
        }
        Files.move(tmp, metaPath, StandardCopyOption.REPLACE_EXISTING);
    }

    private ResumableUploadStatusDTO toResumableStatus(ResumableMeta meta) {
        ResumableUploadStatusDTO out = new ResumableUploadStatusDTO();
        out.setUploadId(meta.uploadId);
        out.setFileName(meta.originalName);
        out.setFileSize(meta.totalBytes);
        out.setUploadedBytes(meta.uploadedBytes);
        out.setChunkSizeBytes(meta.chunkSizeBytes);
        String status = StringUtils.hasText(meta.phase)
                ? meta.phase.trim()
                : (meta.uploadedBytes >= meta.totalBytes ? "COMPLETED" : "UPLOADING");
        out.setStatus(status);
        out.setVerifyBytes(meta.verifyBytes);
        out.setVerifyTotalBytes(meta.totalBytes);
        Long updatedAt = meta.updatedAtEpochMs;
        out.setUpdatedAtEpochMs(updatedAt != null ? updatedAt : meta.createdAtEpochMs);
        out.setErrorMessage(meta.errorMessage);
        return out;
    }

    @Override
    @Transactional
    public UploadResultDTO upload(MultipartFile file) {
        validateUploadsOrThrow(List.of(file));
        return uploadInternal(file);
    }

    @Override
    @Transactional
    public List<UploadResultDTO> uploadMany(List<MultipartFile> files) {
        validateUploadsOrThrow(files);
        List<UploadResultDTO> out = new ArrayList<>();
        for (MultipartFile f : files) {
            out.add(uploadInternal(f));
        }
        return out;
    }

    @Override
    public UploadResultDTO findBySha256(String sha256, String fileName) {
        currentUserIdOrThrow();
        String normalized = normalizeSha256OrThrow(sha256);
        FileAssetsEntity existing = fileAssetsRepository.findBySha256(normalized)
                .orElseThrow(() -> new ResourceNotFoundException("文件不存在"));
        String resolvedName = safeFileName(StringUtils.hasText(fileName) ? fileName : existing.getOriginalName());
        fileAssetExtractionService.requestExtractionIfEnabled(existing);
        return toResult(existing, resolvedName);
    }

    @Override
    public ResumableUploadInitResponseDTO initResumable(ResumableUploadInitRequestDTO req) {
        Long ownerId = currentUserIdOrThrow();
        String originalName = safeFileName(req == null ? null : req.getFileName());
        long totalBytes = req == null || req.getFileSize() == null ? 0L : req.getFileSize();
        String mime = (req == null || !StringUtils.hasText(req.getMimeType())) ? "application/octet-stream" : req.getMimeType();
        validateResumableOrThrow(originalName, totalBytes);

        String uploadId = UUID.randomUUID().toString().replace("-", "");
        Path dir = resumableDirOrThrow();
        Path metaPath = resumableMetaPath(dir, uploadId);
        Path partPath = resumablePartPath(dir, uploadId);
        if (!metaPath.startsWith(dir) || !partPath.startsWith(dir)) throw new IllegalArgumentException("非法文件路径");

        ResumableMeta meta = new ResumableMeta();
        meta.uploadId = uploadId;
        meta.ownerId = ownerId;
        meta.originalName = originalName;
        meta.mimeType = mime;
        meta.totalBytes = totalBytes;
        meta.uploadedBytes = 0L;
        meta.chunkSizeBytes = RESUMABLE_CHUNK_SIZE_BYTES;
        meta.createdAtEpochMs = System.currentTimeMillis();
        meta.phase = "UPLOADING";
        meta.verifyBytes = 0L;
        meta.updatedAtEpochMs = meta.createdAtEpochMs;
        meta.errorMessage = null;

        saveResumableMetaOrThrow(meta);

        try {
            Files.deleteIfExists(partPath);
            Files.createFile(partPath);
        } catch (Exception e) {
            throw new IllegalArgumentException("创建上传文件失败");
        }

        ResumableUploadInitResponseDTO out = new ResumableUploadInitResponseDTO();
        out.setUploadId(uploadId);
        out.setChunkSizeBytes(meta.chunkSizeBytes);
        out.setUploadedBytes(meta.uploadedBytes);
        return out;
    }

    @Override
    public ResumableUploadStatusDTO getResumableStatus(String uploadId) {
        ResumableMeta meta = loadResumableMetaOrThrow(uploadId);
        Long ownerId = currentUserIdOrThrow();
        if (meta.ownerId == null || !meta.ownerId.equals(ownerId)) {
            throw new IllegalArgumentException("无权限访问该上传任务");
        }
        return toResumableStatus(meta);
    }

    @Override
    public ResumableUploadStatusDTO uploadResumableChunk(String uploadId, long offset, long total, InputStream bodyStream) {
        if (bodyStream == null) throw new IllegalArgumentException("分片内容不能为空");
        ResumableMeta meta = loadResumableMetaOrThrow(uploadId);
        Long ownerId = currentUserIdOrThrow();
        if (meta.ownerId == null || !meta.ownerId.equals(ownerId)) {
            throw new IllegalArgumentException("无权限访问该上传任务");
        }
        if (total != meta.totalBytes) {
            throw new IllegalArgumentException("文件大小不匹配");
        }
        if (offset < 0 || offset > meta.totalBytes) {
            throw new IllegalArgumentException("偏移量不合法");
        }
        if (offset != meta.uploadedBytes) {
            throw new IllegalArgumentException("偏移量不匹配，当前已上传: " + meta.uploadedBytes);
        }

        Path dir = resumableDirOrThrow();
        Path partPath = resumablePartPath(dir, uploadId);
        if (!partPath.startsWith(dir)) throw new IllegalArgumentException("非法文件路径");

        long maxAllowed = Math.min(meta.chunkSizeBytes, meta.totalBytes - offset);
        if (maxAllowed <= 0) throw new IllegalArgumentException("无可上传内容");

        long written = 0L;
        try (RandomAccessFile raf = new RandomAccessFile(partPath.toFile(), "rw")) {
            raf.seek(offset);
            byte[] buf = new byte[1024 * 1024];
            int n;
            while ((n = bodyStream.read(buf)) > 0) {
                if (written + n > maxAllowed) {
                    throw new IllegalArgumentException("分片大小超过限制");
                }
                raf.write(buf, 0, n);
                written += n;
            }
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalArgumentException("写入分片失败");
        }

        meta.uploadedBytes = offset + written;
        meta.phase = meta.uploadedBytes >= meta.totalBytes ? "COMPLETED" : "UPLOADING";
        meta.updatedAtEpochMs = System.currentTimeMillis();
        meta.errorMessage = null;
        saveResumableMetaOrThrow(meta);
        return toResumableStatus(meta);
    }

    @Override
    public UploadResultDTO completeResumable(String uploadId) {
        ResumableMeta meta = loadResumableMetaOrThrow(uploadId);
        Long ownerId = currentUserIdOrThrow();
        if (meta.ownerId == null || !meta.ownerId.equals(ownerId)) {
            throw new IllegalArgumentException("无权限访问该上传任务");
        }
        if (meta.uploadedBytes != meta.totalBytes) {
            throw new IllegalArgumentException("文件尚未上传完成");
        }

        Path dir = resumableDirOrThrow();
        Path partPath = resumablePartPath(dir, uploadId);
        Path metaPath = resumableMetaPath(dir, uploadId);
        if (!partPath.startsWith(dir)) throw new IllegalArgumentException("非法文件路径");
        if (!Files.exists(partPath)) throw new IllegalArgumentException("上传文件不存在");

        meta.phase = "VERIFYING";
        meta.verifyBytes = 0L;
        meta.updatedAtEpochMs = System.currentTimeMillis();
        meta.errorMessage = null;
        saveResumableMetaOrThrow(meta);

        String sha256;
        try {
            sha256 = normalizeSha256OrThrow(sha256Hex(partPath, (read) -> {
                long now = System.currentTimeMillis();
                if (!Files.exists(metaPath) || !Files.exists(partPath)) {
                    throw new IllegalArgumentException("上传任务已取消");
                }
                meta.verifyBytes = read;
                meta.updatedAtEpochMs = now;
                saveResumableMetaOrThrow(meta);
            }, () -> !Files.exists(metaPath) || !Files.exists(partPath)));
        } catch (IllegalArgumentException e) {
            meta.phase = "ERROR";
            meta.updatedAtEpochMs = System.currentTimeMillis();
            meta.errorMessage = e.getMessage();
            try {
                if (Files.exists(metaPath)) {
                    saveResumableMetaOrThrow(meta);
                }
            } catch (Exception ignored) {
            }
            throw e;
        }

        meta.phase = "FINALIZING";
        meta.verifyBytes = meta.totalBytes;
        meta.updatedAtEpochMs = System.currentTimeMillis();
        saveResumableMetaOrThrow(meta);
        String originalName = safeFileName(meta.originalName);
        String mime = StringUtils.hasText(meta.mimeType) ? meta.mimeType : "application/octet-stream";

        try {
            FileAssetsEntity existing = fileAssetsRepository.findBySha256(sha256).orElse(null);
            if (existing != null) {
                cancelResumable(uploadId);
                fileAssetExtractionService.requestExtractionIfEnabled(existing);
                return toResult(existing, originalName);
            }

            LocalDate now = LocalDate.now();
            String subDir = String.format("%d/%02d", now.getYear(), now.getMonthValue());
            String storedName = storedFileName();

            Path root = uploadRootPathOrThrow();
            Path finalDir = root.resolve(subDir).normalize();
            Path target = finalDir.resolve(storedName).normalize();
            if (!target.startsWith(root)) throw new IllegalArgumentException("非法文件路径");

            try {
                Files.createDirectories(finalDir);
                Files.move(partPath, target, StandardCopyOption.REPLACE_EXISTING);
            } catch (Exception e) {
                throw new IllegalArgumentException("保存文件失败");
            }

            String normalizedPrefix = urlPrefix.endsWith("/") ? urlPrefix.substring(0, urlPrefix.length() - 1) : urlPrefix;
            String url = normalizedPrefix + "/" + subDir + "/" + storedName;

            FileAssetsEntity fa = new FileAssetsEntity();
            fa.setOwner(administratorService.findById(ownerId).orElse(null));
            fa.setPath(target.toString());
            fa.setUrl(url);
            fa.setOriginalName(originalName);
            fa.setSizeBytes(meta.totalBytes);
            fa.setMimeType(mime);
            fa.setSha256(sha256);
            fa.setStatus(FileAssetStatus.READY);

            try {
                fa = fileAssetsRepository.save(fa);
            } catch (DataIntegrityViolationException ex) {
                FileAssetsEntity found = fileAssetsRepository.findBySha256(sha256).orElse(null);
                if (found != null) {
                    cancelResumable(uploadId);
                    fileAssetExtractionService.requestExtractionIfEnabled(found);
                    return toResult(found, originalName);
                }
                throw ex;
            }

            fileAssetExtractionService.requestExtractionIfEnabled(fa);
            meta.phase = "DONE";
            meta.updatedAtEpochMs = System.currentTimeMillis();
            meta.errorMessage = null;
            try {
                if (Files.exists(metaPath)) {
                    saveResumableMetaOrThrow(meta);
                }
            } catch (Exception ignored) {
            }
            try {
                Files.deleteIfExists(metaPath);
            } catch (Exception ignored) {
            }
            return toResult(fa, originalName);
        } catch (RuntimeException e) {
            meta.phase = "ERROR";
            meta.updatedAtEpochMs = System.currentTimeMillis();
            meta.errorMessage = e.getMessage();
            try {
                if (Files.exists(metaPath)) {
                    saveResumableMetaOrThrow(meta);
                }
            } catch (Exception ignored) {
            }
            throw e;
        }
    }

    @Override
    public void cancelResumable(String uploadId) {
        if (!StringUtils.hasText(uploadId)) return;
        requireValidUploadId(uploadId);
        ResumableMeta meta = null;
        try {
            meta = loadResumableMetaOrThrow(uploadId);
        } catch (Exception ignored) {
        }
        Long ownerId = null;
        try {
            ownerId = currentUserIdOrThrow();
        } catch (Exception ignored) {
        }
        if (meta != null && ownerId != null && meta.ownerId != null && !meta.ownerId.equals(ownerId)) {
            throw new IllegalArgumentException("无权限访问该上传任务");
        }
        Path dir = resumableDirOrThrow();
        Path metaPath = resumableMetaPath(dir, uploadId);
        Path partPath = resumablePartPath(dir, uploadId);
        try {
            Files.deleteIfExists(metaPath);
        } catch (Exception ignored) {
        }
        try {
            Files.deleteIfExists(partPath);
        } catch (Exception ignored) {
        }
    }

    private UploadResultDTO uploadInternal(MultipartFile file) {
        Long ownerId = currentUserIdOrThrow();

        String sha256 = normalizeSha256OrThrow(sha256Hex(file));
        String mime = (file.getContentType() == null || file.getContentType().isBlank())
                ? "application/octet-stream"
                : file.getContentType();

        // Idempotency: if this file already exists (same content hash), return existing record.
        // This prevents 500 caused by UNIQUE KEY uk_file_sha (sha256).
        FileAssetsEntity existing = fileAssetsRepository.findBySha256(sha256).orElse(null);
        if (existing != null) {
            String originalName = safeFileName(file.getOriginalFilename());
            fileAssetExtractionService.requestExtractionIfEnabled(existing);
            return toResult(existing, originalName);
        }

        SavedFile saved = saveBlobOrThrow(file);
        String originalName = safeFileName(file.getOriginalFilename());

        FileAssetsEntity fa = new FileAssetsEntity();
        fa.setOwner(administratorService.findById(ownerId).orElse(null));
        fa.setPath(saved.target().toString());
        fa.setUrl(saved.url());
        fa.setOriginalName(originalName);
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

        fileAssetExtractionService.requestExtractionIfEnabled(fa);

        return toResult(fa, originalName);
    }
}
