# DevFlow Agent 测试指南

## 一、发现问题总览（已修复）

| # | 严重度 | 问题 | 文件 |
|---|--------|------|------|
| 1 | 🔴 CRITICAL | `AgentType` 枚举 code 与 `WorkflowEngine` 不一致（大写 vs 小写） | AgentType.java, WorkflowEngine.java |
| 2 | 🔴 CRITICAL | `ProjectController.updateProject` 静默丢弃 `githubToken` 字段 | ProjectController.java |
| 3 | 🟡 MEDIUM | `PipelineOrchestrator.getTaskOrThrow` 抛 500 而非 404 | PipelineOrchestrator.java |
| 4 | 🟡 MEDIUM | `SupervisorAgent` 注入未使用的 `TaskRepository` | SupervisorAgent.java, AgentConfig.java |
| 5 | 🔴 CRITICAL | `TaskConsumer` MQ 消费异常时不更新任务状态，导致任务永久 PENDING | TaskConsumer.java |
| 6 | 🟡 MEDIUM | `Dockerfile` ENTRYPOINT 缺少 `exec`，信号无法到达 Java 进程 | Dockerfile |
| 7 | 🟡 MEDIUM | `JacksonConfig` 使用 `Visibility.ANY` 暴露所有私有字段 | JacksonConfig.java |
| 8 | 🟡 MEDIUM | `application-dev.yml` MySQL 端口与 docker-compose 不一致 | application-dev.yml |

---

## 二、Web 页面测试（Knife4j / Swagger UI）

### 2.1 启动服务

```bash
# 1. 启动基础设施（MySQL、Redis、RabbitMQ、pgvector）
docker compose up -d

# 2. 编译并启动应用
mvn clean package -DskipTests -pl devflow-agent-api -am
java -jar devflow-agent-api/target/devflow-agent-api-1.0.0-SNAPSHOT.jar --spring.profiles.active=dev
```

### 2.2 打开 Swagger 文档页面

浏览器访问：**http://localhost:8080/doc.html**

> 这是 Knife4j 提供的增强版 Swagger UI，左侧会列出所有 API 分组。

### 2.3 身份认证

所有 `/api/**` 端点需要 **HTTP Basic Auth**，在页面右上角点击 **"Authorize"** 按钮：

| 用户 | 密码(开发环境) | 角色 |
|------|---------------|------|
| `admin` | `devflow2024` | ADMIN - 全部权限 |
| `operator` | `devflow2024` | OPERATOR - 查看+审批 |

---

## 三、测试用例清单（按业务流程排列）

### 模块 A：健康检查 & 认证

#### TC-A1：健康检查（无需认证）

| 项目 | 内容 |
|------|------|
| **接口** | `GET /actuator/health` |
| **预期** | HTTP 200, `{"status": "UP"}` |
| **Swagger** | 在 "Actuator" 分组下，点击 "health" → "Execute" |
| **curl** | `curl http://localhost:8080/actuator/health` |

#### TC-A2：未认证访问 API

| 项目 | 内容 |
|------|------|
| **接口** | `GET /api/projects` (不带 Authorization header) |
| **预期** | HTTP 401 Unauthorized |
| **curl** | `curl -v http://localhost:8080/api/projects` |

#### TC-A3：错误密码访问 API

| 项目 | 内容 |
|------|------|
| **接口** | `GET /api/projects` + `Authorization: Basic YWRtaW46d3Jvbmc=` |
| **预期** | HTTP 401 |
| **curl** | `curl -u admin:wrongpassword http://localhost:8080/api/projects` |

---

### 模块 B：项目管理

#### TC-B1：创建项目（正常）

| 项目 | 内容 |
|------|------|
| **接口** | `POST /api/projects` |
| **Swagger** | "项目控制器" → POST `/api/projects` → "Try it out" |
| **请求体** | 见下方 |
| **预期** | HTTP 200, `code=200`, `data.name`="测试项目" |

```json
{
  "name": "测试项目",
  "repoUrl": "https://github.com/devflow/spring-boot-demo",
  "language": "Java",
  "framework": "Spring Boot",
  "description": "这是一个测试项目",
  "githubToken": "ghp_test123456"
}
```

**Swagger 操作步骤：**
1. 左侧展开 "项目控制器"
2. 点击 `POST /api/projects`
3. 点击 "Try it out" 按钮
4. 粘贴上面 JSON 到请求体
5. 点击 "Execute"
6. 检查响应 `code=200`，记录返回的 `data.id`

**curl：**
```bash
curl -X POST http://localhost:8080/api/projects \
  -H "Content-Type: application/json" \
  -u admin:devflow2024 \
  -d '{"name":"测试项目","repoUrl":"https://github.com/devflow/spring-boot-demo","language":"Java","framework":"Spring Boot","description":"测试项目"}'
```

#### TC-B2：创建项目 - 参数校验（空名称）

| 项目 | 内容 |
|------|------|
| **接口** | `POST /api/projects` |
| **请求体** | `{"name": "", "repoUrl": ""}` |
| **预期** | HTTP 400, `code=400`, 包含 "不能为空" |

```bash
curl -X POST http://localhost:8080/api/projects \
  -H "Content-Type: application/json" \
  -u admin:devflow2024 \
  -d '{"name":"","repoUrl":""}'
```

#### TC-B3：获取项目列表

| 项目 | 内容 |
|------|------|
| **接口** | `GET /api/projects` |
| **预期** | HTTP 200, `code=200`, `data` 为数组，至少含 1 条 |
| **Swagger** | "项目控制器" → `GET /api/projects` → Execute |

```bash
curl -u admin:devflow2024 http://localhost:8080/api/projects
```

#### TC-B4：获取项目详情

| 项目 | 内容 |
|------|------|
| **接口** | `GET /api/projects/{id}` |
| **预期** | HTTP 200, `data.name` 匹配创建时的名称 |
| **Swagger** | "项目控制器" → `GET /api/projects/{id}` → 输入 TC-B1 返回的 id |

```bash
curl -u admin:devflow2024 http://localhost:8080/api/projects/1
```

#### TC-B5：更新项目（验证 githubToken 不丢失修复）

| 项目 | 内容 |
|------|------|
| **接口** | `PUT /api/projects/{id}` |
| **请求体** | 更新描述和 githubToken |
| **预期** | HTTP 200, 更新成功，重新 GET 获取验证 `githubToken` 已更新 |
| **这一步验证修复 #2** |

```bash
# 更新项目
curl -X PUT http://localhost:8080/api/projects/1 \
  -H "Content-Type: application/json" \
  -u admin:devflow2024 \
  -d '{"name":"测试项目(已更新)","repoUrl":"https://github.com/devflow/spring-boot-demo","language":"Java","framework":"Spring Boot","description":"更新后的描述","githubToken":"ghp_updated123"}'

# 验证
curl -u admin:devflow2024 http://localhost:8080/api/projects/1
```

#### TC-B6：获取不存在的项目

| 项目 | 内容 |
|------|------|
| **接口** | `GET /api/projects/99999` |
| **预期** | HTTP 404, `code=404`, message 包含 "not found" |

---

### 模块 C：任务管理

#### TC-C1：创建任务（正常）

| 项目 | 内容 |
|------|------|
| **接口** | `POST /api/tasks` |
| **Swagger** | "任务控制器" → `POST /api/tasks` |
| **请求体** | 见下方 |
| **预期** | HTTP 200, `data.status`="PENDING", `data.currentPhase`="INIT" |

```json
{
  "projectId": 1,
  "issueNumber": 42,
  "issueTitle": "添加用户登录接口",
  "issueBody": "## 需求描述\n实现 JWT 认证接口，包含登录和刷新Token功能。\n\n## 验收标准\n- 正确验证用户名密码\n- 返回 JWT Token\n- Token 过期时间 24 小时"
}
```

```bash
curl -X POST http://localhost:8080/api/tasks \
  -H "Content-Type: application/json" \
  -u admin:devflow2024 \
  -d '{"projectId":1,"issueNumber":42,"issueTitle":"添加用户登录接口","issueBody":"实现JWT认证"}'
```

#### TC-C2：创建任务 - 参数校验（缺少必填字段）

| 项目 | 内容 |
|------|------|
| **接口** | `POST /api/tasks` |
| **请求体** | `{"projectId": null, "issueNumber": null, "issueTitle": ""}` |
| **预期** | HTTP 400 |

#### TC-C3：获取任务列表

| 项目 | 内容 |
|------|------|
| **接口** | `GET /api/tasks` 或 `GET /api/tasks?projectId=1` |
| **预期** | HTTP 200, `data` 为数组 |

#### TC-C4：获取任务详情

| 项目 | 内容 |
|------|------|
| **接口** | `GET /api/tasks/{id}` |
| **预期** | HTTP 200, 返回完整任务信息含 status/currentPhase |
| **Swagger** | "任务控制器" → `GET /api/tasks/{id}` |

#### TC-C5：获取任务进度

| 项目 | 内容 |
|------|------|
| **接口** | `GET /api/tasks/{id}/progress` |
| **预期** | HTTP 200, 返回 `taskId`, `currentPhase`, `status`, `totalTokens`, `totalDurationMs` |
| **Swagger** | "任务控制器" → `GET /api/tasks/{id}/progress` |

#### TC-C6：审批任务（需要任务处于 PAUSED 状态）

| 项目 | 内容 |
|------|------|
| **接口** | `POST /api/tasks/{id}/approve` |
| **前提** | 需要 AI 流水线运行到审批节点，任务状态为 PAUSED |
| **预期 (批准)** | HTTP 200 |
| **预期 (拒绝)** | HTTP 200，任务状态变为 FAILED |
| **预期 (非PAUSED)** | HTTP 422，提示 "not in PAUSED status" |

```bash
# 批准
curl -X POST http://localhost:8080/api/tasks/1/approve \
  -H "Content-Type: application/json" \
  -u admin:devflow2024 \
  -d '{"approver":"admin","comment":"方案通过","action":"APPROVED"}'

# 拒绝
curl -X POST http://localhost:8080/api/tasks/1/approve \
  -H "Content-Type: application/json" \
  -u admin:devflow2024 \
  -d '{"approver":"admin","comment":"需要重新设计","action":"REJECTED"}'
```

#### TC-C7：审批参数校验（空 action）

| 项目 | 内容 |
|------|------|
| **接口** | `POST /api/tasks/{id}/approve` |
| **请求体** | `{"approver": "", "action": ""}` |
| **预期** | HTTP 400 |

#### TC-C8：获取不存在的任务

| 项目 | 内容 |
|------|------|
| **接口** | `GET /api/tasks/99999` |
| **预期** | HTTP 404 |

---

### 模块 D：仪表盘

#### TC-D1：获取统计数据

| 项目 | 内容 |
|------|------|
| **接口** | `GET /api/dashboard/stats` |
| **Swagger** | "仪表盘控制器" → `GET /api/dashboard/stats` |
| **预期** | HTTP 200, 返回 `totalTasks`, `completedTasks`, `runningTasks`, `failedTasks`, `totalTokensUsed`, `avgDurationMs` |

```bash
curl -u admin:devflow2024 http://localhost:8080/api/dashboard/stats
```

---

### 模块 E：Webhook（GitHub 集成）

#### TC-E1：模拟 GitHub Issue Webhook（签名验证关闭时）

| 项目 | 内容 |
|------|------|
| **接口** | `POST /webhook/github` |
| **前提** | `github.webhook.secret` 未配置（开发环境） |
| **预期** | HTTP 200（若项目已注册），HTTP 404（若项目未注册） |

```bash
curl -X POST http://localhost:8080/webhook/github \
  -H "Content-Type: application/json" \
  -H "X-GitHub-Event: issues" \
  -d '{
    "action": "opened",
    "issue": {
      "number": 1,
      "title": "Test Issue from Webhook",
      "body": "测试 Webhook 创建的 Issue",
      "html_url": "https://github.com/devflow/test/issues/1"
    },
    "repository": {
      "html_url": "https://github.com/devflow/spring-boot-demo"
    }
  }'
```

#### TC-E2：非 Issue 事件被忽略

| 项目 | 内容 |
|------|------|
| **接口** | `POST /webhook/github` + `X-GitHub-Event: push` |
| **预期** | HTTP 200（忽略，不创建任务） |

#### TC-E3：无效 Webhook 签名被拒绝（生产模式）

> 需要先配置 `github.webhook.secret` 后测试

| 项目 | 内容 |
|------|------|
| **接口** | `POST /webhook/github` + 错误的 `X-Hub-Signature-256` |
| **预期** | HTTP 403 "Invalid signature" |

---

### 模块 F：资源清理

#### TC-F1：删除任务

```bash
curl -X DELETE -u admin:devflow2024 http://localhost:8080/api/tasks/1
```

#### TC-F2：删除项目

```bash
curl -X DELETE -u admin:devflow2024 http://localhost:8080/api/projects/1
```

---

## 四、自动化测试脚本

项目根目录有 Python 自动化测试脚本：

```bash
# 基本运行（需要服务在 localhost:8080 运行）
python test/test_api.py

# 指定 URL 和清理
python test/test_api.py --url http://localhost:8080 --cleanup

# 跳过健康检查
python test/test_api.py --skip-health
```

JUnit 测试（15 集成测试 + 10 单元测试）：

```bash
mvn test
```

---

## 五、Swagger 页面测试速查表

| 步骤 | 操作 | 页面位置 |
|------|------|---------|
| 1 | 打开文档 | `http://localhost:8080/doc.html` |
| 2 | 认证 | 右上角 **Authorize** → 输入 `admin` / `devflow2024` → Authorize → Close |
| 3 | 创建项目 | 左侧 "项目控制器" → `POST /api/projects` → Try it out → 填 JSON → Execute |
| 4 | 创建任务 | 左侧 "任务控制器" → `POST /api/tasks` → Try it out → 填 JSON → Execute |
| 5 | 查看进度 | 左侧 "任务控制器" → `GET /api/tasks/{id}/progress` → 填 id → Execute |
| 6 | 审批任务 | 左侧 "任务控制器" → `POST /api/tasks/{id}/approve` → 填 id + JSON → Execute |
| 7 | 查看仪表盘 | 左侧 "仪表盘控制器" → `GET /api/dashboard/stats` → Execute |

---

## 六、验证修复的关键测试

以下是验证本次修复的**关键测试点**：

| 修复 # | 验证方法 |
|--------|---------|
| 1 - AgentType 枚举 | 查看 `t_agent_execution` 表的 `agent_type` 字段值，应与 `AgentType` 枚举 code 一致 |
| 2 - githubToken 更新 | 执行 TC-B5，PUT 更新后 GET 确认 `githubToken` 字段已更新 |
| 3 - 404 错误码 | 执行 TC-B6/TC-C8，确认返回 `code=404` 而非 `code=500` |
| 4 - SupervisorAgent | 代码编译无警告（已移除未使用的 TaskRepository 注入） |
| 5 - TaskConsumer | 模拟 MQ 异常场景，确认任务被标记为 FAILED 而非永久 PENDING |
| 6 - Dockerfile | `docker compose up` 后 `docker stop` 能优雅关闭 Java 进程 |
| 7 - JacksonConfig | 确保 Spring 管理的响应不暴露不应该序列化的内部字段 |
| 8 - dev 端口 | 启动 dev profile 后能正常连接 docker compose 启动的 MySQL(3307) |
