package com.example.EnterpriseRagCommunity.service.monitor;

import com.example.EnterpriseRagCommunity.entity.monitor.FileAssetExtractionsEntity;
import com.example.EnterpriseRagCommunity.entity.monitor.FileAssetsEntity;
import com.example.EnterpriseRagCommunity.repository.monitor.FileAssetExtractionsRepository;
import com.example.EnterpriseRagCommunity.repository.monitor.FileAssetsRepository;
import com.example.EnterpriseRagCommunity.repository.semantic.VectorIndicesRepository;
import com.example.EnterpriseRagCommunity.service.ai.TokenCountService;
import com.example.EnterpriseRagCommunity.service.retrieval.RagFileAssetIndexAsyncService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.ByteArrayOutputStream;
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
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.Mockito.when;

class FileAssetExtractionAsyncServiceOrChainCoverageTest {
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
        ReflectionTestUtils.setField(service, "pdfRenderMaxPages", 2);
        ReflectionTestUtils.setField(service, "pdfRenderDpi", 72);
    }

    private Object invokeStatic(String name, Class<?>[] types, Object... args) throws Exception {
        Method m = FileAssetExtractionAsyncService.class.getDeclaredMethod(name, types);
        m.setAccessible(true);
        return m.invoke(null, args);
    }

    private Object invokeInstance(String name, Class<?>[] types, Object... args) throws Exception {
        Method m = FileAssetExtractionAsyncService.class.getDeclaredMethod(name, types);
        m.setAccessible(true);
        return m.invoke(service, args);
    }

    private Object newInner(String simpleName, Class<?>[] types, Object... args) throws Exception {
        Class<?> c = Class.forName(FileAssetExtractionAsyncService.class.getName() + "$" + simpleName);
        Constructor<?> ctor = c.getDeclaredConstructor(types);
        ctor.setAccessible(true);
        return ctor.newInstance(args);
    }

    @Test
    void looksLike7zBytes_shouldCoverSequentialChecks() throws Exception {
        assertEquals(false, invokeStatic("looksLike7zBytes", new Class<?>[]{byte[].class}, (Object) null));
        assertEquals(false, invokeStatic("looksLike7zBytes", new Class<?>[]{byte[].class}, new byte[]{0x37, 0x7A, (byte) 0xBC, (byte) 0xAF, 0x27}));
        assertEquals(false, invokeStatic("looksLike7zBytes", new Class<?>[]{byte[].class}, new byte[]{0x00, 0x7A, (byte) 0xBC, (byte) 0xAF, 0x27, 0x1C}));
        assertEquals(false, invokeStatic("looksLike7zBytes", new Class<?>[]{byte[].class}, new byte[]{0x37, 0x00, (byte) 0xBC, (byte) 0xAF, 0x27, 0x1C}));
        assertEquals(false, invokeStatic("looksLike7zBytes", new Class<?>[]{byte[].class}, new byte[]{0x37, 0x7A, 0x00, (byte) 0xAF, 0x27, 0x1C}));
        assertEquals(false, invokeStatic("looksLike7zBytes", new Class<?>[]{byte[].class}, new byte[]{0x37, 0x7A, (byte) 0xBC, 0x00, 0x27, 0x1C}));
        assertEquals(false, invokeStatic("looksLike7zBytes", new Class<?>[]{byte[].class}, new byte[]{0x37, 0x7A, (byte) 0xBC, (byte) 0xAF, 0x00, 0x1C}));
        assertEquals(false, invokeStatic("looksLike7zBytes", new Class<?>[]{byte[].class}, new byte[]{0x37, 0x7A, (byte) 0xBC, (byte) 0xAF, 0x27, 0x00}));
        assertEquals(true, invokeStatic("looksLike7zBytes", new Class<?>[]{byte[].class}, new byte[]{0x37, 0x7A, (byte) 0xBC, (byte) 0xAF, 0x27, 0x1C}));
    }

    @Test
    void extractEntryBytesAsText_shouldCoverTextExtDecisionChain() throws Exception {
        byte[] bytes = "alpha beta".getBytes(StandardCharsets.UTF_8);
        List<String> exts = List.of("txt", "md", "markdown", "csv", "json", "xml", "yaml", "yml");
        for (String ext : exts) {
            Object out = invokeInstance("extractEntryBytesAsText",
                    new Class<?>[]{String.class, String.class, byte[].class, int.class},
                    "a." + ext, ext, bytes, 100);
            assertTrue(String.valueOf(out).contains("alpha"));
        }
    }

    @Test
    void extractText_and_extractImages_shouldCoverDispatchChains() throws Exception {
        Path txt = tempDir.resolve("x.txt");
        Files.writeString(txt, "hello");
        Path html = tempDir.resolve("x.html");
        Files.writeString(html, "<b>hello</b>");

        Object budget = newInner("ImageBudget", new Class<?>[]{int.class, long.class}, 10, 100000L);
        Map<String, Object> meta = new LinkedHashMap<>();
        assertNotNull(invokeInstance("extractText",
                new Class<?>[]{Path.class, String.class, int.class, Map.class, Long.class, budget.getClass()},
                txt, "txt", 100, meta, 1L, budget));
        assertNotNull(invokeInstance("extractText",
                new Class<?>[]{Path.class, String.class, int.class, Map.class, Long.class, budget.getClass()},
                html, "html", 100, meta, 1L, budget));

        invokeInstance("extractImages",
                new Class<?>[]{Path.class, String.class, Map.class, Long.class, String.class, budget.getClass()},
                txt, "txt", meta, 1L, "hello", budget);
        assertEquals("NONE", String.valueOf(meta.get("imagesExtractionMode")));

        meta.clear();
        invokeInstance("extractImages",
                new Class<?>[]{Path.class, String.class, Map.class, Long.class, String.class, budget.getClass()},
                txt, "json", meta, 1L, "hello", budget);
        assertEquals("NONE", String.valueOf(meta.get("imagesExtractionMode")));

        meta.clear();
        invokeInstance("extractImages",
                new Class<?>[]{Path.class, String.class, Map.class, Long.class, String.class, budget.getClass()},
                txt, "abc", meta, 1L, "hello", budget);
        assertEquals("UNSUPPORTED", String.valueOf(meta.get("imagesExtractionMode")));
    }

    @Test
    void extractEpubImages_shouldCoverBudgetSizeAndSaveNullBranches() throws Exception {
        byte[] img = new byte[]{1, 2, 3, 4, 5, 6};
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (ZipOutputStream zos = new ZipOutputStream(baos)) {
            zos.putNextEntry(new ZipEntry("a.png"));
            zos.write(img);
            zos.closeEntry();
            zos.putNextEntry(new ZipEntry("b.txt"));
            zos.write("text".getBytes(StandardCharsets.UTF_8));
            zos.closeEntry();
        }
        Path epub = tempDir.resolve("x.epub");
        Files.write(epub, baos.toByteArray());

        when(derivedUploadStorageService.getMaxImageBytes()).thenReturn(2L);
        when(derivedUploadStorageService.saveDerivedImage(any(), any(), any(), any())).thenReturn(null);
        Object budget = newInner("ImageBudget", new Class<?>[]{int.class, long.class}, 10, 100000L);
        Map<String, Object> meta = new LinkedHashMap<>();
        Object out = invokeInstance("extractEpubImages",
                new Class<?>[]{Path.class, Map.class, Long.class, budget.getClass()},
                epub, meta, 1L, budget);
        assertNotNull(out);
        assertEquals("EPUB_ZIP", String.valueOf(meta.get("imagesExtractionMode")));
    }

    @Test
    void extractMobiImagesWithTika_shouldCoverZipFallbackBudgetBreak() throws Exception {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (ZipOutputStream zos = new ZipOutputStream(baos)) {
            zos.putNextEntry(new ZipEntry("a.png"));
            zos.write(new byte[]{1, 2, 3});
            zos.closeEntry();
            zos.putNextEntry(new ZipEntry("b.jpg"));
            zos.write(new byte[]{4, 5, 6});
            zos.closeEntry();
        }
        Path mobi = tempDir.resolve("x.mobi");
        Files.write(mobi, baos.toByteArray());

        when(derivedUploadStorageService.getMaxImageBytes()).thenReturn(100000L);
        when(derivedUploadStorageService.saveDerivedImage(any(), any(), any(), any())).thenReturn(Map.of("id", "1"));
        when(derivedUploadStorageService.buildPlaceholder(anyInt(), anyMap())).thenReturn(Map.of("placeholder", "[[IMAGE_1]]"));
        Object budget = newInner("ImageBudget", new Class<?>[]{int.class, long.class}, 1, 10L);
        Map<String, Object> meta = new LinkedHashMap<>();
        Object out = invokeInstance("extractMobiImagesWithTika",
                new Class<?>[]{Path.class, Map.class, Long.class, budget.getClass()},
                mobi, meta, 1L, budget);
        assertNotNull(out);
        assertTrue("MOBI_ZIP".equals(String.valueOf(meta.get("imagesExtractionMode")))
                || "MOBI_TIKA_EMBEDDED".equals(String.valueOf(meta.get("imagesExtractionMode"))));
    }

    @Test
    void saveSlideImagePlaceholder_shouldNormalizeBlankExtAndMime() throws Exception {
        when(derivedUploadStorageService.saveDerivedImage(any(), any(), any(), any())).thenReturn(Map.of("id", "1"));
        when(derivedUploadStorageService.buildPlaceholder(anyInt(), anyMap())).thenReturn(Map.of("placeholder", "[[IMAGE_1]]"));

        Object budget = newInner("ImageBudget", new Class<?>[]{int.class, long.class}, 10, 100000L);
        List<Map<String, Object>> out = new java.util.ArrayList<>();
        Object result = invokeInstance("saveSlideImagePlaceholder",
                new Class<?>[]{byte[].class, int.class, String.class, String.class, String.class, Long.class, budget.getClass(), List.class},
                new byte[]{1, 2, 3}, 2, " ", null, "ppt_image_", 1L, budget, out);
        assertEquals(false, result);
        assertEquals(1, out.size());
    }
}
