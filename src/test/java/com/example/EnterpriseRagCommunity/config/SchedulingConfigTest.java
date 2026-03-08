package com.example.EnterpriseRagCommunity.config;

import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.core.env.MapPropertySource;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SchedulingConfigTest {

    @Test
    void schedulingConfig_should_load_when_property_missing_or_true() {
        try (AnnotationConfigApplicationContext ctx = new AnnotationConfigApplicationContext()) {
            ctx.register(SchedulingConfig.class);
            ctx.refresh();
            assertNotNull(ctx.getBean(SchedulingConfig.class));
        }

        try (AnnotationConfigApplicationContext ctx = new AnnotationConfigApplicationContext()) {
            ctx.getEnvironment().getPropertySources().addFirst(
                    new MapPropertySource("t", Map.of("app.scheduling.enabled", "true"))
            );
            ctx.register(SchedulingConfig.class);
            ctx.refresh();
            assertNotNull(ctx.getBean(SchedulingConfig.class));
        }
    }

    @Test
    void schedulingConfig_should_not_load_when_property_false() {
        try (AnnotationConfigApplicationContext ctx = new AnnotationConfigApplicationContext()) {
            ctx.getEnvironment().getPropertySources().addFirst(
                    new MapPropertySource("t", Map.of("app.scheduling.enabled", "false"))
            );
            ctx.register(SchedulingConfig.class);
            ctx.refresh();
            assertThrows(Exception.class, () -> ctx.getBean(SchedulingConfig.class));
        }
    }
}

