package com.example.EnterpriseRagCommunity.service.ai;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RagContextPromptServiceSelectSlidingBranchTest {

    @Test
    void selectSliding_should_skip_null_candidate() throws Exception {
        Object st = newSelectionState(false);

        List<Object> candidates = new ArrayList<>();
        candidates.add(null);
        candidates.add(newCandidate(1L, 10));

        List<RagContextPromptService.Item> dropped = new ArrayList<>();
        @SuppressWarnings("unchecked")
        List<Object> out = (List<Object>) invokeSelectSliding(candidates, st, 10, 100, dropped);

        assertEquals(1, out.size());
        assertTrue(dropped.isEmpty());
    }

    @Test
    void selectSliding_should_treat_tok_null_as_zero() throws Exception {
        Object st = newSelectionState(false);

        List<Object> candidates = List.of(newCandidate(1L, null));

        List<RagContextPromptService.Item> dropped = new ArrayList<>();
        @SuppressWarnings("unchecked")
        List<Object> out = (List<Object>) invokeSelectSliding(candidates, st, 10, 0, dropped);

        assertEquals(1, out.size());
        assertTrue(dropped.isEmpty());
    }

    @Test
    void selectSliding_should_drop_when_canTake_returns_reason() throws Exception {
        Object st = newSelectionState(true);

        List<Object> candidates = List.of(
                newCandidate(1L, 10),
                newCandidate(1L, 10)
        );

        List<RagContextPromptService.Item> dropped = new ArrayList<>();
        @SuppressWarnings("unchecked")
        List<Object> out = (List<Object>) invokeSelectSliding(candidates, st, 10, 100, dropped);

        assertEquals(1, out.size());
        assertEquals(1, dropped.size());
        assertEquals("dedupPostId", dropped.get(0).getReason());
    }

    @Test
    void selectSliding_remaining_lt_50_should_budgetExceeded_and_continue() throws Exception {
        Object st = newSelectionState(false);

        List<Object> candidates = List.of(
                newCandidate(1L, 20),
                newCandidate(2L, 100),
                newCandidate(3L, 10)
        );

        List<RagContextPromptService.Item> dropped = new ArrayList<>();
        @SuppressWarnings("unchecked")
        List<Object> out = (List<Object>) invokeSelectSliding(candidates, st, 10, 60, dropped);

        assertEquals(2, out.size());
        assertEquals(1, dropped.size());
        assertEquals("budgetExceeded", dropped.get(0).getReason());
        assertEquals(2L, dropped.get(0).getPostId());
    }

    private static Object invokeSelectSliding(
            List<Object> candidates,
            Object st,
            int maxItems,
            int budgetTokens,
            List<RagContextPromptService.Item> dropped
    ) throws Exception {
        Class<?> svcCls = RagContextPromptService.class;
        Class<?> stCls = Class.forName(svcCls.getName() + "$SelectionState");

        Method m = svcCls.getDeclaredMethod(
                "selectSliding",
                List.class,
                stCls,
                int.class,
                int.class,
                List.class,
                double.class,
                double.class,
                double.class,
                String.class
        );
        m.setAccessible(true);
        return m.invoke(null, candidates, st, maxItems, budgetTokens, dropped, 1.0, 1.0, 1.0, "REL_IMP_RED");
    }

    private static Object newSelectionState(boolean dedupByPostId) throws Exception {
        Class<?> stCls = Class.forName(RagContextPromptService.class.getName() + "$SelectionState");
        Constructor<?> ct = stCls.getDeclaredConstructor();
        ct.setAccessible(true);
        Object st = ct.newInstance();

        Field f = stCls.getDeclaredField("dedupByPostId");
        f.setAccessible(true);
        f.setBoolean(st, dedupByPostId);

        return st;
    }

    private static Object newCandidate(Long postId, Integer tokens) throws Exception {
        Class<?> candCls = Class.forName(RagContextPromptService.class.getName() + "$Candidate");
        Constructor<?> ct = candCls.getDeclaredConstructor();
        ct.setAccessible(true);
        Object c = ct.newInstance();

        RagContextPromptService.Item item = new RagContextPromptService.Item();
        item.setPostId(postId);

        Field itemF = candCls.getDeclaredField("item");
        itemF.setAccessible(true);
        itemF.set(c, item);

        Field tokF = candCls.getDeclaredField("tokens");
        tokF.setAccessible(true);
        tokF.set(c, tokens);

        return c;
    }
}
