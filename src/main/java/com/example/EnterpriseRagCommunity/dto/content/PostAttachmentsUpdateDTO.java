package com.example.EnterpriseRagCommunity.dto.content;

import io.swagger.annotations.ApiModelProperty;
import lombok.Data;
import com.fasterxml.jackson.annotation.JsonIgnore;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDateTime;
import java.util.Optional;

@Data
public class PostAttachmentsUpdateDTO {
    @ApiModelProperty(value = "主键ID", required = true)
    @NotNull
    private Long id;

    @ApiModelProperty("帖子ID")
    private Optional<Long> postId = Optional.empty();

    @ApiModelProperty("附件URL")
    private Optional<@Size(max = 512) String> url = Optional.empty();

    @ApiModelProperty("文件名")
    private Optional<@Size(max = 512) String> fileName = Optional.empty();

    @ApiModelProperty("MIME类型")
    private Optional<@Size(max = 64) String> mimeType = Optional.empty();

    @ApiModelProperty("文件大小字节")
    private Optional<Long> sizeBytes = Optional.empty();

    @ApiModelProperty("宽度")
    private Optional<Integer> width = Optional.empty();

    @ApiModelProperty("高度")
    private Optional<Integer> height = Optional.empty();

    // 审计字段定义但不可修改
    @ApiModelProperty("创建时间（不可修改）")
    @JsonIgnore
    private Optional<LocalDateTime> createdAt = Optional.empty();
}

