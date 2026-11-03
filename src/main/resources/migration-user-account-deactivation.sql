-- =============================================
-- KnowLink - US-04 migration
-- Account deactivation (temporary).
--
-- Run once BEFORE deploying the version that maps `DEACTIVATED`:
--   mysql --default-character-set=utf8mb4 -u <user> -p <database> < migration-user-account-deactivation.sql
--
-- Two changes are covered:
--   1. users.deactivated_at: nullable timestamp, set when the user deactivates
--      the account and cleared by US-40 when it is reactivated.
--   2. users.account_status: Hibernate 6 maps @Enumerated(STRING) to a native
--      MySQL ENUM, and ddl-auto=update does NOT add new values to an existing
--      ENUM. Without this step `UPDATE ... 'DEACTIVATED'` fails with
--      "Data truncated". When the column is already VARCHAR nothing is done.
--
-- The script does NOT depend on DDL_AUTO and is safe to re-run.
-- =============================================

-- 1. deactivated_at: NULL means "the account was never deactivated".
SET @column_exists = (
    SELECT COUNT(*)
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'users'
      AND COLUMN_NAME = 'deactivated_at'
);

SET @ddl = IF(@column_exists = 0,
    'ALTER TABLE users ADD COLUMN deactivated_at DATETIME(6) NULL AFTER deleted_at',
    'SELECT 1');

PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- 2. account_status: extend the native ENUM only when it is actually an ENUM.
SET @data_type = (
    SELECT DATA_TYPE
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'users'
      AND COLUMN_NAME = 'account_status'
);

SET @ddl = IF(@data_type = 'enum',
    'ALTER TABLE users MODIFY COLUMN account_status ENUM(''ACTIVE'',''INACTIVE'',''DELETED'',''DEACTIVATED'') NOT NULL',
    'SELECT 1');

PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- 3. Verification.
SELECT COLUMN_NAME, IS_NULLABLE, DATA_TYPE, COLUMN_TYPE
FROM information_schema.COLUMNS
WHERE TABLE_SCHEMA = DATABASE()
  AND TABLE_NAME = 'users'
  AND COLUMN_NAME IN ('deactivated_at', 'account_status');