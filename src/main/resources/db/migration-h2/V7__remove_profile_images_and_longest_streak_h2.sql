-- Remove profile images - not needed for vocabulary app (H2)
ALTER TABLE users DROP COLUMN IF EXISTS profile_image_url;

-- Remove longest streak - keep only current streak (H2)
ALTER TABLE users DROP COLUMN IF EXISTS longest_streak;
