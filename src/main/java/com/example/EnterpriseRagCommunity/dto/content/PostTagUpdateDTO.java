package com.example.EnterpriseRagCommunity.dto.content;

import com.example.EnterpriseRagCommunity.entity.content.enums.PostTagSource;
import com.fasterxml.jackson.annotation.JsonIgnore;
import io.swagger.annotations.ApiModelProperty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class PostTagUpdateDTO {
    @NotNull
    @ApiModelProperty(value = "帖子ID", required = true, example = "1000")
    private Long postId;

    @NotNull
    @ApiModelProperty(value = "标签ID", required = true, example = "10")
    private Long tagId;

    @NotNull
    @ApiModelProperty(value = "来源", required = true, example = "MANUAL")
    private PostTagSource source;

    @ApiModelProperty(value = "置信度")
    private BigDecimal confidence;

    @JsonIgnore
    @ApiModelProperty(value = "创建时间，不可修改")
    private LocalDateTime createdAt;
}

