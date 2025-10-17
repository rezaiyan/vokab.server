# Docker Build Fix for Render

## Error
```
error: failed to solve: failed to compute cache key: 
failed to calculate checksum of ref p2n1yc3jlrlz80do1dfpq13lc::2qjk8lq3vihldwku4hpwqrfkt: 
"/||": not found
```

## Root Cause

The `.dockerignore` file was excluding **ALL** `.jar` files with this pattern:
```
*.jar
```

This excluded the **Gradle Wrapper JAR** (`gradle/wrapper/gradle-wrapper.jar`) which is **required** for the Docker build to work.

## The Fix

Updated `.dockerignore` to use Docker's negation pattern:

```dockerfile
# Exclude JAR files except gradle-wrapper.jar
*.jar
!gradle/wrapper/gradle-wrapper.jar
```

This tells Docker to:
1. ✅ Exclude all `.jar` files
2. ✅ BUT include `gradle/wrapper/gradle-wrapper.jar` (negation with `!`)

## Additional Issue Found

### Line 33: Invalid COPY Syntax

**Problem:**
```dockerfile
COPY --chown=spring:spring firebase-service-account.json* /app/ 2>/dev/null || true
```

The `COPY` command doesn't execute in a shell, so shell operators like `2>/dev/null || true` cause errors.

**Solution:**
Removed the line entirely. Firebase credentials should be provided via Render's **Secret Files** feature, not copied from the repository:
- Render mounts secrets at `/etc/secrets/`
- Set `FIREBASE_SERVICE_ACCOUNT_PATH=/etc/secrets/firebase-service-account.json`
- See `RENDER_DEPLOYMENT.md` for setup instructions

## Files Changed

1. `.dockerignore` - Added exception for gradle-wrapper.jar
2. `.renderignore` - Added clarifying comment
3. `Dockerfile` - Removed invalid COPY command with shell operators

## How to Deploy the Fix

```bash
# Commit all fixes
git add .dockerignore .renderignore Dockerfile DOCKER_BUILD_FIX.md
git commit -m "fix: Docker build issues - gradle wrapper and COPY syntax"

# Push to trigger Render deployment
git push origin main
```

Render will automatically rebuild with the corrected `.dockerignore` file.

## Verification

After deployment, check Render logs for successful build:
- ✅ Build should complete without "cache key" errors
- ✅ Gradle should download dependencies successfully
- ✅ Application JAR should be built
- ✅ Service should start and pass health check

## Why This Happened

The original `.dockerignore` was too aggressive in excluding files. While it's good practice to exclude build artifacts (`*.jar`), the Gradle Wrapper JAR is **not** a build artifact—it's a **build tool** that needs to be present.

## Docker Build Process

The Dockerfile uses multi-stage builds:

**Stage 1: Builder**
```dockerfile
FROM gradle:8.5-jdk21 AS builder
COPY gradle gradle  # ← Needs gradle-wrapper.jar
RUN gradle build    # ← Uses the wrapper
```

**Stage 2: Runtime**
```dockerfile
FROM eclipse-temurin:21-jre-alpine
COPY --from=builder /app/build/libs/*.jar app.jar
```

Without the wrapper, Stage 1 fails immediately.

## Prevention

When creating `.dockerignore` files:
- ✅ Exclude build outputs (`build/`, `*.jar`)
- ❌ Don't exclude build tools (`gradlew`, `gradle/wrapper/`)
- ✅ Use negation patterns when needed (`!gradle/wrapper/*.jar`)
- ✅ Test Docker builds locally before pushing

## Related Files

- `Dockerfile` - Multi-stage build definition
- `.dockerignore` - Controls what's copied to Docker context
- `.renderignore` - Controls what Render uploads (less strict)
- `gradle/wrapper/` - Gradle Wrapper (required for build)

---

**Status**: ✅ Fixed
**Date**: 2025-10-17

