package com.devflow.core.orchestration;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("WorkflowEngine 单元测试")
class WorkflowEngineTest {

    @Nested
    @DisplayName("cleanJsonOutput — LLM JSON 清洗")
    class CleanJsonOutput {

        @Test
        @DisplayName("纯 JSON — 原样返回")
        void shouldReturnPureJsonUnchanged() {
            String input = "{\"key\": \"value\"}";
            assertEquals("{\"key\": \"value\"}", WorkflowEngine.cleanJsonOutput(input));
        }

        @Test
        @DisplayName("带 ```json 代码块标记 — 提取 JSON")
        void shouldExtractFromJsonBlock() {
            String input = "```json\n{\"key\": \"value\"}\n```";
            assertEquals("{\"key\": \"value\"}", WorkflowEngine.cleanJsonOutput(input));
        }

        @Test
        @DisplayName("带 ``` 无语言的代码块 — 提取 JSON")
        void shouldExtractFromCodeBlock() {
            String input = "```\n{\"key\": \"value\"}\n```";
            assertEquals("{\"key\": \"value\"}", WorkflowEngine.cleanJsonOutput(input));
        }

        @Test
        @DisplayName("JSON 前有文字 — 跳过前缀")
        void shouldSkipLeadingText() {
            String input = "Here is the JSON:\n{\"key\": \"value\"}";
            assertEquals("{\"key\": \"value\"}", WorkflowEngine.cleanJsonOutput(input));
        }

        @Test
        @DisplayName("JSON 后有文字 — 截断后缀 ```")
        void shouldTrimTrailingMarkdown() {
            String input = "{\"key\": \"value\"}\n```\nsome text";
            assertEquals("{\"key\": \"value\"}", WorkflowEngine.cleanJsonOutput(input));
        }

        @Test
        @DisplayName("null — 返回 null")
        void shouldReturnNullForNull() {
            assertNull(WorkflowEngine.cleanJsonOutput(null));
        }

        @Test
        @DisplayName("数组 JSON — 原样返回")
        void shouldHandleArrayJson() {
            String input = "[{\"a\": 1}, {\"b\": 2}]";
            assertEquals("[{\"a\": 1}, {\"b\": 2}]", WorkflowEngine.cleanJsonOutput(input));
        }

        @Test
        @DisplayName("嵌套 markdown 中的 JSON")
        void shouldExtractNestedJsonInMarkdown() {
            String input = "```json\n{\n  \"files\": [\n    {\"path\": \"A.java\"}\n  ]\n}\n```";
            assertEquals("{\n  \"files\": [\n    {\"path\": \"A.java\"}\n  ]\n}", WorkflowEngine.cleanJsonOutput(input));
        }
    }

    @Nested
    @DisplayName("parseWorkflowType — 路由决策解析")
    class ParseWorkflowType {

        @Test
        @DisplayName("FULL 类型 — 正常解析")
        void shouldParseFull() {
            assertEquals("FULL", WorkflowEngine.parseWorkflowType(
                    "{\"workflowType\": \"FULL\", \"reason\": \"新功能需求\"}"));
        }

        @Test
        @DisplayName("QUICK 类型 — 正常解析")
        void shouldParseQuick() {
            assertEquals("QUICK", WorkflowEngine.parseWorkflowType(
                    "{\"workflowType\": \"QUICK\", \"reason\": \"Bug修复\"}"));
        }

        @Test
        @DisplayName("SIMPLE 类型 — 正常解析")
        void shouldParseSimple() {
            assertEquals("SIMPLE", WorkflowEngine.parseWorkflowType(
                    "{\"workflowType\": \"SIMPLE\", \"reason\": \"文档变更\"}"));
        }

        @Test
        @DisplayName("REVIEW 类型 — 正常解析")
        void shouldParseReview() {
            assertEquals("REVIEW", WorkflowEngine.parseWorkflowType(
                    "{\"workflowType\": \"REVIEW\", \"reason\": \"PR审查请求\"}"));
        }

        @Test
        @DisplayName("带 markdown 代码块的 JSON — 清洗后解析")
        void shouldParseWithMarkdownBlock() {
            assertEquals("FULL", WorkflowEngine.parseWorkflowType(
                    "```json\n{\"workflowType\": \"FULL\", \"reason\": \"test\"}\n```"));
        }

        @Test
        @DisplayName("未知类型 — fallback 到 FULL")
        void shouldFallbackForUnknownType() {
            assertEquals("FULL", WorkflowEngine.parseWorkflowType(
                    "{\"workflowType\": \"UNKNOWN\", \"reason\": \"??\"}"));
        }

        @Test
        @DisplayName("小写类型 — 转为大写")
        void shouldNormalizeCase() {
            assertEquals("FULL", WorkflowEngine.parseWorkflowType(
                    "{\"workflowType\": \"full\", \"reason\": \"test\"}"));
        }

        @Test
        @DisplayName("畸形 JSON — fallback 到 FULL")
        void shouldFallbackForMalformedJson() {
            assertEquals("FULL", WorkflowEngine.parseWorkflowType("not json at all"));
        }

        @Test
        @DisplayName("缺少 workflowType 字段 — fallback 到 FULL")
        void shouldFallbackForMissingField() {
            assertEquals("FULL", WorkflowEngine.parseWorkflowType(
                    "{\"otherField\": \"value\"}"));
        }
    }
}