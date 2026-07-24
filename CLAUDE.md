# CLAUDE.md — DevFlow Agent

> AI 驱动的全自动研发流水线 — 从 GitHub Issue 到 Pull Request，6 个 AI Agent 协作完成。

## 项目概览

- **语言**: Java 17
- **框架**: Spring Boot 3.2
- **AI**: LangChain4j 1.0.0-beta2 + DeepSeek (兼容 OpenAI)
- **数据库**: MySQL 8.0 + MyBatis-Plus 3.5.5
- **缓存/队列**: Redis 7 + RabbitMQ
- **部署**: Docker Compose
- **API 文档**: Knife4j (Swagger 增强)

## 模块结构

```
devflow-agent/
├── devflow-agent-api/       # REST API 层（Controller、DTO、Security）
├── devflow-agent-core/      # 核心引擎（6 个 Agent、WorkflowEngine、RAG）
├── devflow-agent-common/    # 公共层（枚举、模型、异常、工具类）
├── devflow-agent-infra/     # 基础设施（MyBatis、Redis、RabbitMQ、LangChain4j）
├── sql/init.sql             # 数据库初始化脚本
├── docker-compose.yml       # 一键部署（MySQL + Redis + RabbitMQ + pgvector + App）
├── Dockerfile               # 应用容器镜像
├── .env.example             # 环境变量模板（有真实 .env 则复制此文件填写）
├── Makefile                 # 常用命令
└── test/test_api.py         # Python 自动化 API 测试脚本
```

## 构建与运行

```bash
# 编译（跳过测试）
make build

# 启动所有依赖 + 应用
make docker-up

# 仅启动依赖（MySQL/Redis/RabbitMQ），在 IDE 中调试
make docker-up-infra

# 运行测试
make test

# 进入 Swagger 文档
# http://localhost:8080/doc.html (admin / devflow2024)
```

## 6 个 AI Agent 流水线

```
GitHub Issue → SupervisorAgent(路由) → RequirementsAgent → ArchitectAgent
→ ⏸审批 → CoderAgent → TesterAgent → ReviewerAgent(3维度并行) → ⏸审批 → 创建PR
```

| Agent | 接口 | 职责 |
|-------|------|------|
| SupervisorAgent | `SupervisorAgent.java` | Issue 分类路由 (FULL/QUICK/SIMPLE/REVIEW) |
| RequirementsAgent | `RequirementsAgent.java` | 需求提取、验收标准 |
| ArchitectAgent | `ArchitectAgent.java` | 架构设计、类结构 |
| CoderAgent | `CoderAgent.java` | 代码生成 (JSON 输出) |
| TesterAgent | `TesterAgent.java` | JUnit5 测试生成 |
| ReviewerAgent | `ReviewerAgent.java` | SECURITY/PERFORMANCE/CONVENTION 三维审查 |

## 关键入口

- **REST API 入口**: `TaskController.java` (任务), `ProjectController.java` (项目)
- **Webhook 入口**: `WebhookController.java` (GitHub Issue 事件)
- **工作流引擎**: `WorkflowEngine.java` — 编排流水线，含重试、状态管理
- **MQ 消费者**: `TaskConsumer.java` — 监听 RabbitMQ 触发异步工作流
- **AI 配置**: `LangChain4jConfig.java` — OpenAI/DeepSeek 兼容模型
- **审批流**: `PipelineOrchestrator.java` — approve/reject 逻辑

## 环境变量

主要配置见 `.env.example`。必填项：
- `OPENAI_API_KEY` — DeepSeek 或 OpenAI API Key
- 生产环境额外必填: `DEVFLOW_ADMIN_PASSWORD`, `DEVFLOW_OPERATOR_PASSWORD`

## 代码约定

- Controller 层使用 `R<T>` 统一响应格式
- Service 抛出 `BusinessException(code, msg)` 由 `GlobalExceptionHandler` 统一处理
- WorkflowEngine 通过 Redis 缓存各阶段结果 (`task:{id}:phaseName`)
- Agent 返回都为 String (JSON)，在 `cleanJsonOutput()` 中清理 LLM 的 markdown 包裹
