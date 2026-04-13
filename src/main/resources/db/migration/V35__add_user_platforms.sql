CREATE TABLE user_platforms (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    platform VARCHAR(20) NOT NULL,
    app_version VARCHAR(32),
    first_seen_at TIMESTAMP NOT NULL DEFAULT NOW(),
    last_seen_at TIMESTAMP NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_user_platform UNIQUE (user_id, platform)
);
CREATE INDEX idx_user_platforms_user_id ON user_platforms(user_id);
