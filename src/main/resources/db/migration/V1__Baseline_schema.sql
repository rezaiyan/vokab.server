-- Baseline migration: This migration assumes existing tables are already in place
-- It will only run if Flyway baseline hasn't been set, otherwise it's a no-op
-- This ensures backward compatibility with existing production databases

-- Note: This migration intentionally does not create tables
-- Existing production databases already have these tables created via Hibernate ddl-auto
-- Flyway will baseline at version 1, marking existing schema as migrated

