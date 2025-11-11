package com.example.EnterpriseRagCommunity.dto.semantic;

import io.swagger.annotations.ApiModelProperty;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

@EqualsAndHashCode(callSuper = true)
@Data
public class DocumentChunksQueryDTO extends com.example.EnterpriseRagCommunity.dto.access.PageRequestDTO {
    @ApiModelProperty(value = "分片ID")
    private Long id;

    @ApiModelProperty(value = "文档ID", example = "100")
    private Long documentId;

    @ApiModelProperty(value = "分块序号")
    private Integer chunkIndex;

    @ApiModelProperty(value = "分片文本内容(精确匹配)")
    private String contentText;

    @ApiModelProperty(value = "内容关键词(模糊)")
    private String contentLike;

    @ApiModelProperty(value = "分片Token计数")
    private Integer contentTokens;

    @ApiModelProperty(value = "Token计数-起")
    private Integer contentTokensFrom;

    @ApiModelProperty(value = "Token计数-止")
    private Integer contentTokensTo;

    @ApiModelProperty(value = "嵌入提供方", example = "openai")
    private String embeddingProvider;

    @ApiModelProperty(value = "向量维度", example = "1536")
    private Integer embeddingDim;

    @ApiModelProperty(value = "向量数据是否存在")
    private Boolean hasEmbeddingVector;

    @ApiModelProperty(value = "内容哈希")
    private String contentHash;

    @ApiModelProperty(value = "创建时间-起")
    private LocalDateTime createdFrom;

    @ApiModelProperty(value = "创建时间-止")
    private LocalDateTime createdTo;

    @ApiModelProperty(value = "创建时间(精确)")
    private LocalDateTime createdAt;
}
