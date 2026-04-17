package com.example.EnterpriseRagCommunity.config;

import org.apache.kafka.clients.CommonClientConfigs;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.config.SaslConfigs;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.kafka.KafkaProperties;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;
import org.springframework.kafka.listener.ContainerProperties;

import java.util.LinkedHashMap;
import java.util.Map;

@Configuration
public class AccessLogKafkaProducerConfig {

    @Bean(name = "accessLogsProducerFactory")
    @ConditionalOnProperty(prefix = "app.logging.access.kafka.producer", name = "enabled", havingValue = "true", matchIfMissing = true)
    public ProducerFactory<String, String> accessLogsProducerFactory(
            KafkaProperties kafkaProperties,
            @Value("${app.logging.access.kafka.producer.acks:all}") String acks,
            @Value("${app.logging.access.kafka.producer.idempotence:true}") boolean idempotence,
            @Value("${app.logging.access.kafka.producer.retries:10}") int retries,
            @Value("${app.logging.access.kafka.producer.max-in-flight:5}") int maxInFlight,
            @Value("${app.logging.access.kafka.producer.delivery-timeout-ms:120000}") int deliveryTimeoutMs,
            @Value("${app.logging.access.kafka.producer.request-timeout-ms:30000}") int requestTimeoutMs,
            @Value("${app.logging.access.kafka.producer.linger-ms:5}") int lingerMs,
            @Value("${app.logging.access.kafka.producer.compression-type:lz4}") String compressionType,
            @Value("${APP_KAFKA_AUTH_ENABLED:false}") boolean kafkaAuthEnabled,
            @Value("${APP_KAFKA_SECURITY_PROTOCOL:SASL_SSL}") String kafkaSecurityProtocol,
            @Value("${APP_KAFKA_SASL_MECHANISM:PLAIN}") String kafkaSaslMechanism,
            @Value("${APP_KAFKA_API_KEY:}") String kafkaApiKey,
            @Value("${APP_KAFKA_API_SECRET:}") String kafkaApiSecret
    ) {
        Map<String, Object> props = new LinkedHashMap<>(kafkaProperties.buildProducerProperties());
        props.putIfAbsent(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        props.putIfAbsent(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class);

        props.put(ProducerConfig.ACKS_CONFIG, acks);
        props.put(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, idempotence);
        props.put(ProducerConfig.RETRIES_CONFIG, Math.max(0, retries));
        props.put(ProducerConfig.MAX_IN_FLIGHT_REQUESTS_PER_CONNECTION, Math.max(1, maxInFlight));
        props.put(ProducerConfig.DELIVERY_TIMEOUT_MS_CONFIG, Math.max(1000, deliveryTimeoutMs));
        props.put(ProducerConfig.REQUEST_TIMEOUT_MS_CONFIG, Math.max(1000, requestTimeoutMs));
        props.put(ProducerConfig.LINGER_MS_CONFIG, Math.max(0, lingerMs));
        props.put(ProducerConfig.COMPRESSION_TYPE_CONFIG, compressionType == null || compressionType.isBlank() ? "lz4" : compressionType.trim());
        applyKafkaAuth(props, kafkaAuthEnabled, kafkaSecurityProtocol, kafkaSaslMechanism, kafkaApiKey, kafkaApiSecret);

        return new DefaultKafkaProducerFactory<>(props);
    }

    @Bean(name = "accessLogsKafkaTemplate")
    @ConditionalOnProperty(prefix = "app.logging.access.kafka.producer", name = "enabled", havingValue = "true", matchIfMissing = true)
    public KafkaTemplate<String, String> accessLogsKafkaTemplate(ProducerFactory<String, String> accessLogsProducerFactory) {
        return new KafkaTemplate<>(accessLogsProducerFactory);
    }

    @Bean(name = "accessLogsConsumerFactory")
    @ConditionalOnProperty(prefix = "app.logging.access.es-sink", name = "enabled", havingValue = "true")
    public ConsumerFactory<String, String> accessLogsConsumerFactory(
            KafkaProperties kafkaProperties,
            @Value("${APP_KAFKA_AUTH_ENABLED:false}") boolean kafkaAuthEnabled,
            @Value("${APP_KAFKA_SECURITY_PROTOCOL:SASL_SSL}") String kafkaSecurityProtocol,
            @Value("${APP_KAFKA_SASL_MECHANISM:PLAIN}") String kafkaSaslMechanism,
            @Value("${APP_KAFKA_API_KEY:}") String kafkaApiKey,
            @Value("${APP_KAFKA_API_SECRET:}") String kafkaApiSecret
    ) {
        Map<String, Object> props = new LinkedHashMap<>(kafkaProperties.buildConsumerProperties());
        props.putIfAbsent(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        props.putIfAbsent(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        props.putIfAbsent(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "latest");
        applyKafkaAuth(props, kafkaAuthEnabled, kafkaSecurityProtocol, kafkaSaslMechanism, kafkaApiKey, kafkaApiSecret);
        return new DefaultKafkaConsumerFactory<>(props);
    }

    @Bean(name = "accessLogKafkaListenerContainerFactory")
    @ConditionalOnProperty(prefix = "app.logging.access.es-sink", name = "enabled", havingValue = "true")
    public ConcurrentKafkaListenerContainerFactory<String, String> accessLogKafkaListenerContainerFactory(
            ConsumerFactory<String, String> accessLogsConsumerFactory
    ) {
        ConcurrentKafkaListenerContainerFactory<String, String> factory = new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(accessLogsConsumerFactory);
        factory.getContainerProperties().setAckMode(ContainerProperties.AckMode.MANUAL_IMMEDIATE);
        return factory;
    }

    static void applyKafkaAuth(
            Map<String, Object> props,
            boolean authEnabled,
            String securityProtocol,
            String saslMechanism,
            String apiKey,
            String apiSecret
    ) {
        if (props == null || !authEnabled) return;
        if (apiKey == null || apiKey.isBlank() || apiSecret == null || apiSecret.isBlank()) return;

        String protocol = securityProtocol == null || securityProtocol.isBlank() ? "SASL_SSL" : securityProtocol.trim();
        String mechanism = saslMechanism == null || saslMechanism.isBlank() ? "PLAIN" : saslMechanism.trim();
        String escapedKey = apiKey.replace("\\", "\\\\").replace("\"", "\\\"");
        String escapedSecret = apiSecret.replace("\\", "\\\\").replace("\"", "\\\"");

        props.put(CommonClientConfigs.SECURITY_PROTOCOL_CONFIG, protocol);
        props.put(SaslConfigs.SASL_MECHANISM, mechanism);
        props.put(
                SaslConfigs.SASL_JAAS_CONFIG,
                "org.apache.kafka.common.security.plain.PlainLoginModule required username=\""
                        + escapedKey + "\" password=\"" + escapedSecret + "\";"
        );
    }
}
