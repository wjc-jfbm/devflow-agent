package com.devflow.common.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 代码审查报告
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReviewReport {

    /** 审查维度 */
    private String category;

    /** 总体评分 (0-100) */
    private int score;

    /** 是否通过审查 */
    private boolean passed;

    /** 发现的问题列表 */
    private List<Issue> issues;

    /** 总结 */
    private String summary;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Issue {
        /** 严重级别: CRITICAL/WARNING/INFO */
        private String severity;
        /** 文件路径 */
        private String filePath;
        /** 行号 */
        private Integer lineNumber;
        /** 问题分类 */
        private String category;
        /** 问题描述 */
        private String message;
        /** 修复建议 */
        private String suggestion;
    }
}
