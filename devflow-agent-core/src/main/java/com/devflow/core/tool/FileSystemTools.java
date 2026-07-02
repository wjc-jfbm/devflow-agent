package com.devflow.core.tool;

import com.devflow.common.exception.BusinessException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;

/**
 * 文件系统工具
 * 用于读写 Agent 生成的工作文件
 */
@Slf4j
@Component
public class FileSystemTools {

    @Value("${devflow.workspace.base-path:${java.io.tmpdir}/devflow-workspace}")
    private String basePath;

    /**
     * 获取任务工作目录
     */
    public Path getTaskWorkspace(Long taskId) {
        Path workspace = Paths.get(basePath, "task-" + taskId);
        if (!Files.exists(workspace)) {
            try {
                Files.createDirectories(workspace);
            } catch (IOException e) {
                throw new BusinessException("Failed to create workspace: " + workspace, e);
            }
        }
        return workspace;
    }

    /**
     * 写入文件
     */
    public void writeFile(Long taskId, String relativePath, String content) {
        Path workspace = getTaskWorkspace(taskId);
        Path filePath = workspace.resolve(relativePath);

        try {
            Files.createDirectories(filePath.getParent());
            Files.writeString(filePath, content, StandardCharsets.UTF_8);
            log.info("File written: {}", filePath);
        } catch (IOException e) {
            throw new BusinessException("Failed to write file: " + filePath, e);
        }
    }

    /**
     * 读取文件
     */
    public String readFile(Long taskId, String relativePath) {
        Path workspace = getTaskWorkspace(taskId);
        Path filePath = workspace.resolve(relativePath);

        try {
            return Files.readString(filePath, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new BusinessException("Failed to read file: " + filePath, e);
        }
    }

    /**
     * 检查文件是否存在
     */
    public boolean fileExists(Long taskId, String relativePath) {
        Path workspace = getTaskWorkspace(taskId);
        return Files.exists(workspace.resolve(relativePath));
    }

    /**
     * 清理任务工作目录
     */
    public void cleanWorkspace(Long taskId) {
        Path workspace = getTaskWorkspace(taskId);
        try {
            Files.walk(workspace)
                    .sorted((a, b) -> b.compareTo(a))
                    .forEach(path -> {
                        try {
                            Files.deleteIfExists(path);
                        } catch (IOException e) {
                            log.warn("Failed to delete: {}", path);
                        }
                    });
        } catch (IOException e) {
            log.warn("Failed to clean workspace for task {}", taskId, e);
        }
    }
}
