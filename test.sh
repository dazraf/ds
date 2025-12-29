#!/bin/bash

# Test script for DS - Bitemporal Data API Service
# Demonstrates API functionality with example commands

set -e  # Exit on error

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
CYAN='\033[0;36m'
NC='\033[0m' # No Color

BASE_URL="http://localhost:8080"

echo -e "${BLUE}=================================================${NC}"
echo -e "${BLUE}  DS API Test Suite${NC}"
echo -e "${BLUE}=================================================${NC}"
echo ""

# Function to run command and show output
run_command() {
    local description=$1
    local command=$2

    echo -e "${CYAN}▶ ${description}${NC}"
    echo -e "${YELLOW}  Command: ${command}${NC}"
    echo ""

    eval "$command"
    local exit_code=$?

    echo ""
    if [ $exit_code -eq 0 ]; then
        echo -e "${GREEN}✓ Success${NC}"
    else
        echo -e "${RED}✗ Failed${NC}"
    fi
    echo -e "${BLUE}─────────────────────────────────────────────────${NC}"
    echo ""

    sleep 1
}

# Check if service is running
echo -e "${YELLOW}Checking if service is running...${NC}"
if ! curl -s "${BASE_URL}/api/health" >/dev/null 2>&1; then
    echo -e "${RED}Error: Service is not running at ${BASE_URL}${NC}"
    echo -e "${YELLOW}Please start the service first with: ./start.sh${NC}"
    exit 1
fi
echo -e "${GREEN}✓ Service is running${NC}"
echo ""

# Create test file
echo "Hello from DS Bitemporal API!" > /tmp/test-ds.txt
echo "Version 2 of the document" > /tmp/test-ds-v2.txt

echo -e "${BLUE}=================================================${NC}"
echo -e "${BLUE}  Running API Tests${NC}"
echo -e "${BLUE}=================================================${NC}"
echo ""

# Test 1: Health Check
run_command \
    "Test 1: Health Check" \
    "curl -s ${BASE_URL}/api/health | jq"

# Test 2: Create Namespace
run_command \
    "Test 2: Create Namespace" \
    "curl -s -X POST ${BASE_URL}/api/v1/namespaces \
      -H 'Content-Type: application/json' \
      -d '{\"name\": \"demo-project\"}' | jq"

# Test 3: List Namespaces
run_command \
    "Test 3: List All Namespaces" \
    "curl -s ${BASE_URL}/api/v1/namespaces | jq"

# Test 4: Get Specific Namespace
run_command \
    "Test 4: Get Specific Namespace" \
    "curl -s ${BASE_URL}/api/v1/namespaces/demo-project | jq"

# Test 5: Upload Data (Version 1)
run_command \
    "Test 5: Upload Data (Version 1)" \
    "curl -s -X POST ${BASE_URL}/api/v1/namespaces/demo-project/branches/main/data/documents/readme \
      -F 'file=@/tmp/test-ds.txt' \
      -F 'metadata={\"tags\": [\"important\", \"v1\"]}' | jq"

# Test 6: Download Data
run_command \
    "Test 6: Download Data" \
    "curl -s ${BASE_URL}/api/v1/namespaces/demo-project/branches/main/data/documents/readme"

# Test 7: Get Metadata
run_command \
    "Test 7: Get Metadata (without binary data)" \
    "curl -s ${BASE_URL}/api/v1/namespaces/demo-project/branches/main/data/documents/readme/metadata | jq"

# Test 8: Upload Version 2
run_command \
    "Test 8: Upload New Version (Version 2)" \
    "curl -s -X POST ${BASE_URL}/api/v1/namespaces/demo-project/branches/main/data/documents/readme \
      -F 'file=@/tmp/test-ds-v2.txt' \
      -F 'metadata={\"tags\": [\"v2\"]}' | jq"

# Test 9: Get History
run_command \
    "Test 9: Get Version History (should show 2 versions)" \
    "curl -s ${BASE_URL}/api/v1/namespaces/demo-project/branches/main/data/documents/readme/history | jq"

# Test 10: List Documents
run_command \
    "Test 10: List All Documents" \
    "curl -s ${BASE_URL}/api/v1/namespaces/demo-project/branches/main/data/documents | jq"

# Test 11: Add Tags
run_command \
    "Test 11: Add More Tags" \
    "curl -s -X POST ${BASE_URL}/api/v1/namespaces/demo-project/branches/main/data/documents/readme/tags \
      -H 'Content-Type: application/json' \
      -d '{\"tags\": [\"production\", \"tested\"]}' | jq"

# Test 12: Get Tags
run_command \
    "Test 12: Get All Tags" \
    "curl -s ${BASE_URL}/api/v1/namespaces/demo-project/branches/main/data/documents/readme/tags | jq"

# Test 13: Filter by Tag
run_command \
    "Test 13: Filter Documents by Tag" \
    "curl -s '${BASE_URL}/api/v1/namespaces/demo-project/branches/main/data/documents?tag=production' | jq"

# Test 14: Upload Another Document
run_command \
    "Test 14: Upload Another Document" \
    "curl -s -X POST ${BASE_URL}/api/v1/namespaces/demo-project/branches/main/data/documents/changelog \
      -F 'file=@/tmp/test-ds.txt' \
      -F 'metadata={\"tags\": [\"v1\"]}' | jq"

# Test 15: List All Documents Again
run_command \
    "Test 15: List All Documents (should show 2 documents)" \
    "curl -s ${BASE_URL}/api/v1/namespaces/demo-project/branches/main/data/documents | jq"

# Test 16: Delete Tag
run_command \
    "Test 16: Delete a Specific Tag" \
    "curl -s -X DELETE ${BASE_URL}/api/v1/namespaces/demo-project/branches/main/data/documents/readme/tags/v1"

# Test 17: Soft Delete Document
run_command \
    "Test 17: Soft Delete Document" \
    "curl -s -X DELETE ${BASE_URL}/api/v1/namespaces/demo-project/branches/main/data/documents/changelog"

# Test 18: Verify Deletion
run_command \
    "Test 18: Verify Document is Deleted (should return 404)" \
    "curl -s -w '\nHTTP Status: %{http_code}\n' ${BASE_URL}/api/v1/namespaces/demo-project/branches/main/data/documents/changelog"

# Cleanup
rm -f /tmp/test-ds.txt /tmp/test-ds-v2.txt

echo ""
echo -e "${BLUE}=================================================${NC}"
echo -e "${GREEN}  All Tests Complete!${NC}"
echo -e "${BLUE}=================================================${NC}"
echo ""
echo -e "Additional things to try:"
echo -e "  ${CYAN}•${NC} View traces in Jaeger: ${BLUE}http://localhost:16686${NC}"
echo -e "  ${CYAN}•${NC} Explore API docs: ${BLUE}http://localhost:8080/swagger${NC}"
echo -e "  ${CYAN}•${NC} Check PostgreSQL databases: ${BLUE}docker exec -it ds-postgres psql -U postgres -c '\\l'${NC}"
echo ""
