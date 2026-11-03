-- =============================================
-- KnowLink - US-40 migration
-- Allows users.account_status = 'DEACTIVATED'.
--
-- Run once BEFORE deploying the version that maps `DEACTIVATED`:
--   mysql --default-character-set=utf8mb4 -u <user> -p <database> < migration-user-account-status-deactivated.sql
--
-- US-04 already extends the native MySQL ENUM with DEACTIVATED when the
-- column is an ENUM. This script goes one step further and converts the
-- column to VARCHAR so future account states never require a DDL change.
-- Hibernate maps @Enumerated(STRING) onto VARCHAR without changes.
--
-- Safe to re-run. Existing rows are not modified.
-- =============================================

ALTER TABLE users
    MODIFY COLUMN account_status VARCHAR(20) NOT NULL DEFAULT 'INACTIVE';

SELECT COLUMN_NAME, DATA_TYPE, IS_NULLABLE, COLUMN_DEFAULT
FROM information_schema.COLUMNS
WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'users' AND COLUMN_NAME = 'account_status';