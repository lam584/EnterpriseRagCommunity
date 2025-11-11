package com.example.EnterpriseRagCommunity.dto.moderation;

import com.example.EnterpriseRagCommunity.dto.access.PageRequestDTO;
import com.example.EnterpriseRagCommunity.entity.moderation.enums.ContentType;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;
@EqualsAndHashCode(callSuper = true)
@Data
public class ModerationSimilarHitsQueryDTO extends PageRequestDTO {
    // 主键查询（可选）
    @ApiModelProperty(value = "主键ID(可选)")
    private Long id;

    @ApiModelProperty(value = "内容类型", example = "POST")
    private ContentType contentType;

    @ApiModelProperty(value = "内容ID", example = "123")
    private Long contentId;

    @ApiModelProperty(value = "相似样本ID/参考ID(可选)", example = "456")
    private Long candidateId;

    // 单值距离查询（可选）
    @ApiModelProperty(value = "相似度距离(精确匹配, 可选)", example = "0.42")
    private Double distance;

    // 距离范围查询（保留）
    @ApiModelProperty(value = "相似度距离下限(范围查询, 可选)", example = "0.10")
    private Double minDistance;

    @ApiModelProperty(value = "相似度距离上限(范围查询, 可选)", example = "0.80")
    private Double maxDistance;

    // 单值阈值查询（可选）
    @ApiModelProperty(value = "相似度阈值(可选)", example = "0.50")
    private Double threshold;

    // 匹配时间单值 + 范围查询
    @ApiModelProperty(value = "匹配时间(精确查询, 可选)", example = "2025-01-01T12:00:00")
    private LocalDateTime matchedAt;

    @ApiModelProperty(value = "匹配时间起(范围查询, 可选)", example = "2025-01-01T00:00:00")
    private LocalDateTime matchedFrom;

    @ApiModelProperty(value = "匹配时间止(范围查询, 可选)", example = "2025-12-31T23:59:59")
    private LocalDateTime matchedTo;

    // 移除非本表字段: stage, export
}
