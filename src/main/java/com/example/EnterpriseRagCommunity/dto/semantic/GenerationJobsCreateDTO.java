package com.example.EnterpriseRagCommunity.dto.semantic;

import com.example.EnterpriseRagCommunity.entity.semantic.enums.GenerationJobStatus;
import com.example.EnterpriseRagCommunity.entity.semantic.enums.GenerationJobType;
import com.example.EnterpriseRagCommunity.entity.semantic.enums.GenerationTargetType;
import com.fasterxml.jackson.annotation.JsonIgnore;
import io.swagger.annotations.ApiModelProperty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * Create DTO: 包含除 id 外所有列；系统生成字段使用 @JsonIgnore
 */
@Data
public class GenerationJobsCreateDTO {
    @ApiModelProperty(value = "任务类型", required = true)
    @NotNull
    private GenerationJobType jobType;

    @ApiModelProperty(value = "目标类型", required = true)
    @NotNull
    private GenerationTargetType targetType;

    @ApiModelProperty(value = "目标ID", required = true)
    @NotNull
    private Long targetId;

    @ApiModelProperty(value = "任务状态", notes = "默认 PENDING，可选传入")
    @NotNull
    private GenerationJobStatus status; // 必填 NOT NULL

    @ApiModelProperty(value = "提示词模板ID")
    private Long promptId;

    @ApiModelProperty(value = "模型名称", example = "gpt-4o-mini")
    @Size(max = 64)
    private String model;

    @ApiModelProperty(value = "任务参数(JSON)")
    private Map<String, Object> params;

    @ApiModelProperty(value = "生成结果(JSON)")
    @JsonIgnore // 创建时不由前端提供
    private Map<String, Object> resultJson;

    @ApiModelProperty(value = "输入Tokens")
    @JsonIgnore
    private Integer tokensIn;

    @ApiModelProperty(value = "输出Tokens")
    @JsonIgnore
    private Integer tokensOut;

    @ApiModelProperty(value = "成本(分)")
    @JsonIgnore
    private Integer costCents;

    @ApiModelProperty(value = "错误信息")
    @JsonIgnore
    private String errorMessage;

    @ApiModelProperty(value = "创建时间")
    @JsonIgnore
    private LocalDateTime createdAt;

    @ApiModelProperty(value = "更新时间")
    @JsonIgnore
    private LocalDateTime updatedAt;
}
