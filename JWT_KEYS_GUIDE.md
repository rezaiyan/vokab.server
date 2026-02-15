# JWT Authentication & Key Management Guide

## Overview

This application uses **RS256 JWT tokens** for authentication with **automatic key persistence** to ensure users remain logged in across server restarts.

## How It Works

### 🔑 Token Types

1. **Access Token (JWT)**
   - Algorithm: RS256 (RSA asymmetric encryption)
   - Expiration: 24 hours
   - Storage: Client-side (memory/localStorage)
   - Validation: Stateless - verified using public key
   - Contains: User ID, email, issued date, expiration

2. **Refresh Token (Opaque)**
   - Type: Random cryptographically secure string
   - Expiration: 90 days
   - Storage: Server-side (PostgreSQL database, SHA-256 hashed)
   - Rotation: New token issued on each refresh, old token revoked
   - Purpose: Obtain new access tokens without re-authentication

### 🔐 RSA Key Management

#### Automatic Key Generation & Persistence

On server startup:
1. **Check for existing keys** at configured paths
2. **If keys exist**: Load them (users stay logged in ✅)
3. **If keys don't exist**: Generate new RSA-2048 key pair and save to files

#### Key Locations

**Local Development:**
```
./keys/jwt-private-key.pem  # Never commit this!
./keys/jwt-public-key.pem   # Can be committed (it's public)
```

**Docker/Production:**
```
/app/keys/jwt-private-key.pem  # Mounted volume
/app/keys/jwt-public-key.pem   # Mounted volume
```

#### Why Persistent Keys Matter

**WITHOUT persistent keys:**
- Server restart → New keys generated → All access tokens invalidated → Users logged out ❌

**WITH persistent keys:**
- Server restart → Same keys loaded → Access tokens remain valid → Users stay logged in ✅

## Server Restart Behavior

### What Happens on Restart

1. **Access Tokens**: ✅ Remain valid (if keys are persistent)
2. **Refresh Tokens**: ✅ Remain valid (stored in database)
3. **User Sessions**: ✅ Preserved (no re-authentication needed)

### Cleanup Jobs

- **Expired Refresh Tokens**: Cleaned up daily at 2 AM
- **Revoked Tokens**: Immediately invalidated in database

## Security Best Practices ✅

### ✅ Implemented in This Application

1. **Token Rotation**: Refresh tokens are rotated on each use
2. **Database Tracking**: All refresh tokens tracked in PostgreSQL
3. **Secure Hashing**: Tokens hashed with SHA-256 before storage
4. **Expiration**: Both token types have appropriate expiration times
5. **Revocation**: Supports logout (single device) and logout-all (all devices)
6. **Stateless Access Tokens**: No server-side storage needed for JWT validation
7. **Persistent Keys**: RSA keys survive server restarts
8. **Automatic Cleanup**: Expired tokens removed daily

### 🔒 Production Recommendations

1. **Back Up Private Keys**:
   ```bash
   # Create secure backup
   cp keys/jwt-private-key.pem keys/jwt-private-key.pem.backup
   # Store backup in secure location (vault, encrypted storage, etc.)
   ```

2. **Set Proper Permissions** (Linux/Mac):
   ```bash
   chmod 600 keys/jwt-private-key.pem  # Owner read/write only
   chmod 644 keys/jwt-public-key.pem   # Public key can be readable
   ```

3. **Environment Variables**:
   ```bash
   JWT_PRIVATE_KEY_PATH=/app/keys/jwt-private-key.pem
   JWT_PUBLIC_KEY_PATH=/app/keys/jwt-public-key.pem
   ```

4. **Never Commit Private Keys**: Already configured in `.gitignore`

5. **Use HTTPS**: Always use HTTPS in production to protect tokens in transit

6. **Consider Key Rotation**: Plan for periodic key rotation (requires strategy for handling old tokens)

## Docker Deployment

### Volume Persistence

The `docker-compose.yml` includes a persistent volume for JWT keys:

```yaml
volumes:
  jwt_keys:  # Persist JWT RSA keys across container restarts

services:
  app:
    volumes:
      - jwt_keys:/app/keys  # Mount persistent volume
```

### First Deployment

1. **Start containers**: `docker-compose up -d`
2. **Keys are generated automatically** and saved to volume
3. **Subsequent restarts**: Same keys loaded from volume

### Backup Keys from Docker

```bash
# Copy keys from container to host
docker cp vokab-server:/app/keys/jwt-private-key.pem ./backup/
docker cp vokab-server:/app/keys/jwt-public-key.pem ./backup/
```

### Restore Keys to Docker

```bash
# Copy keys from host to container (if needed)
docker cp ./backup/jwt-private-key.pem vokab-server:/app/keys/
docker cp ./backup/jwt-public-key.pem vokab-server:/app/keys/
docker-compose restart app
```

## Troubleshooting

### All Users Logged Out After Restart

**Cause**: RSA keys changed (new keys generated)

**Solution**: Ensure keys are persisted:
1. Check `.env` has `JWT_PRIVATE_KEY_PATH` and `JWT_PUBLIC_KEY_PATH`
2. Check `keys/` directory exists
3. Check Docker volume is mounted correctly
4. Check logs for "Generated new RSA key pair" (indicates keys were regenerated)

### "Invalid or expired token" Errors

**Possible Causes**:
1. Access token expired (normal - use refresh token)
2. RSA keys changed (all tokens invalidated)
3. User logged out or account deleted

**Solution**: Client should refresh token automatically

### Keys Not Persisting in Docker

**Check**:
```bash
# Verify volume exists
docker volume ls | grep jwt_keys

# Inspect volume
docker volume inspect vokab_jwt_keys

# Check files in container
docker exec vokab-server ls -la /app/keys/
```

## Testing Key Persistence

### Local Test

```bash
# 1. Start server - keys will be generated
./gradlew bootRun

# 2. Login and note your access token
# 3. Stop server (Ctrl+C)
# 4. Start server again
./gradlew bootRun

# 5. Use the same access token - it should still work!
```

### Docker Test

```bash
# 1. Start containers
docker-compose up -d

# 2. Login and note your access token
# 3. Restart container
docker-compose restart app

# 4. Use the same access token - it should still work!
```

## Monitoring

### Logs to Watch

```bash
# Successful key load (good!)
✅ Successfully loaded RSA key pair from files

# Keys generated (first startup - normal)
Generated new RSA key pair (2048 bits)
✅ RSA key pair saved to files

# WARNING: Keys regenerated (investigate why!)
Failed to load existing keys, generating new pair (THIS WILL INVALIDATE ALL TOKENS)
```

## Migration from Old System

If you had an old system without persistent keys:

1. **Deploy new version** with persistent keys
2. **All users will be logged out once** (new keys generated)
3. **Future restarts**: Users stay logged in ✅

No database migration required - refresh tokens in database remain valid.

## Summary

✅ **Persistent RSA keys** ensure users stay logged in after server restarts
✅ **Automatic key management** - no manual intervention needed
✅ **Refresh token rotation** prevents replay attacks
✅ **Database tracking** allows token revocation
✅ **Docker volumes** persist keys across container restarts
✅ **Security best practices** implemented throughout

**Result**: Users enjoy seamless authentication experience even during deployments! 🎉
