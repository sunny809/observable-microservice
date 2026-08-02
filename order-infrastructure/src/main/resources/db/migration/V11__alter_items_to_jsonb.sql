-- V11__alter_items_to_jsonb.sql
-- Convert items and reservation_ids from TEXT to JSONB (PostgreSQL)
-- H2 uses TEXT columns — JPA AttributeConverter handles serialization regardless of column type

-- For PostgreSQL: ALTER TABLE orders ALTER COLUMN items SET DATA TYPE JSONB USING items::JSONB;
-- For PostgreSQL: ALTER TABLE orders ALTER COLUMN reservation_ids SET DATA TYPE JSONB USING reservation_ids::JSONB;
-- H2 does not support JSONB, so we keep TEXT columns and rely on @Convert for type safety.
-- The migration is a no-op for H2; PostgreSQL-specific migrations can use Flyway's
-- db/migration/postgresql/ directory in production.

-- This migration is intentionally empty for H2 compatibility.
-- In production, use Flyway's vendor-specific migration paths:
--   db/migration/common/V11__alter_items_to_jsonb.sql (empty or H2-safe)
--   db/migration/postgresql/V11__alter_items_to_jsonb.sql (actual JSONB ALTER)
-- For now, the JPA AttributeConverter provides type safety regardless of column type.
