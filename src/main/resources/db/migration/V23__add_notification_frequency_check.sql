ALTER TABLE user_settings
    ADD CONSTRAINT chk_notification_frequency
        CHECK (notification_frequency IN ('DAILY', 'EVERY_OTHER_DAY', 'WEEKLY', 'OFF'));
