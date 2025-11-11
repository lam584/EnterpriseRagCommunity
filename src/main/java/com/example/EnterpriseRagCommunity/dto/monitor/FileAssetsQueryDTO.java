package com.example.EnterpriseRagCommunity.dto.monitor;

import io.swagger.annotations.ApiModelProperty;
import lombok.Data;
import lombok.EqualsAndHashCode;

@EqualsAndHashCode(callSuper = true)
@Data
public class FileAssetsQueryDTO extends com.example.EnterpriseRagCommunity.dto.access.PageRequestDTO {
    @ApiModelProperty(value = "所有者用户ID")
    private Long ownerUserId;

    @ApiModelProperty(value = "状态", example = "READY")
    private String status;

    @ApiModelProperty(value = "文件名(模糊)")
    private String filenameLike;

    public FileAssetsQueryDTO() {
        this.setOrderBy("createdAt");
        this.setSort("desc");
    }
}

