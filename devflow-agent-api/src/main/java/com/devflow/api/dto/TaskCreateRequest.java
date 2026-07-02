package com.devflow.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 创建任务请求
 */
@Data
public class TaskCreateRequest {

    @NotNull(message = "项目ID不能为空")
    private Long projectId;

    private String issueUrl;

    @NotNull(message = "Issue编号不能为空")
    private Integer issueNumber;

    @NotBlank(message = "Issue标题不能为空")
    private String issueTitle;

    private String issueBody;
}
