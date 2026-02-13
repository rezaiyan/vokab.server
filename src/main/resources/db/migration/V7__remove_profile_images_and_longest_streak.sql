-- Remove profile images - not needed for vocabulary app
ALTER TABLE users DROP COLUMN IF EXISTS profile_image_url;

-- Remove longest streak - keep only current streak
ALTER TABLE users DROP COLUMN IF EXISTS longest_streak;
