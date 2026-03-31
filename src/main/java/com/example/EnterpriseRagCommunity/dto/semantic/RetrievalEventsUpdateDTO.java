package com.example.EnterpriseRagCommunity.dto.semantic;

import com.fasterxml.jackson.annotation.JsonIgnore;
import io.swagger.annotations.ApiModelProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class RetrievalEventsUpdateDTO {
    @ApiModelProperty(value = "主键ID", required = true)
    @NotNull
    private Long id; // 必须存在

    @ApiModelProperty(value = "触发检索的用户ID")
    private Long userId; // 可选更新

    @ApiModelProperty(value = "查询文本")
    private @NotBlank String queryText; // NOT NULL -> 若出现必须非空

    @ApiModelProperty(value = "BM25 TopK")
    private Integer bm25K;

    @ApiModelProperty(value = "向量召回 TopK")
    private Integer vecK;

    @ApiModelProperty(value = "融合后保留 TopK")
    private Integer hybridK;

    @ApiModelProperty(value = "重排模型")
    private @Size(max = 64) String rerankModel;

    @ApiModelProperty(value = "重排TopK")
    private Integer rerankK;

    @ApiModelProperty(value = "创建时间(不可修改)")
    @JsonIgnore // 保留字段但不允许客户端更新
    private LocalDateTime createdAt; // 映射但禁止更新
}

