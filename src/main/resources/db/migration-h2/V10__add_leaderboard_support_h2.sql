ALTER TABLE users ADD COLUMN display_alias VARCHAR(50);
CREATE INDEX idx_users_longest_streak ON users(longest_streak DESC);
CREATE INDEX idx_users_current_streak ON users(current_streak DESC);
