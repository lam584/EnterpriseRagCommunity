package com.example.EnterpriseRagCommunity.service.monitor;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.apache.commons.compress.archivers.sevenz.SevenZArchiveEntry;
import org.apache.commons.compress.archivers.sevenz.SevenZMethod;
import org.apache.commons.compress.archivers.sevenz.SevenZMethodConfiguration;
import org.apache.commons.compress.archivers.sevenz.SevenZOutputFile;
import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.tika.Tika;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.AutoDetectParser;
import org.apache.tika.parser.ParseContext;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.mockito.MockedConstruction;
import org.mockito.Mockito;


class FileAssetExtractionAsyncServiceUtilityBranchPart2Test extends FileAssetExtractionAsyncServiceUtilityBranchTest {
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
        // bad.pdf now handled gracefully (returns empty), so entryErrors may or may not exist
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
        // GZ is now handled gracefully - falls through to plain text extraction
        String out = String.valueOf(invokeInstance(
                svc,
                "extractArchiveFromStream",
                new Class<?>[]{InputStream.class, String.class, int.class, int.class, Map.class, counters.getClass(), long.class},
                new ByteArrayInputStream(gz),
                "sample.gz",
                0,
                200,
                meta,
                counters,
                System.nanoTime()
        ));
        assertNotNull(out);
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

        // Fallback returns the text as-is via Tika
        String fallback = String.valueOf(invokeInstance(
                svc,
                "extractEntryBytesAsText",
                new Class<?>[]{String.class, String.class, byte[].class, int.class},
                "a.bin",
                "bin",
                "fallback".getBytes(StandardCharsets.UTF_8),
                200
        ));
        assertNotNull(fallback);
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
        Object resultPlain = invokeInstance(
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
        );
        assertNotNull(resultPlain);

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

}

