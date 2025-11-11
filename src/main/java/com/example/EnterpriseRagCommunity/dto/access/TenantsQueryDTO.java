package com.example.EnterpriseRagCommunity.dto.access;

import io.swagger.annotations.ApiModelProperty;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;
@EqualsAndHashCode(callSuper = true)
@Data
public class TenantsQueryDTO extends PageRequestDTO {
    @ApiModelProperty("租户编码（模糊匹配）")
    private String code;

    @ApiModelProperty("租户名称（模糊匹配）")
    private String name;

    @ApiModelProperty("创建时间-起")
    private LocalDateTime createdAfter;

    @ApiModelProperty("创建时间-止")
    private LocalDateTime createdBefore;

    @ApiModelProperty("更新时间-起")
    private LocalDateTime updatedFrom;

    @ApiModelProperty("更新时间-止")
    private LocalDateTime updatedTo;
}
