package com.example.EnterpriseRagCommunity.dto.monitor;

import com.fasterxml.jackson.annotation.JsonIgnore;
import io.swagger.annotations.ApiModelProperty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.Optional;

@Data
public class RagEvalSamplesUpdateDTO {
    @ApiModelProperty(value = "样本ID", required = true)
    @NotNull
    private Long id;

    @ApiModelProperty(value = "评测批次ID")
    private Optional<Long> runId = Optional.empty();

    @ApiModelProperty(value = "问题/查询")
    private Optional<String> query = Optional.empty();

    @ApiModelProperty(value = "参考答案")
    private Optional<String> expectedAnswer = Optional.empty();

    @ApiModelProperty(value = "引用参考(JSON)")
    private Optional<Map<String, Object>> referencesJson = Optional.empty();

    @ApiModelProperty(value = "创建时间(只读)")
    @JsonIgnore
    private Optional<LocalDateTime> createdAt = Optional.empty();
}
