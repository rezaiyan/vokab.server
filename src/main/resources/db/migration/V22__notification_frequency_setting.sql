ALTER TABLE user_settings
    ADD COLUMN notification_frequency VARCHAR(32) NOT NULL DEFAULT 'DAILY';
