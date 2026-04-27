package com.example.EnterpriseRagCommunity.service.access;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.env.Environment;
import org.springframework.kafka.config.KafkaListenerEndpointRegistry;
import org.springframework.kafka.listener.MessageListenerContainer;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Component;

@Component
public class AccessLogKafkaLifecycleManager {

    private static final Logger log = LoggerFactory.getLogger(AccessLogKafkaLifecycleManager.class);
    private static final String ACCESS_LOG_ES_SINK_CONSUMER_ID = "accessLogEsSinkConsumer";

    private final KafkaListenerEndpointRegistry kafkaListenerEndpointRegistry;
    private final Environment environment;

    public AccessLogKafkaLifecycleManager(
            @Nullable KafkaListenerEndpointRegistry kafkaListenerEndpointRegistry,
            Environment environment) {
        this.kafkaListenerEndpointRegistry = kafkaListenerEndpointRegistry;
        this.environment = environment;
    }

    public void startAccessLogEsSinkConsumerIfEnabled() {
        if (kafkaListenerEndpointRegistry == null) {
            log.info("KafkaListenerEndpointRegistry not available, skip starting access log ES sink consumer");
            return;
        }

        boolean esSinkEnabled = boolProperty("app.logging.access.es-sink.enabled", true);
        boolean consumerEnabled = boolProperty("app.logging.access.es-sink.consumer-enabled", false);

        if (!esSinkEnabled || !consumerEnabled) {
            log.info("Skip starting access log ES sink consumer: esSinkEnabled={}, consumerEnabled={}",
                    esSinkEnabled, consumerEnabled);
            return;
        }

        MessageListenerContainer container = kafkaListenerEndpointRegistry.getListenerContainer(ACCESS_LOG_ES_SINK_CONSUMER_ID);
        if (container == null) {
            log.warn("Kafka listener container '{}' not found", ACCESS_LOG_ES_SINK_CONSUMER_ID);
            return;
        }

        if (container.isRunning()) {
            log.info("Kafka listener container '{}' already running", ACCESS_LOG_ES_SINK_CONSUMER_ID);
            return;
        }

        container.start();
        boolean running = container.isRunning();
        if (running) {
            log.info("Kafka listener container '{}' started", ACCESS_LOG_ES_SINK_CONSUMER_ID);
        } else {
            log.warn("Kafka listener container '{}' failed to start", ACCESS_LOG_ES_SINK_CONSUMER_ID);
        }
    }

    private boolean boolProperty(String key, boolean defaultValue) {
        return Boolean.parseBoolean(environment.getProperty(key, Boolean.toString(defaultValue)));
    }
}
