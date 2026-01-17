package com.example.EnterpriseRagCommunity.config;

import lombok.RequiredArgsConstructor;
import org.apache.http.HttpHeaders;
import org.apache.http.message.BasicHeader;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.elasticsearch.RestClientBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * Adds a default Authorization header (ApiKey) to Spring's underlying Elasticsearch REST client.
 *
 * This ensures ElasticsearchTemplate / IndexOperations calls (index create, mapping, etc.)
 * work when ES security is enabled.
 */
@Configuration
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "app.es", name = "api-key")
public class ElasticsearchApiKeyConfig {

    private final EsAuthProperties props;

    @Bean
    RestClientBuilderCustomizer elasticsearchApiKeyRestClientCustomizer() {
        return builder -> {
            String apiKey = props.getApiKey();
            if (apiKey == null || apiKey.isBlank()) return;

            builder.setDefaultHeaders(List.of(
                    new BasicHeader(HttpHeaders.AUTHORIZATION, "ApiKey " + apiKey.trim())
            ).toArray(new org.apache.http.Header[0]));
        };
    }
}
