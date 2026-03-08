package com.example.EnterpriseRagCommunity.testsupport;

import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.ExtensionContext;

public final class MySQLTestContainerExtension implements BeforeAllCallback {

    static {
        ensureLocalDbProps();
    }

    @Override
    public void beforeAll(ExtensionContext context) {
        ensureLocalDbProps();
    }

    private static void ensureLocalDbProps() {
        String localJdbcUrl = "jdbc:mysql://localhost:3306/EnterpriseRagCommunity_test?createDatabaseIfNotExist=true&useSSL=false&serverTimezone=Asia/Shanghai&allowPublicKeyRetrieval=true";
        String localUsername = System.getenv("DB_USERNAME");
        if (localUsername == null || localUsername.isEmpty()) {
            localUsername = "root";
        }
        String localPassword = System.getenv("DB_PASSWORD");
        if (localPassword == null || localPassword.isEmpty()) {
            localPassword = "password";
        }

        System.setProperty("TEST_DB_JDBC_URL", localJdbcUrl);
        System.setProperty("TEST_DB_USERNAME", localUsername);
        System.setProperty("TEST_DB_PASSWORD", localPassword);
    }
}
