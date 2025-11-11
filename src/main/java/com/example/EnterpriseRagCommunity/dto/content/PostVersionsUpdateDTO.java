package com.example.EnterpriseRagCommunity.dto.content;

import io.swagger.annotations.ApiModelProperty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import com.fasterxml.jackson.annotation.JsonIgnore;

import java.time.LocalDateTime;
import java.util.Optional;

@Data
public class PostVersionsUpdateDTO {
    @ApiModelProperty(value = "主键ID", required = true, example = "123")
    @NotNull
    private Long id;

    @ApiModelProperty(value = "帖子ID", example = "1000")
    private Optional<Long> postId = Optional.empty();

    @ApiModelProperty(value = "版本号", example = "2")
    private Optional<Integer> version = Optional.empty();

    @ApiModelProperty(value = "编辑人ID", example = "2")
    private Optional<Long> editorId = Optional.empty();

    @ApiModelProperty(value = "标题", example = "修订后的标题")
    private Optional<String> title = Optional.empty();

    @ApiModelProperty(value = "内容", example = "修订后的内容")
    private Optional<String> content = Optional.empty();

    @ApiModelProperty(value = "编辑原因", example = "修复错别字")
    private Optional<String> reason = Optional.empty();

    @ApiModelProperty(value = "创建时间（不可修改）", example = "2025-01-01T12:00:00")
    @JsonIgnore
    private Optional<LocalDateTime> createdAt = Optional.empty();
}

