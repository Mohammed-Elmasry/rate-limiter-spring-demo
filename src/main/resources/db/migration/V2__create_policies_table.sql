-- Rate limit policies table
CREATE TABLE policies (
    id UUID PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    description TEXT,
    scope VARCHAR(50) NOT NULL,
    algorithm VARCHAR(50) NOT NULL,
    max_requests INTEGER NOT NULL,
    window_seconds INTEGER NOT NULL,
    burst_capacity INTEGER,
    refill_rate DECIMAL(10,4),
    fail_mode VARCHAR(20) NOT NULL DEFAULT 'FAIL_CLOSED',
    enabled BOOLEAN NOT NULL DEFAULT true,
    is_default BOOLEAN NOT NULL DEFAULT false,
    tenant_id UUID REFERENCES tenants(id) ON DELETE CASCADE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_policies_name_tenant UNIQUE (name, tenant_id),
    CONSTRAINT chk_policies_scope CHECK (scope IN ('GLOBAL', 'TENANT', 'API_KEY', 'IP', 'USER')),
    CONSTRAINT chk_policies_algorithm CHECK (algorithm IN ('TOKEN_BUCKET', 'FIXED_WINDOW', 'SLIDING_LOG')),
    CONSTRAINT chk_policies_fail_mode CHECK (fail_mode IN ('FAIL_OPEN', 'FAIL_CLOSED')),
    CONSTRAINT chk_policies_max_requests CHECK (max_requests > 0),
    CONSTRAINT chk_policies_window_seconds CHECK (window_seconds > 0),
    CONSTRAINT chk_policies_burst_capacity CHECK (burst_capacity IS NULL OR burst_capacity > 0),
    CONSTRAINT chk_policies_refill_rate CHECK (refill_rate IS NULL OR refill_rate > 0)
);

CREATE INDEX idx_policies_scope ON policies(scope);
CREATE INDEX idx_policies_enabled ON policies(enabled);
CREATE INDEX idx_policies_tenant_id ON policies(tenant_id);
CREATE INDEX idx_policies_is_default ON policies(is_default) WHERE is_default = true;

COMMENT ON TABLE policies IS 'Rate limiting policies defining limits and algorithms';
COMMENT ON COLUMN policies.scope IS 'Scope at which the policy applies: GLOBAL, TENANT, API_KEY, IP, USER';
COMMENT ON COLUMN policies.algorithm IS 'Rate limiting algorithm: TOKEN_BUCKET, FIXED_WINDOW, SLIDING_LOG';
COMMENT ON COLUMN policies.burst_capacity IS 'Maximum tokens for token bucket algorithm';
COMMENT ON COLUMN policies.refill_rate IS 'Tokens per second refill rate for token bucket';
COMMENT ON COLUMN policies.fail_mode IS 'Behavior when rate limiter is unavailable';
