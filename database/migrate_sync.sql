-- Additive migration. Select the intended database before running; no USE/DROP/business-table DML.
CREATE TABLE IF NOT EXISTS sync_event (
  journal_id BIGINT PRIMARY KEY AUTO_INCREMENT,
  event_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  origin_node_id VARCHAR(64) NOT NULL,
  entity_type VARCHAR(32) NOT NULL,
  entity_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  version BIGINT NOT NULL,
  event_json JSON NOT NULL,
  event_hash CHAR(64) NOT NULL,
  published_seq BIGINT NULL,
  created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  UNIQUE KEY uk_sync_event_id(event_id),
  UNIQUE KEY uk_sync_publication(published_seq)
) ENGINE=InnoDB;
CREATE TABLE IF NOT EXISTS sync_inbox (
  event_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin PRIMARY KEY,
  event_hash CHAR(64) NOT NULL,
  event_json JSON NOT NULL,
  sender_node_id VARCHAR(64) NOT NULL,
  status VARCHAR(32) NOT NULL,
  error_code VARCHAR(64) NULL,
  attempts INT NOT NULL DEFAULT 0,
  received_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
  KEY idx_sync_pending(status,updated_at)
) ENGINE=InnoDB;
CREATE TABLE IF NOT EXISTS sync_cursor (
  peer_node_id VARCHAR(64) NOT NULL,
  direction VARCHAR(4) NOT NULL,
  cursor_value BIGINT NOT NULL DEFAULT 0,
  last_success_at TIMESTAMP(6) NULL,
  last_attempt_at TIMESTAMP(6) NULL,
  last_error VARCHAR(128) NULL,
  failures INT NOT NULL DEFAULT 0,
  retry_at TIMESTAMP(6) NULL,
  PRIMARY KEY(peer_node_id,direction)
) ENGINE=InnoDB;
CREATE TABLE IF NOT EXISTS sync_identity_map (
  entity_type VARCHAR(32) NOT NULL,
  global_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  local_id VARCHAR(100) NOT NULL,
  owner_node_id VARCHAR(64) NOT NULL,
  version BIGINT NOT NULL DEFAULT 0,
  payload_hash CHAR(64) NULL,
  last_event_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NULL,
  receipt_verified BOOLEAN NOT NULL DEFAULT FALSE,
  updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
  PRIMARY KEY(entity_type,global_id),
  UNIQUE KEY uk_sync_local(entity_type,local_id)
) ENGINE=InnoDB;
-- Publication is separate from capture so rolled-back / late-committing AUTO_INCREMENT IDs cannot be skipped.
CREATE TABLE IF NOT EXISTS sync_state (
  id INT PRIMARY KEY,
  node_id VARCHAR(64) NULL,
  last_published_seq BIGINT NOT NULL DEFAULT 0
) ENGINE=InnoDB;
INSERT IGNORE INTO sync_state(id) VALUES(1);
