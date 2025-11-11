package com.example.EnterpriseRagCommunity.dto.semantic;

import com.fasterxml.jackson.annotation.JsonIgnore;
import io.swagger.annotations.ApiModelProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.Optional;

@Data
public class RetrievalEventsUpdateDTO {
    @ApiModelProperty(value = "主键ID", required = true)
    @NotNull
    private Long id; // 必须存在

    @ApiModelProperty(value = "触发检索的用户ID")
    private Optional<Long> userId = Optional.empty(); // 可选更新

    @ApiModelProperty(value = "查询文本")
    private Optional<@NotBlank String> queryText = Optional.empty(); // NOT NULL -> 若出现必须非空

    @ApiModelProperty(value = "BM25 TopK")
    private Optional<Integer> bm25K = Optional.empty();

    @ApiModelProperty(value = "向量召回 TopK")
    private Optional<Integer> vecK = Optional.empty();

    @ApiModelProperty(value = "融合后保留 TopK")
    private Optional<Integer> hybridK = Optional.empty();

    @ApiModelProperty(value = "重排模型")
    private Optional<@Size(max = 64) String> rerankModel = Optional.empty();

    @ApiModelProperty(value = "重排TopK")
    private Optional<Integer> rerankK = Optional.empty();

    @ApiModelProperty(value = "创建时间(不可修改)")
    @JsonIgnore // 保留字段但不允许客户端更新
    private LocalDateTime createdAt; // 映射但禁止更新
}

