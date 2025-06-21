package com.example.NewsPublishingSystem;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.web.servlet.support.SpringBootServletInitializer;
import org.springframework.context.annotation.Bean;
import org.springframework.core.Ordered;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.view.InternalResourceViewResolver;

import java.util.concurrent.atomic.AtomicInteger;

@SpringBootApplication
@Controller
public class NewsPublishingSystemApplication extends SpringBootServletInitializer {

    private final AtomicInteger visitCounter = new AtomicInteger(0);

    public static void main(String[] args) {
        SpringApplication.run(NewsPublishingSystemApplication.class, args);
    }

    @Override
    protected SpringApplicationBuilder configure(SpringApplicationBuilder builder) {
        return builder.sources(NewsPublishingSystemApplication.class);
    }

    // —— 显式注册 JSP ViewResolver ——
    // 优先级要比 FreeMarker 的要高，才能让 "/home"、"/converted" 的返回值被 JSP 解析。
    @Bean
    public InternalResourceViewResolver jspViewResolver() {
        InternalResourceViewResolver resolver = new InternalResourceViewResolver();
        resolver.setPrefix("/WEB-INF/jsp/");
        resolver.setSuffix(".jsp");

        // 让它在所有 resolver 之后才尝试
        resolver.setOrder(Ordered.LOWEST_PRECEDENCE);

        // 只让它处理 home, converted 这两个逻辑视图名
        resolver.setViewNames("home", "converted");

        return resolver;
    }
    @GetMapping("/home")
    public String home() {
        return "home";       // 走 JSP,jsp测试
    }

    @GetMapping("/converted")
    public String converted() {
        return "converted";  // 走 JSP
    }

}
