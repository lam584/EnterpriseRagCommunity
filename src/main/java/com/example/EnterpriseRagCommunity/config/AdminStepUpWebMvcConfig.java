package com.example.EnterpriseRagCommunity.config;

import com.example.EnterpriseRagCommunity.security.stepup.AdminStepUpInterceptor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class AdminStepUpWebMvcConfig implements WebMvcConfigurer {
    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new AdminStepUpInterceptor()).addPathPatterns("/api/**");
    }
}

