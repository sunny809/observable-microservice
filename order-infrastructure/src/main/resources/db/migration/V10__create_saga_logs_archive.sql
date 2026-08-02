-- V10__create_saga_logs_archive.sql
-- Archive table for completed saga logs older than 30 days
CREATE TABLE saga_logs_archive (
    id BIGINT PRIMARY KEY,
    order_id VARCHAR(36) NOT NULL,
    step VARCHAR(255) NOT NULL,
    detail TEXT NOT NULL,
    created_at TIMESTAMP NOT NULL,
    saga_type VARCHAR(50),
    step_name VARCHAR(50),
    step_status VARCHAR(20),
    started_at TIMESTAMP,
    completed_at TIMESTAMP,
    compensation_status VARCHAR(20),
    retry_count INT,
    next_retry_at TIMESTAMP,
    previous_status VARCHAR(32),
    new_status VARCHAR(32),
    changed_by VARCHAR(100)
);

CREATE INDEX idx_saga_logs_archive_order_id ON saga_logs_archive(order_id);
