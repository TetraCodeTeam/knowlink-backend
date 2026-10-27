-- =============================================
-- KnowLink - US-35 migration
-- Adds user_notification.recipient_role and backfills existing rows.
--
-- Run once against the target database BEFORE deploying the version
-- that maps recipient_role as NOT NULL:
--   mysql --default-character-set=utf8mb4 -u <user> -p <database> < migration-user-notification-recipient-role.sql
--
-- The script does NOT depend on DDL_AUTO: it creates the column, the
-- NOT NULL constraint and the indexes itself, so it works with
-- DDL_AUTO=none, validate or update.
--
-- It does NOT delete notifications. Legacy rows are assigned a role
-- from (in order): the notification type for admin notices, the
-- booking side that matches the recipient, or the recipient's role.
-- Rows of dual accounts created before US-35 may need a manual review.
--
-- Safe to re-run. The user_notification table must already exist
-- (created by ddl-auto on a previous deploy). On a fresh database the
-- application creates it with the column, so this script is not needed.
-- =============================================

-- 1. Add the column as NULL only when it does not exist yet.
SET @column_exists = (
    SELECT COUNT(*)
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'user_notification'
      AND COLUMN_NAME = 'recipient_role'
);

SET @ddl = IF(@column_exists = 0,
    'ALTER TABLE user_notification ADD COLUMN recipient_role ENUM(''ADMIN'',''STUDENT'',''TUTOR'') NULL AFTER recipient_user_id',
    'SELECT 1');

PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- 2. Backfill rows that are still NULL (column just created).
UPDATE user_notification n
JOIN users u ON u.user_id = n.recipient_user_id
LEFT JOIN booking b ON b.booking_id = n.resource_id
SET n.recipient_role = CASE
        WHEN n.notification_type IN ('ADMIN_MATERIAL_REPORT_RECEIVED', 'ADMIN_VALIDATION_REQUEST_PENDING') THEN 'ADMIN'
        WHEN b.booking_id IS NOT NULL AND b.student_id = n.recipient_user_id THEN 'STUDENT'
        WHEN b.booking_id IS NOT NULL AND b.tutor_id = n.recipient_user_id THEN 'TUTOR'
        WHEN u.role = 'ADMIN' THEN 'ADMIN'
        ELSE u.role
    END
WHERE n.recipient_role IS NULL;

-- 3. Correct rows that ddl-auto may have filled with the first enum value
--    ('ADMIN') for recipients that are not administrators.
UPDATE user_notification n
JOIN users u ON u.user_id = n.recipient_user_id
LEFT JOIN booking b ON b.booking_id = n.resource_id
SET n.recipient_role = CASE
        WHEN b.booking_id IS NOT NULL AND b.student_id = n.recipient_user_id THEN 'STUDENT'
        WHEN b.booking_id IS NOT NULL AND b.tutor_id = n.recipient_user_id THEN 'TUTOR'
        WHEN u.role = 'ADMIN' THEN 'ADMIN'
        ELSE u.role
    END
WHERE n.recipient_role = 'ADMIN'
  AND u.role <> 'ADMIN'
  AND n.notification_type NOT IN ('ADMIN_MATERIAL_REPORT_RECEIVED', 'ADMIN_VALIDATION_REQUEST_PENDING');

-- 4. Enforce the NOT NULL constraint expected by the entity.
ALTER TABLE user_notification
    MODIFY COLUMN recipient_role ENUM('ADMIN', 'STUDENT', 'TUTOR') NOT NULL;

-- 5. Recreate the role-aware indexes. The previous version of the table
--    may already have them without recipient_role, and MySQL has no
--    CREATE INDEX IF NOT EXISTS.
SET @idx_exists = (
    SELECT COUNT(*)
    FROM information_schema.STATISTICS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'user_notification'
      AND INDEX_NAME = 'idx_user_notification_recipient_created'
);
SET @idx_has_role = (
    SELECT COUNT(*)
    FROM information_schema.STATISTICS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'user_notification'
      AND INDEX_NAME = 'idx_user_notification_recipient_created'
      AND COLUMN_NAME = 'recipient_role'
);
SET @ddl = CASE
    WHEN @idx_exists > 0 AND @idx_has_role = 0
        THEN 'ALTER TABLE user_notification DROP INDEX idx_user_notification_recipient_created, ADD INDEX idx_user_notification_recipient_created (recipient_user_id, recipient_role, created_at)'
    WHEN @idx_exists = 0
        THEN 'ALTER TABLE user_notification ADD INDEX idx_user_notification_recipient_created (recipient_user_id, recipient_role, created_at)'
    ELSE 'SELECT 1'
END;
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @idx_exists = (
    SELECT COUNT(*)
    FROM information_schema.STATISTICS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'user_notification'
      AND INDEX_NAME = 'idx_user_notification_recipient_read'
);
SET @idx_has_role = (
    SELECT COUNT(*)
    FROM information_schema.STATISTICS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'user_notification'
      AND INDEX_NAME = 'idx_user_notification_recipient_read'
      AND COLUMN_NAME = 'recipient_role'
);
SET @ddl = CASE
    WHEN @idx_exists > 0 AND @idx_has_role = 0
        THEN 'ALTER TABLE user_notification DROP INDEX idx_user_notification_recipient_read, ADD INDEX idx_user_notification_recipient_read (recipient_user_id, recipient_role, read_at)'
    WHEN @idx_exists = 0
        THEN 'ALTER TABLE user_notification ADD INDEX idx_user_notification_recipient_read (recipient_user_id, recipient_role, read_at)'
    ELSE 'SELECT 1'
END;
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- 6. Verification: every recipient_role must be valid and the table
--    must have no NULL values left.
SELECT recipient_role, COUNT(*) AS notifications
FROM user_notification
GROUP BY recipient_role;