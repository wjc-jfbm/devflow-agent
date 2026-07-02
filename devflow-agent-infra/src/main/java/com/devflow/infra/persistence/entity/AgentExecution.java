package com.devflow.infra.persistence.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * Agent 执行记录实体
 */
@Data
@TableName("t_agent_execution")
public class AgentExecution {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long taskId;

    private String agentType;

    private String input;

    private String output;

    private String status;

    private Integer tokensUsed;

    private Long durationMs;

    private String errorMsg;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
