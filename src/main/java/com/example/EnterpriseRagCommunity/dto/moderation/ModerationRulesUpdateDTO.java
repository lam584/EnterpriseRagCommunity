package com.example.EnterpriseRagCommunity.dto.moderation;

import com.example.EnterpriseRagCommunity.entity.moderation.enums.RuleType;
import com.example.EnterpriseRagCommunity.entity.moderation.enums.Severity;
import io.swagger.annotations.ApiModelProperty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.Map;
import com.fasterxml.jackson.annotation.JsonIgnore;

@Data
public class ModerationRulesUpdateDTO {
    @NotNull
    @ApiModelProperty(value = "规则ID", required = true, example = "1")
    private Long id;

    @ApiModelProperty(value = "规则名称(可选更新)")
    private String name;
    @JsonIgnore
    private boolean hasName;

    @ApiModelProperty(value = "规则类型(可选更新)")
    private RuleType type;
    @JsonIgnore
    private boolean hasType;

    @ApiModelProperty(value = "匹配模式/提示词(JSON/正则/向量等)(可选更新)")
    private String pattern;
    @JsonIgnore
    private boolean hasPattern;

    @ApiModelProperty(value = "违规等级(可选更新)")
    private Severity severity;
    @JsonIgnore
    private boolean hasSeverity;

    @ApiModelProperty(value = "是否启用(可选更新)")
    private Boolean enabled;
    @JsonIgnore
    private boolean hasEnabled;

    @ApiModelProperty(value = "自定义元数据(JSON)(可选更新)")
    private Map<String, Object> metadata;
    @JsonIgnore
    private boolean hasMetadata;

    @ApiModelProperty(value = "创建时间(不可修改,后端填充)")
    @JsonIgnore
    private LocalDateTime createdAt;

    public void setName(String name) {
        this.name = name;
        this.hasName = true;
    }

    public void setType(RuleType type) {
        this.type = type;
        this.hasType = true;
    }

    public void setPattern(String pattern) {
        this.pattern = pattern;
        this.hasPattern = true;
    }

    public void setSeverity(Severity severity) {
        this.severity = severity;
        this.hasSeverity = true;
    }

    public void setEnabled(Boolean enabled) {
        this.enabled = enabled;
        this.hasEnabled = true;
    }

    public void setMetadata(Map<String, Object> metadata) {
        this.metadata = metadata;
        this.hasMetadata = true;
    }
}
