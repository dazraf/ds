#!/bin/bash

# Stop script for DS - Bitemporal Data API Service

set -e  # Exit on error

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

echo -e "${BLUE}=================================================${NC}"
echo -e "${BLUE}  Stopping DS Service${NC}"
echo -e "${BLUE}=================================================${NC}"
echo ""

# Determine docker-compose command
if command -v docker-compose >/dev/null 2>&1; then
    DOCKER_COMPOSE="docker-compose"
else
    DOCKER_COMPOSE="docker compose"
fi

# Check if user wants to remove volumes
REMOVE_VOLUMES=false
if [ "$1" = "--clean" ] || [ "$1" = "-c" ]; then
    REMOVE_VOLUMES=true
    echo -e "${YELLOW}Warning: This will remove all data (volumes will be deleted)${NC}"
    echo -n "Are you sure? [y/N] "
    read -r response
    if [[ ! "$response" =~ ^[Yy]$ ]]; then
        echo -e "${BLUE}Cancelled${NC}"
        exit 0
    fi
    echo ""
fi

# Stop Docker services
echo -e "${YELLOW}Stopping Docker services...${NC}"

if [ "$REMOVE_VOLUMES" = true ]; then
    $DOCKER_COMPOSE down -v
    echo -e "${GREEN}✓ Services stopped and volumes removed${NC}"
else
    $DOCKER_COMPOSE down
    echo -e "${GREEN}✓ Services stopped (data preserved)${NC}"
fi

echo ""
echo -e "${BLUE}=================================================${NC}"
echo -e "${GREEN}All services stopped${NC}"
echo ""
if [ "$REMOVE_VOLUMES" = false ]; then
    echo -e "Data has been preserved. To remove all data, run:"
    echo -e "  ${BLUE}./stop.sh --clean${NC}"
    echo ""
fi
echo -e "To start again, run:"
echo -e "  ${BLUE}./start.sh${NC}"
echo -e "${BLUE}=================================================${NC}"
