-- User-specific policy assignments
CREATE TABLE user_policies (
    id UUID PRIMARY KEY,
    user_id VARCHAR(255) NOT NULL,
    policy_id UUID NOT NULL REFERENCES policies(id) ON DELETE CASCADE,
    tenant_id UUID NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
    enabled BOOLEAN NOT NULL DEFAULT true,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_user_policies_user_tenant UNIQUE (user_id, tenant_id)
);

CREATE INDEX idx_user_policies_user_id ON user_policies(user_id);
CREATE INDEX idx_user_policies_tenant_id ON user_policies(tenant_id);
CREATE INDEX idx_user_policies_policy_id ON user_policies(policy_id);

COMMENT ON TABLE user_policies IS 'Maps users (from IAM) to specific rate limit policies';
COMMENT ON COLUMN user_policies.user_id IS 'External user ID from IAM provider (e.g., Keycloak sub claim)';
