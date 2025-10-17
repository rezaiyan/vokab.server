# 🚀 Quick Start: Deploy to Render.com

Deploy your Vokab Server to production in **5 minutes**!

## Step 1: Generate Secrets (30 seconds)

```bash
./scripts/generate-secrets.sh
```

Copy the generated `JWT_SECRET` - you'll need it in Step 4.

## Step 2: Push to Git (1 minute)

```bash
# If not already done
git init
git add .
git commit -m "Initial commit: Vokab Server with Render config"

# Create repo on GitHub/GitLab and push
git remote add origin <your-repo-url>
git push -u origin main
```

## Step 3: Deploy on Render (2 minutes)

1. Go to [Render Dashboard](https://dashboard.render.com/)
2. Click **"New +"** → **"Blueprint"**
3. Connect your Git repository
4. Render detects `render.yaml` automatically
5. Click **"Apply"**

✅ Render will create:
- PostgreSQL database
- Web service
- Environment variables (with defaults)

## Step 4: Configure Database Connection (1 minute)

After deployment starts:

**Dashboard → vokab-db (PostgreSQL database)**

1. Copy the **Internal Database URL** (starts with `postgres://`)
2. Go to **Dashboard → vokab-server → Environment**
3. Add/Update `DATABASE_URL`:
   - Copy the internal URL
   - Change `postgres://` to `jdbc:postgresql://`
   - Example: `jdbc:postgresql://dpg-xxxxx:5432/vokabdb`

## Step 5: Add Secret Keys (2 minutes)

In the same Environment section, add these **required** secrets:

| Variable | Where to Get | Example |
|----------|-------------|---------|
| `JWT_SECRET` | Generated in Step 1 | `xK8j3nM...` |
| `GOOGLE_CLIENT_ID` | [Google Console](https://console.cloud.google.com/apis/credentials) | `123456789.apps.googleusercontent.com` |
| `GOOGLE_CLIENT_SECRET` | Google Console | `GOCSPX-abc123...` |
| `OPENROUTER_API_KEY` | [OpenRouter Keys](https://openrouter.ai/keys) | `sk-or-v1-abc123...` |

**Optional** (add if using):
- `REVENUECAT_WEBHOOK_SECRET`
- `REVENUECAT_API_KEY`

## Step 6: Update CORS Origins

In the same Environment section:

1. Find `CORS_ALLOWED_ORIGINS`
2. Replace with your actual domains:
   ```
   https://yourdomain.com,https://app.yourdomain.com
   ```

## Step 7: Deploy!

Click **"Manual Deploy"** → **"Deploy latest commit"**

Wait ~3-5 minutes for the build to complete.

## Step 8: Verify Deployment ✅

Once status shows **"Live"**, test the health endpoint:

```bash
curl https://vokab-server.onrender.com/api/v1/health
```

Expected response:
```json
{"status":"UP"}
```

## 🎉 Done!

Your backend is now live at: `https://vokab-server.onrender.com`

### Next Steps:

1. **Update your mobile/web app** with the new backend URL
2. **Configure Google OAuth redirect URI**: Add `https://vokab-server.onrender.com/login/oauth2/code/google` to Google Console
3. **Set up Firebase** (if using push notifications): See [RENDER_DEPLOYMENT.md](RENDER_DEPLOYMENT.md#firebase-service-account-optional)
4. **Monitor**: Check the Logs tab for any issues

### Auto-Deploy Setup ✅

From now on, every time you push to `main`:

```bash
git push origin main
```

Render automatically deploys your changes! 🚀

---

## Troubleshooting

**Build fails?**
- Check logs in Render Dashboard
- Verify `Dockerfile` syntax
- Ensure Java 21 is being used

**App crashes on startup?**
- Verify all required env vars are set
- Check database connection in logs
- Ensure API keys are valid

**Need help?**
- [Full Deployment Guide](RENDER_DEPLOYMENT.md)
- [Deployment Checklist](DEPLOYMENT_CHECKLIST.md)
- [Render Documentation](https://render.com/docs)

---

## Cost

**Free Tier**: $0/month
- Web service sleeps after 15 min inactivity
- First request takes ~30s (cold start)
- PostgreSQL limited to 90 days

**Starter Tier**: $14/month (recommended for production)
- Always-on service (no cold starts)
- 24/7 PostgreSQL database
- Better performance

Upgrade anytime from Render Dashboard.

