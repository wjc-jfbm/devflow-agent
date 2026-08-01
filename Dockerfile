# ============================================
# DevFlow Agent Dockerfile (Production)
# ============================================
# 构建步骤（在项目根目录执行）：
#   1. mvn clean package -DskipTests -pl devflow-agent-api -am
#   2. docker compose build
#   3. docker compose up -d
#   4. 或一键部署: ./deploy.sh
# ============================================

FROM eclipse-temurin:17-jre-jammy

WORKDIR /app

# 创建非 root 用户（安全最佳实践）
RUN groupadd -r devflow && useradd -r -g devflow -m -d /app devflow

# 复制构建产物
COPY devflow-agent-api/target/devflow-agent-api-*.jar app.jar

# 创建日志和数据目录
RUN mkdir -p /data/devflow/logs /data/devflow/workspace && \
    chown -R devflow:devflow /app /data/devflow

# JVM 参数（可通过环境变量覆盖）
# 默认值适配 8G 服务器，小机器可设: JAVA_OPTS="-Xms256m -Xmx512m -XX:+UseG1GC -Djdk.httpclient.connectionPoolSize=20"
# G1GC: 低延迟垃圾回收器
# MaxGCPauseMillis=200: GC 暂停不超过 200ms
# HeapDumpOnOutOfMemoryError: OOM 时自动 dump
# jdk.httpclient.connectionPoolSize: JDK HttpClient 连接池大小（匹配 AI API 并发量）
ENV JAVA_OPTS="-Xms512m -Xmx1g -XX:+UseG1GC -XX:MaxGCPauseMillis=200 -XX:+HeapDumpOnOutOfMemoryError -XX:HeapDumpPath=/tmp -Djdk.httpclient.connectionPoolSize=50"

# Spring 环境变量（可在 docker-compose.yml 或 .env 中覆盖）
ENV SPRING_PROFILES_ACTIVE=docker

EXPOSE 8080

# 数据卷（日志持久化）
VOLUME /data/devflow/logs

# 健康检查
HEALTHCHECK --interval=30s --timeout=5s --start-period=90s --retries=3 \
  CMD wget -qO- http://localhost:8080/actuator/health || exit 1

# 使用非 root 用户运行
USER devflow

# exec 确保信号正确传递到 Java 进程（优雅关闭）
ENTRYPOINT ["sh", "-c", "exec java ${JAVA_OPTS} -jar app.jar"]
