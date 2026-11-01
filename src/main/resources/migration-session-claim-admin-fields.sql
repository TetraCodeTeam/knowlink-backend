-- =============================================
-- KnowLink - US-50 migration
-- Adds the administrator resolution fields to session_claim.
--
-- Run once against the target database BEFORE deploying the version
-- that maps these columns:
--   mysql --default-character-set=utf8mb4 -u <user> -p <database> < migration-session-claim-admin-fields.sql
--
-- The script does NOT depend on DDL_AUTO: it creates the nullable
-- columns itself, so it works with DDL_AUTO=none, validate or update.
--
-- Safe to re-run. The session_claim table must already exist
-- (created by ddl-auto on a previous deploy). On a fresh database the
-- application creates it with all the columns, so this script is not needed.
--
-- NOTE: version is backfilled to 0 because Hibernate uses it for
-- optimistic locking; a NULL version makes every UPDATE miss the row.
-- =============================================

-- 1. dispute_id: groups every claim of the same booking (first claim keeps its own claim_id).
SET @column_exists = (
    SELECT COUNT(*)
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'session_claim'
      AND COLUMN_NAME = 'dispute_id'
);

SET @ddl = IF(@column_exists = 0,
    'ALTER TABLE session_claim ADD COLUMN dispute_id BINARY(16) NULL AFTER active_key',
    'SELECT 1');

PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- 2. automatic_outcome: outcome the system would have applied, persisted at creation time.
SET @column_exists = (
    SELECT COUNT(*)
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'session_claim'
      AND COLUMN_NAME = 'automatic_outcome'
);

SET @ddl = IF(@column_exists = 0,
    'ALTER TABLE session_claim ADD COLUMN automatic_outcome VARCHAR(60) NULL AFTER dispute_id',
    'SELECT 1');

PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- 3. assigned_admin_id: administrator that took the case.
SET @column_exists = (
    SELECT COUNT(*)
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'session_claim'
      AND COLUMN_NAME = 'assigned_admin_id'
);

SET @ddl = IF(@column_exists = 0,
    'ALTER TABLE session_claim ADD COLUMN assigned_admin_id BINARY(16) NULL AFTER automatic_outcome',
    'SELECT 1');

PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- 4. assigned_at: when the case was taken.
SET @column_exists = (
    SELECT COUNT(*)
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'session_claim'
      AND COLUMN_NAME = 'assigned_at'
);

SET @ddl = IF(@column_exists = 0,
    'ALTER TABLE session_claim ADD COLUMN assigned_at DATETIME(6) NULL AFTER assigned_admin_id',
    'SELECT 1');

PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- 5. resolved_at: when the dispute was resolved.
SET @column_exists = (
    SELECT COUNT(*)
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'session_claim'
      AND COLUMN_NAME = 'resolved_at'
);

SET @ddl = IF(@column_exists = 0,
    'ALTER TABLE session_claim ADD COLUMN resolved_at DATETIME(6) NULL AFTER assigned_at',
    'SELECT 1');

PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- 6. favored_party: TUTOR or STUDENT.
SET @column_exists = (
    SELECT COUNT(*)
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'session_claim'
      AND COLUMN_NAME = 'favored_party'
);

SET @ddl = IF(@column_exists = 0,
    'ALTER TABLE session_claim ADD COLUMN favored_party VARCHAR(20) NULL AFTER resolved_at',
    'SELECT 1');

PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- 7. resolution_justification: mandatory text written by the administrator.
SET @column_exists = (
    SELECT COUNT(*)
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'session_claim'
      AND COLUMN_NAME = 'resolution_justification'
);

SET @ddl = IF(@column_exists = 0,
    'ALTER TABLE session_claim ADD COLUMN resolution_justification VARCHAR(1000) NULL AFTER favored_party',
    'SELECT 1');

PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- 8. version: optimistic locking.
SET @column_exists = (
    SELECT COUNT(*)
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'session_claim'
      AND COLUMN_NAME = 'version'
);

SET @ddl = IF(@column_exists = 0,
    'ALTER TABLE session_claim ADD COLUMN version BIGINT NOT NULL DEFAULT 0 AFTER resolution_justification',
    'SELECT 1');

PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- Existing rows must not keep a NULL version.
UPDATE session_claim SET version = 0 WHERE version IS NULL;

-- 9. Indexes used by the admin listing and the US-33 dashboard.
SET @index_exists = (
    SELECT COUNT(*)
    FROM information_schema.STATISTICS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'session_claim'
      AND INDEX_NAME = 'idx_session_claim_dispute_id'
);

SET @ddl = IF(@index_exists = 0,
    'CREATE INDEX idx_session_claim_dispute_id ON session_claim (dispute_id)',
    'SELECT 1');

PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @index_exists = (
    SELECT COUNT(*)
    FROM information_schema.STATISTICS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'session_claim'
      AND INDEX_NAME = 'idx_session_claim_resolved_at'
);

SET @ddl = IF(@index_exists = 0,
    'CREATE INDEX idx_session_claim_resolved_at ON session_claim (resolved_at)',
    'SELECT 1');

PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- 10. Verification: every column must exist.
SELECT COLUMN_NAME, IS_NULLABLE, DATA_TYPE
FROM information_schema.COLUMNS
WHERE TABLE_SCHEMA = DATABASE()
  AND TABLE_NAME = 'session_claim'
  AND COLUMN_NAME IN ('dispute_id', 'automatic_outcome', 'assigned_admin_id', 'assigned_at',
                      'resolved_at', 'favored_party', 'resolution_justification', 'version');