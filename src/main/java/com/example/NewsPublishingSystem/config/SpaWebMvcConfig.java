package com.example.NewsPublishingSystem.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.resource.PathResourceResolver;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ClassPathResource;

import java.io.IOException;

@Configuration
public class SpaWebMvcConfig implements WebMvcConfigurer {

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        // 只用一个 /**，既能匹配所有静态文件，也能做 SPA 路由回退
        registry
                .addResourceHandler("/**")
                .addResourceLocations("classpath:/static/")   // 前端打包后的文件都被 copy 到这里
                .resourceChain(true)
                .addResolver(new PathResourceResolver() {
                    @Override
                    protected Resource getResource(String resourcePath, Resource location) throws IOException {
                        // 如果是 API 调用 (例如路径以 "api/" 开头)，则不由此 Handler 处理
                        // 交给后续的 Controller Handler (例如 RequestMappingHandlerMapping)
                        if (resourcePath.startsWith("api/")) {
                            return null; // 让请求传递给下一个处理器
                        }

                        // 先尝试读真实文件
                        Resource requested = location.createRelative(resourcePath);
                        if (requested.exists() && requested.isReadable()) {
                            return requested;
                        }
                        // 如果路径里不含“.”（说明不是资源文件），则回退到 index.html
                        if (!resourcePath.contains(".")) {
                            return new ClassPathResource("/static/index.html");
                        }
                        // 走到这里说明既不是前端路由也不是已有文件，返回 null，交给下一个 Handler 处理
                        return null;
                    }
                });
    }
}


