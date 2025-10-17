# Vokab Server Deployment Checklist

Use this checklist to ensure a smooth deployment to Render.com or any production environment.

## Pre-Deployment

### 1. Code Ready ✓
- [ ] All features tested locally
- [ ] No sensitive data in code (API keys, passwords)
- [ ] `.gitignore` properly configured
- [ ] All tests passing (`./gradlew test`)

### 2. Configuration Files ✓
- [ ] `render.yaml` reviewed and updated
- [ ] `Dockerfile` builds successfully locally
- [ ] `application.yml` uses environment variables
- [ ] `env.example` documented with all required variables

### 3. Git Repository
- [ ] Code committed to Git
- [ ] Remote repository created (GitHub/GitLab/Bitbucket)
- [ ] Code pushed to remote: `git push origin main`
- [ ] `.env` and Firebase JSON files NOT committed

### 4. API Keys & Secrets Obtained

#### Required ✓
- [ ] **JWT_SECRET**: Generated using `./scripts/generate-secrets.sh`
- [ ] **GOOGLE_CLIENT_ID**: From [Google Cloud Console](https://console.cloud.google.com/apis/credentials)
- [ ] **GOOGLE_CLIENT_SECRET**: From Google Cloud Console
- [ ] **OPENROUTER_API_KEY**: From [OpenRouter](https://openrouter.ai/keys)

#### Optional
- [ ] **FIREBASE_SERVICE_ACCOUNT**: From [Firebase Console](https://console.firebase.google.com/)
- [ ] **REVENUECAT_WEBHOOK_SECRET**: From [RevenueCat](https://app.revenuecat.com/)
- [ ] **REVENUECAT_API_KEY**: From RevenueCat

### 5. Domain & CORS
- [ ] Domain name ready (if using custom domain)
- [ ] CORS origins list prepared (frontend URLs)

## Deployment on Render

### Step 1: Create Render Account
- [ ] Sign up at [render.com](https://render.com)
- [ ] Verify email address
- [ ] Payment method added (if using paid plans)

### Step 2: Deploy via Blueprint
- [ ] Connected Git repository to Render
- [ ] Selected "New Blueprint" option
- [ ] Render detected `render.yaml`
- [ ] Clicked "Apply" to create services

### Step 3: Configure Environment Variables
- [ ] Opened web service settings → Environment
- [ ] Added `JWT_SECRET`
- [ ] Added `GOOGLE_CLIENT_ID`
- [ ] Added `GOOGLE_CLIENT_SECRET`
- [ ] Added `OPENROUTER_API_KEY`
- [ ] Updated `CORS_ALLOWED_ORIGINS` with real domains
- [ ] (Optional) Added `REVENUECAT_WEBHOOK_SECRET`
- [ ] (Optional) Added `REVENUECAT_API_KEY`

### Step 4: Firebase Setup (Optional)
- [ ] Opened Environment → Secret Files
- [ ] Added secret file: `firebase-service-account.json`
- [ ] Pasted Firebase JSON content
- [ ] Set `FIREBASE_SERVICE_ACCOUNT_PATH=/etc/secrets/firebase-service-account.json`

### Step 5: Initial Deployment
- [ ] Clicked "Manual Deploy" → "Deploy latest commit"
- [ ] Watched build logs for errors
- [ ] Waited for deployment to complete (~3-5 minutes)
- [ ] Deployment status shows "Live"

## Post-Deployment Verification

### 1. Service Health
- [ ] Health endpoint responding: `curl https://your-service.onrender.com/api/v1/health`
- [ ] Response is `{"status":"UP"}`
- [ ] No errors in Render logs

### 2. Database Connection
- [ ] PostgreSQL service status: "Available"
- [ ] Application successfully connected to database
- [ ] No connection errors in logs

### 3. API Endpoints Testing
- [ ] Test authentication endpoint:
  ```bash
  curl -X POST https://your-service.onrender.com/api/v1/auth/google \
    -H "Content-Type: application/json" \
    -d '{"idToken":"test"}'
  ```
- [ ] Proper error response (401 for invalid token)
- [ ] Test AI health endpoint:
  ```bash
  curl https://your-service.onrender.com/api/v1/ai/health
  ```

### 4. External Service Integration

#### Google OAuth
- [ ] Added redirect URI to Google Console:
  - `https://your-service.onrender.com/login/oauth2/code/google`
- [ ] Test OAuth flow from client app
- [ ] Authentication working end-to-end

#### Firebase (if using)
- [ ] Push notification test sent successfully
- [ ] Token registration working
- [ ] No Firebase errors in logs

#### RevenueCat (if using)
- [ ] Webhook URL configured: `https://your-service.onrender.com/api/v1/webhooks/revenuecat`
- [ ] Test webhook delivery
- [ ] Webhook signature verification working

### 5. Client App Configuration
- [ ] Updated client app base URL to production URL
- [ ] Client app can communicate with backend
- [ ] All features working end-to-end
- [ ] Error handling works correctly

## Production Readiness

### 1. Performance
- [ ] Response times acceptable (<1s for most endpoints)
- [ ] No memory leaks observed
- [ ] Database queries optimized
- [ ] Consider upgrading from free tier if experiencing cold starts

### 2. Monitoring
- [ ] Checked Render Metrics dashboard
- [ ] Set up uptime monitoring (e.g., UptimeRobot)
- [ ] Configured email alerts in Render
- [ ] Log aggregation reviewed

### 3. Security
- [ ] All secrets stored securely (not in code)
- [ ] CORS properly configured (no wildcard `*` in production)
- [ ] HTTPS enforced (automatic on Render)
- [ ] Rate limiting configured in application
- [ ] H2 console disabled in production (`spring.h2.console.enabled=false`)

### 4. Backup & Recovery
- [ ] Database backups enabled (automatic on Render paid plans)
- [ ] Environment variables documented
- [ ] Deployment rollback plan tested
- [ ] Know how to access database backups

### 5. Scalability
- [ ] Chosen appropriate plan size for current usage
- [ ] Understand scaling options (vertical/horizontal)
- [ ] Database connection pool sized correctly
- [ ] Caching strategy implemented if needed

## Ongoing Maintenance

### Auto-Deployment
- [ ] Auto-deploy enabled for main branch
- [ ] Git push triggers deployment
- [ ] CI/CD pipeline configured (if applicable)

### Monitoring Checklist (Weekly)
- [ ] Review error logs in Render dashboard
- [ ] Check application metrics (CPU, memory, requests)
- [ ] Verify database disk usage
- [ ] Review response times

### Security Checklist (Monthly)
- [ ] Review and rotate secrets (if policy requires)
- [ ] Update dependencies: `./gradlew dependencyUpdates`
- [ ] Check for security vulnerabilities
- [ ] Review access logs for suspicious activity

### Update Checklist (Per Release)
- [ ] Test changes locally
- [ ] Update version in `build.gradle.kts`
- [ ] Commit and push to trigger deployment
- [ ] Monitor deployment logs
- [ ] Verify health endpoint post-deployment
- [ ] Test critical endpoints
- [ ] Monitor error rates for 24 hours

## Troubleshooting

### Build Failed
- [ ] Check build logs in Render
- [ ] Verify Dockerfile syntax
- [ ] Ensure all dependencies in `build.gradle.kts`
- [ ] Test Docker build locally: `docker build -t test .`

### Application Crashes
- [ ] Check application logs
- [ ] Verify all required environment variables set
- [ ] Check database connection
- [ ] Verify API keys are valid

### Slow Performance
- [ ] Check if on free tier (cold starts expected)
- [ ] Review database query performance
- [ ] Consider upgrading plan
- [ ] Check for N+1 query issues

### Database Issues
- [ ] Verify connection string format
- [ ] Check database is in same region
- [ ] Ensure connection pool settings appropriate
- [ ] Check disk space usage

## Rollback Plan

If deployment fails or critical issues arise:

1. [ ] In Render Dashboard → Service → Manual Deploy
2. [ ] Select previous successful deployment from dropdown
3. [ ] Click "Deploy selected commit"
4. [ ] Monitor logs for successful rollback
5. [ ] Verify health endpoint
6. [ ] Investigate issue before retry

## Emergency Contacts

- **Render Support**: https://render.com/support
- **Render Status**: https://status.render.com/
- **Community Forum**: https://community.render.com/

---

✅ **Deployment Complete!** Your Vokab Server is live and ready to serve requests.

🎉 **Production URL**: `https://your-service.onrender.com`


