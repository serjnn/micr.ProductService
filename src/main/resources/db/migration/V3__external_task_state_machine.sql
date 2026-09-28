-- Migration V3: Add external_task_state table for Two-Phase REST Reconciliation State Machine

CREATE TABLE IF NOT EXISTS external_task_state (
    id BIGSERIAL PRIMARY KEY,
    business_key UUID NOT NULL UNIQUE,
    task_type VARCHAR(50) NOT NULL,
    state VARCHAR(30) NOT NULL,
    payload TEXT NOT NULL,
    external_resource_id VARCHAR(100),
    retry_count INT NOT NULL DEFAULT 0,
    max_retries INT NOT NULL DEFAULT 5,
    next_retry_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    last_error TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- Index for concurrent polling with SKIP LOCKED
CREATE INDEX IF NOT EXISTS idx_task_state_retry
ON external_task_state (state, next_retry_at)
WHERE state IN ('PENDING', 'FAILED_CHECK_NEEDED');

-- Index for lookup by business key
CREATE INDEX IF NOT EXISTS idx_task_business_key
ON external_task_state (business_key);
