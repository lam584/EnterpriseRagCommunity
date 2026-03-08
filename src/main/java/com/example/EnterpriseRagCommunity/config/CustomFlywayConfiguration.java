package com.example.EnterpriseRagCommunity.config;

import javax.sql.DataSource;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.configuration.FluentConfiguration;
import org.springframework.boot.autoconfigure.flyway.FlywayMigrationInitializer;
import org.springframework.boot.autoconfigure.flyway.FlywayProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(FlywayProperties.class)
public class CustomFlywayConfiguration {

    @Bean
    public Flyway flyway(FlywayProperties properties, DataSource dataSource) {
        FluentConfiguration configuration = Flyway.configure()
                .dataSource(dataSource)
                .locations(properties.getLocations().toArray(new String[0]))
                .baselineOnMigrate(properties.isBaselineOnMigrate())
                .outOfOrder(properties.isOutOfOrder())
                .encoding("UTF-8");

        // Map other properties that are commonly used
        if (properties.getBaselineVersion() != null) {
            configuration.baselineVersion(properties.getBaselineVersion());
        }
        if (properties.getTable() != null) {
            configuration.table(properties.getTable());
        }
        // Handle Boolean wrapper types safely
        configuration.failOnMissingLocations(properties.isFailOnMissingLocations());
        
        // Explicitly avoiding cleanOnValidationError which was removed in Flyway 12
        // configuration.cleanOnValidationError(properties.isCleanOnValidationError());

        return configuration.load();
    }

    @Bean
    public FlywayMigrationInitializer flywayMigrationInitializer(Flyway flyway) {
        return new FlywayMigrationInitializer(flyway, f -> {
            f.repair();
            f.migrate();
        });
    }
}
