# Firebase Service Account Setup

## 🔒 Security Warning

**NEVER commit your Firebase service account JSON file to version control!**

This file contains private keys that can be used to access your Firebase project with admin privileges.

---

## 📋 Setup Instructions

### 1. Get Your Service Account File

1. Go to [Firebase Console](https://console.firebase.google.com/)
2. Select your project (`vokab-737ec`)
3. Click the gear icon ⚙️ → **Project Settings**
4. Go to the **Service Accounts** tab
5. Click **Generate new private key**
6. Download the JSON file

### 2. Place the File Securely

**Option A: Local Development (Current)**
```bash
# Place the file in vokab.server directory
vokab.server/vokab-737ec-firebase-adminsdk-xxxxx.json
```

**Option B: Environment Variable (Production)**
```bash
# Store the file path in an environment variable
export FIREBASE_SERVICE_ACCOUNT_PATH="/path/to/firebase-credentials.json"
```

**Option C: Secret Manager (Cloud Deployment)**
- Use Google Cloud Secret Manager
- Use AWS Secrets Manager
- Use Azure Key Vault

### 3. Configure Application

The application reads the file path from `application.yml`:

```yaml
app:
  firebase:
    service-account-path: ${FIREBASE_SERVICE_ACCOUNT_PATH:}
```

**For local development:**
```bash
# Set environment variable
export FIREBASE_SERVICE_ACCOUNT_PATH="/Users/yourname/AndroidStudioProjects/Vokab/vokab.server/vokab-737ec-firebase-adminsdk-xxxxx.json"

# Or use absolute path in application.yml (NOT recommended for git)
```

---

## ✅ Security Checklist

- [x] Added `*firebase-adminsdk*.json` to `.gitignore`
- [x] Created `firebase-adminsdk-example.json` template
- [ ] Store actual credentials outside repository (for production)
- [ ] Use environment variables for file path
- [ ] Never share the JSON file in chat, email, or public places
- [ ] Rotate keys if accidentally exposed

---

## 🚨 What to Do If Exposed

If you accidentally commit or expose your service account key:

1. **Immediately delete the key** in Firebase Console
2. **Generate a new key**
3. **Remove from git history:**
   ```bash
   # Using git filter-branch (careful!)
   git filter-branch --force --index-filter \
     "git rm --cached --ignore-unmatch vokab.server/*firebase*.json" \
     --prune-empty --tag-name-filter cat -- --all
   
   # Force push (if already pushed)
   git push origin --force --all
   ```

---

## 📖 Current Setup

**Project ID:** `vokab-737ec`
**Service Account:** `firebase-adminsdk-fbsvc@vokab-737ec.iam.gserviceaccount.com`

**File Location (Development):**
```
vokab.server/vokab-737ec-firebase-adminsdk-fbsvc-94a8d66ebb.json
```

**Environment Variable:**
```bash
export FIREBASE_SERVICE_ACCOUNT_PATH="/Users/ali/AndroidStudioProjects/Vokab/vokab.server/vokab-737ec-firebase-adminsdk-fbsvc-94a8d66ebb.json"
```

This is already configured and working for local development.

