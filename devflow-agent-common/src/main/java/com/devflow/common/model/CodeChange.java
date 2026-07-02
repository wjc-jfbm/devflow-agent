package com.devflow.common.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 代码变更记录
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CodeChange {

    /** 变更的文件列表 */
    private List<FileContent> files;

    /** 变更摘要 */
    private String summary;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class FileContent {
        private String filePath;
        private String content;
        private String changeType;
    }
}
