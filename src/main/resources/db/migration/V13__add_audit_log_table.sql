-- Audit log for security and GDPR compliance events.
-- user_id has NO foreign key constraint so records survive account deletion,
-- preserving proof that deletion occurred (GDPR Art. 5(2) accountability).
CREATE TABLE audit_log (
    id          BIGSERIAL PRIMARY KEY,
    event_type  TEXT      NOT NULL,
    user_id     BIGINT,                                          -- nullable: pre-auth events have no user
    email       TEXT,                                            -- snapshot at event time; user may be deleted later
    ip_address  TEXT,
    user_agent  TEXT,
    metadata    TEXT,                                            -- JSON for event-specific fields (family_id, reason, etc.)
    created_at  TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_audit_log_user_id    ON audit_log(user_id);
CREATE INDEX idx_audit_log_event_type ON audit_log(event_type);
CREATE INDEX idx_audit_log_created_at ON audit_log(created_at);
