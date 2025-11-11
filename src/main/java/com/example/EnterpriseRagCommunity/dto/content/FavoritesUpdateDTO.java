package com.example.EnterpriseRagCommunity.dto.content;

import com.fasterxml.jackson.annotation.JsonIgnore;
import io.swagger.annotations.ApiModelProperty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.Optional;

@Data
public class FavoritesUpdateDTO {
    @ApiModelProperty(value = "主键ID", required = true, example = "1")
    @NotNull
    private Long id;

    @ApiModelProperty(value = "用户ID", example = "200")
    private Optional<Long> userId = Optional.empty();

    @ApiModelProperty(value = "帖子ID", example = "1000")
    private Optional<Long> postId = Optional.empty();

    @ApiModelProperty(value = "收藏时间（不允许修改）")
    @JsonIgnore
    private Optional<LocalDateTime> createdAt = Optional.empty();
}

