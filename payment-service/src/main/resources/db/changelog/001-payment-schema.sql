CREATE TABLE domain_events (
    id BIGSERIAL PRIMARY KEY,
    aggregate_identifier VARCHAR(255) NOT NULL,
    sequence_number BIGINT NOT NULL,
    event_type VARCHAR(255) NOT NULL,
    payload JSONB NOT NULL,
    timestamp TIMESTAMP NOT NULL,
    UNIQUE (aggregate_identifier, sequence_number)
);

CREATE TABLE transaction_read_model (
    transaction_id VARCHAR(255) PRIMARY KEY,
    user_id VARCHAR(255) NOT NULL,
    amount NUMERIC(19, 2) NOT NULL,
    currency VARCHAR(3) NOT NULL,
    payment_method VARCHAR(255) NOT NULL,
    description TEXT,
    status VARCHAR(255) NOT NULL,
    created_at TIMESTAMP NOT NULL,
    completed_at TIMESTAMP,
    risk_score DOUBLE PRECISION,
    fraud_reason TEXT,
    payment_gateway_transaction_id VARCHAR(255),
    version BIGINT NOT NULL DEFAULT 0
);

CREATE INDEX idx_transaction_read_model_user_status ON transaction_read_model (user_id, status);
CREATE INDEX idx_transaction_read_model_status ON transaction_read_model (status);
CREATE INDEX idx_transaction_read_model_created_at ON transaction_read_model (created_at);
