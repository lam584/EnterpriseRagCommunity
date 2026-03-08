package com.example.EnterpriseRagCommunity.testsupport;

import java.util.concurrent.atomic.AtomicBoolean;

import org.testcontainers.containers.MySQLContainer;

public final class MySQLTestContainerBootstrap {

    private static final AtomicBoolean STARTED = new AtomicBoolean(false);
    private static volatile MySQLContainer<?> mysqlContainer;

    private MySQLTestContainerBootstrap() {
    }

    public static void ensureStarted() {
        if (STARTED.get()) {
            return;
        }
        synchronized (MySQLTestContainerBootstrap.class) {
            if (STARTED.get()) {
                return;
            }
            try {
                // Modified to use local MySQL instead of Docker Testcontainers
                // Original Docker logic is commented out below
                /*
                // Using MySQL 8.0.36 to support "INSERT ... AS new" syntax
                // Docker Image: mysql:8.0.36
                mysqlContainer = new MySQLContainer<>("mysql:8.0.36")
                        .withDatabaseName("EnterpriseRagCommunity_test")
                        .withUsername("root")
                        .withPassword("password")
                        .withCommand("--character-set-server=utf8mb4", "--collation-server=utf8mb4_unicode_ci");

                mysqlContainer.start();

                String baseJdbcUrl = mysqlContainer.getJdbcUrl();
                String connectionParams = "createDatabaseIfNotExist=true&useSSL=false&serverTimezone=Asia/Shanghai&allowPublicKeyRetrieval=true";
                String jdbcUrl;
                if (baseJdbcUrl.contains("?")) {
                    jdbcUrl = baseJdbcUrl + "&" + connectionParams;
                } else {
                    jdbcUrl = baseJdbcUrl + "?" + connectionParams;
                }
                
                System.setProperty("TEST_DB_JDBC_URL", jdbcUrl);
                System.setProperty("TEST_DB_USERNAME", mysqlContainer.getUsername());
                System.setProperty("TEST_DB_PASSWORD", mysqlContainer.getPassword());

                Runtime.getRuntime().addShutdownHook(new Thread(MySQLTestContainerBootstrap::shutdown, "mysql-container-shutdown"));
                */

                // Local MySQL Configuration
                String localJdbcUrl = "jdbc:mysql://localhost:3306/EnterpriseRagCommunity_test?createDatabaseIfNotExist=true&useSSL=false&serverTimezone=Asia/Shanghai&allowPublicKeyRetrieval=true";
                // Prefer environment variables, fallback to defaults (root/password)
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

                STARTED.set(true);
            } catch (Exception e) {
                throw new IllegalStateException("Failed to configure local MySQL for tests.", e);
            }
        }
    }

    public static void shutdown() {
        synchronized (MySQLTestContainerBootstrap.class) {
            if (!STARTED.get()) {
                return;
            }
            try {
                if (mysqlContainer != null) {
                    mysqlContainer.stop();
                }
            } catch (Exception ignored) {
            } finally {
                mysqlContainer = null;
                STARTED.set(false);
            }
        }
    }
}
