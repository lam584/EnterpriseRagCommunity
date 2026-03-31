package com.example.EnterpriseRagCommunity.dto.semantic;

import com.example.EnterpriseRagCommunity.entity.semantic.enums.DocumentSourceType;
import com.fasterxml.jackson.annotation.JsonIgnore;
import io.swagger.annotations.ApiModelProperty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.Map;

@Data
public class DocumentsUpdateDTO {
    @ApiModelProperty(value = "文档ID", required = true, example = "100")
    @NotNull
    private Long id;

    @ApiModelProperty(value = "租户ID")
    private Long tenantId;

    @ApiModelProperty(value = "来源类型，枚举：POST/COMMENT/FILE/URL")
    private DocumentSourceType sourceType;

    @ApiModelProperty(value = "来源对象ID")
    private Long sourceId;

    @ApiModelProperty(value = "标题")
    @Size(max = 191)
    private String title;

    @ApiModelProperty(value = "语言，例如 zh, en")
    @Size(max = 16)
    private String language;

    @ApiModelProperty(value = "版本号")
    private Integer version;

    @ApiModelProperty(value = "是否有效")
    private Boolean isActive;

    @ApiModelProperty(value = "元数据(JSON)")
    private Map<String, Object> metadata;

    @ApiModelProperty(value = "创建时间，审计字段，禁止更新")
    @JsonIgnore
    private LocalDateTime createdAt;
}
