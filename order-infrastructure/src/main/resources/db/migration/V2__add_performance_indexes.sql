-- Performance indexes for high-throughput order operations

-- Index for customer order lookups
CREATE INDEX IF NOT EXISTS idx_orders_customer_id ON orders(customer_id);

-- Index for order status filtering
CREATE INDEX IF NOT EXISTS idx_orders_status ON orders(status);

-- Index for saga log lookups by order
CREATE INDEX IF NOT EXISTS idx_saga_logs_order_id ON saga_logs(order_id);

-- Index for inventory reservation lookups by order
CREATE INDEX IF NOT EXISTS idx_inventory_reservation_order_id ON inventory_reservation(order_id);
