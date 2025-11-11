package com.example.EnterpriseRagCommunity.dto.semantic;

import io.swagger.annotations.ApiModelProperty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.Map;

@Data
public class PromptsUpdateDTO {
    @ApiModelProperty(value = "主键ID", required = true, example = "1")
    @NotNull
    private Long id;

    @ApiModelProperty(value = "名称", example = "default-answer")
    @Size(max = 96)
    private String name;

    @ApiModelProperty(value = "模板内容")
    private String template;

    @ApiModelProperty(value = "模板变量(JSON)")
    private Map<String, Object> variables;

    @ApiModelProperty(value = "版本号", example = "2")
    private Integer version; // optional update

    @ApiModelProperty(value = "是否启用", example = "true")
    private Boolean isActive;

    @ApiModelProperty(value = "创建时间(只读)", example = "2025-01-01T12:00:00", hidden = true)
    private LocalDateTime createdAt; // present but not modifiable by client
}
