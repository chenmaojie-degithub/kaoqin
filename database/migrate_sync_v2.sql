-- Additive upgrade for databases that already have v1 sync tables. Select the intended database first.
-- No business-table DDL/DML. Rerunnable on both v1 and fresh v2 schemas.
SET @sync_v2_ddl = IF(
  EXISTS(SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE()
         AND table_name='sync_identity_map' AND column_name='last_event_id'),
  'SELECT 1',
  'ALTER TABLE sync_identity_map ADD COLUMN last_event_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NULL AFTER payload_hash'
);
PREPARE sync_v2_statement FROM @sync_v2_ddl;
EXECUTE sync_v2_statement;
DEALLOCATE PREPARE sync_v2_statement;
