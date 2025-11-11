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
import java.util.Optional;

/**
 * Update DTO: id 必填，其余字段 Optional 支持部分更新；审计不可修改字段 @JsonIgnore
 */
@Data
public class GenerationJobsUpdateDTO {
    @ApiModelProperty(value = "主键ID", required = true)
    @NotNull
    private Long id;

    @ApiModelProperty(value = "任务类型")
    private Optional<GenerationJobType> jobType = Optional.empty();

    @ApiModelProperty(value = "目标类型")
    private Optional<GenerationTargetType> targetType = Optional.empty();

    @ApiModelProperty(value = "目标ID")
    private Optional<Long> targetId = Optional.empty();

    @ApiModelProperty(value = "任务状态")
    private Optional<GenerationJobStatus> status = Optional.empty();

    @ApiModelProperty(value = "提示词模板ID")
    private Optional<Long> promptId = Optional.empty();

    @ApiModelProperty(value = "模型名称")
    private Optional<@Size(max = 64) String> model = Optional.empty();

    @ApiModelProperty(value = "任务参数(JSON)")
    private Optional<Map<String, Object>> params = Optional.empty();

    @ApiModelProperty(value = "生成结果(JSON)")
    private Optional<Map<String, Object>> resultJson = Optional.empty();

    @ApiModelProperty(value = "输入Tokens")
    private Optional<Integer> tokensIn = Optional.empty();

    @ApiModelProperty(value = "输出Tokens")
    private Optional<Integer> tokensOut = Optional.empty();

    @ApiModelProperty(value = "成本(分)")
    private Optional<Integer> costCents = Optional.empty();

    @ApiModelProperty(value = "错误信息")
    private Optional<String> errorMessage = Optional.empty();

    @ApiModelProperty(value = "创建时间(不可修改)")
    @JsonIgnore
    private LocalDateTime createdAt;

    @ApiModelProperty(value = "更新时间(系统生成)")
    @JsonIgnore
    private LocalDateTime updatedAt;
}
