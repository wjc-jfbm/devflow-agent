package com.devflow.api;

import com.devflow.api.dto.ApprovalRequest;
import com.devflow.api.dto.ProjectCreateRequest;
import com.devflow.api.dto.TaskCreateRequest;
import com.devflow.common.enums.TaskStatus;
import com.devflow.core.agent.*;
import com.devflow.core.orchestration.SupervisorAgent;
import com.devflow.core.tool.GitHubTools;
import com.devflow.infra.mq.TaskProducer;
import com.devflow.infra.persistence.repository.AgentExecutionRepository;
import com.devflow.infra.persistence.repository.ProjectRepository;
import com.devflow.infra.persistence.repository.TaskRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.hamcrest.Matchers.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class DevFlowIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private ProjectRepository projectRepository;

    @Autowired
    private TaskRepository taskRepository;

    @MockBean
    private RequirementsAgent requirementsAgent;

    @MockBean
    private ArchitectAgent architectAgent;

    @MockBean
    private CoderAgent coderAgent;

    @MockBean
    private TesterAgent testerAgent;

    @MockBean
    private ReviewerAgent reviewerAgent;

    @MockBean
    private SupervisorAgent supervisorAgent;

    @MockBean
    private TaskProducer taskProducer;

    @MockBean
    private StringRedisTemplate redisTemplate;

    @MockBean
    private GitHubTools gitHubTools;

    @MockBean
    private AgentExecutionRepository agentExecutionRepository;

    private static Long createdProjectId;
    private static Long createdTaskId;

    @Test
    @Order(1)
    @DisplayName("1. 健康检查 - 服务可用")
    void healthCheck_ShouldReturnOk() throws Exception {
        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
        System.out.println("[PASS] 健康检查 - 服务可用");
    }

    @Test
    @Order(2)
    @DisplayName("2. 创建项目 - 成功")
    void createProject_ShouldReturnProject() throws Exception {
        ProjectCreateRequest request = new ProjectCreateRequest();
        request.setName("集成测试项目");
        request.setRepoUrl("https://github.com/devflow/integration-test");
        request.setLanguage("Java");
        request.setFramework("Spring Boot");
        request.setDescription("集成测试专用项目");

        String requestBody = objectMapper.writeValueAsString(request);

        MvcResult result = mockMvc.perform(post("/api/projects")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody)
                        .with(SecurityMockMvcRequestPostProcessors.httpBasic("admin", "admin123")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.name").value("集成测试项目"))
                .andExpect(jsonPath("$.data.repoUrl").value("https://github.com/devflow/integration-test"))
                .andReturn();

        String response = result.getResponse().getContentAsString();
        createdProjectId = objectMapper.readTree(response).get("data").get("id").asLong();
        System.out.println("[PASS] 创建项目 - 成功, 项目ID: " + createdProjectId);
    }

    @Test
    @Order(3)
    @DisplayName("3. 创建项目 - 参数校验失败")
    void createProject_ShouldFailValidation() throws Exception {
        ProjectCreateRequest request = new ProjectCreateRequest();
        request.setName("");
        request.setRepoUrl("");

        String requestBody = objectMapper.writeValueAsString(request);

        mockMvc.perform(post("/api/projects")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody)
                        .with(SecurityMockMvcRequestPostProcessors.httpBasic("admin", "admin123")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400));
        System.out.println("[PASS] 创建项目 - 参数校验失败");
    }

    @Test
    @Order(4)
    @DisplayName("4. 获取项目列表")
    void listProjects_ShouldReturnProjects() throws Exception {
        mockMvc.perform(get("/api/projects")
                        .with(SecurityMockMvcRequestPostProcessors.httpBasic("admin", "admin123")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data", hasSize(greaterThanOrEqualTo(1))));
        System.out.println("[PASS] 获取项目列表");
    }

    @Test
    @Order(5)
    @DisplayName("5. 获取项目详情")
    void getProject_ShouldReturnProject() throws Exception {
        mockMvc.perform(get("/api/projects/{id}", createdProjectId)
                        .with(SecurityMockMvcRequestPostProcessors.httpBasic("admin", "admin123")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.id").value(createdProjectId))
                .andExpect(jsonPath("$.data.name").value("集成测试项目"));
        System.out.println("[PASS] 获取项目详情");
    }

    @Test
    @Order(6)
    @DisplayName("6. 创建任务 - 成功")
    void createTask_ShouldReturnTask() throws Exception {
        TaskCreateRequest request = new TaskCreateRequest();
        request.setProjectId(createdProjectId);
        request.setIssueNumber(1);
        request.setIssueTitle("测试功能：用户登录");
        request.setIssueBody("实现登录接口和JWT认证");

        String requestBody = objectMapper.writeValueAsString(request);

        doNothing().when(taskProducer).sendTask(any(Long.class));

        MvcResult result = mockMvc.perform(post("/api/tasks")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody)
                        .with(SecurityMockMvcRequestPostProcessors.httpBasic("admin", "admin123")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.issueTitle").value("测试功能：用户登录"))
                .andReturn();

        String response = result.getResponse().getContentAsString();
        createdTaskId = objectMapper.readTree(response).get("data").get("id").asLong();
        System.out.println("[PASS] 创建任务 - 成功, 任务ID: " + createdTaskId);
    }

    @Test
    @Order(7)
    @DisplayName("7. 创建任务 - 参数校验失败")
    void createTask_ShouldFailValidation() throws Exception {
        TaskCreateRequest request = new TaskCreateRequest();
        request.setProjectId(null);
        request.setIssueNumber(null);
        request.setIssueTitle("");

        String requestBody = objectMapper.writeValueAsString(request);

        mockMvc.perform(post("/api/tasks")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody)
                        .with(SecurityMockMvcRequestPostProcessors.httpBasic("admin", "admin123")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400));
        System.out.println("[PASS] 创建任务 - 参数校验失败");
    }

    @Test
    @Order(8)
    @DisplayName("8. 获取任务详情")
    void getTask_ShouldReturnTask() throws Exception {
        mockMvc.perform(get("/api/tasks/{id}", createdTaskId)
                        .with(SecurityMockMvcRequestPostProcessors.httpBasic("admin", "admin123")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.id").value(createdTaskId))
                .andExpect(jsonPath("$.data.status").value(TaskStatus.PENDING.name()));
        System.out.println("[PASS] 获取任务详情");
    }

    @Test
    @Order(9)
    @DisplayName("9. 获取任务列表")
    void listTasks_ShouldReturnTasks() throws Exception {
        mockMvc.perform(get("/api/tasks")
                        .with(SecurityMockMvcRequestPostProcessors.httpBasic("admin", "admin123")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data", hasSize(greaterThanOrEqualTo(1))));
        System.out.println("[PASS] 获取任务列表");
    }

    @Test
    @Order(10)
    @DisplayName("10. 获取任务进度")
    void getTaskProgress_ShouldReturnProgress() throws Exception {
        mockMvc.perform(get("/api/tasks/{id}/progress", createdTaskId)
                        .with(SecurityMockMvcRequestPostProcessors.httpBasic("admin", "admin123")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.taskId").value(createdTaskId))
                .andExpect(jsonPath("$.data.currentPhase").exists());
        System.out.println("[PASS] 获取任务进度");
    }

    @Test
    @Order(11)
    @DisplayName("11. 审批任务 - 参数校验失败")
    void approveTask_ShouldFailValidation() throws Exception {
        ApprovalRequest request = new ApprovalRequest();
        request.setApprover("");
        request.setAction("");

        String requestBody = objectMapper.writeValueAsString(request);

        mockMvc.perform(post("/api/tasks/{id}/approve", createdTaskId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody)
                        .with(SecurityMockMvcRequestPostProcessors.httpBasic("admin", "admin123")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400));
        System.out.println("[PASS] 审批任务 - 参数校验失败");
    }

    @Test
    @Order(12)
    @DisplayName("12. 删除任务")
    void deleteTask_ShouldDelete() throws Exception {
        mockMvc.perform(delete("/api/tasks/{id}", createdTaskId)
                        .with(SecurityMockMvcRequestPostProcessors.httpBasic("admin", "admin123")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
        System.out.println("[PASS] 删除任务");
    }

    @Test
    @Order(13)
    @DisplayName("13. 删除项目")
    void deleteProject_ShouldDelete() throws Exception {
        mockMvc.perform(delete("/api/projects/{id}", createdProjectId)
                        .with(SecurityMockMvcRequestPostProcessors.httpBasic("admin", "admin123")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
        System.out.println("[PASS] 删除项目");
    }

    @Test
    @Order(14)
    @DisplayName("14. 获取不存在的项目 - 返回404")
    void getNonExistentProject_ShouldReturn404() throws Exception {
        mockMvc.perform(get("/api/projects/99999")
                        .with(SecurityMockMvcRequestPostProcessors.httpBasic("admin", "admin123")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(404));
        System.out.println("[PASS] 获取不存在的项目 - 返回404");
    }

    @Test
    @Order(15)
    @DisplayName("15. 获取不存在的任务 - 返回404")
    void getNonExistentTask_ShouldReturn404() throws Exception {
        mockMvc.perform(get("/api/tasks/99999")
                        .with(SecurityMockMvcRequestPostProcessors.httpBasic("admin", "admin123")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(404));
        System.out.println("[PASS] 获取不存在的任务 - 返回404");
    }
}