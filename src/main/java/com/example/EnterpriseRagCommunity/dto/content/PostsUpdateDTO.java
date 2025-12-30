package com.example.EnterpriseRagCommunity.dto.content;

import com.example.EnterpriseRagCommunity.entity.content.enums.ContentFormat;
import io.swagger.annotations.ApiModelProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;
import java.util.Map;

@Data
public class PostsUpdateDTO {

    @NotNull
    @ApiModelProperty(value = "板块ID", required = true)
    private Long boardId;

    @NotBlank
    @Size(max = 191)
    @ApiModelProperty(value = "标题", required = true)
    private String title;

    @NotBlank
    @ApiModelProperty(value = "内容", required = true)
    private String content;

    @ApiModelProperty(value = "内容格式(默认 MARKDOWN)")
    private ContentFormat contentFormat = ContentFormat.MARKDOWN;

    @ApiModelProperty(value = "附件 fileAssetIds")
    private List<Long> attachmentIds;

    @ApiModelProperty(value = "元数据(JSON)，可存 tags 等")
    private Map<String, Object> metadata;
}

