CREATE TABLE IF NOT EXISTS bank_closed_days (
    closed_date DATE PRIMARY KEY,
    reason VARCHAR(500) NOT NULL,
    configured_by VARCHAR(255) NOT NULL,
    configured_at TIMESTAMPTZ NOT NULL
);
