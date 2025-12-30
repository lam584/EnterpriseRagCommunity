package com.example.EnterpriseRagCommunity.dto.ai;

import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class QaSessionUpdateRequest {
    @Size(max = 191)
    private String title;

    private Boolean isActive;
}

