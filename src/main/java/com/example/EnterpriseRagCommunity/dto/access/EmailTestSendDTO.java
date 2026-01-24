package com.example.EnterpriseRagCommunity.dto.access;

import io.swagger.annotations.ApiModelProperty;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class EmailTestSendDTO {
    @ApiModelProperty("测试收件人邮箱")
    @NotBlank
    @Email
    private String to;
}
