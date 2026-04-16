package com.example.EnterpriseRagCommunity.config;

import com.example.EnterpriseRagCommunity.service.config.SystemConfigurationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.http.impl.nio.client.HttpAsyncClientBuilder;
import org.apache.http.HttpHost;
import org.apache.http.message.BasicHeader;
import org.elasticsearch.client.RestClient;
import org.elasticsearch.client.RestClientBuilder;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.aop.target.HotSwappableTargetSource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Lazy;
import org.springframework.util.StringUtils;

import jakarta.annotation.PreDestroy;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

@Slf4j
@Configuration
@RequiredArgsConstructor
public class DynamicElasticsearchConfig {

    private final SystemConfigurationService systemConfigurationService;
    private HotSwappableTargetSource targetSource;
    private final AtomicReference<RestClient> currentClient = new AtomicReference<>();

    @Bean
    @Lazy
    @Primary
    public RestClient restClient() {
        // 1. 创建初始的实际 Client
        RestClient initialClient = createClient();
        currentClient.set(initialClient);
        
        // 2. 创建 TargetSource
        this.targetSource = new HotSwappableTargetSource(initialClient);

        // 3. 创建代理
        ProxyFactory proxyFactory = new ProxyFactory();
        proxyFactory.setTargetSource(targetSource);
        proxyFactory.setProxyTargetClass(true); // RestClient 是一个类，必须使用 CGLIB 代理
        
        log.info("Initialized dynamic RestClient proxy with initial client.");
        return (RestClient) proxyFactory.getProxy();
    }

    /**
     * 刷新 RestClient，通常在配置变更后调用。
     * 这会关闭旧连接并创建新连接。
     */
    public synchronized void refresh() {
        if (targetSource == null) {
            RestClient initialClient = createClient();
            currentClient.set(initialClient);
            this.targetSource = new HotSwappableTargetSource(initialClient);
        }
        log.info("Refreshing Elasticsearch RestClient...");
        RestClient newClient = createClient();
        RestClient oldClient = (RestClient) targetSource.swap(newClient);
        currentClient.set(newClient);
        
        try {
            oldClient.close();
            log.info("Closed old RestClient.");
        } catch (Exception e) {
            log.warn("Error closing old RestClient: {}", e.getMessage());
        }
        log.info("Swapped to new RestClient.");
    }

    @PreDestroy
    public void shutdown() {
        RestClient client = currentClient.getAndSet(null);
        if (client == null) return;
        try {
            client.close();
        } catch (Exception e) {
            log.warn("Error closing RestClient on shutdown: {}", e.getMessage());
        }
    }

    private RestClient createClient() {
        String uris = systemConfigurationService.getConfig("spring.elasticsearch.uris");
        if (!StringUtils.hasText(uris)) {
            uris = "http://localhost:9200";
        }
        String apiKey = systemConfigurationService.getConfig("APP_ES_API_KEY");

        List<HttpHost> hosts = new ArrayList<>();
        for (String u : uris.split(",")) {
            if (!StringUtils.hasText(u)) continue;
            try {
                URI uri = URI.create(u.trim());
                String scheme = uri.getScheme() == null ? "http" : uri.getScheme();
                hosts.add(new HttpHost(uri.getHost(), uri.getPort(), scheme));
            } catch (Exception ignored) {
            }
        }
        if (hosts.isEmpty()) {
            hosts.add(new HttpHost("localhost", 9200, "http"));
        }

        RestClientBuilder builder = RestClient.builder(hosts.toArray(new HttpHost[0]));

        int connectTimeoutMs = getPositiveIntConfig("spring.elasticsearch.connection-timeout", 3000);
        int socketTimeoutMs = getPositiveIntConfig("spring.elasticsearch.socket-timeout", 10000);
        int requestTimeoutMs = getPositiveIntConfig("spring.elasticsearch.connection-request-timeout", 2000);
        int maxConnTotal = getPositiveIntConfig("spring.elasticsearch.max-conn-total", 500);
        int maxConnPerRoute = getPositiveIntConfig("spring.elasticsearch.max-conn-per-route", 200);

        builder.setRequestConfigCallback(requestConfigBuilder -> requestConfigBuilder
            .setConnectTimeout(connectTimeoutMs)
            .setSocketTimeout(socketTimeoutMs)
            .setConnectionRequestTimeout(requestTimeoutMs));

        builder.setHttpClientConfigCallback((HttpAsyncClientBuilder httpClientBuilder) ->
            httpClientBuilder
                .setMaxConnTotal(maxConnTotal)
                .setMaxConnPerRoute(maxConnPerRoute));

        // 静态设置 Header (不再使用拦截器)
        if (StringUtils.hasText(apiKey)) {
            builder.setDefaultHeaders(new org.apache.http.Header[]{
                    new BasicHeader("Authorization", "ApiKey " + apiKey.trim())
            });
            log.info("Configured RestClient with API Key from database.");
        } else {
            log.warn("No API Key found in database (APP_ES_API_KEY). RestClient will perform unauthenticated requests.");
        }

        return builder.build();
    }

    private int getPositiveIntConfig(String key, int defaultValue) {
        String raw = systemConfigurationService.getConfig(key);
        if (!StringUtils.hasText(raw)) {
            return defaultValue;
        }
        try {
            int parsed = Integer.parseInt(raw.trim());
            return parsed > 0 ? parsed : defaultValue;
        } catch (Exception ex) {
            log.warn("Invalid numeric config {}={}, fallback to {}", key, raw, defaultValue);
            return defaultValue;
        }
    }
}
