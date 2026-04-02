package com.example.EnterpriseRagCommunity.service.ai;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LlmGatewayThinkStripperBranchTest {
    private static Class<?> wrap(Class<?> c) {
        if (!c.isPrimitive()) return c;
        if (c == int.class) return Integer.class;
        if (c == long.class) return Long.class;
        if (c == boolean.class) return Boolean.class;
        if (c == double.class) return Double.class;
        if (c == float.class) return Float.class;
        if (c == short.class) return Short.class;
        if (c == byte.class) return Byte.class;
        if (c == char.class) return Character.class;
        return c;
    }

    private static Method findCompatibleMethod(Class<?> type, String name, Object[] args) throws NoSuchMethodException {
        for (Method m : type.getDeclaredMethods()) {
            if (!m.getName().equals(name)) continue;
            Class<?>[] pt = m.getParameterTypes();
            if (pt.length != args.length) continue;
            boolean ok = true;
            for (int i = 0; i < pt.length; i++) {
                Object arg = args[i];
                if (arg == null) {
                    if (pt[i].isPrimitive()) {
                        ok = false;
                        break;
                    }
                    continue;
                }
                if (!wrap(pt[i]).isAssignableFrom(arg.getClass())) {
                    ok = false;
                    break;
                }
            }
            if (ok) return m;
        }
        throw new NoSuchMethodException(name);
    }


    private static final String THINK_STRIPPER_CLASS = "com.example.EnterpriseRagCommunity.service.ai.LlmGateway$ThinkStripper";

    private static Object newStripper() throws Exception {
        Class<?> c = Class.forName(THINK_STRIPPER_CLASS);
        Constructor<?> ctor = c.getDeclaredConstructor();
        ctor.setAccessible(true);
        return ctor.newInstance();
    }

    private static Object invoke(Object target, String name, Object... args) throws Exception {
        Method m = findCompatibleMethod(target.getClass(), name, args);
        m.setAccessible(true);
        return m.invoke(target, args);
    }

    private static Object getField(Object target, String name) throws Exception {
        Field f = target.getClass().getDeclaredField(name);
        f.setAccessible(true);
        return f.get(target);
    }

    @Test
    void appendLimited_and_start_helpers_should_cover_key_branches() throws Exception {
        Object stripper = newStripper();

        long p0 = (long) invoke(stripper, "appendLimited", new StringBuilder(), null, 5);
        assertEquals(0L, p0);
        long p0b = (long) invoke(stripper, "appendLimited", new StringBuilder(), "", 5);
        assertEquals(0L, p0b);

        StringBuilder out = new StringBuilder("xy");
        long p1 = (long) invoke(stripper, "appendLimited", out, "abcd", 2);
        assertEquals(4L, p1);
        assertEquals("xy", out.toString());

        StringBuilder fullAppend = new StringBuilder();
        long p1b = (long) invoke(stripper, "appendLimited", fullAppend, "ab", 10);
        assertEquals(2L, p1b);
        assertEquals("ab", fullAppend.toString());

        StringBuilder partialAppend = new StringBuilder("x");
        long p1c = (long) invoke(stripper, "appendLimited", partialAppend, "yz12", 3);
        assertEquals(4L, p1c);
        assertEquals("xyz", partialAppend.toString());

        long p2 = (long) invoke(stripper, "appendLimited", null, "abc", 10);
        assertEquals(3L, p2);

        boolean escFalse = (boolean) invoke(stripper, "isEscapedStartAt", "&lt;think&gt;", -1);
        boolean escLenFalse = (boolean) invoke(stripper, "isEscapedStartAt", "&lt;t", 1);
        boolean escTrue = (boolean) invoke(stripper, "isEscapedStartAt", "&lt;think&gt;", 0);
        assertFalse(escFalse);
        assertFalse(escLenFalse);
        assertTrue(escTrue);

        boolean escMismatchFalse = (boolean) invoke(stripper, "isEscapedStartAt", "&lt;thinx&gt;", 0);
        assertFalse(escMismatchFalse);
    }

    @Test
    void accept_should_cover_null_empty_plain_and_output_limit() throws Exception {
        Object stripper = newStripper();

        StringBuilder out = new StringBuilder();
        assertEquals(0L, (long) invoke(stripper, "accept", null, out));
        assertEquals(0L, (long) invoke(stripper, "accept", "", out));

        long p = (long) invoke(stripper, "accept", "hello", out);
        assertEquals(5L, p);
        assertEquals("hello", out.toString());

        StringBuilder noAppend = new StringBuilder("12");
        long p2 = (long) invoke(stripper, "accept", "345", noAppend);
        assertEquals(3L, p2);
        assertEquals("12345", noAppend.toString());
    }

    @Test
    void accept_should_cover_raw_escaped_cross_chunk_and_unclosed_paths() throws Exception {
        Object stripper = newStripper();
        StringBuilder out = new StringBuilder();

        long p1 = (long) invoke(stripper, "accept", "A<think>x</think>B", out);
        long p2 = (long) invoke(stripper, "accept", "C&lt;think&gt;y&lt;/think&gt;D", out);
        assertEquals(4L, p1 + p2);
        assertEquals("ABCD", out.toString());

        long p3 = (long) invoke(stripper, "accept", "E<think", out);
        assertEquals(1L, p3);
        assertEquals("ABCD", out.substring(0, 4));
        assertTrue(((String) getField(stripper, "carry")).startsWith("<think"));

        long p4 = (long) invoke(stripper, "accept", ">hidden</think>F", out);
        assertEquals(1L, p4);
        assertEquals("ABCDEF", out.toString());

        long p5 = (long) invoke(stripper, "accept", "G&lt;think", out);
        assertEquals(1L, p5);
        assertTrue(((String) getField(stripper, "carry")).startsWith("&lt;think"));

        long p6 = (long) invoke(stripper, "accept", "&gt;z&lt;/think&gt;H", out);
        assertEquals(1L, p6);
        assertEquals("ABCDEFGH", out.toString());

        long p7 = (long) invoke(stripper, "accept", "I<think>tail-without-close", out);
        assertEquals(1L, p7);
        assertTrue((boolean) getField(stripper, "inThink"));
        String tailCarry = (String) getField(stripper, "carry");
        assertTrue(tailCarry.length() <= 32);

        long p8 = (long) invoke(stripper, "accept", "</think>J", out);
        assertEquals(1L, p8);
        assertFalse((boolean) getField(stripper, "inThink"));
        assertEquals("ABCDEFGHIJ", out.toString());
    }

    @Test
    void accept_should_cover_nested_and_mixed_content_behavior() throws Exception {
        Object stripper = newStripper();
        StringBuilder out = new StringBuilder();

        long p = (long) invoke(
                stripper,
                "accept",
                "P<think>a<think>b</think>c</think>Q",
                out
        );

        assertEquals("Pc</think>Q", out.toString());
        assertEquals(out.length(), p);
    }

    @Test
    void find_helpers_should_cover_none_raw_escaped_and_min_selection() throws Exception {
        Object stripper = newStripper();

        int noneStart = (int) invoke(stripper, "findNextStart", "abc", 0);
        int rawStart = (int) invoke(stripper, "findNextStart", "a<think>x", 0);
        int escapedStart = (int) invoke(stripper, "findNextStart", "a&lt;think&gt;x", 0);
        int mixedStart = (int) invoke(stripper, "findNextStart", "a&lt;think&gt;b<think>c", 0);
        assertEquals(-1, noneStart);
        assertEquals(1, rawStart);
        assertEquals(1, escapedStart);
        assertEquals(1, mixedStart);

        int noneClose = (int) invoke(stripper, "findNextClose", "abc", 0);
        int rawClose = (int) invoke(stripper, "findNextClose", "a</think>b", 0);
        int escapedClose = (int) invoke(stripper, "findNextClose", "a&lt;/think&gt;b", 0);
        int mixedClose = (int) invoke(stripper, "findNextClose", "a&lt;/think&gt;b</think>c", 0);
        assertEquals(-1, noneClose);
        assertEquals(1, rawClose);
        assertEquals(1, escapedClose);
        assertEquals(1, mixedClose);
    }

    @Test
    void accept_should_cover_long_unclosed_tail_and_close_at_end() throws Exception {
        Object stripper = newStripper();
        StringBuilder out = new StringBuilder();

        long p1 = (long) invoke(
                stripper,
                "accept",
                "AA<think>" + "x".repeat(48),
                out
        );
        assertEquals(2L, p1);
        assertEquals("AA", out.toString());
        assertTrue((boolean) getField(stripper, "inThink"));
        String carry = (String) getField(stripper, "carry");
        assertEquals(32, carry.length());
        assertEquals("x".repeat(32), carry);

        long p2 = (long) invoke(
                stripper,
                "accept",
                "</think>",
                out
        );
        assertEquals(0L, p2);
        assertFalse((boolean) getField(stripper, "inThink"));
        assertEquals("", getField(stripper, "carry"));
        assertEquals("AA", out.toString());
    }
}
