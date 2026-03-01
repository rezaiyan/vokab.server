ALTER TABLE users ADD COLUMN longest_streak INT NOT NULL DEFAULT 0;
UPDATE users SET longest_streak = current_streak WHERE current_streak > 0;
