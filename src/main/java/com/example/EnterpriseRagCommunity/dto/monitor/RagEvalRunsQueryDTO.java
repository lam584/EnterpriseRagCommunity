package com.example.EnterpriseRagCommunity.dto.monitor;

import io.swagger.annotations.ApiModelProperty;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;
import java.util.Map;
@EqualsAndHashCode(callSuper = true)
@Data
public class RagEvalRunsQueryDTO extends com.example.EnterpriseRagCommunity.dto.access.PageRequestDTO {
    @ApiModelProperty(value = "主键ID")
    private Long id;

    @ApiModelProperty(value = "评测批次名称(精确匹配)")
    private String name;

    @ApiModelProperty(value = "评测配置（JSON）")
    private Map<String, Object> config;

    @ApiModelProperty(value = "是否基线")
    private Boolean isBaseline;

    @ApiModelProperty(value = "创建时间(精确匹配)")
    private LocalDateTime createdAt;

    @ApiModelProperty(value = "创建时间-起")
    private LocalDateTime createdFrom;

    @ApiModelProperty(value = "创建时间-止")
    private LocalDateTime createdTo;

    public RagEvalRunsQueryDTO() {
        this.setOrderBy("createdAt");
        this.setSort("desc");
    }
}
