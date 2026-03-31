package com.example.EnterpriseRagCommunity.dto.ai;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class UpdateAssistantPreferencesRequest {

    @JsonIgnore
    private boolean defaultProviderIdPresent;

    @Size(max = 128, message = "defaultProviderId长度不能超过128")
    private String defaultProviderId;

    @JsonIgnore
    private boolean defaultModelPresent;

    @Size(max = 128, message = "defaultModel长度不能超过128")
    private String defaultModel;

    @JsonIgnore
    private boolean defaultDeepThinkPresent;

    private Boolean defaultDeepThink;

    @JsonIgnore
    private boolean autoLoadLastSessionPresent;

    private Boolean autoLoadLastSession;

    @JsonIgnore
    private boolean defaultUseRagPresent;

    private Boolean defaultUseRag;

    @JsonIgnore
    private boolean ragTopKPresent;

    @Min(value = 1, message = "ragTopK不能小于1")
    @Max(value = 50, message = "ragTopK不能大于50")
    private Integer ragTopK;

    @JsonIgnore
    private boolean streamPresent;

    private Boolean stream;

    @JsonIgnore
    private boolean temperaturePresent;

    @DecimalMin(value = "0.0", message = "temperature不能小于0")
    @DecimalMax(value = "2.0", message = "temperature不能大于2")
    private Double temperature;

    @JsonIgnore
    private boolean topPPresent;

    @DecimalMin(value = "0.0", message = "topP不能小于0")
    @DecimalMax(value = "1.0", message = "topP不能大于1")
    private Double topP;

    @JsonIgnore
    private boolean defaultSystemPromptPresent;

    @Size(max = 4000, message = "defaultSystemPrompt长度不能超过4000")
    private String defaultSystemPrompt;

    public void setDefaultProviderId(String defaultProviderId) {
        this.defaultProviderIdPresent = true;
        this.defaultProviderId = defaultProviderId;
    }

    public void setDefaultModel(String defaultModel) {
        this.defaultModelPresent = true;
        this.defaultModel = defaultModel;
    }

    public void setDefaultDeepThink(Boolean defaultDeepThink) {
        this.defaultDeepThinkPresent = true;
        this.defaultDeepThink = defaultDeepThink;
    }

    public void setAutoLoadLastSession(Boolean autoLoadLastSession) {
        this.autoLoadLastSessionPresent = true;
        this.autoLoadLastSession = autoLoadLastSession;
    }

    public void setDefaultUseRag(Boolean defaultUseRag) {
        this.defaultUseRagPresent = true;
        this.defaultUseRag = defaultUseRag;
    }

    public void setRagTopK(Integer ragTopK) {
        this.ragTopKPresent = true;
        this.ragTopK = ragTopK;
    }

    public void setStream(Boolean stream) {
        this.streamPresent = true;
        this.stream = stream;
    }

    public void setTemperature(Double temperature) {
        this.temperaturePresent = true;
        this.temperature = temperature;
    }

    public void setTopP(Double topP) {
        this.topPPresent = true;
        this.topP = topP;
    }

    public void setDefaultSystemPrompt(String defaultSystemPrompt) {
        this.defaultSystemPromptPresent = true;
        this.defaultSystemPrompt = defaultSystemPrompt;
    }

}
