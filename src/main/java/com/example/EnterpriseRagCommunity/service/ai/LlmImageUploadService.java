package com.example.EnterpriseRagCommunity.service.ai;

import com.example.EnterpriseRagCommunity.entity.ai.ImageUploadLogEntity;
import com.example.EnterpriseRagCommunity.repository.ai.ImageUploadLogRepository;
import com.example.EnterpriseRagCommunity.service.ai.ImageStorageConfigService.CompressionConfig;
import com.example.EnterpriseRagCommunity.service.ai.ImageStorageConfigService.StorageMode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.MemoryCacheImageOutputStream;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.Iterator;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class LlmImageUploadService {

    private static final Logger logger = LoggerFactory.getLogger(LlmImageUploadService.class);

    private final ImageStorageConfigService configService;
    private final ImageUploadLogRepository uploadLogRepository;

    @Value("${app.upload.root:uploads}")
    private String uploadRoot;

    /**
     * Resolve a local image path to a URL suitable for LLM upstream calls.
     *
     * @param localPath    local file path (e.g. /uploads/2026/01/abc.jpg or relative)
     * @param mimeType     MIME type of the image
     * @param modelName    target model name (required for DASHSCOPE_TEMP)
     * @return resolved URL (https:// or oss://) or null if upload failed
     */
    public String resolveImageUrl(String localPath, String mimeType, String modelName) {
        if (localPath == null || localPath.isBlank()) return null;
        StorageMode mode = configService.getMode();
        if (mode == null) return null;

        return switch (mode) {
            case LOCAL -> resolveLocal(localPath);
            case DASHSCOPE_TEMP -> resolveDashscope(localPath, mimeType, modelName);
            case ALIYUN_OSS -> resolveAliyunOss(localPath, mimeType);
        };
    }

    /** Check if any returned URL uses oss:// protocol (for header injection). */
    public boolean isDashscopeMode() {
        return configService.getMode() == StorageMode.DASHSCOPE_TEMP;
    }

    // ── LOCAL mode ──────────────────────────────────────

    private String resolveLocal(String localPath) {
        String baseUrl = configService.getLocalBaseUrl();
        if (baseUrl == null || baseUrl.isBlank()) return localPath;
        if (localPath.startsWith("http://") || localPath.startsWith("https://")) return localPath;
        // Strip trailing slash from base URL
        String base = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        // Ensure path starts with /
        String path = localPath.startsWith("/") ? localPath : "/" + localPath;
        // If path starts with /uploads/, keep as-is; otherwise prefix with /uploads/
        if (!path.startsWith("/uploads/")) {
            path = "/uploads" + path;
        }
        return base + path;
    }

    // ── DASHSCOPE_TEMP mode ─────────────────────────────

    private String resolveDashscope(String localPath, String mimeType, String modelName) {
        String model = modelName;
        if (model == null || model.isBlank()) {
            model = configService.getDashscopeModel();
        }
        if (model == null || model.isBlank()) return null;
        model = model.trim();

        // Check cache
        Optional<ImageUploadLogEntity> cached = uploadLogRepository
                .findFirstByLocalPathAndModelNameAndStatusAndExpiresAtAfterOrderByUploadedAtDesc(
                        localPath, model, "ACTIVE", LocalDateTime.now());
        if (cached.isPresent()) {
            return cached.get().getRemoteUrl();
        }

        // Read local file
        byte[] fileBytes = readLocalFile(localPath);
        if (fileBytes == null || fileBytes.length == 0) return null;

        // Compress if needed
        fileBytes = compressIfEnabled(fileBytes, mimeType);

        long startMs = System.currentTimeMillis();
        try {
            String apiKey = configService.getDashscopeApiKey();
            if (apiKey == null || apiKey.isBlank()) {
                logger.warn("DashScope API key not configured, cannot upload image");
                return null;
            }

            // Step 1: Get upload policy
            Map<String, String> policy = getDashscopeUploadPolicy(apiKey, model);
            if (policy == null) return null;

            // Step 2: Upload file to OSS
            String fileName = extractFileName(localPath);
            String key = policy.get("upload_dir") + "/" + fileName;
            uploadToDashscopeOss(policy, key, fileBytes, fileName);

            String ossUrl = "oss://" + key;
            long durationMs = System.currentTimeMillis() - startMs;

            // Log upload
            logUpload(localPath, ossUrl, "DASHSCOPE_TEMP", model, fileBytes.length, (int) durationMs,
                    LocalDateTime.now().plusHours(48));

            return ossUrl;
        } catch (Exception e) {
            logger.error("Failed to upload image to DashScope temp storage: {} — {}", localPath, e.getMessage());
            return null;
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, String> getDashscopeUploadPolicy(String apiKey, String model) throws Exception {
        URL url = URI.create("https://dashscope.aliyuncs.com/api/v1/uploads?action=getPolicy&model="
                + java.net.URLEncoder.encode(model, StandardCharsets.UTF_8)).toURL();
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("GET");
        conn.setRequestProperty("Authorization", "Bearer " + apiKey);
        conn.setRequestProperty("Content-Type", "application/json");
        conn.setConnectTimeout(10_000);
        conn.setReadTimeout(15_000);

        int status = conn.getResponseCode();
        if (status != 200) {
            String errBody = readStream(conn.getErrorStream());
            logger.error("DashScope getPolicy failed: HTTP {} — {}", status, errBody);
            return null;
        }
        String body = readStream(conn.getInputStream());
        com.fasterxml.jackson.databind.ObjectMapper om = new com.fasterxml.jackson.databind.ObjectMapper();
        com.fasterxml.jackson.databind.JsonNode root = om.readTree(body);
        com.fasterxml.jackson.databind.JsonNode data = root.get("data");
        if (data == null) {
            logger.error("DashScope getPolicy: missing 'data' in response — {}", body);
            return null;
        }
        java.util.HashMap<String, String> result = new java.util.HashMap<>();
        data.fields().forEachRemaining(e -> result.put(e.getKey(), e.getValue().asText("")));
        return result;
    }

    private void uploadToDashscopeOss(Map<String, String> policy, String key, byte[] fileBytes, String fileName) throws Exception {
        String uploadHost = policy.get("upload_host");
        if (uploadHost == null || uploadHost.isBlank()) {
            throw new IllegalStateException("Missing upload_host in DashScope policy");
        }

        String boundary = "----FormBoundary" + UUID.randomUUID().toString().replace("-", "");
        URL url = URI.create(uploadHost).toURL();
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("POST");
        conn.setDoOutput(true);
        conn.setRequestProperty("Content-Type", "multipart/form-data; boundary=" + boundary);
        conn.setConnectTimeout(30_000);
        conn.setReadTimeout(60_000);

        try (OutputStream os = conn.getOutputStream()) {
            writeFormField(os, boundary, "OSSAccessKeyId", policy.getOrDefault("oss_access_key_id", ""));
            writeFormField(os, boundary, "Signature", policy.getOrDefault("signature", ""));
            writeFormField(os, boundary, "policy", policy.getOrDefault("policy", ""));
            writeFormField(os, boundary, "x-oss-object-acl", policy.getOrDefault("x_oss_object_acl", ""));
            writeFormField(os, boundary, "x-oss-forbid-overwrite", policy.getOrDefault("x_oss_forbid_overwrite", ""));
            writeFormField(os, boundary, "key", key);
            writeFormField(os, boundary, "success_action_status", "200");
            // File part
            os.write(("--" + boundary + "\r\n").getBytes(StandardCharsets.UTF_8));
            os.write(("Content-Disposition: form-data; name=\"file\"; filename=\"" + fileName + "\"\r\n").getBytes(StandardCharsets.UTF_8));
            os.write("Content-Type: application/octet-stream\r\n\r\n".getBytes(StandardCharsets.UTF_8));
            os.write(fileBytes);
            os.write("\r\n".getBytes(StandardCharsets.UTF_8));
            os.write(("--" + boundary + "--\r\n").getBytes(StandardCharsets.UTF_8));
        }

        int status = conn.getResponseCode();
        if (status != 200 && status != 204) {
            String errBody = readStream(conn.getErrorStream());
            throw new RuntimeException("DashScope OSS upload failed: HTTP " + status + " — " + errBody);
        }
    }

    // ── ALIYUN_OSS mode ─────────────────────────────────

    private String resolveAliyunOss(String localPath, String mimeType) {
        // Check cache
        Optional<ImageUploadLogEntity> cached = uploadLogRepository
                .findFirstByLocalPathAndStorageModeAndStatusOrderByUploadedAtDesc(localPath, "ALIYUN_OSS", "ACTIVE");
        if (cached.isPresent()) {
            return cached.get().getRemoteUrl();
        }

        byte[] fileBytes = readLocalFile(localPath);
        if (fileBytes == null || fileBytes.length == 0) return null;

        fileBytes = compressIfEnabled(fileBytes, mimeType);

        ImageStorageConfigService.ImageStorageConfig cfg = configService.getConfigRaw();
        String endpoint = cfg.getOssEndpoint();
        String bucket = cfg.getOssBucket();
        String accessKeyId = cfg.getOssAccessKeyId();
        String accessKeySecret = cfg.getOssAccessKeySecret();

        if (endpoint == null || endpoint.isBlank() || bucket == null || bucket.isBlank()
                || accessKeyId == null || accessKeyId.isBlank()
                || accessKeySecret == null || accessKeySecret.isBlank()) {
            logger.warn("Aliyun OSS config incomplete, cannot upload image");
            return null;
        }

        long startMs = System.currentTimeMillis();
        try {
            String objectKey = buildOssObjectKey(localPath);
            String ossUrl = putObjectSimple(endpoint, bucket, accessKeyId, accessKeySecret, objectKey, fileBytes, mimeType);
            long durationMs = System.currentTimeMillis() - startMs;

            logUpload(localPath, ossUrl, "ALIYUN_OSS", null, fileBytes.length, (int) durationMs, null);
            return ossUrl;
        } catch (Exception e) {
            logger.error("Failed to upload image to Aliyun OSS: {} — {}", localPath, e.getMessage());
            return null;
        }
    }

    /**
     * Simple OSS PUT Object using REST API with V1 signature.
     */
    private String putObjectSimple(String endpoint, String bucket, String accessKeyId, String accessKeySecret,
                                   String objectKey, byte[] data, String contentType) throws Exception {
        String host = bucket + "." + endpoint;
        String urlStr = "https://" + host + "/" + objectKey;
        String ct = (contentType != null && !contentType.isBlank()) ? contentType : "application/octet-stream";
        String date = java.time.ZonedDateTime.now(java.time.ZoneOffset.UTC)
                .format(java.time.format.DateTimeFormatter.ofPattern("EEE, dd MMM yyyy HH:mm:ss 'GMT'", java.util.Locale.US));

        // Build signing string: VERB + "\n" + Content-MD5 + "\n" + Content-Type + "\n" + Date + "\n" + CanonicalizedResource
        String stringToSign = "PUT\n\n" + ct + "\n" + date + "\n/" + bucket + "/" + objectKey;
        String signature = signHmacSha1(accessKeySecret, stringToSign);

        URL url = URI.create(urlStr).toURL();
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("PUT");
        conn.setDoOutput(true);
        conn.setRequestProperty("Date", date);
        conn.setRequestProperty("Content-Type", ct);
        conn.setRequestProperty("Authorization", "OSS " + accessKeyId + ":" + signature);
        conn.setRequestProperty("Content-Length", String.valueOf(data.length));
        conn.setConnectTimeout(30_000);
        conn.setReadTimeout(60_000);

        try (OutputStream os = conn.getOutputStream()) {
            os.write(data);
        }

        int status = conn.getResponseCode();
        if (status != 200 && status != 201) {
            String errBody = readStream(conn.getErrorStream());
            throw new RuntimeException("OSS PUT failed: HTTP " + status + " — " + errBody);
        }
        return urlStr;
    }

    private static String signHmacSha1(String key, String data) throws Exception {
        javax.crypto.Mac mac = javax.crypto.Mac.getInstance("HmacSHA1");
        mac.init(new javax.crypto.spec.SecretKeySpec(key.getBytes(StandardCharsets.UTF_8), "HmacSHA1"));
        byte[] rawHmac = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
        return java.util.Base64.getEncoder().encodeToString(rawHmac);
    }

    private String buildOssObjectKey(String localPath) {
        String name = extractFileName(localPath);
        String prefix = java.time.LocalDate.now().toString().replace("-", "/");
        return "llm-images/" + prefix + "/" + UUID.randomUUID().toString().substring(0, 8) + "_" + name;
    }

    // ── Compression ─────────────────────────────────────

    private byte[] compressIfEnabled(byte[] src, String mimeType) {
        CompressionConfig cfg = configService.getCompressionConfig();
        if (!cfg.enabled() || src == null || src.length == 0) return src;
        if (mimeType != null && !mimeType.toLowerCase().startsWith("image/")) return src;
        if (src.length <= cfg.maxBytes()) return src;

        try {
            BufferedImage image = ImageIO.read(new ByteArrayInputStream(src));
            if (image == null) return src;

            // Convert to RGB if needed
            if (image.getType() != BufferedImage.TYPE_INT_RGB) {
                BufferedImage rgb = new BufferedImage(image.getWidth(), image.getHeight(), BufferedImage.TYPE_INT_RGB);
                rgb.getGraphics().drawImage(image, 0, 0, null);
                image = rgb;
            }

            // Resize if needed
            int w = image.getWidth();
            int h = image.getHeight();
            if (w > cfg.maxWidth() || h > cfg.maxHeight()) {
                double ratio = Math.min((double) cfg.maxWidth() / w, (double) cfg.maxHeight() / h);
                int nw = Math.max(1, (int) Math.round(w * ratio));
                int nh = Math.max(1, (int) Math.round(h * ratio));
                BufferedImage resized = new BufferedImage(nw, nh, BufferedImage.TYPE_INT_RGB);
                resized.getGraphics().drawImage(image, 0, 0, nw, nh, null);
                image = resized;
            }

            // Compress as JPEG
            float quality = (float) cfg.quality();
            for (int i = 0; i < 5; i++) {
                byte[] out = writeJpeg(image, quality);
                if (out != null && out.length <= cfg.maxBytes()) return out;
                quality = Math.max(0.3f, quality - 0.1f);
            }
            // Return best effort
            byte[] last = writeJpeg(image, 0.3f);
            return last != null ? last : src;
        } catch (Exception e) {
            logger.warn("Image compression failed, using original: {}", e.getMessage());
            return src;
        }
    }

    private static byte[] writeJpeg(BufferedImage image, float quality) {
        try {
            Iterator<ImageWriter> writers = ImageIO.getImageWritersByFormatName("jpeg");
            if (!writers.hasNext()) return null;
            ImageWriter writer = writers.next();
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            writer.setOutput(new MemoryCacheImageOutputStream(baos));
            ImageWriteParam param = writer.getDefaultWriteParam();
            param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
            param.setCompressionQuality(quality);
            writer.write(null, new IIOImage(image, null, null), param);
            writer.dispose();
            return baos.toByteArray();
        } catch (Exception e) {
            return null;
        }
    }

    // ── Helpers ──────────────────────────────────────────

    private byte[] readLocalFile(String localPath) {
        try {
            // Try as absolute path first
            Path path = Paths.get(localPath);
            if (Files.exists(path) && Files.isRegularFile(path)) {
                return Files.readAllBytes(path);
            }
            // Try relative to upload root
            String rel = localPath;
            if (rel.startsWith("/uploads/")) rel = rel.substring("/uploads/".length());
            else if (rel.startsWith("uploads/")) rel = rel.substring("uploads/".length());
            Path rootPath = Paths.get(uploadRoot).resolve(rel);
            if (Files.exists(rootPath) && Files.isRegularFile(rootPath)) {
                return Files.readAllBytes(rootPath);
            }
            return null;
        } catch (Exception e) {
            logger.warn("Failed to read local file: {} — {}", localPath, e.getMessage());
            return null;
        }
    }

    private static String extractFileName(String path) {
        if (path == null) return "image.bin";
        int slash = Math.max(path.lastIndexOf('/'), path.lastIndexOf('\\'));
        return slash >= 0 ? path.substring(slash + 1) : path;
    }

    private void logUpload(String localPath, String remoteUrl, String mode, String modelName,
                           long fileSize, int durationMs, LocalDateTime expiresAt) {
        try {
            ImageUploadLogEntity log = new ImageUploadLogEntity();
            log.setLocalPath(localPath);
            log.setRemoteUrl(remoteUrl);
            log.setStorageMode(mode);
            log.setModelName(modelName);
            log.setFileSizeBytes(fileSize);
            log.setUploadDurationMs(durationMs);
            log.setUploadedAt(LocalDateTime.now());
            log.setExpiresAt(expiresAt);
            log.setStatus("ACTIVE");
            uploadLogRepository.save(log);
        } catch (Exception e) {
            logger.warn("Failed to log image upload: {}", e.getMessage());
        }
    }

    private static String readStream(InputStream is) {
        if (is == null) return "";
        try {
            return new String(is.readAllBytes(), StandardCharsets.UTF_8);
        } catch (Exception e) {
            return "";
        }
    }

    private static void writeFormField(OutputStream os, String boundary, String name, String value) throws Exception {
        os.write(("--" + boundary + "\r\n").getBytes(StandardCharsets.UTF_8));
        os.write(("Content-Disposition: form-data; name=\"" + name + "\"\r\n\r\n").getBytes(StandardCharsets.UTF_8));
        os.write(value.getBytes(StandardCharsets.UTF_8));
        os.write("\r\n".getBytes(StandardCharsets.UTF_8));
    }

    /**
     * 测试压缩：读取本地文件并返回原始/压缩后的信息与 Base64 预览。
     */
    public Map<String, Object> testCompress(String localPath) throws Exception {
        byte[] original = readLocalFile(localPath);
        if (original == null || original.length == 0) {
            throw new IllegalArgumentException("无法读取文件: " + localPath);
        }

        String mimeType = guessImageMimeType(localPath);
        BufferedImage originalImage = ImageIO.read(new ByteArrayInputStream(original));
        if (originalImage == null) {
            throw new IllegalArgumentException("文件不是有效的图片格式");
        }

        int origW = originalImage.getWidth();
        int origH = originalImage.getHeight();

        byte[] compressed = compressIfEnabled(original, mimeType);
        boolean wasCompressed = compressed != original;

        BufferedImage compressedImage = wasCompressed ? ImageIO.read(new ByteArrayInputStream(compressed)) : originalImage;
        int compW = compressedImage != null ? compressedImage.getWidth() : origW;
        int compH = compressedImage != null ? compressedImage.getHeight() : origH;

        // Limit Base64 preview size: resize preview images to max 400px for response
        String originalBase64 = toBase64Preview(originalImage, mimeType, 400);
        String compressedBase64 = wasCompressed && compressedImage != null
                ? toBase64Preview(compressedImage, "image/jpeg", 400)
                : originalBase64;

        double ratio = original.length > 0 ? (double) compressed.length / original.length : 1.0;
        String format = mimeType != null ? mimeType : "unknown";

        Map<String, Object> result = new java.util.LinkedHashMap<>();
        result.put("originalSize", original.length);
        result.put("compressedSize", compressed.length);
        result.put("originalWidth", origW);
        result.put("originalHeight", origH);
        result.put("compressedWidth", compW);
        result.put("compressedHeight", compH);
        result.put("compressionRatio", Math.round(ratio * 10000) / 10000.0);
        result.put("format", format);
        result.put("wasCompressed", wasCompressed);
        result.put("originalBase64", originalBase64);
        result.put("compressedBase64", compressedBase64);
        return result;
    }

    private static String guessImageMimeType(String path) {
        if (path == null) return "image/jpeg";
        String lower = path.toLowerCase();
        if (lower.endsWith(".png")) return "image/png";
        if (lower.endsWith(".gif")) return "image/gif";
        if (lower.endsWith(".webp")) return "image/webp";
        if (lower.endsWith(".bmp")) return "image/bmp";
        if (lower.endsWith(".tif") || lower.endsWith(".tiff")) return "image/tiff";
        return "image/jpeg";
    }

    private static String toBase64Preview(BufferedImage image, String mimeType, int maxDim) {
        try {
            int w = image.getWidth();
            int h = image.getHeight();
            BufferedImage preview = image;
            if (w > maxDim || h > maxDim) {
                double ratio = Math.min((double) maxDim / w, (double) maxDim / h);
                int nw = Math.max(1, (int) Math.round(w * ratio));
                int nh = Math.max(1, (int) Math.round(h * ratio));
                preview = new BufferedImage(nw, nh, BufferedImage.TYPE_INT_RGB);
                preview.getGraphics().drawImage(image, 0, 0, nw, nh, null);
            }
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            String formatName = "jpeg";
            if (mimeType != null && mimeType.contains("png")) formatName = "png";
            ImageIO.write(preview, formatName, bos);
            String base64 = java.util.Base64.getEncoder().encodeToString(bos.toByteArray());
            String dataPrefix = "data:" + (formatName.equals("png") ? "image/png" : "image/jpeg") + ";base64,";
            return dataPrefix + base64;
        } catch (Exception e) {
            return "";
        }
    }
}
