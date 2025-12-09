-- Tenants table for multi-tenancy support
CREATE TABLE tenants (
    id UUID PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    tier VARCHAR(50) NOT NULL DEFAULT 'FREE',
    enabled BOOLEAN NOT NULL DEFAULT true,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_tenants_name UNIQUE (name),
    CONSTRAINT chk_tenants_tier CHECK (tier IN ('FREE', 'BASIC', 'PREMIUM', 'ENTERPRISE'))
);

CREATE INDEX idx_tenants_enabled ON tenants(enabled);

COMMENT ON TABLE tenants IS 'Multi-tenant organizations using the rate limiting service';
COMMENT ON COLUMN tenants.tier IS 'Subscription tier determining default rate limits';
