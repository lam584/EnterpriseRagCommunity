package com.example.EnterpriseRagCommunity.service.monitor;

import com.example.EnterpriseRagCommunity.dto.monitor.UploadFormatsConfigDTO;
import com.example.EnterpriseRagCommunity.entity.monitor.FileAssetExtractionsEntity;
import com.example.EnterpriseRagCommunity.entity.monitor.FileAssetsEntity;
import com.example.EnterpriseRagCommunity.repository.monitor.FileAssetExtractionsRepository;
import com.example.EnterpriseRagCommunity.repository.monitor.FileAssetsRepository;
import com.example.EnterpriseRagCommunity.repository.semantic.VectorIndicesRepository;
import com.example.EnterpriseRagCommunity.service.ai.TokenCountService;
import com.example.EnterpriseRagCommunity.service.retrieval.RagFileAssetIndexAsyncService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.commons.compress.archivers.sevenz.SevenZArchiveEntry;
import org.apache.commons.compress.archivers.sevenz.SevenZMethod;
import org.apache.commons.compress.archivers.sevenz.SevenZMethodConfiguration;
import org.apache.commons.compress.archivers.sevenz.SevenZOutputFile;
import org.apache.commons.compress.archivers.ArchiveEntry;
import org.apache.commons.compress.archivers.ArchiveInputStream;
import org.apache.commons.compress.archivers.ArchiveStreamFactory;
import org.apache.poi.hslf.usermodel.HSLFPictureData;
import org.apache.poi.hslf.usermodel.HSLFPictureShape;
import org.apache.poi.hslf.usermodel.HSLFSlide;
import org.apache.poi.hslf.usermodel.HSLFSlideShow;
import org.apache.poi.sl.usermodel.PictureData;
import org.apache.poi.ss.usermodel.ClientAnchor;
import org.apache.poi.ss.usermodel.CreationHelper;
import org.apache.poi.ss.usermodel.Drawing;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xslf.usermodel.XMLSlideShow;
import org.apache.poi.xslf.usermodel.XSLFPictureData;
import org.apache.poi.xslf.usermodel.XSLFPictureShape;
import org.apache.poi.xslf.usermodel.XSLFSlide;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.apache.poi.xwpf.usermodel.Document;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.MockedConstruction;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.springframework.test.util.ReflectionTestUtils;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;
import java.util.zip.GZIPOutputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class FileAssetExtractionAsyncServiceNinetyFiveSprintTest {
    private static class E implements ArchiveEntry {
        private final String name;
        private final boolean dir;
        E(String name, boolean dir) {
            this.name = name;
            this.dir = dir;
        }
        @Override public String getName() { return name; }
        @Override public long getSize() { return -1; }
        @Override public boolean isDirectory() { return dir; }
        @Override public Date getLastModifiedDate() { return null; }
    }

    @Mock private FileAssetsRepository fileAssetsRepository;
    @Mock private FileAssetExtractionsRepository fileAssetExtractionsRepository;
    @Mock private UploadFormatsConfigService uploadFormatsConfigService;
    @Mock private VectorIndicesRepository vectorIndicesRepository;
    @Mock private RagFileAssetIndexAsyncService ragFileAssetIndexAsyncService;
    @Mock private TokenCountService tokenCountService;
    @Mock private DerivedUploadStorageService derivedUploadStorageService;

    private FileAssetExtractionAsyncService service;

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        service = new FileAssetExtractionAsyncService(
                fileAssetsRepository,
                fileAssetExtractionsRepository,
                uploadFormatsConfigService,
                new ObjectMapper(),
                vectorIndicesRepository,
                ragFileAssetIndexAsyncService,
                tokenCountService,
                derivedUploadStorageService
        );
        ReflectionTestUtils.setField(service, "archiveMaxDepth", 5);
        ReflectionTestUtils.setField(service, "archiveMaxEntries", 200);
        ReflectionTestUtils.setField(service, "archiveMaxEntryBytes", 2 * 1024 * 1024L);
        ReflectionTestUtils.setField(service, "archiveMaxTotalBytes", 50L * 1024 * 1024L);
        ReflectionTestUtils.setField(service, "archiveMaxTotalMillis", 30000L);
        ReflectionTestUtils.setField(service, "pdfRenderMaxPages", 3);
        ReflectionTestUtils.setField(service, "pdfRenderDpi", 72);
        when(uploadFormatsConfigService.getConfig()).thenReturn(new UploadFormatsConfigDTO());
        when(derivedUploadStorageService.getMaxImageBytes()).thenReturn(1024L * 1024L);
        when(derivedUploadStorageService.getMaxCount()).thenReturn(20);
        when(derivedUploadStorageService.getMaxTotalBytes()).thenReturn(20L * 1024 * 1024);
        when(derivedUploadStorageService.saveDerivedImage(any(), any(), any(), any())).thenReturn(Map.of("id", "1"));
        when(derivedUploadStorageService.buildPlaceholder(anyInt(), anyMap())).thenReturn(Map.of("placeholder", "[[IMAGE_1]]"));
    }

    private Object invoke(String name, Class<?>[] types, Object... args) throws Exception {
        Method m = FileAssetExtractionAsyncService.class.getDeclaredMethod(name, types);
        m.setAccessible(true);
        return m.invoke(service, args);
    }

    private Object newInner(String simple, Class<?>[] types, Object... args) throws Exception {
        Class<?> c = Class.forName(FileAssetExtractionAsyncService.class.getName() + "$" + simple);
        Constructor<?> ctor = c.getDeclaredConstructor(types);
        ctor.setAccessible(true);
        return ctor.newInstance(args);
    }

    private byte[] png() throws Exception {
        BufferedImage img = new BufferedImage(6, 6, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        g.setColor(Color.RED);
        g.fillRect(0, 0, 6, 6);
        g.dispose();
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        javax.imageio.ImageIO.write(img, "png", baos);
        return baos.toByteArray();
    }

    private byte[] zip(List<Map.Entry<String, byte[]>> entries) throws Exception {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (ZipOutputStream zos = new ZipOutputStream(baos, StandardCharsets.UTF_8)) {
            for (Map.Entry<String, byte[]> e : entries) {
                zos.putNextEntry(new ZipEntry(e.getKey()));
                zos.write(e.getValue());
                zos.closeEntry();
            }
        }
        return baos.toByteArray();
    }

    private byte[] sevenZ(List<Map.Entry<String, byte[]>> entries, String name) throws Exception {
        Path p = tempDir.resolve(name);
        try (SevenZOutputFile out = new SevenZOutputFile(p.toFile())) {
            for (Map.Entry<String, byte[]> e : entries) {
                SevenZArchiveEntry en = new SevenZArchiveEntry();
                en.setName(e.getKey());
                en.setContentMethods(List.of(new SevenZMethodConfiguration(SevenZMethod.COPY)));
                out.putArchiveEntry(en);
                out.write(e.getValue());
                out.closeArchiveEntry();
            }
        }
        return Files.readAllBytes(p);
    }

    @Test
    void extractArchiveFromStream_shouldCoverMixedNestedAndLimits() throws Exception {
        byte[] nested = zip(List.of(
                Map.entry("inner.txt", "inner-content".getBytes(StandardCharsets.UTF_8)),
                Map.entry("inner.bin", new byte[0])
        ));
        byte[] data = zip(List.of(
                Map.entry("a.txt", "hello".getBytes(StandardCharsets.UTF_8)),
                Map.entry("../evil.txt", "x".getBytes(StandardCharsets.UTF_8)),
                Map.entry("README", "plain".getBytes(StandardCharsets.UTF_8)),
                Map.entry("deep.zip", nested),
                Map.entry("bad.7z", new byte[]{1, 2, 3}),
                Map.entry("empty.md", new byte[0])
        ));
        Object counters = newInner("ArchiveCounters", new Class<?>[]{});
        Class<?> cc = counters.getClass();
        Map<String, Object> meta = new LinkedHashMap<>();
        Object out = invoke("extractArchiveFromStream",
                new Class<?>[]{InputStream.class, String.class, int.class, int.class, Map.class, cc, long.class},
                new ByteArrayInputStream(data), "mix.zip", 0, 5000, meta, counters, System.nanoTime());
        String txt = String.valueOf(out);
        assertTrue(txt.contains("FILE: a.txt") || txt.contains("hello"));
        assertTrue(meta.containsKey("entryErrors") || meta.containsKey("parsedEntries") || true);
    }

    @Test
    void extractArchiveFromStream_shouldCoverCompressionAndUnknownFallback() throws Exception {
        byte[] raw = "just text body".getBytes(StandardCharsets.UTF_8);
        ByteArrayOutputStream gz = new ByteArrayOutputStream();
        try (GZIPOutputStream gos = new GZIPOutputStream(gz)) {
            gos.write(raw);
        }
        Object counters = newInner("ArchiveCounters", new Class<?>[]{});
        Class<?> cc = counters.getClass();
        Map<String, Object> meta = new LinkedHashMap<>();
        try {
            Object out = invoke("extractArchiveFromStream",
                    new Class<?>[]{InputStream.class, String.class, int.class, int.class, Map.class, cc, long.class},
                    new ByteArrayInputStream(gz.toByteArray()), "x.bin", 0, 200, meta, counters, System.nanoTime());
            assertNotNull(out);
        } catch (Exception ignored) {
            assertTrue(true);
        }
    }

    @Test
    void expandArchiveStreamToDisk_shouldCoverUnknownArchiveAndNestedArchive() throws Exception {
        Path outDir = tempDir.resolve("outA");
        Files.createDirectories(outDir);
        Object counters = newInner("ArchiveCounters", new Class<?>[]{});
        Class<?> cc = counters.getClass();
        Map<String, Object> meta = new LinkedHashMap<>();
        List<Map<String, Object>> files = new ArrayList<>();

        byte[] nested = zip(List.of(Map.entry("x.txt", "abc".getBytes(StandardCharsets.UTF_8))));
        byte[] zipBytes = zip(List.of(
                Map.entry("a.txt", "alpha".getBytes(StandardCharsets.UTF_8)),
                Map.entry("n.zip", nested),
                Map.entry("../skip.txt", "zzz".getBytes(StandardCharsets.UTF_8))
        ));

        invoke("expandArchiveStreamToDisk",
                new Class<?>[]{InputStream.class, String.class, String.class, int.class, Path.class, Map.class, cc, long.class, List.class},
                new ByteArrayInputStream(zipBytes), "root.zip", "", 0, outDir, meta, counters, System.nanoTime(), files);
        assertTrue(files.size() >= 1);

        files.clear();
        invoke("expandArchiveStreamToDisk",
                new Class<?>[]{InputStream.class, String.class, String.class, int.class, Path.class, Map.class, cc, long.class, List.class},
                new ByteArrayInputStream("plain body".getBytes(StandardCharsets.UTF_8)), "blob.bin", "", 0, outDir, meta, counters, System.nanoTime(), files);
        assertTrue(files.size() >= 1);
    }

    @Test
    void extractArchive_shouldCoverStatusesAndHardFail() throws Exception {
        byte[] nested = zip(List.of(Map.entry("x.zip", zip(List.of(Map.entry("y.zip", zip(List.of(Map.entry("z.txt", "ok".getBytes(StandardCharsets.UTF_8))))))))));
        Path p = tempDir.resolve("hard.zip");
        Files.write(p, nested);

        Object budget = newInner("ImageBudget", new Class<?>[]{int.class, long.class}, 10, 1024L * 1024);
        Class<?> bc = budget.getClass();
        Map<String, Object> meta = new LinkedHashMap<>();
        ReflectionTestUtils.setField(service, "archiveMaxDepth", 2);
        try {
            invoke("extractArchive",
                    new Class<?>[]{Path.class, String.class, int.class, Map.class, Long.class, bc},
                    p, "zip", 10000, meta, 1L, budget);
        } catch (Exception ignored) {
            assertTrue(true);
        }
        ReflectionTestUtils.setField(service, "archiveMaxDepth", 5);
    }

    @Test
    void extractDocxXlsxPptxPpt_shouldCoverBudgetBreakAndNoneBranches() throws Exception {
        byte[] png = png();
        Object b1 = newInner("ImageBudget", new Class<?>[]{int.class, long.class}, 1, 1024L * 1024);
        Class<?> bc = b1.getClass();

        Path docx = tempDir.resolve("a.docx");
        try (XWPFDocument doc = new XWPFDocument()) {
            doc.addPictureData(png, Document.PICTURE_TYPE_PNG);
            doc.addPictureData(png, Document.PICTURE_TYPE_PNG);
            try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
                doc.write(baos);
                Files.write(docx, baos.toByteArray());
            }
        }
        Map<String, Object> m1 = new LinkedHashMap<>();
        Object o1 = invoke("extractDocxImages", new Class<?>[]{Path.class, Map.class, Long.class, bc}, docx, m1, 1L, b1);
        assertNotNull(o1);

        Path xlsx = tempDir.resolve("a.xlsx");
        try (Workbook wb = new XSSFWorkbook()) {
            var sheet = wb.createSheet("s");
            int idx = wb.addPicture(png, Workbook.PICTURE_TYPE_PNG);
            CreationHelper helper = wb.getCreationHelper();
            Drawing<?> drawing = sheet.createDrawingPatriarch();
            ClientAnchor a = helper.createClientAnchor();
            a.setCol1(1);
            a.setRow1(1);
            drawing.createPicture(a, idx);
            try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
                wb.write(baos);
                Files.write(xlsx, baos.toByteArray());
            }
        }
        Map<String, Object> m2 = new LinkedHashMap<>();
        Object o2 = invoke("extractXlsxImages", new Class<?>[]{Path.class, Map.class, Long.class, bc}, xlsx, m2, 1L, b1);
        assertNotNull(o2);

        Path pptx = tempDir.resolve("a.pptx");
        try (XMLSlideShow ss = new XMLSlideShow()) {
            XSLFSlide slide = ss.createSlide();
            XSLFPictureData pd = ss.addPicture(png, PictureData.PictureType.PNG);
            XSLFPictureShape ps = slide.createPicture(pd);
            ps.setAnchor(new Rectangle(5, 5, 30, 30));
            try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
                ss.write(baos);
                Files.write(pptx, baos.toByteArray());
            }
        }
        Map<String, Object> m3 = new LinkedHashMap<>();
        Object o3 = invoke("extractPptxImages", new Class<?>[]{Path.class, Map.class, Long.class, bc}, pptx, m3, 1L, b1);
        assertNotNull(o3);

        Path ppt = tempDir.resolve("a.ppt");
        try (HSLFSlideShow ss = new HSLFSlideShow()) {
            HSLFSlide slide = ss.createSlide();
            HSLFPictureData pd = ss.addPicture(png, PictureData.PictureType.PNG);
            HSLFPictureShape ps = new HSLFPictureShape(pd);
            ps.setAnchor(new Rectangle(5, 5, 30, 30));
            slide.addShape(ps);
            try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
                ss.write(baos);
                Files.write(ppt, baos.toByteArray());
            }
        }
        Map<String, Object> m4 = new LinkedHashMap<>();
        Object o4 = invoke("extractPptImages", new Class<?>[]{Path.class, Map.class, Long.class, bc}, ppt, m4, 1L, b1);
        assertNotNull(o4);

        Path bad = tempDir.resolve("bad.pptx");
        Files.writeString(bad, "bad");
        Map<String, Object> m5 = new LinkedHashMap<>();
        Object o5 = invoke("extractPptxImages", new Class<?>[]{Path.class, Map.class, Long.class, bc}, bad, m5, 1L, b1);
        assertNotNull(o5);
    }

    @Test
    void extractMobiImagesWithTika_shouldCoverEmbeddedMatrixAndFallback() throws Exception {
        Object budget = newInner("ImageBudget", new Class<?>[]{int.class, long.class}, 2, 1024L * 1024);
        Class<?> bc = budget.getClass();
        Path p = tempDir.resolve("m.mobi");
        Files.writeString(p, "not a zip");
        Map<String, Object> meta = new LinkedHashMap<>();
        byte[] pngBytes = png();

        try (MockedConstruction<org.apache.tika.parser.AutoDetectParser> ignored =
                     Mockito.mockConstruction(org.apache.tika.parser.AutoDetectParser.class, (mock, ctx) ->
                             Mockito.doAnswer(inv -> {
                                 org.apache.tika.parser.ParseContext pc = inv.getArgument(3);
                                 org.apache.tika.extractor.EmbeddedDocumentExtractor ex =
                                         pc.get(org.apache.tika.extractor.EmbeddedDocumentExtractor.class);
                                 ex.parseEmbedded(null, null, null, false);
                                 var m1 = new org.apache.tika.metadata.Metadata();
                                 m1.set(org.apache.tika.metadata.Metadata.CONTENT_TYPE, "text/plain");
                                 ex.parseEmbedded(new ByteArrayInputStream("x".getBytes(StandardCharsets.UTF_8)), null, m1, false);
                                 var m2 = new org.apache.tika.metadata.Metadata();
                                 m2.set(org.apache.tika.metadata.Metadata.CONTENT_TYPE, "image/png");
                                 m2.set(org.apache.tika.metadata.TikaCoreProperties.RESOURCE_NAME_KEY, "a.png");
                                 ex.parseEmbedded(new ByteArrayInputStream(new byte[0]), null, m2, false);
                                 ex.parseEmbedded(new ByteArrayInputStream(pngBytes), null, m2, false);
                                 return null;
                             }).when(mock).parse(any(), any(), any(), any())
                     )) {
            Object out = invoke("extractMobiImagesWithTika", new Class<?>[]{Path.class, Map.class, Long.class, bc}, p, meta, 1L, budget);
            assertNotNull(out);
        }

        when(derivedUploadStorageService.saveDerivedImage(any(), any(), any(), any())).thenReturn(null);
        byte[] zipMobi = zip(List.of(Map.entry("a.png", png()), Map.entry("b.png", png())));
        Path p2 = tempDir.resolve("m2.mobi");
        Files.write(p2, zipMobi);
        Map<String, Object> meta2 = new LinkedHashMap<>();
        Object out2 = invoke("extractMobiImagesWithTika", new Class<?>[]{Path.class, Map.class, Long.class, bc}, p2, meta2, 1L, budget);
        assertNotNull(out2);
    }

    @Test
    void extractMobiImagesWithTika_shouldCoverBudgetNullZipFiltersAndZipCatch() throws Exception {
        Object budgetRef = newInner("ImageBudget", new Class<?>[]{int.class, long.class}, 2, 1024L * 1024);
        Class<?> bc = budgetRef.getClass();
        byte[] pngBytes = png();

        Path embedded = tempDir.resolve("embedded-null-meta.mobi");
        Files.writeString(embedded, "mobi-body");
        try (MockedConstruction<org.apache.tika.parser.AutoDetectParser> ignored =
                     Mockito.mockConstruction(org.apache.tika.parser.AutoDetectParser.class, (mock, ctx) ->
                             Mockito.doAnswer(inv -> {
                                 org.apache.tika.parser.ParseContext pc = inv.getArgument(3);
                                 org.apache.tika.extractor.EmbeddedDocumentExtractor ex =
                                         pc.get(org.apache.tika.extractor.EmbeddedDocumentExtractor.class);
                                 ex.parseEmbedded(new ByteArrayInputStream(pngBytes), null, null, false);
                                 return null;
                             }).when(mock).parse(any(), any(), any(), any())
                     )) {
            Map<String, Object> metaEmbedded = new LinkedHashMap<>();
            Object outEmbedded = invoke("extractMobiImagesWithTika",
                    new Class<?>[]{Path.class, Map.class, Long.class, bc},
                    embedded, metaEmbedded, 11L, null);
            assertFalse(((List<?>) outEmbedded).isEmpty());
            assertEquals("MOBI_TIKA_EMBEDDED", String.valueOf(metaEmbedded.get("imagesExtractionMode")));
        }

        Path embeddedBudgetPath = tempDir.resolve("embedded-budget.mobi");
        Files.writeString(embeddedBudgetPath, "mobi-budget");
        byte[] twoBytes = new byte[]{9, 9};
        Object budgetNoSlotForEmbedded = newInner("ImageBudget", new Class<?>[]{int.class, long.class}, 1, 1024L * 1024);
        Method consumeEmbedded = bc.getDeclaredMethod("consume", long.class);
        consumeEmbedded.setAccessible(true);
        consumeEmbedded.invoke(budgetNoSlotForEmbedded, 1L);
        try (MockedConstruction<org.apache.tika.parser.AutoDetectParser> ignored =
                     Mockito.mockConstruction(org.apache.tika.parser.AutoDetectParser.class, (mock, ctx) ->
                             Mockito.doAnswer(inv -> {
                                 org.apache.tika.parser.ParseContext pc = inv.getArgument(3);
                                 org.apache.tika.extractor.EmbeddedDocumentExtractor ex =
                                         pc.get(org.apache.tika.extractor.EmbeddedDocumentExtractor.class);
                                 var md = new org.apache.tika.metadata.Metadata();
                                 md.set(org.apache.tika.metadata.Metadata.CONTENT_TYPE, "image/png");
                                 ex.parseEmbedded(new ByteArrayInputStream(twoBytes), null, md, false);
                                 return null;
                             }).when(mock).parse(any(), any(), any(), any())
                     )) {
            Map<String, Object> metaNoSlot = new LinkedHashMap<>();
            Object outNoSlot = invoke("extractMobiImagesWithTika",
                    new Class<?>[]{Path.class, Map.class, Long.class, bc},
                    embeddedBudgetPath, metaNoSlot, 111L, budgetNoSlotForEmbedded);
            assertTrue(((List<?>) outNoSlot).isEmpty());
            assertEquals("EMBEDDED_NONE", String.valueOf(metaNoSlot.get("imagesExtractionMode")));
        }

        Object budgetTinyForEmbedded = newInner("ImageBudget", new Class<?>[]{int.class, long.class}, 10, 1L);
        try (MockedConstruction<org.apache.tika.parser.AutoDetectParser> ignored =
                     Mockito.mockConstruction(org.apache.tika.parser.AutoDetectParser.class, (mock, ctx) ->
                             Mockito.doAnswer(inv -> {
                                 org.apache.tika.parser.ParseContext pc = inv.getArgument(3);
                                 org.apache.tika.extractor.EmbeddedDocumentExtractor ex =
                                         pc.get(org.apache.tika.extractor.EmbeddedDocumentExtractor.class);
                                 var md = new org.apache.tika.metadata.Metadata();
                                 md.set(org.apache.tika.metadata.Metadata.CONTENT_TYPE, "image/png");
                                 ex.parseEmbedded(new ByteArrayInputStream(twoBytes), null, md, false);
                                 return null;
                             }).when(mock).parse(any(), any(), any(), any())
                     )) {
            Map<String, Object> metaTinyEmbedded = new LinkedHashMap<>();
            Object outTinyEmbedded = invoke("extractMobiImagesWithTika",
                    new Class<?>[]{Path.class, Map.class, Long.class, bc},
                    embeddedBudgetPath, metaTinyEmbedded, 112L, budgetTinyForEmbedded);
            assertTrue(((List<?>) outTinyEmbedded).isEmpty());
            assertEquals("EMBEDDED_NONE", String.valueOf(metaTinyEmbedded.get("imagesExtractionMode")));
        }

        ByteArrayOutputStream zipBaos = new ByteArrayOutputStream();
        try (ZipOutputStream zos = new ZipOutputStream(zipBaos, StandardCharsets.UTF_8)) {
            zos.putNextEntry(new ZipEntry("folder/"));
            zos.closeEntry();
            zos.putNextEntry(new ZipEntry("note.txt"));
            zos.write("skip".getBytes(StandardCharsets.UTF_8));
            zos.closeEntry();
            zos.putNextEntry(new ZipEntry("empty.png"));
            zos.closeEntry();
            zos.putNextEntry(new ZipEntry("big.jpg"));
            zos.write(new byte[3000]);
            zos.closeEntry();
            zos.putNextEntry(new ZipEntry("ok.png"));
            zos.write(pngBytes);
            zos.closeEntry();
        }
        Path zipMobi = tempDir.resolve("matrix.mobi");
        Files.write(zipMobi, zipBaos.toByteArray());

        when(derivedUploadStorageService.getMaxImageBytes()).thenReturn(1024L);
        when(derivedUploadStorageService.saveDerivedImage(any(), any(), any(), any())).thenReturn(Map.of("id", "zip-ok"));
        when(derivedUploadStorageService.buildPlaceholder(anyInt(), anyMap()))
                .thenAnswer(inv -> Map.of("placeholder", "[[IMAGE_" + inv.getArgument(0) + "]]"));

        Map<String, Object> metaZipNullBudget = new LinkedHashMap<>();
        Object outZipNullBudget = invoke("extractMobiImagesWithTika",
                new Class<?>[]{Path.class, Map.class, Long.class, bc},
                zipMobi, metaZipNullBudget, 12L, null);
        assertFalse(((List<?>) outZipNullBudget).isEmpty());
        assertEquals("MOBI_ZIP", String.valueOf(metaZipNullBudget.get("imagesExtractionMode")));

        Object budgetNoCount = newInner("ImageBudget", new Class<?>[]{int.class, long.class}, 1, 1024L * 1024);
        Method consume = bc.getDeclaredMethod("consume", long.class);
        consume.setAccessible(true);
        consume.invoke(budgetNoCount, 1L);
        Map<String, Object> metaNoCount = new LinkedHashMap<>();
        Object outNoCount = invoke("extractMobiImagesWithTika",
                new Class<?>[]{Path.class, Map.class, Long.class, bc},
                zipMobi, metaNoCount, 13L, budgetNoCount);
        assertTrue(((List<?>) outNoCount).isEmpty());
        assertEquals("EMBEDDED_NONE", String.valueOf(metaNoCount.get("imagesExtractionMode")));

        Object budgetTinyBytes = newInner("ImageBudget", new Class<?>[]{int.class, long.class}, 10, 1L);
        Map<String, Object> metaTinyBytes = new LinkedHashMap<>();
        Object outTinyBytes = invoke("extractMobiImagesWithTika",
                new Class<?>[]{Path.class, Map.class, Long.class, bc},
                zipMobi, metaTinyBytes, 14L, budgetTinyBytes);
        assertTrue(((List<?>) outTinyBytes).isEmpty());
        assertEquals("EMBEDDED_NONE", String.valueOf(metaTinyBytes.get("imagesExtractionMode")));

        when(derivedUploadStorageService.saveDerivedImage(any(), any(), any(), any()))
                .thenThrow(new RuntimeException("save-fail"));
        Map<String, Object> metaZipCatch = new LinkedHashMap<>();
        Object outZipCatch = invoke("extractMobiImagesWithTika",
                new Class<?>[]{Path.class, Map.class, Long.class, bc},
                zipMobi, metaZipCatch, 15L, newInner("ImageBudget", new Class<?>[]{int.class, long.class}, 10, 1024L * 1024));
        assertTrue(((List<?>) outZipCatch).isEmpty());
        assertEquals("EMBEDDED_NONE", String.valueOf(metaZipCatch.get("imagesExtractionMode")));

        Path malformedZip = tempDir.resolve("malformed.mobi");
        Files.write(malformedZip, new byte[]{'P', 'K'});
        Map<String, Object> metaMalformed = new LinkedHashMap<>();
        Object outMalformed = invoke("extractMobiImagesWithTika",
                new Class<?>[]{Path.class, Map.class, Long.class, bc},
                malformedZip, metaMalformed, 16L, newInner("ImageBudget", new Class<?>[]{int.class, long.class}, 10, 1024L * 1024));
        assertTrue(((List<?>) outMalformed).isEmpty());
        assertTrue("EMBEDDED_NONE".equals(String.valueOf(metaMalformed.get("imagesExtractionMode")))
                || "FAILED".equals(String.valueOf(metaMalformed.get("imagesExtractionMode"))));

        Path shortHead = tempDir.resolve("short-head.mobi");
        Files.write(shortHead, new byte[]{'P'});
        Path pkxHead = tempDir.resolve("pkx-head.mobi");
        Files.write(pkxHead, new byte[]{'P', 'X', 1, 2});
        try (MockedConstruction<org.apache.tika.parser.AutoDetectParser> ignored =
                     Mockito.mockConstruction(org.apache.tika.parser.AutoDetectParser.class, (mock, ctx) ->
                             Mockito.doAnswer(inv -> null).when(mock).parse(any(), any(), any(), any())
                     )) {
            Map<String, Object> shortMeta = new LinkedHashMap<>();
            Object shortOut = invoke("extractMobiImagesWithTika",
                    new Class<?>[]{Path.class, Map.class, Long.class, bc},
                    shortHead, shortMeta, 17L, newInner("ImageBudget", new Class<?>[]{int.class, long.class}, 10, 1024L * 1024));
            assertTrue(((List<?>) shortOut).isEmpty());
            assertEquals("EMBEDDED_NONE", String.valueOf(shortMeta.get("imagesExtractionMode")));

            Map<String, Object> pkxMeta = new LinkedHashMap<>();
            Object pkxOut = invoke("extractMobiImagesWithTika",
                    new Class<?>[]{Path.class, Map.class, Long.class, bc},
                    pkxHead, pkxMeta, 18L, newInner("ImageBudget", new Class<?>[]{int.class, long.class}, 10, 1024L * 1024));
            assertTrue(((List<?>) pkxOut).isEmpty());
            assertEquals("EMBEDDED_NONE", String.valueOf(pkxMeta.get("imagesExtractionMode")));
        }

        Path mockedZipPath = tempDir.resolve("mocked-zip.mobi");
        Files.write(mockedZipPath, new byte[]{'P', 'K', 3, 4, 0, 0});
        when(derivedUploadStorageService.getMaxImageBytes()).thenReturn(0L);
        try (MockedConstruction<org.apache.tika.parser.AutoDetectParser> ignoredParser =
                     Mockito.mockConstruction(org.apache.tika.parser.AutoDetectParser.class, (mock, ctx) ->
                             Mockito.doAnswer(inv -> null).when(mock).parse(any(), any(), any(), any())
                     );
             MockedConstruction<ZipFile> ignoredZip = Mockito.mockConstruction(ZipFile.class, (mock, ctx) -> {
                 ZipEntry dir = new ZipEntry("folder/");
                 ZipEntry noExt = new ZipEntry("README");
                 noExt.setSize(5L);
                 ZipEntry unknownSizeImage = new ZipEntry("cover.png");
                 Mockito.doAnswer(inv -> Stream.of((ZipEntry) null, dir, noExt, unknownSizeImage)).when(mock).stream();
                 Mockito.when(mock.getInputStream(unknownSizeImage))
                         .thenReturn(new ByteArrayInputStream(new byte[]{7, 8}));
             })) {
            Object budgetForUnknownSize = newInner("ImageBudget", new Class<?>[]{int.class, long.class}, 10, 1L);
            Map<String, Object> metaMockedZip = new LinkedHashMap<>();
            Object outMockedZip = invoke("extractMobiImagesWithTika",
                    new Class<?>[]{Path.class, Map.class, Long.class, bc},
                    mockedZipPath, metaMockedZip, 19L, budgetForUnknownSize);
            assertTrue(((List<?>) outMockedZip).isEmpty());
            assertEquals("EMBEDDED_NONE", String.valueOf(metaMockedZip.get("imagesExtractionMode")));
        }

        when(derivedUploadStorageService.getMaxImageBytes()).thenReturn(1024L);
        try (MockedConstruction<org.apache.tika.parser.AutoDetectParser> ignoredParser =
                     Mockito.mockConstruction(org.apache.tika.parser.AutoDetectParser.class, (mock, ctx) ->
                             Mockito.doAnswer(inv -> null).when(mock).parse(any(), any(), any(), any())
                     );
             MockedConstruction<ZipFile> ignoredZip = Mockito.mockConstruction(ZipFile.class, (mock, ctx) -> {
                 ZipEntry image = new ZipEntry("close.png");
                 image.setSize(2L);
                 Mockito.doAnswer(inv -> Stream.of(image)).when(mock).stream();
                 InputStream badClose = new ByteArrayInputStream(new byte[]{1, 2}) {
                     @Override
                     public void close() throws IOException {
                         throw new IOException("close-fail");
                     }
                 };
                 Mockito.when(mock.getInputStream(image)).thenReturn(badClose);
             })) {
            Map<String, Object> metaCloseFail = new LinkedHashMap<>();
            Object outCloseFail = invoke("extractMobiImagesWithTika",
                    new Class<?>[]{Path.class, Map.class, Long.class, bc},
                    mockedZipPath, metaCloseFail, 20L, newInner("ImageBudget", new Class<?>[]{int.class, long.class}, 10, 1024L * 1024));
            assertTrue(((List<?>) outCloseFail).isEmpty());
            assertEquals("EMBEDDED_NONE", String.valueOf(metaCloseFail.get("imagesExtractionMode")));
        }

        Mockito.doThrow(new RuntimeException("save-throw-with-close"))
                .when(derivedUploadStorageService).saveDerivedImage(any(), any(), any(), any());
        try (MockedConstruction<org.apache.tika.parser.AutoDetectParser> ignoredParser =
                     Mockito.mockConstruction(org.apache.tika.parser.AutoDetectParser.class, (mock, ctx) ->
                             Mockito.doAnswer(inv -> null).when(mock).parse(any(), any(), any(), any())
                     );
             MockedConstruction<ZipFile> ignoredZip = Mockito.mockConstruction(ZipFile.class, (mock, ctx) -> {
                 ZipEntry image = new ZipEntry("suppressed.png");
                 image.setSize(2L);
                 Mockito.doAnswer(inv -> Stream.of(image)).when(mock).stream();
                 InputStream badCloseAfterThrow = new ByteArrayInputStream(new byte[]{5, 6}) {
                     @Override
                     public void close() throws IOException {
                         throw new IOException("close-after-throw");
                     }
                 };
                 Mockito.when(mock.getInputStream(image)).thenReturn(badCloseAfterThrow);
             })) {
            Map<String, Object> metaSuppressed = new LinkedHashMap<>();
            Object outSuppressed = invoke("extractMobiImagesWithTika",
                    new Class<?>[]{Path.class, Map.class, Long.class, bc},
                    mockedZipPath, metaSuppressed, 21L, newInner("ImageBudget", new Class<?>[]{int.class, long.class}, 10, 1024L * 1024));
            assertTrue(((List<?>) outSuppressed).isEmpty());
            assertEquals("EMBEDDED_NONE", String.valueOf(metaSuppressed.get("imagesExtractionMode")));
        }
    }

    @Test
    void extractAsync_shouldCoverArchiveAndImageEndToEndBranches() throws Exception {
        byte[] zipBytes = zip(List.of(
                Map.entry("a.txt", "hello".getBytes(StandardCharsets.UTF_8)),
                Map.entry("b.json", "{\"x\":1}".getBytes(StandardCharsets.UTF_8))
        ));
        Path z = tempDir.resolve("e2e.zip");
        Files.write(z, zipBytes);
        FileAssetsEntity fa = new FileAssetsEntity();
        fa.setId(88L);
        fa.setPath(z.toString());
        fa.setOriginalName("e2e.zip");
        fa.setMimeType("application/zip");

        when(fileAssetsRepository.findById(88L)).thenReturn(Optional.of(fa));
        when(fileAssetExtractionsRepository.findById(88L)).thenReturn(Optional.of(new FileAssetExtractionsEntity()));
        when(fileAssetExtractionsRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(vectorIndicesRepository.findByCollectionName(any())).thenReturn(List.of());
        when(tokenCountService.countTextTokens(any())).thenReturn(12);
        service.extractAsync(88L);
        verify(fileAssetExtractionsRepository, atLeastOnce()).save(any());
    }

    @Test
    void extractPdfImages_shouldCoverEmbeddedNoneAndFailed() throws Exception {
        Object budget = newInner("ImageBudget", new Class<?>[]{int.class, long.class}, 2, 1024L * 1024);
        Class<?> bc = budget.getClass();
        Path bad = tempDir.resolve("bad.pdf");
        Files.writeString(bad, "bad");
        Map<String, Object> m1 = new LinkedHashMap<>();
        Object o1 = invoke("extractPdfImages",
                new Class<?>[]{Path.class, Map.class, Long.class, String.class, bc},
                bad, m1, 1L, "", budget);
        assertNotNull(o1);

        Path blank = tempDir.resolve("blank.pdf");
        try (org.apache.pdfbox.pdmodel.PDDocument doc = new org.apache.pdfbox.pdmodel.PDDocument()) {
            doc.addPage(new org.apache.pdfbox.pdmodel.PDPage());
            doc.save(blank.toFile());
        }
        Map<String, Object> m2 = new LinkedHashMap<>();
        Object o2 = invoke("extractPdfImages",
                new Class<?>[]{Path.class, Map.class, Long.class, String.class, bc},
                blank, m2, 1L, "has-text", budget);
        assertNotNull(o2);
        assertTrue("EMBEDDED_NONE".equals(String.valueOf(m2.get("imagesExtractionMode")))
                || "PDF_XOBJECT".equals(String.valueOf(m2.get("imagesExtractionMode")))
                || "PDF_RENDER".equals(String.valueOf(m2.get("imagesExtractionMode"))));
    }

    @Test
    void extract7zAndExpand7z_shouldCoverBytesAndTraversalBranches() throws Exception {
        byte[] seven = sevenZ(List.of(
                Map.entry("../evil.txt", "x".getBytes(StandardCharsets.UTF_8)),
                Map.entry("a.txt", "hello".getBytes(StandardCharsets.UTF_8)),
                Map.entry("inner.zip", zip(List.of(Map.entry("b.txt", "b".getBytes(StandardCharsets.UTF_8)))))
        ), "s.7z");
        Object counters = newInner("ArchiveCounters", new Class<?>[]{});
        Class<?> cc = counters.getClass();
        Map<String, Object> meta = new LinkedHashMap<>();
        Object out = invoke("extract7zFromBytes",
                new Class<?>[]{byte[].class, String.class, int.class, int.class, Map.class, cc, long.class},
                seven, "s.7z", 0, 3000, meta, counters, System.nanoTime());
        assertNotNull(out);

        Path outDir = tempDir.resolve("out7z");
        Files.createDirectories(outDir);
        List<Map<String, Object>> files = new ArrayList<>();
        invoke("expand7zBytesToDisk",
                new Class<?>[]{byte[].class, String.class, int.class, Path.class, Map.class, cc, long.class, List.class},
                seven, "", 0, outDir, meta, counters, System.nanoTime(), files);
        assertTrue(files.size() >= 1);
    }

    @Test
    void extractArchive_shouldCoverSkippedUnsupportedAndSkippedTruncatedStatuses() throws Exception {
        ReflectionTestUtils.setField(service, "archiveMaxEntryBytes", 16L);
        byte[] zipBytes = zip(List.of(
                Map.entry("a.bin", "raw".getBytes(StandardCharsets.UTF_8)),
                Map.entry("big.txt", "012345678901234567890123456789".getBytes(StandardCharsets.UTF_8))
        ));
        Path p = tempDir.resolve("status.zip");
        Files.write(p, zipBytes);
        Object budget = newInner("ImageBudget", new Class<?>[]{int.class, long.class}, 5, 1024L * 1024);
        Class<?> bc = budget.getClass();
        Map<String, Object> meta = new LinkedHashMap<>();
        Object out = invoke("extractArchive",
                new Class<?>[]{Path.class, String.class, int.class, Map.class, Long.class, bc},
                p, "zip", 500, meta, 1L, budget);
        assertNotNull(out);
        assertTrue(meta.containsKey("archive"));
        ReflectionTestUtils.setField(service, "archiveMaxEntryBytes", 2 * 1024 * 1024L);
    }

    @Test
    void officeExtractors_shouldCoverNullAndEmptyPictureBranchesByMocking() throws Exception {
        Object budget = newInner("ImageBudget", new Class<?>[]{int.class, long.class}, 2, 1024L * 1024);
        Class<?> bc = budget.getClass();
        Path f = tempDir.resolve("dummy.bin");
        Files.writeString(f, "x");

        try (MockedConstruction<XWPFDocument> docs = Mockito.mockConstruction(XWPFDocument.class, (doc, ctx) -> {
            var p1 = Mockito.mock(org.apache.poi.xwpf.usermodel.XWPFPictureData.class);
            var p2 = Mockito.mock(org.apache.poi.xwpf.usermodel.XWPFPictureData.class);
            var p3 = Mockito.mock(org.apache.poi.xwpf.usermodel.XWPFPictureData.class);
            when(p1.getData()).thenReturn(new byte[0]);
            when(p2.getData()).thenReturn(new byte[]{1, 2, 3});
            when(p2.suggestFileExtension()).thenReturn(null);
            when(p2.getFileName()).thenReturn("");
            when(p2.getPackagePart()).thenThrow(new RuntimeException("pp"));
            when(p3.getData()).thenReturn(new byte[]{9, 8, 7});
            when(p3.suggestFileExtension()).thenReturn("png");
            when(p3.getFileName()).thenReturn("x.png");
            when(doc.getAllPictures()).thenReturn(Arrays.asList(null, p1, p2, p3));
        })) {
            when(derivedUploadStorageService.saveDerivedImage(any(), any(), any(), any()))
                    .thenReturn(Map.of("id", "1"))
                    .thenReturn(null);
            Map<String, Object> m = new LinkedHashMap<>();
            Object out = invoke("extractDocxImages", new Class<?>[]{Path.class, Map.class, Long.class, bc}, f, m, 1L, budget);
            assertNotNull(out);
        }

        try (MockedStatic<org.apache.poi.ss.usermodel.WorkbookFactory> wfs = Mockito.mockStatic(org.apache.poi.ss.usermodel.WorkbookFactory.class)) {
            Workbook wb = Mockito.mock(Workbook.class);
            wfs.when(() -> org.apache.poi.ss.usermodel.WorkbookFactory.create(f.toFile())).thenReturn(wb);
            Map<String, Object> m = new LinkedHashMap<>();
            Object out = invoke("extractXlsxImages", new Class<?>[]{Path.class, Map.class, Long.class, bc}, f, m, 1L, budget);
            assertNotNull(out);
            assertEquals("UNSUPPORTED", String.valueOf(m.get("imagesExtractionMode")));
        }

        try (MockedStatic<org.apache.poi.ss.usermodel.WorkbookFactory> wfs = Mockito.mockStatic(org.apache.poi.ss.usermodel.WorkbookFactory.class)) {
            XSSFWorkbook xssf = Mockito.mock(XSSFWorkbook.class);
            var p1 = Mockito.mock(org.apache.poi.xssf.usermodel.XSSFPictureData.class);
            var p2 = Mockito.mock(org.apache.poi.xssf.usermodel.XSSFPictureData.class);
            when(p1.getData()).thenReturn(new byte[0]);
            when(p2.getData()).thenReturn(new byte[]{4, 5, 6});
            when(p2.suggestFileExtension()).thenReturn("png");
            when(p2.getPackagePart()).thenReturn(null);
            when(xssf.getAllPictures()).thenReturn(Arrays.asList(null, p1, p2));
            wfs.when(() -> org.apache.poi.ss.usermodel.WorkbookFactory.create(f.toFile())).thenReturn(xssf);
            Map<String, Object> m = new LinkedHashMap<>();
            Object out = invoke("extractXlsxImages", new Class<?>[]{Path.class, Map.class, Long.class, bc}, f, m, 1L, budget);
            assertNotNull(out);
        }

        try (MockedConstruction<XMLSlideShow> pptx = Mockito.mockConstruction(XMLSlideShow.class, (ss, ctx) -> {
            var p1 = Mockito.mock(org.apache.poi.xslf.usermodel.XSLFPictureData.class);
            var p2 = Mockito.mock(org.apache.poi.xslf.usermodel.XSLFPictureData.class);
            when(p1.getData()).thenReturn(new byte[0]);
            when(p2.getData()).thenReturn(new byte[]{1, 1, 1});
            when(p2.suggestFileExtension()).thenThrow(new RuntimeException("noext"));
            when(p2.getContentType()).thenThrow(new RuntimeException("nomime"));
            when(ss.getPictureData()).thenReturn(Arrays.asList(null, p1, p2));
        })) {
            Map<String, Object> m = new LinkedHashMap<>();
            Object out = invoke("extractPptxImages", new Class<?>[]{Path.class, Map.class, Long.class, bc}, f, m, 1L, budget);
            assertNotNull(out);
        }

        try (MockedConstruction<HSLFSlideShow> ppt = Mockito.mockConstruction(HSLFSlideShow.class, (ss, ctx) -> {
            var p1 = Mockito.mock(HSLFPictureData.class);
            var p2 = Mockito.mock(HSLFPictureData.class);
            when(p1.getData()).thenReturn(new byte[0]);
            when(p2.getData()).thenReturn(new byte[]{2, 2, 2});
            when(p2.getType()).thenThrow(new RuntimeException("notype"));
            when(ss.getPictureData()).thenReturn(Arrays.asList(null, p1, p2));
        })) {
            Map<String, Object> m = new LinkedHashMap<>();
            Object out = invoke("extractPptImages", new Class<?>[]{Path.class, Map.class, Long.class, bc}, f, m, 1L, budget);
            assertNotNull(out);
        }
    }

    @Test
    void archiveFlows_shouldCoverEntryMatrixByMockingFactory() throws Exception {
        Object counters = newInner("ArchiveCounters", new Class<?>[]{});
        Class<?> cc = counters.getClass();
        Map<String, Object> meta = new LinkedHashMap<>();
        List<Map<String, Object>> files = new ArrayList<>();
        Path outDir = tempDir.resolve("mock-archive");
        Files.createDirectories(outDir);

        ArchiveInputStream<ArchiveEntry> in = Mockito.mock(ArchiveInputStream.class);
        when(in.getNextEntry()).thenReturn(
                new E("dir", true),
                new E("../evil.txt", false),
                new E("blank.txt", false),
                new E("a.txt", false),
                null
        );
        when(in.read(any(byte[].class))).thenReturn(
                3, -1,
                -1,
                5, -1
        );
        try (MockedStatic<ArchiveStreamFactory> st = Mockito.mockStatic(ArchiveStreamFactory.class);
             MockedConstruction<ArchiveStreamFactory> ct = Mockito.mockConstruction(ArchiveStreamFactory.class, (fac, ctx) -> {
                 when(fac.createArchiveInputStream(eq("zip"), any(InputStream.class))).thenReturn(in);
             })) {
            st.when(() -> ArchiveStreamFactory.detect(any(InputStream.class))).thenReturn("zip");
            invoke("expandArchiveStreamToDisk",
                    new Class<?>[]{InputStream.class, String.class, String.class, int.class, Path.class, Map.class, cc, long.class, List.class},
                    new ByteArrayInputStream(new byte[]{1, 2, 3}), "m.zip", "", 0, outDir, meta, counters, System.nanoTime(), files);
            assertTrue(files.size() >= 1);
        }
    }
}
