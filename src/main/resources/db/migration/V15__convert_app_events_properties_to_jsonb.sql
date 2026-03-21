-- Convert app_events.properties from TEXT to JSONB so Metabase can query
-- individual fields with the -> / ->> operators and GIN index containment queries.
ALTER TABLE app_events
    ALTER COLUMN properties TYPE JSONB
    USING properties::JSONB;

-- GIN index enables fast containment queries: properties @> '{"method":"manual"}'
CREATE INDEX idx_app_events_properties_gin ON app_events USING GIN (properties);

CREATE INDEX idx_app_events_platform    ON app_events(platform);
CREATE INDEX idx_app_events_server_date ON app_events(DATE(server_timestamp));
