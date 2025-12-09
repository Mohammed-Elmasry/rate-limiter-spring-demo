-- IP-based rules table
CREATE TABLE ip_rules (
    id UUID PRIMARY KEY,
    ip_address INET,
    ip_cidr CIDR,
    rule_type VARCHAR(20) NOT NULL,
    policy_id UUID REFERENCES policies(id) ON DELETE CASCADE,
    tenant_id UUID REFERENCES tenants(id) ON DELETE CASCADE,
    description TEXT,
    enabled BOOLEAN NOT NULL DEFAULT true,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_ip_rules_type CHECK (rule_type IN ('RATE_LIMIT', 'ALLOW', 'BLOCK')),
    CONSTRAINT chk_ip_rules_address CHECK (
        (ip_address IS NOT NULL AND ip_cidr IS NULL) OR
        (ip_address IS NULL AND ip_cidr IS NOT NULL)
    ),
    CONSTRAINT chk_ip_rules_policy CHECK (
        (rule_type = 'RATE_LIMIT' AND policy_id IS NOT NULL) OR
        (rule_type IN ('ALLOW', 'BLOCK') AND policy_id IS NULL)
    )
);

CREATE INDEX idx_ip_rules_ip_address ON ip_rules USING GIST (ip_address inet_ops) WHERE ip_address IS NOT NULL;
CREATE INDEX idx_ip_rules_ip_cidr ON ip_rules USING GIST (ip_cidr inet_ops) WHERE ip_cidr IS NOT NULL;
CREATE INDEX idx_ip_rules_tenant_id ON ip_rules(tenant_id);
CREATE INDEX idx_ip_rules_enabled ON ip_rules(enabled);
CREATE INDEX idx_ip_rules_rule_type ON ip_rules(rule_type);

COMMENT ON TABLE ip_rules IS 'IP-based rate limiting and access control rules';
COMMENT ON COLUMN ip_rules.rule_type IS 'RATE_LIMIT applies a policy, ALLOW bypasses limits, BLOCK denies access';
