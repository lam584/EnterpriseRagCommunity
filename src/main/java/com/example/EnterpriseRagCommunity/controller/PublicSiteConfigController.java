package com.example.EnterpriseRagCommunity.controller;

import com.example.EnterpriseRagCommunity.service.config.SystemConfigurationService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/public")
public class PublicSiteConfigController {

    private final SystemConfigurationService systemConfigurationService;

    public PublicSiteConfigController(SystemConfigurationService systemConfigurationService) {
        this.systemConfigurationService = systemConfigurationService;
    }

    @GetMapping("/site-config")
    public Map<String, Object> getSiteConfig() {
        String beianText = trimToNull(systemConfigurationService.getConfig("APP_SITE_BEIAN"));
        String beianHref = trimToNull(systemConfigurationService.getConfig("APP_SITE_BEIAN_HREF"));
        if (beianHref == null) {
            beianHref = "https://beian.miit.gov.cn/";
        }
        Map<String, Object> out = new java.util.LinkedHashMap<>();
        out.put("beianText", beianText);
        out.put("beianHref", beianText == null ? null : beianHref);
        return out;
    }

    private static String trimToNull(String value) {
        if (value == null) return null;
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
