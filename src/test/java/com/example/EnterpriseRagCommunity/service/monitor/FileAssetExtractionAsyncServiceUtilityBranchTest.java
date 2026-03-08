package com.example.EnterpriseRagCommunity.service.monitor;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

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
}
