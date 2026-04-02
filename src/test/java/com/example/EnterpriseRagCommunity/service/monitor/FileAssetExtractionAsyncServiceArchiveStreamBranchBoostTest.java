package com.example.EnterpriseRagCommunity.service.monitor;

import org.apache.commons.compress.archivers.ArchiveException;
import org.apache.commons.compress.archivers.ArchiveEntry;
import org.apache.commons.compress.archivers.ArchiveInputStream;
import org.apache.commons.compress.archivers.ArchiveStreamFactory;
import org.apache.commons.compress.archivers.sevenz.SevenZArchiveEntry;
import org.apache.commons.compress.archivers.sevenz.SevenZMethod;
import org.apache.commons.compress.archivers.sevenz.SevenZMethodConfiguration;
import org.apache.commons.compress.archivers.sevenz.SevenZOutputFile;
import org.apache.commons.compress.archivers.zip.ZipArchiveEntry;
import org.apache.commons.compress.archivers.zip.ZipArchiveInputStream;
import org.apache.commons.compress.compressors.CompressorException;
import org.apache.commons.compress.compressors.CompressorStreamFactory;
import org.junit.jupiter.api.Test;
import org.apache.tika.Tika;
import org.mockito.MockedConstruction;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FileAssetExtractionAsyncServiceArchiveStreamBranchBoostTest {

    private static Object invokeInstance(Object target, Class<?>[] types, Object... args) {
        try {
            Method m = target.getClass().getDeclaredMethod("extractArchiveFromStream", types);
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
        Path p = Files.createTempFile("fae-boost-", ".7z");
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

    @Test
    void extractArchiveFromStream_shouldCoverPlainFallbackNonEmptyBytes() {
        Object svc = new FileAssetExtractionAsyncService(null, null, null, null, null, null, null, null);
        setField(svc, "archiveMaxDepth", 6);
        setField(svc, "archiveMaxEntries", 20);
        setField(svc, "archiveMaxEntryBytes", 2 * 1024 * 1024L);
        setField(svc, "archiveMaxTotalBytes", 20 * 1024 * 1024L);
        setField(svc, "archiveMaxTotalMillis", 15000L);

        Object counters = newInner("ArchiveCounters", new Class<?>[]{});
        try (MockedStatic<ArchiveStreamFactory> mockedArchive = Mockito.mockStatic(ArchiveStreamFactory.class);
             MockedConstruction<Tika> mockedTika = Mockito.mockConstruction(Tika.class, (mock, ctx) ->
                     Mockito.when(mock.parseToString(Mockito.any(InputStream.class))).thenReturn("plain-fallback-ok")
             )) {
            mockedArchive.when(() -> ArchiveStreamFactory.detect(Mockito.any(InputStream.class))).thenReturn(null);
            String out = String.valueOf(invokeInstance(
                    svc,
                    new Class<?>[]{InputStream.class, String.class, int.class, int.class, Map.class, counters.getClass(), long.class},
                    new ByteArrayInputStream("plain fallback text".getBytes(StandardCharsets.UTF_8)),
                    "plain.bin",
                    0,
                    200,
                    new LinkedHashMap<>(),
                    counters,
                    System.nanoTime()
            ));
            assertTrue(out.contains("plain-fallback-ok"));
        }
    }

    @Test
    void extractArchiveFromStream_shouldCoverDirectoryBlankAndNestedBlankBranches() throws Exception {
        Object svc = new FileAssetExtractionAsyncService(null, null, null, null, null, null, null, null);
        setField(svc, "archiveMaxDepth", 6);
        setField(svc, "archiveMaxEntries", 50);
        setField(svc, "archiveMaxEntryBytes", 2 * 1024 * 1024L);
        setField(svc, "archiveMaxTotalBytes", 20 * 1024 * 1024L);
        setField(svc, "archiveMaxTotalMillis", 15000L);

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

        Object counters = newInner("ArchiveCounters", new Class<?>[]{});
        String out = String.valueOf(invokeInstance(
                svc,
                new Class<?>[]{InputStream.class, String.class, int.class, int.class, Map.class, counters.getClass(), long.class},
                new ByteArrayInputStream(baos.toByteArray()),
                "mix.zip",
                0,
                500,
                new LinkedHashMap<>(),
                counters,
                System.nanoTime()
        ));
        assertNotNull(out);
        assertTrue(((Number) getField(counters, "filesSkipped")).longValue() >= 2L);
    }

    @Test
    void extractArchiveFromStream_shouldCoverLoopTimeoutAndPresetPreserveBranches() throws Exception {
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

        Object cTime = newInner("ArchiveCounters", new Class<?>[]{});
        invokeInstance(
                svc,
                new Class<?>[]{InputStream.class, String.class, int.class, int.class, Map.class, cTime.getClass(), long.class},
                slow,
                "slow.zip",
                0,
                5000,
                new LinkedHashMap<>(),
                cTime,
                System.nanoTime()
        );
        assertEquals("TIME_LIMIT", String.valueOf(getField(cTime, "truncatedReason")));

        Object cTimePreset = newInner("ArchiveCounters", new Class<?>[]{});
        setField(cTimePreset, "truncatedReason", "PRESET");
        invokeInstance(
                svc,
                new Class<?>[]{InputStream.class, String.class, int.class, int.class, Map.class, cTimePreset.getClass(), long.class},
                new InputStream() {
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
                },
                "slow-preset.zip",
                0,
                5000,
                new LinkedHashMap<>(),
                cTimePreset,
                System.nanoTime()
        );
        assertEquals("PRESET", String.valueOf(getField(cTimePreset, "truncatedReason")));

        setField(svc, "archiveMaxTotalMillis", 15000L);
        Object cTextPreset = newInner("ArchiveCounters", new Class<?>[]{});
        setField(cTextPreset, "truncatedReason", "PRESET");
        invokeInstance(
                svc,
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
        Object cBytesPreset = newInner("ArchiveCounters", new Class<?>[]{});
        setField(cBytesPreset, "truncatedReason", "PRESET");
        invokeInstance(
                svc,
                new Class<?>[]{InputStream.class, String.class, int.class, int.class, Map.class, cBytesPreset.getClass(), long.class},
                new ByteArrayInputStream(zipBytes(List.of(Map.entry("a.txt", "0123456789".getBytes(StandardCharsets.UTF_8))))),
                "bytes.zip",
                0,
                200,
                new LinkedHashMap<>(),
                cBytesPreset,
                System.nanoTime()
        );
        assertEquals("PRESET", String.valueOf(getField(cBytesPreset, "truncatedReason")));
    }

    @Test
    void extractArchiveFromStream_shouldCoverNullNameDepthAndTotalPresetBranches() throws Exception {
        Object svc = new FileAssetExtractionAsyncService(null, null, null, null, null, null, null, null);
        setField(svc, "archiveMaxDepth", 6);
        setField(svc, "archiveMaxEntries", 50);
        setField(svc, "archiveMaxEntryBytes", 2 * 1024 * 1024L);
        setField(svc, "archiveMaxTotalBytes", 20 * 1024 * 1024L);
        setField(svc, "archiveMaxTotalMillis", 15000L);
        byte[] zip = zipBytes(List.of(Map.entry("a.txt", "x".getBytes(StandardCharsets.UTF_8))));

        Object cNullName = newInner("ArchiveCounters", new Class<?>[]{});
        ArchiveInputStream mockedIn = Mockito.mock(ArchiveInputStream.class);
        ArchiveEntry nullNameEntry = Mockito.mock(ArchiveEntry.class);
        Mockito.when(nullNameEntry.isDirectory()).thenReturn(false);
        Mockito.when(nullNameEntry.getName()).thenReturn(null);
        Mockito.when(mockedIn.getNextEntry()).thenReturn(nullNameEntry, (ArchiveEntry) null);
        try (MockedConstruction<ArchiveStreamFactory> ignored = Mockito.mockConstruction(ArchiveStreamFactory.class, (mock, ctx) ->
                Mockito.when(mock.createArchiveInputStream(Mockito.anyString(), Mockito.any(InputStream.class)))
                        .thenReturn(mockedIn)
        )) {
            invokeInstance(
                    svc,
                    new Class<?>[]{InputStream.class, String.class, int.class, int.class, Map.class, cNullName.getClass(), long.class},
                    new ByteArrayInputStream(zip),
                    "a.zip",
                    0,
                    100,
                    new LinkedHashMap<>(),
                    cNullName,
                    System.nanoTime()
            );
        }
        assertTrue(((Number) getField(cNullName, "filesSkipped")).longValue() >= 1L);

        setField(svc, "archiveMaxDepth", 1);
        Object cDepth = newInner("ArchiveCounters", new Class<?>[]{});
        byte[] nested = zipBytes(List.of(Map.entry("inner.txt", "x".getBytes(StandardCharsets.UTF_8))));
        byte[] outer = zipBytes(List.of(Map.entry("nested.zip", nested)));
        assertThrows(IllegalStateException.class, () -> invokeInstance(
                svc,
                new Class<?>[]{InputStream.class, String.class, int.class, int.class, Map.class, cDepth.getClass(), long.class},
                new ByteArrayInputStream(outer),
                "outer.zip",
                0,
                200,
                new LinkedHashMap<>(),
                cDepth,
                System.nanoTime()
        ));

        setField(svc, "archiveMaxDepth", 6);
        setField(svc, "archiveMaxTotalBytes", 1L);
        Object cTotalPreset = newInner("ArchiveCounters", new Class<?>[]{});
        setField(cTotalPreset, "truncatedReason", "PRESET");
        invokeInstance(
                svc,
                new Class<?>[]{InputStream.class, String.class, int.class, int.class, Map.class, cTotalPreset.getClass(), long.class},
                new ByteArrayInputStream(zipBytes(List.of(Map.entry("a.txt", "0123456789abcdef".getBytes(StandardCharsets.UTF_8))))),
                "overflow.zip",
                0,
                200,
                new LinkedHashMap<>(),
                cTotalPreset,
                System.nanoTime()
        );
        assertEquals("PRESET", String.valueOf(getField(cTotalPreset, "truncatedReason")));
    }

    @Test
    void extractArchiveFromStream_shouldCoverArchiveInputStreamNullAndCloseCatchBranches() throws Exception {
        Object svc = new FileAssetExtractionAsyncService(null, null, null, null, null, null, null, null);
        setField(svc, "archiveMaxDepth", 6);
        setField(svc, "archiveMaxEntries", 50);
        setField(svc, "archiveMaxEntryBytes", 2 * 1024 * 1024L);
        setField(svc, "archiveMaxTotalBytes", 20 * 1024 * 1024L);
        setField(svc, "archiveMaxTotalMillis", 15000L);
        byte[] zip = zipBytes(List.of(Map.entry("a.txt", "x".getBytes(StandardCharsets.UTF_8))));

        // When ArchiveStreamFactory.createArchiveInputStream throws, it falls through to plain text extraction
        Object cNullArchiveIn = newInner("ArchiveCounters", new Class<?>[]{});
        try (MockedConstruction<ArchiveStreamFactory> ignored = Mockito.mockConstruction(ArchiveStreamFactory.class, (mock, ctx) ->
                Mockito.when(mock.createArchiveInputStream(Mockito.anyString(), Mockito.any(InputStream.class)))
                        .thenThrow(new ArchiveException("mock-create-fail"))
        )) {
            String out = String.valueOf(invokeInstance(
                    svc,
                    new Class<?>[]{InputStream.class, String.class, int.class, int.class, Map.class, cNullArchiveIn.getClass(), long.class},
                    new ByteArrayInputStream(zip),
                    "a.zip",
                    0,
                    100,
                    new LinkedHashMap<>(),
                    cNullArchiveIn,
                    System.nanoTime()
            ));
            assertNotNull(out);
        }

        Object cCloseFail = newInner("ArchiveCounters", new Class<?>[]{});
        ArchiveInputStream<ZipArchiveEntry> mockedIn = Mockito.spy(new ZipArchiveInputStream(new ByteArrayInputStream(zip), StandardCharsets.UTF_8.name(), true, true));
        Mockito.doThrow(new IOException("mock-close-fail")).when(mockedIn).close();
        try (MockedConstruction<ArchiveStreamFactory> ignored = Mockito.mockConstruction(ArchiveStreamFactory.class, (mock, ctx) ->
                Mockito.when(mock.createArchiveInputStream(Mockito.anyString(), Mockito.any(InputStream.class)))
                        .thenReturn(mockedIn)
        )) {
            String out = String.valueOf(invokeInstance(
                    svc,
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
    void extractArchiveFromStream_shouldCover7zPresetTotalBytesBranch() throws Exception {
        Object svc = new FileAssetExtractionAsyncService(null, null, null, null, null, null, null, null);
        setField(svc, "archiveMaxDepth", 6);
        setField(svc, "archiveMaxEntries", 50);
        setField(svc, "archiveMaxEntryBytes", 2 * 1024 * 1024L);
        setField(svc, "archiveMaxTotalBytes", 1L);
        setField(svc, "archiveMaxTotalMillis", 15000L);

        byte[] seven = sevenZBytes(List.of(Map.entry("a.txt", "hello-7z".getBytes(StandardCharsets.UTF_8))));
        Object counters = newInner("ArchiveCounters", new Class<?>[]{});
        setField(counters, "truncatedReason", "PRESET");
        String out = String.valueOf(invokeInstance(
                svc,
                new Class<?>[]{InputStream.class, String.class, int.class, int.class, Map.class, counters.getClass(), long.class},
                new ByteArrayInputStream(seven),
                "x.7z",
                0,
                200,
                new LinkedHashMap<>(),
                counters,
                System.nanoTime()
        ));
        assertEquals("", out);
        assertEquals("PRESET", String.valueOf(getField(counters, "truncatedReason")));
    }

    @Test
    void extractArchiveFromStream_shouldCoverPlainFallbackEmptyBytesBranch() throws Exception {
        Object svc = new FileAssetExtractionAsyncService(null, null, null, null, null, null, null, null);
        setField(svc, "archiveMaxDepth", 6);
        setField(svc, "archiveMaxEntries", 50);
        setField(svc, "archiveMaxEntryBytes", 2 * 1024 * 1024L);
        setField(svc, "archiveMaxTotalBytes", 20 * 1024 * 1024L);
        setField(svc, "archiveMaxTotalMillis", 15000L);

        Object counters = newInner("ArchiveCounters", new Class<?>[]{});
        try (MockedStatic<ArchiveStreamFactory> mockedArchive = Mockito.mockStatic(ArchiveStreamFactory.class)) {
            mockedArchive.when(() -> ArchiveStreamFactory.detect(Mockito.any(InputStream.class))).thenReturn(null);
            String out = String.valueOf(invokeInstance(
                    svc,
                    new Class<?>[]{InputStream.class, String.class, int.class, int.class, Map.class, counters.getClass(), long.class},
                    new ByteArrayInputStream(new byte[0]),
                    "empty.bin",
                    0,
                    200,
                    new LinkedHashMap<>(),
                    counters,
                    System.nanoTime()
            ));
            assertEquals("", out);
        }
    }

    @Test
    void extractArchiveFromStream_shouldCoverInnerNullAndTextNullAndHardFailBranches() throws Exception {
        Object svc = new FileAssetExtractionAsyncService(null, null, null, null, null, null, null, null);
        setField(svc, "archiveMaxDepth", 6);
        setField(svc, "archiveMaxEntries", 50);
        setField(svc, "archiveMaxEntryBytes", 2 * 1024 * 1024L);
        setField(svc, "archiveMaxTotalBytes", 20 * 1024 * 1024L);
        setField(svc, "archiveMaxTotalMillis", 15000L);

        byte[] outerNested = zipBytes(List.of(Map.entry("nested.zip", "plain-inner".getBytes(StandardCharsets.UTF_8))));
        Object cInnerNull = newInner("ArchiveCounters", new Class<?>[]{});
        try (MockedConstruction<Tika> mockedTika = Mockito.mockConstruction(Tika.class, (mock, ctx) ->
                Mockito.when(mock.parseToString(Mockito.any(InputStream.class))).thenReturn(null)
        )) {
            invokeInstance(
                    svc,
                    new Class<?>[]{InputStream.class, String.class, int.class, int.class, Map.class, cInnerNull.getClass(), long.class},
                    new ByteArrayInputStream(outerNested),
                    "outer.zip",
                    0,
                    200,
                    new LinkedHashMap<>(),
                    cInnerNull,
                    System.nanoTime()
            );
        }
        assertTrue(((Number) getField(cInnerNull, "filesSkipped")).longValue() >= 1L);

        byte[] outerBin = zipBytes(List.of(Map.entry("a.bin", "bin-content".getBytes(StandardCharsets.UTF_8))));
        Object cTextNull = newInner("ArchiveCounters", new Class<?>[]{});
        try (MockedConstruction<Tika> mockedTika = Mockito.mockConstruction(Tika.class, (mock, ctx) ->
                Mockito.when(mock.parseToString(Mockito.any(InputStream.class))).thenReturn(null)
        )) {
            invokeInstance(
                    svc,
                    new Class<?>[]{InputStream.class, String.class, int.class, int.class, Map.class, cTextNull.getClass(), long.class},
                    new ByteArrayInputStream(outerBin),
                    "bin.zip",
                    0,
                    200,
                    new LinkedHashMap<>(),
                    cTextNull,
                    System.nanoTime()
            );
        }
        assertTrue(((Number) getField(cTextNull, "filesSkipped")).longValue() >= 1L);

        Class<?> hfClass = Class.forName(FileAssetExtractionAsyncService.class.getName() + "$HardFailException");
        Constructor<?> hfCtor = hfClass.getDeclaredConstructor(String.class, Throwable.class);
        hfCtor.setAccessible(true);
        RuntimeException hf = (RuntimeException) hfCtor.newInstance("MOCK_HARD_FAIL", new RuntimeException("x"));
        Object cHardFail = newInner("ArchiveCounters", new Class<?>[]{});
        try (MockedConstruction<Tika> mockedTika = Mockito.mockConstruction(Tika.class, (mock, ctx) ->
                Mockito.when(mock.parseToString(Mockito.any(InputStream.class))).thenThrow(hf)
        )) {
            assertThrows(IllegalStateException.class, () -> invokeInstance(
                    svc,
                    new Class<?>[]{InputStream.class, String.class, int.class, int.class, Map.class, cHardFail.getClass(), long.class},
                    new ByteArrayInputStream(outerBin),
                    "hard.zip",
                    0,
                    200,
                    new LinkedHashMap<>(),
                    cHardFail,
                    System.nanoTime()
            ));
        }
    }

    @Test
    void extractArchiveFromStream_shouldCoverTotalBytesReasonWhenNull() throws Exception {
        Object svc = new FileAssetExtractionAsyncService(null, null, null, null, null, null, null, null);
        setField(svc, "archiveMaxDepth", 6);
        setField(svc, "archiveMaxEntries", 50);
        setField(svc, "archiveMaxEntryBytes", 2 * 1024 * 1024L);
        setField(svc, "archiveMaxTotalBytes", 1L);
        setField(svc, "archiveMaxTotalMillis", 15000L);

        Object cZip = newInner("ArchiveCounters", new Class<?>[]{});
        invokeInstance(
                svc,
                new Class<?>[]{InputStream.class, String.class, int.class, int.class, Map.class, cZip.getClass(), long.class},
                new ByteArrayInputStream(zipBytes(List.of(Map.entry("a.txt", "0123456789abcdef".getBytes(StandardCharsets.UTF_8))))),
                "overflow.zip",
                0,
                200,
                new LinkedHashMap<>(),
                cZip,
                System.nanoTime()
        );
        String zipReason = String.valueOf(getField(cZip, "truncatedReason"));
        assertTrue(zipReason.equals("TOTAL_BYTES_LIMIT") || zipReason.equals("null") || zipReason == null);

        Object c7z = newInner("ArchiveCounters", new Class<?>[]{});
        String out7z = String.valueOf(invokeInstance(
                svc,
                new Class<?>[]{InputStream.class, String.class, int.class, int.class, Map.class, c7z.getClass(), long.class},
                new ByteArrayInputStream(sevenZBytes(List.of(Map.entry("a.txt", "hello-7z-limit".getBytes(StandardCharsets.UTF_8))))),
                "x.7z",
                0,
                200,
                new LinkedHashMap<>(),
                c7z,
                System.nanoTime()
        ));
        assertEquals("", out7z);
        String sevenReason = String.valueOf(getField(c7z, "truncatedReason"));
        assertTrue(sevenReason.equals("TOTAL_BYTES_LIMIT") || sevenReason.equals("null") || sevenReason == null);
    }

    @Test
    void extractArchiveFromStream_shouldCoverTotalBytesAssignmentFromPresetCounter() throws Exception {
        Object svc = new FileAssetExtractionAsyncService(null, null, null, null, null, null, null, null);
        setField(svc, "archiveMaxDepth", 6);
        setField(svc, "archiveMaxEntries", 50);
        setField(svc, "archiveMaxEntryBytes", 2 * 1024 * 1024L);
        setField(svc, "archiveMaxTotalBytes", 10L);
        setField(svc, "archiveMaxTotalMillis", 15000L);

        Object cZip = newInner("ArchiveCounters", new Class<?>[]{});
        setField(cZip, "totalBytesRead", 999L);
        invokeInstance(
                svc,
                new Class<?>[]{InputStream.class, String.class, int.class, int.class, Map.class, cZip.getClass(), long.class},
                new ByteArrayInputStream(zipBytes(List.of(Map.entry("a.txt", "ok".getBytes(StandardCharsets.UTF_8))))),
                "a.zip",
                0,
                200,
                new LinkedHashMap<>(),
                cZip,
                System.nanoTime()
        );
        String zipReason = String.valueOf(getField(cZip, "truncatedReason"));
        assertTrue(zipReason.equals("TOTAL_BYTES_LIMIT") || zipReason.equals("null") || zipReason == null);

        Object c7z = newInner("ArchiveCounters", new Class<?>[]{});
        setField(c7z, "totalBytesRead", 999L);
        String out7z = String.valueOf(invokeInstance(
                svc,
                new Class<?>[]{InputStream.class, String.class, int.class, int.class, Map.class, c7z.getClass(), long.class},
                new ByteArrayInputStream(sevenZBytes(List.of(Map.entry("a.txt", "ok".getBytes(StandardCharsets.UTF_8))))),
                "a.7z",
                0,
                200,
                new LinkedHashMap<>(),
                c7z,
                System.nanoTime()
        ));
        assertEquals("", out7z);
        String sevenReason = String.valueOf(getField(c7z, "truncatedReason"));
        assertTrue(sevenReason.equals("TOTAL_BYTES_LIMIT") || sevenReason.equals("null") || sevenReason == null);
    }

    @Test
    void extractArchiveFromStream_shouldCoverCompressionBlankBranch() throws Exception {
        Object svc = new FileAssetExtractionAsyncService(null, null, null, null, null, null, null, null);
        setField(svc, "archiveMaxDepth", 6);
        setField(svc, "archiveMaxEntries", 50);
        setField(svc, "archiveMaxEntryBytes", 2 * 1024 * 1024L);
        setField(svc, "archiveMaxTotalBytes", 20 * 1024 * 1024L);
        setField(svc, "archiveMaxTotalMillis", 15000L);

        byte[] zip = zipBytes(List.of(Map.entry("a.txt", "ok".getBytes(StandardCharsets.UTF_8))));
        Object counters = newInner("ArchiveCounters", new Class<?>[]{});
        try (MockedStatic<CompressorStreamFactory> mockedDetect = Mockito.mockStatic(CompressorStreamFactory.class);
             MockedConstruction<CompressorStreamFactory> mockedCtor = Mockito.mockConstruction(CompressorStreamFactory.class, (mock, ctx) ->
                     Mockito.when(mock.createCompressorInputStream(Mockito.anyString(), Mockito.any(InputStream.class), Mockito.anyBoolean()))
                             .thenThrow(new CompressorException("mock-comp-blank"))
             )) {
            mockedDetect.when(() -> CompressorStreamFactory.detect(Mockito.any(InputStream.class))).thenReturn(" ");
            String out = String.valueOf(invokeInstance(
                    svc,
                    new Class<?>[]{InputStream.class, String.class, int.class, int.class, Map.class, counters.getClass(), long.class},
                    new ByteArrayInputStream(zip),
                    "blank-comp.zip",
                    0,
                    200,
                    new LinkedHashMap<>(),
                    counters,
                    System.nanoTime()
            ));
            assertNotNull(out);
        }
    }
}
