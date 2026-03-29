package com.example.EnterpriseRagCommunity.service.monitor;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.poi.extractor.ExtractorFactory;
import org.apache.poi.extractor.POITextExtractor;
import org.apache.tika.Tika;
import org.apache.commons.compress.archivers.ArchiveInputStream;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

final class FileAssetExtractionSupport {

    private FileAssetExtractionSupport() {
    }

    static void putIfAbsentWhenNonBlank(Map<String, Object> archiveMeta, String key, String value) {
        if (archiveMeta == null || key == null || key.isBlank()) return;
        if (value != null && !value.isBlank()) archiveMeta.putIfAbsent(key, value);
    }

    static String fallbackContainerName(String containerName) {
        return (containerName == null || containerName.isBlank()) ? ("unknown_" + UUID.randomUUID() + ".bin") : containerName;
    }

    static String keepReasonOrDefault(String existing, String fallback) {
        return existing == null ? fallback : existing;
    }

    static Path safeNestedUnpackDir(Path target, Path outDir) {
        Path subDir = target.getParent().resolve(target.getFileName().toString() + "__unpacked").normalize();
        if (!subDir.startsWith(outDir)) subDir = outDir.resolve("__unpacked_" + UUID.randomUUID()).normalize();
        return subDir;
    }

    static void closeArchiveInputStreamQuietly(ArchiveInputStream archiveIn) {
        if (archiveIn == null) return;
        try {
            archiveIn.close();
        } catch (Exception ignore) {
        }
    }

    static Path safeResolveUnder(Path root, String rel) {
        if (root == null) return null;
        if (rel == null || rel.isBlank()) return null;
        Path p;
        try {
            p = root.resolve(rel.replace('\\', '/')).normalize();
        } catch (Exception e) {
            return null;
        }
        return p.startsWith(root) ? p : null;
    }

    static void appendExtractedFileBlock(StringBuilder out, String virtualPath, String text) {
        if (out == null) return;
        String p = virtualPath == null ? "" : virtualPath.trim();
        String t = text == null ? "" : text.trim();
        if (t.isBlank()) return;
        out.append("FILE: ").append(p).append('\n');
        out.append(t).append('\n');
        out.append('\n');
    }

    static boolean isSupportedInnerExtForExtraction(String ext) {
        if (ext == null || ext.isBlank()) return false;
        String e = ext.trim().toLowerCase(Locale.ROOT);
        if (isArchiveExt(e)) return false;
        return e.equals("pdf")
                || e.equals("txt") || e.equals("md") || e.equals("markdown") || e.equals("csv") || e.equals("json")
                || e.equals("html") || e.equals("htm")
                || e.equals("doc") || e.equals("docx")
                || e.equals("xls") || e.equals("xlsx")
                || e.equals("ppt") || e.equals("pptx")
                || e.equals("epub") || e.equals("mobi");
    }

    static void deleteDirQuietly(Path dir) {
        if (dir == null) return;
        try {
            if (!Files.exists(dir)) return;
            List<Path> all;
            try (var s = Files.walk(dir)) {
                all = s.sorted(Comparator.reverseOrder()).toList();
            }
            for (Path p : all) {
                try {
                    Files.deleteIfExists(p);
                } catch (Exception ignored) {
                }
            }
        } catch (Exception ignored) {
        }
    }

    static boolean looksLike7zBytes(byte[] bytes) {
        if (bytes == null || bytes.length < 6) return false;
        return (bytes[0] == 0x37) && (bytes[1] == 0x7A) && ((bytes[2] & 0xFF) == 0xBC) && ((bytes[3] & 0xFF) == 0xAF) && (bytes[4] == 0x27) && (bytes[5] == 0x1C);
    }

    static boolean looksLikeArchiveBytes(byte[] bytes) {
        if (bytes == null || bytes.length < 4) return false;
        int b0 = bytes[0] & 0xFF;
        int b1 = bytes[1] & 0xFF;
        int b2 = bytes[2] & 0xFF;
        int b3 = bytes[3] & 0xFF;
        if (b0 == 0x50 && b1 == 0x4B) return true;
        if (b0 == 0x37 && b1 == 0x7A && b2 == 0xBC && b3 == 0xAF) return true;
        if (b0 == 0x1F && b1 == 0x8B) return true;
        if (b0 == 0x42 && b1 == 0x5A) return true;
        if (b0 == 0xFD && b1 == 0x37 && b2 == 0x7A && b3 == 0x58) return true;
        return false;
    }

    static boolean isPathTraversal(String name) {
        if (name == null) return true;
        String s = name.trim();
        if (s.isBlank()) return true;
        if (s.startsWith("/") || s.startsWith("\\")) return true;
        if (s.matches("^[a-zA-Z]:.*")) return true;
        return s.contains("..\\") || s.contains("../") || s.equals("..");
    }

    static String stripHtmlToText(String html) {
        if (html == null) return "";
        String txt = html
                .replaceAll("(?is)<script[^>]*>.*?</script>", " ")
                .replaceAll("(?is)<style[^>]*>.*?</style>", " ")
                .replaceAll("(?is)<[^>]+>", " ")
                .replace("&nbsp;", " ")
                .replace("&amp;", "&")
                .replace("&lt;", "<")
                .replace("&gt;", ">")
                .replace("&quot;", "\"")
                .replace("&#39;", "'");
        return txt.replaceAll("\\s+", " ").trim();
    }

    static String extractPdf(InputStream is, int maxChars, Map<String, Object> meta) throws Exception {
        try (PDDocument doc = Loader.loadPDF(is.readAllBytes())) {
            if (meta != null) meta.put("pages", doc.getNumberOfPages());
            PDFTextStripper stripper = new PDFTextStripper();
            String txt = stripper.getText(doc);
            return truncate(txt, maxChars);
        }
    }

    static String extractWithTika(InputStream is, int maxChars, Map<String, Object> meta) throws Exception {
        Tika tika = new Tika();
        String txt = tika.parseToString(is);
        return truncate(txt, maxChars);
    }

    static String extractOffice(InputStream is, int maxChars) throws Exception {
        try (POITextExtractor extractor = ExtractorFactory.createExtractor(is)) {
            String txt = extractor.getText();
            return truncate(txt, maxChars);
        }
    }

    static boolean isArchiveExt(String ext) {
        if (ext == null || ext.isBlank()) return false;
        String e = ext.trim().toLowerCase(Locale.ROOT);
        return e.equals("zip") || e.equals("jar") || e.equals("war") || e.equals("ear")
                || e.equals("7z") || e.equals("tar") || e.equals("tgz") || e.equals("gz")
                || e.equals("bz2") || e.equals("tbz2") || e.equals("xz") || e.equals("txz");
    }

    static boolean isOfficeExt(String ext) {
        if (ext == null || ext.isBlank()) return false;
        String e = ext.trim().toLowerCase(Locale.ROOT);
        return e.equals("doc") || e.equals("docx") || e.equals("dot") || e.equals("dotx")
                || e.equals("ppt") || e.equals("pptx") || e.equals("pps") || e.equals("ppsx")
                || e.equals("xls") || e.equals("xlsx") || e.equals("xlt") || e.equals("xltx");
    }

    static void recordArchiveEntryError(Map<String, Object> archiveMeta, String entryPath, Exception ex) {
        if (archiveMeta == null) return;
        if (entryPath == null || entryPath.isBlank()) return;
        Object cur = archiveMeta.get("entryErrors");
        List<Map<String, Object>> list;
        if (cur instanceof List<?> l) {
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> cast = (List<Map<String, Object>>) l;
            list = cast;
        } else {
            list = new ArrayList<>();
            archiveMeta.put("entryErrors", list);
        }
        if (list.size() >= 5) {
            archiveMeta.put("entryErrorsTruncated", true);
            return;
        }
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("path", entryPath);
        item.put("error", safeMsg(ex));
        list.add(item);
    }

    static void recordArchiveParsedEntry(Map<String, Object> archiveMeta, String entryPath, int depth, String text) {
        if (archiveMeta == null) return;
        if (entryPath == null || entryPath.isBlank()) return;
        Object cur = archiveMeta.get("parsedEntries");
        List<Map<String, Object>> list;
        if (cur instanceof List<?> l) {
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> cast = (List<Map<String, Object>>) l;
            list = cast;
        } else {
            list = new ArrayList<>();
            archiveMeta.put("parsedEntries", list);
        }
        if (list.size() >= 50) {
            archiveMeta.put("parsedEntriesTruncated", true);
            return;
        }
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("path", entryPath);
        item.put("depth", depth);
        item.put("chars", text == null ? 0 : text.length());
        list.add(item);
    }

    @SuppressWarnings("unchecked")
    static List<Map<String, Object>> takeList(Object v) {
        if (v instanceof List<?> l) return (List<Map<String, Object>>) l;
        return List.of();
    }

    static String extractPdf(Path path, int maxChars, Map<String, Object> meta) throws Exception {
        try (PDDocument doc = Loader.loadPDF(path.toFile())) {
            meta.put("pages", doc.getNumberOfPages());
            PDFTextStripper stripper = new PDFTextStripper();
            String txt = stripper.getText(doc);
            return truncate(txt, maxChars);
        }
    }

    static String extractWithTika(Path path, int maxChars, Map<String, Object> meta) throws Exception {
        Tika tika = new Tika();
        String txt = tika.parseToString(path.toFile());
        String detected = tika.detect(path.toFile());
        if (detected != null && !detected.isBlank()) meta.put("tikaDetectedType", detected);
        return truncate(txt, maxChars);
    }

    static String extractOffice(Path path, int maxChars) throws Exception {
        try (POITextExtractor extractor = ExtractorFactory.createExtractor(path.toFile())) {
            String txt = extractor.getText();
            return truncate(txt, maxChars);
        }
    }

    static String extractPlain(Path path, int maxChars) throws Exception {
        byte[] bytes = Files.readAllBytes(path);
        String txt = new String(bytes, StandardCharsets.UTF_8);
        return truncate(txt, maxChars);
    }

    static String extractHtml(Path path, int maxChars) throws Exception {
        String html = extractPlain(path, maxChars * 2);
        String txt = stripHtmlToText(html);
        return truncate(txt, maxChars);
    }

    static String truncate(String s, int maxChars) {
        if (s == null) return null;
        int m = Math.max(0, maxChars);
        if (m == 0) return "";
        if (s.length() <= m) return s;
        return s.substring(0, m);
    }

    static long estimateTokens(String text) {
        if (text == null) return 0L;
        String t = text.trim();
        if (t.isEmpty()) return 0L;
        return Math.max(1L, (t.length() + 3L) / 4L);
    }

    static void fillDerivedImageCount(Map<String, Object> meta, String finalText) {
        if (meta == null) return;
        if (meta.get("imageCount") != null) return;
        Integer pages = null;
        Object pv = meta.get("pages");
        if (pv instanceof Number n) pages = n.intValue();
        if (pv != null && pages == null) {
            try {
                pages = Integer.parseInt(pv.toString().trim());
            } catch (Exception ignored) {
            }
        }
        String t = finalText == null ? "" : finalText.trim();
        if (pages != null && pages > 0 && t.isEmpty()) {
            meta.put("imageCount", pages);
        } else {
            meta.put("imageCount", 0);
        }
    }

    static boolean isImageExt(String ext) {
        if (ext == null || ext.isBlank()) return false;
        return ext.equals("bmp") || ext.equals("png") || ext.equals("jpg") || ext.equals("jpeg") || ext.equals("gif") || ext.equals("webp");
    }

    static byte[] bufferedImageToPng(BufferedImage bi) {
        if (bi == null) return null;
        try {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            ImageIO.write(bi, "png", baos);
            return baos.toByteArray();
        } catch (Exception ignored) {
            return null;
        }
    }

    static String appendImagePlaceholders(String text, List<Map<String, Object>> extractedImages) {
        String base = text == null ? "" : text;
        if (extractedImages == null || extractedImages.isEmpty()) return base;

        List<String> placeholders = new ArrayList<>();
        for (Map<String, Object> it : extractedImages) {
            if (it == null) continue;
            Object p = it.get("placeholder");
            if (p == null) continue;
            String s = String.valueOf(p).trim();
            if (s.isEmpty()) continue;
            if (!placeholders.contains(s)) placeholders.add(s);
        }
        if (placeholders.isEmpty()) return base;

        if (base.contains("[[IMAGE_")) return base;

        if (base.trim().isEmpty()) {
            StringBuilder sb = new StringBuilder(base);
            if (!sb.isEmpty() && sb.charAt(sb.length() - 1) != '\n') sb.append('\n');
            sb.append('\n');
            for (String p : placeholders) {
                sb.append(p).append('\n');
            }
            return sb.toString();
        }

        int len = base.length();
        int n = placeholders.size();
        int maxScan = 1200;

        StringBuilder sb = new StringBuilder(len + n * 32);
        sb.append(base);

        int insertedChars = 0;
        int minBasePos = 0;
        for (int i = 0; i < n; i++) {
            int target = (int) Math.round(((double) (i + 1) / (double) (n + 1)) * (double) len);
            if (target < minBasePos) target = minBasePos;
            if (target > len) target = len;

            int pos = -1;
            for (int d = 0; d <= maxScan; d++) {
                int left = target - d;
                if (left >= minBasePos && left >= 0 && left <= len) {
                    if (left != 0 && (left >= len || Character.isWhitespace(base.charAt(left - 1)) || Character.isWhitespace(base.charAt(left)))) {
                        pos = left;
                        break;
                    }
                }
                int right = target + d;
                if (right >= minBasePos && right >= 0 && right <= len) {
                    if (right <= 0 || right >= len || Character.isWhitespace(base.charAt(right - 1)) || Character.isWhitespace(base.charAt(right))) {
                        pos = right;
                        break;
                    }
                }
            }

            if (pos < 0) pos = Math.min(len, Math.max(minBasePos, target));

            String token = "\n\n" + placeholders.get(i) + "\n\n";
            sb.insert(pos + insertedChars, token);
            insertedChars += token.length();
            minBasePos = Math.min(len, pos + 1);
        }

        return sb.toString();
    }

    static String guessMimeFromExt(String ext) {
        if (ext == null) return null;
        String e = ext.toLowerCase(Locale.ROOT);
        if (e.equals("png")) return "image/png";
        if (e.equals("jpg") || e.equals("jpeg")) return "image/jpeg";
        if (e.equals("gif")) return "image/gif";
        if (e.equals("bmp")) return "image/bmp";
        if (e.equals("webp")) return "image/webp";
        return null;
    }

    static String extLowerOrNull(String fileName) {
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

    static String safeMsg(Throwable t) {
        if (t == null) return "解析失败";
        String m = t.getMessage();
        if (m == null || m.isBlank()) return t.getClass().getSimpleName();
        String s = m.trim();
        return s.length() > 1000 ? s.substring(0, 1000) : s;
    }
}
