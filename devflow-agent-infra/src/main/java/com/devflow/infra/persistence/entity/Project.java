package com.devflow.infra.persistence.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 项目实体
 */
@Data
@TableName("t_project")
public class Project {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String name;

    private String repoUrl;

    private String language;

    private String framework;

    private String description;

    private String githubToken;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;

    @TableLogic
    private Integer deleted;
}
