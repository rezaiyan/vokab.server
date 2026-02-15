#!/bin/bash

# Pre-Release Security Check Script
# Run this before pushing to GitHub to ensure no secrets are exposed

set -e

echo "🔍 Vokab Server - Pre-Release Security Check"
echo "============================================="
echo ""

FAILED=0
WARNINGS=0

# Color codes
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

# Check 1: .env files
echo "1️⃣  Checking for .env files..."
if ls .env* 2>/dev/null | grep -v .env.example > /dev/null; then
    echo -e "${RED}❌ FAIL: .env files found in directory!${NC}"
    ls .env* 2>/dev/null | grep -v .env.example
    echo "   Action: Remove all .env files before publishing"
    FAILED=$((FAILED+1))
else
    echo -e "${GREEN}✅ PASS: No .env files found${NC}"
fi
echo ""

# Check 2: Firebase credentials
echo "2️⃣  Checking for Firebase credentials..."
if ls *firebase*.json 2>/dev/null; then
    echo -e "${RED}❌ FAIL: Firebase JSON files found!${NC}"
    ls *firebase*.json 2>/dev/null
    echo "   Action: Remove Firebase service account files"
    FAILED=$((FAILED+1))
else
    echo -e "${GREEN}✅ PASS: No Firebase credentials found${NC}"
fi
echo ""

# Check 3: Private keys
echo "3️⃣  Checking for private keys..."
if find . -name "*.pem" -o -name "*.key" -o -name "*.p12" -o -name "*.jks" 2>/dev/null | grep -v "node_modules" | grep -v "build"; then
    echo -e "${YELLOW}⚠️  WARNING: Key files found (check if they should be ignored)${NC}"
    WARNINGS=$((WARNINGS+1))
else
    echo -e "${GREEN}✅ PASS: No private key files found${NC}"
fi
echo ""

# Check 4: Staged secrets
echo "4️⃣  Checking staged files for secrets..."
if git diff --cached --name-only 2>/dev/null | grep -E "\.(env|json|pem|key|p12)$"; then
    echo -e "${RED}❌ FAIL: Sensitive files are staged!${NC}"
    echo "   Action: Unstage these files"
    FAILED=$((FAILED+1))
else
    echo -e "${GREEN}✅ PASS: No sensitive files staged${NC}"
fi
echo ""

# Check 5: Secrets in code
echo "5️⃣  Scanning code for potential secrets..."
if grep -r -E "(sk-or-v1-|sk-[a-zA-Z0-9]{20,}|ghp_[a-zA-Z0-9]{20,})" --include="*.kt" --include="*.yml" --include="*.yaml" src/ 2>/dev/null; then
    echo -e "${RED}❌ FAIL: Potential API keys found in code!${NC}"
    echo "   Action: Remove hardcoded secrets"
    FAILED=$((FAILED+1))
else
    echo -e "${GREEN}✅ PASS: No obvious secrets in code${NC}"
fi
echo ""

# Check 6: .gitignore exists
echo "6️⃣  Checking .gitignore..."
if [ ! -f .gitignore ]; then
    echo -e "${RED}❌ FAIL: .gitignore missing!${NC}"
    FAILED=$((FAILED+1))
else
    # Check if .gitignore has essential patterns
    MISSING=""
    grep -q "\.env" .gitignore || MISSING="${MISSING}.env "
    grep -q "firebase" .gitignore || MISSING="${MISSING}firebase "
    grep -q "keys.*private" .gitignore || MISSING="${MISSING}keys/private "

    if [ -n "$MISSING" ]; then
        echo -e "${YELLOW}⚠️  WARNING: .gitignore missing patterns: ${MISSING}${NC}"
        WARNINGS=$((WARNINGS+1))
    else
        echo -e "${GREEN}✅ PASS: .gitignore configured properly${NC}"
    fi
fi
echo ""

# Check 7: env.example exists
echo "7️⃣  Checking env.example..."
if [ ! -f env.example ]; then
    echo -e "${YELLOW}⚠️  WARNING: env.example missing!${NC}"
    echo "   Recommendation: Create env.example as template"
    WARNINGS=$((WARNINGS+1))
else
    echo -e "${GREEN}✅ PASS: env.example exists${NC}"
fi
echo ""

# Check 8: LICENSE file
echo "8️⃣  Checking LICENSE file..."
if [ ! -f LICENSE ]; then
    echo -e "${YELLOW}⚠️  WARNING: LICENSE file missing!${NC}"
    echo "   Recommendation: Add a license (MIT, Apache 2.0, etc.)"
    WARNINGS=$((WARNINGS+1))
else
    echo -e "${GREEN}✅ PASS: LICENSE file exists${NC}"
fi
echo ""

# Check 9: Git history for secrets
echo "9️⃣  Checking git history for sensitive files..."
HISTORY_ISSUES=""
if git log --all --full-history --oneline -- "*.env" 2>/dev/null | head -1 | grep -q .; then
    HISTORY_ISSUES="${HISTORY_ISSUES}.env "
fi
if git log --all --full-history --oneline -- "*firebase*.json" 2>/dev/null | head -1 | grep -q .; then
    HISTORY_ISSUES="${HISTORY_ISSUES}firebase.json "
fi

if [ -n "$HISTORY_ISSUES" ]; then
    echo -e "${RED}❌ FAIL: Sensitive files found in git history: ${HISTORY_ISSUES}${NC}"
    echo "   Action: You may need to rewrite git history (dangerous!)"
    echo "   See: https://docs.github.com/en/authentication/keeping-your-account-and-data-secure/removing-sensitive-data-from-a-repository"
    FAILED=$((FAILED+1))
else
    echo -e "${GREEN}✅ PASS: No sensitive files in git history${NC}"
fi
echo ""

# Check 10: Hardcoded database credentials
echo "🔟 Checking for hardcoded database credentials..."
if grep -r -E "jdbc:postgresql://[^:]+:[^@]+@" --include="*.kt" --include="*.yml" src/ 2>/dev/null; then
    echo -e "${RED}❌ FAIL: Hardcoded database credentials found!${NC}"
    FAILED=$((FAILED+1))
else
    echo -e "${GREEN}✅ PASS: No hardcoded database credentials${NC}"
fi
echo ""

# Summary
echo "============================================="
echo "📊 Summary"
echo "============================================="
echo ""

if [ $FAILED -eq 0 ] && [ $WARNINGS -eq 0 ]; then
    echo -e "${GREEN}✅ ALL CHECKS PASSED!${NC}"
    echo ""
    echo "🎉 Your repository is safe to publish as open source!"
    echo ""
    echo "Next steps:"
    echo "  1. Review PRE_RELEASE_CHECKLIST.md"
    echo "  2. Add LICENSE file if missing"
    echo "  3. Update README.md with setup instructions"
    echo "  4. git push origin main"
    echo ""
    exit 0
elif [ $FAILED -eq 0 ]; then
    echo -e "${YELLOW}⚠️  ${WARNINGS} WARNING(S) - Review recommended${NC}"
    echo ""
    echo "Your repository is mostly safe, but consider addressing the warnings above."
    echo "Review PRE_RELEASE_CHECKLIST.md for details."
    echo ""
    exit 0
else
    echo -e "${RED}❌ ${FAILED} CRITICAL ISSUE(S) FOUND!${NC}"
    if [ $WARNINGS -gt 0 ]; then
        echo -e "${YELLOW}⚠️  ${WARNINGS} warning(s) also found${NC}"
    fi
    echo ""
    echo "🚨 DO NOT PUBLISH - Fix critical issues first!"
    echo ""
    echo "See OPENSOURCE_SECURITY_AUDIT.md for detailed guidance."
    echo ""
    exit 1
fi
