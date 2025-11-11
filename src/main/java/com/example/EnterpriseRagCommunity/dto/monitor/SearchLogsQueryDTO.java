package com.example.EnterpriseRagCommunity.dto.monitor;

import io.swagger.annotations.ApiModelProperty;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;
@EqualsAndHashCode(callSuper = true)
@Data
public class SearchLogsQueryDTO extends com.example.EnterpriseRagCommunity.dto.access.PageRequestDTO {
    @ApiModelProperty(value = "日志ID")
    private Long id;

    @ApiModelProperty(value = "用户ID")
    private Long userId;

    @ApiModelProperty(value = "搜索词(精确)")
    private String query;

    @ApiModelProperty(value = "搜索词(模糊)")
    private String queryLike;

    @ApiModelProperty(value = "耗时(毫秒)")
    private Integer latencyMs;

    @ApiModelProperty(value = "耗时范围-起(毫秒)")
    private Integer latencyMsFrom;

    @ApiModelProperty(value = "耗时范围-止(毫秒)")
    private Integer latencyMsTo;

    @ApiModelProperty(value = "结果数量")
    private Integer resultsCount;

    @ApiModelProperty(value = "结果数量范围-起")
    private Integer resultsCountFrom;

    @ApiModelProperty(value = "结果数量范围-止")
    private Integer resultsCountTo;

    @ApiModelProperty(value = "创建时间-起")
    private LocalDateTime createdFrom;

    @ApiModelProperty(value = "创建时间-止")
    private LocalDateTime createdTo;

    public SearchLogsQueryDTO() {
        this.setOrderBy("createdAt");
        this.setSort("desc");
    }
}
