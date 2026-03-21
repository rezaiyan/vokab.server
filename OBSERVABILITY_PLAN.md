# Observability & Metabase Enrichment Plan

## Current Gaps

| Gap | Impact |
|---|---|
| `app_events` has only 5 event types | Can't compute DAU, feature adoption, funnel |
| `properties` column is `TEXT` | Metabase can't query JSON fields |
| No cohort/retention data | Can't measure day-7 / day-30 retention |
| No activation timestamps on users | Can't build signup → review funnel |
| No session context (language, trigger) | Can't attribute sessions to notifications |
| No pre-built Metabase views | Complex SQL needed for every chart |
| No server health observability | Error/latency data not in Metabase |

---

## Phase 1 — Expand app_events Coverage

**Effort: Very Low | No schema changes | Client + one server-side hook**

### 1.1 New Client-Side Events

The client calls `POST /api/v1/events` with any `eventName` + `properties` map — no server changes needed.

| `event_name` | When to fire | Required properties |
|---|---|---|
| `signup_completed` | First-ever successful auth | `provider: google\|apple` |
| `session_started` | User opens review screen | `review_type: REVIEW\|LEARN`, `language_pair: en-de` |
| `session_completed` | Session sync succeeds | `total_cards`, `correct_count`, `duration_ms`, `completed_normally: true\|false` |
| `word_added` | User saves one or more words | `method: manual\|ai_extraction\|package_import`, `word_count` |
| `word_deleted` | User deletes a word | `word_count` |
| `paywall_shown` | App shows subscription screen | `trigger: word_limit\|feature_gate\|manual`, `current_word_count` |
| `trial_started` | Free trial begins | `product_id` |
| `feature_used` | AI extraction, AI translate, etc. | `feature: ai_image_extraction\|ai_translation\|ai_suggest\|daily_insight`, `success: true\|false` |
| `notification_tapped` | User taps a push notification | `notification_type: daily_insight\|streak_reminder` |
| `streak_milestone` | Streak hits 3/7/14/30/60/90 days | `milestone: 7`, `streak_days: 7` |
| `settings_changed` | User saves settings | `changed_fields: notifications_enabled,theme_mode` |
| `first_review_completed` | Client detects very first session ever completed | _(none)_ |

### 1.2 Server-Side: `signup_completed` Emission

**File:** `src/main/kotlin/com/alirezaiyan/vokab/server/service/AuthService.kt`

Inject `EventService`. In the new-user branch of `findOrCreateUser` and `findOrCreateAppleUser`, emit after `userRepository.save`:

```kotlin
eventService.track(
    savedUser.id!!,
    TrackEventRequest(
        eventName = "signup_completed",
        properties = mapOf("provider" to "google"), // or "apple"
        platform = null,
        appVersion = null,
        clientTimestampMs = Instant.now().toEpochMilli()
    )
)
```

Detect "new user" by checking if `savedUser.createdAt` is within the last 5 seconds, or by returning a `Pair<User, Boolean>(user, isNew)` from the find-or-create methods.

### 1.3 Metabase Dashboards Unlocked

- Daily Active Users — distinct `user_id` in `app_events` where `event_name = 'session_started'` grouped by `DATE(server_timestamp)`
- New Signups Per Day — filter `event_name = 'signup_completed'`
- Feature Adoption — count by `properties->>'feature'` for `feature_used` events _(requires Phase 2.1 JSONB first)_
- Paywall Exposure — count `paywall_shown`, group by `properties->>'trigger'`
- Word-Add Method Breakdown — group `properties->>'method'` for `word_added`

---

## Phase 2 — Three Schema Migrations

**Effort: Low | Value: High | Prerequisite for Phase 3**

### 2.1 V15 — Convert `app_events.properties` to JSONB

**File to create:** `src/main/resources/db/migration/V15__convert_app_events_properties_to_jsonb.sql`

```sql
-- Convert TEXT JSON to true JSONB for Metabase path queries
ALTER TABLE app_events
    ALTER COLUMN properties TYPE JSONB
    USING properties::JSONB;

-- GIN index enables fast containment queries: properties @> '{"method":"manual"}'
CREATE INDEX idx_app_events_properties_gin ON app_events USING GIN (properties);

CREATE INDEX idx_app_events_platform    ON app_events(platform);
CREATE INDEX idx_app_events_server_date ON app_events(DATE(server_timestamp));
```

**File to update:** `src/main/kotlin/com/alirezaiyan/vokab/server/domain/entity/AppEvent.kt`

Change `@Column(columnDefinition = "TEXT")` → `@Column(columnDefinition = "jsonb")` on the `properties` field.

No service changes — `ObjectMapper.writeValueAsString(Map)` already produces valid JSONB input.

---

### 2.2 V16 — Session Context Columns on `study_sessions`

**File to create:** `src/main/resources/db/migration/V16__add_session_context_columns.sql`

```sql
ALTER TABLE study_sessions ADD COLUMN source_language TEXT;
ALTER TABLE study_sessions ADD COLUMN target_language TEXT;

-- How the session was triggered
-- Values: 'manual' | 'notification_tap' | 'scheduled_reminder' | 'unknown'
ALTER TABLE study_sessions ADD COLUMN trigger_source TEXT NOT NULL DEFAULT 'unknown';

CREATE INDEX idx_study_sessions_language ON study_sessions(source_language, target_language);
CREATE INDEX idx_study_sessions_trigger  ON study_sessions(trigger_source);
```

**File to update:** `src/main/kotlin/com/alirezaiyan/vokab/server/domain/entity/StudySession.kt`

Add three new optional fields:

```kotlin
@Column(name = "source_language")
val sourceLanguage: String? = null,

@Column(name = "target_language")
val targetLanguage: String? = null,

@Column(name = "trigger_source", nullable = false)
val triggerSource: String = "unknown",
```

**File to update:** Sync DTO inside `src/main/kotlin/com/alirezaiyan/vokab/server/presentation/dto/AnalyticsDto.kt`

Add optional fields to the session request DTO (whichever inner class maps to `study_sessions` in `SyncAnalyticsRequest`):

```kotlin
val sourceLanguage: String? = null,
val targetLanguage: String? = null,
val triggerSource: String = "unknown",
```

**File to update:** `src/main/kotlin/com/alirezaiyan/vokab/server/service/AnalyticsService.kt`

Pass the new fields when constructing `StudySession` inside `syncSessions`.

---

### 2.3 V17 — Activation Timestamps on `users`

**File to create:** `src/main/resources/db/migration/V17__add_activation_timestamps_to_users.sql`

```sql
ALTER TABLE users ADD COLUMN first_word_added_at TIMESTAMP;
ALTER TABLE users ADD COLUMN first_review_at      TIMESTAMP;

-- Backfill from existing data
UPDATE users u
SET first_word_added_at = (
    SELECT MIN(w.created_at) FROM words w WHERE w.user_id = u.id
)
WHERE first_word_added_at IS NULL;

UPDATE users u
SET first_review_at = TO_TIMESTAMP(
    (SELECT MIN(re.reviewed_at) FROM review_events re WHERE re.user_id = u.id) / 1000.0
)
WHERE first_review_at IS NULL
  AND EXISTS (SELECT 1 FROM review_events re WHERE re.user_id = u.id);

CREATE INDEX idx_users_first_word_added ON users(first_word_added_at);
CREATE INDEX idx_users_first_review      ON users(first_review_at);
```

**File to update:** `src/main/kotlin/com/alirezaiyan/vokab/server/domain/entity/User.kt`

Add two nullable fields:

```kotlin
@Column(name = "first_word_added_at")
val firstWordAddedAt: Instant? = null,

@Column(name = "first_review_at")
val firstReviewAt: Instant? = null,
```

**File to update:** `src/main/kotlin/com/alirezaiyan/vokab/server/service/WordService.kt`

After saving a word, if `user.firstWordAddedAt == null`:

```kotlin
userRepository.save(user.copy(firstWordAddedAt = Instant.now()))
```

**File to update:** `src/main/kotlin/com/alirezaiyan/vokab/server/service/AnalyticsService.kt`

In `syncSessions`, after saving a session, if this user has no `firstReviewAt`:

```kotlin
if (user.firstReviewAt == null) {
    userRepository.save(user.copy(firstReviewAt = Instant.now()))
}
```

### 2.4 Metabase Dashboards Unlocked

- **Activation Funnel** — `users.created_at` → `first_word_added_at` → `first_review_at` → `subscriptions.started_at`
- **Session Language Breakdown** — group `study_sessions` by `source_language`, `target_language`
- **Trigger Source Effectiveness** — avg `total_cards` for `notification_tap` sessions vs. `manual`
- **Time-to-First-Word Histogram** — `EXTRACT(EPOCH FROM (first_word_added_at - created_at)) / 3600` (hours)
- **Time-to-First-Review Histogram** — same pattern with `first_review_at`

---

## Phase 3 — Metabase-Optimized SQL Views

**Effort: Low (pure SQL) | Value: Very High | Requires V17 to run first**

**File to create:** `src/main/resources/db/migration/V18__create_analytics_views.sql`

```sql
-- =============================================================
-- VIEW: v_dau_wau_mau
-- Daily/weekly/monthly active users from session activity
-- =============================================================
CREATE VIEW v_dau_wau_mau AS
SELECT
    DATE(TO_TIMESTAMP(started_at / 1000.0))             AS activity_date,
    DATE_TRUNC('week',  TO_TIMESTAMP(started_at / 1000.0)) AS activity_week,
    DATE_TRUNC('month', TO_TIMESTAMP(started_at / 1000.0)) AS activity_month,
    user_id,
    COUNT(*)          AS sessions_count,
    SUM(total_cards)  AS cards_reviewed
FROM study_sessions
GROUP BY 1, 2, 3, 4;


-- =============================================================
-- VIEW: v_user_cohorts
-- Each user with their signup cohort + activation metrics
-- =============================================================
CREATE VIEW v_user_cohorts AS
SELECT
    u.id                                              AS user_id,
    DATE_TRUNC('week',  u.created_at)                 AS cohort_week,
    DATE_TRUNC('month', u.created_at)                 AS cohort_month,
    u.subscription_status,
    u.first_word_added_at,
    u.first_review_at,
    EXTRACT(EPOCH FROM (u.first_word_added_at - u.created_at)) / 86400.0 AS days_to_first_word,
    EXTRACT(EPOCH FROM (u.first_review_at    - u.created_at)) / 86400.0  AS days_to_first_review
FROM users u;


-- =============================================================
-- VIEW: v_cohort_retention
-- % of each signup cohort retained at day 7 / 14 / 30
-- =============================================================
CREATE VIEW v_cohort_retention AS
WITH cohort_users AS (
    SELECT
        DATE_TRUNC('week', created_at) AS cohort_week,
        id                             AS user_id,
        created_at                     AS signup_at
    FROM users
),
session_days AS (
    SELECT user_id, TO_TIMESTAMP(started_at / 1000.0) AS session_time
    FROM study_sessions
)
SELECT
    c.cohort_week,
    COUNT(DISTINCT c.user_id) AS cohort_size,
    COUNT(DISTINCT CASE
        WHEN s.session_time >= c.signup_at + INTERVAL '7 days'
         AND s.session_time <  c.signup_at + INTERVAL '8 days'
        THEN c.user_id END)   AS retained_day_7,
    COUNT(DISTINCT CASE
        WHEN s.session_time >= c.signup_at + INTERVAL '14 days'
         AND s.session_time <  c.signup_at + INTERVAL '15 days'
        THEN c.user_id END)   AS retained_day_14,
    COUNT(DISTINCT CASE
        WHEN s.session_time >= c.signup_at + INTERVAL '30 days'
         AND s.session_time <  c.signup_at + INTERVAL '31 days'
        THEN c.user_id END)   AS retained_day_30
FROM cohort_users c
LEFT JOIN session_days s ON s.user_id = c.user_id
GROUP BY 1;


-- =============================================================
-- VIEW: v_subscription_funnel
-- Signup → first word → first review → trial → paid, per cohort week
-- =============================================================
CREATE VIEW v_subscription_funnel AS
SELECT
    DATE_TRUNC('week', u.created_at)                                         AS cohort_week,
    COUNT(DISTINCT u.id)                                                      AS signups,
    COUNT(DISTINCT CASE WHEN u.first_word_added_at IS NOT NULL THEN u.id END) AS added_first_word,
    COUNT(DISTINCT CASE WHEN u.first_review_at     IS NOT NULL THEN u.id END) AS completed_first_review,
    COUNT(DISTINCT CASE WHEN s.is_trial = TRUE  THEN s.user_id END)           AS started_trial,
    COUNT(DISTINCT CASE WHEN s.status = 'ACTIVE'
                         AND s.is_trial = FALSE THEN s.user_id END)           AS converted_to_paid
FROM users u
LEFT JOIN subscriptions s ON s.user_id = u.id
GROUP BY 1;


-- =============================================================
-- VIEW: v_vocabulary_snapshot
-- Cumulative word count per user over time
-- =============================================================
CREATE VIEW v_vocabulary_snapshot AS
SELECT
    DATE(w.created_at)      AS snapshot_date,
    w.user_id,
    u.subscription_status,
    COUNT(*)                AS words_added_on_date,
    SUM(COUNT(*)) OVER (
        PARTITION BY w.user_id
        ORDER BY DATE(w.created_at)
    )                       AS cumulative_word_count
FROM words w
JOIN users u ON u.id = w.user_id
GROUP BY 1, 2, 3;


-- =============================================================
-- VIEW: v_push_notification_performance
-- Daily insights sent vs notification_tapped events → tap rate
-- Requires app_events.properties to be JSONB (V15)
-- =============================================================
CREATE VIEW v_push_notification_performance AS
SELECT
    di.date::DATE                  AS insight_date,
    COUNT(DISTINCT di.id)          AS insights_sent,
    COUNT(DISTINCT ae.id)          AS taps,
    CASE
        WHEN COUNT(DISTINCT di.id) > 0
        THEN ROUND(COUNT(DISTINCT ae.id)::NUMERIC / COUNT(DISTINCT di.id) * 100, 1)
        ELSE 0
    END                            AS tap_rate_pct
FROM daily_insights di
LEFT JOIN app_events ae
       ON ae.user_id      = di.user_id
      AND ae.event_name   = 'notification_tapped'
      AND DATE(ae.server_timestamp) = di.date::DATE
      AND ae.properties->>'notification_type' = 'daily_insight'
WHERE di.sent_via_push = TRUE
GROUP BY 1;
```

### 3.1 Metabase Dashboards Unlocked

- **Cohort Retention Heatmap** — `v_cohort_retention`, pivot `cohort_week` as rows, `retained_day_7/14/30` as columns
- **Signup-to-Paid Funnel** — `v_subscription_funnel`, waterfall bar chart per cohort week
- **DAU/WAU/MAU Trends** — `v_dau_wau_mau`, three overlaid line charts
- **Push Notification CTR by Date** — `v_push_notification_performance`, line chart
- **Vocabulary Growth Curve** — `v_vocabulary_snapshot`, line chart segmented by `subscription_status`
- **Free-User Vocabulary Ceiling** — `v_vocabulary_snapshot` where `subscription_status = 'FREE'`, distribution of `cumulative_word_count` — reveals where to set the free tier limit

---

## Phase 4 — Paywall & Subscription Event Hooks

**Effort: Medium | Value: High | Direct revenue insight**

### 4.1 `trackAsync` on EventService

**File to update:** `src/main/kotlin/com/alirezaiyan/vokab/server/service/EventService.kt`

Add a fire-and-forget async wrapper (identical to `AuditLogService` pattern):

```kotlin
@Async
fun trackAsync(userId: Long, eventName: String, properties: Map<String, String>) {
    runCatching {
        track(userId, TrackEventRequest(
            eventName = eventName,
            properties = properties,
            platform = null,
            appVersion = null,
            clientTimestampMs = Instant.now().toEpochMilli()
        ))
    }.onFailure { logger.warn { "Failed to track event '$eventName' for user $userId: $it" } }
}
```

Use `trackAsync` for all hot-path callers (word upsert, paywall gate) so the request thread is never blocked.

### 4.2 Paywall-Triggered Server Event

Find where the free-tier word-limit 403 is thrown (likely `FeatureAccessService` or a word-count check in `WordService`). Inject `EventService` there and emit:

```kotlin
eventService.trackAsync(
    userId,
    "paywall_triggered",
    mapOf(
        "trigger" to "word_limit",
        "current_word_count" to wordCount.toString(),
        "limit" to freeLimit.toString()
    )
)
```

### 4.3 Subscription Lifecycle Events

**File to update:** `src/main/kotlin/com/alirezaiyan/vokab/server/service/SubscriptionService.kt`

Inject `EventService`. After each handler saves:

```kotlin
// handleInitialPurchase
eventService.trackAsync(userId, if (isTrial) "trial_started" else "subscription_started",
    mapOf("product_id" to productId, "is_trial" to isTrial.toString()))

// handleRenewal
eventService.trackAsync(userId, "subscription_renewed", mapOf("product_id" to productId))

// handleCancellation
eventService.trackAsync(userId, "subscription_cancelled", mapOf("product_id" to productId))

// handleExpiration
eventService.trackAsync(userId, "subscription_expired", mapOf("product_id" to productId))
```

### 4.4 Metabase Dashboards Unlocked

- **Paywall → Conversion Rate** — count `paywall_triggered` per user, join `subscriptions.started_at` within 48 hours
- **Trial Conversion Rate** — users who had `trial_started` then later `subscription_started` with `is_trial=false`
- **Subscription Churn Over Time** — count `subscription_cancelled` per week from `app_events`
- **Word Count at Paywall Trigger** — histogram of `properties->>'current_word_count'` for `paywall_triggered` — data-driven free limit decision

---

## Phase 5 — Application Health Observability

**Effort: Medium | Value: Medium | Ops visibility in Metabase**

### 5.1 V20 Migration — `server_request_log` Table

**File to create:** `src/main/resources/db/migration/V20__create_server_request_log.sql`

```sql
-- Sampled/flagged request telemetry. Written for:
--   (a) all requests with status >= 400
--   (b) requests with duration_ms > 500
--   (c) ~1% sample of all others
CREATE TABLE server_request_log (
    id               BIGSERIAL PRIMARY KEY,
    request_id       TEXT NOT NULL,
    user_id          BIGINT,           -- no FK, survives deletion
    method           TEXT NOT NULL,
    endpoint         TEXT NOT NULL,   -- normalized: /api/v1/words/:id (not /api/v1/words/12345)
    status_code      INT NOT NULL,
    duration_ms      BIGINT NOT NULL,
    error_type       TEXT,            -- exception class name for 5xx responses
    platform         TEXT,
    app_version      TEXT,
    server_timestamp TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_srl_endpoint ON server_request_log(endpoint, server_timestamp);
CREATE INDEX idx_srl_status   ON server_request_log(status_code, server_timestamp);
CREATE INDEX idx_srl_duration ON server_request_log(duration_ms DESC);
CREATE INDEX idx_srl_user     ON server_request_log(user_id, server_timestamp);
```

### 5.2 New Entity

**File to create:** `src/main/kotlin/com/alirezaiyan/vokab/server/domain/entity/ServerRequestLog.kt`

Follow `AppEvent.kt` structure — `@Entity`, `@Table(name = "server_request_log")`, no FK on `userId`.

### 5.3 New Repository

**File to create:** `src/main/kotlin/com/alirezaiyan/vokab/server/domain/repository/ServerRequestLogRepository.kt`

```kotlin
interface ServerRequestLogRepository : JpaRepository<ServerRequestLog, Long>
```

### 5.4 Extend `RequestLoggingFilter`

**File to update:** `src/main/kotlin/com/alirezaiyan/vokab/server/logging/RequestLoggingFilter.kt`

Inject `ServerRequestLogRepository`. After the existing slow-request warn log, persist with sampling:

```kotlin
val shouldPersist = statusCode >= 400
    || executionTime > 500
    || ThreadLocalRandom.current().nextInt(100) == 0

if (shouldPersist) {
    val normalizedPath = request.requestURI.replace(Regex("\\b\\d+\\b"), ":id")
    serverRequestLogRepository.save(ServerRequestLog(
        requestId   = requestId,
        userId      = resolveUserId(request), // extract from JWT claim if present
        method      = request.method,
        endpoint    = normalizedPath,
        statusCode  = statusCode,
        durationMs  = executionTime,
        errorType   = errorType, // set from exception attribute if 5xx
        platform    = request.getHeader("X-Platform"),
        appVersion  = request.getHeader("X-App-Version"),
    ))
}
```

Wrap the save in `@Async` or use `CompletableFuture.runAsync` so request threads are never blocked.

### 5.5 Metabase Dashboards Unlocked

- **Error Rate by Endpoint** — count `status_code >= 500` grouped by `endpoint`, `DATE(server_timestamp)`
- **P95 Latency by Endpoint** — `PERCENTILE_CONT(0.95) WITHIN GROUP (ORDER BY duration_ms)`
- **4xx vs 5xx Trend** — stacked area chart by day
- **Slowest Endpoints This Week** — sort by `AVG(duration_ms) DESC`, filter last 7 days
- **Error Spike Detection** — compare this hour's error rate vs. 7-day rolling average for same hour-of-day

---

## Phase 6 — Materialized Views + Nightly Refresh

**Effort: Low | Value: Medium (Metabase query performance)**

### 6.1 V21 Migration — Materialized Views

**File to create:** `src/main/resources/db/migration/V21__create_materialized_analytics_views.sql`

```sql
-- Replace the two most expensive regular views with materialized versions
DROP VIEW IF EXISTS v_cohort_retention;
DROP VIEW IF EXISTS v_subscription_funnel;

CREATE MATERIALIZED VIEW mv_cohort_retention AS
-- (same SQL as v_cohort_retention in V18)
WITH cohort_users AS ( ... ),
session_days     AS ( ... )
SELECT ...;

-- Unique index required for REFRESH CONCURRENTLY
CREATE UNIQUE INDEX ON mv_cohort_retention(cohort_week);
CREATE        INDEX ON mv_cohort_retention(retained_day_7, retained_day_14, retained_day_30);

CREATE MATERIALIZED VIEW mv_subscription_funnel AS
-- (same SQL as v_subscription_funnel in V18)
SELECT ...;

CREATE UNIQUE INDEX ON mv_subscription_funnel(cohort_week);
```

### 6.2 Nightly Refresh Scheduler

**File to create:** `src/main/kotlin/com/alirezaiyan/vokab/server/scheduler/AnalyticsRefreshScheduler.kt`

Follow the pattern of the existing `StreakReminderScheduler`:

```kotlin
@Component
class AnalyticsRefreshScheduler(private val jdbcTemplate: JdbcTemplate) {

    private val logger = KotlinLogging.logger {}

    @Scheduled(cron = "0 30 3 * * *") // 3:30 AM daily
    fun refreshMaterializedViews() {
        logger.info { "Refreshing materialized analytics views..." }
        runCatching {
            jdbcTemplate.execute("REFRESH MATERIALIZED VIEW CONCURRENTLY mv_cohort_retention")
            jdbcTemplate.execute("REFRESH MATERIALIZED VIEW CONCURRENTLY mv_subscription_funnel")
            logger.info { "Materialized views refreshed." }
        }.onFailure {
            logger.error(it) { "Failed to refresh materialized views" }
        }
    }
}
```

---

## Phase 7 — Acquisition & Platform Signals

**Effort: Very Low | Value: Medium | Client-side only, no server changes**

The client fires one event at the very first app open. Stored in `app_events` with no schema change.

```json
{
    "eventName": "app_installed",
    "properties": {
        "install_source": "app_store_organic | web_landing | referral_code | unknown",
        "referral_code": "optional"
    }
}
```

`platform` and `app_version` are already stored on every `app_events` row.

### Metabase Dashboards Unlocked

- **Platform Mix** — iOS vs. Android from `app_events.platform`
- **App Version Distribution** — count active users by `app_version`
- **Install Source Breakdown** — group `app_installed` events by `properties->>'install_source'`

---

## Implementation Order

```
Phase 1   → No migration. Client events + signup_completed server hook.
Phase 2.1 → V15 (JSONB). Prerequisite for Phase 3 views.
Phase 2.2 → V16 (session context columns + entity + DTO + service update).
Phase 2.3 → V17 (activation timestamps + backfill + entity + word/session hooks).
Phase 3   → V18 (all analytics views — requires V17 complete).
Phase 4   → trackAsync + paywall hook + subscription lifecycle events.
Phase 5   → V20 + ServerRequestLog entity + RequestLoggingFilter update.
Phase 6   → V21 (materialized views) + AnalyticsRefreshScheduler.
Phase 7   → Client-side app_installed event.
```

## Key Files Touched

| File | Phases |
|---|---|
| `service/AuthService.kt` | 1 |
| `service/EventService.kt` | 4 |
| `service/WordService.kt` | 2.3 |
| `service/AnalyticsService.kt` | 2.2, 2.3 |
| `service/SubscriptionService.kt` | 4 |
| `domain/entity/AppEvent.kt` | 2.1 |
| `domain/entity/StudySession.kt` | 2.2 |
| `domain/entity/User.kt` | 2.3 |
| `domain/entity/ServerRequestLog.kt` | 5 _(new)_ |
| `domain/repository/ServerRequestLogRepository.kt` | 5 _(new)_ |
| `presentation/dto/AnalyticsDto.kt` | 2.2 |
| `logging/RequestLoggingFilter.kt` | 5 |
| `scheduler/AnalyticsRefreshScheduler.kt` | 6 _(new)_ |
| `db/migration/V15__*.sql` | 2.1 |
| `db/migration/V16__*.sql` | 2.2 |
| `db/migration/V17__*.sql` | 2.3 |
| `db/migration/V18__*.sql` | 3 |
| `db/migration/V20__*.sql` | 5 |
| `db/migration/V21__*.sql` | 6 |