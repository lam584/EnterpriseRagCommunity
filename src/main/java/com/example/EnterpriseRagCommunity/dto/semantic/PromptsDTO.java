package com.example.EnterpriseRagCommunity.dto.semantic;

import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.Map;

@Data
public class PromptsDTO {
    @ApiModelProperty("主键ID")
    private Long id;

    @ApiModelProperty("名称")
    private String name;

    @ApiModelProperty("Prompt编码")
    private String promptCode;

    @ApiModelProperty("系统Prompt")
    private String systemPrompt;

    @ApiModelProperty("用户Prompt模板内容")
    private String userPromptTemplate;

    @ApiModelProperty("模型名称")
    private String modelName;

    @ApiModelProperty("供应商ID")
    private String providerId;

    @ApiModelProperty("温度")
    private Double temperature;

    @ApiModelProperty("Top P")
    private Double topP;

    @ApiModelProperty("最大Token数")
    private Integer maxTokens;

    @ApiModelProperty("是否启用深度思考")
    private Boolean enableDeepThinking;

    @ApiModelProperty("模板变量(JSON)")
    private Map<String, Object> variables;

    @ApiModelProperty("版本号")
    private Integer version;

    @ApiModelProperty("是否启用")
    private Boolean isActive;

    @ApiModelProperty("创建时间")
    private LocalDateTime createdAt;

    @ApiModelProperty("更新人ID")
    private Long updatedBy;
}
