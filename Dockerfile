# DevFlow Agent Dockerfile
# Build: mvn clean package -DskipTests -pl devflow-agent-api -am
# Run: docker compose up

FROM eclipse-temurin:17-jre-jammy

WORKDIR /app

# 复制构建产物
COPY devflow-agent-api/target/devflow-agent-api-*.jar app.jar

# 环境变量（运行时覆盖）
ENV JAVA_OPTS="-Xms512m -Xmx1024m"
ENV SPRING_PROFILES_ACTIVE=dev
ENV OPENAI_API_KEY=""
ENV OPENAI_BASE_URL="https://api.deepseek.com"
ENV OPENAI_MODEL="deepseek-chat"

EXPOSE 8080

ENTRYPOINT ["sh", "-c", "exec java ${JAVA_OPTS} -jar app.jar"]
