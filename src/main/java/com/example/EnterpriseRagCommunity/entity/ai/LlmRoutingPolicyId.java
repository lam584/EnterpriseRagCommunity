package com.example.EnterpriseRagCommunity.entity.ai;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

@Data
@NoArgsConstructor
@Embeddable
public class LlmRoutingPolicyId implements Serializable {

    @Column(name = "env", length = 32, nullable = false)
    private String env;

    @Column(name = "task_type", length = 64, nullable = false)
    private String taskType;

    public LlmRoutingPolicyId(String env, String taskType) {
        this.env = env;
        this.taskType = taskType;
    }
}
