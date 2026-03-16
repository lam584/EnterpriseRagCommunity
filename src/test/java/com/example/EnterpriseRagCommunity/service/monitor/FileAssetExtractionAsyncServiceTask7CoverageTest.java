package com.example.EnterpriseRagCommunity.service.monitor;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.pdfbox.cos.COSName;
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
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.MockedConstruction;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.springframework.test.util.ReflectionTestUtils;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FileAssetExtractionAsyncServiceTask7CoverageTest {
    @TempDir
    Path tempDir;

    private FileAssetExtractionAsyncService newService() {
        FileAssetExtractionAsyncService service = new FileAssetExtractionAsyncService(
                null,
                null,
                null,
                new ObjectMapper(),
                null,
                null,
                null,
                null
        );
        ReflectionTestUtils.setField(service, "archiveMaxDepth", 5);
        ReflectionTestUtils.setField(service, "archiveMaxEntries", 100);
        ReflectionTestUtils.setField(service, "archiveMaxEntryBytes", 1024L * 1024L);
        ReflectionTestUtils.setField(service, "archiveMaxTotalBytes", 1024L * 1024L);
        ReflectionTestUtils.setField(service, "archiveMaxTotalMillis", 30000L);
        return service;
    }

    private static byte[] zipBytes(List<Map.Entry<String, byte[]>> entries) throws Exception {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (ZipOutputStream zos = new ZipOutputStream(baos, StandardCharsets.UTF_8)) {
            for (Map.Entry<String, byte[]> it : entries) {
                zos.putNextEntry(new ZipEntry(it.getKey()));
                zos.write(it.getValue());
                zos.closeEntry();
            }
        }
        return baos.toByteArray();
    }

    private byte[] sevenZBytes(List<Map.Entry<String, byte[]>> entries) throws Exception {
        Path seven = tempDir.resolve("t7.7z");
        try (SevenZOutputFile out = new SevenZOutputFile(seven.toFile())) {
            for (Map.Entry<String, byte[]> it : entries) {
                SevenZArchiveEntry entry = new SevenZArchiveEntry();
                entry.setName(it.getKey());
                entry.setContentMethods(List.of(new SevenZMethodConfiguration(SevenZMethod.COPY)));
                out.putArchiveEntry(entry);
                out.write(it.getValue());
                out.closeArchiveEntry();
            }
        }
        return Files.readAllBytes(seven);
    }

    private Object invoke(FileAssetExtractionAsyncService service, String name, Class<?>[] types, Object... args) throws Exception {
        Method method = FileAssetExtractionAsyncService.class.getDeclaredMethod(name, types);
        method.setAccessible(true);
        return method.invoke(service, args);
    }

    private Object newInner(String simpleName, Class<?>[] types, Object... args) throws Exception {
        Class<?> c = Class.forName(FileAssetExtractionAsyncService.class.getName() + "$" + simpleName);
        Constructor<?> ctor = c.getDeclaredConstructor(types);
        ctor.setAccessible(true);
        return ctor.newInstance(args);
    }

    @Test
    void extractArchive_shouldKeepTotalBytesReasonWhenTextLimitReached() throws Exception {
        FileAssetExtractionAsyncService service = newService();
        ReflectionTestUtils.setField(service, "archiveMaxTotalBytes", 30L);

        byte[] first = "0123456789abcdefghij".getBytes(StandardCharsets.UTF_8);
        byte[] second = "abcdefghijklmnopqrstuvwxyz".getBytes(StandardCharsets.UTF_8);
        Path zip = tempDir.resolve("reason.zip");
        Files.write(zip, zipBytes(List.of(
                Map.entry("a.txt", first),
                Map.entry("b.txt", second)
        )));

        Object budget = newInner("ImageBudget", new Class<?>[]{int.class, long.class}, 5, 1024L * 1024L);
        Map<String, Object> meta = new LinkedHashMap<>();

        Object text = invoke(
                service,
                "extractArchive",
                new Class<?>[]{Path.class, String.class, int.class, Map.class, Long.class, budget.getClass()},
                zip,
                "zip",
                8,
                meta,
                77L,
                budget
        );
        assertNotNull(text);
        Object archiveObj = meta.get("archive");
        assertTrue(archiveObj instanceof Map<?, ?>);
        Map<?, ?> archive = (Map<?, ?>) archiveObj;
        assertEquals("TOTAL_BYTES_LIMIT", String.valueOf(archive.get("truncatedReason")));
        assertTrue(Boolean.TRUE.equals(archive.get("truncated")));
    }

    @Test
    void expandArchiveStreamToDisk_shouldKeepPresetReasonOn7zTotalLimit() throws Exception {
        FileAssetExtractionAsyncService service = newService();
        ReflectionTestUtils.setField(service, "archiveMaxTotalBytes", 8L);

        byte[] seven = sevenZBytes(List.of(Map.entry("a.txt", "0123456789".getBytes(StandardCharsets.UTF_8))));
        Object counters = newInner("ArchiveCounters", new Class<?>[]{});
        ReflectionTestUtils.setField(counters, "truncatedReason", "PRESET");
        Class<?> countersClass = counters.getClass();
        Map<String, Object> meta = new LinkedHashMap<>();

        invoke(
                service,
                "expandArchiveStreamToDisk",
                new Class<?>[]{java.io.InputStream.class, String.class, String.class, int.class, Path.class, Map.class, countersClass, long.class, java.util.List.class},
                new ByteArrayInputStream(seven),
                "seven.7z",
                "",
                0,
                tempDir.resolve("out-7z"),
                meta,
                counters,
                System.nanoTime(),
                new java.util.ArrayList<>()
        );
        assertEquals("PRESET", String.valueOf(ReflectionTestUtils.getField(counters, "truncatedReason")));
    }

    @Test
    void expandArchiveStreamToDisk_shouldStopBeforeExpand7zWhenReadAllLimitedOverflows() throws Exception {
        FileAssetExtractionAsyncService service = newService();
        ReflectionTestUtils.setField(service, "archiveMaxTotalBytes", 1_000_000L);

        byte[] payload = new byte[20000];
        for (int i = 0; i < payload.length; i++) payload[i] = (byte) (i % 251);
        byte[] seven = sevenZBytes(List.of(Map.entry("big.bin", payload)));

        Object counters = newInner("ArchiveCounters", new Class<?>[]{});
        Class<?> countersClass = counters.getClass();
        Map<String, Object> meta = new LinkedHashMap<>();
        java.util.ArrayList<Map<String, Object>> files = new java.util.ArrayList<>();
        boolean[] patchedAtEof = new boolean[]{false};
        java.io.InputStream eofPatched = new ByteArrayInputStream(seven) {
            @Override
            public synchronized int read(byte[] b, int off, int len) {
                int n = super.read(b, off, len);
                if (n < 0 && !patchedAtEof[0]) {
                    ReflectionTestUtils.setField(counters, "totalBytesRead", 1_000_001L);
                    patchedAtEof[0] = true;
                }
                return n;
            }
        };

        invoke(
                service,
                "expandArchiveStreamToDisk",
                new Class<?>[]{java.io.InputStream.class, String.class, String.class, int.class, Path.class, Map.class, countersClass, long.class, java.util.List.class},
                eofPatched,
                "big.7z",
                "",
                0,
                tempDir.resolve("out-7z-overflow"),
                meta,
                counters,
                System.nanoTime(),
                files
        );

        assertEquals("TOTAL_BYTES_LIMIT", String.valueOf(ReflectionTestUtils.getField(counters, "truncatedReason")));
    }

    @Test
    void expandArchiveStreamToDisk_shouldKeepPresetReasonOnLoopTimeLimit() throws Exception {
        FileAssetExtractionAsyncService service = newService();
        ReflectionTestUtils.setField(service, "archiveMaxTotalMillis", 1L);

        byte[] zip = zipBytes(List.of(
                Map.entry("a.txt", "aaa".getBytes(StandardCharsets.UTF_8)),
                Map.entry("b.txt", "bbb".getBytes(StandardCharsets.UTF_8))
        ));
        Object counters = newInner("ArchiveCounters", new Class<?>[]{});
        ReflectionTestUtils.setField(counters, "truncatedReason", "PRESET");
        Class<?> countersClass = counters.getClass();
        Map<String, Object> meta = new LinkedHashMap<>();

        invoke(
                service,
                "expandArchiveStreamToDisk",
                new Class<?>[]{java.io.InputStream.class, String.class, String.class, int.class, Path.class, Map.class, countersClass, long.class, java.util.List.class},
                new ByteArrayInputStream(zip),
                "normal.zip",
                "",
                0,
                tempDir.resolve("out-zip"),
                meta,
                counters,
                1L,
                new java.util.ArrayList<>()
        );
        assertEquals("PRESET", String.valueOf(ReflectionTestUtils.getField(counters, "truncatedReason")));
    }

    private static byte[] tinyPngBytes() throws Exception {
        BufferedImage img = new BufferedImage(2, 2, BufferedImage.TYPE_INT_RGB);
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ImageIO.write(img, "png", baos);
        return baos.toByteArray();
    }

    private Path createPdfWithImage() throws Exception {
        Path pdf = tempDir.resolve("task7-img.pdf");
        byte[] png = tinyPngBytes();
        try (PDDocument doc = new PDDocument()) {
            PDPage page = new PDPage();
            doc.addPage(page);
            PDImageXObject image = PDImageXObject.createFromByteArray(doc, png, "tiny");
            try (org.apache.pdfbox.pdmodel.PDPageContentStream cs = new org.apache.pdfbox.pdmodel.PDPageContentStream(doc, page)) {
                cs.drawImage(image, 10, 10, 20, 20);
            }
            doc.save(pdf.toFile());
        }
        return pdf;
    }

    @Test
    void extractPdfImages_shouldCoverNullPageNonImageNullImageAndBudgetNullPath() throws Exception {
        DerivedUploadStorageService storage = Mockito.mock(DerivedUploadStorageService.class);
        Mockito.when(storage.saveDerivedImage(Mockito.any(byte[].class), Mockito.anyString(), Mockito.any(), Mockito.any()))
                .thenReturn(Map.of("path", "/tmp/task7-pdf-xo.png"));
        Mockito.when(storage.buildPlaceholder(Mockito.anyInt(), Mockito.anyMap()))
                .thenAnswer(inv -> Map.of("placeholder", "[[IMAGE_" + inv.getArgument(0) + "]]"));
        FileAssetExtractionAsyncService service = new FileAssetExtractionAsyncService(
                null, null, null, new ObjectMapper(), null, null, null, storage
        );
        ReflectionTestUtils.setField(service, "pdfRenderMaxPages", 2);
        ReflectionTestUtils.setField(service, "pdfRenderDpi", 96);
        Object budget = newInner("ImageBudget", new Class<?>[]{int.class, long.class}, 10, 1024L * 1024L);

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

        Map<String, Object> meta = new LinkedHashMap<>();
        try (MockedStatic<Loader> mocked = Mockito.mockStatic(Loader.class)) {
            mocked.when(() -> Loader.loadPDF(Mockito.any(File.class))).thenReturn(doc);
            Object out = invoke(
                    service,
                    "extractPdfImages",
                    new Class<?>[]{Path.class, Map.class, Long.class, String.class, budget.getClass()},
                    tempDir.resolve("mock-task7.pdf"),
                    meta,
                    901L,
                    null,
                    null
            );
            assertFalse(((List<?>) out).isEmpty());
            assertEquals("PDF_XOBJECT", String.valueOf(meta.get("imagesExtractionMode")));
            assertEquals(2, meta.get("pages"));
        }
    }

    @Test
    void extractPdfImages_shouldCoverEmbeddedNoneRenderSkippedRenderAndBudgetBreaks() throws Exception {
        DerivedUploadStorageService storage = Mockito.mock(DerivedUploadStorageService.class);
        Mockito.when(storage.saveDerivedImage(Mockito.any(byte[].class), Mockito.anyString(), Mockito.any(), Mockito.any()))
                .thenReturn(Map.of("path", "/tmp/task7-render.png"));
        Mockito.when(storage.buildPlaceholder(Mockito.anyInt(), Mockito.anyMap()))
                .thenAnswer(inv -> Map.of("placeholder", "[[IMAGE_" + inv.getArgument(0) + "]]"));
        FileAssetExtractionAsyncService service = new FileAssetExtractionAsyncService(
                null, null, null, new ObjectMapper(), null, null, null, storage
        );
        ReflectionTestUtils.setField(service, "pdfRenderDpi", 96);
        Object budget = newInner("ImageBudget", new Class<?>[]{int.class, long.class}, 10, 1024L * 1024L);

        Path blank = tempDir.resolve("task7-blank.pdf");
        try (PDDocument doc = new PDDocument()) {
            doc.addPage(new PDPage());
            doc.save(blank.toFile());
        }

        ReflectionTestUtils.setField(service, "pdfRenderMaxPages", 1);
        Map<String, Object> m1 = new LinkedHashMap<>();
        Object o1 = invoke(
                service,
                "extractPdfImages",
                new Class<?>[]{Path.class, Map.class, Long.class, String.class, budget.getClass()},
                blank, m1, 902L, "has text", budget
        );
        assertTrue(((List<?>) o1).isEmpty());
        assertEquals("EMBEDDED_NONE", String.valueOf(m1.get("imagesExtractionMode")));

        ReflectionTestUtils.setField(service, "pdfRenderMaxPages", 0);
        Map<String, Object> m2 = new LinkedHashMap<>();
        Object o2 = invoke(
                service,
                "extractPdfImages",
                new Class<?>[]{Path.class, Map.class, Long.class, String.class, budget.getClass()},
                blank, m2, 903L, "", budget
        );
        assertTrue(((List<?>) o2).isEmpty());
        assertEquals("PDF_RENDER_SKIPPED", String.valueOf(m2.get("imagesExtractionMode")));

        ReflectionTestUtils.setField(service, "pdfRenderMaxPages", 1);
        Map<String, Object> m3 = new LinkedHashMap<>();
        Object o3 = invoke(
                service,
                "extractPdfImages",
                new Class<?>[]{Path.class, Map.class, Long.class, String.class, budget.getClass()},
                blank, m3, 904L, "", null
        );
        assertFalse(((List<?>) o3).isEmpty());
        assertEquals("PDF_RENDER", String.valueOf(m3.get("imagesExtractionMode")));

        Object zeroBudget = newInner("ImageBudget", new Class<?>[]{int.class, long.class}, 0, 1024L);
        Map<String, Object> m4 = new LinkedHashMap<>();
        Object o4 = invoke(
                service,
                "extractPdfImages",
                new Class<?>[]{Path.class, Map.class, Long.class, String.class, budget.getClass()},
                blank, m4, 905L, "", zeroBudget
        );
        assertTrue(((List<?>) o4).isEmpty());
        assertEquals("PDF_RENDER", String.valueOf(m4.get("imagesExtractionMode")));

        Path withImage = createPdfWithImage();
        Object smallByteBudget = newInner("ImageBudget", new Class<?>[]{int.class, long.class}, 10, 1L);
        Map<String, Object> m5 = new LinkedHashMap<>();
        Object o5 = invoke(
                service,
                "extractPdfImages",
                new Class<?>[]{Path.class, Map.class, Long.class, String.class, budget.getClass()},
                withImage, m5, 906L, "", smallByteBudget
        );
        assertTrue(((List<?>) o5).isEmpty());
        assertEquals("PDF_RENDER", String.valueOf(m5.get("imagesExtractionMode")));
    }

    @Test
    void extractPdfImages_shouldCoverBytesZeroSavedNullAndFailedPaths() throws Exception {
        DerivedUploadStorageService storage = Mockito.mock(DerivedUploadStorageService.class);
        Mockito.when(storage.saveDerivedImage(Mockito.any(byte[].class), Mockito.anyString(), Mockito.any(), Mockito.any()))
                .thenReturn(null);
        FileAssetExtractionAsyncService service = new FileAssetExtractionAsyncService(
                null, null, null, new ObjectMapper(), null, null, null, storage
        );
        ReflectionTestUtils.setField(service, "pdfRenderMaxPages", 3);
        ReflectionTestUtils.setField(service, "pdfRenderDpi", 96);
        Object budget = newInner("ImageBudget", new Class<?>[]{int.class, long.class}, 10, 1024L * 1024L);

        PDDocument doc = Mockito.mock(PDDocument.class);
        PDPage page = Mockito.mock(PDPage.class);
        PDResources resources = Mockito.mock(PDResources.class);
        PDImageXObject image = Mockito.mock(PDImageXObject.class);
        COSName name = COSName.getPDFName("img");
        Mockito.when(doc.getNumberOfPages()).thenReturn(3);
        Mockito.when(doc.getPage(Mockito.anyInt())).thenReturn(page);
        Mockito.when(page.getResources()).thenReturn(resources);
        Mockito.when(resources.getXObjectNames()).thenReturn(List.of(name));
        Mockito.when(resources.getXObject(name)).thenReturn(image);
        Mockito.when(image.getImage()).thenReturn(new BufferedImage(2, 2, BufferedImage.TYPE_INT_RGB));

        Map<String, Object> m1 = new LinkedHashMap<>();
        try (MockedStatic<Loader> mockedLoader = Mockito.mockStatic(Loader.class);
             MockedStatic<ImageIO> mockedImageIo = Mockito.mockStatic(ImageIO.class)) {
            mockedLoader.when(() -> Loader.loadPDF(Mockito.any(File.class))).thenReturn(doc);
            mockedImageIo.when(() -> ImageIO.write(Mockito.any(), Mockito.eq("png"), Mockito.any(java.io.OutputStream.class)))
                    .thenReturn(false);
            Object o1 = invoke(
                    service,
                    "extractPdfImages",
                    new Class<?>[]{Path.class, Map.class, Long.class, String.class, budget.getClass()},
                    tempDir.resolve("task7-bytes-zero.pdf"), m1, 907L, "", budget
            );
            assertTrue(((List<?>) o1).isEmpty());
            String mode = String.valueOf(m1.get("imagesExtractionMode"));
            assertTrue("PDF_RENDER".equals(mode) || "FAILED".equals(mode));
        }

        Mockito.when(page.getResources()).thenReturn(null);
        Mockito.doThrow(new RuntimeException("close-boom")).when(doc).close();
        Map<String, Object> m2 = new LinkedHashMap<>();
        try (MockedStatic<Loader> mockedLoader = Mockito.mockStatic(Loader.class);
             MockedConstruction<PDFRenderer> mockedRenderer = Mockito.mockConstruction(
                     PDFRenderer.class,
                     (mock, ctx) -> Mockito.when(mock.renderImageWithDPI(Mockito.anyInt(), Mockito.anyInt()))
                             .thenAnswer(inv -> {
                                 int i = inv.getArgument(0);
                                 if (i == 0) return null;
                                 if (i == 1) return new BufferedImage(2, 2, BufferedImage.TYPE_INT_RGB);
                                 throw new RuntimeException("render-boom");
                             })
             )) {
            mockedLoader.when(() -> Loader.loadPDF(Mockito.any(File.class))).thenReturn(doc);
            Object o2 = invoke(
                    service,
                    "extractPdfImages",
                    new Class<?>[]{Path.class, Map.class, Long.class, String.class, budget.getClass()},
                    tempDir.resolve("task7-failed-render.pdf"), m2, 908L, "", budget
            );
            assertTrue(((List<?>) o2).isEmpty());
            assertEquals("FAILED", String.valueOf(m2.get("imagesExtractionMode")));
            assertNotNull(m2.get("imagesExtractionError"));
            assertEquals(1, mockedRenderer.constructed().size());
        }

        Map<String, Object> m3 = new LinkedHashMap<>();
        try (MockedStatic<Loader> mockedLoader = Mockito.mockStatic(Loader.class)) {
            mockedLoader.when(() -> Loader.loadPDF(Mockito.any(File.class))).thenThrow(new RuntimeException("load-boom"));
            Object o3 = invoke(
                    service,
                    "extractPdfImages",
                    new Class<?>[]{Path.class, Map.class, Long.class, String.class, budget.getClass()},
                    tempDir.resolve("task7-failed-load.pdf"), m3, 909L, "", budget
            );
            assertTrue(((List<?>) o3).isEmpty());
            assertEquals("FAILED", String.valueOf(m3.get("imagesExtractionMode")));
            assertNotNull(m3.get("imagesExtractionError"));
        }
    }

    @Test
    void extractPdfImages_shouldCoverRemainingXObjectBranches() throws Exception {
        DerivedUploadStorageService storage = Mockito.mock(DerivedUploadStorageService.class);
        Mockito.when(storage.buildPlaceholder(Mockito.anyInt(), Mockito.anyMap()))
                .thenAnswer(inv -> Map.of("placeholder", "[[IMAGE_" + inv.getArgument(0) + "]]"));
        FileAssetExtractionAsyncService service = new FileAssetExtractionAsyncService(
                null, null, null, new ObjectMapper(), null, null, null, storage
        );
        ReflectionTestUtils.setField(service, "pdfRenderMaxPages", 1);
        ReflectionTestUtils.setField(service, "pdfRenderDpi", 96);
        Object budget = newInner("ImageBudget", new Class<?>[]{int.class, long.class}, 10, 1024L * 1024L);

        PDDocument doc = Mockito.mock(PDDocument.class);
        PDPage page = Mockito.mock(PDPage.class);
        PDResources resources = Mockito.mock(PDResources.class);
        PDImageXObject image = Mockito.mock(PDImageXObject.class);
        COSName n1 = COSName.getPDFName("x1");
        COSName n2 = COSName.getPDFName("x2");
        Mockito.when(doc.getNumberOfPages()).thenReturn(1);
        Mockito.when(doc.getPage(0)).thenReturn(page);
        Mockito.when(page.getResources()).thenReturn(resources);
        Mockito.when(resources.getXObjectNames()).thenReturn(List.of(n1, n2));
        Mockito.when(resources.getXObject(n1)).thenReturn(image);
        Mockito.when(resources.getXObject(n2)).thenReturn(image);
        Mockito.when(image.getImage()).thenReturn(new BufferedImage(2, 2, BufferedImage.TYPE_INT_RGB));

        try (MockedStatic<Loader> mocked = Mockito.mockStatic(Loader.class)) {
            mocked.when(() -> Loader.loadPDF(Mockito.any(File.class))).thenReturn(doc);

            Mockito.when(storage.saveDerivedImage(Mockito.any(byte[].class), Mockito.anyString(), Mockito.any(), Mockito.any()))
                    .thenReturn(null);
            Map<String, Object> m1 = new LinkedHashMap<>();
            Object o1 = invoke(
                    service,
                    "extractPdfImages",
                    new Class<?>[]{Path.class, Map.class, Long.class, String.class, budget.getClass()},
                    tempDir.resolve("xobj-save-null.pdf"), m1, 910L, "text", budget
            );
            assertTrue(((List<?>) o1).isEmpty());
            assertEquals("EMBEDDED_NONE", String.valueOf(m1.get("imagesExtractionMode")));

            Mockito.when(storage.saveDerivedImage(Mockito.any(byte[].class), Mockito.anyString(), Mockito.any(), Mockito.any()))
                    .thenReturn(Map.of("path", "/tmp/xobj-budget-null.png"));
            Map<String, Object> m2 = new LinkedHashMap<>();
            Object o2 = invoke(
                    service,
                    "extractPdfImages",
                    new Class<?>[]{Path.class, Map.class, Long.class, String.class, budget.getClass()},
                    tempDir.resolve("xobj-budget-null.pdf"), m2, 911L, "", null
            );
            assertFalse(((List<?>) o2).isEmpty());
            assertEquals("PDF_XOBJECT", String.valueOf(m2.get("imagesExtractionMode")));

            Object oneCountBudget = newInner("ImageBudget", new Class<?>[]{int.class, long.class}, 1, 1024L * 1024L);
            Map<String, Object> m3 = new LinkedHashMap<>();
            Object o3 = invoke(
                    service,
                    "extractPdfImages",
                    new Class<?>[]{Path.class, Map.class, Long.class, String.class, budget.getClass()},
                    tempDir.resolve("xobj-count-break.pdf"), m3, 912L, "", oneCountBudget
            );
            assertFalse(((List<?>) o3).isEmpty());
            assertEquals("PDF_XOBJECT", String.valueOf(m3.get("imagesExtractionMode")));

            Object tinyBudget = newInner("ImageBudget", new Class<?>[]{int.class, long.class}, 10, 1L);
            Map<String, Object> m4 = new LinkedHashMap<>();
            Object o4 = invoke(
                    service,
                    "extractPdfImages",
                    new Class<?>[]{Path.class, Map.class, Long.class, String.class, budget.getClass()},
                    tempDir.resolve("xobj-byte-break.pdf"), m4, 913L, "", tinyBudget
            );
            assertTrue(((List<?>) o4).isEmpty());
            assertTrue(List.of("PDF_RENDER", "EMBEDDED_NONE", "FAILED").contains(String.valueOf(m4.get("imagesExtractionMode"))));
        }

        try (MockedStatic<Loader> mockedLoader = Mockito.mockStatic(Loader.class);
             MockedStatic<ImageIO> mockedImageIo = Mockito.mockStatic(ImageIO.class)) {
            mockedLoader.when(() -> Loader.loadPDF(Mockito.any(File.class))).thenReturn(doc);
            mockedImageIo.when(() -> ImageIO.write(Mockito.any(), Mockito.eq("png"), Mockito.any(java.io.OutputStream.class)))
                    .thenReturn(false);
            Map<String, Object> m5 = new LinkedHashMap<>();
            Object o5 = invoke(
                    service,
                    "extractPdfImages",
                    new Class<?>[]{Path.class, Map.class, Long.class, String.class, budget.getClass()},
                    tempDir.resolve("xobj-bytes-zero.pdf"), m5, 914L, "text", budget
            );
            assertTrue(((List<?>) o5).isEmpty());
            assertEquals("EMBEDDED_NONE", String.valueOf(m5.get("imagesExtractionMode")));
        }
    }

    @Test
    void extractPdfImages_shouldCoverRemainingRenderBranchesAndCatchVariants() throws Exception {
        DerivedUploadStorageService storage = Mockito.mock(DerivedUploadStorageService.class);
        Mockito.when(storage.buildPlaceholder(Mockito.anyInt(), Mockito.anyMap()))
                .thenAnswer(inv -> Map.of("placeholder", "[[IMAGE_" + inv.getArgument(0) + "]]"));
        FileAssetExtractionAsyncService service = new FileAssetExtractionAsyncService(
                null, null, null, new ObjectMapper(), null, null, null, storage
        );
        ReflectionTestUtils.setField(service, "pdfRenderMaxPages", 3);
        ReflectionTestUtils.setField(service, "pdfRenderDpi", 96);
        Object budget = newInner("ImageBudget", new Class<?>[]{int.class, long.class}, 10, 1024L * 1024L);

        PDDocument doc = Mockito.mock(PDDocument.class);
        PDPage page = Mockito.mock(PDPage.class);
        Mockito.when(doc.getNumberOfPages()).thenReturn(3);
        Mockito.when(doc.getPage(Mockito.anyInt())).thenReturn(page);
        Mockito.when(page.getResources()).thenReturn(null);

        try (MockedStatic<Loader> mockedLoader = Mockito.mockStatic(Loader.class);
             MockedConstruction<PDFRenderer> mockedRenderer = Mockito.mockConstruction(
                     PDFRenderer.class,
                     (mock, ctx) -> Mockito.when(mock.renderImageWithDPI(Mockito.anyInt(), Mockito.anyInt()))
                             .thenAnswer(inv -> new BufferedImage(2, 2, BufferedImage.TYPE_INT_RGB))
             )) {
            mockedLoader.when(() -> Loader.loadPDF(Mockito.any(File.class))).thenReturn(doc);

            Object zeroBudget = newInner("ImageBudget", new Class<?>[]{int.class, long.class}, 0, 1024L);
            Map<String, Object> m1 = new LinkedHashMap<>();
            Object o1 = invoke(
                    service,
                    "extractPdfImages",
                    new Class<?>[]{Path.class, Map.class, Long.class, String.class, budget.getClass()},
                    tempDir.resolve("render-count-break.pdf"), m1, 915L, "", zeroBudget
            );
            assertTrue(((List<?>) o1).isEmpty());
            assertEquals("PDF_RENDER", String.valueOf(m1.get("imagesExtractionMode")));

            Mockito.when(storage.saveDerivedImage(Mockito.any(byte[].class), Mockito.anyString(), Mockito.any(), Mockito.any()))
                    .thenReturn(null);
            Map<String, Object> m2 = new LinkedHashMap<>();
            Object o2 = invoke(
                    service,
                    "extractPdfImages",
                    new Class<?>[]{Path.class, Map.class, Long.class, String.class, budget.getClass()},
                    tempDir.resolve("render-save-null.pdf"), m2, 916L, "", budget
            );
            assertTrue(((List<?>) o2).isEmpty());
            assertEquals("PDF_RENDER", String.valueOf(m2.get("imagesExtractionMode")));

            Mockito.when(storage.saveDerivedImage(Mockito.any(byte[].class), Mockito.anyString(), Mockito.any(), Mockito.any()))
                    .thenReturn(Map.of("path", "/tmp/render-budget-null.png"));
            Map<String, Object> m3 = new LinkedHashMap<>();
            Object o3 = invoke(
                    service,
                    "extractPdfImages",
                    new Class<?>[]{Path.class, Map.class, Long.class, String.class, budget.getClass()},
                    tempDir.resolve("render-budget-null.pdf"), m3, 917L, "", null
            );
            assertTrue(List.of("PDF_RENDER", "FAILED").contains(String.valueOf(m3.get("imagesExtractionMode"))));

            Object tinyBudget = newInner("ImageBudget", new Class<?>[]{int.class, long.class}, 10, 1L);
            Map<String, Object> m4 = new LinkedHashMap<>();
            Object o4 = invoke(
                    service,
                    "extractPdfImages",
                    new Class<?>[]{Path.class, Map.class, Long.class, String.class, budget.getClass()},
                    tempDir.resolve("render-byte-break.pdf"), m4, 918L, "", tinyBudget
            );
            assertTrue(((List<?>) o4).isEmpty());
            assertEquals("PDF_RENDER", String.valueOf(m4.get("imagesExtractionMode")));
            assertTrue(mockedRenderer.constructed().size() >= 1);
        }

        try (MockedStatic<Loader> mockedLoader = Mockito.mockStatic(Loader.class);
             MockedConstruction<PDFRenderer> mockedRenderer = Mockito.mockConstruction(
                     PDFRenderer.class,
                     (mock, ctx) -> Mockito.when(mock.renderImageWithDPI(Mockito.anyInt(), Mockito.anyInt()))
                             .thenThrow(new RuntimeException("render-boom"))
             )) {
            Mockito.doNothing().when(doc).close();
            mockedLoader.when(() -> Loader.loadPDF(Mockito.any(File.class))).thenReturn(doc);
            Map<String, Object> m5 = new LinkedHashMap<>();
            Object budgetForThrow = newInner("ImageBudget", new Class<?>[]{int.class, long.class}, 10, 1024L * 1024L);
            Object o5 = invoke(
                    service,
                    "extractPdfImages",
                    new Class<?>[]{Path.class, Map.class, Long.class, String.class, budget.getClass()},
                    tempDir.resolve("render-throw-close-ok.pdf"), m5, 919L, "", budgetForThrow
            );
            assertTrue(((List<?>) o5).isEmpty());
            String mode5 = String.valueOf(m5.get("imagesExtractionMode"));
            assertTrue(List.of("FAILED", "PDF_RENDER").contains(mode5));
            if ("FAILED".equals(mode5)) {
                assertNotNull(m5.get("imagesExtractionError"));
            }
            assertTrue(mockedRenderer.constructed().size() >= 1);
        }

        try (MockedStatic<Loader> mockedLoader = Mockito.mockStatic(Loader.class)) {
            PDDocument closeFailDoc = Mockito.mock(PDDocument.class);
            Mockito.when(closeFailDoc.getNumberOfPages()).thenReturn(0);
            Mockito.doThrow(new RuntimeException("close-only-boom")).when(closeFailDoc).close();
            mockedLoader.when(() -> Loader.loadPDF(Mockito.any(File.class))).thenReturn(closeFailDoc);
            Map<String, Object> m6 = new LinkedHashMap<>();
            Object o6 = invoke(
                    service,
                    "extractPdfImages",
                    new Class<?>[]{Path.class, Map.class, Long.class, String.class, budget.getClass()},
                    tempDir.resolve("close-only-boom.pdf"), m6, 920L, "", budget
            );
            assertTrue(((List<?>) o6).isEmpty());
            assertEquals("FAILED", String.valueOf(m6.get("imagesExtractionMode")));
            assertNotNull(m6.get("imagesExtractionError"));
        }
    }

    private static void consumeBudget(Object budget, long bytes) throws Exception {
        Method m = budget.getClass().getDeclaredMethod("consume", long.class);
        m.setAccessible(true);
        m.invoke(budget, bytes);
    }

    @Test
    void extractPdfImages_shouldCoverResidualBranchesForTask7() throws Exception {
        DerivedUploadStorageService storage = Mockito.mock(DerivedUploadStorageService.class);
        Mockito.when(storage.buildPlaceholder(Mockito.anyInt(), Mockito.anyMap()))
                .thenAnswer(inv -> Map.of("placeholder", "[[IMAGE_" + inv.getArgument(0) + "]]"));
        FileAssetExtractionAsyncService service = new FileAssetExtractionAsyncService(
                null, null, null, new ObjectMapper(), null, null, null, storage
        );
        ReflectionTestUtils.setField(service, "pdfRenderMaxPages", 2);
        ReflectionTestUtils.setField(service, "pdfRenderDpi", 96);
        Object budgetTemplate = newInner("ImageBudget", new Class<?>[]{int.class, long.class}, 10, 1024L * 1024L);

        PDDocument doc = Mockito.mock(PDDocument.class);
        PDPage page = Mockito.mock(PDPage.class);
        Mockito.when(doc.getNumberOfPages()).thenReturn(1);
        Mockito.when(doc.getPage(0)).thenReturn(page);
        Mockito.when(page.getResources()).thenReturn(null);

        try (MockedStatic<Loader> mockedLoader = Mockito.mockStatic(Loader.class)) {
            mockedLoader.when(() -> Loader.loadPDF(Mockito.any(File.class))).thenReturn(doc);
            Object preConsumed = newInner("ImageBudget", new Class<?>[]{int.class, long.class}, 1, 1024L * 1024L);
            consumeBudget(preConsumed, 1L);
            Map<String, Object> m = new LinkedHashMap<>();
            Object o = invoke(
                    service,
                    "extractPdfImages",
                    new Class<?>[]{Path.class, Map.class, Long.class, String.class, budgetTemplate.getClass()},
                    tempDir.resolve("residual-1804.pdf"), m, 921L, "text", preConsumed
            );
            assertTrue(((List<?>) o).isEmpty());
            assertEquals("EMBEDDED_NONE", String.valueOf(m.get("imagesExtractionMode")));
        }

        try (MockedStatic<Loader> mockedLoader = Mockito.mockStatic(Loader.class);
             MockedConstruction<PDFRenderer> mockedRenderer = Mockito.mockConstruction(
                     PDFRenderer.class,
                     (mock, ctx) -> Mockito.when(mock.renderImageWithDPI(Mockito.anyInt(), Mockito.anyInt()))
                             .thenReturn(new BufferedImage(2, 2, BufferedImage.TYPE_INT_RGB))
             )) {
            mockedLoader.when(() -> Loader.loadPDF(Mockito.any(File.class))).thenReturn(doc);

            Object preConsumed = newInner("ImageBudget", new Class<?>[]{int.class, long.class}, 1, 1024L * 1024L);
            consumeBudget(preConsumed, 1L);
            Map<String, Object> m1 = new LinkedHashMap<>();
            Object o1 = invoke(
                    service,
                    "extractPdfImages",
                    new Class<?>[]{Path.class, Map.class, Long.class, String.class, budgetTemplate.getClass()},
                    tempDir.resolve("residual-1849.pdf"), m1, 922L, "", preConsumed
            );
            assertTrue(((List<?>) o1).isEmpty());
            assertEquals("PDF_RENDER", String.valueOf(m1.get("imagesExtractionMode")));

            Mockito.when(storage.saveDerivedImage(Mockito.any(byte[].class), Mockito.anyString(), Mockito.any(), Mockito.any()))
                    .thenReturn(null);
            Object freshBudget = newInner("ImageBudget", new Class<?>[]{int.class, long.class}, 10, 1024L * 1024L);
            Map<String, Object> m2 = new LinkedHashMap<>();
            Object o2 = invoke(
                    service,
                    "extractPdfImages",
                    new Class<?>[]{Path.class, Map.class, Long.class, String.class, budgetTemplate.getClass()},
                    tempDir.resolve("residual-1856.pdf"), m2, 923L, "", freshBudget
            );
            assertTrue(((List<?>) o2).isEmpty());
            assertEquals("PDF_RENDER", String.valueOf(m2.get("imagesExtractionMode")));

            Mockito.when(storage.saveDerivedImage(Mockito.any(byte[].class), Mockito.anyString(), Mockito.any(), Mockito.any()))
                    .thenReturn(Map.of("path", "/tmp/residual-render.png"));
            Map<String, Object> m3 = new LinkedHashMap<>();
            Object o3 = invoke(
                    service,
                    "extractPdfImages",
                    new Class<?>[]{Path.class, Map.class, Long.class, String.class, budgetTemplate.getClass()},
                    tempDir.resolve("residual-1854-1857.pdf"), m3, 924L, "", null
            );
            assertTrue(List.of("PDF_RENDER", "FAILED").contains(String.valueOf(m3.get("imagesExtractionMode"))));

            Object tinyBytesBudget = newInner("ImageBudget", new Class<?>[]{int.class, long.class}, 10, 1L);
            Map<String, Object> m4 = new LinkedHashMap<>();
            Object o4 = invoke(
                    service,
                    "extractPdfImages",
                    new Class<?>[]{Path.class, Map.class, Long.class, String.class, budgetTemplate.getClass()},
                    tempDir.resolve("residual-1853.pdf"), m4, 925L, "", tinyBytesBudget
            );
            assertTrue(((List<?>) o4).isEmpty());
            assertEquals("PDF_RENDER", String.valueOf(m4.get("imagesExtractionMode")));
            assertTrue(mockedRenderer.constructed().size() >= 1);
        }

        try (MockedStatic<Loader> mockedLoader = Mockito.mockStatic(Loader.class);
             MockedConstruction<PDFRenderer> mockedRenderer = Mockito.mockConstruction(
                     PDFRenderer.class,
                     (mock, ctx) -> Mockito.when(mock.renderImageWithDPI(Mockito.anyInt(), Mockito.anyInt()))
                             .thenReturn((BufferedImage) null)
             )) {
            mockedLoader.when(() -> Loader.loadPDF(Mockito.any(File.class))).thenReturn(doc);
            Object freshBudget = newInner("ImageBudget", new Class<?>[]{int.class, long.class}, 10, 1024L * 1024L);
            Map<String, Object> m5 = new LinkedHashMap<>();
            Object o5 = invoke(
                    service,
                    "extractPdfImages",
                    new Class<?>[]{Path.class, Map.class, Long.class, String.class, budgetTemplate.getClass()},
                    tempDir.resolve("residual-1852.pdf"), m5, 926L, "", freshBudget
            );
            assertTrue(((List<?>) o5).isEmpty());
            assertEquals("PDF_RENDER", String.valueOf(m5.get("imagesExtractionMode")));
            assertTrue(mockedRenderer.constructed().size() >= 1);
        }

        try (MockedStatic<Loader> mockedLoader = Mockito.mockStatic(Loader.class)) {
            mockedLoader.when(() -> Loader.loadPDF(Mockito.any(File.class))).thenThrow(new RuntimeException("load-fail"));
            Map<String, Object> m6 = new LinkedHashMap<>();
            Object freshBudget = newInner("ImageBudget", new Class<?>[]{int.class, long.class}, 10, 1024L * 1024L);
            Object o6 = invoke(
                    service,
                    "extractPdfImages",
                    new Class<?>[]{Path.class, Map.class, Long.class, String.class, budgetTemplate.getClass()},
                    tempDir.resolve("residual-catch-load.pdf"), m6, 927L, "", freshBudget
            );
            assertTrue(((List<?>) o6).isEmpty());
            assertEquals("FAILED", String.valueOf(m6.get("imagesExtractionMode")));
            assertNotNull(m6.get("imagesExtractionError"));
        }
    }

    @Test
    void extractPdfImages_shouldForceRenderResidualBranchesDeterministically() throws Exception {
        DerivedUploadStorageService storage = Mockito.mock(DerivedUploadStorageService.class);
        Mockito.when(storage.buildPlaceholder(Mockito.anyInt(), Mockito.anyMap()))
                .thenAnswer(inv -> Map.of("placeholder", "[[IMAGE_" + inv.getArgument(0) + "]]"));
        FileAssetExtractionAsyncService service = new FileAssetExtractionAsyncService(
                null, null, null, new ObjectMapper(), null, null, null, storage
        );
        ReflectionTestUtils.setField(service, "pdfRenderMaxPages", 1);
        ReflectionTestUtils.setField(service, "pdfRenderDpi", 96);
        Object budgetTemplate = newInner("ImageBudget", new Class<?>[]{int.class, long.class}, 10, 1024L * 1024L);

        PDDocument doc = Mockito.mock(PDDocument.class);
        PDPage page = Mockito.mock(PDPage.class);
        Mockito.when(doc.getNumberOfPages()).thenReturn(1);
        Mockito.when(doc.getPage(0)).thenReturn(page);
        Mockito.when(page.getResources()).thenReturn(null);

        try (MockedStatic<Loader> mockedLoader = Mockito.mockStatic(Loader.class);
             MockedConstruction<PDFRenderer> mockedRenderer = Mockito.mockConstruction(
                     PDFRenderer.class,
                     (mock, ctx) -> Mockito.when(mock.renderImageWithDPI(Mockito.anyInt(), Mockito.anyInt()))
                             .thenReturn(new BufferedImage(2, 2, BufferedImage.TYPE_INT_RGB))
             )) {
            mockedLoader.when(() -> Loader.loadPDF(Mockito.any(File.class))).thenReturn(doc);
            Mockito.when(storage.saveDerivedImage(Mockito.any(byte[].class), Mockito.anyString(), Mockito.any(), Mockito.any()))
                    .thenReturn(Map.of("path", "/tmp/render-b-null.png"));
            Map<String, Object> m1 = new LinkedHashMap<>();
            Object o1 = invoke(
                    service,
                    "extractPdfImages",
                    new Class<?>[]{Path.class, Map.class, Long.class, String.class, budgetTemplate.getClass()},
                    tempDir.resolve("force-render-budget-null.pdf"), m1, 928L, "", null
            );
            assertTrue(List.of("PDF_RENDER", "FAILED").contains(String.valueOf(m1.get("imagesExtractionMode"))));
            assertTrue(mockedRenderer.constructed().size() >= 1);
        }

        try (MockedStatic<Loader> mockedLoader = Mockito.mockStatic(Loader.class);
             MockedConstruction<PDFRenderer> mockedRenderer = Mockito.mockConstruction(
                     PDFRenderer.class,
                     (mock, ctx) -> Mockito.when(mock.renderImageWithDPI(Mockito.anyInt(), Mockito.anyInt()))
                             .thenReturn(new BufferedImage(2, 2, BufferedImage.TYPE_INT_RGB))
             )) {
            mockedLoader.when(() -> Loader.loadPDF(Mockito.any(File.class))).thenReturn(doc);
            Mockito.when(storage.saveDerivedImage(Mockito.any(byte[].class), Mockito.anyString(), Mockito.any(), Mockito.any()))
                    .thenReturn(null);
            Object budget = newInner("ImageBudget", new Class<?>[]{int.class, long.class}, 10, 1024L * 1024L);
            Map<String, Object> m2 = new LinkedHashMap<>();
            Object o2 = invoke(
                    service,
                    "extractPdfImages",
                    new Class<?>[]{Path.class, Map.class, Long.class, String.class, budgetTemplate.getClass()},
                    tempDir.resolve("force-render-save-null.pdf"), m2, 929L, "", budget
            );
            assertTrue(((List<?>) o2).isEmpty());
            assertEquals("PDF_RENDER", String.valueOf(m2.get("imagesExtractionMode")));
            assertTrue(mockedRenderer.constructed().size() >= 1);
        }

        try (MockedStatic<Loader> mockedLoader = Mockito.mockStatic(Loader.class);
             MockedConstruction<PDFRenderer> mockedRenderer = Mockito.mockConstruction(
                     PDFRenderer.class,
                     (mock, ctx) -> Mockito.when(mock.renderImageWithDPI(Mockito.anyInt(), Mockito.anyInt()))
                             .thenReturn(new BufferedImage(2, 2, BufferedImage.TYPE_INT_RGB))
             )) {
            mockedLoader.when(() -> Loader.loadPDF(Mockito.any(File.class))).thenReturn(doc);
            Mockito.when(storage.saveDerivedImage(Mockito.any(byte[].class), Mockito.anyString(), Mockito.any(), Mockito.any()))
                    .thenReturn(Map.of("path", "/tmp/render-byte-break.png"));
            Object tinyBudget = newInner("ImageBudget", new Class<?>[]{int.class, long.class}, 10, 1L);
            Map<String, Object> m3 = new LinkedHashMap<>();
            Object o3 = invoke(
                    service,
                    "extractPdfImages",
                    new Class<?>[]{Path.class, Map.class, Long.class, String.class, budgetTemplate.getClass()},
                    tempDir.resolve("force-render-byte-break.pdf"), m3, 930L, "", tinyBudget
            );
            assertTrue(((List<?>) o3).isEmpty());
            assertEquals("PDF_RENDER", String.valueOf(m3.get("imagesExtractionMode")));
            assertTrue(mockedRenderer.constructed().size() >= 1);
        }

        try (MockedStatic<Loader> mockedLoader = Mockito.mockStatic(Loader.class);
             MockedConstruction<PDFRenderer> mockedRenderer = Mockito.mockConstruction(
                     PDFRenderer.class,
                     (mock, ctx) -> Mockito.when(mock.renderImageWithDPI(Mockito.anyInt(), Mockito.anyInt()))
                             .thenReturn((BufferedImage) null)
             )) {
            mockedLoader.when(() -> Loader.loadPDF(Mockito.any(File.class))).thenReturn(doc);
            Object budget = newInner("ImageBudget", new Class<?>[]{int.class, long.class}, 10, 1024L * 1024L);
            Map<String, Object> m4 = new LinkedHashMap<>();
            Object o4 = invoke(
                    service,
                    "extractPdfImages",
                    new Class<?>[]{Path.class, Map.class, Long.class, String.class, budgetTemplate.getClass()},
                    tempDir.resolve("force-render-null-image.pdf"), m4, 931L, "", budget
            );
            assertTrue(((List<?>) o4).isEmpty());
            assertEquals("PDF_RENDER", String.valueOf(m4.get("imagesExtractionMode")));
            assertTrue(mockedRenderer.constructed().size() >= 1);
        }
    }

    @Test
    void extractPdfImages_shouldCoverRenderBranchMatrixWithRealBlankPdf() throws Exception {
        DerivedUploadStorageService storage = Mockito.mock(DerivedUploadStorageService.class);
        Mockito.when(storage.buildPlaceholder(Mockito.anyInt(), Mockito.anyMap()))
                .thenAnswer(inv -> Map.of("placeholder", "[[IMAGE_" + inv.getArgument(0) + "]]"));
        FileAssetExtractionAsyncService service = new FileAssetExtractionAsyncService(
                null, null, null, new ObjectMapper(), null, null, null, storage
        );
        ReflectionTestUtils.setField(service, "pdfRenderMaxPages", 1);
        ReflectionTestUtils.setField(service, "pdfRenderDpi", 96);
        Object budgetTemplate = newInner("ImageBudget", new Class<?>[]{int.class, long.class}, 10, 1024L * 1024L);

        Path blank = tempDir.resolve("render-matrix-blank.pdf");
        try (PDDocument doc = new PDDocument()) {
            doc.addPage(new PDPage());
            doc.save(blank.toFile());
        }

        Mockito.when(storage.saveDerivedImage(Mockito.any(byte[].class), Mockito.anyString(), Mockito.any(), Mockito.any()))
                .thenReturn(Map.of("path", "/tmp/render-matrix-ok.png"));
        Object budgetA = newInner("ImageBudget", new Class<?>[]{int.class, long.class}, 10, 1024L * 1024L);
        Map<String, Object> m1 = new LinkedHashMap<>();
        Object o1 = invoke(
                service,
                "extractPdfImages",
                new Class<?>[]{Path.class, Map.class, Long.class, String.class, budgetTemplate.getClass()},
                blank, m1, 932L, "", budgetA
        );
        assertFalse(((List<?>) o1).isEmpty());
        assertEquals("PDF_RENDER", String.valueOf(m1.get("imagesExtractionMode")));

        Mockito.when(storage.saveDerivedImage(Mockito.any(byte[].class), Mockito.anyString(), Mockito.any(), Mockito.any()))
                .thenReturn(null);
        Object budgetB = newInner("ImageBudget", new Class<?>[]{int.class, long.class}, 10, 1024L * 1024L);
        Map<String, Object> m2 = new LinkedHashMap<>();
        Object o2 = invoke(
                service,
                "extractPdfImages",
                new Class<?>[]{Path.class, Map.class, Long.class, String.class, budgetTemplate.getClass()},
                blank, m2, 933L, "", budgetB
        );
        assertTrue(((List<?>) o2).isEmpty());
        assertEquals("PDF_RENDER", String.valueOf(m2.get("imagesExtractionMode")));

        Mockito.when(storage.saveDerivedImage(Mockito.any(byte[].class), Mockito.anyString(), Mockito.any(), Mockito.any()))
                .thenReturn(Map.of("path", "/tmp/render-matrix-byte-break.png"));
        Object budgetC = newInner("ImageBudget", new Class<?>[]{int.class, long.class}, 10, 1L);
        Map<String, Object> m3 = new LinkedHashMap<>();
        Object o3 = invoke(
                service,
                "extractPdfImages",
                new Class<?>[]{Path.class, Map.class, Long.class, String.class, budgetTemplate.getClass()},
                blank, m3, 934L, "", budgetC
        );
        assertTrue(((List<?>) o3).isEmpty());
        assertEquals("PDF_RENDER", String.valueOf(m3.get("imagesExtractionMode")));

        try (MockedConstruction<PDFRenderer> mockedRenderer = Mockito.mockConstruction(
                PDFRenderer.class,
                (mock, ctx) -> Mockito.when(mock.renderImageWithDPI(Mockito.anyInt(), Mockito.anyInt()))
                        .thenReturn((BufferedImage) null)
        )) {
            Object budgetD = newInner("ImageBudget", new Class<?>[]{int.class, long.class}, 10, 1024L * 1024L);
            Map<String, Object> m4 = new LinkedHashMap<>();
            Object o4 = invoke(
                    service,
                    "extractPdfImages",
                    new Class<?>[]{Path.class, Map.class, Long.class, String.class, budgetTemplate.getClass()},
                    blank, m4, 935L, "", budgetD
            );
            assertTrue(((List<?>) o4).isEmpty());
            assertEquals("PDF_RENDER", String.valueOf(m4.get("imagesExtractionMode")));
            assertTrue(mockedRenderer.constructed().size() >= 1);
        }
    }

    @Test
    void extractPdfImages_shouldCoverBufferedImageToPngNullBranches() throws Exception {
        DerivedUploadStorageService storage = Mockito.mock(DerivedUploadStorageService.class);
        FileAssetExtractionAsyncService service = new FileAssetExtractionAsyncService(
                null, null, null, new ObjectMapper(), null, null, null, storage
        );
        ReflectionTestUtils.setField(service, "pdfRenderMaxPages", 1);
        ReflectionTestUtils.setField(service, "pdfRenderDpi", 96);
        Object budget = newInner("ImageBudget", new Class<?>[]{int.class, long.class}, 10, 1024L * 1024L);

        Path withImage = createPdfWithImage();
        Path blank = tempDir.resolve("png-null-blank.pdf");
        try (PDDocument doc = new PDDocument()) {
            doc.addPage(new PDPage());
            doc.save(blank.toFile());
        }

        try (MockedStatic<ImageIO> mockedImageIo = Mockito.mockStatic(ImageIO.class)) {
            mockedImageIo.when(() -> ImageIO.write(Mockito.any(), Mockito.eq("png"), Mockito.any(java.io.OutputStream.class)))
                    .thenThrow(new RuntimeException("imgio-boom"));

            Map<String, Object> m1 = new LinkedHashMap<>();
            Object o1 = invoke(
                    service,
                    "extractPdfImages",
                    new Class<?>[]{Path.class, Map.class, Long.class, String.class, budget.getClass()},
                    withImage, m1, 936L, "has text", budget
            );
            assertTrue(((List<?>) o1).isEmpty());
            assertEquals("EMBEDDED_NONE", String.valueOf(m1.get("imagesExtractionMode")));

            Object budget2 = newInner("ImageBudget", new Class<?>[]{int.class, long.class}, 10, 1024L * 1024L);
            Map<String, Object> m2 = new LinkedHashMap<>();
            Object o2 = invoke(
                    service,
                    "extractPdfImages",
                    new Class<?>[]{Path.class, Map.class, Long.class, String.class, budget.getClass()},
                    blank, m2, 937L, "", budget2
            );
            assertTrue(((List<?>) o2).isEmpty());
            assertEquals("PDF_RENDER", String.valueOf(m2.get("imagesExtractionMode")));
        }
    }

    @Test
    void extractPdfImages_shouldCoverRenderBytesZeroBranch() throws Exception {
        DerivedUploadStorageService storage = Mockito.mock(DerivedUploadStorageService.class);
        FileAssetExtractionAsyncService service = new FileAssetExtractionAsyncService(
                null, null, null, new ObjectMapper(), null, null, null, storage
        );
        ReflectionTestUtils.setField(service, "pdfRenderMaxPages", 1);
        ReflectionTestUtils.setField(service, "pdfRenderDpi", 96);
        Object budget = newInner("ImageBudget", new Class<?>[]{int.class, long.class}, 10, 1024L * 1024L);

        Path blank = tempDir.resolve("render-bytes-zero-blank.pdf");
        try (PDDocument doc = new PDDocument()) {
            doc.addPage(new PDPage());
            doc.save(blank.toFile());
        }

        try (MockedStatic<ImageIO> mockedImageIo = Mockito.mockStatic(ImageIO.class)) {
            mockedImageIo.when(() -> ImageIO.write(Mockito.any(), Mockito.eq("png"), Mockito.any(java.io.OutputStream.class)))
                    .thenReturn(false);
            Map<String, Object> m = new LinkedHashMap<>();
            Object o = invoke(
                    service,
                    "extractPdfImages",
                    new Class<?>[]{Path.class, Map.class, Long.class, String.class, budget.getClass()},
                    blank, m, 938L, "", budget
            );
            assertTrue(((List<?>) o).isEmpty());
            assertEquals("PDF_RENDER", String.valueOf(m.get("imagesExtractionMode")));
        }
    }
}
