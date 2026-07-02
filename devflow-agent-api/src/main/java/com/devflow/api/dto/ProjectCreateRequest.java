package com.devflow.api.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 创建项目请求
 */
@Data
public class ProjectCreateRequest {

    @NotBlank(message = "项目名称不能为空")
    private String name;

    @NotBlank(message = "仓库地址不能为空")
    private String repoUrl;

    private String language = "Java";

    private String framework = "Spring Boot";

    private String description;

    private String githubToken;
}
