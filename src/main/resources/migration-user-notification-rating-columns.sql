-- =============================================
-- KnowLink - US-20 migration
-- Adds user_notification.expires_at and user_notification.resolved_at.
--
-- Run once against the target database BEFORE deploying the version
-- that maps both columns:
--   mysql --default-character-set=utf8mb4 -u <user> -p <database> < migration-user-notification-rating-columns.sql
--
-- The script does NOT depend on DDL_AUTO: it creates the nullable
-- columns itself, so it works with DDL_AUTO=none, validate or update.
--
-- Both columns are NULL for every existing row, which is a valid state:
-- notifications without expires_at never expire and notifications
-- without resolved_at have no completed action.
--
-- Safe to re-run. The user_notification table must already exist
-- (created by ddl-auto on a previous deploy). On a fresh database the
-- application creates it with both columns, so this script is not needed.
-- =============================================

-- 1. expires_at: only for RATING_ENABLED, NULL means "never expires".
SET @column_exists = (
    SELECT COUNT(*)
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'user_notification'
      AND COLUMN_NAME = 'expires_at'
);

SET @ddl = IF(@column_exists = 0,
    'ALTER TABLE user_notification ADD COLUMN expires_at DATETIME(6) NULL AFTER read_at',
    'SELECT 1');

PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- 2. resolved_at: set when the user completed the notification action.
SET @column_exists = (
    SELECT COUNT(*)
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'user_notification'
      AND COLUMN_NAME = 'resolved_at'
);

SET @ddl = IF(@column_exists = 0,
    'ALTER TABLE user_notification ADD COLUMN resolved_at DATETIME(6) NULL AFTER expires_at',
    'SELECT 1');

PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- 3. Verification: the two columns must exist.
SELECT COLUMN_NAME, IS_NULLABLE, DATA_TYPE
FROM information_schema.COLUMNS
WHERE TABLE_SCHEMA = DATABASE()
  AND TABLE_NAME = 'user_notification'
  AND COLUMN_NAME IN ('expires_at', 'resolved_at');