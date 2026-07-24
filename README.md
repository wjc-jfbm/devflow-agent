# DevFlow Agent

> AI 驱动的全自动研发流水线 — 从 GitHub Issue 到 Pull Request，6 个 AI Agent 协作完成。

## 一句话描述

收到 GitHub Issue → **AI 自动分析需求 → 设计架构 → 生成代码 → 编写测试 → 多维度审查 → 创建 PR**，中间有两次人工审批门禁。

## 架构概览

```
GitHub Issue (WebHook)
       │
       ▼
   RabbitMQ ──→ SupervisorAgent (智能路由)
                     │
       ┌─────────────┼─────────────┐
       ▼             ▼             ▼
    FULL          QUICK         SIMPLE/REVIEW
       │
       ▼
   RequirementsAgent ──→ ArchitectAgent ──→ ⏸ 审批 ──→ CoderAgent
                                                          │
                                                          ▼
                       ⏸ 审批 ←── ReviewerAgent ←── TesterAgent
                          │
                          ▼
                    创建 GitHub PR ✅
```

## 6 个 AI Agent

| Agent | 角色 | 职责 |
|-------|------|------|
| **SupervisorAgent** | 监督者 | 分析 Issue 类型，智能路由到不同流水线（FULL/QUICK/SIMPLE/REVIEW） |
| **RequirementsAgent** | 需求分析师 | 提取功能需求、验收标准、边界条件 |
| **ArchitectAgent** | 架构设计师 | 设计最小改动方案、类结构、接口定义 |
| **CoderAgent** | 程序员 | 编写完整的 Spring Boot + MyBatis-Plus 代码 |
| **TesterAgent** | 测试工程师 | 生成 JUnit5 + Mockito 单元测试 |
| **ReviewerAgent** | 代码审查员 | 安全/性能/规范三维并行审查，0-100 评分 |

## 技术栈

| 层级 | 技术 |
|------|------|
| 框架 | Spring Boot 3.2、Java 17 |
| AI | LangChain4j + DeepSeek（兼容 OpenAI） |
| 数据库 | MySQL 8.0 + MyBatis-Plus |
| 缓存 | Redis 7 |
| 消息队列 | RabbitMQ |
| 部署 | Docker + Docker Compose |
| 文档 | Knife4j / Swagger |

## 快速开始

### 前置条件

- **Docker** & Docker Compose
- **DeepSeek API Key**（[免费注册获取](https://platform.deepseek.com/)）
- **GitHub Token**（可选，如需自动创建 PR）

### 1. 克隆项目

```bash
git clone <your-repo-url>
cd devflow-agent
```

### 2. 配置 API Key

编辑 `.env` 文件，填入你的 API Key：

```bash
OPENAI_API_KEY=sk-xxxxxxxxxxxxxxxx    # DeepSeek 或 OpenAI Key
# 可选：GitHub Token
GITHUB_TOKEN=ghp_xxxxxxxxxxxxxxxx
```

### 3. 编译项目

```bash
mvn clean package -DskipTests -pl devflow-agent-api -am
```

### 4. 一键启动

```bash
docker compose up -d
```

等待约 30 秒，打开浏览器访问：

- **Swagger 文档**：http://localhost:8080/doc.html
- **健康检查**：http://localhost:8080/actuator/health

### 5. 测试接口

Swagger 页面右上角点 **Authorize**，输入 `admin / devflow2024`，然后：

1. `POST /api/projects` — 创建一个项目
2. `POST /api/tasks` — 创建一个任务（自动触发 AI 流水线）
3. `GET /api/tasks/{id}/progress` — 查看 AI 处理进度
4. `POST /api/tasks/{id}/approve` — 人工审批（流水线会暂停等审批）

## 项目结构

```
devflow-agent/
├── devflow-agent-api/       # REST API 层（Controller、DTO、Security）
├── devflow-agent-core/      # 核心引擎（6 个 Agent、WorkflowEngine、RAG）
├── devflow-agent-common/    # 公共层（枚举、模型、异常处理）
├── devflow-agent-infra/     # 基础设施（MyBatis、Redis、RabbitMQ、LangChain4j）
├── sql/init.sql             # 数据库建表脚本
├── docker-compose.yml       # 一键部署（MySQL + Redis + RabbitMQ + App）
├── Dockerfile               # 应用容器镜像
├── .env                     # 环境变量配置
└── pom.xml                  # Maven 父 POM
```

## API 接口一览

| 模块 | 端点 | 说明 |
|------|------|------|
| 项目管理 | `GET/POST /api/projects` | 项目 CRUD |
| 任务管理 | `GET/POST /api/tasks` | 创建任务，触发流水线 |
| 任务进度 | `GET /api/tasks/{id}/progress` | 查看流水线进度和 Token 消耗 |
| 审批 | `POST /api/tasks/{id}/approve` | 人工审批（APPROVED/REJECTED） |
| 仪表盘 | `GET /api/dashboard/stats` | 任务统计 |
| Webhook | `POST /webhook/github` | GitHub Issue 事件接收 |

## 配置说明

所有配置通过环境变量覆盖，见 `.env` 文件：

| 变量 | 必填 | 说明 |
|------|------|------|
| `OPENAI_API_KEY` | ✅ | DeepSeek 或 OpenAI API Key |
| `OPENAI_BASE_URL` | — | API 地址，默认 `https://api.deepseek.com` |
| `OPENAI_MODEL` | — | 模型名，默认 `deepseek-chat` |
| `GITHUB_TOKEN` | — | GitHub Personal Access Token（自动 PR 需要） |

## License

MIT
