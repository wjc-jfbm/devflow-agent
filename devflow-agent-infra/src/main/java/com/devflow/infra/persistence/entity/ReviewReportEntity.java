package com.devflow.infra.persistence.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 审查报告实体
 */
@Data
@TableName("t_review_report")
public class ReviewReportEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long taskId;

    private String agentType;

    private String severity;

    private String filePath;

    private Integer lineNumber;

    private String category;

    private String message;

    private String suggestion;

    private Integer isFixed;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
