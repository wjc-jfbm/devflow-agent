package com.devflow.core.tool;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.regex.Pattern;

/**
 * 静态分析工具
 * 提供代码质量分析（后续可集成 SpotBugs/Checkstyle）
 */
@Slf4j
@Component
public class CodeAnalysisTools {

    // 正则模式：匹配各种形式的空 catch 块
    // 如 catch (Exception e) { } / catch(IOException e){} / catch (Exception e) {\n  }
    private static final Pattern EMPTY_CATCH = Pattern.compile(
            "catch\\s*\\(\\s*\\w+\\s+\\w+\\s*\\)\\s*\\{\\s*(//.*)?\\s*\\}",
            Pattern.CASE_INSENSITIVE);

    // 正则模式：匹配 System.out.print / System.err.print
    private static final Pattern SYSTEM_OUT = Pattern.compile(
            "System\\.(out|err)\\.print(ln|f)?\\s*\\(");

    // 正则模式：匹配硬编码密码/密钥
    private static final Pattern HARDCODED_SECRET = Pattern.compile(
            "(password|passwd|pwd|secret|apiKey|api_key|token)\\s*[=:]\\s*\"[^\"]+\"",
            Pattern.CASE_INSENSITIVE);

    // 正则模式：检测 SQL 拼接（潜在的 SQL 注入）
    // 匹配 "SELECT ... " + var 或 "WHERE ... " + var 等字符串拼接模式
    private static final Pattern SQL_CONCAT = Pattern.compile(
            "\"[^\"]*\\b(SELECT|INSERT|UPDATE|DELETE|WHERE|FROM|JOIN)\\b[^\"]*\"\\s*\\+\\s*\\w+",
            Pattern.CASE_INSENSITIVE);

    /**
     * 分析代码质量
     */
    public AnalysisResult analyze(String codeContent) {
        if (codeContent == null || codeContent.isBlank()) {
            return new AnalysisResult(true, 0, "No code to analyze");
        }

        int issues = 0;
        StringBuilder report = new StringBuilder();

        // 检查空 catch 块
        if (EMPTY_CATCH.matcher(codeContent).find()) {
            issues++;
            report.append("- Found empty catch block(s) — swallow exceptions without handling\n");
        }

        // 检查 System.out/System.err 使用
        if (SYSTEM_OUT.matcher(codeContent).find()) {
            issues++;
            report.append("- Using System.out/System.err instead of logging framework (SLF4J/Logback)\n");
        }

        // 检查硬编码密码
        if (HARDCODED_SECRET.matcher(codeContent).find()) {
            issues++;
            report.append("- Found hardcoded password/secret/token assignment\n");
        }

        // 检查 SQL 拼接
        if (SQL_CONCAT.matcher(codeContent).find()) {
            issues++;
            report.append("- Potential SQL concatenation — use parameterized queries or MyBatis-Plus\n");
        }

        return new AnalysisResult(issues == 0, issues, report.toString());
    }

    public record AnalysisResult(boolean passed, int issueCount, String report) {}
}
