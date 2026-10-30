-- V2__add_policy_name_to_request_logs.sql
ALTER TABLE request_logs ADD COLUMN IF NOT EXISTS policy_name VARCHAR(100);
CREATE INDEX IF NOT EXISTS idx_req_logs_policy_name ON request_logs(policy_name);

