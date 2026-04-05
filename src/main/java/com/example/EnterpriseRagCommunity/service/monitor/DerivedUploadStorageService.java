package com.example.EnterpriseRagCommunity.service.monitor;

import com.example.EnterpriseRagCommunity.config.UploadProperties;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.io.ByteArrayInputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class DerivedUploadStorageService {

    private final UploadProperties uploadProperties;
    private final AppSettingsService appSettingsService;
    private final ObjectMapper objectMapper;

    public static final String KEY_DERIVED_IMAGES_BUDGET_JSON = "file_extraction.derived_images_budget.json";

    public int getMaxCount() {
        return Math.max(0, readBudget().maxCount);
    }

    public long getMaxImageBytes() {
        return Math.max(0L, readBudget().maxImageBytes);
    }

    public long getMaxTotalBytes() {
        return Math.max(0L, readBudget().maxTotalBytes);
    }

    public Map<String, Object> saveDerivedImage(byte[] bytes, String originalName, String mimeType, Long parentFileAssetId) {
        if (bytes == null || bytes.length == 0) return null;
        if (getMaxImageBytes() > 0 && bytes.length > getMaxImageBytes()) return null;

        String safeName = safeFileName(originalName);
        String sha256 = sha256Hex(bytes);
        String storedName = sha256 + "_" + (parentFileAssetId == null ? "na" : parentFileAssetId) + "_" + safeName;

        LocalDate now = LocalDate.now();
        String subDir = String.format("derived-images/%d/%02d", now.getYear(), now.getMonthValue());

        Path root = uploadProperties.rootPath();
        Path dir = root.resolve(subDir).normalize();
        Path target = dir.resolve(storedName).normalize();
        if (!target.startsWith(root)) return null;

        try {
            Files.createDirectories(dir);
            Files.copy(new ByteArrayInputStream(bytes), target, StandardCopyOption.REPLACE_EXISTING);
        } catch (Exception ignored) {
            return null;
        }

        String url = uploadProperties.normalizedUrlPrefix() + "/" + subDir + "/" + storedName;
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("url", url);
        out.put("fileName", storedName);
        out.put("mimeType", StringUtils.hasText(mimeType) ? mimeType : guessImageMimeTypeFromName(safeName));
        out.put("sizeBytes", (long) bytes.length);
        return out;
    }

    public Map<String, Object> buildPlaceholder(int index, Map<String, Object> imageMeta) {
        if (imageMeta == null) return null;
        Map<String, Object> out = new LinkedHashMap<>(imageMeta);
        out.put("index", index);
        out.put("placeholder", "[[IMAGE_" + index + "]]");
        return out;
    }

    private static String safeFileName(String original) {
        String name = StringUtils.hasText(original) ? original : ("image_" + UUID.randomUUID() + ".png");
        name = Path.of(name).getFileName().toString();
        name = name.replaceAll("[\\\\/]+", "_");
        name = name.replaceAll("\\s+", " ").trim();
        if (name.length() > 191) name = name.substring(name.length() - 191);
        return name;
    }

    private static String sha256Hex(byte[] bytes) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            md.update(bytes);
            byte[] d = md.digest();
            StringBuilder sb = new StringBuilder(d.length * 2);
            for (byte b : d) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (Exception e) {
            return UUID.randomUUID().toString().replace("-", "").toLowerCase(Locale.ROOT);
        }
    }

    private static String guessImageMimeTypeFromName(String name) {
        if (!StringUtils.hasText(name)) return "application/octet-stream";
        String n = name.toLowerCase(Locale.ROOT).trim();
        if (n.endsWith(".png")) return "image/png";
        if (n.endsWith(".jpg") || n.endsWith(".jpeg")) return "image/jpeg";
        if (n.endsWith(".gif")) return "image/gif";
        if (n.endsWith(".bmp")) return "image/bmp";
        if (n.endsWith(".webp")) return "image/webp";
        return "application/octet-stream";
    }

    private Budget readBudget() {
        String json = appSettingsService.getString(KEY_DERIVED_IMAGES_BUDGET_JSON)
                .orElseThrow(() -> new IllegalStateException("missing app_settings: " + KEY_DERIVED_IMAGES_BUDGET_JSON));
        try {
            Map<String, Object> m = objectMapper.readValue(json, new TypeReference<>() {
            });
            return Budget.fromMap(m);
        } catch (Exception e) {
            throw new IllegalStateException("invalid app_settings: " + KEY_DERIVED_IMAGES_BUDGET_JSON);
        }
    }

    private record Budget(int maxCount, long maxImageBytes, long maxTotalBytes) {

        static Budget fromMap(Map<String, Object> m) {
                int c = (int) clampLong(asLong(m == null ? null : m.get("maxCount")), 100000L);
                long mib = clampLong(asLong(m.get("maxImageBytes")), 1024L * 1024 * 1024);
                long mtb = clampLong(asLong(m.get("maxTotalBytes")), 20L * 1024 * 1024 * 1024);
                return new Budget(c, mib, mtb);
            }

            private static long asLong(Object v) {
                if (v == null) throw new IllegalStateException("missing budget field");
                if (v instanceof Number n) return n.longValue();
                try {
                    return Long.parseLong(String.valueOf(v).trim());
                } catch (Exception e) {
                    throw new IllegalStateException("invalid budget field");
                }
            }

            private static long clampLong(long v, long max) {
                if (v < 0L) return 0L;
                return Math.min(v, max);
            }
        }
}
