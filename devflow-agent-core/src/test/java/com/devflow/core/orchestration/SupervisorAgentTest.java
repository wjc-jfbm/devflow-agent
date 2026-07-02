package com.devflow.core.orchestration;

import com.devflow.infra.persistence.entity.Task;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.chat.response.ChatResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("SupervisorAgent 单元测试")
class SupervisorAgentTest {

    @Mock
    private ChatLanguageModel chatModel;

    @InjectMocks
    private SupervisorAgent supervisorAgent;

    private Task testTask;

    @BeforeEach
    void setUp() {
        testTask = new Task();
        testTask.setId(1L);
        testTask.setIssueTitle("Add user login feature with JWT");
        testTask.setIssueBody("Implement login endpoint and JWT token based authentication");
    }

    @Nested
    @DisplayName("analyzeAndRoute — 路由决策")
    class AnalyzeAndRoute {

        @Test
        @DisplayName("返回 FULL 类型（新功能需求）")
        void shouldReturnFullForFeatureRequest() {
            String llmResponse = "{\"workflowType\": \"FULL\", \"reason\": \"这是一个新功能需求\"}";
            mockChatResponse(llmResponse);

            String result = supervisorAgent.analyzeAndRoute(testTask);

            assertNotNull(result);
            assertTrue(result.contains("FULL"));
        }

        @Test
        @DisplayName("返回 QUICK 类型（Bug 修复）")
        void shouldReturnQuickForBugFix() {
            mockChatResponse("{\"workflowType\": \"QUICK\", \"reason\": \"这是一个Bug修复\"}");

            String result = supervisorAgent.analyzeAndRoute(testTask);

            assertTrue(result.contains("QUICK"));
        }

        @Test
        @DisplayName("返回 SIMPLE 类型（文档变更）")
        void shouldReturnSimpleForDocChange() {
            mockChatResponse("{\"workflowType\": \"SIMPLE\", \"reason\": \"文档配置变更\"}");

            String result = supervisorAgent.analyzeAndRoute(testTask);

            assertTrue(result.contains("SIMPLE"));
        }

        @Test
        @DisplayName("返回 REVIEW 类型（PR 审查）")
        void shouldReturnReviewForPrReview() {
            mockChatResponse("{\"workflowType\": \"REVIEW\", \"reason\": \"PR审查请求\"}");

            String result = supervisorAgent.analyzeAndRoute(testTask);

            assertTrue(result.contains("REVIEW"));
        }

        @Test
        @DisplayName("结果包含 reason 字段")
        void shouldIncludeReason() {
            mockChatResponse("{\"workflowType\": \"QUICK\", \"reason\": \"修复空指针异常\"}");

            String result = supervisorAgent.analyzeAndRoute(testTask);

            assertTrue(result.contains("reason"));
            assertTrue(result.contains("空指针"));
        }
    }

    private void mockChatResponse(String jsonResponse) {
        AiMessage aiMessage = AiMessage.from(jsonResponse);
        ChatResponse chatResponse = ChatResponse.builder()
                .aiMessage(aiMessage)
                .build();
        when(chatModel.chat(anyList())).thenReturn(chatResponse);
    }
}