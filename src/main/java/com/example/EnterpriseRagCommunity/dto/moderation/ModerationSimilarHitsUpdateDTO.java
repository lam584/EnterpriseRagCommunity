package com.example.EnterpriseRagCommunity.dto.moderation;

import com.example.EnterpriseRagCommunity.entity.moderation.enums.ContentType;
import io.swagger.annotations.ApiModelProperty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.DecimalMin;
import lombok.Data;
import com.fasterxml.jackson.annotation.JsonIgnore;

import java.time.LocalDateTime;

@Data
public class ModerationSimilarHitsUpdateDTO {
    @ApiModelProperty(value = "主键ID", required = true)
    @NotNull
    private Long id;

    @ApiModelProperty(value = "内容类型", example = "POST")
    private ContentType contentType;

    @ApiModelProperty(value = "内容ID", example = "456")
    private Long contentId;

    @ApiModelProperty(value = "相似样本/参考ID")
    private Long candidateId;

    @ApiModelProperty(value = "相似距离", example = "0.312")
    private @DecimalMin("0.0") Double distance;

    @ApiModelProperty(value = "距离阈值", example = "0.500")
    private @DecimalMin("0.0") Double threshold;

    @ApiModelProperty(value = "命中时间(只读)")
    @JsonIgnore
    private LocalDateTime matchedAt;
}

/**
 * Global config for similarity (VEC) checks.
 */
@lombok.Data
class ModerationSimilarityConfigDTO {
    private Long id;
    private Boolean enabled;
    private Integer version;
    private String updatedAt;
    private String updatedBy;
}
