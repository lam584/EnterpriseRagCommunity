package com.example.EnterpriseRagCommunity.service.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.LongNode;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.*;

public class AiEmbeddingServiceBranchCoverageTest {

    private static final ObjectMapper OM = new ObjectMapper();

    private static Object invokePrivateStatic(Class<?> clazz, String name, Class<?>[] paramTypes, Object... args) throws Exception {
        Method m = clazz.getDeclaredMethod(name, paramTypes);
        m.setAccessible(true);
        return m.invoke(null, args);
    }

    private static JsonNode node(String json) throws Exception {
        return OM.readTree(json);
    }

    @Test
    void escapeJson_private_null_and_replacements() throws Exception {
        assertEquals("", invokePrivateStatic(AiEmbeddingService.class, "escapeJson", new Class<?>[]{String.class}, (String) null));
        String out = (String) invokePrivateStatic(AiEmbeddingService.class, "escapeJson", new Class<?>[]{String.class}, "a\\b\"\n\r\t");
        assertTrue(out.contains("\\\\"));
        assertTrue(out.contains("\\\""));
        assertTrue(out.contains("\\n"));
        assertTrue(out.contains("\\r"));
        assertTrue(out.contains("\\t"));
        assertFalse(out.contains("\n"));
        assertFalse(out.contains("\r"));
        assertFalse(out.contains("\t"));
    }

    @Test
    void readIntLike_private_numeric_textual_empty_nan_infinity_illegal_and_otherShapes() throws Exception {
        assertNull(invokePrivateStatic(AiEmbeddingService.class, "readIntLike", new Class<?>[]{JsonNode.class}, (JsonNode) null));
        assertNull(invokePrivateStatic(AiEmbeddingService.class, "readIntLike", new Class<?>[]{JsonNode.class}, node("null")));

        assertEquals(2, invokePrivateStatic(AiEmbeddingService.class, "readIntLike", new Class<?>[]{JsonNode.class}, node("2")));
        assertEquals(3, invokePrivateStatic(AiEmbeddingService.class, "readIntLike", new Class<?>[]{JsonNode.class}, node("2.6")));
        assertEquals(-1, invokePrivateStatic(AiEmbeddingService.class, "readIntLike", new Class<?>[]{JsonNode.class}, node("-1.5")));

        assertEquals(7, invokePrivateStatic(AiEmbeddingService.class, "readIntLike", new Class<?>[]{JsonNode.class}, node("\" 7 \"")));
        assertNull(invokePrivateStatic(AiEmbeddingService.class, "readIntLike", new Class<?>[]{JsonNode.class}, node("\"\"")));
        assertNull(invokePrivateStatic(AiEmbeddingService.class, "readIntLike", new Class<?>[]{JsonNode.class}, node("\"  \"")));
        assertNull(invokePrivateStatic(AiEmbeddingService.class, "readIntLike", new Class<?>[]{JsonNode.class}, node("\"NaN\"")));
        assertNull(invokePrivateStatic(AiEmbeddingService.class, "readIntLike", new Class<?>[]{JsonNode.class}, node("\"Infinity\"")));
        assertNull(invokePrivateStatic(AiEmbeddingService.class, "readIntLike", new Class<?>[]{JsonNode.class}, node("\"-Infinity\"")));
        assertNull(invokePrivateStatic(AiEmbeddingService.class, "readIntLike", new Class<?>[]{JsonNode.class}, node("\"oops\"")));

        assertNull(invokePrivateStatic(AiEmbeddingService.class, "readIntLike", new Class<?>[]{JsonNode.class}, node("{}")));
        assertNull(invokePrivateStatic(AiEmbeddingService.class, "readIntLike", new Class<?>[]{JsonNode.class}, node("[]")));

        assertEquals(Integer.MIN_VALUE,
                invokePrivateStatic(AiEmbeddingService.class, "readIntLike", new Class<?>[]{JsonNode.class}, LongNode.valueOf(2147483648L)));
    }

    @Test
    void normalizeForDedup_private_null_blank_and_collapseWhitespace() throws Exception {
        assertEquals("", invokePrivateStatic(AiEmbeddingService.class, "normalizeForDedup", new Class<?>[]{String.class}, (String) null));
        assertEquals("", invokePrivateStatic(AiEmbeddingService.class, "normalizeForDedup", new Class<?>[]{String.class}, "   "));
        assertEquals("a b c d", invokePrivateStatic(AiEmbeddingService.class, "normalizeForDedup", new Class<?>[]{String.class}, " a   b\tc\n d  "));
        assertEquals("a b", invokePrivateStatic(AiEmbeddingService.class, "normalizeForDedup", new Class<?>[]{String.class}, "a b"));
    }

    @Test
    void extractFirstEmbeddingVector_branches_and_edges() {
        assertNull(AiEmbeddingService.extractFirstEmbeddingVector(null));
        assertNull(AiEmbeddingService.extractFirstEmbeddingVector("{}"));
        assertNull(AiEmbeddingService.extractFirstEmbeddingVector("{\"embedding\":{}}"));
        assertNull(AiEmbeddingService.extractFirstEmbeddingVector("{\"embedding\":[1,2}"));

        float[] empty = AiEmbeddingService.extractFirstEmbeddingVector("{\"data\":[{\"embedding\":[]}]}");
        assertNotNull(empty);
        assertEquals(0, empty.length);

        float[] ok = AiEmbeddingService.extractFirstEmbeddingVector("{\"data\":[{\"embedding\":[ 1 , -2.5 ,3e0]}]}");
        assertNotNull(ok);
        assertEquals(3, ok.length);
        assertEquals(1.0f, ok[0], 1e-6);
        assertEquals(-2.5f, ok[1], 1e-6);
        assertEquals(3.0f, ok[2], 1e-6);

        float[] trailingComma = AiEmbeddingService.extractFirstEmbeddingVector("{\"data\":[{\"embedding\":[1,2,]}]}");
        assertNotNull(trailingComma);
        assertEquals(2, trailingComma.length);
        assertEquals(1.0f, trailingComma[0], 1e-6);
        assertEquals(2.0f, trailingComma[1], 1e-6);

        float[] doubleComma = AiEmbeddingService.extractFirstEmbeddingVector("{\"data\":[{\"embedding\":[1,,2]}]}");
        assertNotNull(doubleComma);
        assertEquals(2, doubleComma.length);
        assertEquals(1.0f, doubleComma[0], 1e-6);
        assertEquals(2.0f, doubleComma[1], 1e-6);

        assertThrows(NumberFormatException.class,
                () -> AiEmbeddingService.extractFirstEmbeddingVector("{\"data\":[{\"embedding\":[[1,2],[3]]}]}"));
    }
}
