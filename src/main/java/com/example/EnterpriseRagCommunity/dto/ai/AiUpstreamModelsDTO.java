package com.example.EnterpriseRagCommunity.dto.ai;

import java.util.List;

public class AiUpstreamModelsDTO {
    private String providerId;
    private List<String> models;

    public String getProviderId() {
        return providerId;
    }

    public void setProviderId(String providerId) {
        this.providerId = providerId;
    }

    public List<String> getModels() {
        return models;
    }

    public void setModels(List<String> models) {
        this.models = models;
    }
}

