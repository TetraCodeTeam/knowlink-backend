-- =============================================
-- KnowLink - US-23 migration
-- Creates material_report_case, the administrator case that groups every
-- report filed against the same academic material, and adds the nullable
-- academic_material_report.material_report_case_id link.
--
-- Run once against the target database BEFORE deploying the version that
-- maps the case entity:
--   mysql --default-character-set=utf8mb4 -u <user> -p <database> < migration-material-report-case.sql
--
-- The script does NOT depend on DDL_AUTO: it creates the table, the column,
-- the indexes and the backfill itself, so it works with DDL_AUTO=none,
-- validate or update.
--
-- Backfill policy: only reports that are still open (status NULL, PENDING or
-- IN_REVIEW) are grouped. Historical reports already RESOLVED keep
-- material_report_case_id NULL on purpose: they were never visible in the
-- admin panel and inventing a resolved_at for them would distort the US-33
-- dashboard. Those rows are still readable through the tutor/student report
-- endpoints, which do not depend on the case.
--
-- Safe to re-run. The academic_material_report table must already exist
-- (created by ddl-auto on a previous deploy). On a fresh database the
-- application creates everything by itself, so this script is not needed.
--
-- NOTE: no foreign keys are declared here. The JPA mappings only need the
-- columns, Hibernate schema validation does not check constraints, and an
-- existing database may hold rows that would violate them. On a fresh
-- database ddl-auto creates the foreign keys by itself.
--
-- NOTE: version is NOT NULL DEFAULT 0 because Hibernate uses it for
-- optimistic locking; a NULL version makes every UPDATE miss the row.
-- =============================================

-- 1. material_report_case: one row per material with at least one open report.
CREATE TABLE IF NOT EXISTS material_report_case (
    material_report_case_id BINARY(16)   NOT NULL,
    academic_material_id    BINARY(16)   NOT NULL,
    status                  VARCHAR(20)  NOT NULL,
    assigned_admin_id       BINARY(16)   NULL,
    assigned_at             DATETIME(6)  NULL,
    opened_at               DATETIME(6)  NOT NULL,
    resolved_at             DATETIME(6)  NULL,
    resolution_note         VARCHAR(500) NULL,
    reports_count           INT          NOT NULL DEFAULT 0,
    version                 BIGINT       NOT NULL DEFAULT 0,
    PRIMARY KEY (material_report_case_id),
    KEY idx_mrc_status (status),
    KEY idx_mrc_status_opened (status, opened_at),
    KEY idx_mrc_resolved_at (resolved_at),
    KEY idx_mrc_material (academic_material_id)
) ENGINE = InnoDB;

-- 2. academic_material_report.material_report_case_id: NULL for reports
--    created before this deploy and for already-resolved historical reports.
SET @column_exists = (
    SELECT COUNT(*)
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'academic_material_report'
      AND COLUMN_NAME = 'material_report_case_id'
);

SET @ddl = IF(@column_exists = 0,
    'ALTER TABLE academic_material_report ADD COLUMN material_report_case_id BINARY(16) NULL AFTER academic_material_id',
    'SELECT 1');

PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- 3. Index for the case -> reports lookup (resolveReportsByCase).
SET @index_exists = (
    SELECT COUNT(*)
    FROM information_schema.STATISTICS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'academic_material_report'
      AND INDEX_NAME = 'idx_amr_report_case'
);

SET @ddl = IF(@index_exists = 0,
    'CREATE INDEX idx_amr_report_case ON academic_material_report (material_report_case_id)',
    'SELECT 1');

PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- 4. Backfill: one open case per material that still has unlinked open
--    reports. opened_at is the oldest report of the group and reports_count
--    is the number of reports linked below.
INSERT INTO material_report_case (
    material_report_case_id,
    academic_material_id,
    status,
    assigned_admin_id,
    assigned_at,
    opened_at,
    resolved_at,
    resolution_note,
    reports_count,
    version
)
SELECT UNHEX(REPLACE(UUID(), '-', '')),
       r.academic_material_id,
       CASE WHEN SUM(r.status = 'IN_REVIEW') > 0 THEN 'IN_REVIEW' ELSE 'PENDING' END,
       NULL,
       NULL,
       COALESCE(MIN(r.reported_at), NOW(6)),
       NULL,
       NULL,
       COUNT(*),
       0
FROM academic_material_report r
WHERE (r.status IS NULL OR r.status IN ('PENDING', 'IN_REVIEW'))
  AND r.material_report_case_id IS NULL
  AND NOT EXISTS (
        SELECT 1
        FROM material_report_case c
        WHERE c.academic_material_id = r.academic_material_id
          AND c.status IN ('PENDING', 'IN_REVIEW')
  )
GROUP BY r.academic_material_id;

-- 5. Link the backfilled reports to their case. The NOT EXISTS above
--    guarantees at most one open case per material, so this join is
--    deterministic.
UPDATE academic_material_report r
JOIN material_report_case c
  ON c.academic_material_id = r.academic_material_id
 AND c.status IN ('PENDING', 'IN_REVIEW')
SET r.material_report_case_id = c.material_report_case_id
WHERE (r.status IS NULL OR r.status IN ('PENDING', 'IN_REVIEW'))
  AND r.material_report_case_id IS NULL;

-- 6. Verification: the table, the column and the indexes must exist, and
--    every open report must belong to a case.
SELECT
    (SELECT COUNT(*) FROM information_schema.TABLES
     WHERE TABLE_SCHEMA = DATABASE()
       AND TABLE_NAME = 'material_report_case') AS case_table_exists,
    (SELECT COUNT(*) FROM information_schema.COLUMNS
     WHERE TABLE_SCHEMA = DATABASE()
       AND TABLE_NAME = 'academic_material_report'
       AND COLUMN_NAME = 'material_report_case_id') AS link_column_exists,
    (SELECT COUNT(*) FROM information_schema.STATISTICS
     WHERE TABLE_SCHEMA = DATABASE()
       AND TABLE_NAME = 'material_report_case') AS case_index_entries,
    (SELECT COUNT(*) FROM academic_material_report
     WHERE (status IS NULL OR status IN ('PENDING', 'IN_REVIEW'))
       AND material_report_case_id IS NULL) AS open_reports_without_case;