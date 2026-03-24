-- Per-user computed timing + suppression state
CREATE TABLE notification_schedule (
    id                    BIGSERIAL PRIMARY KEY,
    user_id               BIGINT NOT NULL,
    optimal_send_hour     SMALLINT NOT NULL DEFAULT 18,   -- 0–23, UTC hour to send
    timezone_offset_hrs   SMALLINT NOT NULL DEFAULT 0,    -- UTC offset derived from behavior
    data_confidence       SMALLINT NOT NULL DEFAULT 0,    -- 0–100: how much data backs the hour
    last_computed_at      TIMESTAMPTZ,                    -- when optimal_send_hour was last recalculated
    last_sent_date        DATE,
    last_sent_type        VARCHAR(64),
    consecutive_ignores   SMALLINT NOT NULL DEFAULT 0,
    suppressed_until      DATE,
    created_at            TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at            TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT fk_ns_user    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    CONSTRAINT uq_ns_user    UNIQUE (user_id),
    CONSTRAINT chk_hour      CHECK (optimal_send_hour BETWEEN 0 AND 23),
    CONSTRAINT chk_confidence CHECK (data_confidence BETWEEN 0 AND 100)
);

CREATE INDEX idx_ns_optimal_hour ON notification_schedule(optimal_send_hour);
CREATE INDEX idx_ns_user_id      ON notification_schedule(user_id);

-- Immutable log of every sent notification (no FK — survives account deletion, GDPR)
CREATE TABLE notification_log (
    id                 BIGSERIAL PRIMARY KEY,
    user_id            BIGINT NOT NULL,
    notification_type  VARCHAR(64) NOT NULL,
    title              VARCHAR(256),
    body               TEXT,
    sent_at            TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    opened_at          TIMESTAMPTZ,
    data_payload       JSONB
);

CREATE INDEX idx_nl_user_sent ON notification_log(user_id, sent_at DESC);
CREATE INDEX idx_nl_opened    ON notification_log(user_id, opened_at) WHERE opened_at IS NOT NULL;
