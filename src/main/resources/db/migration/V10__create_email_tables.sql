-- Email/Newsletter infrastructure tables

-- Email subscription preferences (opt-in/opt-out per category)
CREATE TABLE email_subscriptions (
    id          BIGSERIAL PRIMARY KEY,
    user_id     BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    category    VARCHAR(50) NOT NULL,  -- e.g. 'newsletter', 'product_updates', 'streak_summary', 'weekly_digest'
    subscribed  BOOLEAN NOT NULL DEFAULT TRUE,
    created_at  TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at  TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE(user_id, category)
);

CREATE INDEX idx_email_sub_user ON email_subscriptions(user_id);
CREATE INDEX idx_email_sub_category ON email_subscriptions(category) WHERE subscribed = TRUE;

-- Email send log (audit trail + dedup)
CREATE TABLE email_log (
    id              BIGSERIAL PRIMARY KEY,
    user_id         BIGINT REFERENCES users(id) ON DELETE SET NULL,
    recipient_email VARCHAR(255) NOT NULL,
    category        VARCHAR(50) NOT NULL,
    template_id     VARCHAR(100) NOT NULL,
    subject         VARCHAR(500) NOT NULL,
    status          VARCHAR(20) NOT NULL DEFAULT 'QUEUED',  -- QUEUED, SENT, FAILED, BOUNCED
    provider        VARCHAR(50),          -- e.g. 'resend', 'ses', 'smtp'
    provider_id     VARCHAR(255),         -- external message ID from provider
    error_message   TEXT,
    sent_at         TIMESTAMP,
    created_at      TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_email_log_user ON email_log(user_id);
CREATE INDEX idx_email_log_status ON email_log(status);
CREATE INDEX idx_email_log_category_created ON email_log(category, created_at DESC);

-- Email templates (optional, for DB-managed templates)
CREATE TABLE email_templates (
    id          VARCHAR(100) PRIMARY KEY,  -- e.g. 'welcome', 'weekly_digest', 'streak_lost'
    name        VARCHAR(255) NOT NULL,
    subject     VARCHAR(500) NOT NULL,
    body_html   TEXT NOT NULL,
    body_text   TEXT,
    category    VARCHAR(50) NOT NULL,
    active      BOOLEAN NOT NULL DEFAULT TRUE,
    created_at  TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at  TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);
