package com.example.EnterpriseRagCommunity.dto.access;

import io.swagger.annotations.ApiModelProperty;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.Data;

@Data
public class PageRequestDTO {
    @ApiModelProperty("页码，从1开始")
    @Min(1)
    private Integer pageNum = 1;

    @ApiModelProperty("每页大小，默认20，最大20000")
    @Min(1)
    @Max(20000)
    private Integer pageSize = 20;

    @ApiModelProperty("排序字段，如 createdAt")
    private String orderBy;

    @ApiModelProperty("排序方向，asc/desc")
    private String sort;
}
