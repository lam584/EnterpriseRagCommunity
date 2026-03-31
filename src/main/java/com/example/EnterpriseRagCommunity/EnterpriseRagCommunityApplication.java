package com.example.EnterpriseRagCommunity;
import java.util.concurrent.atomic.AtomicInteger;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.data.elasticsearch.ElasticsearchRepositoriesAutoConfiguration;
import org.springframework.boot.autoconfigure.flyway.FlywayAutoConfiguration;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.web.servlet.support.SpringBootServletInitializer;
import org.springframework.data.web.config.EnableSpringDataWebSupport;
import org.springframework.scheduling.annotation.EnableAsync;

import static org.springframework.data.web.config.EnableSpringDataWebSupport.PageSerializationMode.VIA_DTO;

@SpringBootApplication(exclude = {FlywayAutoConfiguration.class, ElasticsearchRepositoriesAutoConfiguration.class})
@EnableAsync
@EnableSpringDataWebSupport(pageSerializationMode = VIA_DTO)
public class EnterpriseRagCommunityApplication extends SpringBootServletInitializer {

    private final AtomicInteger visitCounter = new AtomicInteger(0);

    public static void main(String[] args) {
        SpringApplication.run(EnterpriseRagCommunityApplication.class, args);
    }

    @Override
    protected SpringApplicationBuilder configure(SpringApplicationBuilder builder) {
        return builder.sources(EnterpriseRagCommunityApplication.class);
    }


}
