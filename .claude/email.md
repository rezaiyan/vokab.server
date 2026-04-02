# Email & Newsletter Service

## Overview

Provider-based email system for transactional emails and newsletters. Disabled by default. Supports pluggable providers (Resend, SES, SMTP) via the `EmailProvider` interface.

## Architecture

```
EmailController          (REST API for subscription management)
       │
EmailService             (orchestrator: template rendering, dedup, logging)
       │
  ┌────┴────┐
  │         │
EmailSubscriptionService  EmailProvider (interface)
  │                        ├── ResendEmailProvider
  │                        ├── LogOnlyEmailProvider (default)
  │                        └── (add your own)
  │
  ├── EmailSubscriptionRepository
  ├── EmailLogRepository
  └── EmailTemplateRepository
```

## Database Tables

| Table | Purpose |
|---|---|
| `email_subscriptions` | Per-user opt-in/out per category (newsletter, product_updates, weekly_digest) |
| `email_log` | Audit trail: every email sent, with status, provider ID, errors |
| `email_templates` | DB-managed HTML templates with `{{variable}}` placeholders |

Migration: `V10__create_email_tables.sql`

## Configuration

Environment variables (all optional, email is disabled by default):

```bash
APP_EMAIL_ENABLED=true                          # Master switch
APP_EMAIL_PROVIDER=resend                       # 'resend' or 'log' (default)
APP_EMAIL_API_KEY=re_xxxxxxxxxxxx               # Resend API key
APP_EMAIL_FROM_ADDRESS=Lexicon <noreply@mail.lexicon.app>
APP_EMAIL_REPLY_TO=support@lexicon.app
```

Maps to `EmailConfig` (`app.email.*` prefix in `application.yml`).

## API Endpoints

All require authentication (`Authorization: Bearer <token>`).

### GET /api/v1/email/preferences

Returns the user's email subscription preferences.

```json
{
  "success": true,
  "data": [
    { "category": "newsletter", "subscribed": true },
    { "category": "product_updates", "subscribed": true },
    { "category": "weekly_digest", "subscribed": false }
  ]
}
```

### POST /api/v1/email/subscribe

Subscribe to a category.

```json
// Request
{ "category": "weekly_digest" }

// Response
{ "success": true, "data": { "category": "weekly_digest", "subscribed": true } }
```

### POST /api/v1/email/unsubscribe

Unsubscribe from a category.

```json
// Request
{ "category": "newsletter" }

// Response
{ "success": true, "data": { "category": "newsletter", "subscribed": false } }
```

## Sending Emails

### Templated (recommended)

```kotlin
emailService.sendTemplated(
    userId = user.id!!,
    recipientEmail = user.email,
    templateId = "welcome",
    variables = mapOf("name" to user.name, "streak" to "5"),
    dedupHours = 24  // skip if same template sent within 24h
)
```

This will:
1. Check `emailConfig.enabled`
2. Look up the template by ID (must be `active = true`)
3. Check user's subscription preference for the template's category
4. Dedup against `email_log`
5. Render `{{variables}}` in subject, HTML body, and text body
6. Send via the configured provider
7. Log the result to `email_log`

### Raw (no template)

```kotlin
emailService.sendRaw(
    userId = user.id,
    recipientEmail = user.email,
    category = "transactional",
    templateId = "custom-alert",
    subject = "Your streak is about to end!",
    bodyHtml = "<h1>Hey ${user.name}!</h1><p>...</p>"
)
```

## Email Templates

Templates are stored in `email_templates` table. Insert via migration or admin tooling.

| Field | Description |
|---|---|
| `id` | Unique key, e.g. `welcome`, `weekly_digest`, `streak_lost` |
| `subject` | Email subject with `{{variable}}` support |
| `body_html` | HTML body with `{{variable}}` support |
| `body_text` | Plain text fallback (optional) |
| `category` | Maps to subscription categories |
| `active` | Toggle without deleting |

Example template insert:

```sql
INSERT INTO email_templates (id, name, subject, body_html, category) VALUES
('welcome', 'Welcome Email', 'Welcome to Lexicon, {{name}}!',
 '<h1>Welcome, {{name}}!</h1><p>Start building your vocabulary today.</p>',
 'product_updates');
```

## Subscription Categories

Default categories (initialized on user signup via `EmailSubscriptionService.initDefaults`):

| Category | Description |
|---|---|
| `newsletter` | General announcements, tips, content |
| `product_updates` | New features, releases, changelogs |
| `weekly_digest` | Weekly progress summary |

Add new categories by updating `EmailSubscriptionService.DEFAULT_CATEGORIES`.

## Adding a New Provider

1. Implement `EmailProvider` interface:

```kotlin
@Component
@ConditionalOnProperty(name = ["app.email.provider"], havingValue = "ses")
class SesEmailProvider(private val emailConfig: EmailConfig) : EmailProvider {
    override val name = "ses"
    override fun send(request: EmailSendRequest): EmailSendResult { ... }
}
```

2. Set `APP_EMAIL_PROVIDER=ses` in environment.

Spring auto-selects the matching provider via `@ConditionalOnProperty`.

## Email Log & Monitoring

Every email attempt is logged in `email_log` with status:

| Status | Meaning |
|---|---|
| `QUEUED` | Created, about to send |
| `SENT` | Provider accepted the email |
| `FAILED` | Provider returned error (see `error_message`) |
| `BOUNCED` | Delivery failed (update via webhook) |

Query recent failures:

```sql
SELECT * FROM email_log WHERE status = 'FAILED' ORDER BY created_at DESC LIMIT 20;
```

## Integration Points

To wire email into existing flows:

- **User registration**: Call `emailSubscriptionService.initDefaults(userId)` after creating user
- **Welcome email**: Call `emailService.sendTemplated(userId, email, "welcome", ...)` after registration
- **Weekly digest**: Add a scheduled job in `ScheduledTasks.kt` that queries active users and sends digest
- **Streak reminders**: Use alongside existing push notifications as a fallback channel
