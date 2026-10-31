-- =============================================
-- KnowLink - US-03 migration
-- Adds users.first_name and users.last_name.
--
-- Run once against the target database BEFORE deploying the version
-- that maps both columns:
--   mysql --default-character-set=utf8mb4 -u <user> -p <database> < migration-user-first-last-name.sql
--
-- The script does NOT depend on DDL_AUTO: it creates the nullable
-- columns itself, so it works with DDL_AUTO=none, validate or update.
--
-- Both columns are NULL for every existing row, which is a valid state:
-- the application resolves the first and last name from full_name as a
-- fallback until the user saves the profile form once.
--
-- Safe to re-run. The users table must already exist (created by
-- ddl-auto on a previous deploy). On a fresh database the application
-- creates it with both columns, so this script is not needed.
-- =============================================

-- 1. first_name: NULL means "fallback to full_name".
SET @column_exists = (
    SELECT COUNT(*)
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'users'
      AND COLUMN_NAME = 'first_name'
);

SET @ddl = IF(@column_exists = 0,
    'ALTER TABLE users ADD COLUMN first_name VARCHAR(255) NULL AFTER full_name',
    'SELECT 1');

PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- 2. last_name: NULL means "fallback to full_name".
SET @column_exists = (
    SELECT COUNT(*)
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'users'
      AND COLUMN_NAME = 'last_name'
);

SET @ddl = IF(@column_exists = 0,
    'ALTER TABLE users ADD COLUMN last_name VARCHAR(255) NULL AFTER first_name',
    'SELECT 1');

PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- 3. Verification: the two columns must exist.
SELECT COLUMN_NAME, IS_NULLABLE, DATA_TYPE
FROM information_schema.COLUMNS
WHERE TABLE_SCHEMA = DATABASE()
  AND TABLE_NAME = 'users'
  AND COLUMN_NAME IN ('first_name', 'last_name');
