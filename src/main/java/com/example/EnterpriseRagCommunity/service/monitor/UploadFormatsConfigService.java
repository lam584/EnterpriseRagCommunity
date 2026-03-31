package com.example.EnterpriseRagCommunity.service.monitor;

import com.example.EnterpriseRagCommunity.dto.monitor.UploadFormatsConfigDTO;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class UploadFormatsConfigService {

    public static final String KEY_CONFIG_JSON = "uploads.formats.config.json";

    private final AppSettingsService appSettingsService;
    private final ObjectMapper objectMapper;

    @Transactional(readOnly = true)
    public UploadFormatsConfigDTO getConfig() {
        String json = appSettingsService.getString(KEY_CONFIG_JSON).orElse(null);
        if (json == null || json.isBlank()) return defaultConfig();
        try {
            UploadFormatsConfigDTO cfg = objectMapper.readValue(json, UploadFormatsConfigDTO.class);
            return normalize(cfg);
        } catch (Exception e) {
            return defaultConfig();
        }
    }

    @Transactional
    public UploadFormatsConfigDTO updateConfig(UploadFormatsConfigDTO payload) {
        if (payload == null) throw new IllegalArgumentException("payload is required");
        UploadFormatsConfigDTO cfg = normalize(payload);
        try {
            String json = objectMapper.writeValueAsString(cfg);
            appSettingsService.upsertString(KEY_CONFIG_JSON, json);
        } catch (Exception e) {
            throw new IllegalStateException("保存配置失败: " + e.getMessage(), e);
        }
        return cfg;
    }

    @Transactional(readOnly = true)
    public Map<String, UploadFormatsConfigDTO.UploadFormatRuleDTO> enabledExtensionToRule() {
        UploadFormatsConfigDTO cfg = getConfig();
        Map<String, UploadFormatsConfigDTO.UploadFormatRuleDTO> map = new LinkedHashMap<>();
        if (!Boolean.TRUE.equals(cfg.getEnabled())) return map;
        if (cfg.getFormats() == null) return map;
        for (UploadFormatsConfigDTO.UploadFormatRuleDTO r : cfg.getFormats()) {
            if (r == null) continue;
            if (!Boolean.TRUE.equals(r.getEnabled())) continue;
            if (r.getExtensions() == null) continue;
            for (String ext : r.getExtensions()) {
                String e = normExt(ext);
                if (e == null) continue;
                map.putIfAbsent(e, r);
            }
        }
        return map;
    }

    public UploadFormatsConfigDTO defaultConfig() {
        UploadFormatsConfigDTO dto = UploadFormatsConfigDTO.empty();
        dto.setEnabled(true);
        dto.setMaxFilesPerRequest(100000);
        dto.setMaxFileSizeBytes(500L * 1024 * 1024 * 1024);
        dto.setMaxTotalSizeBytes(2L * 1024 * 1024 * 1024 * 1024);
        dto.setParseTimeoutMillis(86_400_000);
        dto.setParseMaxChars(10_000_000_000L);

        List<UploadFormatsConfigDTO.UploadFormatRuleDTO> rules = new ArrayList<>();
        rules.add(rule("PDF", true, List.of("pdf"), null, true));
        rules.add(rule("WORD", true, List.of("doc", "docx"), null, true));
        rules.add(rule("EXCEL", true, List.of("xls", "xlsx"), null, true));
        rules.add(rule("PPT", true, List.of("ppt", "pptx"), null, true));
        rules.add(rule("TXT", true, List.of("txt"), null, true));
        rules.add(rule("CSV", true, List.of("csv"), null, true));
        rules.add(rule("JSON", true, List.of("json"), null, true));
        rules.add(rule("HTML", true, List.of("html", "htm"), null, true));
        rules.add(rule("MD", true, List.of("md", "markdown"), null, true));
        rules.add(rule("EPUB", true, List.of("epub"), null, true));
        rules.add(rule("ARCHIVE", true, List.of("zip", "jar", "war", "ear", "7z", "tar", "tgz", "gz", "bz2", "tbz2", "xz", "txz"), null, true));
        rules.add(rule("IMAGE", true, List.of("bmp", "png", "jpg", "jpeg", "gif", "webp"), 10L * 1024 * 1024, true));
        dto.setFormats(rules);
        return dto;
    }

    private static UploadFormatsConfigDTO.UploadFormatRuleDTO rule(
            String format,
            boolean enabled,
            List<String> exts,
            Long maxBytes,
            boolean parseEnabled
    ) {
        UploadFormatsConfigDTO.UploadFormatRuleDTO r = new UploadFormatsConfigDTO.UploadFormatRuleDTO();
        r.setFormat(format);
        r.setEnabled(enabled);
        r.setExtensions(exts);
        r.setMaxFileSizeBytes(maxBytes);
        r.setParseEnabled(parseEnabled);
        return r;
    }

    private UploadFormatsConfigDTO normalize(UploadFormatsConfigDTO in) {
        UploadFormatsConfigDTO dto = in == null ? UploadFormatsConfigDTO.empty() : in;
        dto.setEnabled(Boolean.TRUE.equals(dto.getEnabled()));
        dto.setMaxFilesPerRequest(clampInt(dto.getMaxFilesPerRequest(), 1, 100000, 100000));
        dto.setMaxFileSizeBytes(clampLong(dto.getMaxFileSizeBytes(), 1L, 500L * 1024 * 1024 * 1024, 500L * 1024 * 1024 * 1024));
        dto.setMaxTotalSizeBytes(clampLong(dto.getMaxTotalSizeBytes(), 1L, 2L * 1024 * 1024 * 1024 * 1024, 2L * 1024 * 1024 * 1024 * 1024));
        dto.setParseTimeoutMillis(clampInt(dto.getParseTimeoutMillis(), 1000, 86_400_000, 86_400_000));
        dto.setParseMaxChars(clampLong(dto.getParseMaxChars(), 1000L, 10_000_000_000L, 10_000_000_000L));

        List<UploadFormatsConfigDTO.UploadFormatRuleDTO> normalizedRules = new ArrayList<>();
        if (dto.getFormats() != null) {
            for (UploadFormatsConfigDTO.UploadFormatRuleDTO r : dto.getFormats()) {
                UploadFormatsConfigDTO.UploadFormatRuleDTO nr = normalizeRule(r);
                if (nr != null) normalizedRules.add(nr);
            }
        }
        dto.setFormats(normalizedRules);
        return dto;
    }

    private static UploadFormatsConfigDTO.UploadFormatRuleDTO normalizeRule(UploadFormatsConfigDTO.UploadFormatRuleDTO r) {
        if (r == null) return null;
        UploadFormatsConfigDTO.UploadFormatRuleDTO out = new UploadFormatsConfigDTO.UploadFormatRuleDTO();
        String fmt = r.getFormat() == null ? "" : r.getFormat().trim().toUpperCase(Locale.ROOT);
        if (fmt.isBlank()) return null;
        out.setFormat(fmt);
        out.setEnabled(Boolean.TRUE.equals(r.getEnabled()));
        out.setParseEnabled(Boolean.TRUE.equals(r.getParseEnabled()));

        if (r.getMaxFileSizeBytes() != null && r.getMaxFileSizeBytes() > 0) {
            out.setMaxFileSizeBytes(r.getMaxFileSizeBytes());
        }

        Set<String> exts = new LinkedHashSet<>();
        if (r.getExtensions() != null) {
            for (String e : r.getExtensions()) {
                String ne = normExt(e);
                if (ne != null) exts.add(ne);
            }
        }
        out.setExtensions(new ArrayList<>(exts));
        return out;
    }

    private static String normExt(String ext) {
        if (ext == null) return null;
        String t = ext.trim().toLowerCase(Locale.ROOT);
        if (t.startsWith(".")) t = t.substring(1);
        if (t.isBlank()) return null;
        if (t.length() > 16) return null;
        if (!t.matches("[a-z0-9]+")) return null;
        return t;
    }

    private static int clampInt(Integer v, int min, int max, int def) {
        int x = v == null ? def : v;
        if (x < min) x = min;
        if (x > max) x = max;
        return x;
    }

    private static long clampLong(Long v, long min, long max, long def) {
        long x = v == null ? def : v;
        if (x < min) x = min;
        if (x > max) x = max;
        return x;
    }
}
