package com.example.EnterpriseRagCommunity.security;

import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class HttpURLConnectionLoggerLevelTest {

    @Test
    void applicationProperties_shouldDisableHttpURLConnectionInternalLogger() throws Exception {
        try (InputStream in = Thread.currentThread().getContextClassLoader().getResourceAsStream("application.properties")) {
            assertNotNull(in);
            String props = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            assertTrue(props.contains("logging.level.sun.net.www.protocol.http.HttpURLConnection=OFF"));
            assertTrue(props.contains("logging.level.sun.net.www.protocol.http=OFF"));
        }
    }
}
