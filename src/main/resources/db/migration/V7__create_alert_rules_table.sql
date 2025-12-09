-- Alert rules for threshold-based notifications
CREATE TABLE alert_rules (
    id UUID PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    policy_id UUID REFERENCES policies(id) ON DELETE CASCADE,
    threshold_percentage INTEGER NOT NULL,
    window_seconds INTEGER NOT NULL DEFAULT 60,
    cooldown_seconds INTEGER NOT NULL DEFAULT 300,
    enabled BOOLEAN NOT NULL DEFAULT true,
    last_triggered_at TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_alert_threshold CHECK (threshold_percentage > 0 AND threshold_percentage <= 100),
    CONSTRAINT chk_alert_window CHECK (window_seconds > 0),
    CONSTRAINT chk_alert_cooldown CHECK (cooldown_seconds >= 0)
);

CREATE INDEX idx_alert_rules_policy_id ON alert_rules(policy_id);
CREATE INDEX idx_alert_rules_enabled ON alert_rules(enabled);

COMMENT ON TABLE alert_rules IS 'Defines when to trigger alerts based on rate limit usage';
COMMENT ON COLUMN alert_rules.threshold_percentage IS 'Trigger alert when usage exceeds this percentage of the limit';
COMMENT ON COLUMN alert_rules.cooldown_seconds IS 'Minimum time between consecutive alerts';
