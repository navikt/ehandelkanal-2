CREATE TABLE IF NOT EXISTS inbox_download_state (
  msg_no          VARCHAR(64) PRIMARY KEY,
  failure_count   INT NOT NULL,
  next_attempt_ms BIGINT NOT NULL,
  last_error      VARCHAR(128),
  updated_ms      BIGINT NOT NULL
);
