package com.example.EnterpriseRagCommunity.service.access;

import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.env.Environment;
import org.springframework.kafka.config.KafkaListenerEndpointRegistry;
import org.springframework.kafka.listener.MessageListenerContainer;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class AccessLogKafkaLifecycleManager {

    private static final Logger log = LoggerFactory.getLogger(AccessLogKafkaLifecycleManager.class);
    private static final String ACCESS_LOG_ES_SINK_CONSUMER_ID = "accessLogEsSinkConsumer";

    private final KafkaListenerEndpointRegistry kafkaListenerEndpointRegistry;
    private final Environment environment;

    public boolean startAccessLogEsSinkConsumerIfEnabled() {
        boolean esSinkEnabled = boolProperty("app.logging.access.es-sink.enabled", true);
        boolean consumerEnabled = boolProperty("app.logging.access.es-sink.consumer-enabled", false);

        if (!esSinkEnabled || !consumerEnabled) {
            log.info("Skip starting access log ES sink consumer: esSinkEnabled={}, consumerEnabled={}",
                    esSinkEnabled, consumerEnabled);
            return false;
        }

        MessageListenerContainer container = kafkaListenerEndpointRegistry.getListenerContainer(ACCESS_LOG_ES_SINK_CONSUMER_ID);
        if (container == null) {
            log.warn("Kafka listener container '{}' not found", ACCESS_LOG_ES_SINK_CONSUMER_ID);
            return false;
        }

        if (container.isRunning()) {
            log.info("Kafka listener container '{}' already running", ACCESS_LOG_ES_SINK_CONSUMER_ID);
            return true;
        }

        container.start();
        boolean running = container.isRunning();
        if (running) {
            log.info("Kafka listener container '{}' started", ACCESS_LOG_ES_SINK_CONSUMER_ID);
        } else {
            log.warn("Kafka listener container '{}' failed to start", ACCESS_LOG_ES_SINK_CONSUMER_ID);
        }
        return running;
    }

    private boolean boolProperty(String key, boolean defaultValue) {
        return Boolean.parseBoolean(environment.getProperty(key, Boolean.toString(defaultValue)));
    }
}
