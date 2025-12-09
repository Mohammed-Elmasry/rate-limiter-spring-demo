-- Rate limit events for metrics and auditing (partitioned by month)
CREATE TABLE rate_limit_events (
    id BIGSERIAL,
    policy_id UUID NOT NULL,
    identifier VARCHAR(255) NOT NULL,
    identifier_type VARCHAR(50) NOT NULL,
    allowed BOOLEAN NOT NULL,
    remaining INTEGER NOT NULL,
    limit_value INTEGER NOT NULL,
    ip_address INET,
    resource VARCHAR(255),
    event_time TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    partition_key VARCHAR(7) NOT NULL,
    PRIMARY KEY (id, partition_key),
    CONSTRAINT chk_events_identifier_type CHECK (identifier_type IN ('API_KEY', 'USER', 'IP', 'GLOBAL', 'TENANT'))
) PARTITION BY LIST (partition_key);

-- Create partitions for the next 12 months (will need to be maintained)
CREATE TABLE rate_limit_events_default PARTITION OF rate_limit_events DEFAULT;

CREATE INDEX idx_events_policy_id ON rate_limit_events(policy_id);
CREATE INDEX idx_events_identifier ON rate_limit_events(identifier);
CREATE INDEX idx_events_event_time ON rate_limit_events(event_time);
CREATE INDEX idx_events_allowed ON rate_limit_events(allowed);

COMMENT ON TABLE rate_limit_events IS 'Audit log of rate limit check events for metrics and analysis';
COMMENT ON COLUMN rate_limit_events.partition_key IS 'Format: YYYY-MM for monthly partitioning';
