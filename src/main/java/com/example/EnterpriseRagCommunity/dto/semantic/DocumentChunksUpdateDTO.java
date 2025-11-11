package com.example.EnterpriseRagCommunity.dto.semantic;

import com.fasterxml.jackson.annotation.JsonIgnore;
import io.swagger.annotations.ApiModelProperty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.Optional;

@Data
public class DocumentChunksUpdateDTO {
    @ApiModelProperty(value = "分片ID", required = true, example = "1")
    @NotNull
    private Long id;

    @ApiModelProperty(value = "文档ID")
    private Optional<Long> documentId = Optional.empty();

    @ApiModelProperty(value = "分片序号(从0开始)")
    private Optional<Integer> chunkIndex = Optional.empty();

    @ApiModelProperty(value = "分片文本内容")
    private Optional<String> contentText = Optional.empty();

    @ApiModelProperty(value = "分片Token计数")
    private Optional<Integer> contentTokens = Optional.empty();

    @ApiModelProperty(value = "嵌入提供方/模型")
    @Size(max = 64)
    private Optional<String> embeddingProvider = Optional.empty();

    @ApiModelProperty(value = "嵌入维度")
    private Optional<Integer> embeddingDim = Optional.empty();

    @ApiModelProperty(value = "向量数据(BLOB)")
    private Optional<byte[]> embeddingVector = Optional.empty();

    @ApiModelProperty(value = "内容哈希(去重)")
    @Size(min = 64, max = 64)
    private Optional<String> contentHash = Optional.empty();

    @ApiModelProperty(value = "创建时间，审计字段，禁止更新")
    @JsonIgnore
    private Optional<LocalDateTime> createdAt = Optional.empty();
}

