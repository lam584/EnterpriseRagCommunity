package com.example.EnterpriseRagCommunity.testsupport;

import ch.vorburger.mariadb4j.DB;
import ch.vorburger.mariadb4j.DBConfigurationBuilder;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicBoolean;

public final class EmbeddedMariaDbBootstrap {

    private static final AtomicBoolean STARTED = new AtomicBoolean(false);
    private static volatile DB db;
    private static volatile int port;

    private EmbeddedMariaDbBootstrap() {
    }

    public static void ensureStarted() {
        if (STARTED.get()) {
            return;
        }
        synchronized (EmbeddedMariaDbBootstrap.class) {
            if (STARTED.get()) {
                return;
            }
            try {
                Path dataDir = Files.createTempDirectory("mariaDB4j-data");
                DBConfigurationBuilder config = DBConfigurationBuilder.newBuilder();
                config.setPort(0);
                config.setDataDir(dataDir.toString());
                config.addArg("--character-set-server=utf8mb4");
                config.addArg("--collation-server=utf8mb4_unicode_ci");

                db = DB.newEmbeddedDB(config.build());
                db.start();
                port = db.getConfiguration().getPort();
                db.createDB("EnterpriseRagCommunity_test");

                String jdbcUrl = "jdbc:mysql://localhost:" + port + "/EnterpriseRagCommunity_test"
                        + "?createDatabaseIfNotExist=true&useSSL=false&serverTimezone=Asia/Shanghai&allowPublicKeyRetrieval=true";
                System.setProperty("TEST_DB_JDBC_URL", jdbcUrl);
                System.setProperty("TEST_DB_USERNAME", "root");
                System.setProperty("TEST_DB_PASSWORD", "");

                Runtime.getRuntime().addShutdownHook(new Thread(EmbeddedMariaDbBootstrap::shutdown, "embedded-mariadb-shutdown"));

                STARTED.set(true);
            } catch (Exception e) {
                throw new IllegalStateException("Failed to start embedded MariaDB for tests.", e);
            }
        }
    }

    public static void shutdown() {
        synchronized (EmbeddedMariaDbBootstrap.class) {
            if (!STARTED.get()) {
                return;
            }
            try {
                if (db != null) {
                    db.stop();
                }
            } catch (Exception ignored) {
            } finally {
                db = null;
                STARTED.set(false);
            }
        }
    }
}
