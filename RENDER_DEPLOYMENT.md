# Deploying Vokab Server on Render.com

This guide walks you through deploying the Vokab Server backend on Render.com with automatic deployments from Git.

## Prerequisites

1. **Render.com Account**: Sign up at [render.com](https://render.com)
2. **Git Repository**: Push your code to GitHub/GitLab/Bitbucket
3. **API Keys**: Obtain required API keys (see below)

## Required API Keys

Before deployment, ensure you have:

1. **JWT Secret**: Generate a secure random string (256-bit recommended)
   ```bash
   openssl rand -base64 32
   ```

2. **Google OAuth Credentials**: Get from [Google Cloud Console](https://console.cloud.google.com/apis/credentials)
   - Create OAuth 2.0 Client ID
   - Note: `GOOGLE_CLIENT_ID` and `GOOGLE_CLIENT_SECRET`

3. **OpenRouter API Key**: Get from [OpenRouter](https://openrouter.ai/keys)
   - Required for AI vocabulary extraction and insights

4. **RevenueCat** (Optional): Get from [RevenueCat Dashboard](https://app.revenuecat.com/)
   - Only needed if using in-app purchases

5. **Firebase Service Account** (Optional): For push notifications
   - Download from [Firebase Console](https://console.firebase.google.com/)
   - See `FIREBASE_SETUP.md` for details

## Deployment Methods

### Option 1: Deploy via Render Blueprint (Recommended)

This method uses the `render.yaml` file to automatically configure all services.

1. **Push Code to Git**:
   ```bash
   git init
   git add .
   git commit -m "Initial commit"
   git remote add origin <your-repo-url>
   git push -u origin main
   ```

2. **Create New Blueprint on Render**:
   - Go to [Render Dashboard](https://dashboard.render.com/)
   - Click **"New +"** → **"Blueprint"**
   - Connect your Git repository
   - Render will detect `render.yaml` automatically
   - Click **"Apply"**

3. **Configure Secret Environment Variables**:
   
   After deployment, go to your service settings and add these secrets:
   
   **Required:**
   - `JWT_SECRET`: Your generated JWT secret
   - `GOOGLE_CLIENT_ID`: Your Google OAuth client ID
   - `GOOGLE_CLIENT_SECRET`: Your Google OAuth client secret
   - `OPENROUTER_API_KEY`: Your OpenRouter API key
   
   **Optional:**
   - `REVENUECAT_WEBHOOK_SECRET`: If using subscriptions
   - `REVENUECAT_API_KEY`: If using subscriptions

4. **Update CORS Origins**:
   - In Render Dashboard → Service → Environment
   - Update `CORS_ALLOWED_ORIGINS` with your actual domain(s)
   - Example: `https://vokab.app,https://app.vokab.com`

5. **Trigger Redeploy**:
   - Click **"Manual Deploy"** → **"Deploy latest commit"**

### Option 2: Manual Deployment

1. **Create PostgreSQL Database**:
   - Dashboard → **"New +"** → **"PostgreSQL"**
   - Name: `vokab-db`
   - Region: Choose closest to your users
   - Plan: `Free` or `Starter`
   - Click **"Create Database"**

2. **Create Web Service**:
   - Dashboard → **"New +"** → **"Web Service"**
   - Connect your Git repository
   - Settings:
     - **Name**: `vokab-server`
     - **Region**: Same as database
     - **Branch**: `main`
     - **Root Directory**: Leave empty (or path to vokab.server if nested)
     - **Environment**: `Docker`
     - **Dockerfile Path**: `./Dockerfile`
     - **Plan**: `Free` or `Starter`

3. **Configure Environment Variables**:
   
   Add all variables from `render.yaml` manually in the Environment tab.
   
   **Database (from PostgreSQL service):**
   - `DATABASE_URL`: Copy internal connection string
   - `DATABASE_USERNAME`: Copy from database credentials
   - `DATABASE_PASSWORD`: Copy from database credentials
   - `DATABASE_DRIVER`: `org.postgresql.Driver`
   - `HIBERNATE_DIALECT`: `org.hibernate.dialect.PostgreSQLDialect`
   
   **Application Secrets:**
   - Add all required secrets listed in Option 1, step 3

4. **Set Health Check**:
   - **Health Check Path**: `/api/v1/health`

5. **Deploy**:
   - Click **"Create Web Service"**
   - Wait for build and deployment

## Post-Deployment Configuration

### 1. Update Client Applications

Update your mobile/web apps with the new server URL:

```kotlin
// Android/iOS Client
const val BASE_URL = "https://vokab-server.onrender.com"
```

### 2. Configure Google OAuth Redirect URIs

Add your Render URL to Google OAuth settings:
- Go to [Google Cloud Console](https://console.cloud.google.com/apis/credentials)
- Edit your OAuth 2.0 Client ID
- Add Authorized Redirect URIs:
  ```
  https://vokab-server.onrender.com/login/oauth2/code/google
  ```

### 3. Firebase Service Account (Optional)

If using push notifications:

1. **Upload via Render Secret Files**:
   - Dashboard → Service → Environment
   - Scroll to **"Secret Files"**
   - Add new secret file:
     - **Filename**: `firebase-service-account.json`
     - **Contents**: Paste your Firebase JSON content
   - The file will be available at `/etc/secrets/firebase-service-account.json`

2. **Update Environment Variable**:
   - Set `FIREBASE_SERVICE_ACCOUNT_PATH` to `/etc/secrets/firebase-service-account.json`

### 4. Configure RevenueCat Webhooks (Optional)

If using in-app purchases:
- RevenueCat Dashboard → Project Settings → Webhooks
- Add webhook URL: `https://vokab-server.onrender.com/api/v1/webhooks/revenuecat`
- Set authentication to match your `REVENUECAT_WEBHOOK_SECRET`

## Automatic Deployments

Render automatically deploys when you push to your Git repository:

```bash
git add .
git commit -m "Update feature"
git push origin main
```

Deployment typically takes 2-5 minutes.

## Monitoring

### Check Service Health

```bash
curl https://vokab-server.onrender.com/api/v1/health
```

Expected response:
```json
{
  "status": "UP"
}
```

### View Logs

- Dashboard → Service → Logs
- Real-time logs show all application output
- Filter by level: Info, Warning, Error

### Metrics

- Dashboard → Service → Metrics
- View CPU, Memory, Request count, Response times

## Troubleshooting

### Build Fails

1. Check logs in Render Dashboard
2. Verify Dockerfile is correct
3. Ensure all dependencies are in `build.gradle.kts`

### Application Crashes on Startup

Common issues:
- Missing required environment variables
- Database connection issues
- Invalid API keys

**Solution**: Check logs and verify all required env vars are set.

### Database Connection Errors

1. Verify database is running
2. Check `DATABASE_URL` format: `jdbc:postgresql://hostname:port/dbname`
3. Ensure web service and database are in the same region

### High Latency

Free tier services sleep after 15 minutes of inactivity:
- First request may take 30-60 seconds (cold start)
- Upgrade to paid plan for always-on service
- Use [UptimeRobot](https://uptimerobot.com/) to ping every 10 minutes (keeps service warm)

## Production Recommendations

1. **Upgrade Plans**:
   - Database: `Starter` ($7/month) - 256 MB RAM, 1 GB storage
   - Web Service: `Starter` ($7/month) - Always on, no cold starts

2. **Enable Disk Storage** (for logs, if needed):
   - Dashboard → Service → Settings → Persistent Disks

3. **Add Custom Domain**:
   - Dashboard → Service → Settings → Custom Domain
   - Add: `api.yourdomain.com`

4. **Set Up Alerts**:
   - Dashboard → Service → Settings → Notifications
   - Email alerts for deploy failures, crashes

5. **Database Backups**:
   - Render automatically backs up PostgreSQL
   - Paid plans: Daily backups with point-in-time recovery

6. **Security**:
   - Rotate JWT secret regularly
   - Use strong database passwords
   - Keep API keys secure
   - Review CORS settings

## Cost Breakdown

### Free Tier
- Web Service: Free (sleeps after 15 min inactivity)
- PostgreSQL: Free (90-day limit, 256 MB RAM, 1 GB storage)
- **Total**: $0/month

### Starter Tier (Recommended for Production)
- Web Service: $7/month (always on)
- PostgreSQL Starter: $7/month (256 MB RAM, 1 GB storage)
- **Total**: $14/month

### Growing App
- Web Service Standard: $25/month (2 GB RAM)
- PostgreSQL Standard: $25/month (4 GB RAM, 100 GB storage)
- **Total**: $50/month

## Support

- [Render Documentation](https://render.com/docs)
- [Render Community](https://community.render.com/)
- [Render Support](https://render.com/support)

## CI/CD Integration

Render automatically deploys from Git, but you can also:

1. **Deploy Hooks**: Trigger deployments via API
   ```bash
   curl https://api.render.com/deploy/srv-xxxxx?key=xxxxx
   ```

2. **GitHub Actions**: Use Render's official action
   ```yaml
   - name: Trigger Render Deploy
     uses: johnbeynon/render-deploy-action@v0.0.8
     with:
       service-id: ${{ secrets.RENDER_SERVICE_ID }}
       api-key: ${{ secrets.RENDER_API_KEY }}
   ```

---

**🎉 Your Vokab Server is now deployed and ready to scale!**

