-- =============================================
-- KnowLink - US-34 migration
-- Adds tokens.token_type (ACCOUNT_CONFIRMATION | PASSWORD_RESET).
--
-- Run once BEFORE deploying the version that maps the column:
--   mysql --default-character-set=utf8mb4 -u <user> -p <database> < migration-token-type.sql
--
-- Existing rows become ACCOUNT_CONFIRMATION (the only type that existed).
-- Safe to re-run. The tokens table must already exist.
-- =============================================

SET @column_exists = (
    SELECT COUNT(*)
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'tokens'
      AND COLUMN_NAME = 'token_type'
);

SET @ddl = IF(@column_exists = 0,
    'ALTER TABLE tokens ADD COLUMN token_type VARCHAR(30) NOT NULL DEFAULT ''ACCOUNT_CONFIRMATION''',
    'SELECT 1');

PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SELECT COLUMN_NAME, IS_NULLABLE, DATA_TYPE, COLUMN_DEFAULT
FROM information_schema.COLUMNS
WHERE TABLE_SCHEMA = DATABASE()
  AND TABLE_NAME = 'tokens'
  AND COLUMN_NAME = 'token_type';