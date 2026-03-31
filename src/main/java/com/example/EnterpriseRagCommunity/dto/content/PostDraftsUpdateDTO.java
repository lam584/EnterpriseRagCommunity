package com.example.EnterpriseRagCommunity.dto.content;

import com.example.EnterpriseRagCommunity.entity.content.enums.ContentFormat;
import io.swagger.annotations.ApiModelProperty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.Map;

@Data
public class PostDraftsUpdateDTO {
    @NotNull
    @ApiModelProperty(value = "板块ID", required = true)
    private Long boardId;

    @Size(max = 191)
    @ApiModelProperty(value = "标题(可选，草稿允许为空)")
    private String title;

    @ApiModelProperty(value = "内容(可选，草稿允许为空)")
    private String content;

    @NotNull
    @ApiModelProperty(value = "内容格式", required = true)
    private ContentFormat contentFormat;

    @ApiModelProperty(value = "元数据(JSON)，可存 tags/attachmentIds")
    private Map<String, Object> metadata;
}

