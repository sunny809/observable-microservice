-- Create compensation_logs table for idempotent compensation tracking
CREATE TABLE compensation_logs (
    idempotency_key VARCHAR(255) PRIMARY KEY,
    order_id VARCHAR(36) NOT NULL,
    step_name VARCHAR(50) NOT NULL,
    reservation_id VARCHAR(255),
    status VARCHAR(20) NOT NULL,
    attempted_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    error_message TEXT
);

CREATE INDEX idx_compensation_logs_order_id ON compensation_logs(order_id);
