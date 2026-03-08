package com.example.EnterpriseRagCommunity.dto.semantic;

import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.Map;

@Data
public class PromptsQueryDTO {
    @ApiModelProperty("主键ID")
    private Long id;

    @ApiModelProperty("名称")
    private String name;

    @ApiModelProperty("Prompt编码")
    private String promptCode;

    @ApiModelProperty("系统Prompt包含")
    private String systemPromptContains;

    @ApiModelProperty("用户Prompt模板内容包含")
    private String userPromptTemplateContains;

    @ApiModelProperty("模型名称")
    private String modelName;

    @ApiModelProperty("供应商ID")
    private String providerId;

    @ApiModelProperty("模板变量(JSON)")
    private Map<String, Object> variables;

    @ApiModelProperty("版本号")
    private Integer version;

    @ApiModelProperty("是否启用")
    private Boolean isActive;

    @ApiModelProperty("创建时间-起")
    private LocalDateTime createdAtFrom;

    @ApiModelProperty("创建时间-止")
    private LocalDateTime createdAtTo;
}

