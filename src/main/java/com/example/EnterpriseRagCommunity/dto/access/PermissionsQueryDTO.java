package com.example.EnterpriseRagCommunity.dto.access;

import io.swagger.annotations.ApiModelProperty;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = false)
public class PermissionsQueryDTO extends PageRequestDTO {
    @ApiModelProperty("主键ID（精确匹配）")
    private Long id;

    @ApiModelProperty("资源标识（模糊匹配）")
    private String resource;

    @ApiModelProperty("操作标识（模糊匹配）")
    private String action;

    @ApiModelProperty("描述（模糊匹配）")
    private String description;
}
