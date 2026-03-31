package com.example.EnterpriseRagCommunity.dto.monitor;

import com.fasterxml.jackson.annotation.JsonIgnore;
import io.swagger.annotations.ApiModelProperty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.Map;

@Data
public class RagEvalSamplesUpdateDTO {
    @ApiModelProperty(value = "样本ID", required = true)
    @NotNull
    private Long id;

    @ApiModelProperty(value = "评测批次ID")
    private Long runId;

    @ApiModelProperty(value = "问题/查询")
    private String query;

    @ApiModelProperty(value = "参考答案")
    private String expectedAnswer;

    @ApiModelProperty(value = "引用参考(JSON)")
    private Map<String, Object> referencesJson;

    @ApiModelProperty(value = "创建时间(只读)")
    @JsonIgnore
    private LocalDateTime createdAt;
}
