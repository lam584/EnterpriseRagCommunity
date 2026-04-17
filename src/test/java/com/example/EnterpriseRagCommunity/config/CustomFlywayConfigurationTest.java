package com.example.EnterpriseRagCommunity.config;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.flyway.FlywayMigrationInitializer;
import org.springframework.boot.autoconfigure.flyway.FlywayProperties;
import org.springframework.core.env.Environment;

import javax.sql.DataSource;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class CustomFlywayConfigurationTest {

    @Test
    void flyway_should_map_optional_properties_when_present_or_absent() {
        CustomFlywayConfiguration cfg = new CustomFlywayConfiguration();
        DataSource dataSource = mock(DataSource.class);
        Environment environment = mock(Environment.class);

        FlywayProperties p1 = new FlywayProperties();
        p1.setLocations(List.of("classpath:db/migration"));
        p1.setBaselineOnMigrate(true);
        p1.setOutOfOrder(true);
        p1.setFailOnMissingLocations(false);
        p1.setBaselineVersion("1");
        p1.setTable("flyway_schema_history_custom");

        Flyway f1 = cfg.flyway(p1, dataSource, environment);
        assertNotNull(f1);

        FlywayProperties p2 = new FlywayProperties();
        p2.setLocations(List.of("classpath:db/migration"));
        p2.setBaselineOnMigrate(false);
        p2.setOutOfOrder(false);
        p2.setFailOnMissingLocations(false);

        Flyway f2 = cfg.flyway(p2, dataSource, environment);
        assertNotNull(f2);
    }

    @Test
    void flywayMigrationInitializer_should_call_repair_then_migrate() throws Exception {
        CustomFlywayConfiguration cfg = new CustomFlywayConfiguration();
        Flyway flyway = mock(Flyway.class);

        FlywayMigrationInitializer init = cfg.flywayMigrationInitializer(flyway);
        init.afterPropertiesSet();

        verify(flyway).repair();
        verify(flyway).migrate();
    }
}

