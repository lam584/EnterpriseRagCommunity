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
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
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
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDResources;
import org.apache.pdfbox.pdmodel.graphics.PDXObject;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.apache.tika.Tika;
import org.apache.tika.parser.AutoDetectParser;
import org.apache.tika.parser.ParseContext;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.mockito.MockedConstruction;
import org.mockito.MockedStatic;
import org.mockito.Mockito;


class FileAssetExtractionAsyncServiceUtilityBranchPart3Test extends FileAssetExtractionAsyncServiceUtilityBranchTest {
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

        // mobi now returns text via tika instead of throwing
        String mobi = String.valueOf(invokeInstance(
                svc,
                "extractEntryBytesAsText",
                new Class<?>[]{String.class, String.class, byte[].class, int.class},
                "a.mobi",
                "mobi",
                "not-mobi".getBytes(StandardCharsets.UTF_8),
                200
        ));
        assertNotNull(mobi);
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
        String plain = String.valueOf(invokeInstance(
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
        // Plain text fallback returns the text content (may be processed by Tika)
        assertNotNull(plain);

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

        // When ArchiveStreamFactory.createArchiveInputStream throws, it falls through to plain text extraction
        Object cCreateFail = newInner("ArchiveCounters", new Class<?>[]{});
        try (MockedConstruction<ArchiveStreamFactory> ignored = Mockito.mockConstruction(ArchiveStreamFactory.class, (mock, ctx) ->
                Mockito.when(mock.createArchiveInputStream(Mockito.anyString(), Mockito.any(InputStream.class)))
                        .thenThrow(new ArchiveException("mock-create-fail"))
        )) {
            String out = String.valueOf(invokeInstance(
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
            assertNotNull(out);
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
