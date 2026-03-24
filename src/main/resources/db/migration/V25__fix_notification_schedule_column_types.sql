ALTER TABLE notification_schedule
    ALTER COLUMN optimal_send_hour   TYPE INTEGER,
    ALTER COLUMN timezone_offset_hrs TYPE INTEGER,
    ALTER COLUMN data_confidence     TYPE INTEGER;
