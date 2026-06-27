-- Add items column for JSON-serialized order line items
-- This column stores order items as a JSON TEXT blob, populated by OrderPersistenceAdapter

ALTER TABLE orders ADD COLUMN IF NOT EXISTS items TEXT;

-- Add reservation_ids column for saga reservation tracking
-- This column stores reservation IDs as a JSON TEXT array, populated by OrderPersistenceAdapter

ALTER TABLE orders ADD COLUMN IF NOT EXISTS reservation_ids TEXT;
