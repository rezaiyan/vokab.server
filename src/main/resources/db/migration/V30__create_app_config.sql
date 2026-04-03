-- Runtime configuration table — replaces env-var-based flags.
-- Changes take effect within 30 s (service cache TTL) with no restart.

CREATE TABLE app_config (
    id          BIGSERIAL    PRIMARY KEY,
    namespace   TEXT         NOT NULL DEFAULT 'global',
    key         TEXT         NOT NULL,
    value       TEXT,
    type        TEXT         NOT NULL DEFAULT 'string'
                             CHECK (type IN ('string', 'boolean', 'integer', 'json')),
    description TEXT,
    enabled     BOOLEAN      NOT NULL DEFAULT true,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at  TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    UNIQUE (namespace, key)
);

CREATE INDEX idx_app_config_namespace_key ON app_config (namespace, key);

-- Full audit trail for every value change
CREATE TABLE app_config_history (
    id          BIGSERIAL    PRIMARY KEY,
    namespace   TEXT         NOT NULL,
    key         TEXT         NOT NULL,
    old_value   TEXT,
    new_value   TEXT,
    changed_by  TEXT,
    changed_at  TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_app_config_history_namespace_key ON app_config_history (namespace, key);

-- Seed: existing env-based flags migrate here
INSERT INTO app_config (namespace, key, value, type, description) VALUES
    ('testing',  'test_emails',  '',      'string',  'Comma-separated emails that bypass active check and get auto-premium'),
    ('features', 'push_notifications_enabled', 'true', 'boolean', 'Master switch for push notifications'),
    ('limits',   'ai_suggestion_count',        '50',   'integer', 'Number of vocabulary items AI generates for onboarding');
