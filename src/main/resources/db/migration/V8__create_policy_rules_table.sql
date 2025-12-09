-- Policy rules table for URL pattern and HTTP method-based rate limiting
CREATE TABLE policy_rules (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    policy_id UUID NOT NULL REFERENCES policies(id) ON DELETE CASCADE,
    name VARCHAR(255) NOT NULL,
    resource_pattern VARCHAR(500) NOT NULL,
    http_methods VARCHAR(100),
    priority INTEGER NOT NULL DEFAULT 0,
    enabled BOOLEAN NOT NULL DEFAULT true,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_policy_rules_priority CHECK (priority >= 0)
);

CREATE INDEX idx_policy_rules_policy_id ON policy_rules(policy_id);
CREATE INDEX idx_policy_rules_enabled ON policy_rules(enabled) WHERE enabled = true;
CREATE INDEX idx_policy_rules_priority ON policy_rules(priority DESC);
CREATE INDEX idx_policy_rules_resource_pattern ON policy_rules(resource_pattern);

COMMENT ON TABLE policy_rules IS 'Rules for applying policies based on URL patterns and HTTP methods';
COMMENT ON COLUMN policy_rules.resource_pattern IS 'Ant-style URL pattern, e.g., /api/v1/users/**, /orders/*, /products/{id}';
COMMENT ON COLUMN policy_rules.http_methods IS 'Comma-separated HTTP methods (GET,POST) or null for all methods';
COMMENT ON COLUMN policy_rules.priority IS 'Higher priority rules are matched first (0 is lowest priority)';
