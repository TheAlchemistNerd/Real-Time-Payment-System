-- Event Store Table
CREATE TABLE IF NOT EXISTS domain_events (
    id BIGSERIAL PRIMARY KEY,
    aggregate_identifier VARCHAR(255) NOT NULL,
    sequence_number BIGINT NOT NULL,
    event_type VARCHAR(255) NOT NULL,
    payload JSONB NOT NULL,
    timestamp TIMESTAMP NOT NULL,
    UNIQUE(aggregate_identifier, sequence_number)
);

-- Read Model Table
CREATE TABLE IF NOT EXISTS transaction_read_model (
    transaction_id VARCHAR(255) PRIMARY KEY,
    user_id VARCHAR(255) NOT NULL,
    amount DECIMAL(19, 2) NOT NULL,
    payment_method VARCHAR(3) NOT NULL,
    description TEXT,
    status VARCHAR(50) NOT NULL,
    created_at TIMESTAMP NOT NULL,
    completed_at TIMESTAMP,
    risk_score DECIMAL(3, 2)
    fraud_reason TEXT,
    payment_gateway_transaction_id VARCHAR(255),
    version BIGINT DEFAULT 0
);

CREATE INDEX idx transaction_read_model_user_id ON transaction_read_model(user_id);
CREATE INDEX idx_transaction_read_model_status ON transaction_read_model(status);
CREATE INDEX idx_transaction_read_model_created_at ON transaction_read_model(created_at);
CREATE INDEX idx_transaction_read_model_user_status ON transaction_read_model(user_id, status);