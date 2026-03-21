-- App events table for business analytics queryable via Metabase.
-- Tracks high-value client-side events (login, onboarding, subscription, import, account deletion).
-- user_id has NO foreign key so records survive account deletion.
CREATE TABLE app_events (
    id               BIGSERIAL PRIMARY KEY,
    user_id          BIGINT,                                         -- nullable: no FK, survives account deletion
    event_name       VARCHAR(100) NOT NULL,
    properties       TEXT,                                           -- JSON string for event-specific params
    platform         VARCHAR(20),                                    -- 'android' | 'ios'
    app_version      VARCHAR(30),
    client_timestamp TIMESTAMP NOT NULL,
    server_timestamp TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_at       TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_app_events_user_id         ON app_events(user_id);
CREATE INDEX idx_app_events_event_name      ON app_events(event_name);
CREATE INDEX idx_app_events_client_ts       ON app_events(client_timestamp);
CREATE INDEX idx_app_events_user_event      ON app_events(user_id, event_name);
