package com.example.EnterpriseRagCommunity.db;

import com.example.EnterpriseRagCommunity.testsupport.MySQLTestContainerBootstrap;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

public class FlywayMigrationsSmokeTest {

    @Test
    void flywayMigrateSucceeds() {
        MySQLTestContainerBootstrap.ensureStarted();
        String url = System.getProperty("TEST_DB_JDBC_URL");
        String user = System.getProperty("TEST_DB_USERNAME");
        String pass = System.getProperty("TEST_DB_PASSWORD");

        Flyway flyway = Flyway.configure()
                .dataSource(url, user, pass)
                .locations("classpath:db/migration")
                .baselineOnMigrate(true)
                .baselineVersion("1")
                .validateOnMigrate(false)
                .load();

        assertDoesNotThrow(flyway::migrate);
    }
}

