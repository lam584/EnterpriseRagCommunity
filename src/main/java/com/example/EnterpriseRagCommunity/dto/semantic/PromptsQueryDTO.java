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

    @ApiModelProperty("模板内容包含")
    private String templateContains;

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

