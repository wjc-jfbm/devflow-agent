package com.devflow.common.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 结构化需求文档
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RequirementsDoc {

    /** 需求摘要 */
    private String summary;

    /** 功能点列表 */
    private List<FeaturePoint> features;

    /** 验收标准 */
    private List<String> acceptanceCriteria;

    /** 边界条件 */
    private List<String> edgeCases;

    /** 需要澄清的问题 */
    private List<String> questions;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class FeaturePoint {
        /** 功能名称 */
        private String name;
        /** 功能描述 */
        private String description;
        /** 优先级: HIGH/MEDIUM/LOW */
        private String priority;
    }
}
