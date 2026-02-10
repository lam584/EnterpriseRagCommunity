package com.example.EnterpriseRagCommunity.config;

import com.example.EnterpriseRagCommunity.service.config.SystemConfigurationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.http.HttpHost;
import org.apache.http.message.BasicHeader;
import org.elasticsearch.client.RestClient;
import org.elasticsearch.client.RestClientBuilder;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.aop.target.HotSwappableTargetSource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.util.StringUtils;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Configuration
@RequiredArgsConstructor
public class DynamicElasticsearchConfig {

    private final SystemConfigurationService systemConfigurationService;
    private HotSwappableTargetSource targetSource;

    @Bean
    @Primary
    public RestClient restClient() {
        // 1. 创建初始的实际 Client
        RestClient initialClient = createClient();
        
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
    public void refresh() {
        if (targetSource == null) {
            log.warn("TargetSource is null, cannot refresh RestClient.");
            return;
        }
        log.info("Refreshing Elasticsearch RestClient...");
        RestClient newClient = createClient();
        RestClient oldClient = (RestClient) targetSource.swap(newClient);
        
        try {
            if (oldClient != null) {
                oldClient.close();
                log.info("Closed old RestClient.");
            }
        } catch (Exception e) {
            log.warn("Error closing old RestClient: {}", e.getMessage());
        }
        log.info("Swapped to new RestClient.");
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
}
