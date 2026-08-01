-- Order query view for CQRS read model
-- Uses H2-compatible syntax (no LATERAL join)
CREATE VIEW order_view AS
SELECT
    o.id,
    o.customer_id,
    o.status,
    o.created_at,
    o.idempotency_key,
    o.version,
    COALESCE(resv.reservation_count, 0) AS reservation_count,
    COALESCE(resv.total_quantity, 0) AS total_quantity,
    latest.step_name AS last_saga_step,
    latest.step_status AS last_saga_status,
    latest.step_created_at AS last_saga_step_at
FROM orders o
LEFT JOIN (
    SELECT order_id, COUNT(*) AS reservation_count, SUM(quantity) AS total_quantity
    FROM inventory_reservation
    GROUP BY order_id
) resv ON resv.order_id = o.id
LEFT JOIN (
    SELECT order_id, step_name, step_status, created_at AS step_created_at,
           ROW_NUMBER() OVER (PARTITION BY order_id ORDER BY created_at DESC) AS rn
    FROM saga_logs
) latest ON latest.order_id = o.id AND latest.rn = 1;
