-- Prevent duplicate insights for the same user on the same day.
-- The check-then-insert pattern in DailyInsightService is not atomic, so a race condition
-- can produce two rows for the same (user_id, date). This constraint is the last line of
-- defence; callers catch DataIntegrityViolationException and fall back to the existing row.
ALTER TABLE daily_insights
    ADD CONSTRAINT uq_daily_insights_user_date UNIQUE (user_id, date);
