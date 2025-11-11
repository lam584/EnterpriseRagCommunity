package com.example.EnterpriseRagCommunity.dto.semantic;

import io.swagger.annotations.ApiModelProperty;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;
@EqualsAndHashCode(callSuper = true)
@Data
public class RetrievalEventsQueryDTO extends com.example.EnterpriseRagCommunity.dto.access.PageRequestDTO {
    @ApiModelProperty(value = "主键ID")
    private Long id;

    @ApiModelProperty(value = "用户ID", example = "200")
    private Long userId;

    @ApiModelProperty(value = "查询文本(精确匹配)")
    private String queryText;

    @ApiModelProperty(value = "查询文本(模糊)")
    private String queryTextLike;

    @ApiModelProperty(value = "BM25 K")
    private Integer bm25K;

    @ApiModelProperty(value = "向量检索K")
    private Integer vecK;

    @ApiModelProperty(value = "混合检索K")
    private Integer hybridK;

    @ApiModelProperty(value = "重排模型", example = "bge-reranker")
    private String rerankModel;

    @ApiModelProperty(value = "重排K")
    private Integer rerankK;

    @ApiModelProperty(value = "创建时间(单值查询)")
    private LocalDateTime createdAt;

    @ApiModelProperty(value = "创建时间-起")
    private LocalDateTime createdFrom;

    @ApiModelProperty(value = "创建时间-止")
    private LocalDateTime createdTo;
}
