package com.example.EnterpriseRagCommunity.controller;

//import org.springframework.stereotype.Controller;
//import org.springframework.web.bind.annotation.GetMapping;
//
//@Controller
//public class SpaController {
//
//    // 访问站点根路径时，转发到打包后的 index.html
//    @GetMapping({"/", "/index", "/index.html"})
//    public String index() {
//        return "forward:/index.html";
//    }
//
//    // 兼容 Spring 6 PathPatternParser：使用首段变量 + 结尾 **，且排除后端与静态资源前缀
//    // 说明：
//    // - {path:...} 仅匹配首个路径段，并使用负向前瞻排除 api、assets、static、public、resources、webjars 等常见静态与后端前缀
//    // - 结尾使用 /** 捕获其余多级段（PathPattern 要求 ** 必须位于末尾）
//    // - 避免包含点号的静态资源（如 favicon.ico），通过排除列表规避
//    @GetMapping("/{path:^(?!api|assets|static|public|resources|webjars|db|css|js|img|images|fonts|favicon\\.ico$).*$}/**")
//    public String any() {
//        return "forward:/index.html";
//    }
//}


import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.resource.PathResourceResolver;
import org.springframework.core.io.Resource;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;

@Configuration
public class SpaController implements WebMvcConfigurer {

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        Path viteDist = Paths.get("my-vite-app", "dist").toAbsolutePath().normalize();
        String viteDistLocation = viteDist.toUri().toString();
        if (!viteDistLocation.endsWith("/")) {
            viteDistLocation = viteDistLocation + "/";
        }

        // 只用一个 /**，既能匹配所有静态文件，也能做 SPA 路由回退
        registry
                .addResourceHandler("/**")
                .addResourceLocations(
                        viteDistLocation,
                        "classpath:/static/"
                )
                .resourceChain(true)
                .addResolver(new PathResourceResolver() {
                    @Override
                    protected Resource getResource(String resourcePath, Resource location) throws IOException {
                        String normalized = resourcePath == null ? "" : resourcePath;
                        while (normalized.startsWith("/")) {
                            normalized = normalized.substring(1);
                        }
                        if (normalized.isBlank()) {
                            Resource index = location.createRelative("index.html");
                            if (index.exists() && index.isReadable()) {
                                return index;
                            }
                            return null;
                        }

                        // 如果是 API 调用 (例如路径以 "api/" 开头)，则不由此 Handler 处理
                        // 交给后续的 Controller Handler (例如 RequestMappingHandlerMapping)
                        if (normalized.startsWith("api/")) {
                            return null; // 让请求传递给下一个处理器
                        }

                        // 先尝试读真实文件
                        Resource requested = location.createRelative(normalized);
                        if (requested.exists() && requested.isReadable()) {
                            return requested;
                        }
                        // 如果路径里不含“.”（说明不是资源文件），则回退到 index.html
                        if (!normalized.contains(".")) {
                            Resource index = location.createRelative("index.html");
                            if (index.exists() && index.isReadable()) {
                                return index;
                            }
                        }
                        // 走到这里说明既不是前端路由也不是已有文件，返回 null，交给下一个 Handler 处理
                        return null;
                    }
                });
    }
}
