package com.devflow.common.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 工作流阶段枚举
 */
@Getter
@AllArgsConstructor
public enum WorkflowPhase {

    INIT("INIT", "初始化"),
    REQUIREMENTS("REQUIREMENTS", "需求分析"),
    ARCHITECT("ARCHITECT", "架构设计"),
    APPROVAL_ARCHITECT("APPROVAL_ARCHITECT", "架构审批"),
    CODING("CODING", "编码"),
    TESTING("TESTING", "测试"),
    REVIEW("REVIEW", "代码审查"),
    APPROVAL_REVIEW("APPROVAL_REVIEW", "审查审批"),
    PR_CREATION("PR_CREATION", "创建PR"),
    DONE("DONE", "完成");

    private final String code;
    private final String description;

    /**
     * 是否为审批节点
     */
    public boolean isApprovalPhase() {
        return this == APPROVAL_ARCHITECT || this == APPROVAL_REVIEW;
    }

    /**
     * 获取下一个阶段
     */
    public WorkflowPhase next() {
        WorkflowPhase[] phases = values();
        int index = this.ordinal();
        if (index < phases.length - 1) {
            return phases[index + 1];
        }
        return DONE;
    }

    /**
     * 根据 code 获取枚举（替代脆弱的 valueOf，因为 code 可能不等于枚举名）
     */
    public static WorkflowPhase fromCode(String code) {
        for (WorkflowPhase phase : values()) {
            if (phase.getCode().equals(code)) {
                return phase;
            }
        }
        throw new IllegalArgumentException("Unknown workflow phase: " + code);
    }
}
