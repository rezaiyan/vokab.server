-- Composite index for countMasteredWordsByUserIds / countMasteredWordsByUserId
-- Replaces the bitmap-AND of idx_words_user_id + idx_words_level
CREATE INDEX IF NOT EXISTS idx_words_user_id_level
    ON words(user_id, level);

-- Index to support due-card DB query
CREATE INDEX IF NOT EXISTS idx_words_user_id_next_review
    ON words(user_id, next_review_date);

-- Index required by the delta-sync query
CREATE INDEX IF NOT EXISTS idx_words_updated_at
    ON words(updated_at);
