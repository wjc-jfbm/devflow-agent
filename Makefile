# DevFlow Agent Makefile
# 常用命令快捷方式，避免每次记忆 Maven/Docker 参数

.PHONY: help build test clean run-dev run-docker docker-up docker-down docker-up-infra docker-rebuild logs lint

.DEFAULT_GOAL := help

help: ## 显示可用命令
	@grep -E '^[a-zA-Z_-]+:.*?## .*$$' $(MAKEFILE_LIST) | sort | \
	awk 'BEGIN {FS = ":.*?## "}; {printf "\033[36m%-22s\033[0m %s\n", $$1, $$2}'

# ========== 构建 ==========

build: ## 编译项目（跳过测试）
	mvn clean package -DskipTests -pl devflow-agent-api -am

build-with-tests: ## 编译项目（含测试）
	mvn clean package -pl devflow-agent-api -am

# ========== 测试 ==========

test: ## 运行 JUnit 单元测试
	mvn test

test-api: ## 运行 Python API 自动化测试（需服务已在运行）
	python test/test_api.py --cleanup

test-api-keep: ## 运行 Python API 测试（不清理数据）
	python test/test_api.py

# ========== 本地开发 ==========

run-dev: build ## 本地开发模式启动（需先启动 docker-up-infra）
	java -jar devflow-agent-api/target/devflow-agent-api-1.0.0-SNAPSHOT.jar --spring.profiles.active=dev

# ========== Docker ==========

docker-up: build ## 编译 + 启动所有容器
	docker compose up -d --build

docker-up-infra: ## 仅启动基础设施（MySQL/Redis/RabbitMQ/pgvector，不启动 App）
	docker compose up -d mysql redis rabbitmq pgvector

docker-down: ## 停止并删除所有容器
	docker compose down

docker-down-clean: ## 停止容器 + 删除数据卷（⚠ 会清空数据库）
	docker compose down -v

docker-rebuild: ## 强制重建应用镜像
	docker compose build --no-cache app
	docker compose up -d app

docker-logs: ## 查看应用日志
	docker compose logs -f app

docker-logs-all: ## 查看所有容器日志
	docker compose logs -f

# ========== 代码质量 ==========

lint: ## 检查代码风格
	mvn checkstyle:check

# ========== 工具 ==========

clean: ## 清理构建产物
	mvn clean
	docker compose down 2>/dev/null || true

api-docs: ## 打开 Swagger 文档（Windows）
	start http://localhost:8080/doc.html
