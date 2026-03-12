package com.example.EnterpriseRagCommunity.service.ai.client;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.HttpURLConnection;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DashscopeRerankClientBranchCoverageTest {

    private static class RecordingHttpURLConnection extends HttpURLConnection {
        private final Map<String, String> headers = new HashMap<>();

        protected RecordingHttpURLConnection() {
            super(null);
        }

        @Override
        public void disconnect() {
        }

        @Override
        public boolean usingProxy() {
            return false;
        }

        @Override
        public void connect() {
        }

        @Override
        public void setRequestProperty(String key, String value) {
            headers.put(key, value);
        }

        @Override
        public String getRequestProperty(String key) {
            return headers.get(key);
        }
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> invokeBuildBody(
            String model,
            String query,
            List<?> documents,
            Integer topN,
            Boolean returnDocuments,
            String instruct,
            Double fps
    ) throws Exception {
        Method m = DashscopeRerankClient.class.getDeclaredMethod(
                "buildBody",
                String.class,
                String.class,
                List.class,
                Integer.class,
                Boolean.class,
                String.class,
                Double.class
        );
        m.setAccessible(true);
        return (Map<String, Object>) m.invoke(null, model, query, documents, topN, returnDocuments, instruct, fps);
    }

    private static Object invokeStatic(String methodName, Class<?>[] parameterTypes, Object... args) throws Exception {
        Method m = DashscopeRerankClient.class.getDeclaredMethod(methodName, parameterTypes);
        m.setAccessible(true);
        return m.invoke(null, args);
    }

    private static HttpURLConnection newHttpConnection() {
        return new RecordingHttpURLConnection();
    }

    private static HttpServer startServer() throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);

        server.createContext("/compatible-api/v1/reranks", exchange -> writeJson(exchange, 200, "{\"ok\":true}"));

        server.createContext("/api/v1/services/rerank/text-rerank/text-rerank", exchange -> {
            String scenario = exchange.getRequestHeaders().getFirst("X-Scenario");
            if ("error-no-body".equals(scenario)) {
                exchange.sendResponseHeaders(502, -1);
                exchange.close();
                return;
            }
            if ("error-with-body".equals(scenario)) {
                writeJson(exchange, 500, "{\"error\":\"boom\"}");
                return;
            }
            writeJson(exchange, 200, "{\"ok\":false}");
        });

        server.start();
        return server;
    }

    private static void writeJson(HttpExchange exchange, int code, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(code, bytes.length);
        exchange.getResponseBody().write(bytes);
        exchange.close();
    }

    @Test
    void buildBody_coversCompatAndNonCompatBranches() throws Exception {
        Map<String, Object> compatWithParams = invokeBuildBody(
                "qwen3-rerank",
                null,
                List.of("d1"),
                3,
                null,
                "hint",
                null
        );
        assertEquals("qwen3-rerank", compatWithParams.get("model"));
        assertEquals("", compatWithParams.get("query"));
        assertEquals(3, compatWithParams.get("top_n"));
        assertEquals("hint", compatWithParams.get("instruct"));

        Map<String, Object> compatWithoutOptional = invokeBuildBody(
                "qwen3-rerank",
                "q",
                List.of("d2"),
                0,
                null,
                " ",
                null
        );
        assertFalse(compatWithoutOptional.containsKey("top_n"));
        assertFalse(compatWithoutOptional.containsKey("instruct"));
        assertEquals("q", compatWithoutOptional.get("query"));

        Map<String, Object> compatNullOptional = invokeBuildBody(
                "qwen3-rerank",
                "q2",
                List.of("d3"),
                null,
                null,
                null,
                null
        );
        assertFalse(compatNullOptional.containsKey("top_n"));
        assertFalse(compatNullOptional.containsKey("instruct"));

        Map<String, Object> normalWithParameters = invokeBuildBody(
                "gte-rerank-v2",
                "query",
                List.of("a", "b"),
                5,
                true,
                "guide",
                0.4
        );
        assertTrue(normalWithParameters.containsKey("input"));
        assertTrue(normalWithParameters.containsKey("parameters"));
        Map<String, Object> parameters = (Map<String, Object>) normalWithParameters.get("parameters");
        assertEquals(5, parameters.get("top_n"));
        assertEquals(Boolean.TRUE, parameters.get("return_documents"));
        assertEquals("guide", parameters.get("instruct"));
        assertEquals(0.4, parameters.get("fps"));

        Map<String, Object> normalWithoutParameters = invokeBuildBody(
                "gte-rerank-v2",
                null,
                List.of(),
                null,
                null,
                " ",
                null
        );
        assertTrue(normalWithoutParameters.containsKey("input"));
        assertFalse(normalWithoutParameters.containsKey("parameters"));
        Map<String, Object> input = (Map<String, Object>) normalWithoutParameters.get("input");
        assertEquals("", input.get("query"));

        Map<String, Object> normalWithBoundaryOptional = invokeBuildBody(
                "gte-rerank-v2",
                "query2",
                List.of("x"),
                0,
                null,
                null,
                null
        );
        assertFalse(normalWithBoundaryOptional.containsKey("parameters"));
    }

    @Test
    void applyHeaders_coversAuthAndFilteringBranches() throws Exception {
        HttpURLConnection conn1 = newHttpConnection();
        invokeStatic("applyHeaders", new Class[]{HttpURLConnection.class, String.class, Map.class}, conn1, "ak-1", null);
        assertEquals("Bearer ak-1", conn1.getRequestProperty("Authorization"));

        HttpURLConnection conn2 = newHttpConnection();
        Map<String, String> headersWithAuthorization = new LinkedHashMap<>();
        headersWithAuthorization.put("Authorization", "Bearer ext");
        headersWithAuthorization.put("X-Trace", "t1");
        invokeStatic("applyHeaders", new Class[]{HttpURLConnection.class, String.class, Map.class}, conn2, "ak-2", headersWithAuthorization);
        assertEquals("Bearer ext", conn2.getRequestProperty("Authorization"));
        assertEquals("t1", conn2.getRequestProperty("X-Trace"));

        HttpURLConnection conn3 = newHttpConnection();
        Map<String, String> headersWithInvalidItems = new LinkedHashMap<>();
        headersWithInvalidItems.put(null, "null-key");
        headersWithInvalidItems.put(" ", "v");
        headersWithInvalidItems.put("X-Null", null);
        headersWithInvalidItems.put("X-Ok", "v2");
        invokeStatic("applyHeaders", new Class[]{HttpURLConnection.class, String.class, Map.class}, conn3, " ", headersWithInvalidItems);
        assertEquals("v2", conn3.getRequestProperty("X-Ok"));
        assertEquals(null, conn3.getRequestProperty("Authorization"));

        HttpURLConnection conn4 = newHttpConnection();
        invokeStatic("applyHeaders", new Class[]{HttpURLConnection.class, String.class, Map.class}, conn4, null, Map.of());
        assertEquals(null, conn4.getRequestProperty("Authorization"));
    }

    @Test
    void helperMethods_coverFallbackAndFormattingBranches() throws Exception {
        assertEquals("https://dashscope.aliyuncs.com/compatible-api/v1",
                invokeStatic("compatApiBase", new Class[]{String.class}, "https://dashscope.aliyuncs.com/compatible-mode/v1"));
        assertEquals("https://dashscope.aliyuncs.com/path",
                invokeStatic("compatApiBase", new Class[]{String.class}, "https://dashscope.aliyuncs.com/path/"));
        assertEquals("",
                invokeStatic("compatApiBase", new Class[]{String.class}, new Object[]{null}));

        assertEquals("http://host", invokeStatic("dashscopeOrigin", new Class[]{String.class}, "http://host/path/x"));
        assertEquals("bad://host", invokeStatic("dashscopeOrigin", new Class[]{String.class}, "bad://host/path"));
        assertEquals("bad://host", invokeStatic("dashscopeOrigin", new Class[]{String.class}, "bad://host"));
        assertEquals("", invokeStatic("dashscopeOrigin", new Class[]{String.class}, new Object[]{null}));

        assertEquals("", invokeStatic("normalizeBaseUrl", new Class[]{String.class, String.class}, null, null));
        assertEquals("https://a.com", invokeStatic("normalizeBaseUrl", new Class[]{String.class, String.class}, "https://a.com/", null));
        assertEquals("https://a.com", invokeStatic("normalizeBaseUrl", new Class[]{String.class, String.class}, " https://a.com ", null));

        assertEquals("fb", invokeStatic("normalizeString", new Class[]{String.class, String.class}, null, "fb"));
        assertEquals("fb", invokeStatic("normalizeString", new Class[]{String.class, String.class}, "   ", "fb"));
        assertEquals("v", invokeStatic("normalizeString", new Class[]{String.class, String.class}, "  v ", "fb"));

        assertEquals("https://dashscope.aliyuncs.com/compatible-api/v1/reranks",
                invokeStatic("selectEndpoint", new Class[]{String.class, String.class}, "https://dashscope.aliyuncs.com/compatible-mode/v1", "qwen3-rerank"));
        assertEquals("https://dashscope.aliyuncs.com/api/v1/services/rerank/text-rerank/text-rerank",
                invokeStatic("selectEndpoint", new Class[]{String.class, String.class}, "https://dashscope.aliyuncs.com/compatible-mode/v1", null));
    }

    @Test
    void openJsonPost_coversDefaultAndCustomTimeoutBranches() throws Exception {
        DashscopeRerankClient client = new DashscopeRerankClient();
        Method method = DashscopeRerankClient.class.getDeclaredMethod(
                "openJsonPost", String.class, String.class, Map.class, Integer.class, Integer.class
        );
        method.setAccessible(true);

        HttpURLConnection connDefaultByNull = (HttpURLConnection) method.invoke(
                client, "http://example.com/path", "ak", null, null, null
        );
        assertEquals(10_000, connDefaultByNull.getConnectTimeout());
        assertEquals(300_000, connDefaultByNull.getReadTimeout());
        assertEquals("application/json", connDefaultByNull.getRequestProperty("Accept"));

        HttpURLConnection connDefaultByNonPositive = (HttpURLConnection) method.invoke(
                client, "http://example.com/path", "ak", null, 0, -1
        );
        assertEquals(10_000, connDefaultByNonPositive.getConnectTimeout());
        assertEquals(300_000, connDefaultByNonPositive.getReadTimeout());

        HttpURLConnection connCustom = (HttpURLConnection) method.invoke(
                client, "http://example.com/path", "ak", null, 1234, 5678
        );
        assertEquals(1234, connCustom.getConnectTimeout());
        assertEquals(5678, connCustom.getReadTimeout());
    }

    @Test
    void rerankOnce_coversSuccessAndErrorBranches() throws Exception {
        HttpServer server = startServer();
        try {
            int port = server.getAddress().getPort();
            String baseUrl = "http://localhost:" + port + "/compatible-mode/v1";
            DashscopeRerankClient client = new DashscopeRerankClient();

            DashscopeRerankClient.RerankRequest okReq = new DashscopeRerankClient.RerankRequest(
                    "ak",
                    baseUrl,
                    "qwen3-rerank",
                    "q",
                    null,
                    3,
                    null,
                    "i",
                    null,
                    Map.of("X-Case", "ok"),
                    null,
                    null
            );
            String okResp = client.rerankOnce(okReq);
            assertNotNull(okResp);
            assertTrue(okResp.contains("\"ok\":true"));

            DashscopeRerankClient.RerankRequest errReq = new DashscopeRerankClient.RerankRequest(
                    "ak",
                    baseUrl,
                    "gte-rerank-v2",
                    "q2",
                    List.of("d"),
                    2,
                    true,
                    "i2",
                    0.2,
                    Map.of("X-Scenario", "error-with-body"),
                    1200,
                    3400
            );
            IOException withBody = assertThrows(IOException.class, () -> client.rerankOnce(errReq));
            assertTrue(withBody.getMessage().contains("HTTP 500"));

            DashscopeRerankClient.RerankRequest noBodyReq = new DashscopeRerankClient.RerankRequest(
                    "ak",
                    baseUrl,
                    "gte-rerank-v2",
                    "q3",
                    List.of("d"),
                    null,
                    false,
                    " ",
                    null,
                    Map.of("X-Scenario", "error-no-body"),
                    1200,
                    3400
            );
            IOException noBody = assertThrows(IOException.class, () -> client.rerankOnce(noBodyReq));
            assertTrue(noBody.getMessage().contains("without body"));
        } finally {
            server.stop(0);
        }
    }

    @Test
    void rerankOnce_coversModelValidationBranches() {
        DashscopeRerankClient client = new DashscopeRerankClient();
        DashscopeRerankClient.RerankRequest nullModel = new DashscopeRerankClient.RerankRequest(
                "ak",
                "http://localhost:8080/compatible-mode/v1",
                null,
                "q",
                List.of("d"),
                null,
                null,
                null,
                null,
                null,
                null,
                null
        );
        IllegalArgumentException ex1 = assertThrows(IllegalArgumentException.class, () -> client.rerankOnce(nullModel));
        assertTrue(ex1.getMessage().contains("model"));

        DashscopeRerankClient.RerankRequest blankModel = new DashscopeRerankClient.RerankRequest(
                "ak",
                "http://localhost:8080/compatible-mode/v1",
                "   ",
                "q",
                List.of("d"),
                null,
                null,
                null,
                null,
                null,
                null,
                null
        );
        IllegalArgumentException ex2 = assertThrows(IllegalArgumentException.class, () -> client.rerankOnce(blankModel));
        assertTrue(ex2.getMessage().contains("model"));
    }

    @Test
    void helpers_reflectionInvocationExceptionsAreTransparent() throws Exception {
        Method m = DashscopeRerankClient.class.getDeclaredMethod("normalizeString", String.class, String.class);
        m.setAccessible(true);
        assertEquals("x", m.invoke(null, "x", "fb"));

        InvocationTargetException ex = assertThrows(InvocationTargetException.class, () -> {
            Method bad = DashscopeRerankClient.class.getDeclaredMethod("readAll", java.io.InputStream.class);
            bad.setAccessible(true);
            bad.invoke(null, new Object[]{null});
        });
        assertNotNull(ex.getCause());
    }
}
