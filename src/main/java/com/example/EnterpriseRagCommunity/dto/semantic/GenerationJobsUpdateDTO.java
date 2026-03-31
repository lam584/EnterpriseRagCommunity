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
 * Update DTO: id 必填，其余字段 Optional 支持部分更新；审计不可修改字段 @JsonIgnore
 */
@Data
public class GenerationJobsUpdateDTO {
    @ApiModelProperty(value = "主键ID", required = true)
    @NotNull
    private Long id;

    @ApiModelProperty(value = "任务类型")
    private GenerationJobType jobType;

    @ApiModelProperty(value = "目标类型")
    private GenerationTargetType targetType;

    @ApiModelProperty(value = "目标ID")
    private Long targetId;

    @ApiModelProperty(value = "任务状态")
    private GenerationJobStatus status;

    @ApiModelProperty(value = "提示词模板ID")
    private Long promptId;

    @ApiModelProperty(value = "模型名称")
    private @Size(max = 64) String model;

    @ApiModelProperty(value = "任务参数(JSON)")
    private Map<String, Object> params;

    @ApiModelProperty(value = "生成结果(JSON)")
    private Map<String, Object> resultJson;

    @ApiModelProperty(value = "输入Tokens")
    private Integer tokensIn;

    @ApiModelProperty(value = "输出Tokens")
    private Integer tokensOut;

    @ApiModelProperty(value = "成本(分)")
    private Integer costCents;

    @ApiModelProperty(value = "错误信息")
    private String errorMessage;

    @ApiModelProperty(value = "创建时间(不可修改)")
    @JsonIgnore
    private LocalDateTime createdAt;

    @ApiModelProperty(value = "更新时间(系统生成)")
    @JsonIgnore
    private LocalDateTime updatedAt;
}
