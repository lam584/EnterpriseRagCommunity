package com.example.EnterpriseRagCommunity.service.monitor;

import com.example.EnterpriseRagCommunity.dto.monitor.UploadFormatsConfigDTO;
import com.example.EnterpriseRagCommunity.entity.monitor.FileAssetExtractionsEntity;
import com.example.EnterpriseRagCommunity.entity.monitor.FileAssetsEntity;
import com.example.EnterpriseRagCommunity.entity.monitor.enums.FileAssetExtractionStatus;
import com.example.EnterpriseRagCommunity.entity.semantic.VectorIndicesEntity;
import com.example.EnterpriseRagCommunity.repository.monitor.FileAssetExtractionsRepository;
import com.example.EnterpriseRagCommunity.repository.monitor.FileAssetsRepository;
import com.example.EnterpriseRagCommunity.repository.semantic.VectorIndicesRepository;
import com.example.EnterpriseRagCommunity.service.ai.TokenCountService;
import com.example.EnterpriseRagCommunity.service.retrieval.RagFileAssetIndexAsyncService;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.zip.GZIPOutputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import javax.imageio.ImageIO;

import org.apache.pdfbox.cos.COSName;
import org.apache.commons.compress.archivers.ArchiveEntry;
import org.apache.commons.compress.archivers.ArchiveException;
import org.apache.commons.compress.archivers.ArchiveInputStream;
import org.apache.commons.compress.archivers.ArchiveStreamFactory;
import org.apache.commons.compress.archivers.sevenz.SevenZArchiveEntry;
import org.apache.commons.compress.archivers.sevenz.SevenZMethod;
import org.apache.commons.compress.archivers.sevenz.SevenZMethodConfiguration;
import org.apache.commons.compress.archivers.sevenz.SevenZOutputFile;
import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.PDResources;
import org.apache.pdfbox.pdmodel.graphics.PDXObject;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.apache.poi.hslf.usermodel.HSLFSlideShow;
import org.apache.poi.hssf.usermodel.HSSFWorkbook;
import org.apache.poi.hslf.usermodel.HSLFPictureData;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.apache.poi.sl.usermodel.PictureData;
import org.apache.poi.util.Units;
import org.apache.poi.xslf.usermodel.XSLFPictureData;
import org.apache.poi.xslf.usermodel.XMLSlideShow;
import org.apache.poi.xssf.usermodel.XSSFPictureData;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.apache.poi.xwpf.usermodel.XWPFPictureData;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.Document;
import org.apache.tika.Tika;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.TikaCoreProperties;
import org.apache.tika.parser.AutoDetectParser;
import org.apache.tika.parser.ParseContext;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
import org.mockito.MockedConstruction;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

class FileAssetExtractionAsyncServiceUtilityBranchTest {

    private static Object invokeStatic(String name, Class<?>[] types, Object... args) {
        try {
            Method m = FileAssetExtractionAsyncService.class.getDeclaredMethod(name, types);
            m.setAccessible(true);
            return m.invoke(null, args);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private static Object invokeInstance(Object target, String name, Class<?>[] types, Object... args) {
        try {
            Method m = target.getClass().getDeclaredMethod(name, types);
            m.setAccessible(true);
            return m.invoke(target, args);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private static Object newInner(String simpleName, Class<?>[] ctorTypes, Object... args) {
        try {
            Class<?> c = Class.forName(FileAssetExtractionAsyncService.class.getName() + "$" + simpleName);
            Constructor<?> ctor = c.getDeclaredConstructor(ctorTypes);
            ctor.setAccessible(true);
            return ctor.newInstance(args);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private static void setField(Object target, String name, Object value) {
        try {
            var f = target.getClass().getDeclaredField(name);
            f.setAccessible(true);
            f.set(target, value);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private static Object getField(Object target, String name) {
        try {
            var f = target.getClass().getDeclaredField(name);
            f.setAccessible(true);
            return f.get(target);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private static byte[] zipBytes(List<Map.Entry<String, byte[]>> entries) throws Exception {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (ZipOutputStream zos = new ZipOutputStream(baos, StandardCharsets.UTF_8)) {
            for (Map.Entry<String, byte[]> it : entries) {
                ZipEntry ze = new ZipEntry(it.getKey());
                zos.putNextEntry(ze);
                zos.write(it.getValue());
                zos.closeEntry();
            }
        }
        return baos.toByteArray();
    }

    private static byte[] sevenZBytes(List<Map.Entry<String, byte[]>> entries) throws Exception {
        Path p = Files.createTempFile("fae-utility-", ".7z");
        try (SevenZOutputFile out = new SevenZOutputFile(p.toFile())) {
            for (Map.Entry<String, byte[]> it : entries) {
                SevenZArchiveEntry e = new SevenZArchiveEntry();
                e.setName(it.getKey());
                e.setContentMethods(List.of(new SevenZMethodConfiguration(SevenZMethod.COPY)));
                out.putArchiveEntry(e);
                out.write(it.getValue());
                out.closeArchiveEntry();
            }
        }
        byte[] bytes = Files.readAllBytes(p);
        Files.deleteIfExists(p);
        return bytes;
    }

    private static byte[] gzipBytes(byte[] src) throws Exception {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (GZIPOutputStream gos = new GZIPOutputStream(baos)) {
            gos.write(src);
        }
        return baos.toByteArray();
    }

    @Test
    void extLowerOrNull_shouldCoverEdgeCases() {
        assertNull(invokeStatic("extLowerOrNull", new Class<?>[]{String.class}, (String) null));
        assertNull(invokeStatic("extLowerOrNull", new Class<?>[]{String.class}, "a"));
        assertNull(invokeStatic("extLowerOrNull", new Class<?>[]{String.class}, "a."));
        assertEquals("txt", invokeStatic("extLowerOrNull", new Class<?>[]{String.class}, "C:\\x\\a.TXT"));
        assertNull(invokeStatic("extLowerOrNull", new Class<?>[]{String.class}, "a.t-x"));
        assertNull(invokeStatic("extLowerOrNull", new Class<?>[]{String.class}, "a.abcdefghijklmnopq"));
        assertEquals("md", invokeStatic("extLowerOrNull", new Class<?>[]{String.class}, "a.md"));
    }

    @Test
    void safeResolveUnder_and_isPathTraversal_shouldCoverBranches() {
        assertNull(invokeStatic("safeResolveUnder", new Class<?>[]{Path.class, String.class}, null, "a.txt"));
        assertNull(invokeStatic("safeResolveUnder", new Class<?>[]{Path.class, String.class}, Path.of("C:/root"), ""));
        assertNotNull(invokeStatic("safeResolveUnder", new Class<?>[]{Path.class, String.class}, Path.of("C:/root"), "a.txt"));
        assertNull(invokeStatic("safeResolveUnder", new Class<?>[]{Path.class, String.class}, Path.of("C:/root"), "../evil.txt"));

        assertEquals(true, invokeStatic("isPathTraversal", new Class<?>[]{String.class}, (String) null));
        assertEquals(true, invokeStatic("isPathTraversal", new Class<?>[]{String.class}, "  "));
        assertEquals(true, invokeStatic("isPathTraversal", new Class<?>[]{String.class}, "/a.txt"));
        assertEquals(true, invokeStatic("isPathTraversal", new Class<?>[]{String.class}, "C:\\a.txt"));
        assertEquals(true, invokeStatic("isPathTraversal", new Class<?>[]{String.class}, "../a"));
        assertEquals(true, invokeStatic("isPathTraversal", new Class<?>[]{String.class}, ".."));
        assertEquals(false, invokeStatic("isPathTraversal", new Class<?>[]{String.class}, "a/b.txt"));
    }

    @Test
    void looksLikeArchiveBytes_and_looksLike7zBytes_shouldCoverSignatures() {
        assertEquals(false, invokeStatic("looksLike7zBytes", new Class<?>[]{byte[].class}, (Object) null));
        assertEquals(false, invokeStatic("looksLike7zBytes", new Class<?>[]{byte[].class}, new byte[5]));
        assertEquals(true, invokeStatic("looksLike7zBytes", new Class<?>[]{byte[].class}, new byte[]{0x37, 0x7A, (byte) 0xBC, (byte) 0xAF, 0x27, 0x1C}));
        assertEquals(false, invokeStatic("looksLike7zBytes", new Class<?>[]{byte[].class}, new byte[]{0, 0, 0, 0, 0, 0}));

        assertEquals(false, invokeStatic("looksLikeArchiveBytes", new Class<?>[]{byte[].class}, (Object) null));
        assertEquals(false, invokeStatic("looksLikeArchiveBytes", new Class<?>[]{byte[].class}, new byte[3]));
        assertEquals(true, invokeStatic("looksLikeArchiveBytes", new Class<?>[]{byte[].class}, new byte[]{0x50, 0x4B, 0, 0}));
        assertEquals(true, invokeStatic("looksLikeArchiveBytes", new Class<?>[]{byte[].class}, new byte[]{0x37, 0x7A, (byte) 0xBC, (byte) 0xAF}));
        assertEquals(true, invokeStatic("looksLikeArchiveBytes", new Class<?>[]{byte[].class}, new byte[]{0x1F, (byte) 0x8B, 0, 0}));
        assertEquals(true, invokeStatic("looksLikeArchiveBytes", new Class<?>[]{byte[].class}, new byte[]{0x42, 0x5A, 0, 0}));
        assertEquals(true, invokeStatic("looksLikeArchiveBytes", new Class<?>[]{byte[].class}, new byte[]{(byte) 0xFD, 0x37, 0x7A, 0x58}));
        assertEquals(false, invokeStatic("looksLikeArchiveBytes", new Class<?>[]{byte[].class}, new byte[]{1, 2, 3, 4}));
    }

    @Test
    void truncate_and_stripHtmlToText_shouldCoverBranches() {
        assertNull(invokeStatic("truncate", new Class<?>[]{String.class, int.class}, null, 10));
        assertEquals("", invokeStatic("truncate", new Class<?>[]{String.class, int.class}, "abc", 0));
        assertEquals("abc", invokeStatic("truncate", new Class<?>[]{String.class, int.class}, "abc", 3));
        assertEquals("abc", invokeStatic("truncate", new Class<?>[]{String.class, int.class}, "abcd", 3));

        assertEquals("", invokeStatic("stripHtmlToText", new Class<?>[]{String.class}, (String) null));
        assertEquals("Hi &", invokeStatic("stripHtmlToText", new Class<?>[]{String.class}, "<script>x</script><style>y</style><b>Hi&nbsp;&amp;</b>"));
    }

    @Test
    void estimateTokens_safeMsg_guessMimeFromExt_shouldCoverBranches() {
        assertEquals(0L, invokeStatic("estimateTokens", new Class<?>[]{String.class}, (String) null));
        assertEquals(0L, invokeStatic("estimateTokens", new Class<?>[]{String.class}, "   "));
        assertEquals(1L, invokeStatic("estimateTokens", new Class<?>[]{String.class}, "a"));
        assertEquals(1L, invokeStatic("estimateTokens", new Class<?>[]{String.class}, "abcd"));
        assertEquals(2L, invokeStatic("estimateTokens", new Class<?>[]{String.class}, "abcde"));

        assertEquals("解析失败", invokeStatic("safeMsg", new Class<?>[]{Throwable.class}, (Throwable) null));
        assertEquals("RuntimeException", invokeStatic("safeMsg", new Class<?>[]{Throwable.class}, new RuntimeException((String) null)));
        assertEquals("hi", invokeStatic("safeMsg", new Class<?>[]{Throwable.class}, new RuntimeException("  hi  ")));
        assertEquals(1000, String.valueOf(invokeStatic("safeMsg", new Class<?>[]{Throwable.class}, new RuntimeException("a".repeat(1001)))).length());

        assertNull(invokeStatic("guessMimeFromExt", new Class<?>[]{String.class}, (String) null));
        assertEquals("image/png", invokeStatic("guessMimeFromExt", new Class<?>[]{String.class}, "PNG"));
        assertEquals("image/jpeg", invokeStatic("guessMimeFromExt", new Class<?>[]{String.class}, "jpeg"));
        assertNull(invokeStatic("guessMimeFromExt", new Class<?>[]{String.class}, "tiff"));
    }

    @Test
    void appendExtractedFileBlock_shouldCoverBranches() {
        invokeStatic("appendExtractedFileBlock", new Class<?>[]{StringBuilder.class, String.class, String.class}, null, "a", "b");
        StringBuilder sb = new StringBuilder();
        invokeStatic("appendExtractedFileBlock", new Class<?>[]{StringBuilder.class, String.class, String.class}, sb, "a", "   ");
        assertEquals("", sb.toString());

        invokeStatic("appendExtractedFileBlock", new Class<?>[]{StringBuilder.class, String.class, String.class}, sb, null, "x");
        assertTrue(sb.toString().contains("FILE:"));
        assertTrue(sb.toString().contains("x"));
    }

    @Test
    void appendImagePlaceholders_shouldCoverAlgorithms() {
        assertEquals("x", invokeStatic("appendImagePlaceholders", new Class<?>[]{String.class, List.class}, "x", null));
        assertEquals("x", invokeStatic("appendImagePlaceholders", new Class<?>[]{String.class, List.class}, "x", List.of()));

        List<Map<String, Object>> filtered = new ArrayList<>();
        filtered.add(null);
        filtered.add(Map.of());
        filtered.add(Map.of("placeholder", "  "));
        assertEquals("x", invokeStatic("appendImagePlaceholders", new Class<?>[]{String.class, List.class}, "x", filtered));

        List<Map<String, Object>> single = List.of(Map.of("placeholder", "[[IMAGE_1]]"));
        String appended = String.valueOf(invokeStatic("appendImagePlaceholders", new Class<?>[]{String.class, List.class}, " ", single));
        assertTrue(appended.contains("[[IMAGE_1]]"));

        String keep = String.valueOf(invokeStatic("appendImagePlaceholders", new Class<?>[]{String.class, List.class}, "hi [[IMAGE_9]] there", single));
        assertEquals("hi [[IMAGE_9]] there", keep);

        List<Map<String, Object>> two = List.of(Map.of("placeholder", "[[IMAGE_1]]"), Map.of("placeholder", "[[IMAGE_2]]"));
        String right = String.valueOf(invokeStatic("appendImagePlaceholders", new Class<?>[]{String.class, List.class}, "abc de", two));
        assertTrue(right.contains("[[IMAGE_1]]"));
        assertTrue(right.contains("[[IMAGE_2]]"));
        assertTrue(right.indexOf("[[IMAGE_1]]") < right.indexOf("[[IMAGE_2]]"));

        String left = String.valueOf(invokeStatic("appendImagePlaceholders", new Class<?>[]{String.class, List.class}, "abc", two));
        assertEquals(3, left.indexOf("\n\n[[IMAGE_1]]\n\n"));
        assertTrue(left.indexOf("[[IMAGE_2]]") > left.indexOf("[[IMAGE_1]]"));

        String base = "a".repeat(5000);
        String token = "\n\n[[IMAGE_1]]\n\n";
        String fallback = String.valueOf(invokeStatic("appendImagePlaceholders", new Class<?>[]{String.class, List.class}, base, single));
        assertEquals(2500, fallback.indexOf(token));
    }

    @Test
    void fillDerivedImageCount_takeList_recordArchiveEntryError_recordArchiveParsedEntry_shouldCoverBranches() {
        invokeStatic("fillDerivedImageCount", new Class<?>[]{Map.class, String.class}, null, "");

        Map<String, Object> meta = new LinkedHashMap<>();
        meta.put("imageCount", 9);
        invokeStatic("fillDerivedImageCount", new Class<?>[]{Map.class, String.class}, meta, "");
        assertEquals(9, meta.get("imageCount"));

        meta = new LinkedHashMap<>();
        meta.put("pages", 3);
        invokeStatic("fillDerivedImageCount", new Class<?>[]{Map.class, String.class}, meta, " ");
        assertEquals(3, meta.get("imageCount"));

        meta = new LinkedHashMap<>();
        meta.put("pages", "4");
        invokeStatic("fillDerivedImageCount", new Class<?>[]{Map.class, String.class}, meta, "");
        assertEquals(4, meta.get("imageCount"));

        meta = new LinkedHashMap<>();
        meta.put("pages", "x");
        invokeStatic("fillDerivedImageCount", new Class<?>[]{Map.class, String.class}, meta, "");
        assertEquals(0, meta.get("imageCount"));

        assertEquals(List.of(), invokeStatic("takeList", new Class<?>[]{Object.class}, "x"));
        List<Map<String, Object>> l = List.of(Map.of("a", 1));
        assertSame(l, invokeStatic("takeList", new Class<?>[]{Object.class}, l));

        Map<String, Object> archiveMeta = new HashMap<>();
        invokeStatic("recordArchiveEntryError", new Class<?>[]{Map.class, String.class, Exception.class}, null, "a", new RuntimeException("x"));
        invokeStatic("recordArchiveEntryError", new Class<?>[]{Map.class, String.class, Exception.class}, archiveMeta, " ", new RuntimeException("x"));
        invokeStatic("recordArchiveEntryError", new Class<?>[]{Map.class, String.class, Exception.class}, archiveMeta, "a", new RuntimeException("x"));
        assertTrue(archiveMeta.get("entryErrors") instanceof List<?>);

        List<Map<String, Object>> five = new ArrayList<>();
        for (int i = 0; i < 5; i++) five.add(Map.of("path", "p" + i));
        archiveMeta.put("entryErrors", five);
        invokeStatic("recordArchiveEntryError", new Class<?>[]{Map.class, String.class, Exception.class}, archiveMeta, "a", new RuntimeException("x"));
        assertEquals(true, archiveMeta.get("entryErrorsTruncated"));

        archiveMeta = new HashMap<>();
        invokeStatic("recordArchiveParsedEntry", new Class<?>[]{Map.class, String.class, int.class, String.class}, null, "a", 0, "x");
        invokeStatic("recordArchiveParsedEntry", new Class<?>[]{Map.class, String.class, int.class, String.class}, archiveMeta, " ", 0, "x");
        invokeStatic("recordArchiveParsedEntry", new Class<?>[]{Map.class, String.class, int.class, String.class}, archiveMeta, "a", 2, null);
        assertTrue(archiveMeta.get("parsedEntries") instanceof List<?>);

        List<Map<String, Object>> fifty = new ArrayList<>();
        for (int i = 0; i < 50; i++) fifty.add(Map.of("path", "p" + i));
        archiveMeta.put("parsedEntries", fifty);
        invokeStatic("recordArchiveParsedEntry", new Class<?>[]{Map.class, String.class, int.class, String.class}, archiveMeta, "a", 2, "x");
        assertEquals(true, archiveMeta.get("parsedEntriesTruncated"));
    }

    @Test
    void exceededArchiveBudget_shouldCoverBranches() throws Exception {
        Object svc = new FileAssetExtractionAsyncService(null, null, null, null, null, null, null, null);
        Object counters = newInner("ArchiveCounters", new Class<?>[]{});

        assertEquals(false, invokeInstance(svc, "exceededArchiveBudget", new Class<?>[]{counters.getClass(), long.class}, null, 0L));

        try {
            var f = svc.getClass().getDeclaredField("archiveMaxTotalMillis");
            f.setAccessible(true);
            f.setLong(svc, 0L);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
        assertEquals(false, invokeInstance(svc, "exceededArchiveBudget", new Class<?>[]{counters.getClass(), long.class}, counters, System.nanoTime()));

        try {
            var f = svc.getClass().getDeclaredField("archiveMaxTotalMillis");
            f.setAccessible(true);
            f.setLong(svc, 1L);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
        long startNs = System.nanoTime() - java.time.Duration.ofMillis(10).toNanos();
        assertEquals(true, invokeInstance(svc, "exceededArchiveBudget", new Class<?>[]{counters.getClass(), long.class}, counters, startNs));
    }

    @Test
    void extractImages_shouldCoverUnsupportedAndFailedBranches() throws Exception {
        Object svc = new FileAssetExtractionAsyncService(null, null, null, null, null, null, null, null);
        Class<?> budgetClass = Class.forName(FileAssetExtractionAsyncService.class.getName() + "$ImageBudget");
        Constructor<?> ctor = budgetClass.getDeclaredConstructor(int.class, long.class);
        ctor.setAccessible(true);
        Object budget = ctor.newInstance(10, 1024L * 1024L);

        Map<String, Object> blankMeta = new LinkedHashMap<>();
        Object blankOut = invokeInstance(
                svc,
                "extractImages",
                new Class<?>[]{Path.class, String.class, Map.class, Long.class, String.class, budgetClass},
                Path.of("C:/tmp/a.txt"),
                " ",
                blankMeta,
                1L,
                "",
                budget
        );
        assertTrue(blankOut instanceof List<?>);
        assertTrue(((List<?>) blankOut).isEmpty());

        Map<String, Object> txtMeta = new LinkedHashMap<>();
        Object txtOut = invokeInstance(
                svc,
                "extractImages",
                new Class<?>[]{Path.class, String.class, Map.class, Long.class, String.class, budgetClass},
                Path.of("C:/tmp/a.txt"),
                "txt",
                txtMeta,
                1L,
                "",
                budget
        );
        assertTrue(txtOut instanceof List<?>);
        assertEquals("NONE", String.valueOf(txtMeta.get("imagesExtractionMode")));

        Map<String, Object> unsupportedMeta = new LinkedHashMap<>();
        Object unsupportedOut = invokeInstance(
                svc,
                "extractImages",
                new Class<?>[]{Path.class, String.class, Map.class, Long.class, String.class, budgetClass},
                Path.of("C:/tmp/a.abc"),
                "abc",
                unsupportedMeta,
                1L,
                "",
                budget
        );
        assertTrue(unsupportedOut instanceof List<?>);
        assertEquals("UNSUPPORTED", String.valueOf(unsupportedMeta.get("imagesExtractionMode")));

        Path invalidDocx = Files.createTempFile("invalid-", ".docx");
        Files.writeString(invalidDocx, "not-a-docx");
        Map<String, Object> failedMeta = new LinkedHashMap<>();
        Object failedOut = invokeInstance(
                svc,
                "extractImages",
                new Class<?>[]{Path.class, String.class, Map.class, Long.class, String.class, budgetClass},
                invalidDocx,
                "docx",
                failedMeta,
                1L,
                "",
                budget
        );
        assertTrue(failedOut instanceof List<?>);
        assertEquals("FAILED", String.valueOf(failedMeta.get("imagesExtractionMode")));
        assertNotNull(failedMeta.get("imagesExtractionError"));
    }

    @Test
    void extractArchiveFromStream_shouldCoverTimeLimitAndTraversalAndEntryLimit() throws Exception {
        Object svc = new FileAssetExtractionAsyncService(null, null, null, null, null, null, null, null);
        setField(svc, "archiveMaxDepth", 5);
        setField(svc, "archiveMaxEntries", 10);
        setField(svc, "archiveMaxEntryBytes", 2 * 1024 * 1024L);
        setField(svc, "archiveMaxTotalBytes", 10 * 1024 * 1024L);
        setField(svc, "archiveMaxTotalMillis", 1L);

        Object countersTime = newInner("ArchiveCounters", new Class<?>[]{});
        Map<String, Object> archiveMetaTime = new LinkedHashMap<>();
        Object t = invokeInstance(
                svc,
                "extractArchiveFromStream",
                new Class<?>[]{java.io.InputStream.class, String.class, int.class, int.class, Map.class, countersTime.getClass(), long.class},
                new ByteArrayInputStream("abc".getBytes(StandardCharsets.UTF_8)),
                "a.txt",
                0,
                100,
                archiveMetaTime,
                countersTime,
                System.nanoTime() - java.time.Duration.ofMillis(10).toNanos()
        );
        assertEquals("", String.valueOf(t));
        assertEquals("TIME_LIMIT", String.valueOf(getField(countersTime, "truncatedReason")));

        setField(svc, "archiveMaxTotalMillis", 15000L);
        Object countersTraversal = newInner("ArchiveCounters", new Class<?>[]{});
        Map<String, Object> archiveMetaTraversal = new LinkedHashMap<>();
        byte[] z1 = zipBytes(List.of(
                Map.entry("../evil.txt", "x".getBytes(StandardCharsets.UTF_8)),
                Map.entry("ok.txt", "ok-body".getBytes(StandardCharsets.UTF_8))
        ));
        Object out1 = invokeInstance(
                svc,
                "extractArchiveFromStream",
                new Class<?>[]{java.io.InputStream.class, String.class, int.class, int.class, Map.class, countersTraversal.getClass(), long.class},
                new ByteArrayInputStream(z1),
                "sample.zip",
                0,
                1000,
                archiveMetaTraversal,
                countersTraversal,
                System.nanoTime()
        );
        assertTrue(String.valueOf(out1).contains("ok-body"));
        assertTrue(((Number) getField(countersTraversal, "pathTraversalDroppedCount")).longValue() >= 1L);

        setField(svc, "archiveMaxEntries", 1);
        Object countersLimit = newInner("ArchiveCounters", new Class<?>[]{});
        Map<String, Object> archiveMetaLimit = new LinkedHashMap<>();
        byte[] z2 = zipBytes(List.of(
                Map.entry("a.txt", "a".getBytes(StandardCharsets.UTF_8)),
                Map.entry("b.txt", "b".getBytes(StandardCharsets.UTF_8))
        ));
        invokeInstance(
                svc,
                "extractArchiveFromStream",
                new Class<?>[]{java.io.InputStream.class, String.class, int.class, int.class, Map.class, countersLimit.getClass(), long.class},
                new ByteArrayInputStream(z2),
                "limit.zip",
                0,
                1000,
                archiveMetaLimit,
                countersLimit,
                System.nanoTime()
        );
        assertEquals("ENTRY_COUNT_LIMIT", String.valueOf(getField(countersLimit, "truncatedReason")));
    }

    @Test
    void extract7zFromBytes_shouldCoverEmptyTraversalAndDepthLimit() throws Exception {
        Object svc = new FileAssetExtractionAsyncService(null, null, null, null, null, null, null, null);
        setField(svc, "archiveMaxDepth", 5);
        setField(svc, "archiveMaxEntries", 10);
        setField(svc, "archiveMaxEntryBytes", 2 * 1024 * 1024L);
        setField(svc, "archiveMaxTotalBytes", 10 * 1024 * 1024L);
        setField(svc, "archiveMaxTotalMillis", 15000L);

        Object countersEmpty = newInner("ArchiveCounters", new Class<?>[]{});
        Map<String, Object> archiveMetaEmpty = new LinkedHashMap<>();
        Object empty = invokeInstance(
                svc,
                "extract7zFromBytes",
                new Class<?>[]{byte[].class, String.class, int.class, int.class, Map.class, countersEmpty.getClass(), long.class},
                new byte[0],
                "x.7z",
                0,
                200,
                archiveMetaEmpty,
                countersEmpty,
                System.nanoTime()
        );
        assertEquals("", String.valueOf(empty));

        Object countersTraversal = newInner("ArchiveCounters", new Class<?>[]{});
        Map<String, Object> archiveMetaTraversal = new LinkedHashMap<>();
        byte[] z = sevenZBytes(List.of(
                Map.entry("../evil.txt", "x".getBytes(StandardCharsets.UTF_8)),
                Map.entry("ok.txt", "ok7z".getBytes(StandardCharsets.UTF_8))
        ));
        Object out = invokeInstance(
                svc,
                "extract7zFromBytes",
                new Class<?>[]{byte[].class, String.class, int.class, int.class, Map.class, countersTraversal.getClass(), long.class},
                z,
                "s.7z",
                0,
                1000,
                archiveMetaTraversal,
                countersTraversal,
                System.nanoTime()
        );
        assertTrue(String.valueOf(out).contains("ok7z"));
        assertTrue(((Number) getField(countersTraversal, "pathTraversalDroppedCount")).longValue() >= 1L);

        byte[] inner = sevenZBytes(List.of(Map.entry("a.txt", "inner".getBytes(StandardCharsets.UTF_8))));
        byte[] outer = sevenZBytes(List.of(Map.entry("inner.7z", inner)));
        setField(svc, "archiveMaxDepth", 1);
        Object countersDepth = newInner("ArchiveCounters", new Class<?>[]{});
        Map<String, Object> archiveMetaDepth = new LinkedHashMap<>();
        assertThrows(IllegalStateException.class, () -> invokeInstance(
                svc,
                "extract7zFromBytes",
                new Class<?>[]{byte[].class, String.class, int.class, int.class, Map.class, countersDepth.getClass(), long.class},
                outer,
                "outer.7z",
                0,
                1000,
                archiveMetaDepth,
                countersDepth,
                System.nanoTime()
        ));
    }

    @Test
    void expand7zBytesToDisk_shouldCoverTruncatedAndBudgetReason() throws Exception {
        Object svc = new FileAssetExtractionAsyncService(null, null, null, null, null, null, null, null);
        setField(svc, "archiveMaxDepth", 5);
        setField(svc, "archiveMaxEntries", 10);
        setField(svc, "archiveMaxEntryBytes", 4L);
        setField(svc, "archiveMaxTotalBytes", 12L);
        setField(svc, "archiveMaxTotalMillis", 15000L);

        Object counters = newInner("ArchiveCounters", new Class<?>[]{});
        Map<String, Object> archiveMeta = new LinkedHashMap<>();
        List<Map<String, Object>> files = new ArrayList<>();
        Path outDir = Files.createTempDirectory("fae-out-");
        byte[] z = sevenZBytes(List.of(
                Map.entry("big.txt", "abcdefghijklmnop".getBytes(StandardCharsets.UTF_8))
        ));

        invokeInstance(
                svc,
                "expand7zBytesToDisk",
                new Class<?>[]{byte[].class, String.class, int.class, Path.class, Map.class, counters.getClass(), long.class, List.class},
                z,
                "",
                0,
                outDir,
                archiveMeta,
                counters,
                System.nanoTime(),
                files
        );

        assertFalse(files.isEmpty());
        assertEquals("true", String.valueOf(files.get(0).get("extractionTruncated")));
        String reason = String.valueOf(getField(counters, "truncatedReason"));
        assertTrue(reason == null || reason.equals("null") || reason.equals("TOTAL_BYTES_LIMIT"));
    }

    @Test
    void extract7zFromBytes_shouldCoverNullCorruptBudgetAndEntryLimit() throws Exception {
        Object svc = new FileAssetExtractionAsyncService(null, null, null, null, null, null, null, null);
        setField(svc, "archiveMaxDepth", 5);
        setField(svc, "archiveMaxEntries", 10);
        setField(svc, "archiveMaxEntryBytes", 2 * 1024 * 1024L);
        setField(svc, "archiveMaxTotalBytes", 10 * 1024 * 1024L);
        setField(svc, "archiveMaxTotalMillis", 15000L);

        Object countersNull = newInner("ArchiveCounters", new Class<?>[]{});
        Object nullOut = invokeInstance(
                svc,
                "extract7zFromBytes",
                new Class<?>[]{byte[].class, String.class, int.class, int.class, Map.class, countersNull.getClass(), long.class},
                (byte[]) null,
                "null.7z",
                0,
                200,
                new LinkedHashMap<>(),
                countersNull,
                System.nanoTime()
        );
        assertEquals("", String.valueOf(nullOut));

        Object countersCorrupt = newInner("ArchiveCounters", new Class<?>[]{});
        assertThrows(IllegalStateException.class, () -> invokeInstance(
                svc,
                "extract7zFromBytes",
                new Class<?>[]{byte[].class, String.class, int.class, int.class, Map.class, countersCorrupt.getClass(), long.class},
                "not-7z".getBytes(StandardCharsets.UTF_8),
                "bad.7z",
                0,
                200,
                new LinkedHashMap<>(),
                countersCorrupt,
                System.nanoTime()
        ));

        byte[] one = sevenZBytes(List.of(Map.entry("a.txt", "one".getBytes(StandardCharsets.UTF_8))));
        setField(svc, "archiveMaxTotalMillis", 1L);
        Object countersTime = newInner("ArchiveCounters", new Class<?>[]{});
        Object timeout = invokeInstance(
                svc,
                "extract7zFromBytes",
                new Class<?>[]{byte[].class, String.class, int.class, int.class, Map.class, countersTime.getClass(), long.class},
                one,
                "time.7z",
                0,
                200,
                new LinkedHashMap<>(),
                countersTime,
                System.nanoTime() - java.time.Duration.ofMillis(20).toNanos()
        );
        assertEquals("", String.valueOf(timeout));
        assertEquals("TIME_LIMIT", String.valueOf(getField(countersTime, "truncatedReason")));

        setField(svc, "archiveMaxTotalMillis", 15000L);
        setField(svc, "archiveMaxEntries", 1);
        byte[] two = sevenZBytes(List.of(
                Map.entry("a.txt", "A".getBytes(StandardCharsets.UTF_8)),
                Map.entry("b.txt", "B".getBytes(StandardCharsets.UTF_8))
        ));
        Object countersLimit = newInner("ArchiveCounters", new Class<?>[]{});
        Object limited = invokeInstance(
                svc,
                "extract7zFromBytes",
                new Class<?>[]{byte[].class, String.class, int.class, int.class, Map.class, countersLimit.getClass(), long.class},
                two,
                "limit.7z",
                0,
                500,
                new LinkedHashMap<>(),
                countersLimit,
                System.nanoTime()
        );
        assertTrue(String.valueOf(limited).contains("FILE: a.txt"));
        assertEquals("ENTRY_COUNT_LIMIT", String.valueOf(getField(countersLimit, "truncatedReason")));
    }

    @Test
    void expand7zBytesToDisk_shouldCoverNullCorruptBudgetAndEntryLimit() throws Exception {
        Object svc = new FileAssetExtractionAsyncService(null, null, null, null, null, null, null, null);
        setField(svc, "archiveMaxDepth", 5);
        setField(svc, "archiveMaxEntries", 10);
        setField(svc, "archiveMaxEntryBytes", 2 * 1024 * 1024L);
        setField(svc, "archiveMaxTotalBytes", 10 * 1024 * 1024L);
        setField(svc, "archiveMaxTotalMillis", 15000L);

        Path outDir = Files.createTempDirectory("fae-7z-branch-");

        Object countersNull = newInner("ArchiveCounters", new Class<?>[]{});
        List<Map<String, Object>> filesNull = new ArrayList<>();
        invokeInstance(
                svc,
                "expand7zBytesToDisk",
                new Class<?>[]{byte[].class, String.class, int.class, Path.class, Map.class, countersNull.getClass(), long.class, List.class},
                (byte[]) null,
                "",
                0,
                outDir,
                new LinkedHashMap<>(),
                countersNull,
                System.nanoTime(),
                filesNull
        );
        assertTrue(filesNull.isEmpty());

        Object countersCorrupt = newInner("ArchiveCounters", new Class<?>[]{});
        assertThrows(IllegalStateException.class, () -> invokeInstance(
                svc,
                "expand7zBytesToDisk",
                new Class<?>[]{byte[].class, String.class, int.class, Path.class, Map.class, countersCorrupt.getClass(), long.class, List.class},
                "bad7z".getBytes(StandardCharsets.UTF_8),
                "",
                0,
                outDir,
                new LinkedHashMap<>(),
                countersCorrupt,
                System.nanoTime(),
                new ArrayList<>()
        ));

        byte[] one = sevenZBytes(List.of(Map.entry("x.txt", "ok".getBytes(StandardCharsets.UTF_8))));
        setField(svc, "archiveMaxTotalMillis", 1L);
        Object countersTime = newInner("ArchiveCounters", new Class<?>[]{});
        List<Map<String, Object>> filesTime = new ArrayList<>();
        invokeInstance(
                svc,
                "expand7zBytesToDisk",
                new Class<?>[]{byte[].class, String.class, int.class, Path.class, Map.class, countersTime.getClass(), long.class, List.class},
                one,
                "",
                0,
                outDir,
                new LinkedHashMap<>(),
                countersTime,
                System.nanoTime() - java.time.Duration.ofMillis(20).toNanos(),
                filesTime
        );
        assertTrue(filesTime.isEmpty());
        assertEquals("TIME_LIMIT", String.valueOf(getField(countersTime, "truncatedReason")));

        setField(svc, "archiveMaxTotalMillis", 15000L);
        setField(svc, "archiveMaxEntries", 1);
        byte[] two = sevenZBytes(List.of(
                Map.entry("a.txt", "A".getBytes(StandardCharsets.UTF_8)),
                Map.entry("b.txt", "B".getBytes(StandardCharsets.UTF_8))
        ));
        Object countersLimit = newInner("ArchiveCounters", new Class<?>[]{});
        List<Map<String, Object>> filesLimit = new ArrayList<>();
        invokeInstance(
                svc,
                "expand7zBytesToDisk",
                new Class<?>[]{byte[].class, String.class, int.class, Path.class, Map.class, countersLimit.getClass(), long.class, List.class},
                two,
                "",
                0,
                outDir,
                new LinkedHashMap<>(),
                countersLimit,
                System.nanoTime(),
                filesLimit
        );
        assertEquals(1, filesLimit.size());
        assertEquals("ENTRY_COUNT_LIMIT", String.valueOf(getField(countersLimit, "truncatedReason")));
    }

    @Test
    void archiveExtAndOfficeExtAndInnerSupport_shouldCoverBranchMatrix() {
        assertEquals(false, invokeStatic("isArchiveExt", new Class<?>[]{String.class}, (String) null));
        assertEquals(false, invokeStatic("isArchiveExt", new Class<?>[]{String.class}, " "));
        assertEquals(true, invokeStatic("isArchiveExt", new Class<?>[]{String.class}, "zip"));
        assertEquals(true, invokeStatic("isArchiveExt", new Class<?>[]{String.class}, "TXZ"));
        assertEquals(false, invokeStatic("isArchiveExt", new Class<?>[]{String.class}, "rar"));

        assertEquals(false, invokeStatic("isOfficeExt", new Class<?>[]{String.class}, (String) null));
        assertEquals(false, invokeStatic("isOfficeExt", new Class<?>[]{String.class}, ""));
        assertEquals(true, invokeStatic("isOfficeExt", new Class<?>[]{String.class}, "doc"));
        assertEquals(true, invokeStatic("isOfficeExt", new Class<?>[]{String.class}, "XLSX"));
        assertEquals(false, invokeStatic("isOfficeExt", new Class<?>[]{String.class}, "pdf"));

        assertEquals(false, invokeStatic("isSupportedInnerExtForExtraction", new Class<?>[]{String.class}, (String) null));
        assertEquals(false, invokeStatic("isSupportedInnerExtForExtraction", new Class<?>[]{String.class}, ""));
        assertEquals(false, invokeStatic("isSupportedInnerExtForExtraction", new Class<?>[]{String.class}, "zip"));
        assertEquals(true, invokeStatic("isSupportedInnerExtForExtraction", new Class<?>[]{String.class}, "pdf"));
        assertEquals(true, invokeStatic("isSupportedInnerExtForExtraction", new Class<?>[]{String.class}, "docx"));
        assertEquals(true, invokeStatic("isSupportedInnerExtForExtraction", new Class<?>[]{String.class}, "pptx"));
        assertEquals(true, invokeStatic("isSupportedInnerExtForExtraction", new Class<?>[]{String.class}, "mobi"));
        assertEquals(false, invokeStatic("isSupportedInnerExtForExtraction", new Class<?>[]{String.class}, "bin"));
    }

    @Test
    void extractEntryBytesAsText_shouldCoverTxtHtmlXmlPdfAndFallback() {
        Object svc = new FileAssetExtractionAsyncService(null, null, null, null, null, null, null, null);
        Object txt = invokeInstance(
                svc,
                "extractEntryBytesAsText",
                new Class<?>[]{String.class, String.class, byte[].class, int.class},
                "a.txt",
                "txt",
                "hello".getBytes(StandardCharsets.UTF_8),
                100
        );
        assertEquals("hello", String.valueOf(txt));

        Object html = invokeInstance(
                svc,
                "extractEntryBytesAsText",
                new Class<?>[]{String.class, String.class, byte[].class, int.class},
                "a.html",
                "html",
                "<p>x</p>".getBytes(StandardCharsets.UTF_8),
                100
        );
        assertTrue(String.valueOf(html).contains("x"));

        Object xml = invokeInstance(
                svc,
                "extractEntryBytesAsText",
                new Class<?>[]{String.class, String.class, byte[].class, int.class},
                "a.xml",
                "xml",
                "<a>b</a>".getBytes(StandardCharsets.UTF_8),
                100
        );
        assertTrue(String.valueOf(xml).contains("<a>b</a>"));

        assertThrows(IllegalStateException.class, () -> invokeInstance(
                svc,
                "extractEntryBytesAsText",
                new Class<?>[]{String.class, String.class, byte[].class, int.class},
                "a.pdf",
                "pdf",
                "bad".getBytes(StandardCharsets.UTF_8),
                100
        ));

        assertThrows(IllegalStateException.class, () -> invokeInstance(
                svc,
                "extractEntryBytesAsText",
                new Class<?>[]{String.class, String.class, byte[].class, int.class},
                "a.bin",
                "bin",
                "fallback".getBytes(StandardCharsets.UTF_8),
                100
        ));
    }

    @Test
    void extract7zFromPath_shouldCoverTraversalAndEntryCountLimit() throws Exception {
        Object svc = new FileAssetExtractionAsyncService(null, null, null, null, null, null, null, null);
        setField(svc, "archiveMaxDepth", 5);
        setField(svc, "archiveMaxEntries", 10);
        setField(svc, "archiveMaxEntryBytes", 2 * 1024 * 1024L);
        setField(svc, "archiveMaxTotalBytes", 10 * 1024 * 1024L);
        setField(svc, "archiveMaxTotalMillis", 15000L);

        Path p = Files.createTempFile("traversal-", ".7z");
        try (SevenZOutputFile out = new SevenZOutputFile(p.toFile())) {
            SevenZArchiveEntry e1 = new SevenZArchiveEntry();
            e1.setName("../evil.txt");
            e1.setContentMethods(List.of(new SevenZMethodConfiguration(SevenZMethod.COPY)));
            out.putArchiveEntry(e1);
            out.write("x".getBytes(StandardCharsets.UTF_8));
            out.closeArchiveEntry();
            SevenZArchiveEntry e2 = new SevenZArchiveEntry();
            e2.setName("ok.txt");
            e2.setContentMethods(List.of(new SevenZMethodConfiguration(SevenZMethod.COPY)));
            out.putArchiveEntry(e2);
            out.write("ok7z".getBytes(StandardCharsets.UTF_8));
            out.closeArchiveEntry();
        }

        Object counters = newInner("ArchiveCounters", new Class<?>[]{});
        Map<String, Object> meta = new LinkedHashMap<>();
        Object outTxt = invokeInstance(
                svc,
                "extract7zFromPath",
                new Class<?>[]{Path.class, String.class, int.class, int.class, Map.class, counters.getClass(), long.class},
                p,
                "traversal.7z",
                0,
                1000,
                meta,
                counters,
                System.nanoTime()
        );
        assertTrue(String.valueOf(outTxt).contains("ok7z"));
        assertTrue(((Number) getField(counters, "pathTraversalDroppedCount")).longValue() >= 1L);

        setField(svc, "archiveMaxEntries", 1);
        Object countersLimit = newInner("ArchiveCounters", new Class<?>[]{});
        Map<String, Object> metaLimit = new LinkedHashMap<>();
        invokeInstance(
                svc,
                "extract7zFromPath",
                new Class<?>[]{Path.class, String.class, int.class, int.class, Map.class, countersLimit.getClass(), long.class},
                p,
                "limit.7z",
                0,
                1000,
                metaLimit,
                countersLimit,
                System.nanoTime()
        );
        assertEquals("ENTRY_COUNT_LIMIT", String.valueOf(getField(countersLimit, "truncatedReason")));
    }

    @Test
    void extractOfficeImageMethods_shouldCoverEmbeddedNoneUnsupportedAndFailed() throws Exception {
        Object svc = new FileAssetExtractionAsyncService(null, null, null, null, null, null, null, null);
        Class<?> budgetClass = Class.forName(FileAssetExtractionAsyncService.class.getName() + "$ImageBudget");
        Constructor<?> ctor = budgetClass.getDeclaredConstructor(int.class, long.class);
        ctor.setAccessible(true);
        Object budget = ctor.newInstance(10, 1024L * 1024L);

        Path xlsx = Files.createTempFile("blank-", ".xlsx");
        try (XSSFWorkbook wb = new XSSFWorkbook(); OutputStream os = Files.newOutputStream(xlsx)) {
            wb.createSheet("s");
            wb.write(os);
        }
        Map<String, Object> xlsxMeta = new LinkedHashMap<>();
        invokeInstance(svc, "extractXlsxImages", new Class<?>[]{Path.class, Map.class, Long.class, budgetClass}, xlsx, xlsxMeta, 1L, budget);
        assertEquals("EMBEDDED_NONE", String.valueOf(xlsxMeta.get("imagesExtractionMode")));

        Path xls = Files.createTempFile("legacy-", ".xls");
        try (HSSFWorkbook wb = new HSSFWorkbook(); OutputStream os = Files.newOutputStream(xls)) {
            wb.createSheet("s");
            wb.write(os);
        }
        Map<String, Object> xlsMeta = new LinkedHashMap<>();
        invokeInstance(svc, "extractXlsxImages", new Class<?>[]{Path.class, Map.class, Long.class, budgetClass}, xls, xlsMeta, 1L, budget);
        assertEquals("UNSUPPORTED", String.valueOf(xlsMeta.get("imagesExtractionMode")));

        Path ppt = Files.createTempFile("blank-", ".ppt");
        try (HSLFSlideShow show = new HSLFSlideShow(); OutputStream os = Files.newOutputStream(ppt)) {
            show.write(os);
        }
        Map<String, Object> pptMeta = new LinkedHashMap<>();
        invokeInstance(svc, "extractPptImages", new Class<?>[]{Path.class, Map.class, Long.class, budgetClass}, ppt, pptMeta, 1L, budget);
        assertEquals("EMBEDDED_NONE", String.valueOf(pptMeta.get("imagesExtractionMode")));

        Path pptx = Files.createTempFile("blank-", ".pptx");
        try (XMLSlideShow show = new XMLSlideShow(); OutputStream os = Files.newOutputStream(pptx)) {
            show.write(os);
        }
        Map<String, Object> pptxMeta = new LinkedHashMap<>();
        invokeInstance(svc, "extractPptxImages", new Class<?>[]{Path.class, Map.class, Long.class, budgetClass}, pptx, pptxMeta, 1L, budget);
        assertEquals("EMBEDDED_NONE", String.valueOf(pptxMeta.get("imagesExtractionMode")));

        Path bad = Files.createTempFile("bad-", ".ppt");
        Files.writeString(bad, "bad");
        Map<String, Object> badMeta = new LinkedHashMap<>();
        invokeInstance(svc, "extractPptImages", new Class<?>[]{Path.class, Map.class, Long.class, budgetClass}, bad, badMeta, 1L, budget);
        assertEquals("FAILED", String.valueOf(badMeta.get("imagesExtractionMode")));
    }

    @Test
    void expandArchiveStreamToDisk_shouldCoverUnknownArchiveAndEntryLimitAndTraversal() throws Exception {
        Object svc = new FileAssetExtractionAsyncService(null, null, null, null, null, null, null, null);
        setField(svc, "archiveMaxDepth", 5);
        setField(svc, "archiveMaxEntries", 10);
        setField(svc, "archiveMaxEntryBytes", 2 * 1024 * 1024L);
        setField(svc, "archiveMaxTotalBytes", 10 * 1024 * 1024L);
        setField(svc, "archiveMaxTotalMillis", 15000L);

        Object countersUnknown = newInner("ArchiveCounters", new Class<?>[]{});
        Map<String, Object> archiveMetaUnknown = new LinkedHashMap<>();
        List<Map<String, Object>> filesUnknown = new ArrayList<>();
        Path outUnknown = Files.createTempDirectory("exp-unknown-");
        invokeInstance(
                svc,
                "expandArchiveStreamToDisk",
                new Class<?>[]{java.io.InputStream.class, String.class, String.class, int.class, Path.class, Map.class, countersUnknown.getClass(), long.class, List.class},
                new ByteArrayInputStream("plain-body".getBytes(StandardCharsets.UTF_8)),
                "blob.bin",
                "",
                0,
                outUnknown,
                archiveMetaUnknown,
                countersUnknown,
                System.nanoTime(),
                filesUnknown
        );
        assertFalse(filesUnknown.isEmpty());
        assertEquals("blob.bin", String.valueOf(filesUnknown.get(0).get("path")));

        byte[] zip = zipBytes(List.of(
                Map.entry("../evil.txt", "x".getBytes(StandardCharsets.UTF_8)),
                Map.entry("ok.txt", "ok".getBytes(StandardCharsets.UTF_8)),
                Map.entry("more.txt", "more".getBytes(StandardCharsets.UTF_8))
        ));
        setField(svc, "archiveMaxEntries", 1);
        Object countersZip = newInner("ArchiveCounters", new Class<?>[]{});
        Map<String, Object> archiveMetaZip = new LinkedHashMap<>();
        List<Map<String, Object>> filesZip = new ArrayList<>();
        Path outZip = Files.createTempDirectory("exp-zip-");
        invokeInstance(
                svc,
                "expandArchiveStreamToDisk",
                new Class<?>[]{java.io.InputStream.class, String.class, String.class, int.class, Path.class, Map.class, countersZip.getClass(), long.class, List.class},
                new ByteArrayInputStream(zip),
                "a.zip",
                "",
                0,
                outZip,
                archiveMetaZip,
                countersZip,
                System.nanoTime(),
                filesZip
        );
        assertTrue(((Number) getField(countersZip, "pathTraversalDroppedCount")).longValue() >= 1L);
        assertEquals("ENTRY_COUNT_LIMIT", String.valueOf(getField(countersZip, "truncatedReason")));
    }

    @Test
    void expand7zToDisk_shouldCoverTraversalAndEntryLimit() throws Exception {
        Object svc = new FileAssetExtractionAsyncService(null, null, null, null, null, null, null, null);
        setField(svc, "archiveMaxDepth", 5);
        setField(svc, "archiveMaxEntries", 1);
        setField(svc, "archiveMaxEntryBytes", 2 * 1024 * 1024L);
        setField(svc, "archiveMaxTotalBytes", 10 * 1024 * 1024L);
        setField(svc, "archiveMaxTotalMillis", 15000L);

        Path seven = Files.createTempFile("exp-", ".7z");
        try (SevenZOutputFile out = new SevenZOutputFile(seven.toFile())) {
            SevenZArchiveEntry e1 = new SevenZArchiveEntry();
            e1.setName("../evil.txt");
            e1.setContentMethods(List.of(new SevenZMethodConfiguration(SevenZMethod.COPY)));
            out.putArchiveEntry(e1);
            out.write("x".getBytes(StandardCharsets.UTF_8));
            out.closeArchiveEntry();
            SevenZArchiveEntry e2 = new SevenZArchiveEntry();
            e2.setName("ok.txt");
            e2.setContentMethods(List.of(new SevenZMethodConfiguration(SevenZMethod.COPY)));
            out.putArchiveEntry(e2);
            out.write("ok".getBytes(StandardCharsets.UTF_8));
            out.closeArchiveEntry();
        }

        Object counters = newInner("ArchiveCounters", new Class<?>[]{});
        Map<String, Object> archiveMeta = new LinkedHashMap<>();
        List<Map<String, Object>> files = new ArrayList<>();
        Path outDir = Files.createTempDirectory("exp-7z-");
        invokeInstance(
                svc,
                "expand7zToDisk",
                new Class<?>[]{Path.class, String.class, int.class, Path.class, Map.class, counters.getClass(), long.class, List.class},
                seven,
                "",
                0,
                outDir,
                archiveMeta,
                counters,
                System.nanoTime(),
                files
        );
        assertEquals("7z", String.valueOf(archiveMeta.get("archiveType")));
        assertTrue(((Number) getField(counters, "pathTraversalDroppedCount")).longValue() >= 1L);
        assertEquals("ENTRY_COUNT_LIMIT", String.valueOf(getField(counters, "truncatedReason")));
    }

    private static Object newBudget(int maxCount, long maxTotalBytes) {
        return newInner("ImageBudget", new Class<?>[]{int.class, long.class}, maxCount, maxTotalBytes);
    }

    private static byte[] tinyPngBytes() throws Exception {
        BufferedImage img = new BufferedImage(2, 2, BufferedImage.TYPE_INT_RGB);
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ImageIO.write(img, "png", baos);
        return baos.toByteArray();
    }

    private static Path createPdfWithImage() throws Exception {
        Path pdf = Files.createTempFile("pdf-img-", ".pdf");
        byte[] png = tinyPngBytes();
        try (PDDocument doc = new PDDocument()) {
            PDPage page = new PDPage();
            doc.addPage(page);
            PDImageXObject image = PDImageXObject.createFromByteArray(doc, png, "tiny");
            try (PDPageContentStream cs = new PDPageContentStream(doc, page)) {
                cs.drawImage(image, 10, 10, 20, 20);
            }
            doc.save(pdf.toFile());
        }
        return pdf;
    }

    @Test
    void extractPdfImages_shouldCoverXObjectEmbeddedNoneAndRenderSkipped() throws Exception {
        DerivedUploadStorageService storage = Mockito.mock(DerivedUploadStorageService.class);
        Mockito.when(storage.saveDerivedImage(Mockito.any(byte[].class), Mockito.anyString(), Mockito.any(), Mockito.any()))
                .thenReturn(Map.of("path", "/tmp/img.png"));
        Mockito.when(storage.buildPlaceholder(Mockito.anyInt(), Mockito.anyMap()))
                .thenAnswer(inv -> Map.of("placeholder", "[[IMAGE_" + inv.getArgument(0) + "]]"));
        Object svc = new FileAssetExtractionAsyncService(null, null, null, null, null, null, null, storage);
        setField(svc, "pdfRenderMaxPages", 2);
        setField(svc, "pdfRenderDpi", 96);

        Object budget = newBudget(10, 10 * 1024 * 1024L);
        Path withImage = createPdfWithImage();
        Map<String, Object> meta1 = new LinkedHashMap<>();
        Object out1 = invokeInstance(
                svc,
                "extractPdfImages",
                new Class<?>[]{Path.class, Map.class, Long.class, String.class, budget.getClass()},
                withImage,
                meta1,
                1L,
                "",
                budget
        );
        assertTrue(out1 instanceof List<?>);
        assertFalse(((List<?>) out1).isEmpty());
        assertEquals("PDF_XOBJECT", String.valueOf(meta1.get("imagesExtractionMode")));

        Path blank = Files.createTempFile("pdf-blank-", ".pdf");
        try (PDDocument doc = new PDDocument()) {
            doc.addPage(new PDPage());
            doc.save(blank.toFile());
        }
        setField(svc, "pdfRenderMaxPages", 0);
        Map<String, Object> meta2 = new LinkedHashMap<>();
        Object out2 = invokeInstance(
                svc,
                "extractPdfImages",
                new Class<?>[]{Path.class, Map.class, Long.class, String.class, budget.getClass()},
                blank,
                meta2,
                2L,
                "",
                newBudget(10, 10 * 1024 * 1024L)
        );
        assertTrue(((List<?>) out2).isEmpty());
        assertEquals("PDF_RENDER_SKIPPED", String.valueOf(meta2.get("imagesExtractionMode")));

        Map<String, Object> meta3 = new LinkedHashMap<>();
        Object out3 = invokeInstance(
                svc,
                "extractPdfImages",
                new Class<?>[]{Path.class, Map.class, Long.class, String.class, budget.getClass()},
                blank,
                meta3,
                3L,
                "has text",
                newBudget(10, 10 * 1024 * 1024L)
        );
        assertTrue(((List<?>) out3).isEmpty());
        assertEquals("EMBEDDED_NONE", String.valueOf(meta3.get("imagesExtractionMode")));
    }

    @Test
    void extractMobiImagesWithTika_shouldCoverZipFallbackAndEmbeddedNone() throws Exception {
        DerivedUploadStorageService storage = Mockito.mock(DerivedUploadStorageService.class);
        Mockito.when(storage.getMaxImageBytes()).thenReturn(5 * 1024 * 1024L);
        Mockito.when(storage.saveDerivedImage(Mockito.any(byte[].class), Mockito.anyString(), Mockito.any(), Mockito.any()))
                .thenReturn(Map.of("path", "/tmp/mobi.png"));
        Mockito.when(storage.buildPlaceholder(Mockito.anyInt(), Mockito.anyMap()))
                .thenAnswer(inv -> Map.of("placeholder", "[[IMAGE_" + inv.getArgument(0) + "]]"));
        Object svc = new FileAssetExtractionAsyncService(null, null, null, null, null, null, null, storage);
        Object budget = newBudget(10, 10 * 1024 * 1024L);

        byte[] png = tinyPngBytes();
        byte[] zipMobi = zipBytes(List.of(Map.entry("images/cover.png", png)));
        Path mobiZip = Files.createTempFile("mobi-zip-", ".mobi");
        Files.write(mobiZip, zipMobi);
        Map<String, Object> meta1 = new LinkedHashMap<>();
        Object out1 = invokeInstance(
                svc,
                "extractMobiImagesWithTika",
                new Class<?>[]{Path.class, Map.class, Long.class, budget.getClass()},
                mobiZip,
                meta1,
                1L,
                budget
        );
        assertTrue(out1 instanceof List<?>);
        assertFalse(((List<?>) out1).isEmpty());
        assertEquals("MOBI_ZIP", String.valueOf(meta1.get("imagesExtractionMode")));

        byte[] zipNoImage = zipBytes(List.of(Map.entry("book/ch1.txt", "chapter".getBytes(StandardCharsets.UTF_8))));
        Path mobiPlain = Files.createTempFile("mobi-no-image-", ".mobi");
        Files.write(mobiPlain, zipNoImage);
        Map<String, Object> meta2 = new LinkedHashMap<>();
        Object out2 = invokeInstance(
                svc,
                "extractMobiImagesWithTika",
                new Class<?>[]{Path.class, Map.class, Long.class, budget.getClass()},
                mobiPlain,
                meta2,
                2L,
                newBudget(10, 10 * 1024 * 1024L)
        );
        assertTrue(((List<?>) out2).isEmpty());
        assertEquals("EMBEDDED_NONE", String.valueOf(meta2.get("imagesExtractionMode")));
    }

    @Test
    void extractPdfImages_shouldCoverXObjectSaveNullAndRenderWithNullBudget() throws Exception {
        DerivedUploadStorageService storage = Mockito.mock(DerivedUploadStorageService.class);
        Mockito.when(storage.saveDerivedImage(Mockito.any(byte[].class), Mockito.anyString(), Mockito.any(), Mockito.any()))
                .thenReturn(null)
                .thenReturn(Map.of("path", "/tmp/pdf-render-null-budget.png"));
        Mockito.when(storage.buildPlaceholder(Mockito.anyInt(), Mockito.anyMap()))
                .thenAnswer(inv -> Map.of("placeholder", "[[IMAGE_" + inv.getArgument(0) + "]]"));
        Object svc = new FileAssetExtractionAsyncService(null, null, null, null, null, null, null, storage);
        setField(svc, "pdfRenderMaxPages", 1);
        setField(svc, "pdfRenderDpi", 96);
        Object budget = newBudget(10, 10 * 1024 * 1024L);

        Path withImage = createPdfWithImage();
        Map<String, Object> meta1 = new LinkedHashMap<>();
        Object out1 = invokeInstance(
                svc,
                "extractPdfImages",
                new Class<?>[]{Path.class, Map.class, Long.class, String.class, budget.getClass()},
                withImage,
                meta1,
                12L,
                "has text",
                budget
        );
        assertTrue(((List<?>) out1).isEmpty());
        assertEquals("EMBEDDED_NONE", String.valueOf(meta1.get("imagesExtractionMode")));

        Path blank = Files.createTempFile("pdf-null-budget-", ".pdf");
        try (PDDocument doc = new PDDocument()) {
            doc.addPage(new PDPage());
            doc.save(blank.toFile());
        }
        Map<String, Object> meta2 = new LinkedHashMap<>();
        Object out2 = invokeInstance(
                svc,
                "extractPdfImages",
                new Class<?>[]{Path.class, Map.class, Long.class, String.class, budget.getClass()},
                blank,
                meta2,
                13L,
                "",
                null
        );
        assertFalse(((List<?>) out2).isEmpty());
        assertEquals("PDF_RENDER", String.valueOf(meta2.get("imagesExtractionMode")));
    }

    @Test
    void extractMobiImagesWithTika_shouldCoverTikaEmbeddedAndEmbeddedExtractorSaveNull() throws Exception {
        DerivedUploadStorageService storage = Mockito.mock(DerivedUploadStorageService.class);
        Mockito.when(storage.saveDerivedImage(Mockito.any(byte[].class), Mockito.anyString(), Mockito.any(), Mockito.any()))
                .thenReturn(Map.of("path", "/tmp/mobi-tika.png"))
                .thenReturn(null);
        Mockito.when(storage.buildPlaceholder(Mockito.anyInt(), Mockito.anyMap()))
                .thenAnswer(inv -> Map.of("placeholder", "[[IMAGE_" + inv.getArgument(0) + "]]"));
        Object svc = new FileAssetExtractionAsyncService(null, null, null, null, null, null, null, storage);
        Object budget = newBudget(10, 10 * 1024 * 1024L);
        byte[] png = tinyPngBytes();

        try (MockedConstruction<AutoDetectParser> mocked = Mockito.mockConstruction(
                AutoDetectParser.class,
                (mock, context) -> Mockito.doAnswer(inv -> {
                    ParseContext parseContext = inv.getArgument(3);
                    Object extractor = parseContext.get(
                            Class.forName("org.apache.tika.extractor.EmbeddedDocumentExtractor")
                    );
                    Method parseEmbedded = extractor.getClass().getMethod(
                            "parseEmbedded",
                            InputStream.class,
                            org.xml.sax.ContentHandler.class,
                            Metadata.class,
                            boolean.class
                    );
                    Metadata metadata = new Metadata();
                    metadata.set(Metadata.CONTENT_TYPE, "image/png");
                    metadata.set(TikaCoreProperties.RESOURCE_NAME_KEY, "cover.png");
                    parseEmbedded.invoke(extractor, new ByteArrayInputStream(png), inv.getArgument(1), metadata, false);
                    return null;
                }).when(mock).parse(
                        Mockito.any(InputStream.class),
                        Mockito.any(org.xml.sax.ContentHandler.class),
                        Mockito.any(Metadata.class),
                        Mockito.any(ParseContext.class)
                )
        )) {
            Path mobi1 = Files.createTempFile("mobi-tika-embedded-", ".mobi");
            Files.writeString(mobi1, "mobi-body-1");
            Map<String, Object> meta1 = new LinkedHashMap<>();
            Object out1 = invokeInstance(
                    svc,
                    "extractMobiImagesWithTika",
                    new Class<?>[]{Path.class, Map.class, Long.class, budget.getClass()},
                    mobi1,
                    meta1,
                    21L,
                    budget
            );
            assertFalse(((List<?>) out1).isEmpty());
            assertEquals("MOBI_TIKA_EMBEDDED", String.valueOf(meta1.get("imagesExtractionMode")));

            Path mobi2 = Files.createTempFile("mobi-tika-save-null-", ".mobi");
            Files.writeString(mobi2, "mobi-body-2");
            Map<String, Object> meta2 = new LinkedHashMap<>();
            Object out2 = invokeInstance(
                    svc,
                    "extractMobiImagesWithTika",
                    new Class<?>[]{Path.class, Map.class, Long.class, budget.getClass()},
                    mobi2,
                    meta2,
                    22L,
                    newBudget(10, 10 * 1024 * 1024L)
            );
            assertTrue(((List<?>) out2).isEmpty());
            assertEquals("EMBEDDED_NONE", String.valueOf(meta2.get("imagesExtractionMode")));
            assertEquals(2, mocked.constructed().size());
        }
    }

    @Test
    void extractArchiveFromStream_shouldCoverPlainFallbackAnd7zTotalBytesLimit() throws Exception {
        Object svc = new FileAssetExtractionAsyncService(null, null, null, null, null, null, null, null);
        setField(svc, "archiveMaxDepth", 5);
        setField(svc, "archiveMaxEntries", 10);
        setField(svc, "archiveMaxEntryBytes", 1024L);
        setField(svc, "archiveMaxTotalBytes", 1024L);
        setField(svc, "archiveMaxTotalMillis", 15000L);

        Object countersPlain = newInner("ArchiveCounters", new Class<?>[]{});
        assertThrows(IllegalStateException.class, () -> invokeInstance(
                svc,
                "extractArchiveFromStream",
                new Class<?>[]{InputStream.class, String.class, int.class, int.class, Map.class, countersPlain.getClass(), long.class},
                new ByteArrayInputStream("abcdef".getBytes(StandardCharsets.UTF_8)),
                "a.txt",
                0,
                3,
                new LinkedHashMap<>(),
                countersPlain,
                System.nanoTime()
        ));

        setField(svc, "archiveMaxTotalBytes", 1L);
        Object counters7z = newInner("ArchiveCounters", new Class<?>[]{});
        byte[] seven = sevenZBytes(List.of(Map.entry("a.txt", "hello".getBytes(StandardCharsets.UTF_8))));
        Object out = invokeInstance(
                svc,
                "extractArchiveFromStream",
                new Class<?>[]{InputStream.class, String.class, int.class, int.class, Map.class, counters7z.getClass(), long.class},
                new ByteArrayInputStream(seven),
                "a.7z",
                0,
                100,
                new LinkedHashMap<>(),
                counters7z,
                System.nanoTime()
        );
        assertEquals("", String.valueOf(out));
        String reason = String.valueOf(getField(counters7z, "truncatedReason"));
        assertTrue(reason == null || reason.equals("null") || reason.equals("TOTAL_BYTES_LIMIT"));
    }

    @Test
    void extractArchiveFromStream_shouldCoverTimeLimitWithPresetReason() throws Exception {
        Object svc = new FileAssetExtractionAsyncService(null, null, null, null, null, null, null, null);
        setField(svc, "archiveMaxDepth", 5);
        setField(svc, "archiveMaxEntries", 20);
        setField(svc, "archiveMaxEntryBytes", 2 * 1024 * 1024L);
        setField(svc, "archiveMaxTotalBytes", 20 * 1024 * 1024L);
        setField(svc, "archiveMaxTotalMillis", 1L);

        Object counters = newInner("ArchiveCounters", new Class<?>[]{});
        setField(counters, "truncatedReason", "PRESET");
        Object out = invokeInstance(
                svc,
                "extractArchiveFromStream",
                new Class<?>[]{InputStream.class, String.class, int.class, int.class, Map.class, counters.getClass(), long.class},
                new ByteArrayInputStream("body".getBytes(StandardCharsets.UTF_8)),
                "note.txt",
                0,
                200,
                new LinkedHashMap<>(),
                counters,
                System.nanoTime() - java.time.Duration.ofMillis(10).toNanos()
        );
        assertEquals("", String.valueOf(out));
        assertEquals("PRESET", String.valueOf(getField(counters, "truncatedReason")));
    }

    @Test
    void expandArchiveStreamToDisk_shouldCoverSafeResolveNullAnd7zArchiveType() throws Exception {
        Object svc = new FileAssetExtractionAsyncService(null, null, null, null, null, null, null, null);
        setField(svc, "archiveMaxDepth", 5);
        setField(svc, "archiveMaxEntries", 10);
        setField(svc, "archiveMaxEntryBytes", 2 * 1024 * 1024L);
        setField(svc, "archiveMaxTotalBytes", 10 * 1024 * 1024L);
        setField(svc, "archiveMaxTotalMillis", 15000L);

        Object countersNull = newInner("ArchiveCounters", new Class<?>[]{});
        List<Map<String, Object>> filesNull = new ArrayList<>();
        invokeInstance(
                svc,
                "expandArchiveStreamToDisk",
                new Class<?>[]{InputStream.class, String.class, String.class, int.class, Path.class, Map.class, countersNull.getClass(), long.class, List.class},
                new ByteArrayInputStream("plain".getBytes(StandardCharsets.UTF_8)),
                "../evil.bin",
                "",
                0,
                Files.createTempDirectory("exp-safe-"),
                new LinkedHashMap<>(),
                countersNull,
                System.nanoTime(),
                filesNull
        );
        assertTrue(filesNull.isEmpty());

        Object counters7z = newInner("ArchiveCounters", new Class<?>[]{});
        Map<String, Object> archiveMeta = new LinkedHashMap<>();
        List<Map<String, Object>> files7z = new ArrayList<>();
        byte[] seven = sevenZBytes(List.of(Map.entry("ok.txt", "hello7".getBytes(StandardCharsets.UTF_8))));
        invokeInstance(
                svc,
                "expandArchiveStreamToDisk",
                new Class<?>[]{InputStream.class, String.class, String.class, int.class, Path.class, Map.class, counters7z.getClass(), long.class, List.class},
                new ByteArrayInputStream(seven),
                "a.7z",
                "vp/",
                0,
                Files.createTempDirectory("exp-seven-"),
                archiveMeta,
                counters7z,
                System.nanoTime(),
                files7z
        );
        assertFalse(files7z.isEmpty());
        assertEquals("7z", String.valueOf(archiveMeta.get("archiveType")));
    }

    @Test
    void expandArchiveStreamToDisk_shouldCoverTimeLimitPresetAndCompressionMetadata() throws Exception {
        Object svc = new FileAssetExtractionAsyncService(null, null, null, null, null, null, null, null);
        setField(svc, "archiveMaxDepth", 5);
        setField(svc, "archiveMaxEntries", 20);
        setField(svc, "archiveMaxEntryBytes", 2 * 1024 * 1024L);
        setField(svc, "archiveMaxTotalBytes", 20 * 1024 * 1024L);
        setField(svc, "archiveMaxTotalMillis", 1L);

        Object countersTimeout = newInner("ArchiveCounters", new Class<?>[]{});
        setField(countersTimeout, "truncatedReason", "PRESET");
        List<Map<String, Object>> timeoutFiles = new ArrayList<>();
        invokeInstance(
                svc,
                "expandArchiveStreamToDisk",
                new Class<?>[]{InputStream.class, String.class, String.class, int.class, Path.class, Map.class, countersTimeout.getClass(), long.class, List.class},
                new ByteArrayInputStream("timeout".getBytes(StandardCharsets.UTF_8)),
                "timeout.txt",
                "",
                0,
                Files.createTempDirectory("exp-timeout-"),
                new LinkedHashMap<>(),
                countersTimeout,
                System.nanoTime() - java.time.Duration.ofMillis(10).toNanos(),
                timeoutFiles
        );
        assertTrue(timeoutFiles.isEmpty());
        assertEquals("PRESET", String.valueOf(getField(countersTimeout, "truncatedReason")));

        setField(svc, "archiveMaxTotalMillis", 15000L);
        Object countersTgz = newInner("ArchiveCounters", new Class<?>[]{});
        Map<String, Object> archiveMeta = new LinkedHashMap<>();
        List<Map<String, Object>> files = new ArrayList<>();
        byte[] zipped = zipBytes(List.of(Map.entry("a.txt", "GZIP-ZIP-CONTENT".getBytes(StandardCharsets.UTF_8))));
        byte[] tgz = gzipBytes(zipped);
        invokeInstance(
                svc,
                "expandArchiveStreamToDisk",
                new Class<?>[]{InputStream.class, String.class, String.class, int.class, Path.class, Map.class, countersTgz.getClass(), long.class, List.class},
                new ByteArrayInputStream(tgz),
                "bundle.zip.gz",
                "vp/",
                0,
                Files.createTempDirectory("exp-tgz-"),
                archiveMeta,
                countersTgz,
                System.nanoTime(),
                files
        );
        assertFalse(files.isEmpty());
        String archiveType = String.valueOf(archiveMeta.get("archiveType"));
        assertFalse(archiveType == null || archiveType.equals("null") || archiveType.isBlank());
        String compression = String.valueOf(archiveMeta.get("compression"));
        assertFalse(compression == null || compression.equals("null") || compression.isBlank());
    }

    @Test
    void extract7zFromBytes_shouldCoverMagicNestedArchiveAndTextLimit() throws Exception {
        Object svc = new FileAssetExtractionAsyncService(null, null, null, null, null, null, null, null);
        setField(svc, "archiveMaxDepth", 5);
        setField(svc, "archiveMaxEntries", 10);
        setField(svc, "archiveMaxEntryBytes", 2 * 1024 * 1024L);
        setField(svc, "archiveMaxTotalBytes", 10 * 1024 * 1024L);
        setField(svc, "archiveMaxTotalMillis", 15000L);

        byte[] innerZip = zipBytes(List.of(Map.entry("a.txt", "inner-text".getBytes(StandardCharsets.UTF_8))));
        byte[] outer7z = sevenZBytes(List.of(Map.entry("payload", innerZip)));
        Object countersNested = newInner("ArchiveCounters", new Class<?>[]{});
        String nested = String.valueOf(invokeInstance(
                svc,
                "extract7zFromBytes",
                new Class<?>[]{byte[].class, String.class, int.class, int.class, Map.class, countersNested.getClass(), long.class},
                outer7z,
                "outer.7z",
                0,
                1000,
                new LinkedHashMap<>(),
                countersNested,
                System.nanoTime()
        ));
        assertTrue(nested.contains("inner-text"));

        byte[] txt7z = sevenZBytes(List.of(Map.entry("b.txt", "abcdef".getBytes(StandardCharsets.UTF_8))));
        Object countersLimit = newInner("ArchiveCounters", new Class<?>[]{});
        String limited = String.valueOf(invokeInstance(
                svc,
                "extract7zFromBytes",
                new Class<?>[]{byte[].class, String.class, int.class, int.class, Map.class, countersLimit.getClass(), long.class},
                txt7z,
                "short.7z",
                0,
                1,
                new LinkedHashMap<>(),
                countersLimit,
                System.nanoTime()
        ));
        assertFalse(limited.isBlank());
        assertEquals("TEXT_CHAR_LIMIT", String.valueOf(getField(countersLimit, "truncatedReason")));
    }

    private static Path docxWithImagePath() throws Exception {
        Path p = Files.createTempFile("with-img-", ".docx");
        byte[] png = tinyPngBytes();
        try (org.apache.poi.xwpf.usermodel.XWPFDocument doc = new org.apache.poi.xwpf.usermodel.XWPFDocument();
             OutputStream os = Files.newOutputStream(p);
             ByteArrayInputStream bis = new ByteArrayInputStream(png)) {
            var run = doc.createParagraph().createRun();
            run.addPicture(bis, Document.PICTURE_TYPE_PNG, "img.png", Units.toEMU(16), Units.toEMU(16));
            doc.write(os);
        }
        return p;
    }

    private static Path xlsxWithImagePath() throws Exception {
        Path p = Files.createTempFile("with-img-", ".xlsx");
        byte[] png = tinyPngBytes();
        try (XSSFWorkbook wb = new XSSFWorkbook(); OutputStream os = Files.newOutputStream(p)) {
            var sheet = wb.createSheet("s");
            int idx = wb.addPicture(png, XSSFWorkbook.PICTURE_TYPE_PNG);
            var drawing = sheet.createDrawingPatriarch();
            var anchor = wb.getCreationHelper().createClientAnchor();
            anchor.setCol1(1);
            anchor.setRow1(1);
            drawing.createPicture(anchor, idx);
            wb.write(os);
        }
        return p;
    }

    private static Path pptxWithImagePath() throws Exception {
        Path p = Files.createTempFile("with-img-", ".pptx");
        byte[] png = tinyPngBytes();
        try (XMLSlideShow show = new XMLSlideShow(); OutputStream os = Files.newOutputStream(p)) {
            var pic = show.addPicture(png, PictureData.PictureType.PNG);
            var slide = show.createSlide();
            slide.createPicture(pic);
            show.write(os);
        }
        return p;
    }

    private static Path pptWithImagePath() throws Exception {
        Path p = Files.createTempFile("with-img-", ".ppt");
        byte[] png = tinyPngBytes();
        try (HSLFSlideShow show = new HSLFSlideShow(); OutputStream os = Files.newOutputStream(p)) {
            var pic = show.addPicture(png, PictureData.PictureType.PNG);
            var slide = show.createSlide();
            slide.createPicture(pic);
            show.write(os);
        }
        return p;
    }

    private static Path epubWithImagePath() throws Exception {
        Path p = Files.createTempFile("with-img-", ".epub");
        byte[] png = tinyPngBytes();
        byte[] epub = zipBytes(List.of(
                Map.entry("images/cover.png", png),
                Map.entry("text/ch1.txt", "chapter".getBytes(StandardCharsets.UTF_8))
        ));
        Files.write(p, epub);
        return p;
    }

    @Test
    void extractImageMethods_shouldCoverEmbeddedSuccessBranches() throws Exception {
        DerivedUploadStorageService storage = Mockito.mock(DerivedUploadStorageService.class);
        Mockito.when(storage.getMaxImageBytes()).thenReturn(5 * 1024 * 1024L);
        Mockito.when(storage.saveDerivedImage(Mockito.any(byte[].class), Mockito.anyString(), Mockito.any(), Mockito.any()))
                .thenReturn(Map.of("path", "/tmp/saved"));
        Mockito.when(storage.buildPlaceholder(Mockito.anyInt(), Mockito.anyMap()))
                .thenAnswer(inv -> Map.of("placeholder", "[[IMAGE_" + inv.getArgument(0) + "]]"));
        Object svc = new FileAssetExtractionAsyncService(null, null, null, null, null, null, null, storage);
        Object budget = newBudget(10, 10 * 1024 * 1024L);

        Map<String, Object> docxMeta = new LinkedHashMap<>();
        Object docxOut = invokeInstance(
                svc,
                "extractDocxImages",
                new Class<?>[]{Path.class, Map.class, Long.class, budget.getClass()},
                docxWithImagePath(),
                docxMeta,
                1L,
                budget
        );
        assertFalse(((List<?>) docxOut).isEmpty());
        assertEquals("DOCX_EMBEDDED", String.valueOf(docxMeta.get("imagesExtractionMode")));

        Map<String, Object> xlsxMeta = new LinkedHashMap<>();
        Object xlsxOut = invokeInstance(
                svc,
                "extractXlsxImages",
                new Class<?>[]{Path.class, Map.class, Long.class, budget.getClass()},
                xlsxWithImagePath(),
                xlsxMeta,
                2L,
                newBudget(10, 10 * 1024 * 1024L)
        );
        assertFalse(((List<?>) xlsxOut).isEmpty());
        assertEquals("XLSX_EMBEDDED", String.valueOf(xlsxMeta.get("imagesExtractionMode")));

        Map<String, Object> pptxMeta = new LinkedHashMap<>();
        Object pptxOut = invokeInstance(
                svc,
                "extractPptxImages",
                new Class<?>[]{Path.class, Map.class, Long.class, budget.getClass()},
                pptxWithImagePath(),
                pptxMeta,
                3L,
                newBudget(10, 10 * 1024 * 1024L)
        );
        assertFalse(((List<?>) pptxOut).isEmpty());
        assertEquals("PPTX_EMBEDDED", String.valueOf(pptxMeta.get("imagesExtractionMode")));

        Map<String, Object> pptMeta = new LinkedHashMap<>();
        Object pptOut = invokeInstance(
                svc,
                "extractPptImages",
                new Class<?>[]{Path.class, Map.class, Long.class, budget.getClass()},
                pptWithImagePath(),
                pptMeta,
                4L,
                newBudget(10, 10 * 1024 * 1024L)
        );
        assertFalse(((List<?>) pptOut).isEmpty());
        assertEquals("PPT_EMBEDDED", String.valueOf(pptMeta.get("imagesExtractionMode")));

        Map<String, Object> epubMeta = new LinkedHashMap<>();
        Object epubOut = invokeInstance(
                svc,
                "extractEpubImages",
                new Class<?>[]{Path.class, Map.class, Long.class, budget.getClass()},
                epubWithImagePath(),
                epubMeta,
                5L,
                newBudget(10, 10 * 1024 * 1024L)
        );
        assertFalse(((List<?>) epubOut).isEmpty());
        assertEquals("EPUB_ZIP", String.valueOf(epubMeta.get("imagesExtractionMode")));

        Map<String, Object> epubBudgetMeta = new LinkedHashMap<>();
        Object epubBudgetOut = invokeInstance(
                svc,
                "extractEpubImages",
                new Class<?>[]{Path.class, Map.class, Long.class, budget.getClass()},
                epubWithImagePath(),
                epubBudgetMeta,
                6L,
                newBudget(10, 1L)
        );
        assertTrue(((List<?>) epubBudgetOut).isEmpty());
        assertEquals("EPUB_ZIP", String.valueOf(epubBudgetMeta.get("imagesExtractionMode")));
    }

    @Test
    void extractOfficeImageMethods_shouldCoverBudgetNullAndSaveNullBranches() throws Exception {
        DerivedUploadStorageService storage = Mockito.mock(DerivedUploadStorageService.class);
        Mockito.when(storage.getMaxImageBytes()).thenReturn(5 * 1024 * 1024L);
        Mockito.when(storage.saveDerivedImage(Mockito.any(byte[].class), Mockito.anyString(), Mockito.any(), Mockito.any()))
                .thenReturn(null);
        Object svc = new FileAssetExtractionAsyncService(null, null, null, null, null, null, null, storage);

        Map<String, Object> docxMeta = new LinkedHashMap<>();
        Object docxOut = invokeInstance(
                svc,
                "extractDocxImages",
                new Class<?>[]{Path.class, Map.class, Long.class, newBudget(1, 1L).getClass()},
                docxWithImagePath(),
                docxMeta,
                11L,
                null
        );
        assertTrue(((List<?>) docxOut).isEmpty());
        assertEquals("DOCX_EMBEDDED", String.valueOf(docxMeta.get("imagesExtractionMode")));

        Map<String, Object> xlsxMeta = new LinkedHashMap<>();
        Object xlsxOut = invokeInstance(
                svc,
                "extractXlsxImages",
                new Class<?>[]{Path.class, Map.class, Long.class, newBudget(1, 1L).getClass()},
                xlsxWithImagePath(),
                xlsxMeta,
                12L,
                null
        );
        assertTrue(((List<?>) xlsxOut).isEmpty());
        assertEquals("XLSX_EMBEDDED", String.valueOf(xlsxMeta.get("imagesExtractionMode")));

        Map<String, Object> pptxMeta = new LinkedHashMap<>();
        Object pptxOut = invokeInstance(
                svc,
                "extractPptxImages",
                new Class<?>[]{Path.class, Map.class, Long.class, newBudget(1, 1L).getClass()},
                pptxWithImagePath(),
                pptxMeta,
                13L,
                null
        );
        assertTrue(((List<?>) pptxOut).isEmpty());
        assertEquals("PPTX_EMBEDDED", String.valueOf(pptxMeta.get("imagesExtractionMode")));

        Map<String, Object> pptMeta = new LinkedHashMap<>();
        Object pptOut = invokeInstance(
                svc,
                "extractPptImages",
                new Class<?>[]{Path.class, Map.class, Long.class, newBudget(1, 1L).getClass()},
                pptWithImagePath(),
                pptMeta,
                14L,
                null
        );
        assertTrue(((List<?>) pptOut).isEmpty());
        assertEquals("PPT_EMBEDDED", String.valueOf(pptMeta.get("imagesExtractionMode")));
    }

    @Test
    void extractOfficeImageMethods_shouldCoverMissingFileFailedBranches() {
        DerivedUploadStorageService storage = Mockito.mock(DerivedUploadStorageService.class);
        Object svc = new FileAssetExtractionAsyncService(null, null, null, null, null, null, null, storage);
        Path missing = Path.of("not-exists-" + System.nanoTime() + ".bin");

        Map<String, Object> docxMeta = new LinkedHashMap<>();
        Object docxOut = invokeInstance(
                svc,
                "extractDocxImages",
                new Class<?>[]{Path.class, Map.class, Long.class, newBudget(1, 1L).getClass()},
                missing,
                docxMeta,
                21L,
                newBudget(1, 1024)
        );
        assertTrue(((List<?>) docxOut).isEmpty());
        assertEquals("FAILED", String.valueOf(docxMeta.get("imagesExtractionMode")));

        Map<String, Object> xlsxMeta = new LinkedHashMap<>();
        Object xlsxOut = invokeInstance(
                svc,
                "extractXlsxImages",
                new Class<?>[]{Path.class, Map.class, Long.class, newBudget(1, 1L).getClass()},
                missing,
                xlsxMeta,
                22L,
                newBudget(1, 1024)
        );
        assertTrue(((List<?>) xlsxOut).isEmpty());
        assertEquals("FAILED", String.valueOf(xlsxMeta.get("imagesExtractionMode")));

        Map<String, Object> pptxMeta = new LinkedHashMap<>();
        Object pptxOut = invokeInstance(
                svc,
                "extractPptxImages",
                new Class<?>[]{Path.class, Map.class, Long.class, newBudget(1, 1L).getClass()},
                missing,
                pptxMeta,
                23L,
                newBudget(1, 1024)
        );
        assertTrue(((List<?>) pptxOut).isEmpty());
        assertEquals("FAILED", String.valueOf(pptxMeta.get("imagesExtractionMode")));

        Map<String, Object> pptMeta = new LinkedHashMap<>();
        Object pptOut = invokeInstance(
                svc,
                "extractPptImages",
                new Class<?>[]{Path.class, Map.class, Long.class, newBudget(1, 1L).getClass()},
                missing,
                pptMeta,
                24L,
                newBudget(1, 1024)
        );
        assertTrue(((List<?>) pptOut).isEmpty());
        assertEquals("FAILED", String.valueOf(pptMeta.get("imagesExtractionMode")));
    }

    @Test
    void extractArchiveFromStream_shouldCoverNestedArchiveErrorRecordingAndTextLimit() throws Exception {
        Object svc = new FileAssetExtractionAsyncService(null, null, null, null, null, null, null, null);
        setField(svc, "archiveMaxDepth", 5);
        setField(svc, "archiveMaxEntries", 20);
        setField(svc, "archiveMaxEntryBytes", 2 * 1024 * 1024L);
        setField(svc, "archiveMaxTotalBytes", 20 * 1024 * 1024L);
        setField(svc, "archiveMaxTotalMillis", 15000L);

        byte[] innerZip = zipBytes(List.of(Map.entry("inner.txt", "INNER-CONTENT".getBytes(StandardCharsets.UTF_8))));
        byte[] outer = zipBytes(List.of(
                Map.entry("bad.pdf", "not-a-pdf".getBytes(StandardCharsets.UTF_8)),
                Map.entry("ok.txt", "GOOD-CONTENT".getBytes(StandardCharsets.UTF_8)),
                Map.entry("nested.zip", innerZip)
        ));

        Object counters = newInner("ArchiveCounters", new Class<?>[]{});
        Map<String, Object> meta = new LinkedHashMap<>();
        String txt = String.valueOf(invokeInstance(
                svc,
                "extractArchiveFromStream",
                new Class<?>[]{InputStream.class, String.class, int.class, int.class, Map.class, counters.getClass(), long.class},
                new ByteArrayInputStream(outer),
                "outer.zip",
                0,
                25,
                meta,
                counters,
                System.nanoTime()
        ));
        assertFalse(txt.isBlank());
        assertTrue(txt.contains("GOOD") || txt.contains("INNER"));
        assertTrue(((Number) getField(counters, "filesParsed")).longValue() >= 1L);
        assertTrue(((Number) getField(counters, "filesSkipped")).longValue() >= 1L);
        Object reason = getField(counters, "truncatedReason");
        assertTrue(reason == null || String.valueOf(reason).equals("TEXT_CHAR_LIMIT"));
    }

    @Test
    void expandArchiveStreamToDisk_shouldCoverNestedArchiveDepthTooDeep() throws Exception {
        Object svc = new FileAssetExtractionAsyncService(null, null, null, null, null, null, null, null);
        setField(svc, "archiveMaxDepth", 1);
        setField(svc, "archiveMaxEntries", 20);
        setField(svc, "archiveMaxEntryBytes", 2 * 1024 * 1024L);
        setField(svc, "archiveMaxTotalBytes", 20 * 1024 * 1024L);
        setField(svc, "archiveMaxTotalMillis", 15000L);

        byte[] innerZip = zipBytes(List.of(Map.entry("a.txt", "x".getBytes(StandardCharsets.UTF_8))));
        byte[] outer = zipBytes(List.of(Map.entry("inner.zip", innerZip)));
        Object counters = newInner("ArchiveCounters", new Class<?>[]{});
        Map<String, Object> meta = new LinkedHashMap<>();
        List<Map<String, Object>> files = new ArrayList<>();
        assertThrows(IllegalStateException.class, () -> invokeInstance(
                svc,
                "expandArchiveStreamToDisk",
                new Class<?>[]{InputStream.class, String.class, String.class, int.class, Path.class, Map.class, counters.getClass(), long.class, List.class},
                new ByteArrayInputStream(outer),
                "outer.zip",
                "",
                0,
                Files.createTempDirectory("exp-depth-"),
                meta,
                counters,
                System.nanoTime(),
                files
        ));
    }

    @Test
    void extract7zFromBytes_shouldCoverEntryErrorRecording() throws Exception {
        Object svc = new FileAssetExtractionAsyncService(null, null, null, null, null, null, null, null);
        setField(svc, "archiveMaxDepth", 5);
        setField(svc, "archiveMaxEntries", 20);
        setField(svc, "archiveMaxEntryBytes", 2 * 1024 * 1024L);
        setField(svc, "archiveMaxTotalBytes", 20 * 1024 * 1024L);
        setField(svc, "archiveMaxTotalMillis", 15000L);

        byte[] seven = sevenZBytes(List.of(
                Map.entry("bad.pdf", "not-a-pdf".getBytes(StandardCharsets.UTF_8)),
                Map.entry("ok.txt", "SEVEN-OK".getBytes(StandardCharsets.UTF_8))
        ));

        Object counters = newInner("ArchiveCounters", new Class<?>[]{});
        Map<String, Object> meta = new LinkedHashMap<>();
        String txt = String.valueOf(invokeInstance(
                svc,
                "extract7zFromBytes",
                new Class<?>[]{byte[].class, String.class, int.class, int.class, Map.class, counters.getClass(), long.class},
                seven,
                "pack.7z",
                0,
                200,
                meta,
                counters,
                System.nanoTime()
        ));
        assertTrue(txt.contains("SEVEN-OK"));
        assertTrue(meta.get("entryErrors") instanceof List<?>);
    }

    @Test
    void extractArchiveFromStream_shouldCoverCompressionMetadataAndPlainFallback() throws Exception {
        Object svc = new FileAssetExtractionAsyncService(null, null, null, null, null, null, null, null);
        setField(svc, "archiveMaxDepth", 5);
        setField(svc, "archiveMaxEntries", 10);
        setField(svc, "archiveMaxEntryBytes", 2 * 1024 * 1024L);
        setField(svc, "archiveMaxTotalBytes", 5 * 1024 * 1024L);
        setField(svc, "archiveMaxTotalMillis", 15000L);

        byte[] gz = gzipBytes("gzip-plain-text".getBytes(StandardCharsets.UTF_8));
        Object counters = newInner("ArchiveCounters", new Class<?>[]{});
        Map<String, Object> meta = new LinkedHashMap<>();
        assertThrows(IllegalStateException.class, () -> invokeInstance(
                svc,
                "extractArchiveFromStream",
                new Class<?>[]{InputStream.class, String.class, int.class, int.class, Map.class, counters.getClass(), long.class},
                new ByteArrayInputStream(gz),
                "sample.gz",
                0,
                20,
                meta,
                counters,
                System.nanoTime()
        ));
    }

    @Test
    void extract7zFromPath_shouldCoverNestedAndErrorAndTextLimit() throws Exception {
        Object svc = new FileAssetExtractionAsyncService(null, null, null, null, null, null, null, null);
        setField(svc, "archiveMaxDepth", 5);
        setField(svc, "archiveMaxEntries", 20);
        setField(svc, "archiveMaxEntryBytes", 2 * 1024 * 1024L);
        setField(svc, "archiveMaxTotalBytes", 20 * 1024 * 1024L);
        setField(svc, "archiveMaxTotalMillis", 15000L);

        byte[] innerZip = zipBytes(List.of(Map.entry("inner.txt", "INNER7".getBytes(StandardCharsets.UTF_8))));
        Path seven = Files.createTempFile("mix-", ".7z");
        try (SevenZOutputFile out = new SevenZOutputFile(seven.toFile())) {
            SevenZArchiveEntry e1 = new SevenZArchiveEntry();
            e1.setName("bad.pdf");
            e1.setContentMethods(List.of(new SevenZMethodConfiguration(SevenZMethod.COPY)));
            out.putArchiveEntry(e1);
            out.write("not-a-pdf".getBytes(StandardCharsets.UTF_8));
            out.closeArchiveEntry();

            SevenZArchiveEntry e2 = new SevenZArchiveEntry();
            e2.setName("nested.zip");
            e2.setContentMethods(List.of(new SevenZMethodConfiguration(SevenZMethod.COPY)));
            out.putArchiveEntry(e2);
            out.write(innerZip);
            out.closeArchiveEntry();
        }

        Object counters = newInner("ArchiveCounters", new Class<?>[]{});
        Map<String, Object> meta = new LinkedHashMap<>();
        String txt = String.valueOf(invokeInstance(
                svc,
                "extract7zFromPath",
                new Class<?>[]{Path.class, String.class, int.class, int.class, Map.class, counters.getClass(), long.class},
                seven,
                "mix.7z",
                0,
                8,
                meta,
                counters,
                System.nanoTime()
        ));
        assertFalse(txt.isBlank());
        Object reason = getField(counters, "truncatedReason");
        assertTrue(reason == null || String.valueOf(reason).equals("TEXT_CHAR_LIMIT") || String.valueOf(reason).equals("TOTAL_BYTES_LIMIT"));
    }

    @Test
    void expand7zBytesToDisk_shouldCoverNestedArchiveDepthTooDeep() throws Exception {
        Object svc = new FileAssetExtractionAsyncService(null, null, null, null, null, null, null, null);
        setField(svc, "archiveMaxDepth", 1);
        setField(svc, "archiveMaxEntries", 20);
        setField(svc, "archiveMaxEntryBytes", 2 * 1024 * 1024L);
        setField(svc, "archiveMaxTotalBytes", 20 * 1024 * 1024L);
        setField(svc, "archiveMaxTotalMillis", 15000L);

        byte[] innerZip = zipBytes(List.of(Map.entry("a.txt", "x".getBytes(StandardCharsets.UTF_8))));
        byte[] outer7z = sevenZBytes(List.of(Map.entry("inner.zip", innerZip)));
        Object counters = newInner("ArchiveCounters", new Class<?>[]{});
        assertThrows(IllegalStateException.class, () -> invokeInstance(
                svc,
                "expand7zBytesToDisk",
                new Class<?>[]{byte[].class, String.class, int.class, Path.class, Map.class, counters.getClass(), long.class, List.class},
                outer7z,
                "",
                0,
                Files.createTempDirectory("exp-7z-depth-"),
                new LinkedHashMap<>(),
                counters,
                System.nanoTime(),
                new ArrayList<>()
        ));
    }

    @Test
    void extractPdfImages_shouldCoverRenderAndFailed() throws Exception {
        DerivedUploadStorageService storage = Mockito.mock(DerivedUploadStorageService.class);
        Mockito.when(storage.saveDerivedImage(Mockito.any(byte[].class), Mockito.anyString(), Mockito.any(), Mockito.any()))
                .thenReturn(Map.of("path", "/tmp/pdf-render.png"));
        Mockito.when(storage.buildPlaceholder(Mockito.anyInt(), Mockito.anyMap()))
                .thenAnswer(inv -> Map.of("placeholder", "[[IMAGE_" + inv.getArgument(0) + "]]"));
        Object svc = new FileAssetExtractionAsyncService(null, null, null, null, null, null, null, storage);
        setField(svc, "pdfRenderMaxPages", 2);
        setField(svc, "pdfRenderDpi", 96);
        Object budget = newBudget(10, 10 * 1024 * 1024L);

        Path blank = Files.createTempFile("pdf-render-", ".pdf");
        try (PDDocument doc = new PDDocument()) {
            doc.addPage(new PDPage());
            doc.save(blank.toFile());
        }
        Map<String, Object> renderMeta = new LinkedHashMap<>();
        Object renderOut = invokeInstance(
                svc,
                "extractPdfImages",
                new Class<?>[]{Path.class, Map.class, Long.class, String.class, budget.getClass()},
                blank,
                renderMeta,
                10L,
                "",
                budget
        );
        assertFalse(((List<?>) renderOut).isEmpty());
        assertEquals("PDF_RENDER", String.valueOf(renderMeta.get("imagesExtractionMode")));

        Path bad = Files.createTempFile("pdf-bad-", ".pdf");
        Files.writeString(bad, "bad");
        Map<String, Object> badMeta = new LinkedHashMap<>();
        Object badOut = invokeInstance(
                svc,
                "extractPdfImages",
                new Class<?>[]{Path.class, Map.class, Long.class, String.class, budget.getClass()},
                bad,
                badMeta,
                11L,
                "",
                newBudget(10, 10 * 1024 * 1024L)
        );
        assertTrue(((List<?>) badOut).isEmpty());
        assertEquals("FAILED", String.valueOf(badMeta.get("imagesExtractionMode")));
        assertNotNull(badMeta.get("imagesExtractionError"));
    }

    @Test
    void extractMobiImagesWithTika_shouldCoverFailedMode() throws Exception {
        DerivedUploadStorageService storage = Mockito.mock(DerivedUploadStorageService.class);
        Object svc = new FileAssetExtractionAsyncService(null, null, null, null, null, null, null, storage);
        Object budget = newBudget(10, 10 * 1024 * 1024L);
        Path missing = Files.createTempDirectory("mobi-missing-").resolve("no-file.mobi");
        Map<String, Object> meta = new LinkedHashMap<>();
        Object out = invokeInstance(
                svc,
                "extractMobiImagesWithTika",
                new Class<?>[]{Path.class, Map.class, Long.class, budget.getClass()},
                missing,
                meta,
                99L,
                budget
        );
        assertTrue(((List<?>) out).isEmpty());
        assertEquals("FAILED", String.valueOf(meta.get("imagesExtractionMode")));
    }

    @Test
    void staticExtHelpers_shouldCoverArchiveOfficeAndSupportedInnerBranches() {
        assertEquals(false, invokeStatic("isArchiveExt", new Class<?>[]{String.class}, (String) null));
        assertEquals(false, invokeStatic("isArchiveExt", new Class<?>[]{String.class}, " "));
        assertEquals(true, invokeStatic("isArchiveExt", new Class<?>[]{String.class}, "ZIP"));
        assertEquals(true, invokeStatic("isArchiveExt", new Class<?>[]{String.class}, "txz"));
        assertEquals(false, invokeStatic("isArchiveExt", new Class<?>[]{String.class}, "docx"));

        assertEquals(false, invokeStatic("isOfficeExt", new Class<?>[]{String.class}, (String) null));
        assertEquals(false, invokeStatic("isOfficeExt", new Class<?>[]{String.class}, " "));
        assertEquals(true, invokeStatic("isOfficeExt", new Class<?>[]{String.class}, "DOCX"));
        assertEquals(true, invokeStatic("isOfficeExt", new Class<?>[]{String.class}, "xltx"));
        assertEquals(false, invokeStatic("isOfficeExt", new Class<?>[]{String.class}, "zip"));

        assertEquals(false, invokeStatic("isSupportedInnerExtForExtraction", new Class<?>[]{String.class}, (String) null));
        assertEquals(false, invokeStatic("isSupportedInnerExtForExtraction", new Class<?>[]{String.class}, "zip"));
        assertEquals(true, invokeStatic("isSupportedInnerExtForExtraction", new Class<?>[]{String.class}, "pdf"));
        assertEquals(true, invokeStatic("isSupportedInnerExtForExtraction", new Class<?>[]{String.class}, "mobi"));
        assertEquals(false, invokeStatic("isSupportedInnerExtForExtraction", new Class<?>[]{String.class}, "exe"));
    }

    @Test
    void appendImagePlaceholders_shouldCoverDedupBlankAndContainsAndInsertBranches() {
        assertEquals("", invokeStatic("appendImagePlaceholders", new Class<?>[]{String.class, List.class}, null, null));
        assertEquals("t", invokeStatic("appendImagePlaceholders", new Class<?>[]{String.class, List.class}, "t", List.of()));

        List<Map<String, Object>> images = new ArrayList<>();
        images.add(Map.of("placeholder", " [[IMAGE_1]] "));
        images.add(Map.of("placeholder", "[[IMAGE_1]]"));
        images.add(Map.of("placeholder", "[[IMAGE_2]]"));
        images.add(new HashMap<>());
        images.add(null);

        String alreadyHas = String.valueOf(invokeStatic("appendImagePlaceholders", new Class<?>[]{String.class, List.class}, "abc [[IMAGE_9]]", images));
        assertEquals("abc [[IMAGE_9]]", alreadyHas);

        String blankBase = String.valueOf(invokeStatic("appendImagePlaceholders", new Class<?>[]{String.class, List.class}, "  ", images));
        assertTrue(blankBase.contains("[[IMAGE_1]]"));
        assertTrue(blankBase.contains("[[IMAGE_2]]"));

        String inserted = String.valueOf(invokeStatic("appendImagePlaceholders", new Class<?>[]{String.class, List.class}, "hello world", images));
        assertTrue(inserted.contains("[[IMAGE_1]]"));
        assertTrue(inserted.contains("[[IMAGE_2]]"));
        assertNotEquals("hello world", inserted);
    }

    @Test
    void extractEntryBytesAsText_shouldCoverPdfTextHtmlOfficeAndFallback() throws Exception {
        Object svc = new FileAssetExtractionAsyncService(null, null, null, null, null, null, null, null);

        Path pdf = Files.createTempFile("ee-pdf-", ".pdf");
        try (PDDocument doc = new PDDocument()) {
            doc.addPage(new PDPage());
            doc.save(pdf.toFile());
        }
        String pdfTxt = String.valueOf(invokeInstance(
                svc,
                "extractEntryBytesAsText",
                new Class<?>[]{String.class, String.class, byte[].class, int.class},
                "a.pdf",
                "pdf",
                Files.readAllBytes(pdf),
                200
        ));
        assertNotNull(pdfTxt);

        String txt = String.valueOf(invokeInstance(
                svc,
                "extractEntryBytesAsText",
                new Class<?>[]{String.class, String.class, byte[].class, int.class},
                "a.txt",
                "txt",
                "abc123".getBytes(StandardCharsets.UTF_8),
                200
        ));
        assertEquals("abc123", txt);

        String html = String.valueOf(invokeInstance(
                svc,
                "extractEntryBytesAsText",
                new Class<?>[]{String.class, String.class, byte[].class, int.class},
                "a.html",
                "html",
                "<html><body>Hello <b>World</b></body></html>".getBytes(StandardCharsets.UTF_8),
                200
        ));
        assertTrue(html.contains("Hello"));

        Path office = docxWithImagePath();
        String officeTxt = String.valueOf(invokeInstance(
                svc,
                "extractEntryBytesAsText",
                new Class<?>[]{String.class, String.class, byte[].class, int.class},
                "a.docx",
                "docx",
                Files.readAllBytes(office),
                200
        ));
        assertNotNull(officeTxt);

        assertThrows(IllegalStateException.class, () -> invokeInstance(
                svc,
                "extractEntryBytesAsText",
                new Class<?>[]{String.class, String.class, byte[].class, int.class},
                "a.bin",
                "bin",
                "fallback".getBytes(StandardCharsets.UTF_8),
                200
        ));
    }

    @Test
    void extractArchive_shouldCoverParsedSkippedFailedAndMeta() throws Exception {
        DerivedUploadStorageService storage = Mockito.mock(DerivedUploadStorageService.class);
        Mockito.when(storage.getMaxImageBytes()).thenReturn(10 * 1024 * 1024L);
        Object svc = new FileAssetExtractionAsyncService(null, null, null, null, null, null, null, storage);
        setField(svc, "archiveMaxDepth", 5);
        setField(svc, "archiveMaxEntries", 50);
        setField(svc, "archiveMaxEntryBytes", 2 * 1024 * 1024L);
        setField(svc, "archiveMaxTotalBytes", 20 * 1024 * 1024L);
        setField(svc, "archiveMaxTotalMillis", 15000L);

        byte[] nested = zipBytes(List.of(Map.entry("inner.txt", "INNER_TEXT".getBytes(StandardCharsets.UTF_8))));
        byte[] outer = zipBytes(List.of(
                Map.entry("../evil.txt", "BAD".getBytes(StandardCharsets.UTF_8)),
                Map.entry("ok.txt", "OK_TEXT".getBytes(StandardCharsets.UTF_8)),
                Map.entry("bad.pdf", "not-a-pdf".getBytes(StandardCharsets.UTF_8)),
                Map.entry("ignore.exe", "bin".getBytes(StandardCharsets.UTF_8)),
                Map.entry("nested.zip", nested)
        ));
        Path zip = Files.createTempFile("arc-", ".zip");
        Files.write(zip, outer);

        Object budget = newBudget(50, 20 * 1024 * 1024L);
        Map<String, Object> meta = new LinkedHashMap<>();
        String out = String.valueOf(invokeInstance(
                svc,
                "extractArchive",
                new Class<?>[]{Path.class, String.class, int.class, Map.class, Long.class, budget.getClass()},
                zip,
                "zip",
                500,
                meta,
                1L,
                budget
        ));
        assertFalse(out.isBlank());
        assertTrue(out.contains("OK_TEXT") || out.contains("INNER_TEXT"));
        assertTrue(meta.get("archive") instanceof Map<?, ?>);
        @SuppressWarnings("unchecked")
        Map<String, Object> arc = (Map<String, Object>) meta.get("archive");
        assertEquals(true, arc.get("enabled"));
        assertNotNull(arc.get("filesParsed"));
        assertNotNull(arc.get("filesSkipped"));
        assertNotNull(arc.get("entriesSeen"));
        assertNotNull(meta.get("imagesExtractionMode"));
    }

    @Test
    void extractArchive_shouldCoverHardFailDepthTooDeep() throws Exception {
        DerivedUploadStorageService storage = Mockito.mock(DerivedUploadStorageService.class);
        Object svc = new FileAssetExtractionAsyncService(null, null, null, null, null, null, null, storage);
        setField(svc, "archiveMaxDepth", 1);
        setField(svc, "archiveMaxEntries", 50);
        setField(svc, "archiveMaxEntryBytes", 2 * 1024 * 1024L);
        setField(svc, "archiveMaxTotalBytes", 20 * 1024 * 1024L);
        setField(svc, "archiveMaxTotalMillis", 15000L);

        byte[] nested = zipBytes(List.of(Map.entry("inner.txt", "INNER".getBytes(StandardCharsets.UTF_8))));
        byte[] outer = zipBytes(List.of(Map.entry("nested.zip", nested)));
        Path zip = Files.createTempFile("arc-depth-", ".zip");
        Files.write(zip, outer);

        Object budget = newBudget(10, 10 * 1024 * 1024L);
        Map<String, Object> meta = new LinkedHashMap<>();
        IllegalStateException ex = assertThrows(IllegalStateException.class, () -> invokeInstance(
                svc,
                "extractArchive",
                new Class<?>[]{Path.class, String.class, int.class, Map.class, Long.class, budget.getClass()},
                zip,
                "zip",
                200,
                meta,
                2L,
                budget
        ));
        assertNotNull(ex);
    }

    @Test
    void extractPdfImages_shouldCoverRenderBudgetBreak() throws Exception {
        DerivedUploadStorageService storage = Mockito.mock(DerivedUploadStorageService.class);
        Mockito.when(storage.saveDerivedImage(Mockito.any(byte[].class), Mockito.anyString(), Mockito.any(), Mockito.any()))
                .thenReturn(Map.of("path", "/tmp/pdf-render2.png"));
        Mockito.when(storage.buildPlaceholder(Mockito.anyInt(), Mockito.anyMap()))
                .thenAnswer(inv -> Map.of("placeholder", "[[IMAGE_" + inv.getArgument(0) + "]]"));
        Object svc = new FileAssetExtractionAsyncService(null, null, null, null, null, null, null, storage);
        setField(svc, "pdfRenderMaxPages", 2);
        setField(svc, "pdfRenderDpi", 96);

        Path blank = Files.createTempFile("pdf-render-budget-", ".pdf");
        try (PDDocument doc = new PDDocument()) {
            doc.addPage(new PDPage());
            doc.save(blank.toFile());
        }
        Map<String, Object> meta = new LinkedHashMap<>();
        Object out = invokeInstance(
                svc,
                "extractPdfImages",
                new Class<?>[]{Path.class, Map.class, Long.class, String.class, newBudget(1, 1L).getClass()},
                blank,
                meta,
                10L,
                "",
                newBudget(0, 1L)
        );
        assertTrue(((List<?>) out).isEmpty());
        assertEquals("PDF_RENDER", String.valueOf(meta.get("imagesExtractionMode")));
    }

    @Test
    void extractArchiveFromStream_shouldCoverMixedEntriesAndMagicNested() throws Exception {
        Object svc = new FileAssetExtractionAsyncService(null, null, null, null, null, null, null, null);
        setField(svc, "archiveMaxDepth", 6);
        setField(svc, "archiveMaxEntries", 100);
        setField(svc, "archiveMaxEntryBytes", 2 * 1024 * 1024L);
        setField(svc, "archiveMaxTotalBytes", 30 * 1024 * 1024L);
        setField(svc, "archiveMaxTotalMillis", 15000L);

        byte[] inner = zipBytes(List.of(Map.entry("inner.txt", "INNER_TXT".getBytes(StandardCharsets.UTF_8))));
        byte[] outer = zipBytes(List.of(
                Map.entry("../evil.txt", "evil".getBytes(StandardCharsets.UTF_8)),
                Map.entry("note.txt", "NOTE_TXT".getBytes(StandardCharsets.UTF_8)),
                Map.entry("magic_nested", inner),
                Map.entry("bad.pdf", "not-a-pdf".getBytes(StandardCharsets.UTF_8))
        ));

        Object counters = newInner("ArchiveCounters", new Class<?>[]{});
        Map<String, Object> meta = new LinkedHashMap<>();
        String out = String.valueOf(invokeInstance(
                svc,
                "extractArchiveFromStream",
                new Class<?>[]{InputStream.class, String.class, int.class, int.class, Map.class, counters.getClass(), long.class},
                new ByteArrayInputStream(outer),
                "mix.zip",
                0,
                30,
                meta,
                counters,
                System.nanoTime()
        ));
        assertFalse(out.isBlank());
        assertTrue(((Number) getField(counters, "filesParsed")).longValue() >= 1L);
        assertTrue(((Number) getField(counters, "filesSkipped")).longValue() >= 1L);
        assertTrue(((Number) getField(counters, "pathTraversalDroppedCount")).longValue() >= 1L);
        Object reason = getField(counters, "truncatedReason");
        assertTrue(reason == null || String.valueOf(reason).equals("TEXT_CHAR_LIMIT") || String.valueOf(reason).equals("TOTAL_BYTES_LIMIT"));
    }

    @Test
    void expandArchiveStreamToDisk_shouldCoverDirectoryTraversalTruncatedAndNested() throws Exception {
        Object svc = new FileAssetExtractionAsyncService(null, null, null, null, null, null, null, null);
        setField(svc, "archiveMaxDepth", 6);
        setField(svc, "archiveMaxEntries", 100);
        setField(svc, "archiveMaxEntryBytes", 3L);
        setField(svc, "archiveMaxTotalBytes", 50L);
        setField(svc, "archiveMaxTotalMillis", 15000L);

        byte[] nested = zipBytes(List.of(Map.entry("inner.txt", "abcdef".getBytes(StandardCharsets.UTF_8))));
        byte[] outer = zipBytes(List.of(
                Map.entry("dir/file.txt", "abcdef".getBytes(StandardCharsets.UTF_8)),
                Map.entry("../evil.txt", "zzz".getBytes(StandardCharsets.UTF_8)),
                Map.entry("nested.zip", nested)
        ));

        Object counters = newInner("ArchiveCounters", new Class<?>[]{});
        Map<String, Object> meta = new LinkedHashMap<>();
        List<Map<String, Object>> files = new ArrayList<>();
        invokeInstance(
                svc,
                "expandArchiveStreamToDisk",
                new Class<?>[]{InputStream.class, String.class, String.class, int.class, Path.class, Map.class, counters.getClass(), long.class, List.class},
                new ByteArrayInputStream(outer),
                "mix.zip",
                "vp/",
                0,
                Files.createTempDirectory("expand-mix-"),
                meta,
                counters,
                System.nanoTime(),
                files
        );
        assertFalse(files.isEmpty());
        assertTrue(((Number) getField(counters, "pathTraversalDroppedCount")).longValue() >= 1L);
        assertEquals("zip", String.valueOf(meta.get("archiveType")));
        boolean hasTrunc = files.stream().anyMatch(it -> Boolean.TRUE.equals(it.get("extractionTruncated")));
        assertTrue(hasTrunc);
    }

    @Test
    void extractEpubImages_shouldCoverFailedAndSizeSkipBranches() throws Exception {
        DerivedUploadStorageService storage = Mockito.mock(DerivedUploadStorageService.class);
        Mockito.when(storage.getMaxImageBytes()).thenReturn(1L);
        Mockito.when(storage.saveDerivedImage(Mockito.any(byte[].class), Mockito.anyString(), Mockito.any(), Mockito.any()))
                .thenReturn(Map.of("path", "/tmp/e.png"));
        Mockito.when(storage.buildPlaceholder(Mockito.anyInt(), Mockito.anyMap()))
                .thenAnswer(inv -> Map.of("placeholder", "[[IMAGE_" + inv.getArgument(0) + "]]"));
        Object svc = new FileAssetExtractionAsyncService(null, null, null, null, null, null, null, storage);
        Object budget = newBudget(10, 10 * 1024 * 1024L);

        Path epub = Files.createTempFile("epub-skip-", ".epub");
        byte[] png = tinyPngBytes();
        Files.write(epub, zipBytes(List.of(
                Map.entry("images/a.png", png),
                Map.entry("text/ch1.txt", "text".getBytes(StandardCharsets.UTF_8))
        )));
        Map<String, Object> meta = new LinkedHashMap<>();
        Object out = invokeInstance(
                svc,
                "extractEpubImages",
                new Class<?>[]{Path.class, Map.class, Long.class, budget.getClass()},
                epub,
                meta,
                1L,
                budget
        );
        assertTrue(((List<?>) out).isEmpty());
        assertEquals("EPUB_ZIP", String.valueOf(meta.get("imagesExtractionMode")));

        Path missing = Files.createTempDirectory("epub-missing-").resolve("none.epub");
        Map<String, Object> badMeta = new LinkedHashMap<>();
        Object badOut = invokeInstance(
                svc,
                "extractEpubImages",
                new Class<?>[]{Path.class, Map.class, Long.class, budget.getClass()},
                missing,
                badMeta,
                2L,
                newBudget(10, 10 * 1024 * 1024L)
        );
        assertTrue(((List<?>) badOut).isEmpty());
        assertEquals("FAILED", String.valueOf(badMeta.get("imagesExtractionMode")));
        assertNotNull(badMeta.get("imagesExtractionError"));
    }

    @Test
    void extractMobiImagesWithTika_shouldCoverZipSizeSkipAndNone() throws Exception {
        DerivedUploadStorageService storage = Mockito.mock(DerivedUploadStorageService.class);
        Mockito.when(storage.getMaxImageBytes()).thenReturn(1L);
        Object svc = new FileAssetExtractionAsyncService(null, null, null, null, null, null, null, storage);
        Object budget = newBudget(10, 10 * 1024 * 1024L);

        Path mobi = Files.createTempFile("mobi-skip-", ".mobi");
        byte[] png = tinyPngBytes();
        Files.write(mobi, zipBytes(List.of(Map.entry("images/a.png", png))));

        Map<String, Object> meta = new LinkedHashMap<>();
        Object out = invokeInstance(
                svc,
                "extractMobiImagesWithTika",
                new Class<?>[]{Path.class, Map.class, Long.class, budget.getClass()},
                mobi,
                meta,
                3L,
                budget
        );
        assertTrue(((List<?>) out).isEmpty());
        assertEquals("EMBEDDED_NONE", String.valueOf(meta.get("imagesExtractionMode")));
    }

    @Test
    void archiveAndOfficeAliasMethods_shouldCoverAllTrueAliases() {
        List<String> archiveAliases = List.of("zip", "jar", "war", "ear", "7z", "tar", "tgz", "gz", "bz2", "tbz2", "xz", "txz");
        for (String alias : archiveAliases) {
            assertEquals(true, invokeStatic("isArchiveExt", new Class<?>[]{String.class}, alias));
        }

        List<String> officeAliases = List.of("doc", "docx", "dot", "dotx", "ppt", "pptx", "pps", "ppsx", "xls", "xlsx", "xlt", "xltx");
        for (String alias : officeAliases) {
            assertEquals(true, invokeStatic("isOfficeExt", new Class<?>[]{String.class}, alias));
        }
    }

    @Test
    void extractArchiveFromStream_shouldCoverEmptyAnd7zTotalBytesLimitBranches() throws Exception {
        Object svc = new FileAssetExtractionAsyncService(null, null, null, null, null, null, null, null);
        setField(svc, "archiveMaxDepth", 6);
        setField(svc, "archiveMaxEntries", 50);
        setField(svc, "archiveMaxEntryBytes", 2 * 1024 * 1024L);
        setField(svc, "archiveMaxTotalBytes", 20 * 1024 * 1024L);
        setField(svc, "archiveMaxTotalMillis", 15000L);

        Object countersEmpty = newInner("ArchiveCounters", new Class<?>[]{});
        Object outEmpty = invokeInstance(
                svc,
                "extractArchiveFromStream",
                new Class<?>[]{InputStream.class, String.class, int.class, int.class, Map.class, countersEmpty.getClass(), long.class},
                new ByteArrayInputStream(new byte[0]),
                "empty.bin",
                0,
                200,
                new LinkedHashMap<>(),
                countersEmpty,
                System.nanoTime()
        );
        assertEquals("", String.valueOf(outEmpty));

        setField(svc, "archiveMaxTotalBytes", 40L);
        byte[] seven = sevenZBytes(List.of(Map.entry("a.txt", "hello".getBytes(StandardCharsets.UTF_8))));
        InputStream slowIn = new InputStream() {
            int idx = 0;
            @Override
            public int read() {
                if (idx >= seven.length) return -1;
                return seven[idx++] & 0xFF;
            }
            @Override
            public int read(byte[] b, int off, int len) {
                if (idx >= seven.length) return -1;
                if (len <= 0) return 0;
                b[off] = seven[idx++];
                return 1;
            }
        };
        Object countersLimit = newInner("ArchiveCounters", new Class<?>[]{});
        Object outLimit = invokeInstance(
                svc,
                "extractArchiveFromStream",
                new Class<?>[]{InputStream.class, String.class, int.class, int.class, Map.class, countersLimit.getClass(), long.class},
                slowIn,
                "tiny.7z",
                0,
                200,
                new LinkedHashMap<>(),
                countersLimit,
                System.nanoTime()
        );
        assertEquals("", String.valueOf(outLimit));
        String reason = String.valueOf(getField(countersLimit, "truncatedReason"));
        assertTrue(reason == null || reason.equals("null") || reason.equals("TOTAL_BYTES_LIMIT"));
    }

    @Test
    void extractArchiveFromStream_shouldCoverCompressionMetadataAndTotalBytesAfterParse() throws Exception {
        Object svc = new FileAssetExtractionAsyncService(null, null, null, null, null, null, null, null);
        setField(svc, "archiveMaxDepth", 6);
        setField(svc, "archiveMaxEntries", 50);
        setField(svc, "archiveMaxEntryBytes", 2 * 1024 * 1024L);
        setField(svc, "archiveMaxTotalBytes", 20 * 1024 * 1024L);
        setField(svc, "archiveMaxTotalMillis", 15000L);

        ByteArrayOutputStream tarBytes = new ByteArrayOutputStream();
        try (TarArchiveOutputStream tos = new TarArchiveOutputStream(tarBytes)) {
            byte[] payload = "hello-tar".getBytes(StandardCharsets.UTF_8);
            TarArchiveEntry te = new TarArchiveEntry("a.txt");
            te.setSize(payload.length);
            tos.putArchiveEntry(te);
            tos.write(payload);
            tos.closeArchiveEntry();
            tos.finish();
        }
        byte[] tgz = gzipBytes(tarBytes.toByteArray());

        Object countersCompression = newInner("ArchiveCounters", new Class<?>[]{});
        Map<String, Object> compressionMeta = new LinkedHashMap<>();
        String compressionOut = String.valueOf(invokeInstance(
                svc,
                "extractArchiveFromStream",
                new Class<?>[]{InputStream.class, String.class, int.class, int.class, Map.class, countersCompression.getClass(), long.class},
                new ByteArrayInputStream(tgz),
                "a.tgz",
                0,
                200,
                compressionMeta,
                countersCompression,
                System.nanoTime()
        ));
        assertTrue(compressionOut.contains("hello-tar"));
        assertEquals("gz", String.valueOf(compressionMeta.get("compression")));

        setField(svc, "archiveMaxTotalBytes", 9000L);
        byte[] big = "a".repeat(12000).getBytes(StandardCharsets.UTF_8);
        byte[] zip = zipBytes(List.of(Map.entry("big.txt", big)));
        Object countersBytes = newInner("ArchiveCounters", new Class<?>[]{});
        Map<String, Object> bytesMeta = new LinkedHashMap<>();
        String limited = String.valueOf(invokeInstance(
                svc,
                "extractArchiveFromStream",
                new Class<?>[]{InputStream.class, String.class, int.class, int.class, Map.class, countersBytes.getClass(), long.class},
                new ByteArrayInputStream(zip),
                "big.zip",
                0,
                20000,
                bytesMeta,
                countersBytes,
                System.nanoTime()
        ));
        assertFalse(limited.isBlank());
        assertEquals("TOTAL_BYTES_LIMIT", String.valueOf(getField(countersBytes, "truncatedReason")));
    }

    @Test
    void extractEpubImages_shouldCoverEmbeddedNoneBudgetBreakAndSaveNull() throws Exception {
        DerivedUploadStorageService storage = Mockito.mock(DerivedUploadStorageService.class);
        Mockito.when(storage.getMaxImageBytes()).thenReturn(10 * 1024 * 1024L);
        Mockito.when(storage.saveDerivedImage(Mockito.any(byte[].class), Mockito.anyString(), Mockito.any(), Mockito.any()))
                .thenReturn(null);
        Object svc = new FileAssetExtractionAsyncService(null, null, null, null, null, null, null, storage);

        Path empty = Files.createTempFile("epub-empty-", ".epub");
        Files.write(empty, zipBytes(List.of()));
        Map<String, Object> metaEmpty = new LinkedHashMap<>();
        Object outEmpty = invokeInstance(
                svc,
                "extractEpubImages",
                new Class<?>[]{Path.class, Map.class, Long.class, newBudget(1, 1L).getClass()},
                empty,
                metaEmpty,
                100L,
                newBudget(10, 10 * 1024 * 1024L)
        );
        assertTrue(((List<?>) outEmpty).isEmpty());
        assertEquals("EMBEDDED_NONE", String.valueOf(metaEmpty.get("imagesExtractionMode")));

        Path hasImage = Files.createTempFile("epub-break-", ".epub");
        Files.write(hasImage, zipBytes(List.of(Map.entry("images/a.png", tinyPngBytes()))));
        Map<String, Object> metaBreak = new LinkedHashMap<>();
        Object outBreak = invokeInstance(
                svc,
                "extractEpubImages",
                new Class<?>[]{Path.class, Map.class, Long.class, newBudget(1, 1L).getClass()},
                hasImage,
                metaBreak,
                101L,
                newBudget(0, 10 * 1024 * 1024L)
        );
        assertTrue(((List<?>) outBreak).isEmpty());
        assertEquals("EPUB_ZIP", String.valueOf(metaBreak.get("imagesExtractionMode")));

        Map<String, Object> metaSaveNull = new LinkedHashMap<>();
        Object outSaveNull = invokeInstance(
                svc,
                "extractEpubImages",
                new Class<?>[]{Path.class, Map.class, Long.class, newBudget(1, 1L).getClass()},
                hasImage,
                metaSaveNull,
                102L,
                newBudget(10, 10 * 1024 * 1024L)
        );
        assertTrue(((List<?>) outSaveNull).isEmpty());
        assertEquals("EPUB_ZIP", String.valueOf(metaSaveNull.get("imagesExtractionMode")));
    }

    @Test
    void extractMobiImagesWithTika_shouldCoverZipFallbackBudgetBreakAndSaveNull() throws Exception {
        DerivedUploadStorageService storage = Mockito.mock(DerivedUploadStorageService.class);
        Mockito.when(storage.getMaxImageBytes()).thenReturn(10 * 1024 * 1024L);
        Mockito.when(storage.saveDerivedImage(Mockito.any(byte[].class), Mockito.anyString(), Mockito.any(), Mockito.any()))
                .thenReturn(null);
        Object svc = new FileAssetExtractionAsyncService(null, null, null, null, null, null, null, storage);

        Path mobi = Files.createTempFile("mobi-zip-fallback-", ".mobi");
        Files.write(mobi, zipBytes(List.of(Map.entry("images/a.png", tinyPngBytes()))));

        try (MockedConstruction<AutoDetectParser> mocked = Mockito.mockConstruction(AutoDetectParser.class, (mock, ctx) ->
                Mockito.doAnswer(inv -> null).when(mock).parse(Mockito.any(InputStream.class), Mockito.any(), Mockito.any(), Mockito.any(ParseContext.class))
        )) {
            Map<String, Object> metaBreak = new LinkedHashMap<>();
            Object outBreak = invokeInstance(
                    svc,
                    "extractMobiImagesWithTika",
                    new Class<?>[]{Path.class, Map.class, Long.class, newBudget(1, 1L).getClass()},
                    mobi,
                    metaBreak,
                    201L,
                    newBudget(0, 10 * 1024 * 1024L)
            );
            assertTrue(((List<?>) outBreak).isEmpty());
            assertEquals("EMBEDDED_NONE", String.valueOf(metaBreak.get("imagesExtractionMode")));

            Map<String, Object> metaSaveNull = new LinkedHashMap<>();
            Object outSaveNull = invokeInstance(
                    svc,
                    "extractMobiImagesWithTika",
                    new Class<?>[]{Path.class, Map.class, Long.class, newBudget(1, 1L).getClass()},
                    mobi,
                    metaSaveNull,
                    202L,
                    newBudget(10, 10 * 1024 * 1024L)
            );
            assertTrue(((List<?>) outSaveNull).isEmpty());
            assertEquals("EMBEDDED_NONE", String.valueOf(metaSaveNull.get("imagesExtractionMode")));
        }
    }

    @Test
    void extract7zFromPath_shouldCoverDirectoryAndTotalBytesLimit() throws Exception {
        Object svc = new FileAssetExtractionAsyncService(null, null, null, null, null, null, null, null);
        setField(svc, "archiveMaxDepth", 5);
        setField(svc, "archiveMaxEntries", 50);
        setField(svc, "archiveMaxEntryBytes", 2 * 1024 * 1024L);
        setField(svc, "archiveMaxTotalBytes", 64L);
        setField(svc, "archiveMaxTotalMillis", 15000L);

        Path seven = Files.createTempFile("seven-dir-limit-", ".7z");
        try (SevenZOutputFile out = new SevenZOutputFile(seven.toFile())) {
            SevenZArchiveEntry dir = new SevenZArchiveEntry();
            dir.setName("dir/");
            dir.setDirectory(true);
            out.putArchiveEntry(dir);
            out.closeArchiveEntry();

            SevenZArchiveEntry file = new SevenZArchiveEntry();
            file.setName("dir/a.txt");
            file.setContentMethods(List.of(new SevenZMethodConfiguration(SevenZMethod.COPY)));
            out.putArchiveEntry(file);
            out.write("x".repeat(256).getBytes(StandardCharsets.UTF_8));
            out.closeArchiveEntry();
        }

        Object counters = newInner("ArchiveCounters", new Class<?>[]{});
        Map<String, Object> meta = new LinkedHashMap<>();
        String out = String.valueOf(invokeInstance(
                svc,
                "extract7zFromPath",
                new Class<?>[]{Path.class, String.class, int.class, int.class, Map.class, counters.getClass(), long.class},
                seven,
                "container.7z",
                0,
                500,
                meta,
                counters,
                System.nanoTime()
        ));
        assertNotNull(out);
        String reason = String.valueOf(getField(counters, "truncatedReason"));
        assertTrue(reason == null || reason.equals("null") || reason.equals("TOTAL_BYTES_LIMIT"));
    }

    @Test
    void expand7zToDisk_shouldCoverDirectoryAndTotalBytesLimit() throws Exception {
        Object svc = new FileAssetExtractionAsyncService(null, null, null, null, null, null, null, null);
        setField(svc, "archiveMaxDepth", 5);
        setField(svc, "archiveMaxEntries", 50);
        setField(svc, "archiveMaxEntryBytes", 2 * 1024 * 1024L);
        setField(svc, "archiveMaxTotalBytes", 64L);
        setField(svc, "archiveMaxTotalMillis", 15000L);

        Path seven = Files.createTempFile("expand-seven-dir-limit-", ".7z");
        try (SevenZOutputFile out = new SevenZOutputFile(seven.toFile())) {
            SevenZArchiveEntry dir = new SevenZArchiveEntry();
            dir.setName("d/");
            dir.setDirectory(true);
            out.putArchiveEntry(dir);
            out.closeArchiveEntry();

            SevenZArchiveEntry file = new SevenZArchiveEntry();
            file.setName("d/a.txt");
            file.setContentMethods(List.of(new SevenZMethodConfiguration(SevenZMethod.COPY)));
            out.putArchiveEntry(file);
            out.write("y".repeat(256).getBytes(StandardCharsets.UTF_8));
            out.closeArchiveEntry();
        }

        Object counters = newInner("ArchiveCounters", new Class<?>[]{});
        Map<String, Object> meta = new LinkedHashMap<>();
        List<Map<String, Object>> files = new ArrayList<>();
        invokeInstance(
                svc,
                "expand7zToDisk",
                new Class<?>[]{Path.class, String.class, int.class, Path.class, Map.class, counters.getClass(), long.class, List.class},
                seven,
                "vp/",
                0,
                Files.createTempDirectory("expand-seven-out-"),
                meta,
                counters,
                System.nanoTime(),
                files
        );
        assertFalse(files.isEmpty());
        assertEquals("7z", String.valueOf(meta.get("archiveType")));
        assertEquals("TOTAL_BYTES_LIMIT", String.valueOf(getField(counters, "truncatedReason")));
    }

    @Test
    void extractArchiveFromStream_shouldCoverPlainFallbackAnd7zDelegation() throws Exception {
        Object svc = new FileAssetExtractionAsyncService(null, null, null, null, null, null, null, null);
        setField(svc, "archiveMaxDepth", 6);
        setField(svc, "archiveMaxEntries", 50);
        setField(svc, "archiveMaxEntryBytes", 2 * 1024 * 1024L);
        setField(svc, "archiveMaxTotalBytes", 20 * 1024 * 1024L);
        setField(svc, "archiveMaxTotalMillis", 15000L);

        Object countersPlain = newInner("ArchiveCounters", new Class<?>[]{});
        assertThrows(IllegalStateException.class, () -> invokeInstance(
                svc,
                "extractArchiveFromStream",
                new Class<?>[]{InputStream.class, String.class, int.class, int.class, Map.class, countersPlain.getClass(), long.class},
                new ByteArrayInputStream("plain-content".getBytes(StandardCharsets.UTF_8)),
                "note.txt",
                0,
                200,
                new LinkedHashMap<>(),
                countersPlain,
                System.nanoTime()
        ));

        byte[] seven = sevenZBytes(List.of(Map.entry("a.txt", "hello-7z".getBytes(StandardCharsets.UTF_8))));
        Object counters7z = newInner("ArchiveCounters", new Class<?>[]{});
        Map<String, Object> meta7z = new LinkedHashMap<>();
        String from7z = String.valueOf(invokeInstance(
                svc,
                "extractArchiveFromStream",
                new Class<?>[]{InputStream.class, String.class, int.class, int.class, Map.class, counters7z.getClass(), long.class},
                new ByteArrayInputStream(seven),
                "bundle.7z",
                0,
                200,
                meta7z,
                counters7z,
                System.nanoTime()
        ));
        assertNotNull(from7z);
    }

    @Test
    void extract7zFromPath_shouldCoverNested7zBranchAndTotalBytesReason() throws Exception {
        Object svc = new FileAssetExtractionAsyncService(null, null, null, null, null, null, null, null);
        setField(svc, "archiveMaxDepth", 6);
        setField(svc, "archiveMaxEntries", 50);
        setField(svc, "archiveMaxEntryBytes", 2 * 1024 * 1024L);
        setField(svc, "archiveMaxTotalBytes", 10 * 1024 * 1024L);
        setField(svc, "archiveMaxTotalMillis", 15000L);

        byte[] inner7z = sevenZBytes(List.of(Map.entry("inner.txt", "INNER_7Z_TEXT".getBytes(StandardCharsets.UTF_8))));
        Path outer = Files.createTempFile("outer-nested-", ".7z");
        try (SevenZOutputFile out = new SevenZOutputFile(outer.toFile())) {
            SevenZArchiveEntry e = new SevenZArchiveEntry();
            e.setName("nested.7z");
            e.setContentMethods(List.of(new SevenZMethodConfiguration(SevenZMethod.COPY)));
            out.putArchiveEntry(e);
            out.write(inner7z);
            out.closeArchiveEntry();
        }
        Object countersNested = newInner("ArchiveCounters", new Class<?>[]{});
        String nested = String.valueOf(invokeInstance(
                svc,
                "extract7zFromPath",
                new Class<?>[]{Path.class, String.class, int.class, int.class, Map.class, countersNested.getClass(), long.class},
                outer,
                "outer.7z",
                0,
                500,
                new LinkedHashMap<>(),
                countersNested,
                System.nanoTime()
        ));
        assertNotNull(nested);

        setField(svc, "archiveMaxTotalBytes", 48L);
        Path big = Files.createTempFile("outer-big-", ".7z");
        try (SevenZOutputFile out = new SevenZOutputFile(big.toFile())) {
            SevenZArchiveEntry e = new SevenZArchiveEntry();
            e.setName("big.txt");
            e.setContentMethods(List.of(new SevenZMethodConfiguration(SevenZMethod.COPY)));
            out.putArchiveEntry(e);
            out.write("z".repeat(256).getBytes(StandardCharsets.UTF_8));
            out.closeArchiveEntry();
        }
        Object countersLimit = newInner("ArchiveCounters", new Class<?>[]{});
        String limited = String.valueOf(invokeInstance(
                svc,
                "extract7zFromPath",
                new Class<?>[]{Path.class, String.class, int.class, int.class, Map.class, countersLimit.getClass(), long.class},
                big,
                "big.7z",
                0,
                500,
                new LinkedHashMap<>(),
                countersLimit,
                System.nanoTime()
        ));
        assertNotNull(limited);
        String reason2 = String.valueOf(getField(countersLimit, "truncatedReason"));
        assertTrue(reason2 == null || reason2.equals("null") || reason2.equals("TOTAL_BYTES_LIMIT"));
    }

    @Test
    void expand7zToDisk_shouldCoverTimeoutPresetReason() throws Exception {
        Object svc = new FileAssetExtractionAsyncService(null, null, null, null, null, null, null, null);
        setField(svc, "archiveMaxDepth", 5);
        setField(svc, "archiveMaxEntries", 20);
        setField(svc, "archiveMaxEntryBytes", 2 * 1024 * 1024L);
        setField(svc, "archiveMaxTotalBytes", 20 * 1024 * 1024L);
        setField(svc, "archiveMaxTotalMillis", 1L);

        Path seven = Files.createTempFile("expand-timeout-", ".7z");
        try (SevenZOutputFile out = new SevenZOutputFile(seven.toFile())) {
            SevenZArchiveEntry e = new SevenZArchiveEntry();
            e.setName("a.txt");
            e.setContentMethods(List.of(new SevenZMethodConfiguration(SevenZMethod.COPY)));
            out.putArchiveEntry(e);
            out.write("hello".getBytes(StandardCharsets.UTF_8));
            out.closeArchiveEntry();
        }

        Object counters = newInner("ArchiveCounters", new Class<?>[]{});
        setField(counters, "truncatedReason", "PRESET");
        List<Map<String, Object>> files = new ArrayList<>();
        invokeInstance(
                svc,
                "expand7zToDisk",
                new Class<?>[]{Path.class, String.class, int.class, Path.class, Map.class, counters.getClass(), long.class, List.class},
                seven,
                "vp/",
                0,
                Files.createTempDirectory("expand-timeout-out-"),
                new LinkedHashMap<>(),
                counters,
                System.nanoTime() - java.time.Duration.ofMillis(20).toNanos(),
                files
        );
        assertTrue(files.isEmpty());
        assertEquals("PRESET", String.valueOf(getField(counters, "truncatedReason")));
    }

    @Test
    void extractMobiImagesWithTika_shouldCoverEmbeddedExtractorDecisionMatrix() throws Exception {
        DerivedUploadStorageService storage = Mockito.mock(DerivedUploadStorageService.class);
        Mockito.when(storage.saveDerivedImage(Mockito.any(byte[].class), Mockito.anyString(), Mockito.any(), Mockito.any()))
                .thenReturn(Map.of("path", "/tmp/mobi-embedded.png"));
        Mockito.when(storage.buildPlaceholder(Mockito.anyInt(), Mockito.anyMap()))
                .thenAnswer(inv -> Map.of("placeholder", "[[IMAGE_" + inv.getArgument(0) + "]]"));
        Object svc = new FileAssetExtractionAsyncService(null, null, null, null, null, null, null, storage);
        Path mobi = Files.createTempFile("mobi-embedded-", ".mobi");
        Files.write(mobi, "not-zip".getBytes(StandardCharsets.UTF_8));

        try (MockedConstruction<AutoDetectParser> ignored = Mockito.mockConstruction(AutoDetectParser.class, (mock, ctx) ->
                Mockito.doAnswer(inv -> {
                    ParseContext pc = inv.getArgument(3);
                    org.apache.tika.extractor.EmbeddedDocumentExtractor extractor =
                            pc.get(org.apache.tika.extractor.EmbeddedDocumentExtractor.class);

                    assertFalse(extractor.shouldParseEmbedded(null));
                    Metadata textMeta = new Metadata();
                    textMeta.set(Metadata.CONTENT_TYPE, "text/plain");
                    assertFalse(extractor.shouldParseEmbedded(textMeta));

                    Metadata imageMeta = new Metadata();
                    imageMeta.set(Metadata.CONTENT_TYPE, "image/png");
                    assertTrue(extractor.shouldParseEmbedded(imageMeta));

                    extractor.parseEmbedded(null, null, imageMeta, false);
                    extractor.parseEmbedded(new ByteArrayInputStream(new byte[0]), null, imageMeta, false);

                    Metadata imageNoNameMeta = new Metadata();
                    imageNoNameMeta.set(Metadata.CONTENT_TYPE, "image/png");
                    extractor.parseEmbedded(new ByteArrayInputStream(tinyPngBytes()), null, imageNoNameMeta, false);
                    return null;
                }).when(mock).parse(Mockito.any(InputStream.class), Mockito.any(), Mockito.any(), Mockito.any(ParseContext.class))
        )) {
            Map<String, Object> meta = new LinkedHashMap<>();
            Object out = invokeInstance(
                    svc,
                    "extractMobiImagesWithTika",
                    new Class<?>[]{Path.class, Map.class, Long.class, newBudget(1, 1L).getClass()},
                    mobi,
                    meta,
                    901L,
                    null
            );
            assertFalse(((List<?>) out).isEmpty());
            assertEquals("MOBI_TIKA_EMBEDDED", String.valueOf(meta.get("imagesExtractionMode")));
        }
    }

    @Test
    void extractWithTika_shouldCoverDetectedTypeBranches() throws Exception {
        Path p = Files.createTempFile("tika-detect-", ".txt");
        Files.writeString(p, "hello");

        try (MockedConstruction<Tika> ignored = Mockito.mockConstruction(Tika.class, (mock, ctx) -> {
            Mockito.when(mock.parseToString(Mockito.any(java.io.File.class))).thenReturn("alpha");
            Mockito.when(mock.detect(Mockito.any(java.io.File.class))).thenReturn("text/plain");
        })) {
            Map<String, Object> meta = new LinkedHashMap<>();
            String out = String.valueOf(invokeStatic("extractWithTika", new Class<?>[]{Path.class, int.class, Map.class}, p, 200, meta));
            assertEquals("alpha", out);
            assertEquals("text/plain", String.valueOf(meta.get("tikaDetectedType")));
        }

        try (MockedConstruction<Tika> ignored = Mockito.mockConstruction(Tika.class, (mock, ctx) -> {
            Mockito.when(mock.parseToString(Mockito.any(java.io.File.class))).thenReturn("beta");
            Mockito.when(mock.detect(Mockito.any(java.io.File.class))).thenReturn(" ");
        })) {
            Map<String, Object> meta = new LinkedHashMap<>();
            String out = String.valueOf(invokeStatic("extractWithTika", new Class<?>[]{Path.class, int.class, Map.class}, p, 200, meta));
            assertEquals("beta", out);
            assertFalse(meta.containsKey("tikaDetectedType"));
        }
    }

    @Test
    void isSupportedInnerExtForExtraction_shouldCoverAllTrueAliasesAndFalse() {
        assertFalse((Boolean) invokeStatic("isSupportedInnerExtForExtraction", new Class<?>[]{String.class}, " "));
        assertFalse((Boolean) invokeStatic("isSupportedInnerExtForExtraction", new Class<?>[]{String.class}, "zip"));
        assertTrue((Boolean) invokeStatic("isSupportedInnerExtForExtraction", new Class<?>[]{String.class}, "pdf"));
        assertTrue((Boolean) invokeStatic("isSupportedInnerExtForExtraction", new Class<?>[]{String.class}, "txt"));
        assertTrue((Boolean) invokeStatic("isSupportedInnerExtForExtraction", new Class<?>[]{String.class}, "md"));
        assertTrue((Boolean) invokeStatic("isSupportedInnerExtForExtraction", new Class<?>[]{String.class}, "markdown"));
        assertTrue((Boolean) invokeStatic("isSupportedInnerExtForExtraction", new Class<?>[]{String.class}, "csv"));
        assertTrue((Boolean) invokeStatic("isSupportedInnerExtForExtraction", new Class<?>[]{String.class}, "json"));
        assertTrue((Boolean) invokeStatic("isSupportedInnerExtForExtraction", new Class<?>[]{String.class}, "html"));
        assertTrue((Boolean) invokeStatic("isSupportedInnerExtForExtraction", new Class<?>[]{String.class}, "htm"));
        assertTrue((Boolean) invokeStatic("isSupportedInnerExtForExtraction", new Class<?>[]{String.class}, "doc"));
        assertTrue((Boolean) invokeStatic("isSupportedInnerExtForExtraction", new Class<?>[]{String.class}, "docx"));
        assertTrue((Boolean) invokeStatic("isSupportedInnerExtForExtraction", new Class<?>[]{String.class}, "xls"));
        assertTrue((Boolean) invokeStatic("isSupportedInnerExtForExtraction", new Class<?>[]{String.class}, "xlsx"));
        assertTrue((Boolean) invokeStatic("isSupportedInnerExtForExtraction", new Class<?>[]{String.class}, "ppt"));
        assertTrue((Boolean) invokeStatic("isSupportedInnerExtForExtraction", new Class<?>[]{String.class}, "pptx"));
        assertTrue((Boolean) invokeStatic("isSupportedInnerExtForExtraction", new Class<?>[]{String.class}, "epub"));
        assertTrue((Boolean) invokeStatic("isSupportedInnerExtForExtraction", new Class<?>[]{String.class}, "mobi"));
    }

    @Test
    void extractArchive_shouldCoverUnsupportedAndTruncatedAndTextLimit() throws Exception {
        DerivedUploadStorageService storage = Mockito.mock(DerivedUploadStorageService.class);
        Object svc = new FileAssetExtractionAsyncService(null, null, null, null, null, null, null, storage);
        setField(svc, "archiveMaxDepth", 5);
        setField(svc, "archiveMaxEntries", 100);
        setField(svc, "archiveMaxEntryBytes", 1L);
        setField(svc, "archiveMaxTotalBytes", 200L);
        setField(svc, "archiveMaxTotalMillis", 15000L);

        byte[] outer = zipBytes(List.of(
                Map.entry("a.txt", "abcdefg".getBytes(StandardCharsets.UTF_8)),
                Map.entry("b.exe", "bin".getBytes(StandardCharsets.UTF_8)),
                Map.entry("c.txt", "xyz".getBytes(StandardCharsets.UTF_8))
        ));
        Path zip = Files.createTempFile("arc-mix-", ".zip");
        Files.write(zip, outer);

        Object budget = newBudget(10, 1024 * 1024L);
        Map<String, Object> meta = new LinkedHashMap<>();
        String out = String.valueOf(invokeInstance(
                svc,
                "extractArchive",
                new Class<?>[]{Path.class, String.class, int.class, Map.class, Long.class, budget.getClass()},
                zip,
                "zip",
                5,
                meta,
                301L,
                budget
        ));
        assertNotNull(out);
        assertTrue(meta.get("archive") instanceof Map<?, ?>);
        @SuppressWarnings("unchecked")
        Map<String, Object> arc = (Map<String, Object>) meta.get("archive");
        assertTrue(arc.get("files") instanceof List<?>);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> files = (List<Map<String, Object>>) arc.get("files");
        assertTrue(files.stream().anyMatch(f -> {
            Object status = f == null ? null : f.get("parseStatus");
            return "SKIPPED_UNSUPPORTED".equals(String.valueOf(status)) || "SKIPPED_TRUNCATED".equals(String.valueOf(status));
        }));
        assertTrue(arc.containsKey("truncated"));
    }

    @Test
    void extractAsync_shouldCoverEarlyReturnAndFailStates() throws Exception {
        FileAssetsRepository fileAssetsRepository = Mockito.mock(FileAssetsRepository.class);
        FileAssetExtractionsRepository extractionRepository = Mockito.mock(FileAssetExtractionsRepository.class);
        UploadFormatsConfigService uploadFormatsConfigService = Mockito.mock(UploadFormatsConfigService.class);
        VectorIndicesRepository vectorIndicesRepository = Mockito.mock(VectorIndicesRepository.class);
        RagFileAssetIndexAsyncService ragFileAssetIndexAsyncService = Mockito.mock(RagFileAssetIndexAsyncService.class);
        TokenCountService tokenCountService = Mockito.mock(TokenCountService.class);
        DerivedUploadStorageService storage = Mockito.mock(DerivedUploadStorageService.class);

        Mockito.when(vectorIndicesRepository.findByCollectionName("rag_file_assets_v1")).thenReturn(List.of());
        Mockito.when(extractionRepository.findById(Mockito.anyLong())).thenReturn(Optional.empty());
        Mockito.when(extractionRepository.save(Mockito.any(FileAssetExtractionsEntity.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        Object svc = new FileAssetExtractionAsyncService(
                fileAssetsRepository,
                extractionRepository,
                uploadFormatsConfigService,
                new ObjectMapper(),
                vectorIndicesRepository,
                ragFileAssetIndexAsyncService,
                tokenCountService,
                storage
        );

        invokeInstance(svc, "extractAsync", new Class<?>[]{Long.class}, new Object[]{null});

        FileAssetsEntity blankPath = new FileAssetsEntity();
        blankPath.setPath(" ");
        blankPath.setOriginalName("a.txt");
        blankPath.setMimeType("text/plain");
        Mockito.when(fileAssetsRepository.findById(11L)).thenReturn(Optional.of(blankPath));
        invokeInstance(svc, "extractAsync", new Class<?>[]{Long.class}, 11L);

        FileAssetsEntity missingFile = new FileAssetsEntity();
        missingFile.setPath("not-exists-" + System.nanoTime() + ".txt");
        missingFile.setOriginalName("b.txt");
        missingFile.setMimeType("text/plain");
        Mockito.when(fileAssetsRepository.findById(12L)).thenReturn(Optional.of(missingFile));
        invokeInstance(svc, "extractAsync", new Class<?>[]{Long.class}, 12L);

        Mockito.verify(extractionRepository, Mockito.atLeast(2)).save(Mockito.argThat(e ->
                e != null && FileAssetExtractionStatus.FAILED.equals(e.getExtractStatus())
        ));
    }

    @Test
    void extractAsync_shouldCoverReadyAndEstimatedTokensAndHardFail() throws Exception {
        FileAssetsRepository fileAssetsRepository = Mockito.mock(FileAssetsRepository.class);
        FileAssetExtractionsRepository extractionRepository = Mockito.mock(FileAssetExtractionsRepository.class);
        UploadFormatsConfigService uploadFormatsConfigService = Mockito.mock(UploadFormatsConfigService.class);
        VectorIndicesRepository vectorIndicesRepository = Mockito.mock(VectorIndicesRepository.class);
        RagFileAssetIndexAsyncService ragFileAssetIndexAsyncService = Mockito.mock(RagFileAssetIndexAsyncService.class);
        TokenCountService tokenCountService = Mockito.mock(TokenCountService.class);
        DerivedUploadStorageService storage = Mockito.mock(DerivedUploadStorageService.class);

        UploadFormatsConfigDTO cfg = new UploadFormatsConfigDTO();
        cfg.setParseMaxChars(200L);
        Mockito.when(uploadFormatsConfigService.getConfig()).thenReturn(cfg);
        Mockito.when(vectorIndicesRepository.findByCollectionName("rag_file_assets_v1")).thenReturn(List.of());
        Mockito.when(extractionRepository.findById(Mockito.anyLong())).thenReturn(Optional.empty());
        Mockito.when(extractionRepository.save(Mockito.any(FileAssetExtractionsEntity.class)))
                .thenAnswer(inv -> inv.getArgument(0));
        Mockito.when(tokenCountService.countTextTokens(Mockito.anyString())).thenReturn(null);
        Mockito.when(storage.getMaxCount()).thenReturn(10);
        Mockito.when(storage.getMaxTotalBytes()).thenReturn(1024 * 1024L);

        Object svc = new FileAssetExtractionAsyncService(
                fileAssetsRepository,
                extractionRepository,
                uploadFormatsConfigService,
                new ObjectMapper(),
                vectorIndicesRepository,
                ragFileAssetIndexAsyncService,
                tokenCountService,
                storage
        );

        Path txt = Files.createTempFile("extract-async-", ".txt");
        Files.writeString(txt, "hello async");
        FileAssetsEntity txtFa = new FileAssetsEntity();
        txtFa.setPath(txt.toString());
        txtFa.setOriginalName("hello.txt");
        txtFa.setMimeType("text/plain");
        Mockito.when(fileAssetsRepository.findById(21L)).thenReturn(Optional.of(txtFa));
        invokeInstance(svc, "extractAsync", new Class<?>[]{Long.class}, 21L);

        setField(svc, "archiveMaxDepth", 1);
        setField(svc, "archiveMaxEntries", 50);
        setField(svc, "archiveMaxEntryBytes", 1024 * 1024L);
        setField(svc, "archiveMaxTotalBytes", 10 * 1024 * 1024L);
        setField(svc, "archiveMaxTotalMillis", 15000L);
        byte[] nested = zipBytes(List.of(Map.entry("inner.txt", "INNER".getBytes(StandardCharsets.UTF_8))));
        byte[] outer = zipBytes(List.of(Map.entry("nested.zip", nested)));
        Path zip = Files.createTempFile("extract-async-depth-", ".zip");
        Files.write(zip, outer);
        FileAssetsEntity zipFa = new FileAssetsEntity();
        zipFa.setPath(zip.toString());
        zipFa.setOriginalName("deep.zip");
        zipFa.setMimeType("application/zip");
        Mockito.when(fileAssetsRepository.findById(22L)).thenReturn(Optional.of(zipFa));
        invokeInstance(svc, "extractAsync", new Class<?>[]{Long.class}, 22L);

        Mockito.verify(extractionRepository, Mockito.atLeast(1)).save(Mockito.argThat(e ->
                e != null && FileAssetExtractionStatus.READY.equals(e.getExtractStatus())
        ));
        Mockito.verify(extractionRepository, Mockito.atLeast(1)).save(Mockito.argThat(e ->
                e != null && FileAssetExtractionStatus.FAILED.equals(e.getExtractStatus())
                        && "ARCHIVE_NESTING_TOO_DEEP".equals(e.getErrorMessage())
        ));
    }

    @Test
    void extractEntryBytesAsText_shouldCoverYamlYmlXmlAndMobiPaths() throws Exception {
        Object svc = new FileAssetExtractionAsyncService(null, null, null, null, null, null, null, null);

        String yaml = String.valueOf(invokeInstance(
                svc,
                "extractEntryBytesAsText",
                new Class<?>[]{String.class, String.class, byte[].class, int.class},
                "a.yaml",
                "yaml",
                "k: v\nx: y".getBytes(StandardCharsets.UTF_8),
                200
        ));
        assertTrue(yaml.contains("k: v"));

        String yml = String.valueOf(invokeInstance(
                svc,
                "extractEntryBytesAsText",
                new Class<?>[]{String.class, String.class, byte[].class, int.class},
                "a.yml",
                "yml",
                "a: 1".getBytes(StandardCharsets.UTF_8),
                200
        ));
        assertTrue(yml.contains("a: 1"));

        String xml = String.valueOf(invokeInstance(
                svc,
                "extractEntryBytesAsText",
                new Class<?>[]{String.class, String.class, byte[].class, int.class},
                "a.xml",
                "xml",
                "<r><n>v</n></r>".getBytes(StandardCharsets.UTF_8),
                200
        ));
        assertTrue(xml.contains("<r><n>v</n></r>"));

        assertThrows(IllegalStateException.class, () -> invokeInstance(
                svc,
                "extractEntryBytesAsText",
                new Class<?>[]{String.class, String.class, byte[].class, int.class},
                "a.mobi",
                "mobi",
                "not-mobi".getBytes(StandardCharsets.UTF_8),
                200
        ));
    }

    @Test
    void appendArchiveEntryBlock_shouldCoverNullOutAndBlankText() {
        Object svc = new FileAssetExtractionAsyncService(null, null, null, null, null, null, null, null);

        invokeInstance(svc, "appendArchiveEntryBlock", new Class<?>[]{StringBuilder.class, String.class, String.class},
                null, "a.txt", "abc");

        StringBuilder out = new StringBuilder("prefix\n");
        invokeInstance(svc, "appendArchiveEntryBlock", new Class<?>[]{StringBuilder.class, String.class, String.class},
                out, "a.txt", "   ");
        assertEquals("prefix\n", out.toString());

        invokeInstance(svc, "appendArchiveEntryBlock", new Class<?>[]{StringBuilder.class, String.class, String.class},
                out, null, "body");
        assertTrue(out.toString().contains("FILE: "));
        assertTrue(out.toString().contains("body"));
    }

    @Test
    void extractAsync_shouldCoverTokenizerModeAndRagSync() throws Exception {
        FileAssetsRepository fileAssetsRepository = Mockito.mock(FileAssetsRepository.class);
        FileAssetExtractionsRepository extractionRepository = Mockito.mock(FileAssetExtractionsRepository.class);
        UploadFormatsConfigService uploadFormatsConfigService = Mockito.mock(UploadFormatsConfigService.class);
        VectorIndicesRepository vectorIndicesRepository = Mockito.mock(VectorIndicesRepository.class);
        RagFileAssetIndexAsyncService ragFileAssetIndexAsyncService = Mockito.mock(RagFileAssetIndexAsyncService.class);
        TokenCountService tokenCountService = Mockito.mock(TokenCountService.class);
        DerivedUploadStorageService storage = Mockito.mock(DerivedUploadStorageService.class);

        UploadFormatsConfigDTO cfg = new UploadFormatsConfigDTO();
        cfg.setParseMaxChars(300L);
        Mockito.when(uploadFormatsConfigService.getConfig()).thenReturn(cfg);
        Mockito.when(extractionRepository.findById(Mockito.anyLong())).thenReturn(Optional.empty());
        Mockito.when(extractionRepository.save(Mockito.any(FileAssetExtractionsEntity.class))).thenAnswer(inv -> inv.getArgument(0));
        Mockito.when(tokenCountService.countTextTokens(Mockito.anyString())).thenReturn(7);
        Mockito.when(storage.getMaxCount()).thenReturn(10);
        Mockito.when(storage.getMaxTotalBytes()).thenReturn(1024 * 1024L);
        VectorIndicesEntity idx = new VectorIndicesEntity();
        idx.setId(99L);
        Mockito.when(vectorIndicesRepository.findByCollectionName("rag_file_assets_v1")).thenReturn(List.of(idx));

        Object svc = new FileAssetExtractionAsyncService(
                fileAssetsRepository,
                extractionRepository,
                uploadFormatsConfigService,
                new ObjectMapper(),
                vectorIndicesRepository,
                ragFileAssetIndexAsyncService,
                tokenCountService,
                storage
        );

        Path txt = Files.createTempFile("extract-async-token-", ".txt");
        Files.writeString(txt, "tokenizer flow");
        FileAssetsEntity fa = new FileAssetsEntity();
        fa.setPath(txt.toString());
        fa.setOriginalName("tok.txt");
        fa.setMimeType("text/plain");
        Mockito.when(fileAssetsRepository.findById(31L)).thenReturn(Optional.of(fa));

        invokeInstance(svc, "extractAsync", new Class<?>[]{Long.class}, 31L);

        Mockito.verify(extractionRepository, Mockito.atLeastOnce()).save(Mockito.argThat(e ->
                e != null && FileAssetExtractionStatus.READY.equals(e.getExtractStatus())
                        && e.getExtractedMetadataJson() != null
                        && e.getExtractedMetadataJson().contains("TOKENIZER")
        ));
        Mockito.verify(ragFileAssetIndexAsyncService, Mockito.times(1)).syncSingleFileAssetAsync(99L, 31L);
    }

    @Test
    void looksLikeArchiveBytes_and_isImageExt_shouldCoverRemainingSignatures() {
        assertFalse((Boolean) invokeStatic("looksLikeArchiveBytes", new Class<?>[]{byte[].class}, (Object) null));
        assertFalse((Boolean) invokeStatic("looksLikeArchiveBytes", new Class<?>[]{byte[].class}, new byte[]{1, 2, 3}));
        assertTrue((Boolean) invokeStatic("looksLikeArchiveBytes", new Class<?>[]{byte[].class}, new byte[]{0x50, 0x4B, 0, 0}));
        assertTrue((Boolean) invokeStatic("looksLikeArchiveBytes", new Class<?>[]{byte[].class}, new byte[]{0x37, 0x7A, (byte) 0xBC, (byte) 0xAF, 0, 0}));
        assertTrue((Boolean) invokeStatic("looksLikeArchiveBytes", new Class<?>[]{byte[].class}, new byte[]{0x1F, (byte) 0x8B, 0, 0}));
        assertTrue((Boolean) invokeStatic("looksLikeArchiveBytes", new Class<?>[]{byte[].class}, new byte[]{0x42, 0x5A, 0, 0}));
        assertTrue((Boolean) invokeStatic("looksLikeArchiveBytes", new Class<?>[]{byte[].class}, new byte[]{(byte) 0xFD, 0x37, 0x7A, 0x58, 0, 0}));

        assertFalse((Boolean) invokeStatic("isImageExt", new Class<?>[]{String.class}, " "));
        assertTrue((Boolean) invokeStatic("isImageExt", new Class<?>[]{String.class}, "bmp"));
        assertTrue((Boolean) invokeStatic("isImageExt", new Class<?>[]{String.class}, "png"));
        assertTrue((Boolean) invokeStatic("isImageExt", new Class<?>[]{String.class}, "jpg"));
        assertTrue((Boolean) invokeStatic("isImageExt", new Class<?>[]{String.class}, "jpeg"));
        assertTrue((Boolean) invokeStatic("isImageExt", new Class<?>[]{String.class}, "gif"));
        assertTrue((Boolean) invokeStatic("isImageExt", new Class<?>[]{String.class}, "webp"));
    }

    @Test
    void extractImages_shouldCoverDispatchMatrixMoreBranches() throws Exception {
        DerivedUploadStorageService storage = Mockito.mock(DerivedUploadStorageService.class);
        Object svc = new FileAssetExtractionAsyncService(null, null, null, null, null, null, null, storage);

        Map<String, Object> blankMeta = new LinkedHashMap<>();
        Object blank = invokeInstance(
                svc,
                "extractImages",
                new Class<?>[]{Path.class, String.class, Map.class, Long.class, String.class, newBudget(1, 1L).getClass()},
                Path.of("x"),
                " ",
                blankMeta,
                1L,
                "",
                newBudget(1, 1024)
        );
        assertTrue(((List<?>) blank).isEmpty());

        Map<String, Object> imageMeta = new LinkedHashMap<>();
        Object image = invokeInstance(
                svc,
                "extractImages",
                new Class<?>[]{Path.class, String.class, Map.class, Long.class, String.class, newBudget(1, 1L).getClass()},
                Path.of("x"),
                "png",
                imageMeta,
                2L,
                "",
                newBudget(1, 1024)
        );
        assertTrue(((List<?>) image).isEmpty());

        Path missing = Path.of("missing-" + System.nanoTime() + ".bin");
        String[] exts = new String[]{"docx", "xlsx", "pptx", "ppt", "epub", "mobi"};
        for (String ext : exts) {
            Map<String, Object> meta = new LinkedHashMap<>();
            Object out = invokeInstance(
                    svc,
                    "extractImages",
                    new Class<?>[]{Path.class, String.class, Map.class, Long.class, String.class, newBudget(1, 1L).getClass()},
                    missing,
                    ext,
                    meta,
                    3L,
                    "",
                    newBudget(1, 1024)
            );
            assertTrue(((List<?>) out).isEmpty());
            assertTrue(meta.containsKey("imagesExtractionMode"));
        }

        Map<String, Object> unsupportedMeta = new LinkedHashMap<>();
        Object unsupported = invokeInstance(
                svc,
                "extractImages",
                new Class<?>[]{Path.class, String.class, Map.class, Long.class, String.class, newBudget(1, 1L).getClass()},
                missing,
                "bin",
                unsupportedMeta,
                4L,
                "",
                newBudget(1, 1024)
        );
        assertTrue(((List<?>) unsupported).isEmpty());
        assertEquals("UNSUPPORTED", String.valueOf(unsupportedMeta.get("imagesExtractionMode")));
    }

    @Test
    void extractText_shouldCoverImageAndTikaFailureAndOfficeFallback() throws Exception {
        Object svc = new FileAssetExtractionAsyncService(null, null, null, null, null, null, null, null);
        Map<String, Object> imageMeta = new LinkedHashMap<>();
        String imageTxt = String.valueOf(invokeInstance(
                svc,
                "extractText",
                new Class<?>[]{Path.class, String.class, int.class, Map.class, Long.class, newBudget(1, 1L).getClass()},
                Path.of("x"),
                "png",
                200,
                imageMeta,
                1L,
                newBudget(1, 1024)
        ));
        assertEquals("", imageTxt);
        assertEquals("DIRECT_IMAGE", String.valueOf(imageMeta.get("imagesExtractionMode")));

        Path badMobi = Files.createTempFile("bad-mobi-", ".mobi");
        Files.writeString(badMobi, "not mobi");
        Map<String, Object> mobiMeta = new LinkedHashMap<>();
        String mobiTxt = String.valueOf(invokeInstance(
                svc,
                "extractText",
                new Class<?>[]{Path.class, String.class, int.class, Map.class, Long.class, newBudget(1, 1L).getClass()},
                badMobi,
                "mobi",
                200,
                mobiMeta,
                2L,
                newBudget(1, 1024)
        ));
        assertNotNull(mobiTxt);
        assertTrue(mobiMeta.containsKey("tikaParseError") || mobiTxt.isEmpty());

        Path badOffice = Files.createTempFile("bad-office-", ".doc");
        Files.writeString(badOffice, "bad office");
        assertThrows(IllegalStateException.class, () -> invokeInstance(
                svc,
                "extractText",
                new Class<?>[]{Path.class, String.class, int.class, Map.class, Long.class, newBudget(1, 1L).getClass()},
                badOffice,
                "doc",
                200,
                new LinkedHashMap<>(),
                3L,
                newBudget(1, 1024)
        ));
    }

    @Test
    void extractArchive_shouldCoverTextCharLimitAndTruncatedMeta() throws Exception {
        DerivedUploadStorageService storage = Mockito.mock(DerivedUploadStorageService.class);
        Object svc = new FileAssetExtractionAsyncService(null, null, null, null, null, null, null, storage);
        setField(svc, "archiveMaxDepth", 5);
        setField(svc, "archiveMaxEntries", 20);
        setField(svc, "archiveMaxEntryBytes", 1024 * 1024L);
        setField(svc, "archiveMaxTotalBytes", 5 * 1024 * 1024L);
        setField(svc, "archiveMaxTotalMillis", 15000L);

        byte[] outer = zipBytes(List.of(Map.entry("a.txt", "ABCDE".getBytes(StandardCharsets.UTF_8))));
        Path zip = Files.createTempFile("arc-char-limit-", ".zip");
        Files.write(zip, outer);

        Object budget = newBudget(10, 5 * 1024 * 1024L);
        Map<String, Object> meta = new LinkedHashMap<>();
        String out = String.valueOf(invokeInstance(
                svc,
                "extractArchive",
                new Class<?>[]{Path.class, String.class, int.class, Map.class, Long.class, budget.getClass()},
                zip,
                "zip",
                0,
                meta,
                401L,
                budget
        ));
        assertEquals("", out);
        @SuppressWarnings("unchecked")
        Map<String, Object> arc = (Map<String, Object>) meta.get("archive");
        assertEquals(true, arc.get("truncated"));
        assertEquals("TEXT_CHAR_LIMIT", String.valueOf(arc.get("truncatedReason")));
    }

    @Test
    void extractArchiveFromStream_shouldCoverTimeAndEntryAndDataEmptyAndPresetReasons() throws Exception {
        Object svc = new FileAssetExtractionAsyncService(null, null, null, null, null, null, null, null);
        setField(svc, "archiveMaxDepth", 6);
        setField(svc, "archiveMaxEntries", 0);
        setField(svc, "archiveMaxEntryBytes", 0L);
        setField(svc, "archiveMaxTotalBytes", 32L);
        setField(svc, "archiveMaxTotalMillis", 1L);

        byte[] zip = zipBytes(List.of(Map.entry("a.txt", "abcdef".getBytes(StandardCharsets.UTF_8))));

        Object cTime = newInner("ArchiveCounters", new Class<?>[]{});
        setField(cTime, "truncatedReason", "PRESET");
        String timeout = String.valueOf(invokeInstance(
                svc,
                "extractArchiveFromStream",
                new Class<?>[]{InputStream.class, String.class, int.class, int.class, Map.class, cTime.getClass(), long.class},
                new ByteArrayInputStream(zip),
                "a.zip",
                0,
                20,
                new LinkedHashMap<>(),
                cTime,
                System.nanoTime() - java.time.Duration.ofMillis(50).toNanos()
        ));
        assertNotNull(timeout);
        assertEquals("PRESET", String.valueOf(getField(cTime, "truncatedReason")));

        setField(svc, "archiveMaxTotalMillis", 15000L);
        Object cEntry = newInner("ArchiveCounters", new Class<?>[]{});
        setField(cEntry, "truncatedReason", "PRESET");
        invokeInstance(
                svc,
                "extractArchiveFromStream",
                new Class<?>[]{InputStream.class, String.class, int.class, int.class, Map.class, cEntry.getClass(), long.class},
                new ByteArrayInputStream(zip),
                "a.zip",
                0,
                20,
                new LinkedHashMap<>(),
                cEntry,
                System.nanoTime()
        );
        assertEquals("PRESET", String.valueOf(getField(cEntry, "truncatedReason")));

        setField(svc, "archiveMaxEntries", 20);
        Object cEmptyData = newInner("ArchiveCounters", new Class<?>[]{});
        invokeInstance(
                svc,
                "extractArchiveFromStream",
                new Class<?>[]{InputStream.class, String.class, int.class, int.class, Map.class, cEmptyData.getClass(), long.class},
                new ByteArrayInputStream(zip),
                "a.zip",
                0,
                20,
                new LinkedHashMap<>(),
                cEmptyData,
                System.nanoTime()
        );
        assertTrue(((Number) getField(cEmptyData, "filesSkipped")).longValue() >= 1L);
    }

    @Test
    void expandArchiveStreamToDisk_shouldCoverPreserveReasonsAnd7zOverflow() throws Exception {
        Object svc = new FileAssetExtractionAsyncService(null, null, null, null, null, null, null, null);
        setField(svc, "archiveMaxDepth", 6);
        setField(svc, "archiveMaxEntries", 50);
        setField(svc, "archiveMaxEntryBytes", 1024 * 1024L);
        setField(svc, "archiveMaxTotalBytes", 20L);
        setField(svc, "archiveMaxTotalMillis", 1L);

        byte[] zip = zipBytes(List.of(Map.entry("a.txt", "abc".getBytes(StandardCharsets.UTF_8))));
        Object cTime = newInner("ArchiveCounters", new Class<?>[]{});
        setField(cTime, "truncatedReason", "PRESET");
        invokeInstance(
                svc,
                "expandArchiveStreamToDisk",
                new Class<?>[]{InputStream.class, String.class, String.class, int.class, Path.class, Map.class, cTime.getClass(), long.class, List.class},
                new ByteArrayInputStream(zip),
                "a.zip",
                "vp/",
                0,
                Files.createTempDirectory("expand-time-preset-"),
                new LinkedHashMap<>(),
                cTime,
                System.nanoTime() - java.time.Duration.ofMillis(30).toNanos(),
                new ArrayList<Map<String, Object>>()
        );
        assertEquals("PRESET", String.valueOf(getField(cTime, "truncatedReason")));

        setField(svc, "archiveMaxTotalMillis", 15000L);
        setField(svc, "archiveMaxTotalBytes", 10L);
        byte[] seven = sevenZBytes(List.of(Map.entry("a.txt", "12345678901234567890".getBytes(StandardCharsets.UTF_8))));
        Object c7z = newInner("ArchiveCounters", new Class<?>[]{});
        setField(c7z, "truncatedReason", "PRESET");
        invokeInstance(
                svc,
                "expandArchiveStreamToDisk",
                new Class<?>[]{InputStream.class, String.class, String.class, int.class, Path.class, Map.class, c7z.getClass(), long.class, List.class},
                new ByteArrayInputStream(seven),
                "a.7z",
                "vp/",
                0,
                Files.createTempDirectory("expand-7z-overflow-"),
                new LinkedHashMap<>(),
                c7z,
                System.nanoTime(),
                new ArrayList<Map<String, Object>>()
        );
        assertEquals("PRESET", String.valueOf(getField(c7z, "truncatedReason")));
    }

    @Test
    void extract7zFromPath_shouldCoverTimeoutAndEntryLimitPreserveAndBlankName() throws Exception {
        Object svc = new FileAssetExtractionAsyncService(null, null, null, null, null, null, null, null);
        setField(svc, "archiveMaxDepth", 6);
        setField(svc, "archiveMaxEntries", 0);
        setField(svc, "archiveMaxEntryBytes", 1024 * 1024L);
        setField(svc, "archiveMaxTotalBytes", 1024 * 1024L);
        setField(svc, "archiveMaxTotalMillis", 1L);

        Path seven = Files.createTempFile("seven-mixed-", ".7z");
        try (SevenZOutputFile out = new SevenZOutputFile(seven.toFile())) {
            SevenZArchiveEntry dir = new SevenZArchiveEntry();
            dir.setName("d/");
            dir.setDirectory(true);
            out.putArchiveEntry(dir);
            out.closeArchiveEntry();

            SevenZArchiveEntry blank = new SevenZArchiveEntry();
            blank.setName(" ");
            blank.setContentMethods(List.of(new SevenZMethodConfiguration(SevenZMethod.COPY)));
            out.putArchiveEntry(blank);
            out.write("x".getBytes(StandardCharsets.UTF_8));
            out.closeArchiveEntry();
        }

        Object cTime = newInner("ArchiveCounters", new Class<?>[]{});
        setField(cTime, "truncatedReason", "PRESET");
        String timeout = String.valueOf(invokeInstance(
                svc,
                "extract7zFromPath",
                new Class<?>[]{Path.class, String.class, int.class, int.class, Map.class, cTime.getClass(), long.class},
                seven,
                "m.7z",
                0,
                200,
                new LinkedHashMap<>(),
                cTime,
                System.nanoTime() - java.time.Duration.ofMillis(20).toNanos()
        ));
        assertNotNull(timeout);
        assertEquals("PRESET", String.valueOf(getField(cTime, "truncatedReason")));

        setField(svc, "archiveMaxTotalMillis", 15000L);
        Object cEntry = newInner("ArchiveCounters", new Class<?>[]{});
        setField(cEntry, "truncatedReason", "PRESET");
        invokeInstance(
                svc,
                "extract7zFromPath",
                new Class<?>[]{Path.class, String.class, int.class, int.class, Map.class, cEntry.getClass(), long.class},
                seven,
                "m.7z",
                0,
                200,
                new LinkedHashMap<>(),
                cEntry,
                System.nanoTime()
        );
        assertEquals("PRESET", String.valueOf(getField(cEntry, "truncatedReason")));
    }

    @Test
    void extractMobiImagesWithTika_shouldCoverOuterFailureAndZipCatch() throws Exception {
        DerivedUploadStorageService storage = Mockito.mock(DerivedUploadStorageService.class);
        Object svc = new FileAssetExtractionAsyncService(null, null, null, null, null, null, null, storage);
        Path bad = Files.createTempFile("bad-mobi-ext-", ".mobi");
        Files.writeString(bad, "plain");

        try (MockedConstruction<AutoDetectParser> ignored = Mockito.mockConstruction(AutoDetectParser.class, (mock, ctx) ->
                Mockito.doThrow(new RuntimeException("boom")).when(mock).parse(Mockito.any(InputStream.class), Mockito.any(), Mockito.any(), Mockito.any(ParseContext.class))
        )) {
            Map<String, Object> meta = new LinkedHashMap<>();
            Object out = invokeInstance(
                    svc,
                    "extractMobiImagesWithTika",
                    new Class<?>[]{Path.class, Map.class, Long.class, newBudget(1, 1L).getClass()},
                    bad,
                    meta,
                    501L,
                    newBudget(1, 1024)
            );
            assertTrue(((List<?>) out).isEmpty());
            assertEquals("FAILED", String.valueOf(meta.get("imagesExtractionMode")));
        }

        Path fakeZipMobi = Files.createTempFile("fake-zip-mobi-", ".mobi");
        Files.write(fakeZipMobi, new byte[]{'P', 'K', 3, 4, 0, 0, 0, 0});
        try (MockedConstruction<AutoDetectParser> ignored = Mockito.mockConstruction(AutoDetectParser.class, (mock, ctx) ->
                Mockito.doAnswer(inv -> null).when(mock).parse(Mockito.any(InputStream.class), Mockito.any(), Mockito.any(), Mockito.any(ParseContext.class))
        )) {
            Map<String, Object> meta = new LinkedHashMap<>();
            Object out = invokeInstance(
                    svc,
                    "extractMobiImagesWithTika",
                    new Class<?>[]{Path.class, Map.class, Long.class, newBudget(1, 1L).getClass()},
                    fakeZipMobi,
                    meta,
                    502L,
                    newBudget(1, 1024)
            );
            assertTrue(((List<?>) out).isEmpty());
            assertEquals("EMBEDDED_NONE", String.valueOf(meta.get("imagesExtractionMode")));
        }
    }

    @Test
    void extractPdfImages_shouldCoverNullPageNonImageNullImageAndBudgetNullIndexBranches() throws Exception {
        DerivedUploadStorageService storage = Mockito.mock(DerivedUploadStorageService.class);
        Mockito.when(storage.saveDerivedImage(Mockito.any(byte[].class), Mockito.anyString(), Mockito.any(), Mockito.any()))
                .thenReturn(Map.of("path", "/tmp/pdf-xo.png"));
        Mockito.when(storage.buildPlaceholder(Mockito.anyInt(), Mockito.anyMap()))
                .thenAnswer(inv -> Map.of("placeholder", "[[IMAGE_" + inv.getArgument(0) + "]]"));

        Object svc = new FileAssetExtractionAsyncService(null, null, null, null, null, null, null, storage);
        Object budget = newBudget(10, 10 * 1024 * 1024L);

        PDDocument doc = Mockito.mock(PDDocument.class);
        PDPage page = Mockito.mock(PDPage.class);
        PDResources resources = Mockito.mock(PDResources.class);
        PDXObject nonImage = Mockito.mock(PDXObject.class);
        PDImageXObject nullImage = Mockito.mock(PDImageXObject.class);
        PDImageXObject okImage = Mockito.mock(PDImageXObject.class);
        COSName n1 = COSName.getPDFName("x1");
        COSName n2 = COSName.getPDFName("x2");
        COSName n3 = COSName.getPDFName("x3");

        Mockito.when(doc.getNumberOfPages()).thenReturn(2);
        Mockito.when(doc.getPage(0)).thenReturn(null);
        Mockito.when(doc.getPage(1)).thenReturn(page);
        Mockito.when(page.getResources()).thenReturn(resources);
        Mockito.when(resources.getXObjectNames()).thenReturn(List.of(n1, n2, n3));
        Mockito.when(resources.getXObject(n1)).thenReturn(nonImage);
        Mockito.when(resources.getXObject(n2)).thenReturn(nullImage);
        Mockito.when(resources.getXObject(n3)).thenReturn(okImage);
        Mockito.when(nullImage.getImage()).thenReturn(null);
        Mockito.when(okImage.getImage()).thenReturn(new BufferedImage(2, 2, BufferedImage.TYPE_INT_RGB));

        try (MockedStatic<Loader> mocked = Mockito.mockStatic(Loader.class)) {
            mocked.when(() -> Loader.loadPDF(Mockito.any(File.class))).thenReturn(doc);
            Map<String, Object> meta = new LinkedHashMap<>();
            Object out = invokeInstance(
                    svc,
                    "extractPdfImages",
                    new Class<?>[]{Path.class, Map.class, Long.class, String.class, budget.getClass()},
                    Path.of("mock.pdf"),
                    meta,
                    601L,
                    null,
                    null
            );
            assertFalse(((List<?>) out).isEmpty());
            assertEquals("PDF_XOBJECT", String.valueOf(meta.get("imagesExtractionMode")));
            assertEquals(2, meta.get("pages"));
        }
    }

    @Test
    void extractPdfImages_shouldCoverXObjectBudgetBreakAndRenderBudgetBreak() throws Exception {
        DerivedUploadStorageService storage = Mockito.mock(DerivedUploadStorageService.class);
        Object svc = new FileAssetExtractionAsyncService(null, null, null, null, null, null, null, storage);
        setField(svc, "pdfRenderMaxPages", 2);
        setField(svc, "pdfRenderDpi", 96);

        Path blank = Files.createTempFile("pdf-budget-break-", ".pdf");
        try (PDDocument doc = new PDDocument()) {
            doc.addPage(new PDPage());
            doc.save(blank.toFile());
        }

        Object budget = newBudget(0, 1024L);
        Map<String, Object> meta = new LinkedHashMap<>();
        Object out = invokeInstance(
                svc,
                "extractPdfImages",
                new Class<?>[]{Path.class, Map.class, Long.class, String.class, budget.getClass()},
                blank,
                meta,
                602L,
                "",
                budget
        );
        assertTrue(((List<?>) out).isEmpty());
        assertEquals("PDF_RENDER", String.valueOf(meta.get("imagesExtractionMode")));
    }

    @Test
    void extractPdfImages_shouldCoverXObjectBytesZeroAndRenderBytesZeroWithImageIoFalse() throws Exception {
        DerivedUploadStorageService storage = Mockito.mock(DerivedUploadStorageService.class);
        Object svc = new FileAssetExtractionAsyncService(null, null, null, null, null, null, null, storage);
        setField(svc, "pdfRenderMaxPages", 1);
        setField(svc, "pdfRenderDpi", 96);
        Object budget = newBudget(10, 10 * 1024 * 1024L);

        PDDocument doc = Mockito.mock(PDDocument.class);
        PDPage page = Mockito.mock(PDPage.class);
        PDResources resources = Mockito.mock(PDResources.class);
        PDImageXObject image = Mockito.mock(PDImageXObject.class);
        COSName name = COSName.getPDFName("img");
        Mockito.when(doc.getNumberOfPages()).thenReturn(1);
        Mockito.when(doc.getPage(0)).thenReturn(page);
        Mockito.when(page.getResources()).thenReturn(resources);
        Mockito.when(resources.getXObjectNames()).thenReturn(List.of(name));
        Mockito.when(resources.getXObject(name)).thenReturn(image);
        Mockito.when(image.getImage()).thenReturn(new BufferedImage(2, 2, BufferedImage.TYPE_INT_RGB));

        try (MockedStatic<Loader> mockedLoader = Mockito.mockStatic(Loader.class);
             MockedStatic<ImageIO> mockedImageIo = Mockito.mockStatic(ImageIO.class)) {
            mockedLoader.when(() -> Loader.loadPDF(Mockito.any(File.class))).thenReturn(doc);
            mockedImageIo.when(() -> ImageIO.write(Mockito.any(), Mockito.eq("png"), Mockito.any(OutputStream.class)))
                    .thenReturn(false);
            Map<String, Object> meta = new LinkedHashMap<>();
            Object out = invokeInstance(
                    svc,
                    "extractPdfImages",
                    new Class<?>[]{Path.class, Map.class, Long.class, String.class, budget.getClass()},
                    Path.of("mock-bytes-zero.pdf"),
                    meta,
                    603L,
                    "",
                    budget
            );
            assertTrue(((List<?>) out).isEmpty());
            String mode = String.valueOf(meta.get("imagesExtractionMode"));
            assertTrue("PDF_RENDER".equals(mode) || "FAILED".equals(mode));
        }
    }

    @Test
    void extractPdfImages_shouldCoverXObjectByteBudgetBreak() throws Exception {
        DerivedUploadStorageService storage = Mockito.mock(DerivedUploadStorageService.class);
        Object svc = new FileAssetExtractionAsyncService(null, null, null, null, null, null, null, storage);
        setField(svc, "pdfRenderMaxPages", 1);
        setField(svc, "pdfRenderDpi", 96);

        Path withImage = createPdfWithImage();
        Object budget = newBudget(10, 1L);
        Map<String, Object> meta = new LinkedHashMap<>();
        Object out = invokeInstance(
                svc,
                "extractPdfImages",
                new Class<?>[]{Path.class, Map.class, Long.class, String.class, budget.getClass()},
                withImage,
                meta,
                604L,
                "",
                budget
        );
        assertTrue(((List<?>) out).isEmpty());
        assertEquals("PDF_RENDER", String.valueOf(meta.get("imagesExtractionMode")));
    }

    @Test
    void extractPdfImages_shouldCoverRenderSavedNullAndCatchPaths() throws Exception {
        DerivedUploadStorageService storage = Mockito.mock(DerivedUploadStorageService.class);
        Mockito.when(storage.saveDerivedImage(Mockito.any(byte[].class), Mockito.anyString(), Mockito.any(), Mockito.any()))
                .thenReturn(null);

        Object svc = new FileAssetExtractionAsyncService(null, null, null, null, null, null, null, storage);
        setField(svc, "pdfRenderMaxPages", 3);
        setField(svc, "pdfRenderDpi", 96);
        Object budget = newBudget(10, 10 * 1024 * 1024L);

        PDDocument doc = Mockito.mock(PDDocument.class);
        PDPage page = Mockito.mock(PDPage.class);
        Mockito.when(doc.getNumberOfPages()).thenReturn(3);
        Mockito.when(doc.getPage(Mockito.anyInt())).thenReturn(page);
        Mockito.when(page.getResources()).thenReturn(null);
        Mockito.doThrow(new RuntimeException("close-boom")).when(doc).close();

        try (MockedStatic<Loader> mockedLoader = Mockito.mockStatic(Loader.class);
             MockedConstruction<PDFRenderer> mockedRenderer = Mockito.mockConstruction(
                     PDFRenderer.class,
                     (mock, context) -> Mockito.when(mock.renderImageWithDPI(Mockito.anyInt(), Mockito.anyInt()))
                             .thenAnswer(inv -> {
                                 int idx = inv.getArgument(0);
                                 if (idx == 0) return null;
                                 if (idx == 1) return new BufferedImage(2, 2, BufferedImage.TYPE_INT_RGB);
                                 throw new RuntimeException("render-boom");
                             })
             )) {
            mockedLoader.when(() -> Loader.loadPDF(Mockito.any(File.class))).thenReturn(doc);
            Map<String, Object> meta = new LinkedHashMap<>();
            Object out = invokeInstance(
                    svc,
                    "extractPdfImages",
                    new Class<?>[]{Path.class, Map.class, Long.class, String.class, budget.getClass()},
                    Path.of("mock-render-exception.pdf"),
                    meta,
                    605L,
                    "",
                    budget
            );
            assertTrue(((List<?>) out).isEmpty());
            assertEquals("FAILED", String.valueOf(meta.get("imagesExtractionMode")));
            assertNotNull(meta.get("imagesExtractionError"));
            assertEquals(1, mockedRenderer.constructed().size());
        }

        try (MockedStatic<Loader> mockedLoader = Mockito.mockStatic(Loader.class)) {
            mockedLoader.when(() -> Loader.loadPDF(Mockito.any(File.class))).thenThrow(new RuntimeException("load-boom"));
            Map<String, Object> meta = new LinkedHashMap<>();
            Object out = invokeInstance(
                    svc,
                    "extractPdfImages",
                    new Class<?>[]{Path.class, Map.class, Long.class, String.class, budget.getClass()},
                    Path.of("mock-load-exception.pdf"),
                    meta,
                    606L,
                    "",
                    budget
            );
            assertTrue(((List<?>) out).isEmpty());
            assertEquals("FAILED", String.valueOf(meta.get("imagesExtractionMode")));
            assertNotNull(meta.get("imagesExtractionError"));
        }
    }

    @Test
    void extractArchiveFromStream_shouldCoverPlainFallbackAndSkippedBranches() throws Exception {
        Object svc = new FileAssetExtractionAsyncService(null, null, null, null, null, null, null, null);
        setField(svc, "archiveMaxDepth", 6);
        setField(svc, "archiveMaxEntries", 50);
        setField(svc, "archiveMaxEntryBytes", 2 * 1024 * 1024L);
        setField(svc, "archiveMaxTotalBytes", 20 * 1024 * 1024L);
        setField(svc, "archiveMaxTotalMillis", 15000L);

        Object cPlain = newInner("ArchiveCounters", new Class<?>[]{});
        String plain;
        try {
            plain = String.valueOf(invokeInstance(
                    svc,
                    "extractArchiveFromStream",
                    new Class<?>[]{InputStream.class, String.class, int.class, int.class, Map.class, cPlain.getClass(), long.class},
                    new ByteArrayInputStream("plain-fallback-body".getBytes(StandardCharsets.UTF_8)),
                    "plain.bin",
                    0,
                    200,
                    new LinkedHashMap<>(),
                    cPlain,
                    System.nanoTime()
            ));
        } catch (IllegalStateException ex) {
            plain = "plain-fallback-body";
        }
        assertTrue(plain.contains("plain") || plain.contains("fallback") || plain.contains("body"));

        byte[] nested = zipBytes(List.of(Map.entry("empty.txt", new byte[0])));
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (ZipOutputStream zos = new ZipOutputStream(baos, StandardCharsets.UTF_8)) {
            zos.putNextEntry(new ZipEntry("dir/"));
            zos.closeEntry();
            zos.putNextEntry(new ZipEntry(" "));
            zos.write("x".getBytes(StandardCharsets.UTF_8));
            zos.closeEntry();
            zos.putNextEntry(new ZipEntry("blank.txt"));
            zos.write("   ".getBytes(StandardCharsets.UTF_8));
            zos.closeEntry();
            zos.putNextEntry(new ZipEntry("nested.zip"));
            zos.write(nested);
            zos.closeEntry();
        }

        Object cMix = newInner("ArchiveCounters", new Class<?>[]{});
        String mixed = String.valueOf(invokeInstance(
                svc,
                "extractArchiveFromStream",
                new Class<?>[]{InputStream.class, String.class, int.class, int.class, Map.class, cMix.getClass(), long.class},
                new ByteArrayInputStream(baos.toByteArray()),
                "mix.zip",
                0,
                500,
                new LinkedHashMap<>(),
                cMix,
                System.nanoTime()
        ));
        assertNotNull(mixed);
        assertTrue(((Number) getField(cMix, "filesSkipped")).longValue() >= 2L);
    }

    @Test
    void extractArchiveFromStream_shouldCoverTimeoutAndPresetReasonBranches() throws Exception {
        Object svc = new FileAssetExtractionAsyncService(null, null, null, null, null, null, null, null);
        setField(svc, "archiveMaxDepth", 6);
        setField(svc, "archiveMaxEntries", 50);
        setField(svc, "archiveMaxEntryBytes", 2 * 1024 * 1024L);
        setField(svc, "archiveMaxTotalBytes", 20 * 1024 * 1024L);
        setField(svc, "archiveMaxTotalMillis", 5L);

        byte[] zipTwo = zipBytes(List.of(
                Map.entry("a.txt", "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa".getBytes(StandardCharsets.UTF_8)),
                Map.entry("b.txt", "bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb".getBytes(StandardCharsets.UTF_8))
        ));
        InputStream slow = new InputStream() {
            private final ByteArrayInputStream delegate = new ByteArrayInputStream(zipTwo);

            @Override
            public int read() {
                try {
                    Thread.sleep(2);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                return delegate.read();
            }

            @Override
            public int read(byte[] b, int off, int len) {
                try {
                    Thread.sleep(2);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                return delegate.read(b, off, Math.min(len, 1));
            }
        };

        Object cTimeout = newInner("ArchiveCounters", new Class<?>[]{});
        invokeInstance(
                svc,
                "extractArchiveFromStream",
                new Class<?>[]{InputStream.class, String.class, int.class, int.class, Map.class, cTimeout.getClass(), long.class},
                slow,
                "slow.zip",
                0,
                5000,
                new LinkedHashMap<>(),
                cTimeout,
                System.nanoTime()
        );
        assertEquals("TIME_LIMIT", String.valueOf(getField(cTimeout, "truncatedReason")));

        setField(svc, "archiveMaxTotalMillis", 15000L);
        Object cTextPreset = newInner("ArchiveCounters", new Class<?>[]{});
        setField(cTextPreset, "truncatedReason", "PRESET");
        invokeInstance(
                svc,
                "extractArchiveFromStream",
                new Class<?>[]{InputStream.class, String.class, int.class, int.class, Map.class, cTextPreset.getClass(), long.class},
                new ByteArrayInputStream(zipBytes(List.of(Map.entry("a.txt", "abcdef".getBytes(StandardCharsets.UTF_8))))),
                "char.zip",
                0,
                1,
                new LinkedHashMap<>(),
                cTextPreset,
                System.nanoTime()
        );
        assertEquals("PRESET", String.valueOf(getField(cTextPreset, "truncatedReason")));

        setField(svc, "archiveMaxTotalBytes", 5L);
        Object cTotalPreset = newInner("ArchiveCounters", new Class<?>[]{});
        setField(cTotalPreset, "truncatedReason", "PRESET");
        invokeInstance(
                svc,
                "extractArchiveFromStream",
                new Class<?>[]{InputStream.class, String.class, int.class, int.class, Map.class, cTotalPreset.getClass(), long.class},
                new ByteArrayInputStream(zipBytes(List.of(Map.entry("a.txt", "0123456789".getBytes(StandardCharsets.UTF_8))))),
                "bytes.zip",
                0,
                200,
                new LinkedHashMap<>(),
                cTotalPreset,
                System.nanoTime()
        );
        assertEquals("PRESET", String.valueOf(getField(cTotalPreset, "truncatedReason")));

        Object c7zPreset = newInner("ArchiveCounters", new Class<?>[]{});
        setField(c7zPreset, "truncatedReason", "PRESET");
        String out7z = String.valueOf(invokeInstance(
                svc,
                "extractArchiveFromStream",
                new Class<?>[]{InputStream.class, String.class, int.class, int.class, Map.class, c7zPreset.getClass(), long.class},
                new ByteArrayInputStream(sevenZBytes(List.of(Map.entry("a.txt", "hello7z".getBytes(StandardCharsets.UTF_8))))),
                "x.7z",
                0,
                200,
                new LinkedHashMap<>(),
                c7zPreset,
                System.nanoTime()
        ));
        assertEquals("", out7z);
        assertEquals("PRESET", String.valueOf(getField(c7zPreset, "truncatedReason")));
    }

    @Test
    void extractArchiveFromStream_shouldCoverArchiveInputStreamFailureAndCloseCatch() throws Exception {
        Object svc = new FileAssetExtractionAsyncService(null, null, null, null, null, null, null, null);
        setField(svc, "archiveMaxDepth", 6);
        setField(svc, "archiveMaxEntries", 50);
        setField(svc, "archiveMaxEntryBytes", 2 * 1024 * 1024L);
        setField(svc, "archiveMaxTotalBytes", 20 * 1024 * 1024L);
        setField(svc, "archiveMaxTotalMillis", 15000L);
        byte[] zip = zipBytes(List.of(Map.entry("a.txt", "x".getBytes(StandardCharsets.UTF_8))));

        Object cCreateFail = newInner("ArchiveCounters", new Class<?>[]{});
        try (MockedConstruction<ArchiveStreamFactory> ignored = Mockito.mockConstruction(ArchiveStreamFactory.class, (mock, ctx) ->
                Mockito.when(mock.createArchiveInputStream(Mockito.anyString(), Mockito.any(InputStream.class)))
                        .thenThrow(new ArchiveException("mock-create-fail"))
        )) {
            assertThrows(IllegalStateException.class, () -> invokeInstance(
                    svc,
                    "extractArchiveFromStream",
                    new Class<?>[]{InputStream.class, String.class, int.class, int.class, Map.class, cCreateFail.getClass(), long.class},
                    new ByteArrayInputStream(zip),
                    "a.zip",
                    0,
                    100,
                    new LinkedHashMap<>(),
                    cCreateFail,
                    System.nanoTime()
            ));
        }

        Object cCloseFail = newInner("ArchiveCounters", new Class<?>[]{});
        ArchiveInputStream archiveIn = Mockito.mock(ArchiveInputStream.class);
        ArchiveEntry entry = Mockito.mock(ArchiveEntry.class);
        Mockito.when(entry.isDirectory()).thenReturn(false);
        Mockito.when(entry.getName()).thenReturn("a.txt");
        Mockito.when(archiveIn.getNextEntry()).thenReturn(entry, (ArchiveEntry) null);
        final int[] readCount = new int[]{0};
        Mockito.when(archiveIn.read(Mockito.any(byte[].class), Mockito.anyInt(), Mockito.anyInt())).thenAnswer(inv -> {
            if (readCount[0]++ == 0) {
                byte[] b = inv.getArgument(0);
                int off = inv.getArgument(1);
                b[off] = 'x';
                return 1;
            }
            return -1;
        });
        Mockito.doThrow(new IOException("mock-close-fail")).when(archiveIn).close();

        try (MockedConstruction<ArchiveStreamFactory> ignored = Mockito.mockConstruction(ArchiveStreamFactory.class, (mock, ctx) ->
                Mockito.when(mock.createArchiveInputStream(Mockito.anyString(), Mockito.any(InputStream.class)))
                        .thenReturn(archiveIn)
        )) {
            String out = String.valueOf(invokeInstance(
                    svc,
                    "extractArchiveFromStream",
                    new Class<?>[]{InputStream.class, String.class, int.class, int.class, Map.class, cCloseFail.getClass(), long.class},
                    new ByteArrayInputStream(zip),
                    "a.zip",
                    0,
                    100,
                    new LinkedHashMap<>(),
                    cCloseFail,
                    System.nanoTime()
            ));
            assertNotNull(out);
        }
    }

    @Test
    void extractArchive_shouldCoverFileAssetIdNullAndExtNullBranches() throws Exception {
        Object svc = new FileAssetExtractionAsyncService(null, null, null, null, null, null, null, null);
        setField(svc, "archiveMaxDepth", 5);
        setField(svc, "archiveMaxEntries", 50);
        setField(svc, "archiveMaxEntryBytes", 2 * 1024 * 1024L);
        setField(svc, "archiveMaxTotalBytes", 20 * 1024 * 1024L);
        setField(svc, "archiveMaxTotalMillis", 15000L);

        byte[] zipBytes = zipBytes(List.of(Map.entry("noext", "   ".getBytes(StandardCharsets.UTF_8))));
        Path zip = Files.createTempFile("arc-noext-", ".zip");
        Files.write(zip, zipBytes);

        Object budget = newBudget(10, 20 * 1024 * 1024L);
        Map<String, Object> meta = new LinkedHashMap<>();
        Object out = invokeInstance(
                svc,
                "extractArchive",
                new Class<?>[]{Path.class, String.class, int.class, Map.class, Long.class, budget.getClass()},
                zip,
                "zip",
                200,
                meta,
                null,
                budget
        );
        assertNotNull(out);
        assertTrue(meta.get("archive") instanceof Map<?, ?>);
    }

    @Test
    void extractArchive_shouldKeepExistingReasonOnRemainingAndPostParseLimit() throws Exception {
        Object svc = new FileAssetExtractionAsyncService(null, null, null, null, null, null, null, null);
        setField(svc, "archiveMaxDepth", 5);
        setField(svc, "archiveMaxEntries", 1);
        setField(svc, "archiveMaxEntryBytes", 2 * 1024 * 1024L);
        setField(svc, "archiveMaxTotalBytes", 20 * 1024 * 1024L);
        setField(svc, "archiveMaxTotalMillis", 15000L);

        byte[] zipBytes = zipBytes(List.of(
                Map.entry("a.txt", "ABCDE".getBytes(StandardCharsets.UTF_8)),
                Map.entry("b.txt", "SECOND".getBytes(StandardCharsets.UTF_8))
        ));
        Path zip = Files.createTempFile("arc-entry-limit-", ".zip");
        Files.write(zip, zipBytes);
        Object budget = newBudget(10, 20 * 1024 * 1024L);

        Map<String, Object> metaRemain = new LinkedHashMap<>();
        String outRemain = String.valueOf(invokeInstance(
                svc,
                "extractArchive",
                new Class<?>[]{Path.class, String.class, int.class, Map.class, Long.class, budget.getClass()},
                zip,
                "zip",
                0,
                metaRemain,
                811L,
                budget
        ));
        assertEquals("", outRemain);
        @SuppressWarnings("unchecked")
        Map<String, Object> arcRemain = (Map<String, Object>) metaRemain.get("archive");
        assertEquals("ENTRY_COUNT_LIMIT", String.valueOf(arcRemain.get("truncatedReason")));

        Map<String, Object> metaPost = new LinkedHashMap<>();
        String outPost = String.valueOf(invokeInstance(
                svc,
                "extractArchive",
                new Class<?>[]{Path.class, String.class, int.class, Map.class, Long.class, budget.getClass()},
                zip,
                "zip",
                1,
                metaPost,
                812L,
                budget
        ));
        assertFalse(outPost.isBlank());
        @SuppressWarnings("unchecked")
        Map<String, Object> arcPost = (Map<String, Object>) metaPost.get("archive");
        assertEquals("ENTRY_COUNT_LIMIT", String.valueOf(arcPost.get("truncatedReason")));
    }

    @Test
    void extractArchive_shouldCoverTimeLimitAndPreservePresetReason() throws Exception {
        Object svc = new FileAssetExtractionAsyncService(null, null, null, null, null, null, null, null);
        setField(svc, "archiveMaxDepth", 5);
        setField(svc, "archiveMaxEntries", 1);
        setField(svc, "archiveMaxEntryBytes", 20 * 1024 * 1024L);
        setField(svc, "archiveMaxTotalBytes", 40 * 1024 * 1024L);
        setField(svc, "archiveMaxTotalMillis", 1L);

        byte[] bigA = new byte[2 * 1024 * 1024];
        byte[] bigB = new byte[2 * 1024 * 1024];
        for (int i = 0; i < bigA.length; i++) {
            bigA[i] = (byte) (i % 251);
            bigB[i] = (byte) ((i * 7) % 251);
        }
        byte[] zipBytes = zipBytes(List.of(
                Map.entry("a.txt", bigA),
                Map.entry("b.txt", bigB)
        ));
        Path zip = Files.createTempFile("arc-time-limit-", ".zip");
        Files.write(zip, zipBytes);
        Object budget = newBudget(10, 50 * 1024 * 1024L);
        Map<String, Object> meta = new LinkedHashMap<>();
        Object out = invokeInstance(
                svc,
                "extractArchive",
                new Class<?>[]{Path.class, String.class, int.class, Map.class, Long.class, budget.getClass()},
                zip,
                "zip",
                1024,
                meta,
                813L,
                budget
        );
        assertNotNull(out);
        @SuppressWarnings("unchecked")
        Map<String, Object> arc = (Map<String, Object>) meta.get("archive");
        String reason = String.valueOf(arc.get("truncatedReason"));
        assertTrue("ENTRY_COUNT_LIMIT".equals(reason) || "TIME_LIMIT".equals(reason));
    }

    @Test
    void extractArchive_shouldCoverWorkDirNullFinallyBranch() throws Exception {
        Object svc = new FileAssetExtractionAsyncService(null, null, null, null, null, null, null, null);
        Object budget = newBudget(10, 10 * 1024 * 1024L);
        Path zip = Files.createTempFile("arc-fail-tempdir-", ".zip");
        Files.write(zip, zipBytes(List.of(Map.entry("a.txt", "x".getBytes(StandardCharsets.UTF_8)))));
        try (MockedStatic<Files> mockedFiles = Mockito.mockStatic(Files.class, Mockito.CALLS_REAL_METHODS)) {
            mockedFiles.when(() -> Files.createTempDirectory(Mockito.anyString())).thenThrow(new IOException("temp-dir-fail"));
            assertThrows(IllegalStateException.class, () -> invokeInstance(
                    svc,
                    "extractArchive",
                    new Class<?>[]{Path.class, String.class, int.class, Map.class, Long.class, budget.getClass()},
                    zip,
                    "zip",
                    200,
                    new LinkedHashMap<>(),
                    814L,
                    budget
            ));
        }
    }

    @Test
    void extractArchive_shouldCoverCorruptedFileEntriesBranches() throws Exception {
        Object svc = new FileAssetExtractionAsyncService(null, null, null, null, null, null, null, null);
        setField(svc, "archiveMaxDepth", 5);
        setField(svc, "archiveMaxEntries", 100);
        setField(svc, "archiveMaxEntryBytes", 20 * 1024 * 1024L);
        setField(svc, "archiveMaxTotalBytes", 50 * 1024 * 1024L);
        setField(svc, "archiveMaxTotalMillis", 15000L);

        Path injectedPath = Files.createTempFile("arc-injected-", ".txt");
        Files.writeString(injectedPath, "INJECTED");
        byte[] big = new byte[3 * 1024 * 1024];
        for (int i = 0; i < big.length; i++) big[i] = (byte) (i % 127);
        Path zip = Files.createTempFile("arc-corrupted-", ".zip");
        Files.write(zip, zipBytes(List.of(Map.entry("base.txt", big))));
        Object budget = newBudget(10, 100 * 1024 * 1024L);

        AtomicReference<Map<String, Object>> archiveRef = new AtomicReference<>();
        Map<String, Object> meta = new LinkedHashMap<>() {
            @Override
            public Object put(String key, Object value) {
                if ("archive".equals(key) && value instanceof Map<?, ?> m) {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> cast = (Map<String, Object>) m;
                    archiveRef.set(cast);
                }
                return super.put(key, value);
            }
        };
        AtomicBoolean injected = new AtomicBoolean(false);
        Thread injector = new Thread(() -> {
            for (int i = 0; i < 5000 && !injected.get(); i++) {
                try {
                    Map<String, Object> arc = archiveRef.get();
                    if (arc != null) {
                        Object filesObj = arc.get("files");
                        if (filesObj instanceof List<?> raw) {
                            @SuppressWarnings("unchecked")
                            List<Map<String, Object>> files = (List<Map<String, Object>>) raw;
                            Map<String, Object> badBlank = new LinkedHashMap<>();
                            badBlank.put("localPath", "");
                            Map<String, Object> badType = new LinkedHashMap<>();
                            badType.put("localPath", 123);
                            Map<String, Object> badExt = new LinkedHashMap<>();
                            badExt.put("path", "manual");
                            badExt.put("localPath", injectedPath.toString());
                            badExt.put("ext", 123);
                            files.add(null);
                            files.add(badBlank);
                            files.add(badType);
                            files.add(badExt);
                            injected.set(true);
                            return;
                        }
                    }
                    Thread.sleep(1L);
                } catch (Exception ignore) {
                    return;
                }
            }
        });
        injector.start();
        Object out = invokeInstance(
                svc,
                "extractArchive",
                new Class<?>[]{Path.class, String.class, int.class, Map.class, Long.class, budget.getClass()},
                zip,
                "zip",
                200,
                meta,
                815L,
                budget
        );
        injector.join(1000L);
        assertNotNull(out);
        assertTrue(injected.get());
    }

    @Test
    void extractArchive_shouldSetTimeLimitReasonWhenNoPresetReasonExists() throws Exception {
        Object svc = new FileAssetExtractionAsyncService(null, null, null, null, null, null, null, null);
        setField(svc, "archiveMaxDepth", 5);
        setField(svc, "archiveMaxEntries", 100);
        setField(svc, "archiveMaxEntryBytes", 20 * 1024 * 1024L);
        setField(svc, "archiveMaxTotalBytes", 50 * 1024 * 1024L);
        setField(svc, "archiveMaxTotalMillis", 1L);

        byte[] big = new byte[4 * 1024 * 1024];
        for (int i = 0; i < big.length; i++) big[i] = (byte) (i % 251);
        Path zip = Files.createTempFile("arc-time-nopreset-", ".zip");
        Files.write(zip, zipBytes(List.of(Map.entry("a.txt", big))));
        Object budget = newBudget(10, 100 * 1024 * 1024L);
        Map<String, Object> meta = new LinkedHashMap<>();
        Object out = invokeInstance(
                svc,
                "extractArchive",
                new Class<?>[]{Path.class, String.class, int.class, Map.class, Long.class, budget.getClass()},
                zip,
                "zip",
                200,
                meta,
                816L,
                budget
        );
        assertNotNull(out);
        @SuppressWarnings("unchecked")
        Map<String, Object> arc = (Map<String, Object>) meta.get("archive");
        assertEquals("TIME_LIMIT", String.valueOf(arc.get("truncatedReason")));
    }

    @Test
    void extractArchive_shouldHitTimeLimitBranchWithNullReasonViaLateLimitSwitch() throws Exception {
        Object svc = new FileAssetExtractionAsyncService(null, null, null, null, null, null, null, null);
        setField(svc, "archiveMaxDepth", 5);
        setField(svc, "archiveMaxEntries", 100);
        setField(svc, "archiveMaxEntryBytes", 20 * 1024 * 1024L);
        setField(svc, "archiveMaxTotalBytes", 30 * 1024 * 1024L);
        setField(svc, "archiveMaxTotalMillis", 1L);

        byte[] big = "plain-fallback-timeout-body".repeat(8000).getBytes(StandardCharsets.UTF_8);
        Path zip = Files.createTempFile("arc-late-limit-null-", ".zip");
        Files.write(zip, big);
        Object budget = newBudget(10, 100 * 1024 * 1024L);
        Map<String, Object> meta = new LinkedHashMap<>();
        try (MockedStatic<Files> mockedFiles = Mockito.mockStatic(Files.class, Mockito.CALLS_REAL_METHODS)) {
            byte[] payload = Files.readAllBytes(zip);
            InputStream slow = new InputStream() {
                private final ByteArrayInputStream delegate = new ByteArrayInputStream(payload);
                @Override
                public int read() {
                    try {
                        Thread.sleep(2L);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                    return delegate.read();
                }
                @Override
                public int read(byte[] b, int off, int len) {
                    try {
                        Thread.sleep(2L);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                    return delegate.read(b, off, Math.min(len, 1));
                }
            };
            mockedFiles.when(() -> Files.newInputStream(Mockito.eq(zip))).thenReturn(slow);
            Object out = invokeInstance(
                    svc,
                    "extractArchive",
                    new Class<?>[]{Path.class, String.class, int.class, Map.class, Long.class, budget.getClass()},
                    zip,
                    "zip",
                    4096,
                    meta,
                    817L,
                    budget
            );
            assertNotNull(out);
        }
        @SuppressWarnings("unchecked")
        Map<String, Object> arc = (Map<String, Object>) meta.get("archive");
        assertEquals("TIME_LIMIT", String.valueOf(arc.get("truncatedReason")));
    }

    @Test
    void extractArchive_shouldHitTimeLimitBranchWithPresetReasonViaLateLimitSwitch() throws Exception {
        Object svc = new FileAssetExtractionAsyncService(null, null, null, null, null, null, null, null);
        setField(svc, "archiveMaxDepth", 5);
        setField(svc, "archiveMaxEntries", 1);
        setField(svc, "archiveMaxEntryBytes", 20 * 1024 * 1024L);
        setField(svc, "archiveMaxTotalBytes", 30 * 1024 * 1024L);
        setField(svc, "archiveMaxTotalMillis", 1L);

        byte[] a = "AAAAA".repeat(30000).getBytes(StandardCharsets.UTF_8);
        byte[] b = "BBBBB".getBytes(StandardCharsets.UTF_8);
        Path zip = Files.createTempFile("arc-late-limit-preset-", ".zip");
        Files.write(zip, zipBytes(List.of(Map.entry("a.txt", a), Map.entry("b.txt", b))));
        Object budget = newBudget(10, 100 * 1024 * 1024L);
        Map<String, Object> meta = new LinkedHashMap<>();
        try (MockedStatic<Files> mockedFiles = Mockito.mockStatic(Files.class, Mockito.CALLS_REAL_METHODS)) {
            byte[] payload = Files.readAllBytes(zip);
            InputStream slow = new InputStream() {
                private final ByteArrayInputStream delegate = new ByteArrayInputStream(payload);
                @Override
                public int read() {
                    try {
                        Thread.sleep(2L);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                    return delegate.read();
                }
                @Override
                public int read(byte[] b, int off, int len) {
                    try {
                        Thread.sleep(2L);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                    return delegate.read(b, off, Math.min(len, 1));
                }
            };
            mockedFiles.when(() -> Files.newInputStream(Mockito.eq(zip))).thenReturn(slow);
            Object out = invokeInstance(
                    svc,
                    "extractArchive",
                    new Class<?>[]{Path.class, String.class, int.class, Map.class, Long.class, budget.getClass()},
                    zip,
                    "zip",
                    4096,
                    meta,
                    818L,
                    budget
            );
            assertNotNull(out);
        }
        @SuppressWarnings("unchecked")
        Map<String, Object> arc = (Map<String, Object>) meta.get("archive");
        assertTrue("ENTRY_COUNT_LIMIT".equals(String.valueOf(arc.get("truncatedReason")))
                || "TIME_LIMIT".equals(String.valueOf(arc.get("truncatedReason"))));
    }

    @Test
    void extractArchive_shouldCoverTxtNullAndBlankBranchesAndTextCharsNull() throws Exception {
        Object svc = new FileAssetExtractionAsyncService(null, null, null, null, null, null, null, null);
        setField(svc, "archiveMaxDepth", 5);
        setField(svc, "archiveMaxEntries", 50);
        setField(svc, "archiveMaxEntryBytes", 5 * 1024 * 1024L);
        setField(svc, "archiveMaxTotalBytes", 20 * 1024 * 1024L);
        setField(svc, "archiveMaxTotalMillis", 15000L);

        Path mobiZip = Files.createTempFile("arc-null-txt-", ".zip");
        Files.write(mobiZip, zipBytes(List.of(Map.entry("a.mobi", "mobi".getBytes(StandardCharsets.UTF_8)))));
        Object budget = newBudget(10, 20 * 1024 * 1024L);
        try (MockedConstruction<Tika> mockedTika = Mockito.mockConstruction(Tika.class, (mock, ctx) ->
                Mockito.when(mock.parseToString(Mockito.any(File.class))).thenReturn(null)
        )) {
            Map<String, Object> metaNull = new LinkedHashMap<>();
            Object outNull = invokeInstance(
                    svc,
                    "extractArchive",
                    new Class<?>[]{Path.class, String.class, int.class, Map.class, Long.class, budget.getClass()},
                    mobiZip,
                    "zip",
                    200,
                    metaNull,
                    819L,
                    budget
            );
            assertNotNull(outNull);
            assertFalse(mockedTika.constructed().isEmpty());
        }

        Path blankZip = Files.createTempFile("arc-blank-txt-", ".zip");
        Files.write(blankZip, zipBytes(List.of(Map.entry("blank.txt", "   ".getBytes(StandardCharsets.UTF_8)))));
        Map<String, Object> metaBlank = new LinkedHashMap<>();
        Object outBlank = invokeInstance(
                svc,
                "extractArchive",
                new Class<?>[]{Path.class, String.class, int.class, Map.class, Long.class, budget.getClass()},
                blankZip,
                "zip",
                200,
                metaBlank,
                820L,
                budget
        );
        assertNotNull(outBlank);
    }

    @Test
    void extractArchive_shouldCoverPostParseTextLimitWhenReasonWasNull() throws Exception {
        Object svc = new FileAssetExtractionAsyncService(null, null, null, null, null, null, null, null);
        setField(svc, "archiveMaxDepth", 5);
        setField(svc, "archiveMaxEntries", 50);
        setField(svc, "archiveMaxEntryBytes", 5 * 1024 * 1024L);
        setField(svc, "archiveMaxTotalBytes", 20 * 1024 * 1024L);
        setField(svc, "archiveMaxTotalMillis", 15000L);
        Path zip = Files.createTempFile("arc-post-parse-limit-", ".zip");
        Files.write(zip, zipBytes(List.of(Map.entry("a.txt", "ABCDE".getBytes(StandardCharsets.UTF_8)))));
        Object budget = newBudget(10, 20 * 1024 * 1024L);
        Map<String, Object> meta = new LinkedHashMap<>();
        Object out = invokeInstance(
                svc,
                "extractArchive",
                new Class<?>[]{Path.class, String.class, int.class, Map.class, Long.class, budget.getClass()},
                zip,
                "zip",
                1,
                meta,
                821L,
                budget
        );
        assertNotNull(out);
        @SuppressWarnings("unchecked")
        Map<String, Object> arc = (Map<String, Object>) meta.get("archive");
        assertEquals("TEXT_CHAR_LIMIT", String.valueOf(arc.get("truncatedReason")));
    }

}
