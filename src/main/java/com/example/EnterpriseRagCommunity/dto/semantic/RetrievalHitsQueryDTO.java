package com.example.EnterpriseRagCommunity.dto.semantic;

import com.example.EnterpriseRagCommunity.entity.semantic.enums.RetrievalHitType;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;
import lombok.EqualsAndHashCode;

@EqualsAndHashCode(callSuper = true)
@Data
public class RetrievalHitsQueryDTO extends com.example.EnterpriseRagCommunity.dto.access.PageRequestDTO {
    @ApiModelProperty(value = "主键ID")
    private Long id;

    @ApiModelProperty(value = "事件ID", example = "1000")
    private Long eventId;

    @ApiModelProperty(value = "命中类型", example = "BM25")
    private RetrievalHitType hitType;

    @ApiModelProperty(value = "帖子ID", example = "100")
    private Long postId;

    @ApiModelProperty(value = "分块ID", example = "999")
    private Long chunkId;

    @ApiModelProperty(value = "得分精确匹配", example = "0.876")
    private Double score; // 单值查询补充

    @ApiModelProperty(value = "得分-起", example = "0.2")
    private Double scoreFrom;

    @ApiModelProperty(value = "得分-止", example = "1.0")
    private Double scoreTo;

    @ApiModelProperty(value = "排名精确匹配", example = "1")
    private Integer rank; // 单值查询补充

    @ApiModelProperty(value = "名次-起", example = "1")
    private Integer rankFrom;

    @ApiModelProperty(value = "名次-止", example = "50")
    private Integer rankTo;
}
