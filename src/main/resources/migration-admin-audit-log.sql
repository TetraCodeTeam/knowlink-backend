-- =============================================
-- KnowLink - Admin base migration
-- Creates admin_audit_log, the append-only audit trail shared by the admin
-- module (contact-info views, material downloads, case take-over, material
-- removal, report dismissal and claim resolution).
--
-- Run once against the target database BEFORE deploying the version that
-- maps AdminAuditLog:
--   mysql --default-character-set=utf8mb4 -u <user> -p <database> < migration-admin-audit-log.sql
--
-- The script does NOT depend on DDL_AUTO: it creates the table and the
-- indexes itself, so it works with DDL_AUTO=none, validate or update.
-- On a fresh database the application creates it by itself, so this script
-- is not needed.
--
-- Safe to re-run: CREATE TABLE IF NOT EXISTS and the index names match the
-- @Index declarations of the entity.
--
-- NOTE: no foreign keys are declared here. admin_user_id and target_id are
-- plain UUID columns in the entity (not JPA associations), and Hibernate
-- schema validation does not check constraints.
-- =============================================

CREATE TABLE IF NOT EXISTS admin_audit_log (
    admin_audit_log_id BINARY(16)  NOT NULL,
    admin_user_id      BINARY(16)  NOT NULL,
    action             VARCHAR(40) NOT NULL,
    target_type        VARCHAR(40) NOT NULL,
    target_id          BINARY(16)  NOT NULL,
    created_at         DATETIME(6) NOT NULL,
    PRIMARY KEY (admin_audit_log_id),
    KEY idx_admin_audit_admin_created (admin_user_id, created_at),
    KEY idx_admin_audit_target (target_type, target_id)
) ENGINE = InnoDB;

-- Verification: the table and both indexes must exist.
SELECT
    (SELECT COUNT(*) FROM information_schema.TABLES
     WHERE TABLE_SCHEMA = DATABASE()
       AND TABLE_NAME = 'admin_audit_log') AS audit_table_exists,
    (SELECT COUNT(DISTINCT INDEX_NAME) FROM information_schema.STATISTICS
     WHERE TABLE_SCHEMA = DATABASE()
       AND TABLE_NAME = 'admin_audit_log'
       AND INDEX_NAME IN ('idx_admin_audit_admin_created', 'idx_admin_audit_target')) AS audit_indexes;