package com.example.EnterpriseRagCommunity.dto.ai;

import lombok.Data;

@Data
public class SupportedLanguageDTO {
    private String languageCode;
    private String displayName;
    private String nativeName;
    private Integer sortOrder;
}

