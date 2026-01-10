package com.example.EnterpriseRagCommunity.dto.moderation;

import com.example.EnterpriseRagCommunity.entity.moderation.enums.RuleType;
import com.example.EnterpriseRagCommunity.entity.moderation.enums.Severity;
import io.swagger.annotations.ApiModelProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.Map;
import com.fasterxml.jackson.annotation.JsonIgnore;

@Data
public class ModerationRulesCreateDTO {
    @NotBlank
    @Size(max = 96)
    @ApiModelProperty(value = "规则名称", required = true, example = "Profanity filter")
    private String name;

    @NotNull
    @ApiModelProperty(value = "规则类型", required = true, example = "REGEX")
    private RuleType type;

    @NotBlank
    @ApiModelProperty(value = "匹配模式/提示词(JSON/正则/向量等)", required = true)
    private String pattern;

    @NotNull
    @ApiModelProperty(value = "违规等级", required = true, example = "HIGH")
    private Severity severity;

    @NotNull
    @ApiModelProperty(value = "是否启用", required = true, example = "true")
    private Boolean enabled = true;

    @ApiModelProperty(value = "自定义元数据(JSON)")
    private Map<String, Object> metadata;

    // createdAt 由后端生成，不允许客户端传入；因此不应对创建请求强制 @NotNull 校验
    @ApiModelProperty(value = "创建时间(后端填充)", required = false, example = "2025-11-09T10:15:30")
    @JsonIgnore // 不由前端传递，DB 默认值填充
    private LocalDateTime createdAt;
}
