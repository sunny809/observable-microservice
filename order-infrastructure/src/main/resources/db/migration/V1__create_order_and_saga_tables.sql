CREATE TABLE orders (
  id VARCHAR(36) PRIMARY KEY,
  customer_id VARCHAR(255) NOT NULL,
  idempotency_key VARCHAR(255) NOT NULL UNIQUE,
  reservation_id VARCHAR(36),
  status VARCHAR(32) NOT NULL,
  created_at TIMESTAMP NOT NULL
);

CREATE TABLE saga_logs (
  id SERIAL PRIMARY KEY,
  order_id VARCHAR(36) NOT NULL,
  step VARCHAR(255) NOT NULL,
  detail TEXT NOT NULL,
  created_at TIMESTAMP NOT NULL
);

CREATE TABLE inventory_reservation (
  reservation_id VARCHAR(36) PRIMARY KEY,
  sku VARCHAR(255) NOT NULL,
  quantity INTEGER NOT NULL,
  order_id VARCHAR(36) NOT NULL,
  status VARCHAR(32) NOT NULL,
  created_time TIMESTAMP NOT NULL,
  confirmed_time TIMESTAMP
);
