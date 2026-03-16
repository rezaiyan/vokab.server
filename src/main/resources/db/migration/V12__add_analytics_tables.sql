CREATE TABLE study_sessions (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users(id),
    client_session_id TEXT NOT NULL,
    started_at BIGINT NOT NULL,
    ended_at BIGINT,
    duration_ms BIGINT NOT NULL DEFAULT 0,
    total_cards INT NOT NULL DEFAULT 0,
    correct_count INT NOT NULL DEFAULT 0,
    incorrect_count INT NOT NULL DEFAULT 0,
    review_type TEXT NOT NULL DEFAULT 'REVIEW',
    completed_normally BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE(user_id, client_session_id)
);

CREATE TABLE review_events (
    id BIGSERIAL PRIMARY KEY,
    session_id BIGINT NOT NULL REFERENCES study_sessions(id),
    user_id BIGINT NOT NULL REFERENCES users(id),
    word_id BIGINT NOT NULL,
    word_text TEXT NOT NULL DEFAULT '',
    word_translation TEXT NOT NULL DEFAULT '',
    source_language TEXT NOT NULL DEFAULT '',
    target_language TEXT NOT NULL DEFAULT '',
    rating INT NOT NULL,
    previous_level INT NOT NULL,
    new_level INT NOT NULL,
    response_time_ms BIGINT NOT NULL DEFAULT 0,
    reviewed_at BIGINT NOT NULL
);

CREATE INDEX idx_study_sessions_user ON study_sessions(user_id);
CREATE INDEX idx_study_sessions_started ON study_sessions(user_id, started_at);
CREATE INDEX idx_review_events_session ON review_events(session_id);
CREATE INDEX idx_review_events_user ON review_events(user_id);
CREATE INDEX idx_review_events_word ON review_events(user_id, word_id);
CREATE INDEX idx_review_events_reviewed_at ON review_events(user_id, reviewed_at);
CREATE INDEX idx_review_events_level ON review_events(user_id, previous_level);
CREATE INDEX idx_review_events_new_level ON review_events(user_id, new_level);
