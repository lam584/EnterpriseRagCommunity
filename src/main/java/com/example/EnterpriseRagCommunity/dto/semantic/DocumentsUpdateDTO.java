package com.example.EnterpriseRagCommunity.dto.semantic;

import com.example.EnterpriseRagCommunity.entity.semantic.enums.DocumentSourceType;
import com.fasterxml.jackson.annotation.JsonIgnore;
import io.swagger.annotations.ApiModelProperty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.Optional;

@Data
public class DocumentsUpdateDTO {
    @ApiModelProperty(value = "文档ID", required = true, example = "100")
    @NotNull
    private Long id;

    @ApiModelProperty(value = "租户ID")
    private Optional<Long> tenantId = Optional.empty();

    @ApiModelProperty(value = "来源类型，枚举：POST/COMMENT/FILE/URL")
    private Optional<DocumentSourceType> sourceType = Optional.empty();

    @ApiModelProperty(value = "来源对象ID")
    private Optional<Long> sourceId = Optional.empty();

    @ApiModelProperty(value = "标题")
    @Size(max = 191)
    private Optional<String> title = Optional.empty();

    @ApiModelProperty(value = "语言，例如 zh, en")
    @Size(max = 16)
    private Optional<String> language = Optional.empty();

    @ApiModelProperty(value = "版本号")
    private Optional<Integer> version = Optional.empty();

    @ApiModelProperty(value = "是否有效")
    private Optional<Boolean> isActive = Optional.empty();

    @ApiModelProperty(value = "元数据(JSON)")
    private Optional<Map<String, Object>> metadata = Optional.empty();

    @ApiModelProperty(value = "创建时间，审计字段，禁止更新")
    @JsonIgnore
    private Optional<LocalDateTime> createdAt = Optional.empty();
}
