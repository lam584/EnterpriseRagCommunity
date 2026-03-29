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

    protected static Object invokeStatic(String name, Class<?>[] types, Object... args) {
        try {
            Method m = FileAssetExtractionAsyncService.class.getDeclaredMethod(name, types);
            m.setAccessible(true);
            return m.invoke(null, args);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    protected static Object invokeInstance(Object target, String name, Class<?>[] types, Object... args) {
        try {
            Method m = target.getClass().getDeclaredMethod(name, types);
            m.setAccessible(true);
            return m.invoke(target, args);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    protected static Object newInner(String simpleName, Class<?>[] ctorTypes, Object... args) {
        try {
            Class<?> c = Class.forName(FileAssetExtractionAsyncService.class.getName() + "$" + simpleName);
            Constructor<?> ctor = c.getDeclaredConstructor(ctorTypes);
            ctor.setAccessible(true);
            return ctor.newInstance(args);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    protected static void setField(Object target, String name, Object value) {
        try {
            var f = target.getClass().getDeclaredField(name);
            f.setAccessible(true);
            f.set(target, value);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    protected static Object getField(Object target, String name) {
        try {
            var f = target.getClass().getDeclaredField(name);
            f.setAccessible(true);
            return f.get(target);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    protected static byte[] zipBytes(List<Map.Entry<String, byte[]>> entries) throws Exception {
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

    protected static byte[] sevenZBytes(List<Map.Entry<String, byte[]>> entries) throws Exception {
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

    protected static byte[] gzipBytes(byte[] src) throws Exception {
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

    protected static Object newBudget(int maxCount, long maxTotalBytes) {
        return newInner("ImageBudget", new Class<?>[]{int.class, long.class}, maxCount, maxTotalBytes);
    }

    protected static byte[] tinyPngBytes() throws Exception {
        BufferedImage img = new BufferedImage(2, 2, BufferedImage.TYPE_INT_RGB);
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ImageIO.write(img, "png", baos);
        return baos.toByteArray();
    }

    protected static Path createPdfWithImage() throws Exception {
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

    protected static Path docxWithImagePath() throws Exception {
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

    protected static Path xlsxWithImagePath() throws Exception {
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

    protected static Path pptxWithImagePath() throws Exception {
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

    protected static Path pptWithImagePath() throws Exception {
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

    protected static Path epubWithImagePath() throws Exception {
        Path p = Files.createTempFile("with-img-", ".epub");
        byte[] png = tinyPngBytes();
        byte[] epub = zipBytes(List.of(
                Map.entry("images/cover.png", png),
                Map.entry("text/ch1.txt", "chapter".getBytes(StandardCharsets.UTF_8))
        ));
        Files.write(p, epub);
        return p;
    }

}
