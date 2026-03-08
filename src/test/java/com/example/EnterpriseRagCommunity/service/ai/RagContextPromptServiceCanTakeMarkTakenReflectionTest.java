package com.example.EnterpriseRagCommunity.service.ai;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RagContextPromptServiceCanTakeMarkTakenReflectionTest {

    @Test
    void canTake_should_cover_dedupPostId_title_contentHash_and_maxSamePostItems() throws Exception {
        Method canTake = RagContextPromptService.class.getDeclaredMethod(
                "canTake",
                cls("com.example.EnterpriseRagCommunity.service.ai.RagContextPromptService$Candidate"),
                cls("com.example.EnterpriseRagCommunity.service.ai.RagContextPromptService$SelectionState")
        );
        canTake.setAccessible(true);

        Object st = newSelectionState(true, true, true, 1);

        seenPostIds(st).add(1L);
        Object c1 = newCandidate(1L, "t1", 11L);
        assertEquals("dedupPostId", canTake.invoke(null, c1, st));

        seenTitles(st).add("k");
        Object c2 = newCandidate(2L, "k", 22L);
        assertEquals("dedupTitle", canTake.invoke(null, c2, st));

        seenContentHash(st).add(33L);
        Object c3 = newCandidate(3L, "t3", 33L);
        assertEquals("dedupContent", canTake.invoke(null, c3, st));

        postCount(st).put(4L, 1);
        Object c4 = newCandidate(4L, "t4", 44L);
        assertEquals("maxSamePostItems", canTake.invoke(null, c4, st));

        Object ok = newCandidate(5L, "t5", 55L);
        assertNull(canTake.invoke(null, ok, st));
    }

    @Test
    void markTaken_should_mark_all_dimensions_and_increment_postCount() throws Exception {
        Method markTaken = RagContextPromptService.class.getDeclaredMethod(
                "markTaken",
                cls("com.example.EnterpriseRagCommunity.service.ai.RagContextPromptService$Candidate"),
                cls("com.example.EnterpriseRagCommunity.service.ai.RagContextPromptService$SelectionState")
        );
        markTaken.setAccessible(true);

        Object st = newSelectionState(true, true, true, 2);
        Object c = newCandidate(10L, "titleKey", 99L);

        assertFalse(seenPostIds(st).contains(10L));
        assertFalse(seenTitles(st).contains("titleKey"));
        assertFalse(seenContentHash(st).contains(99L));
        assertEquals(0, postCount(st).getOrDefault(10L, 0));

        markTaken.invoke(null, c, st);

        assertTrue(seenPostIds(st).contains(10L));
        assertTrue(seenTitles(st).contains("titleKey"));
        assertTrue(seenContentHash(st).contains(99L));
        assertEquals(1, postCount(st).getOrDefault(10L, 0));

        markTaken.invoke(null, c, st);
        assertEquals(2, postCount(st).getOrDefault(10L, 0));
    }

    private static Class<?> cls(String name) throws ClassNotFoundException {
        return Class.forName(name);
    }

    private static Object newSelectionState(boolean dp, boolean dt, boolean dc, int maxSame) throws Exception {
        Class<?> stCls = cls("com.example.EnterpriseRagCommunity.service.ai.RagContextPromptService$SelectionState");
        var ctor = stCls.getDeclaredConstructor();
        ctor.setAccessible(true);
        Object st = ctor.newInstance();
        setField(st, "dedupByPostId", dp);
        setField(st, "dedupByTitle", dt);
        setField(st, "dedupByContentHash", dc);
        setField(st, "maxSamePostItems", maxSame);
        return st;
    }

    private static Object newCandidate(Long postId, String titleKey, Long contentHash) throws Exception {
        Class<?> cCls = cls("com.example.EnterpriseRagCommunity.service.ai.RagContextPromptService$Candidate");
        var ctor = cCls.getDeclaredConstructor();
        ctor.setAccessible(true);
        Object c = ctor.newInstance();

        RagContextPromptService.Item item = new RagContextPromptService.Item();
        item.setPostId(postId);

        setField(c, "item", item);
        setField(c, "titleKey", titleKey);
        setField(c, "contentHash", contentHash);
        return c;
    }

    @SuppressWarnings("unchecked")
    private static Set<Long> seenPostIds(Object st) throws Exception {
        return (Set<Long>) getField(st, "seenPostIds");
    }

    @SuppressWarnings("unchecked")
    private static Set<String> seenTitles(Object st) throws Exception {
        return (Set<String>) getField(st, "seenTitles");
    }

    @SuppressWarnings("unchecked")
    private static Set<Long> seenContentHash(Object st) throws Exception {
        return (Set<Long>) getField(st, "seenContentHash");
    }

    @SuppressWarnings("unchecked")
    private static Map<Long, Integer> postCount(Object st) throws Exception {
        return (Map<Long, Integer>) getField(st, "postCount");
    }

    private static Object getField(Object o, String name) throws Exception {
        Field f = o.getClass().getDeclaredField(name);
        f.setAccessible(true);
        return f.get(o);
    }

    private static void setField(Object o, String name, Object v) throws Exception {
        Field f = o.getClass().getDeclaredField(name);
        f.setAccessible(true);
        f.set(o, v);
    }
}
