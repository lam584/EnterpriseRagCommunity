package com.example.EnterpriseRagCommunity.config;

import freemarker.template.Version;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import com.example.EnterpriseRagCommunity.service.ViteManifestService;
import freemarker.template.TemplateModelException;

@Configuration
public class FreemarkerGlobalConfig {
    @Autowired
    private ViteManifestService viteManifestService;

    @Autowired
    private freemarker.template.Configuration configuration;

    @PostConstruct
    public void customizeFreemarker() throws TemplateModelException {

        // 开启 ?api、它本身已经被 Spring Boot 初始化好了
        configuration.setAPIBuiltinEnabled(true);
        // 2. 强制提升 incompatible_improvements 到 2.3.34（这样才支持 <#block> 等新特性）
        configuration.setIncompatibleImprovements(
                new Version(2, 3, 34)
        );
        // 全局注入 vm 供所有模板直接用
        configuration.setSharedVariable("vm", viteManifestService);
    }
}