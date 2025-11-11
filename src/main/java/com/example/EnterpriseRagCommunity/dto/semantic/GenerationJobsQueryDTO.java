package com.example.EnterpriseRagCommunity.dto.semantic;

import com.example.EnterpriseRagCommunity.entity.semantic.enums.GenerationJobStatus;
import com.example.EnterpriseRagCommunity.entity.semantic.enums.GenerationJobType;
import com.example.EnterpriseRagCommunity.entity.semantic.enums.GenerationTargetType;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;
import java.util.Map;
@EqualsAndHashCode(callSuper = true)
@Data
public class GenerationJobsQueryDTO extends com.example.EnterpriseRagCommunity.dto.access.PageRequestDTO {
    @ApiModelProperty(value = "主键ID")
    private Long id;

    @ApiModelProperty(value = "任务类型", example = "TITLE")
    private GenerationJobType jobType;

    @ApiModelProperty(value = "任务状态", example = "PENDING")
    private GenerationJobStatus status;

    @ApiModelProperty(value = "目标类型", example = "POST")
    private GenerationTargetType targetType;

    @ApiModelProperty(value = "目标ID", example = "100")
    private Long targetId;

    @ApiModelProperty(value = "模型名称", example = "gpt-4o-mini")
    private String model;

    @ApiModelProperty(value = "提示词模板ID", example = "1")
    private Long promptId;

    @ApiModelProperty(value = "任务参数JSON")
    private Map<String, Object> params;

    @ApiModelProperty(value = "结果JSON")
    private Map<String, Object> resultJson;

    @ApiModelProperty(value = "输入Tokens")
    private Integer tokensIn;

    @ApiModelProperty(value = "输出Tokens")
    private Integer tokensOut;

    @ApiModelProperty(value = "成本(分)")
    private Integer costCents;

    @ApiModelProperty(value = "错误信息")
    private String errorMessage;

    @ApiModelProperty(value = "输入Tokens-起")
    private Integer tokensInFrom;

    @ApiModelProperty(value = "输入Tokens-止")
    private Integer tokensInTo;

    @ApiModelProperty(value = "输出Tokens-起")
    private Integer tokensOutFrom;

    @ApiModelProperty(value = "输出Tokens-止")
    private Integer tokensOutTo;

    @ApiModelProperty(value = "成本-起")
    private Integer costCentsFrom;

    @ApiModelProperty(value = "成本-止")
    private Integer costCentsTo;

    @ApiModelProperty(value = "创建时间-单值")
    private LocalDateTime createdAt;

    @ApiModelProperty(value = "创建时间-起")
    private LocalDateTime createdFrom;

    @ApiModelProperty(value = "创建时间-止")
    private LocalDateTime createdTo;

    @ApiModelProperty(value = "更新时间-单值")
    private LocalDateTime updatedAt;

    @ApiModelProperty(value = "更新时间-起")
    private LocalDateTime updatedFrom;

    @ApiModelProperty(value = "更新时间-止")
    private LocalDateTime updatedTo;
}
