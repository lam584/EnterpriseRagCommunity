package com.example.EnterpriseRagCommunity.dto.content;

import io.swagger.annotations.ApiModelProperty;
import lombok.Data;
import com.fasterxml.jackson.annotation.JsonIgnore;

import jakarta.validation.constraints.*;
import java.time.LocalDateTime;

@Data
public class PostAttachmentsCreateDTO {
    @ApiModelProperty(value = "帖子ID", required = true)
    @NotNull
    private Long postId;

    @ApiModelProperty(value = "附件URL", required = true)
    @NotBlank
    @Size(max = 512)
    private String url;

    @ApiModelProperty(value = "文件名", required = true)
    @NotBlank
    @Size(max = 191)
    private String fileName;

    @ApiModelProperty(value = "MIME类型", required = true)
    @NotBlank
    @Size(max = 64)
    private String mimeType;

    @ApiModelProperty(value = "文件大小字节", required = true)
    @NotNull
    private Long sizeBytes;

    @ApiModelProperty(value = "宽度")
    private Integer width;

    @ApiModelProperty(value = "高度")
    private Integer height;

    // Audit field explicit but ignored on create (DB default fills it)
    @ApiModelProperty(value = "创建时间", notes = "由数据库默认值填充")
    @JsonIgnore
    private LocalDateTime createdAt;
}

