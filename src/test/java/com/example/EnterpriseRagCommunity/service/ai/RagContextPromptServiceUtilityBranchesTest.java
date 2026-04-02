package com.example.EnterpriseRagCommunity.service.ai;

import com.example.EnterpriseRagCommunity.entity.semantic.enums.RetrievalHitType;
import com.example.EnterpriseRagCommunity.service.retrieval.RagPostChatRetrievalService;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RagContextPromptServiceUtilityBranchesTest {

    private static Object invokePrivateStatic(Class<?> clazz, String name, Class<?>[] paramTypes, Object... args) throws Exception {
        Method m;
        try {
            m = clazz.getDeclaredMethod(name, paramTypes);
        } catch (NoSuchMethodException ex) {
            m = findCompatibleMethod(clazz, name, args);
        }
        m.setAccessible(true);
        return m.invoke(null, args);
    }

    @Test
    void normalizeTextKey_should_cover_null_blank_and_600_cap() throws Exception {
        assertNull(invokePrivateStatic(
                RagContextPromptService.class,
                "normalizeTextKey",
                new Class<?>[]{String.class},
                new Object[]{null}
        ));

        assertNull(invokePrivateStatic(
                RagContextPromptService.class,
                "normalizeTextKey",
                new Class<?>[]{String.class},
                "   \n\t  "
        ));

        String longText = "x".repeat(700);
        String out = (String) invokePrivateStatic(
                RagContextPromptService.class,
                "normalizeTextKey",
                new Class<?>[]{String.class},
                longText
        );
        assertEquals(600, out.length());
    }

    @Test
    void resolveSourceKey_should_cover_null_type_docId_variants() throws Exception {
        assertNull(invokePrivateStatic(
                RagContextPromptService.class,
                "resolveSourceKey",
                new Class<?>[]{RagPostChatRetrievalService.Hit.class},
                new Object[]{null}
        ));

        RagPostChatRetrievalService.Hit typed = new RagPostChatRetrievalService.Hit();
        typed.setType(RetrievalHitType.POST);
        assertEquals("POST", invokePrivateStatic(
                RagContextPromptService.class,
                "resolveSourceKey",
                new Class<?>[]{RagPostChatRetrievalService.Hit.class},
                typed
        ));

        RagPostChatRetrievalService.Hit noDocId = new RagPostChatRetrievalService.Hit();
        noDocId.setDocId("   ");
        assertEquals("UNKNOWN", invokePrivateStatic(
                RagContextPromptService.class,
                "resolveSourceKey",
                new Class<?>[]{RagPostChatRetrievalService.Hit.class},
                noDocId
        ));

        RagPostChatRetrievalService.Hit withColon = new RagPostChatRetrievalService.Hit();
        withColon.setDocId("comment:123");
        assertEquals("COMMENT", invokePrivateStatic(
                RagContextPromptService.class,
                "resolveSourceKey",
                new Class<?>[]{RagPostChatRetrievalService.Hit.class},
                withColon
        ));

        RagPostChatRetrievalService.Hit noColon = new RagPostChatRetrievalService.Hit();
        noColon.setDocId("abcdef");
        assertEquals("DOC", invokePrivateStatic(
                RagContextPromptService.class,
                "resolveSourceKey",
                new Class<?>[]{RagPostChatRetrievalService.Hit.class},
                noColon
        ));
    }

    private static Method findCompatibleMethod(Class<?> clazz, String name, Object[] args) throws NoSuchMethodException {
        for (Method m : clazz.getDeclaredMethods()) {
            if (!m.getName().equals(name) || !Modifier.isStatic(m.getModifiers())) {
                continue;
            }
            Class<?>[] pts = m.getParameterTypes();
            if (pts.length != (args == null ? 0 : args.length)) {
                continue;
            }
            boolean ok = true;
            for (int i = 0; i < pts.length; i++) {
                Object arg = args[i];
                if (arg == null) {
                    continue;
                }
                Class<?> want = wrap(pts[i]);
                if (!want.isInstance(arg)) {
                    ok = false;
                    break;
                }
            }
            if (ok) {
                return m;
            }
        }
        throw new NoSuchMethodException(name);
    }

    private static Class<?> wrap(Class<?> c) {
        if (!c.isPrimitive()) {
            return c;
        }
        if (c == int.class) return Integer.class;
        if (c == long.class) return Long.class;
        if (c == double.class) return Double.class;
        if (c == float.class) return Float.class;
        if (c == boolean.class) return Boolean.class;
        if (c == byte.class) return Byte.class;
        if (c == short.class) return Short.class;
        if (c == char.class) return Character.class;
        return c;
    }

    @Test
    void tokenizeSet_should_cover_empty_normalize_and_maxTerms_cap() throws Exception {
        @SuppressWarnings("unchecked")
        Set<String> emptyNull = (Set<String>) invokePrivateStatic(
                RagContextPromptService.class,
                "tokenizeSet",
                new Class<?>[]{String.class},
                new Object[]{null}
        );
        assertTrue(emptyNull.isEmpty());

        @SuppressWarnings("unchecked")
        Set<String> emptyBlank = (Set<String>) invokePrivateStatic(
                RagContextPromptService.class,
                "tokenizeSet",
                new Class<?>[]{String.class},
                "   !!!   "
        );
        assertTrue(emptyBlank.isEmpty());

        @SuppressWarnings("unchecked")
        Set<String> capped = (Set<String>) invokePrivateStatic(
                RagContextPromptService.class,
                "tokenizeSet",
                new Class<?>[]{String.class},
                "A b c d"
        );
        assertEquals(4, capped.size());
        assertTrue(capped.contains("a"));

        StringBuilder manyTerms = new StringBuilder();
        for (int i = 0; i < 120; i++) {
            manyTerms.append("t").append(i).append(' ');
        }
        @SuppressWarnings("unchecked")
        Set<String> capped80 = (Set<String>) invokePrivateStatic(
                RagContextPromptService.class,
                "tokenizeSet",
                new Class<?>[]{String.class},
                manyTerms.toString()
        );
        assertEquals(80, capped80.size());
    }

    @Test
    void trimHelpers_and_buildSnippet_should_cover_defaults_null_and_truncate() throws Exception {
        assertEquals("", invokePrivateStatic(
                RagContextPromptService.class,
                "trimOrDefault",
                new Class<?>[]{String.class},
                "   "
        ));

        assertNull(invokePrivateStatic(
                RagContextPromptService.class,
                "trimOrNull",
                new Class<?>[]{String.class},
                "   "
        ));

        assertNull(invokePrivateStatic(
                RagContextPromptService.class,
                "buildSnippet",
                new Class<?>[]{String.class},
                " \n\t "
        ));

        String longText = "a".repeat(400);
        String snippet = (String) invokePrivateStatic(
                RagContextPromptService.class,
                "buildSnippet",
                new Class<?>[]{String.class},
                longText
        );
        assertTrue(snippet.endsWith("…"));
        assertEquals(321, snippet.length());
    }

    @Test
    void clampDouble_and_normalizeAblationMode_should_cover_all_switch_paths() throws Exception {
        assertEquals(1.0, (Double) invokePrivateStatic(
                RagContextPromptService.class,
                "clampDouble",
                new Class<?>[]{Double.class},
                new Object[]{null}
        ));

        assertEquals(1.0, (Double) invokePrivateStatic(
                RagContextPromptService.class,
                "clampDouble",
                new Class<?>[]{Double.class},
                Double.NaN
        ));

        assertEquals(1.0, (Double) invokePrivateStatic(
                RagContextPromptService.class,
                "clampDouble",
                new Class<?>[]{Double.class},
                Double.POSITIVE_INFINITY
        ));

        assertEquals(0.0, (Double) invokePrivateStatic(
                RagContextPromptService.class,
                "clampDouble",
                new Class<?>[]{Double.class},
                -10.0
        ));

        assertEquals(10.0, (Double) invokePrivateStatic(
                RagContextPromptService.class,
                "clampDouble",
                new Class<?>[]{Double.class},
                10.0
        ));

        assertEquals("NONE", invokePrivateStatic(
                RagContextPromptService.class,
                "normalizeAblationMode",
                new Class<?>[]{String.class},
                "none"
        ));
        assertEquals("REL_ONLY", invokePrivateStatic(
                RagContextPromptService.class,
                "normalizeAblationMode",
                new Class<?>[]{String.class},
                "rel"
        ));
        assertEquals("REL_IMP", invokePrivateStatic(
                RagContextPromptService.class,
                "normalizeAblationMode",
                new Class<?>[]{String.class},
                "relative+importance"
        ));
        assertEquals("NONE", invokePrivateStatic(
                RagContextPromptService.class,
                "normalizeAblationMode",
                new Class<?>[]{String.class},
                "no_pruning"
        ));
        assertEquals("REL_ONLY", invokePrivateStatic(
                RagContextPromptService.class,
                "normalizeAblationMode",
                new Class<?>[]{String.class},
                "rel_only"
        ));
        assertEquals("REL_IMP_RED", invokePrivateStatic(
                RagContextPromptService.class,
                "normalizeAblationMode",
                new Class<?>[]{String.class},
                "rel_imp_reduction"
        ));
        assertEquals("REL_IMP_RED", invokePrivateStatic(
                RagContextPromptService.class,
                "normalizeAblationMode",
                new Class<?>[]{String.class},
                "rel_imp_redundancy"
        ));
        assertEquals("REL_IMP_RED", invokePrivateStatic(
                RagContextPromptService.class,
                "normalizeAblationMode",
                new Class<?>[]{String.class},
                "unknown"
        ));
    }
}
