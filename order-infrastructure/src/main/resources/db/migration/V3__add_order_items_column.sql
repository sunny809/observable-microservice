-- Add items column for JSON-serialized order line items
-- This column stores order items as a JSON TEXT blob, populated by OrderPersistenceAdapter

ALTER TABLE orders ADD COLUMN IF NOT EXISTS items TEXT;
