package com.example.EnterpriseRagCommunity.service.ai.client;

import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.nio.charset.StandardCharsets;

final class RerankHttpResponseSupport {

    private RerankHttpResponseSupport() {
    }

    static String readJsonResponse(HttpURLConnection conn) throws IOException {
        int code = conn.getResponseCode();
        InputStream is = (code >= 200 && code < 300) ? conn.getInputStream() : conn.getErrorStream();
        if (is == null) throw new IOException("Upstream returned HTTP " + code + " without body");
        String resp = new String(is.readAllBytes(), StandardCharsets.UTF_8);
        if (code < 200 || code >= 300) {
            throw new IOException("Upstream returned HTTP " + code + ": " + resp);
        }
        return resp;
    }
}
