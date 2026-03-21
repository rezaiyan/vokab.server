-- Add language pair and trigger source to study_sessions for richer Metabase analysis.
-- trigger_source values: 'manual' | 'notification_tap' | 'scheduled_reminder' | 'unknown'
ALTER TABLE study_sessions ADD COLUMN source_language  TEXT;
ALTER TABLE study_sessions ADD COLUMN target_language  TEXT;
ALTER TABLE study_sessions ADD COLUMN trigger_source   TEXT NOT NULL DEFAULT 'unknown';

CREATE INDEX idx_study_sessions_language ON study_sessions(source_language, target_language);
CREATE INDEX idx_study_sessions_trigger  ON study_sessions(trigger_source);
