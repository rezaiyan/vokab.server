# Render.com Deployment Notes

## Database Configuration Fix

### Issue
The original `render.yaml` had incorrect database configuration that caused Render validation errors:
- `databaseName` and `databaseUser` fields not recognized in `type: pserv`
- Database was in wrong section

### Solution
✅ **Fixed Structure:**
```yaml
databases:
  - name: vokab-db
    plan: free
    region: oregon
    databaseName: vokabdb
    user: vokab_user

services:
  - type: web
    name: vokab-server
    # ... rest of config
```

### Database Connection Setup

After deploying with the blueprint, you need to **manually configure DATABASE_URL**:

1. Go to Render Dashboard → **vokab-db** (database)
2. Copy the **Internal Database URL**
3. Go to **vokab-server** (web service) → **Environment**
4. Add/Update `DATABASE_URL`:
   - Render provides: `postgres://user:password@host:5432/dbname`
   - Convert to JDBC: `jdbc:postgresql://host:5432/dbname`

**Example:**
```
Render provides: postgres://vokab_user:abc123@dpg-xxxxx-a:5432/vokabdb
Convert to:      jdbc:postgresql://dpg-xxxxx-a:5432/vokabdb
```

### Why Manual Configuration?

Render's blueprint doesn't support automatic environment variable substitution like `${DATABASE_HOST}:${DATABASE_PORT}`, so the JDBC URL must be constructed manually after deployment.

### Automatic Values (No Action Needed)

These are automatically populated from the database:
- ✅ `DATABASE_HOST` - from database host
- ✅ `DATABASE_USERNAME` - from database user
- ✅ `DATABASE_PASSWORD` - from database password

---

## Alternative: Using Render Dashboard Only

If you prefer not to use the blueprint:

1. Create PostgreSQL database manually
2. Create Web Service manually
3. Set all environment variables via the dashboard
4. See `RENDER_DEPLOYMENT.md` → "Option 2: Manual Deployment"

---

## Validation Checklist

Before deploying, ensure your `render.yaml`:
- [ ] Has `databases:` section at the top level
- [ ] Has `services:` section at the top level
- [ ] Database uses `name`, `plan`, `region` (not `type: pserv`)
- [ ] Web service references database with `fromDatabase`
- [ ] All required secrets marked with `sync: false`

---

## Testing render.yaml Locally

You can validate the YAML syntax:
```bash
# Check YAML syntax
python3 -c "import yaml; yaml.safe_load(open('render.yaml'))"
```

Or use online validators:
- https://www.yamllint.com/
- https://codebeautify.org/yaml-validator

---

## Common Render Errors

### "Property not found: databaseName"
**Cause**: Using wrong section or structure for database
**Fix**: Use `databases:` section at top level (not `services`)

### "Invalid fromDatabase reference"
**Cause**: Database name doesn't match
**Fix**: Ensure `name: vokab-db` matches in both database definition and `fromDatabase` references

### "Build failed: Cannot find Dockerfile"
**Cause**: Wrong `dockerfilePath` or not in root
**Fix**: Set `dockerfilePath: ./Dockerfile` and `dockerContext: .`

---

## Region Options

Available Render regions:
- `oregon` (US West)
- `ohio` (US East)
- `frankfurt` (Europe)
- `singapore` (Asia)

**Tip**: Use the same region for database and web service for lowest latency.

---

## Blueprint vs Manual Deployment

| Feature | Blueprint (`render.yaml`) | Manual Dashboard |
|---------|---------------------------|------------------|
| Speed | Faster (one-click) | Slower (multiple steps) |
| Version Control | ✅ In Git | ❌ Not tracked |
| Reproducibility | ✅ Easy | ❌ Manual steps |
| Flexibility | Limited by YAML | Full control |
| Updates | Edit YAML, push | Manual changes |

**Recommendation**: Use Blueprint for consistent, reproducible deployments.

---

Last Updated: 2025-10-17

