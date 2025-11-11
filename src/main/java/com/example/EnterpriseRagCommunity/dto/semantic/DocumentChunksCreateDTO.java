package com.example.EnterpriseRagCommunity.dto.semantic;

import com.fasterxml.jackson.annotation.JsonIgnore;
import io.swagger.annotations.ApiModelProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class DocumentChunksCreateDTO {
    @ApiModelProperty(value = "文档ID", required = true, example = "100")
    @NotNull
    private Long documentId;

    @ApiModelProperty(value = "分片序号(从0开始)", required = true)
    @NotNull
    private Integer chunkIndex;

    @ApiModelProperty(value = "分片文本内容", required = true)
    @NotBlank
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

    @ApiModelProperty(value = "内容哈希(去重)", required = true)
    @NotBlank
    @Size(min = 64, max = 64)
    private String contentHash;

    @ApiModelProperty(value = "创建时间，审计字段，DB默认填充")
    @JsonIgnore
    private LocalDateTime createdAt;
}

