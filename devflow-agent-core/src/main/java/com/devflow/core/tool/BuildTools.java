package com.devflow.core.tool;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 构建工具
 * 执行 Maven 编译、运行测试
 */
@Slf4j
@Component
public class BuildTools {

    private static final long BUILD_TIMEOUT_MINUTES = 10;

    /**
     * 执行 Maven 编译
     */
    public BuildResult compile(String projectPath) {
        return executeCommand(projectPath, "mvn", "compile", "-q");
    }

    /**
     * 执行 Maven 测试
     */
    public BuildResult test(String projectPath) {
        return executeCommand(projectPath, "mvn", "test", "-q");
    }

    private BuildResult executeCommand(String projectPath, String... command) {
        try {
            ProcessBuilder pb = new ProcessBuilder(command);
            pb.directory(new java.io.File(projectPath));
            pb.redirectErrorStream(true);

            Process process = pb.start();
            boolean finished = process.waitFor(BUILD_TIMEOUT_MINUTES, java.util.concurrent.TimeUnit.MINUTES);
            if (!finished) {
                process.destroyForcibly();
                return new BuildResult(false, "Build timed out after " + BUILD_TIMEOUT_MINUTES + " minutes", -1);
            }

            String output = new String(process.getInputStream().readAllBytes());
            int exitCode = process.exitValue();

            return new BuildResult(exitCode == 0, output, exitCode);
        } catch (Exception e) {
            log.error("Build command failed", e);
            return new BuildResult(false, e.getMessage(), -1);
        }
    }

    public record BuildResult(boolean success, String output, int exitCode) {}
}
