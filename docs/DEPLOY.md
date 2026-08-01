# 部署指南

> 从零到生产就绪的完整部署步骤。

---

## 前置条件

准备一台服务器（最低 **4核8G**），安装：

| 软件 | 版本 | 安装方式 |
|------|------|----------|
| Docker | 24+ | `curl -fsSL https://get.docker.com \| sh` |
| Java | 17+ | `apt install openjdk-17-jdk` (Ubuntu) |
| Maven | 3.8+ | `apt install maven` (Ubuntu) |

还需要一个 **DeepSeek API Key**（[免费注册](https://platform.deepseek.com/) 送 500 万 token）。

---

## 快速部署（Docker Compose）

```bash
# 1. 克隆项目
git clone https://github.com/wjc-jfbm/devflow-agent.git
cd devflow-agent

# 2. 配置环境变量
cp .env.example .env
# 编辑 .env，必须填以下 3 项：
#   OPENAI_API_KEY=sk-xxxxx         你的 DeepSeek Key
#   DEVFLOW_ADMIN_PASSWORD=xxxxx    管理员密码（自己设一个强的）
#   DEVFLOW_OPERATOR_PASSWORD=xxxxx 操作员密码

# 3. 一键部署
chmod +x deploy.sh
./deploy.sh
```

脚本会自动完成：编译项目 → 生成 TLS 证书 → 构建镜像 → 启动所有服务 → 健康检查。大约 **2-3 分钟**。

---

## 生产环境部署

### 使用 docker-compose.prod.yml

```bash
# 生产环境覆盖配置（隐藏内部端口，只暴露 80/443）
docker compose -f docker-compose.yml -f docker-compose.prod.yml up -d
```

### 申请真实 HTTPS 证书

```bash
# 方式一：使用 Let's Encrypt（推荐）
certbot certonly --standalone -d your-domain.com

# 把证书复制到 nginx/ssl/
cp /etc/letsencrypt/live/your-domain.com/fullchain.pem nginx/ssl/cert.pem
cp /etc/letsencrypt/live/your-domain.com/privkey.pem nginx/ssl/key.pem

# 重启 Nginx
docker compose restart nginx
```

### 应用 HTTPS 后

打开 `https://你的域名/doc.html`，用户名 `admin`，密码为你设置的 `DEVFLOW_ADMIN_PASSWORD`。

---

## 配置 GitHub Webhook（让 Issue 自动触发）

1. 进入 GitHub 仓库 → **Settings** → **Webhooks** → **Add webhook**
2. **Payload URL**: `https://你的域名/webhook/github`
3. **Content type**: `application/json`
4. **Secret**: 填 `.env` 里的 `GITHUB_WEBHOOK_SECRET`
5. **Events**: 选择 `Issues`
6. 点击 **Add webhook**

之后每当有人提 Issue，DevFlow 会自动分析并生成代码。

---

## 部署后使用

打开 Swagger 文档（`https://你的域名/doc.html`），右上角 **Authorize** 登录：

| 步骤 | 接口 | 说明 |
|------|------|------|
| 1 | `POST /api/projects` | 创建项目（填 GitHub 仓库信息） |
| 2 | `POST /api/tasks` | 创建任务，自动触发 AI 工作流 |
| 3 | `GET /api/tasks/{id}/progress` | 查看 AI 处理进度和阶段输出 |
| 4 | `POST /api/tasks/{id}/approve` | 架构设计完成后审批（action=APPROVED） |
| 5 | `POST /api/tasks/{id}/approve` | 代码审查完成后再次审批 |
| 6 | 查看 GitHub | AI 自动创建了 Pull Request |

---

## 环境变量完整说明

全部通过 `.env` 文件或环境变量注入：

| 变量 | 必填 | 默认值 | 说明 |
|------|------|--------|------|
| `OPENAI_API_KEY` | ✅ | - | DeepSeek 或 OpenAI API Key |
| `OPENAI_BASE_URL` | - | `https://api.deepseek.com` | API 地址 |
| `OPENAI_MODEL` | - | `deepseek-chat` | 模型名称 |
| `GITHUB_TOKEN` | 创建 PR 时需要 | - | GitHub Personal Access Token |
| `GITHUB_WEBHOOK_SECRET` | Webhook 需要 | - | HMAC 签名密钥 |
| `DEVFLOW_ADMIN_PASSWORD` | ✅ prod | - | 管理员密码 |
| `DEVFLOW_OPERATOR_PASSWORD` | ✅ prod | - | 操作员密码 |
| `SPRING_PROFILES_ACTIVE` | - | `docker` | Spring Profile |
| `JAVA_OPTS` | - | `-Xms512m -Xmx1g` | JVM 参数 |

---

## 多环境切换

| Profile | 配置文件 | 用途 |
|---------|----------|------|
| `dev` | `application-dev.yml` | 本地开发，连本地中间件，DEBUG 日志 |
| `docker` | `application-docker.yml` | Docker 部署，通过服务名连接 |
| `prod` | `application-prod.yml` | 生产环境，HikariCP 调优，关闭 SQL 日志 |

```bash
# 切换环境
SPRING_PROFILES_ACTIVE=prod docker compose up -d
```

---

## 故障排查

### 容器启动失败

```bash
# 查看所有容器状态
docker compose ps

# 查看具体容器日志
docker compose logs app
docker compose logs mysql
```

### AI 调用失败

检查 `agent_execution` 表的 `error_msg` 字段，或查看应用日志：

```bash
docker compose logs app | grep -i error
```

常见原因：API Key 无效、DeepSeek 限流（429）、网络超时。

### 任务卡住不动

1. 调 `GET /api/tasks/{id}` 查看 `status` 和 `currentPhase`
2. 检查 Redis 中 `task:{id}:*` 缓存的阶段输出
3. 查看 `agent_execution` 表对应 task 的记录
