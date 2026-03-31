package com.example.EnterpriseRagCommunity.dto.semantic;

import com.fasterxml.jackson.annotation.JsonIgnore;
import io.swagger.annotations.ApiModelProperty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class DocumentChunksUpdateDTO {
    @ApiModelProperty(value = "分片ID", required = true, example = "1")
    @NotNull
    private Long id;

    @ApiModelProperty(value = "文档ID")
    private Long documentId;

    @ApiModelProperty(value = "分片序号(从0开始)")
    private Integer chunkIndex;

    @ApiModelProperty(value = "分片文本内容")
    private String contentText;

    @ApiModelProperty(value = "分片Token计数")
    private Integer contentTokens;

    @ApiModelProperty(value = "嵌入提供方/模型")
    @Size(max = 64)
    private String embeddingProvider;

    @ApiModelProperty(value = "嵌入维度")
    private Integer embeddingDim;

    @ApiModelProperty(value = "向量数据(BLOB)")
    private byte[] embeddingVector;

    @ApiModelProperty(value = "内容哈希(去重)")
    @Size(min = 64, max = 64)
    private String contentHash;

    @ApiModelProperty(value = "创建时间，审计字段，禁止更新")
    @JsonIgnore
    private LocalDateTime createdAt;
}

