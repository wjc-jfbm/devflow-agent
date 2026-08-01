#!/bin/bash
# ============================================
# DevFlow Agent — 一键部署脚本
# 使用方式: chmod +x deploy.sh && ./deploy.sh
# ============================================
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
cd "$SCRIPT_DIR"

RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

echo -e "${GREEN}╔══════════════════════════════════════════╗${NC}"
echo -e "${GREEN}║   DevFlow Agent — Production Deploy      ║${NC}"
echo -e "${GREEN}╚══════════════════════════════════════════╝${NC}"
echo ""

# ---- Step 1: Check prerequisites ----
echo -e "${YELLOW}[1/6] Checking prerequisites...${NC}"

if ! command -v docker &>/dev/null; then
    echo -e "${RED}ERROR: docker is required but not found${NC}"
    echo "  Install: https://docs.docker.com/engine/install/"
    exit 1
fi

if ! docker compose version &>/dev/null; then
    echo -e "${RED}ERROR: docker compose (v2) is required${NC}"
    exit 1
fi

if ! command -v java &>/dev/null; then
    echo -e "${RED}ERROR: java 17+ is required for building${NC}"
    echo "  Install: https://adoptium.net/"
    exit 1
fi

echo "  docker:        $(docker --version)"
echo "  docker compose: $(docker compose version --short 2>/dev/null || echo 'v2.x')"
echo "  java:           $(java -version 2>&1 | head -1)"

# ---- Step 2: Check .env file ----
echo -e "${YELLOW}[2/6] Checking environment configuration...${NC}"

if [ ! -f .env ]; then
    if [ -f .env.production ]; then
        echo "  .env not found. Copying .env.production as template..."
        cp .env.production .env
        echo -e "${RED}  ⚠  Please edit .env with your production secrets before proceeding!${NC}"
        echo -e "${RED}  ⚠  At minimum, set OPENAI_API_KEY and all passwords.${NC}"
        echo ""
        echo "  Run this script again after editing .env"
        exit 1
    else
        echo -e "${RED}ERROR: No .env or .env.production found${NC}"
        echo "  Copy .env.example to .env and fill in your configuration"
        exit 1
    fi
fi

# Validate critical env vars
source .env 2>/dev/null || true
CRITICAL_VARS=("OPENAI_API_KEY" "DEVFLOW_ADMIN_PASSWORD" "DEVFLOW_OPERATOR_PASSWORD")
for var in "${CRITICAL_VARS[@]}"; do
    val="${!var:-}"
    if [ -z "$val" ] || [[ "$val" == *"PLACEHOLDER"* ]] || [[ "$val" == *"change-me"* ]] || [[ "$val" == *"your-api-key"* ]]; then
        echo -e "${RED}ERROR: $var is not set or still has placeholder value in .env${NC}"
        exit 1
    fi
done
echo "  .env validated ✓"

# ---- Step 3: Build application ----
echo -e "${YELLOW}[3/6] Building application...${NC}"
mvn clean package -DskipTests -pl devflow-agent-api -am -q
echo "  Build complete ✓"

# ---- Step 4: Generate TLS certificate (if missing) ----
echo -e "${YELLOW}[4/6] Checking TLS certificate...${NC}"
if [ ! -f nginx/ssl/cert.pem ] || [ ! -f nginx/ssl/key.pem ]; then
    echo "  Generating self-signed TLS certificate..."
    mkdir -p nginx/ssl
    openssl req -x509 -nodes -days 365 -newkey rsa:2048 \
        -keyout nginx/ssl/key.pem \
        -out nginx/ssl/cert.pem \
        -subj "/CN=devflow/O=DevFlow/C=CN" 2>/dev/null
    echo -e "  ${YELLOW}⚠  Self-signed cert generated. Replace with real cert for production use.${NC}"
    echo "     Place your real cert at: nginx/ssl/cert.pem"
    echo "     Place your real key  at: nginx/ssl/key.pem"
else
    echo "  TLS certificate found ✓"
fi

# ---- Step 5: Docker Compose ----
echo -e "${YELLOW}[5/6] Starting services...${NC}"
docker compose build app --quiet
docker compose up -d --wait 2>/dev/null || docker compose up -d
echo "  Services started ✓"

# ---- Step 6: Health check ----
echo -e "${YELLOW}[6/6] Waiting for application to become healthy...${NC}"
MAX_WAIT=120
WAITED=0
while [ $WAITED -lt $MAX_WAIT ]; do
    if curl -sf http://localhost:8080/actuator/health > /dev/null 2>&1; then
        echo -e "  ${GREEN}Application is healthy!${NC}"
        break
    fi
    echo -n "."
    sleep 5
    WAITED=$((WAITED + 5))
done

if [ $WAITED -ge $MAX_WAIT ]; then
    echo ""
    echo -e "${YELLOW}⚠  Health check timed out. Check logs: docker compose logs app${NC}"
fi

# ---- Done ----
echo ""
echo -e "${GREEN}╔══════════════════════════════════════════╗${NC}"
echo -e "${GREEN}║          Deployment Complete!             ║${NC}"
echo -e "${GREEN}╠══════════════════════════════════════════╣${NC}"
echo -e "${GREEN}║                                          ║${NC}"
echo -e "${GREEN}║  Health:  http://localhost:8080/actuator/health${NC}"
echo -e "${GREEN}║  API:     https://localhost/api/tasks     ${NC}"
echo -e "${GREEN}║  Swagger: https://localhost/doc.html      ${NC}"
echo -e "${GREEN}║  RabbitMQ: http://localhost:15672         ${NC}"
echo -e "${GREEN}║                                          ║${NC}"
echo -e "${GREEN}║  Login:   admin / (your password)         ${NC}"
echo -e "${GREEN}║                                          ║${NC}"
echo -e "${GREEN}╚══════════════════════════════════════════╝${NC}"
echo ""
echo "  View logs:     docker compose logs -f app"
echo "  Stop services: docker compose down"
