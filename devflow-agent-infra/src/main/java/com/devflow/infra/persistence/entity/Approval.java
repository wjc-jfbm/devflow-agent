package com.devflow.infra.persistence.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 人工审批记录实体
 */
@Data
@TableName("t_approval")
public class Approval {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long taskId;

    private String phase;

    private String status;

    private String approver;

    private String comment;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
