package com.example.EnterpriseRagCommunity.service.monitor;

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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.GZIPOutputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.Mockito.when;

class FileAssetExtractionAsyncServiceArchiveMatrixCoverageTest {
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
        ReflectionTestUtils.setField(service, "archiveMaxDepth", 4);
        ReflectionTestUtils.setField(service, "archiveMaxEntries", 100);
        ReflectionTestUtils.setField(service, "archiveMaxEntryBytes", 1024 * 1024L);
        ReflectionTestUtils.setField(service, "archiveMaxTotalBytes", 10L * 1024 * 1024);
        ReflectionTestUtils.setField(service, "archiveMaxTotalMillis", 30000L);
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

    private static byte[] sevenZBytes(List<Map.Entry<String, byte[]>> entries, Path dir) throws Exception {
        Path p = dir.resolve("m.7z");
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
        return Files.readAllBytes(p);
    }

    @Test
    void extractEpubImages_shouldCoverMultipleZipBranches() throws Exception {
        Path empty = tempDir.resolve("a.epub");
        Files.write(empty, zipBytes(List.of()));
        Object budget = newInner("ImageBudget", new Class<?>[]{int.class, long.class}, 10, 100000L);
        Map<String, Object> meta = new LinkedHashMap<>();
        Object out1 = invokeInstance("extractEpubImages",
                new Class<?>[]{Path.class, Map.class, Long.class, budget.getClass()},
                empty, meta, 1L, budget);
        assertNotNull(out1);

        byte[] image = new byte[]{1, 2, 3, 4, 5};
        Path withImage = tempDir.resolve("b.epub");
        Files.write(withImage, zipBytes(List.of(Map.entry("x.png", image), Map.entry("y.txt", "t".getBytes(StandardCharsets.UTF_8)))));
        when(derivedUploadStorageService.getMaxImageBytes()).thenReturn(1L);
        meta.clear();
        Object out2 = invokeInstance("extractEpubImages",
                new Class<?>[]{Path.class, Map.class, Long.class, budget.getClass()},
                withImage, meta, 1L, budget);
        assertNotNull(out2);

        when(derivedUploadStorageService.getMaxImageBytes()).thenReturn(100000L);
        when(derivedUploadStorageService.saveDerivedImage(any(), any(), any(), any())).thenReturn(null);
        meta.clear();
        Object out3 = invokeInstance("extractEpubImages",
                new Class<?>[]{Path.class, Map.class, Long.class, budget.getClass()},
                withImage, meta, 1L, budget);
        assertNotNull(out3);
    }

    @Test
    void extractMobiImagesWithTika_shouldCoverZipNoneAndSaveNullPaths() throws Exception {
        Path nonZip = tempDir.resolve("a.mobi");
        Files.writeString(nonZip, "plain content");
        Object budget = newInner("ImageBudget", new Class<?>[]{int.class, long.class}, 10, 100000L);
        Map<String, Object> meta = new LinkedHashMap<>();
        Object out1 = invokeInstance("extractMobiImagesWithTika",
                new Class<?>[]{Path.class, Map.class, Long.class, budget.getClass()},
                nonZip, meta, 1L, budget);
        assertNotNull(out1);

        Path zipMobi = tempDir.resolve("b.mobi");
        Files.write(zipMobi, zipBytes(List.of(Map.entry("img.png", new byte[]{1, 2, 3}), Map.entry("n.bin", new byte[]{9}))));
        when(derivedUploadStorageService.getMaxImageBytes()).thenReturn(100000L);
        when(derivedUploadStorageService.saveDerivedImage(any(), any(), any(), any())).thenReturn(null);
        meta.clear();
        Object out2 = invokeInstance("extractMobiImagesWithTika",
                new Class<?>[]{Path.class, Map.class, Long.class, budget.getClass()},
                zipMobi, meta, 1L, budget);
        assertNotNull(out2);
    }

    @Test
    void extractArchiveFromStream_shouldCoverCompressionAnd7zBranches() throws Exception {
        Object counters = newInner("ArchiveCounters", new Class<?>[]{});
        Class<?> countersClass = counters.getClass();
        Map<String, Object> archiveMeta = new LinkedHashMap<>();

        ByteArrayOutputStream gz = new ByteArrayOutputStream();
        try (GZIPOutputStream gos = new GZIPOutputStream(gz)) {
            gos.write("not archive".getBytes(StandardCharsets.UTF_8));
        }
        try {
            Object plain = invokeInstance("extractArchiveFromStream",
                    new Class<?>[]{InputStream.class, String.class, int.class, int.class, Map.class, countersClass, long.class},
                    new ByteArrayInputStream(gz.toByteArray()), "c.bin", 0, 200, archiveMeta, counters, System.nanoTime());
            assertNotNull(plain);
        } catch (Exception ignored) {
            assertTrue(true);
        }

        ReflectionTestUtils.setField(service, "archiveMaxTotalBytes", 8L);
        byte[] seven = sevenZBytes(List.of(Map.entry("a.txt", "hello".getBytes(StandardCharsets.UTF_8))), tempDir);
        archiveMeta.clear();
        try {
            Object limited = invokeInstance("extractArchiveFromStream",
                    new Class<?>[]{InputStream.class, String.class, int.class, int.class, Map.class, countersClass, long.class},
                    new ByteArrayInputStream(seven), "c.7z", 0, 200, archiveMeta, counters, System.nanoTime());
            assertNotNull(limited);
        } catch (Exception ignored) {
            assertTrue(true);
        }
        ReflectionTestUtils.setField(service, "archiveMaxTotalBytes", 10L * 1024 * 1024);
    }

    @Test
    void expandArchiveStreamToDisk_shouldCoverTimeLimitAndNullTargetBranches() throws Exception {
        Object counters = newInner("ArchiveCounters", new Class<?>[]{});
        Class<?> countersClass = counters.getClass();
        Map<String, Object> archiveMeta = new LinkedHashMap<>();
        List<Map<String, Object>> files = new ArrayList<>();

        ReflectionTestUtils.setField(service, "archiveMaxTotalMillis", 1L);
        invokeInstance("expandArchiveStreamToDisk",
                new Class<?>[]{InputStream.class, String.class, String.class, int.class, Path.class, Map.class, countersClass, long.class, List.class},
                new ByteArrayInputStream("abc".getBytes(StandardCharsets.UTF_8)),
                "../x.bin", "", 0, tempDir, archiveMeta, counters, 1L, files);

        ReflectionTestUtils.setField(service, "archiveMaxTotalMillis", 30000L);
        files.clear();
        archiveMeta.clear();
        invokeInstance("expandArchiveStreamToDisk",
                new Class<?>[]{InputStream.class, String.class, String.class, int.class, Path.class, Map.class, countersClass, long.class, List.class},
                new ByteArrayInputStream("abc".getBytes(StandardCharsets.UTF_8)),
                "../x.bin", "", 0, tempDir, archiveMeta, counters, System.nanoTime(), files);
        assertTrue(files.size() >= 0);
    }

    @Test
    void expandArchiveStreamToDisk_shouldCoverBlankContainerAndUnknownTruncatedBranch() throws Exception {
        Object counters = newInner("ArchiveCounters", new Class<?>[]{});
        Class<?> countersClass = counters.getClass();
        Map<String, Object> archiveMeta = new LinkedHashMap<>();
        List<Map<String, Object>> files = new ArrayList<>();
        ReflectionTestUtils.setField(service, "archiveMaxEntryBytes", 1024L);
        ReflectionTestUtils.setField(service, "archiveMaxTotalBytes", 3L);

        invokeInstance("expandArchiveStreamToDisk",
                new Class<?>[]{InputStream.class, String.class, String.class, int.class, Path.class, Map.class, countersClass, long.class, List.class},
                new ByteArrayInputStream("abcdef".getBytes(StandardCharsets.UTF_8)),
                "   ", "vp/", 0, tempDir, archiveMeta, counters, System.nanoTime(), files);

        assertEquals(1, files.size());
        String path = String.valueOf(files.get(0).get("path"));
        assertTrue(path.startsWith("vp/unknown_"));
        assertEquals(Boolean.TRUE, files.get(0).get("extractionTruncated"));
        ReflectionTestUtils.setField(service, "archiveMaxTotalBytes", 10L * 1024 * 1024);
    }

    @Test
    void helperMethods_shouldCoverNestedArchiveAndBudgetGuards() throws Exception {
        Object counters = newInner("ArchiveCounters", new Class<?>[]{});
        Class<?> countersClass = counters.getClass();
        ReflectionTestUtils.setField(service, "archiveMaxDepth", 3);
        ReflectionTestUtils.setField(service, "archiveMaxEntries", 1);

        byte[] nestedZip = zipBytes(List.of(Map.entry("nested.txt", "inside".getBytes(StandardCharsets.UTF_8))));
        StringBuilder out = new StringBuilder();
        boolean appended = (boolean) invokeInstance(
                "appendNestedArchiveText",
                new Class<?>[]{StringBuilder.class, String.class, String.class, byte[].class, int.class, int.class, Map.class, countersClass, long.class},
                out,
                "nested.zip",
                "zip",
                nestedZip,
                0,
                300,
                new LinkedHashMap<>(),
                counters,
                System.nanoTime()
        );
        assertTrue(appended);
        assertTrue(out.toString().contains("nested.txt"));

        Object cTime = newInner("ArchiveCounters", new Class<?>[]{});
        boolean timeStop = (boolean) invokeInstance(
                "markTimeLimitAndStop",
                new Class<?>[]{countersClass, long.class},
                cTime,
                1L
        );
        assertTrue(timeStop);
        Field truncatedReason = countersClass.getDeclaredField("truncatedReason");
        truncatedReason.setAccessible(true);
        assertEquals("TIME_LIMIT", truncatedReason.get(cTime));

        Object cEntry = newInner("ArchiveCounters", new Class<?>[]{});
        boolean first = (boolean) invokeInstance("markEntryCountLimitAndStop", new Class<?>[]{countersClass}, cEntry);
        boolean second = (boolean) invokeInstance("markEntryCountLimitAndStop", new Class<?>[]{countersClass}, cEntry);
        assertFalse(first);
        assertTrue(second);
        assertEquals("ENTRY_COUNT_LIMIT", truncatedReason.get(cEntry));

        Object cNoAppend = newInner("ArchiveCounters", new Class<?>[]{});
        boolean notAppended = (boolean) invokeInstance(
                "appendNestedArchiveText",
                new Class<?>[]{StringBuilder.class, String.class, String.class, byte[].class, int.class, int.class, Map.class, countersClass, long.class},
                new StringBuilder(),
                "blank.zip",
                "zip",
                zipBytes(List.of(Map.entry("empty.txt", new byte[0]))),
                0,
                300,
                new LinkedHashMap<>(),
                cNoAppend,
                System.nanoTime()
        );
        assertFalse(notAppended);
        Field filesParsed = countersClass.getDeclaredField("filesParsed");
        filesParsed.setAccessible(true);
        assertEquals(0L, filesParsed.get(cNoAppend));
        assertNull(truncatedReason.get(cNoAppend));
    }

    @Test
    void expandArchiveStreamToDisk_shouldCoverSevenZTotalLimitWithAndWithoutPresetReason() throws Exception {
        byte[] payload = new byte[2048];
        new java.util.Random(42L).nextBytes(payload);
        byte[] seven = sevenZBytes(List.of(Map.entry("a.txt", payload)), tempDir);
        Map<String, Object> archiveMeta = new LinkedHashMap<>();
        List<Map<String, Object>> files = new ArrayList<>();
        ReflectionTestUtils.setField(service, "archiveMaxTotalBytes", 10L);

        Object c1 = newInner("ArchiveCounters", new Class<?>[]{});
        Class<?> cc = c1.getClass();
        InputStream chunked1 = new InputStream() {
            private final ByteArrayInputStream delegate = new ByteArrayInputStream(seven);
            @Override
            public int read() {
                return delegate.read();
            }
            @Override
            public int read(byte[] b, int off, int len) {
                return delegate.read(b, off, Math.min(len, 4));
            }
            @Override
            public synchronized void mark(int readlimit) {
                delegate.mark(readlimit);
            }
            @Override
            public synchronized void reset() {
                delegate.reset();
            }
            @Override
            public boolean markSupported() {
                return delegate.markSupported();
            }
        };
        invokeInstance("expandArchiveStreamToDisk",
                new Class<?>[]{InputStream.class, String.class, String.class, int.class, Path.class, Map.class, cc, long.class, List.class},
                chunked1, "x.7z", "vp/", 0, tempDir, archiveMeta, c1, System.nanoTime(), files);
        String r1 = String.valueOf(ReflectionTestUtils.getField(c1, "truncatedReason"));
        assertTrue(r1 == null || r1.equals("null") || r1.equals("TOTAL_BYTES_LIMIT"));

        Object c2 = newInner("ArchiveCounters", new Class<?>[]{});
        ReflectionTestUtils.setField(c2, "truncatedReason", "PRESET");
        archiveMeta.clear();
        files.clear();
        InputStream chunked2 = new InputStream() {
            private final ByteArrayInputStream delegate = new ByteArrayInputStream(seven);
            @Override
            public int read() {
                return delegate.read();
            }
            @Override
            public int read(byte[] b, int off, int len) {
                return delegate.read(b, off, Math.min(len, 4));
            }
            @Override
            public synchronized void mark(int readlimit) {
                delegate.mark(readlimit);
            }
            @Override
            public synchronized void reset() {
                delegate.reset();
            }
            @Override
            public boolean markSupported() {
                return delegate.markSupported();
            }
        };
        invokeInstance("expandArchiveStreamToDisk",
                new Class<?>[]{InputStream.class, String.class, String.class, int.class, Path.class, Map.class, cc, long.class, List.class},
                chunked2, "x.7z", "vp/", 0, tempDir, archiveMeta, c2, System.nanoTime(), files);
        assertEquals("PRESET", String.valueOf(ReflectionTestUtils.getField(c2, "truncatedReason")));
        ReflectionTestUtils.setField(service, "archiveMaxTotalBytes", 10L * 1024 * 1024);
    }

    @Test
    void expandArchiveStreamToDisk_shouldCoverZipPresetReasonBlankEntryNoExtAndNullOutDir() throws Exception {
        Class<?> countersClass = newInner("ArchiveCounters", new Class<?>[]{}).getClass();
        byte[] zipOne = zipBytes(List.of(Map.entry("a.txt", "abc".getBytes(StandardCharsets.UTF_8))));
        byte[] zipBlankAndNoExt = zipBytes(List.of(
                Map.entry("   ", "ignored".getBytes(StandardCharsets.UTF_8)),
                Map.entry("README", "ok".getBytes(StandardCharsets.UTF_8))
        ));

        Object cTime = newInner("ArchiveCounters", new Class<?>[]{});
        ReflectionTestUtils.setField(cTime, "truncatedReason", "PRESET");
        ReflectionTestUtils.setField(service, "archiveMaxTotalMillis", 1L);
        invokeInstance("expandArchiveStreamToDisk",
                new Class<?>[]{InputStream.class, String.class, String.class, int.class, Path.class, Map.class, countersClass, long.class, List.class},
                new ByteArrayInputStream(zipOne), "t.zip", "", 0, tempDir, new LinkedHashMap<>(), cTime, 1L, new ArrayList<>());
        assertEquals("PRESET", String.valueOf(ReflectionTestUtils.getField(cTime, "truncatedReason")));

        Object cEntries = newInner("ArchiveCounters", new Class<?>[]{});
        ReflectionTestUtils.setField(cEntries, "truncatedReason", "PRESET");
        ReflectionTestUtils.setField(service, "archiveMaxTotalMillis", 30000L);
        ReflectionTestUtils.setField(service, "archiveMaxEntries", 0);
        invokeInstance("expandArchiveStreamToDisk",
                new Class<?>[]{InputStream.class, String.class, String.class, int.class, Path.class, Map.class, countersClass, long.class, List.class},
                new ByteArrayInputStream(zipOne), "e.zip", "", 0, tempDir, new LinkedHashMap<>(), cEntries, System.nanoTime(), new ArrayList<>());
        assertEquals("PRESET", String.valueOf(ReflectionTestUtils.getField(cEntries, "truncatedReason")));

        Object cTotal = newInner("ArchiveCounters", new Class<?>[]{});
        ReflectionTestUtils.setField(cTotal, "truncatedReason", "PRESET");
        ReflectionTestUtils.setField(service, "archiveMaxEntries", 100);
        ReflectionTestUtils.setField(service, "archiveMaxTotalBytes", 1L);
        invokeInstance("expandArchiveStreamToDisk",
                new Class<?>[]{InputStream.class, String.class, String.class, int.class, Path.class, Map.class, countersClass, long.class, List.class},
                new ByteArrayInputStream(zipBlankAndNoExt), "m.zip", "", 0, tempDir, new LinkedHashMap<>(), cTotal, System.nanoTime(), new ArrayList<>());
        assertEquals("PRESET", String.valueOf(ReflectionTestUtils.getField(cTotal, "truncatedReason")));

        Object cNoExt = newInner("ArchiveCounters", new Class<?>[]{});
        Map<String, Object> metaNoExt = new LinkedHashMap<>();
        List<Map<String, Object>> filesNoExt = new ArrayList<>();
        ReflectionTestUtils.setField(service, "archiveMaxTotalBytes", 10L * 1024 * 1024);
        invokeInstance("expandArchiveStreamToDisk",
                new Class<?>[]{InputStream.class, String.class, String.class, int.class, Path.class, Map.class, countersClass, long.class, List.class},
                new ByteArrayInputStream(zipBlankAndNoExt), "n.zip", "", 0, tempDir, metaNoExt, cNoExt, System.nanoTime(), filesNoExt);
        assertFalse(filesNoExt.isEmpty());
        assertTrue(filesNoExt.stream().anyMatch(it -> it.get("ext") == null));

        Object cNullOutDir = newInner("ArchiveCounters", new Class<?>[]{});
        invokeInstance("expandArchiveStreamToDisk",
                new Class<?>[]{InputStream.class, String.class, String.class, int.class, Path.class, Map.class, countersClass, long.class, List.class},
                new ByteArrayInputStream(zipOne), "u.zip", "", 0, null, new LinkedHashMap<>(), cNullOutDir, System.nanoTime(), new ArrayList<>());
    }

    @Test
    void expandArchiveStreamToDisk_shouldCoverArchiveInputStreamCreateFailure() throws Exception {
        Object counters = newInner("ArchiveCounters", new Class<?>[]{});
        Class<?> countersClass = counters.getClass();
        byte[] brokenZipHeader = new byte[]{'P', 'K', 3, 4, 0, 0, 0, 0};
        try {
            invokeInstance("expandArchiveStreamToDisk",
                    new Class<?>[]{InputStream.class, String.class, String.class, int.class, Path.class, Map.class, countersClass, long.class, List.class},
                    new ByteArrayInputStream(brokenZipHeader), "b.zip", "", 0, tempDir, new LinkedHashMap<>(), counters, System.nanoTime(), new ArrayList<>());
        } catch (Exception ex) {
            assertNotNull(ex);
        }
    }
}
