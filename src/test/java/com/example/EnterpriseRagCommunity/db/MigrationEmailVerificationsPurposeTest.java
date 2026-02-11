package com.example.EnterpriseRagCommunity.db;

import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class MigrationEmailVerificationsPurposeTest {

    @Test
    void v6ShouldContainLogin2faPreferencePurpose() throws Exception {
        try (InputStream in = Thread.currentThread()
                .getContextClassLoader()
                .getResourceAsStream("db/migration/V6__extend_email_verifications_purpose.sql")) {
            assertNotNull(in);
            String sql = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            assertTrue(sql.contains("LOGIN_2FA_PREFERENCE"));
        }
    }
}
