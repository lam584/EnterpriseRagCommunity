package com.example.EnterpriseRagCommunity.config;

import org.apache.kafka.clients.CommonClientConfigs;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.config.SaslConfigs;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.kafka.KafkaProperties;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.ProducerFactory;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AccessLogKafkaProducerConfigTest {

    @Test
    void applyKafkaAuth_should_not_apply_when_disabled() {
        Map<String, Object> props = new LinkedHashMap<>();

        AccessLogKafkaProducerConfig.applyKafkaAuth(
                props,
                false,
                "SASL_SSL",
                "PLAIN",
                "k",
                "s"
        );

        assertFalse(props.containsKey(CommonClientConfigs.SECURITY_PROTOCOL_CONFIG));
        assertFalse(props.containsKey(SaslConfigs.SASL_JAAS_CONFIG));
    }

    @Test
    void applyKafkaAuth_should_apply_when_enabled_and_credentials_present() {
        Map<String, Object> props = new LinkedHashMap<>();

        AccessLogKafkaProducerConfig.applyKafkaAuth(
                props,
                true,
                "SASL_SSL",
                "PLAIN",
                "api-key",
                "api-secret"
        );

        assertEquals("SASL_SSL", props.get(CommonClientConfigs.SECURITY_PROTOCOL_CONFIG));
        assertEquals("PLAIN", props.get(SaslConfigs.SASL_MECHANISM));
        String jaas = String.valueOf(props.get(SaslConfigs.SASL_JAAS_CONFIG));
        assertTrue(jaas.contains("username=\"api-key\""));
        assertTrue(jaas.contains("password=\"api-secret\""));
    }

    @Test
    void accessLogsProducerFactory_should_include_auth_and_reliability_properties() {
        KafkaProperties kafkaProperties = new KafkaProperties();
        kafkaProperties.setBootstrapServers(List.of("127.0.0.1:9092"));

        AccessLogKafkaProducerConfig config = new AccessLogKafkaProducerConfig();
        ProducerFactory<String, String> pf = config.accessLogsProducerFactory(
                kafkaProperties,
                "all",
                true,
                10,
                5,
                120000,
                30000,
                5,
                "lz4",
                true,
                "SASL_SSL",
                "PLAIN",
                "k",
                "s"
        );

        Map<String, Object> out = ((DefaultKafkaProducerFactory<String, String>) pf).getConfigurationProperties();
        assertEquals("all", out.get(ProducerConfig.ACKS_CONFIG));
        assertEquals(true, out.get(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG));
        assertEquals(10, out.get(ProducerConfig.RETRIES_CONFIG));
        assertEquals("SASL_SSL", out.get(CommonClientConfigs.SECURITY_PROTOCOL_CONFIG));
        assertEquals("PLAIN", out.get(SaslConfigs.SASL_MECHANISM));
    }

    @Test
    void accessLogsConsumerFactory_should_include_auth_and_defaults() {
        KafkaProperties kafkaProperties = new KafkaProperties();
        kafkaProperties.setBootstrapServers(List.of("127.0.0.1:9092"));

        AccessLogKafkaProducerConfig config = new AccessLogKafkaProducerConfig();
        ConsumerFactory<String, String> cf = config.accessLogsConsumerFactory(
                kafkaProperties,
                true,
                "SASL_SSL",
                "PLAIN",
                "k",
                "s"
        );

        Map<String, Object> out = cf.getConfigurationProperties();
        assertEquals("latest", out.get(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG));
        assertEquals("SASL_SSL", out.get(CommonClientConfigs.SECURITY_PROTOCOL_CONFIG));
        assertEquals("PLAIN", out.get(SaslConfigs.SASL_MECHANISM));
    }
}
