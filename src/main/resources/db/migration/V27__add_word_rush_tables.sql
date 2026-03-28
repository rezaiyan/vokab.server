CREATE TABLE word_rush_games (
    id                BIGSERIAL PRIMARY KEY,
    user_id           BIGINT    NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    client_game_id    TEXT      NOT NULL,
    score             INT       NOT NULL DEFAULT 0,
    total_questions   INT       NOT NULL DEFAULT 0,
    correct_count     INT       NOT NULL DEFAULT 0,
    best_streak       INT       NOT NULL DEFAULT 0,
    duration_ms       BIGINT    NOT NULL DEFAULT 0,
    avg_response_ms   BIGINT    NOT NULL DEFAULT 0,
    grade             TEXT      NOT NULL DEFAULT 'D',
    lives_remaining   INT       NOT NULL DEFAULT 0,
    completed_normally BOOLEAN  NOT NULL DEFAULT FALSE,
    played_at         BIGINT    NOT NULL,
    created_at        TIMESTAMP NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_word_rush_user_game UNIQUE (user_id, client_game_id)
);

CREATE INDEX idx_word_rush_user         ON word_rush_games (user_id);
CREATE INDEX idx_word_rush_user_played  ON word_rush_games (user_id, played_at DESC);
CREATE INDEX idx_word_rush_user_streak  ON word_rush_games (user_id, best_streak DESC);
