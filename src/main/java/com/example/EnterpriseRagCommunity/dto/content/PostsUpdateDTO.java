package com.example.EnterpriseRagCommunity.dto.content;

import com.example.EnterpriseRagCommunity.entity.content.enums.ContentFormat;
import com.example.EnterpriseRagCommunity.entity.content.enums.PostStatus;
import com.fasterxml.jackson.annotation.JsonIgnore;
import io.swagger.annotations.ApiModelProperty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.Optional;

@Data
public class PostsUpdateDTO {
    @NotNull
    @ApiModelProperty(value = "帖子ID", required = true, example = "1000")
    private Long id;

    @ApiModelProperty(value = "租户ID，不可变，保留仅供一致性", example = "1")
    @JsonIgnore
    private Optional<Long> tenantId = Optional.empty();

    @ApiModelProperty(value = "板块ID，不可变，保留仅供一致性", example = "10")
    @JsonIgnore
    private Optional<Long> boardId = Optional.empty();

    @ApiModelProperty(value = "作者ID，不可变，保留仅供一致性", example = "200")
    @JsonIgnore
    private Optional<Long> authorId = Optional.empty();

    @Size(max = 191)
    @ApiModelProperty(value = "标题", example = "Site maintenance notice")
    private Optional<String> title = Optional.empty();

    @ApiModelProperty(value = "内容")
    private Optional<String> content = Optional.empty();

    @ApiModelProperty(value = "内容格式", example = "MARKDOWN")
    private Optional<ContentFormat> contentFormat = Optional.empty();

    @ApiModelProperty(value = "帖子状态", example = "PUBLISHED")
    private Optional<PostStatus> status = Optional.empty();

    @ApiModelProperty(value = "发布时间", example = "2025-01-01T00:00:00")
    private Optional<LocalDateTime> publishedAt = Optional.empty();

    @ApiModelProperty(value = "是否删除(软删除标记)")
    private Optional<Boolean> isDeleted = Optional.empty();

    @ApiModelProperty(value = "自定义元数据(JSON)")
    private Optional<Map<String, Object>> metadata = Optional.empty();

    @JsonIgnore
    @ApiModelProperty(value = "创建时间，不可修改")
    private Optional<LocalDateTime> createdAt = Optional.empty();

    @JsonIgnore
    @ApiModelProperty(value = "更新时间，系统生成")
    private Optional<LocalDateTime> updatedAt = Optional.empty();
}
