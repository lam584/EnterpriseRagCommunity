package com.example.EnterpriseRagCommunity.dto.ai;

import java.util.List;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class AiUpstreamModelsDTO {
    private String providerId;
    private List<String> models;
}

