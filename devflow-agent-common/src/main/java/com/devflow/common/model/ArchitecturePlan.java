package com.devflow.common.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 架构设计方案
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ArchitecturePlan {

    /** 方案概述 */
    private String summary;

    /** 需要修改/新增的文件清单 */
    private List<FileChange> fileChanges;

    /** 类设计描述 */
    private List<ClassDesign> classDesigns;

    /** 接口定义 */
    private List<InterfaceDesign> interfaceDesigns;

    /** 技术选型说明 */
    private String techNotes;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class FileChange {
        private String filePath;
        private String changeType; // ADD / MODIFY / DELETE
        private String description;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ClassDesign {
        private String className;
        private String packageName;
        private String description;
        private List<String> fields;
        private List<String> methods;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class InterfaceDesign {
        private String interfaceName;
        private String packageName;
        private String description;
        private List<String> methods;
    }
}
