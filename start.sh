#!/bin/bash

# Start script for DS - Bitemporal Data API Service
# This script starts all dependencies and the application

set -e  # Exit on error

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

echo -e "${BLUE}=================================================${NC}"
echo -e "${BLUE}  DS - Bitemporal Data API Service${NC}"
echo -e "${BLUE}=================================================${NC}"
echo ""

# Function to check if command exists
command_exists() {
    command -v "$1" >/dev/null 2>&1
}

# Check prerequisites
echo -e "${YELLOW}[1/6] Checking prerequisites...${NC}"
if ! command_exists docker; then
    echo -e "${RED}Error: docker is not installed${NC}"
    exit 1
fi

if ! command_exists docker-compose && ! docker compose version >/dev/null 2>&1; then
    echo -e "${RED}Error: docker-compose is not installed${NC}"
    exit 1
fi

# Determine docker-compose command
if command_exists docker-compose; then
    DOCKER_COMPOSE="docker-compose"
else
    DOCKER_COMPOSE="docker compose"
fi

echo -e "${GREEN}✓ Prerequisites check passed${NC}"
echo ""

# Start docker-compose services
echo -e "${YELLOW}[2/6] Starting Docker services (PostgreSQL, OpenTelemetry, Jaeger)...${NC}"
$DOCKER_COMPOSE up -d

if [ $? -ne 0 ]; then
    echo -e "${RED}Error: Failed to start Docker services${NC}"
    exit 1
fi

echo -e "${GREEN}✓ Docker services started${NC}"
echo ""

# Wait for PostgreSQL to be healthy
echo -e "${YELLOW}[3/6] Waiting for PostgreSQL to be ready...${NC}"
echo -n "  "

MAX_WAIT=30
WAIT_COUNT=0
while [ $WAIT_COUNT -lt $MAX_WAIT ]; do
    if docker exec ds-postgres pg_isready -U postgres >/dev/null 2>&1; then
        echo ""
        echo -e "${GREEN}✓ PostgreSQL is ready${NC}"
        break
    fi
    echo -n "."
    sleep 1
    WAIT_COUNT=$((WAIT_COUNT + 1))
done

if [ $WAIT_COUNT -eq $MAX_WAIT ]; then
    echo ""
    echo -e "${RED}Error: PostgreSQL did not become ready in time${NC}"
    exit 1
fi
echo ""

# Create registry database if it doesn't exist
echo -e "${YELLOW}[4/6] Setting up registry database...${NC}"

DB_EXISTS=$(docker exec ds-postgres psql -U postgres -tAc "SELECT 1 FROM pg_database WHERE datname='ds_registry'" 2>/dev/null || echo "")

if [ "$DB_EXISTS" = "1" ]; then
    echo -e "${GREEN}✓ Registry database already exists${NC}"
else
    echo "  Creating ds_registry database..."
    docker exec ds-postgres psql -U postgres -c "CREATE DATABASE ds_registry;" >/dev/null 2>&1
    if [ $? -eq 0 ]; then
        echo -e "${GREEN}✓ Registry database created${NC}"
    else
        echo -e "${RED}Error: Failed to create registry database${NC}"
        exit 1
    fi
fi
echo ""

# Build the application
echo -e "${YELLOW}[5/6] Building application...${NC}"
./gradlew build -x test --quiet

if [ $? -ne 0 ]; then
    echo -e "${RED}Error: Build failed${NC}"
    exit 1
fi

echo -e "${GREEN}✓ Build successful${NC}"
echo ""

# Start the application
echo -e "${YELLOW}[6/6] Starting application...${NC}"
echo ""
echo -e "${BLUE}=================================================${NC}"
echo -e "${GREEN}Application starting on http://localhost:8080${NC}"
echo ""
echo -e "Available endpoints:"
echo -e "  ${BLUE}•${NC} Health Check:  http://localhost:8080/api/health"
echo -e "  ${BLUE}•${NC} Swagger UI:    http://localhost:8080/swagger"
echo -e "  ${BLUE}•${NC} OpenAPI Spec:  http://localhost:8080/openapi.json"
echo -e "  ${BLUE}•${NC} Jaeger UI:     http://localhost:16686"
echo ""
echo -e "Quick start commands:"
echo -e "  ${BLUE}# Create namespace${NC}"
echo -e "  curl -X POST http://localhost:8080/api/v1/namespaces \\"
echo -e "    -H \"Content-Type: application/json\" \\"
echo -e "    -d '{\"name\": \"my-project\"}'"
echo ""
echo -e "  ${BLUE}# Upload data${NC}"
echo -e "  curl -X POST http://localhost:8080/api/v1/namespaces/my-project/branches/main/data/documents/test \\"
echo -e "    -F \"file=@test.txt\" \\"
echo -e "    -F 'metadata={\"tags\": [\"v1\"]}'"
echo ""
echo -e "Press Ctrl+C to stop the application"
echo -e "${BLUE}=================================================${NC}"
echo ""

# Run the application
./gradlew run

# This will only execute if gradlew run exits
EXIT_CODE=$?

echo ""
echo -e "${YELLOW}Application stopped${NC}"
echo ""
echo -e "To stop Docker services, run:"
echo -e "  ${BLUE}docker-compose down${NC}"
echo ""

exit $EXIT_CODE
