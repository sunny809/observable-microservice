-- Extend saga_logs table with state tracking columns
ALTER TABLE saga_logs
    ADD COLUMN saga_type VARCHAR(50) NOT NULL DEFAULT 'ORDER_PLACEMENT',
    ADD COLUMN step_name VARCHAR(50),
    ADD COLUMN step_status VARCHAR(20),
    ADD COLUMN started_at TIMESTAMP,
    ADD COLUMN completed_at TIMESTAMP,
    ADD COLUMN compensation_status VARCHAR(20),
    ADD COLUMN retry_count INT DEFAULT 0,
    ADD COLUMN next_retry_at TIMESTAMP;

CREATE INDEX idx_saga_logs_status_started_at ON saga_logs(step_status, started_at);
CREATE INDEX idx_saga_logs_order_id ON saga_logs(order_id);
