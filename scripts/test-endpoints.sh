#!/bin/bash

# Script to test Vokab Server endpoints
BASE_URL="${BASE_URL:-http://localhost:8080/api/v1}"

echo "🧪 Testing Vokab Server Endpoints"
echo "Base URL: $BASE_URL"
echo ""

# Colors for output
GREEN='\033[0;32m'
RED='\033[0;31m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

# Test function
test_endpoint() {
    local method=$1
    local endpoint=$2
    local description=$3
    local data=$4
    local auth_header=$5
    
    echo -n "Testing: $description... "
    
    if [ -n "$data" ]; then
        if [ -n "$auth_header" ]; then
            response=$(curl -s -w "\n%{http_code}" -X $method "$BASE_URL$endpoint" \
                -H "Content-Type: application/json" \
                -H "Authorization: Bearer $auth_header" \
                -d "$data")
        else
            response=$(curl -s -w "\n%{http_code}" -X $method "$BASE_URL$endpoint" \
                -H "Content-Type: application/json" \
                -d "$data")
        fi
    else
        if [ -n "$auth_header" ]; then
            response=$(curl -s -w "\n%{http_code}" -X $method "$BASE_URL$endpoint" \
                -H "Authorization: Bearer $auth_header")
        else
            response=$(curl -s -w "\n%{http_code}" -X $method "$BASE_URL$endpoint")
        fi
    fi
    
    http_code=$(echo "$response" | tail -n1)
    body=$(echo "$response" | sed '$d')
    
    if [ "$http_code" -ge 200 ] && [ "$http_code" -lt 300 ]; then
        echo -e "${GREEN}✓ PASS${NC} (HTTP $http_code)"
    elif [ "$http_code" -eq 401 ]; then
        echo -e "${YELLOW}⚠ AUTH REQUIRED${NC} (HTTP $http_code)"
    else
        echo -e "${RED}✗ FAIL${NC} (HTTP $http_code)"
    fi
    
    if [ "$VERBOSE" = "true" ]; then
        echo "   Response: $body" | head -c 200
        echo ""
    fi
}

echo "=== Public Endpoints ==="
echo ""

test_endpoint "GET" "/health" "Health Check"
test_endpoint "GET" "/version" "Version Info"

echo ""
echo "=== Authentication Endpoints (require valid tokens) ==="
echo ""

test_endpoint "POST" "/auth/google" "Google OAuth Login" '{"idToken":"test-token"}'
test_endpoint "POST" "/auth/refresh" "Refresh Token" '{"refreshToken":"test-token"}'

echo ""
echo "=== Protected Endpoints (require authentication) ==="
echo ""

if [ -n "$ACCESS_TOKEN" ]; then
    echo "Using provided ACCESS_TOKEN"
    test_endpoint "GET" "/users/me" "Get Current User" "" "$ACCESS_TOKEN"
    test_endpoint "GET" "/subscriptions" "Get Subscriptions" "" "$ACCESS_TOKEN"
    test_endpoint "GET" "/subscriptions/active" "Get Active Subscription" "" "$ACCESS_TOKEN"
    test_endpoint "GET" "/notifications/tokens" "Get Token Count" "" "$ACCESS_TOKEN"
    test_endpoint "GET" "/ai/health" "AI Service Health" "" "$ACCESS_TOKEN"
else
    echo -e "${YELLOW}⚠ Skipping protected endpoints (no ACCESS_TOKEN provided)${NC}"
    echo "   Run with: ACCESS_TOKEN=your_jwt_token ./scripts/test-endpoints.sh"
fi

echo ""
echo "=== Summary ==="
echo "Server is responding to requests."
echo ""
echo "To test authenticated endpoints:"
echo "1. Get a valid Google ID token"
echo "2. Call POST /auth/google with the token"
echo "3. Use the returned accessToken:"
echo "   export ACCESS_TOKEN=your_jwt_token"
echo "   ./scripts/test-endpoints.sh"
echo ""
echo "For verbose output: VERBOSE=true ./scripts/test-endpoints.sh"
echo ""

