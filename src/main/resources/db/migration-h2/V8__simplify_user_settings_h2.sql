-- Remove review customization settings - hardcode optimal values (H2)
ALTER TABLE user_settings DROP COLUMN IF EXISTS successes_to_advance;
ALTER TABLE user_settings DROP COLUMN IF EXISTS forgot_penalty;

-- Remove notification granularity - simplify to on/off + time (H2)
ALTER TABLE user_settings DROP COLUMN IF EXISTS review_reminders;
ALTER TABLE user_settings DROP COLUMN IF EXISTS motivational_messages;
ALTER TABLE user_settings DROP COLUMN IF EXISTS minimum_due_cards;
