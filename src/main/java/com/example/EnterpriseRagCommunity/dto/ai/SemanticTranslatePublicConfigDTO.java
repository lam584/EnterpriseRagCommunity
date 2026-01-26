package com.example.EnterpriseRagCommunity.dto.ai;

import java.util.List;

import lombok.Data;

@Data
public class SemanticTranslatePublicConfigDTO {
    private Boolean enabled;
    private List<String> allowedTargetLanguages;
}
