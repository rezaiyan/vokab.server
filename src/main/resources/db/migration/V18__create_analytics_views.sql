-- Metabase-optimized SQL views. These appear as tables in Metabase and
-- pre-compute the joins and aggregations that would otherwise require complex inline SQL.
-- Requires V17 (users.first_word_added_at / first_review_at) to be applied first.
-- Requires V15 (app_events.properties JSONB) for v_push_notification_performance.

-- =============================================================
-- VIEW: v_dau_wau_mau
-- Daily/weekly/monthly active users derived from study sessions.
-- =============================================================
CREATE VIEW v_dau_wau_mau AS
SELECT
    DATE(TO_TIMESTAMP(started_at / 1000.0))                AS activity_date,
    DATE_TRUNC('week',  TO_TIMESTAMP(started_at / 1000.0)) AS activity_week,
    DATE_TRUNC('month', TO_TIMESTAMP(started_at / 1000.0)) AS activity_month,
    user_id,
    COUNT(*)         AS sessions_count,
    SUM(total_cards) AS cards_reviewed
FROM study_sessions
GROUP BY 1, 2, 3, 4;


-- =============================================================
-- VIEW: v_user_cohorts
-- Each user with their signup cohort week/month and activation metrics.
-- =============================================================
CREATE VIEW v_user_cohorts AS
SELECT
    u.id                                                                        AS user_id,
    DATE_TRUNC('week',  u.created_at)                                           AS cohort_week,
    DATE_TRUNC('month', u.created_at)                                           AS cohort_month,
    u.subscription_status,
    u.first_word_added_at,
    u.first_review_at,
    EXTRACT(EPOCH FROM (u.first_word_added_at - u.created_at)) / 86400.0       AS days_to_first_word,
    EXTRACT(EPOCH FROM (u.first_review_at    - u.created_at)) / 86400.0        AS days_to_first_review
FROM users u;


-- =============================================================
-- VIEW: v_cohort_retention
-- Percentage of each signup cohort retained at day 7, 14, and 30.
-- A user is "retained" on day N if they had at least one session
-- that started on that exact calendar day.
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
    SELECT
        user_id,
        TO_TIMESTAMP(started_at / 1000.0) AS session_time
    FROM study_sessions
)
SELECT
    c.cohort_week,
    COUNT(DISTINCT c.user_id)                       AS cohort_size,
    COUNT(DISTINCT CASE
        WHEN s.session_time >= c.signup_at + INTERVAL '7 days'
         AND s.session_time <  c.signup_at + INTERVAL '8 days'
        THEN c.user_id END)                         AS retained_day_7,
    COUNT(DISTINCT CASE
        WHEN s.session_time >= c.signup_at + INTERVAL '14 days'
         AND s.session_time <  c.signup_at + INTERVAL '15 days'
        THEN c.user_id END)                         AS retained_day_14,
    COUNT(DISTINCT CASE
        WHEN s.session_time >= c.signup_at + INTERVAL '30 days'
         AND s.session_time <  c.signup_at + INTERVAL '31 days'
        THEN c.user_id END)                         AS retained_day_30
FROM cohort_users c
LEFT JOIN session_days s ON s.user_id = c.user_id
GROUP BY 1;


-- =============================================================
-- VIEW: v_subscription_funnel
-- Signup → first word → first review → trial → paid, per cohort week.
-- =============================================================
CREATE VIEW v_subscription_funnel AS
SELECT
    DATE_TRUNC('week', u.created_at)                                              AS cohort_week,
    COUNT(DISTINCT u.id)                                                           AS signups,
    COUNT(DISTINCT CASE WHEN u.first_word_added_at IS NOT NULL THEN u.id END)     AS added_first_word,
    COUNT(DISTINCT CASE WHEN u.first_review_at     IS NOT NULL THEN u.id END)     AS completed_first_review,
    COUNT(DISTINCT CASE WHEN s.is_trial = TRUE  THEN s.user_id END)               AS started_trial,
    COUNT(DISTINCT CASE WHEN s.status = 'ACTIVE'
                         AND s.is_trial = FALSE THEN s.user_id END)               AS converted_to_paid
FROM users u
LEFT JOIN subscriptions s ON s.user_id = u.id
GROUP BY 1;


-- =============================================================
-- VIEW: v_vocabulary_snapshot
-- Cumulative word count per user per day, segmented by subscription tier.
-- Useful for seeing where FREE users plateau (free-tier ceiling).
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
-- Compares daily insights sent vs notification_tapped app events.
-- Requires V15 (app_events.properties as JSONB).
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
