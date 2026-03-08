package com.example.EnterpriseRagCommunity.db;

import com.example.EnterpriseRagCommunity.testsupport.MySQLTestContainerBootstrap;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class RolesSeedSmokeTest {
    @Test
    void builtinRolesExistAfterMigrate() throws Exception {
        MySQLTestContainerBootstrap.ensureStarted();
        String baseUrl = System.getProperty("TEST_DB_JDBC_URL");
        String user = System.getProperty("TEST_DB_USERNAME");
        String pass = System.getProperty("TEST_DB_PASSWORD");

        String url = withDatabase(baseUrl, "EnterpriseRagCommunity_test_roles_seed");
        Flyway flyway = Flyway.configure()
                .dataSource(url, user, pass)
                .locations("classpath:db/migration")
                .baselineOnMigrate(true)
                .baselineVersion("1")
                .cleanDisabled(false)
                .validateOnMigrate(false)
                .load();
        flyway.clean();
        flyway.migrate();

        Set<Long> ids = new HashSet<>();
        try (Connection c = DriverManager.getConnection(url, user, pass);
             PreparedStatement ps = c.prepareStatement("select role_id from roles where role_id in (1,2)");
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                ids.add(rs.getLong(1));
            }
        }

        assertEquals(Set.of(1L, 2L), ids);
    }

    private static String withDatabase(String jdbcUrl, String dbName) {
        int q = jdbcUrl.indexOf('?');
        String base = q >= 0 ? jdbcUrl.substring(0, q) : jdbcUrl;
        String suffix = q >= 0 ? jdbcUrl.substring(q) : "";
        int slash = base.lastIndexOf('/');
        if (slash < 0) {
            throw new IllegalArgumentException("Invalid JDBC url: " + jdbcUrl);
        }
        return base.substring(0, slash + 1) + dbName + suffix;
    }
}
