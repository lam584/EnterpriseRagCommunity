package com.example.EnterpriseRagCommunity.testutil;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLConnection;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicBoolean;

public final class MockHttpUrl {

    private MockHttpUrl() {
    }

    private static final AtomicBoolean INSTALLED = new AtomicBoolean(false);
    private static final Queue<Response> RESPONSES = new ConcurrentLinkedQueue<>();
    private static final Queue<RequestCapture> REQUESTS = new ConcurrentLinkedQueue<>();

    public record Response(int status, String body) {
    }

    public record RequestCapture(
            String method,
            URL url,
            Map<String, String> headers,
            byte[] body
    ) {
    }

    public static void installOnce() {
        if (INSTALLED.get()) return;
        synchronized (MockHttpUrl.class) {
            if (INSTALLED.get()) return;
            String ours = "com.example.EnterpriseRagCommunity.testutil.protocol";
            String existing = System.getProperty("java.protocol.handler.pkgs");
            if (existing == null || existing.isBlank()) {
                System.setProperty("java.protocol.handler.pkgs", ours);
            } else if (!existing.contains(ours)) {
                System.setProperty("java.protocol.handler.pkgs", existing + "|" + ours);
            }
            INSTALLED.set(true);
        }
    }

    public static void reset() {
        RESPONSES.clear();
        REQUESTS.clear();
    }

    public static void enqueue(int status, String body) {
        RESPONSES.add(new Response(status, body));
    }

    public static RequestCapture pollRequest() {
        return REQUESTS.poll();
    }

    public static URLConnection openConnection(URL u) {
        Response resp = RESPONSES.poll();
        if (resp == null) resp = new Response(200, "{\"hits\":{\"hits\":[]}}");
        return new Conn(u, resp);
    }

    private static final class Conn extends HttpURLConnection {

        private final Response response;
        private final Map<String, String> headers = new LinkedHashMap<>();
        private final ByteArrayOutputStream requestBody = new ByteArrayOutputStream();
        private boolean connected = false;
        private boolean captured = false;

        protected Conn(URL u, Response response) {
            super(u);
            this.response = Objects.requireNonNull(response);
        }

        @Override
        public void disconnect() {
            connected = false;
        }

        @Override
        public boolean usingProxy() {
            return false;
        }

        @Override
        public void connect() {
            connected = true;
        }

        @Override
        public void setRequestMethod(String method) {
            this.method = method;
        }

        @Override
        public void setRequestProperty(String key, String value) {
            if (key != null) {
                headers.put(key, value);
            }
        }

        @Override
        public String getRequestProperty(String key) {
            return headers.get(key);
        }

        @Override
        public OutputStream getOutputStream() {
            return requestBody;
        }

        @Override
        public int getResponseCode() {
            captureIfNeeded();
            return response.status();
        }

        @Override
        public InputStream getInputStream() throws IOException {
            captureIfNeeded();
            if (response.status() < 200 || response.status() >= 300) {
                throw new IOException("HTTP " + response.status());
            }
            String b = response.body();
            if (b == null) return new ByteArrayInputStream(new byte[0]);
            return new ByteArrayInputStream(b.getBytes(StandardCharsets.UTF_8));
        }

        @Override
        public InputStream getErrorStream() {
            captureIfNeeded();
            if (response.status() >= 200 && response.status() < 300) return null;
            String b = response.body();
            if (b == null) return null;
            return new ByteArrayInputStream(b.getBytes(StandardCharsets.UTF_8));
        }

        private void captureIfNeeded() {
            if (!connected) connected = true;
            if (captured) return;
            captured = true;
            REQUESTS.add(new RequestCapture(
                    method,
                    url,
                    Map.copyOf(headers),
                    requestBody.toByteArray()
            ));
        }
    }
}
