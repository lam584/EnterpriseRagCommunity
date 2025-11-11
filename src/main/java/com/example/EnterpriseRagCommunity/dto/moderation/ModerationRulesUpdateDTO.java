package com.example.EnterpriseRagCommunity.dto.moderation;

import com.example.EnterpriseRagCommunity.entity.moderation.enums.RuleType;
import com.example.EnterpriseRagCommunity.entity.moderation.enums.Severity;
import io.swagger.annotations.ApiModelProperty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.Optional;
import com.fasterxml.jackson.annotation.JsonIgnore;

@Data
public class ModerationRulesUpdateDTO {
    @NotNull
    @ApiModelProperty(value = "规则ID", required = true, example = "1")
    private Long id;

    @ApiModelProperty(value = "规则名称(可选更新)")
    private Optional<String> name = Optional.empty();

    @ApiModelProperty(value = "规则类型(可选更新)")
    private Optional<RuleType> type = Optional.empty();

    @ApiModelProperty(value = "匹配模式/提示词(JSON/正则/向量等)(可选更新)")
    private Optional<String> pattern = Optional.empty();

    @ApiModelProperty(value = "违规等级(可选更新)")
    private Optional<Severity> severity = Optional.empty();

    @ApiModelProperty(value = "是否启用(可选更新)")
    private Optional<Boolean> enabled = Optional.empty();

    @ApiModelProperty(value = "自定义元数据(JSON)(可选更新)")
    private Optional<Map<String, Object>> metadata = Optional.empty();

    @ApiModelProperty(value = "创建时间(不可修改,后端填充)")
    @JsonIgnore
    private Optional<LocalDateTime> createdAt = Optional.empty();
}
