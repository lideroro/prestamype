CREATE TABLE collection_batch (
    id UUID PRIMARY KEY,
    cutoff_date DATE NOT NULL,
    horizon_days INT NOT NULL,
    horizon_end_date DATE NOT NULL,
    status VARCHAR(20) NOT NULL,
    processed_orders INT NOT NULL DEFAULT 0,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE TABLE collection_order (
    id UUID PRIMARY KEY,
    last_batch_id UUID NOT NULL REFERENCES collection_batch(id),
    fineract_client_id BIGINT NOT NULL,
    client_external_id VARCHAR(100),
    client_name VARCHAR(200) NOT NULL,
    fineract_loan_id BIGINT NOT NULL,
    loan_account_no VARCHAR(100) NOT NULL,
    loan_external_id VARCHAR(100),
    installment_number INT NOT NULL,
    due_date DATE NOT NULL,
    currency_code VARCHAR(3) NOT NULL,
    principal_due NUMERIC(19, 2) NOT NULL,
    interest_due NUMERIC(19, 2) NOT NULL,
    total_due NUMERIC(19, 2) NOT NULL,
    status VARCHAR(20) NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT uq_loan_installment UNIQUE (fineract_loan_id, installment_number)
);

CREATE INDEX idx_order_loan ON collection_order(fineract_loan_id);
CREATE INDEX idx_order_due_date ON collection_order(due_date);
CREATE INDEX idx_order_status ON collection_order(status);
