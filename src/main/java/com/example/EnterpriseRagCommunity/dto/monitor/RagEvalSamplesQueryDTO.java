package com.example.EnterpriseRagCommunity.dto.monitor;

import io.swagger.annotations.ApiModelProperty;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;
import java.util.Map;
@EqualsAndHashCode(callSuper = true)
@Data
public class RagEvalSamplesQueryDTO extends com.example.EnterpriseRagCommunity.dto.access.PageRequestDTO {
    // Full table fields
    @ApiModelProperty(value = "样本ID")
    private Long id;

    @ApiModelProperty(value = "评测批次ID")
    private Long runId;

    @ApiModelProperty(value = "问题/查询全文(精确匹配)")
    private String query;

    @ApiModelProperty(value = "参考答案(精确匹配)")
    private String expectedAnswer;

    @ApiModelProperty(value = "引用参考(JSON)")
    private Map<String, Object> referencesJson;

    @ApiModelProperty(value = "创建时间")
    private LocalDateTime createdAt;

    // Auxiliary search fields
    @ApiModelProperty(value = "问题/查询 关键字(模糊)")
    private String queryLike;

    @ApiModelProperty(value = "创建时间-起")
    private LocalDateTime createdFrom;

    @ApiModelProperty(value = "创建时间-止")
    private LocalDateTime createdTo;

    @ApiModelProperty(value = "聚合维度", example = "DATE")
    private String groupBy; // NONE|HOUR|DATE 查询辅助字段, 非表字段

    public RagEvalSamplesQueryDTO() {
        this.setOrderBy("createdAt");
        this.setSort("desc");
    }
}
