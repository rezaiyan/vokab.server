#!/bin/bash

# Deployment Verification Script for Vokab Server
# This script verifies that the server is properly deployed and configured

set -e

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

# Server URL
SERVER_URL="${SERVER_URL:-https://vokab-server-hcsu.onrender.com}"
API_BASE="${SERVER_URL}/api/v1"

echo "🔍 Verifying Vokab Server Deployment"
echo "Server: $SERVER_URL"
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo

# Function to check endpoint
check_endpoint() {
    local name=$1
    local url=$2
    local expected_status=$3
    
    echo -n "Checking $name... "
    
    response=$(curl -s -o /dev/null -w "%{http_code}" "$url" 2>&1)
    
    if [ "$response" = "$expected_status" ]; then
        echo -e "${GREEN}✓ OK${NC} (HTTP $response)"
        return 0
    else
        echo -e "${RED}✗ FAILED${NC} (HTTP $response, expected $expected_status)"
        return 1
    fi
}

# Function to check endpoint with JSON response
check_json_endpoint() {
    local name=$1
    local url=$2
    local expected_field=$3
    
    echo -n "Checking $name... "
    
    response=$(curl -s "$url" 2>&1)
    
    if echo "$response" | grep -q "$expected_field"; then
        echo -e "${GREEN}✓ OK${NC}"
        echo "  Response: $response"
        return 0
    else
        echo -e "${RED}✗ FAILED${NC}"
        echo "  Response: $response"
        return 1
    fi
}

# Track overall status
FAILED=0

# 1. Health Check
echo "📊 Health Checks"
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
check_json_endpoint "Health endpoint" "$API_BASE/health" "status" || ((FAILED++))
echo

# 2. Authentication Endpoints
echo "🔐 Authentication Endpoints"
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"

# Test Google auth endpoint (should reject with 400 for invalid request)
echo -n "Checking Google auth endpoint... "
response=$(curl -s -X POST "$API_BASE/auth/google" \
    -H "Content-Type: application/json" \
    -d '{"idToken":"test"}' 2>&1)

if echo "$response" | grep -q "success"; then
    echo -e "${GREEN}✓ OK${NC} (endpoint accessible)"
    echo "  Response: $response"
else
    echo -e "${RED}✗ FAILED${NC} (endpoint not responding)"
    echo "  Response: $response"
    ((FAILED++))
fi
echo

# 3. Protected Endpoints (should return 401 without auth)
echo "🔒 Protected Endpoints"
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
check_endpoint "Users endpoint (protected)" "$API_BASE/users/me" "401" || ((FAILED++))
check_endpoint "Notifications endpoint (protected)" "$API_BASE/notifications/tokens" "401" || ((FAILED++))
echo

# 4. CORS Configuration
echo "🌐 CORS Configuration"
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo -n "Checking CORS headers... "
cors_response=$(curl -s -I -X OPTIONS "$API_BASE/auth/google" \
    -H "Origin: https://example.com" \
    -H "Access-Control-Request-Method: POST" 2>&1)

if echo "$cors_response" | grep -q "Access-Control-Allow"; then
    echo -e "${GREEN}✓ OK${NC} (CORS enabled)"
else
    echo -e "${YELLOW}⚠ WARNING${NC} (CORS headers not found - may be OK for mobile apps)"
fi
echo

# 5. Firebase Configuration Check
echo "🔥 Firebase Configuration"
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo "Note: Cannot directly verify Firebase from here."
echo "Check Render logs for:"
echo "  - '✅ Firebase initialized successfully'"
echo "  - or '✅ Firebase initialized for authentication'"
echo

# Summary
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
if [ $FAILED -eq 0 ]; then
    echo -e "${GREEN}✓ All checks passed!${NC}"
    echo
    echo "Next steps:"
    echo "1. Check Render logs for Firebase initialization"
    echo "2. Test Google Sign-In from mobile app"
    echo "3. Monitor logs for authentication attempts"
    exit 0
else
    echo -e "${RED}✗ $FAILED check(s) failed${NC}"
    echo
    echo "Troubleshooting:"
    echo "1. Check Render dashboard - is service 'Live'?"
    echo "2. Review Render logs for errors"
    echo "3. Verify environment variables are set"
    echo "4. See PRODUCTION_GOOGLE_AUTH_CHECKLIST.md for detailed guide"
    exit 1
fi

