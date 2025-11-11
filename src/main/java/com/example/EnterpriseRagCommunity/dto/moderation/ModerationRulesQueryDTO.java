package com.example.EnterpriseRagCommunity.dto.moderation;

import com.example.EnterpriseRagCommunity.dto.access.PageRequestDTO;
import com.example.EnterpriseRagCommunity.entity.moderation.enums.RuleType;
import com.example.EnterpriseRagCommunity.entity.moderation.enums.Severity;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;
import java.util.Map;

@EqualsAndHashCode(callSuper = true)
@Data
public class ModerationRulesQueryDTO extends PageRequestDTO {
    @ApiModelProperty(value = "规则ID")
    private Long id;

    @ApiModelProperty(value = "规则名(模糊)", example = "Profanity")
    private String name;

    @ApiModelProperty(value = "规则类型", example = "REGEX")
    private RuleType type;

    @ApiModelProperty(value = "严重程度", example = "HIGH")
    private Severity severity;

    @ApiModelProperty(value = "是否启用", example = "true")
    private Boolean enabled;

    @ApiModelProperty(value = "匹配表达式/模式")
    private String pattern;

    @ApiModelProperty(value = "自定义元数据(JSON)")
    private Map<String, Object> metadata;

    @ApiModelProperty(value = "创建时间")
    private LocalDateTime createdAt;

    @ApiModelProperty(value = "创建时间起")
    private LocalDateTime createdAfter;

    @ApiModelProperty(value = "创建时间止")
    private LocalDateTime createdBefore;
}
