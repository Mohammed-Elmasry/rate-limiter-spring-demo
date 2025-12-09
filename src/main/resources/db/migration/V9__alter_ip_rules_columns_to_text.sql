-- Change INET/CIDR columns to TEXT for simpler JDBC compatibility
-- IP validation is handled at the application layer
-- CIDR matching is done via explicit CAST in native queries

-- Drop existing GIST indexes (they won't work with TEXT)
DROP INDEX IF EXISTS idx_ip_rules_ip_address;
DROP INDEX IF EXISTS idx_ip_rules_ip_cidr;

-- Alter columns from INET/CIDR to TEXT
ALTER TABLE ip_rules
    ALTER COLUMN ip_address TYPE TEXT,
    ALTER COLUMN ip_cidr TYPE TEXT;

-- Create new B-tree indexes for TEXT columns
CREATE INDEX idx_ip_rules_ip_address ON ip_rules(ip_address) WHERE ip_address IS NOT NULL;
CREATE INDEX idx_ip_rules_ip_cidr ON ip_rules(ip_cidr) WHERE ip_cidr IS NOT NULL;

COMMENT ON COLUMN ip_rules.ip_address IS 'Single IP address stored as text, e.g., 192.168.1.100';
COMMENT ON COLUMN ip_rules.ip_cidr IS 'CIDR range stored as text, e.g., 192.168.0.0/16';
