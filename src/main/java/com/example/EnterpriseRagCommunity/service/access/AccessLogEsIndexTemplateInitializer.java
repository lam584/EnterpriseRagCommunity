package com.example.EnterpriseRagCommunity.service.access;

import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "app.logging.access.es-sink", name = "enabled", havingValue = "true")
public class AccessLogEsIndexTemplateInitializer implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(AccessLogEsIndexTemplateInitializer.class);

    private final AccessLogEsIndexProvisioningService provisioningService;

    @Value("${app.logging.access.es-sink.auto-init-template:true}")
    private boolean autoInitTemplate = true;

    @Override
    public void run(ApplicationArguments args) {
        if (!autoInitTemplate) return;
        try {
            provisioningService.initializeFromCurrentConfig();
        } catch (Exception ex) {
            log.warn("access_log_es_index_provision_failed err={}", ex.getMessage());
        }
    }
}
