# ============================================
# DevFlow Agent Dockerfile
# ============================================
# 构建步骤（在项目根目录执行）：
#   1. mvn clean package -DskipTests -pl devflow-agent-api -am
#   2. docker compose build
#   3. docker compose up -d
# ============================================

FROM eclipse-temurin:17-jre-jammy

WORKDIR /app

# 复制构建产物
COPY devflow-agent-api/target/devflow-agent-api-*.jar app.jar

# 环境变量（可在 docker-compose.yml 或 .env 中覆盖）
ENV JAVA_OPTS="-Xms512m -Xmx1024m"
ENV SPRING_PROFILES_ACTIVE=docker

EXPOSE 8080

# 使用 exec 确保信号能正确传递到 Java 进程（优雅关闭）
ENTRYPOINT ["sh", "-c", "exec java ${JAVA_OPTS} -jar app.jar"]
