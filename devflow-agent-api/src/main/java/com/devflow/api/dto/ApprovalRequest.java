package com.devflow.api.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 审批请求
 */
@Data
public class ApprovalRequest {

    @NotBlank(message = "审批人不能为空")
    private String approver;

    private String comment;

    @NotBlank(message = "审批动作不能为空")
    private String action; // APPROVED / REJECTED
}
