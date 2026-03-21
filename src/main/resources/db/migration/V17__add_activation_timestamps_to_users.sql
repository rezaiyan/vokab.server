-- Activation timestamps for funnel analysis: signup → first word → first review.
-- Backfilled from existing data on migration; kept current by application hooks.
ALTER TABLE users ADD COLUMN first_word_added_at TIMESTAMP;
ALTER TABLE users ADD COLUMN first_review_at      TIMESTAMP;

-- Backfill first_word_added_at from the earliest word created per user
UPDATE users u
SET first_word_added_at = (
    SELECT MIN(w.created_at) FROM words w WHERE w.user_id = u.id
)
WHERE first_word_added_at IS NULL;

-- Backfill first_review_at from the earliest review_event reviewed_at per user
-- reviewed_at is stored as epoch-milliseconds (BIGINT), so we convert
UPDATE users u
SET first_review_at = TO_TIMESTAMP(
    (SELECT MIN(re.reviewed_at) FROM review_events re WHERE re.user_id = u.id) / 1000.0
)
WHERE first_review_at IS NULL
  AND EXISTS (SELECT 1 FROM review_events re WHERE re.user_id = u.id);

CREATE INDEX idx_users_first_word_added ON users(first_word_added_at);
CREATE INDEX idx_users_first_review      ON users(first_review_at);
