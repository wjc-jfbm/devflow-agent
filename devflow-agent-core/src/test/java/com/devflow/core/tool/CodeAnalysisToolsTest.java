package com.devflow.core.tool;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("CodeAnalysisTools 单元测试")
class CodeAnalysisToolsTest {

    private CodeAnalysisTools tools;

    @BeforeEach
    void setUp() {
        tools = new CodeAnalysisTools();
    }

    @Nested
    @DisplayName("analyze — 空 catch 块检测")
    class EmptyCatchDetection {

        @Test
        @DisplayName("检测到空 catch 块 (Exception e) { }")
        void shouldDetectEmptyCatchWithException() {
            String code = """
                    public void doSomething() {
                        try {
                            riskyOperation();
                        } catch (Exception e) { }
                    }""";
            CodeAnalysisTools.AnalysisResult result = tools.analyze(code);
            assertFalse(result.passed());
            assertEquals(1, result.issueCount());
            assertTrue(result.report().contains("empty catch"));
        }

        @Test
        @DisplayName("检测到空 catch 块 (IOException e) {}")
        void shouldDetectEmptyCatchWithIoException() {
            String code = """
                    try {
                        Files.read(path);
                    } catch(IOException e){}""";
            CodeAnalysisTools.AnalysisResult result = tools.analyze(code);
            assertFalse(result.passed());
            assertTrue(result.report().contains("empty catch"));
        }

        @Test
        @DisplayName("不误报有内容的 catch 块")
        void shouldNotFlagNonEmptyCatch() {
            String code = """
                    try {
                        doSomething();
                    } catch (Exception e) {
                        log.error("Failed", e);
                        throw new BusinessException("error");
                    }""";
            CodeAnalysisTools.AnalysisResult result = tools.analyze(code);
            // May flag other issues, but not empty catch
            assertFalse(result.report().contains("empty catch"));
        }

        @Test
        @DisplayName("检测带注释的空 catch 块")
        void shouldDetectEmptyCatchWithComment() {
            String code = "try { doIt(); } catch (Exception e) { // TODO }";
            CodeAnalysisTools.AnalysisResult result = tools.analyze(code);
            assertFalse(result.passed());
            assertTrue(result.report().contains("empty catch"));
        }
    }

    @Nested
    @DisplayName("analyze — System.out 检测")
    class SystemOutDetection {

        @Test
        @DisplayName("检测到 System.out.println")
        void shouldDetectSystemOutPrintln() {
            String code = "System.out.println(\"debug: \" + value);";
            CodeAnalysisTools.AnalysisResult result = tools.analyze(code);
            assertFalse(result.passed());
            assertTrue(result.report().contains("System.out"));
        }

        @Test
        @DisplayName("检测到 System.err.println")
        void shouldDetectSystemErr() {
            String code = "System.err.println(\"error!\");";
            CodeAnalysisTools.AnalysisResult result = tools.analyze(code);
            assertFalse(result.passed());
            assertTrue(result.report().contains("System.out"));
        }

        @Test
        @DisplayName("不误报 log.info")
        void shouldNotFlagLogStatement() {
            String code = "log.info(\"message\"); logger.debug(\"test\");";
            CodeAnalysisTools.AnalysisResult result = tools.analyze(code);
            assertFalse(result.report().contains("System.out"));
        }
    }

    @Nested
    @DisplayName("analyze — 硬编码密码检测")
    class HardcodedSecretDetection {

        @Test
        @DisplayName("检测到硬编码密码")
        void shouldDetectHardcodedPassword() {
            String code = "String password = \"admin123\";";
            CodeAnalysisTools.AnalysisResult result = tools.analyze(code);
            assertFalse(result.passed());
            assertTrue(result.report().contains("hardcoded"));
        }

        @Test
        @DisplayName("检测到硬编码 API Key")
        void shouldDetectHardcodedApiKey() {
            String code = "String apiKey = \"sk-abc123def456\";";
            CodeAnalysisTools.AnalysisResult result = tools.analyze(code);
            assertFalse(result.passed());
            assertTrue(result.report().contains("hardcoded"));
        }

        @Test
        @DisplayName("检测到硬编码 secret")
        void shouldDetectHardcodedSecret() {
            String code = "secret: \"my-secret-value\"";
            CodeAnalysisTools.AnalysisResult result = tools.analyze(code);
            assertFalse(result.passed());
            assertTrue(result.report().contains("hardcoded"));
        }

        @Test
        @DisplayName("不误报环境变量读取")
        void shouldNotFlagEnvVarLookup() {
            String code = "String password = System.getenv(\"DB_PASSWORD\");";
            CodeAnalysisTools.AnalysisResult result = tools.analyze(code);
            assertFalse(result.report().contains("hardcoded"));
        }
    }

    @Nested
    @DisplayName("analyze — SQL 拼接检测")
    class SqlConcatDetection {

        @Test
        @DisplayName("检测到 SQL 字符串拼接")
        void shouldDetectSqlConcat() {
            String code = "String sql = \"SELECT * FROM users WHERE id = \" + userId;";
            CodeAnalysisTools.AnalysisResult result = tools.analyze(code);
            assertFalse(result.passed());
            assertTrue(result.report().contains("SQL"));
        }

        @Test
        @DisplayName("不误报参数化查询")
        void shouldNotFlagParameterizedQuery() {
            String code = "jdbcTemplate.query(\"SELECT * FROM users WHERE id = ?\", userId);";
            CodeAnalysisTools.AnalysisResult result = tools.analyze(code);
            assertFalse(result.report().contains("SQL"));
        }
    }

    @Nested
    @DisplayName("analyze — 干净代码")
    class CleanCode {

        @Test
        @DisplayName("无问题的代码返回 passed=true")
        void shouldPassForCleanCode() {
            String code = """
                    @Slf4j
                    @Service
                    public class UserService {
                        public User getUser(Long id) {
                            try {
                                return repository.getById(id);
                            } catch (Exception e) {
                                log.error("Failed to get user", e);
                                throw new BusinessException("User not found");
                            }
                        }
                    }""";
            CodeAnalysisTools.AnalysisResult result = tools.analyze(code);
            assertTrue(result.passed());
            assertEquals(0, result.issueCount());
        }

        @Test
        @DisplayName("空/null 输入返回 passed=true")
        void shouldPassForEmptyInput() {
            CodeAnalysisTools.AnalysisResult result = tools.analyze("");
            assertTrue(result.passed());
            assertEquals(0, result.issueCount());
        }

        @Test
        @DisplayName("null 输入返回 passed=true")
        void shouldPassForNullInput() {
            CodeAnalysisTools.AnalysisResult result = tools.analyze(null);
            assertTrue(result.passed());
            assertEquals(0, result.issueCount());
        }
    }

    @Nested
    @DisplayName("analyze — 多问题检测")
    class MultipleIssues {

        @Test
        @DisplayName("同时检测到多个问题")
        void shouldDetectMultipleIssues() {
            String code = """
                    public class Bad {
                        String password = "123456";
                        public void run() {
                            try { doIt(); } catch (Exception e) { }
                            System.out.println("done");
                            String sql = "SELECT * FROM t WHERE id = " + id;
                        }
                    }""";
            CodeAnalysisTools.AnalysisResult result = tools.analyze(code);
            assertFalse(result.passed());
            assertTrue(result.issueCount() >= 3, "Expected at least 3 issues, got " + result.issueCount());
        }
    }

    @Nested
    @DisplayName("AnalysisResult record")
    class AnalysisResultRecord {

        @Test
        @DisplayName("记录构造和访问器")
        void shouldConstructCorrectly() {
            CodeAnalysisTools.AnalysisResult result = new CodeAnalysisTools.AnalysisResult(true, 0, "clean");
            assertTrue(result.passed());
            assertEquals(0, result.issueCount());
            assertEquals("clean", result.report());
        }
    }
}