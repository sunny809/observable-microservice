CREATE TABLE outbox_events (
    id           BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    aggregate_id VARCHAR(36) NOT NULL,
    event_type   VARCHAR(100) NOT NULL,
    payload      TEXT NOT NULL,
    status       VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    created_at   TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    sent_at      TIMESTAMP,
    retry_count  INT NOT NULL DEFAULT 0,
    max_retries  INT NOT NULL DEFAULT 5
);

CREATE INDEX idx_outbox_status_created ON outbox_events(status, created_at);
