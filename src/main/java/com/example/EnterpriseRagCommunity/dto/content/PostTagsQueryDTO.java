package com.example.EnterpriseRagCommunity.dto.content;

import com.example.EnterpriseRagCommunity.entity.content.enums.PostTagSource;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class PostTagsQueryDTO {
    @ApiModelProperty(value = "帖子ID", example = "1000")
    private Long postId;

    @ApiModelProperty(value = "标签ID", example = "10")
    private Long tagId;

    @ApiModelProperty(value = "来源", example = "LLM")
    private PostTagSource source;

    @ApiModelProperty(value = "置信度", example = "0.8500")
    private BigDecimal confidence;

    @ApiModelProperty(value = "置信度下限", example = "0.5")
    private BigDecimal confidenceFrom;

    @ApiModelProperty(value = "置信度上限", example = "1.0")
    private BigDecimal confidenceTo;

    @ApiModelProperty(value = "创建时间", example = "2025-11-09T00:00:00")
    private LocalDateTime createdAt;

    @ApiModelProperty(value = "创建时间起", example = "2025-01-01T00:00:00")
    private LocalDateTime createdAfter;

    @ApiModelProperty(value = "创建时间止", example = "2025-12-31T23:59:59")
    private LocalDateTime createdBefore;

    @ApiModelProperty(value = "页码，从1开始", example = "1")
    private Integer page = 1;

    @ApiModelProperty(value = "每页大小", example = "20")
    private Integer pageSize = 20;

    @ApiModelProperty(value = "排序字段", example = "createdAt")
    private String sortBy;

    @ApiModelProperty(value = "排序方向 asc/desc", example = "desc")
    private String sortOrder = "desc";
}
