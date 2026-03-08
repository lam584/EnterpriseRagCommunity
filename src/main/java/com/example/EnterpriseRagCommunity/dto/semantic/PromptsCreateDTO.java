package com.example.EnterpriseRagCommunity.dto.semantic;

import io.swagger.annotations.ApiModelProperty;
import jakarta.validation.constraints.*;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.Map;

@Data
public class PromptsCreateDTO {
    @ApiModelProperty(value = "名称", required = true, example = "default-answer")
    @NotBlank
    @Size(max = 96)
    private String name;

    @ApiModelProperty(value = "Prompt编码", required = true, example = "default-answer-code")
    @NotBlank
    @Size(max = 64)
    private String promptCode;

    @ApiModelProperty(value = "系统Prompt")
    private String systemPrompt;

    @ApiModelProperty(value = "用户Prompt模板内容", required = true)
    @NotBlank
    private String userPromptTemplate;

    @ApiModelProperty(value = "模型名称", example = "gpt-4")
    @Size(max = 128)
    private String modelName;

    @ApiModelProperty(value = "供应商ID", example = "openai")
    @Size(max = 64)
    private String providerId;

    @ApiModelProperty(value = "温度", example = "0.7")
    private Double temperature;

    @ApiModelProperty(value = "Top P", example = "0.9")
    private Double topP;

    @ApiModelProperty(value = "最大Token数", example = "1000")
    private Integer maxTokens;

    @ApiModelProperty(value = "是否启用深度思考", example = "false")
    private Boolean enableDeepThinking;

    @ApiModelProperty(value = "模板变量(JSON)")
    private Map<String, Object> variables;

    @ApiModelProperty(value = "版本号", example = "1")
    @NotNull
    private Integer version = 1; // required, NOT NULL with default 1 in DB

    @ApiModelProperty(value = "是否启用", example = "true")
    @NotNull
    private Boolean isActive = true; // NOT NULL default 1

    @ApiModelProperty(value = "创建时间(由系统生成)", example = "2025-01-01T12:00:00", hidden = true)
    private LocalDateTime createdAt; // included per spec, provided by DB default
}
