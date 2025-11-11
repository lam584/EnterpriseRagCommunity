package com.example.EnterpriseRagCommunity.dto.rag;

import io.swagger.annotations.ApiModelProperty;
import lombok.Data;
import lombok.EqualsAndHashCode;

@EqualsAndHashCode(callSuper = true)
@Data
public class AnswerCitationsQueryDTO extends com.example.EnterpriseRagCommunity.dto.access.PageRequestDTO {
    @ApiModelProperty(value = "主键ID")
    private Long id;

    @ApiModelProperty(value = "答案消息ID")
    private Long messageId;

    @ApiModelProperty(value = "引用文档ID")
    private Long documentId;

    @ApiModelProperty(value = "引用分片ID")
    private Long chunkId;

    @ApiModelProperty(value = "引用片段文本（包含匹配）")
    private String quoteText;

    @ApiModelProperty(value = "来源URL")
    private String sourceUrl;

    @ApiModelProperty(value = "片段起始偏移")
    private Integer startOffset;

    @ApiModelProperty(value = "片段结束偏移")
    private Integer endOffset;

    @ApiModelProperty(value = "相关性得分")
    private Double score;

    // 范围查询支持
    @ApiModelProperty(value = "起始偏移（下界）")
    private Integer startOffsetFrom;

    @ApiModelProperty(value = "起始偏移（上界）")
    private Integer startOffsetTo;

    @ApiModelProperty(value = "结束偏移（下界）")
    private Integer endOffsetFrom;

    @ApiModelProperty(value = "结束偏移（上界）")
    private Integer endOffsetTo;

    @ApiModelProperty(value = "相关性得分（下界）")
    private Double scoreFrom;

    @ApiModelProperty(value = "相关性得分（上界）")
    private Double scoreTo;
}
