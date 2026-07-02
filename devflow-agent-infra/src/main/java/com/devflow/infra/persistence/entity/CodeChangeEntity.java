package com.devflow.infra.persistence.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 代码变更记录实体
 */
@Data
@TableName("t_code_change")
public class CodeChangeEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long taskId;

    private String filePath;

    private String changeType;

    private String originalContent;

    private String newContent;

    private String commitSha;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
