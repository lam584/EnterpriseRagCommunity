package com.example.EnterpriseRagCommunity.dto.rag;

import io.swagger.annotations.ApiModelProperty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class AnswerCitationsCreateDTO {
    @ApiModelProperty(value = "答案消息ID", required = true)
    @NotNull
    private Long messageId;

    @ApiModelProperty(value = "引用文档ID")
    private Long documentId;

    @ApiModelProperty(value = "引用分片ID")
    private Long chunkId;

    @ApiModelProperty(value = "引用片段文本")
    private String quoteText;

    @ApiModelProperty(value = "来源URL")
    @Size(max = 512)
    private String sourceUrl;

    @ApiModelProperty(value = "片段起始偏移")
    private Integer startOffset;

    @ApiModelProperty(value = "片段结束偏移")
    private Integer endOffset;

    @ApiModelProperty(value = "相关性得分")
    private Double score;
}

