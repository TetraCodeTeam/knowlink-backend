-- =============================================
-- KnowLink - Community (US-54 a US-58) migration
-- Lets user_notification.notification_type hold the new COMMUNITY_REPLY value.
--
-- Run once against the target database BEFORE deploying the version
-- that publishes COMMUNITY_REPLY notifications:
--   mysql --default-character-set=utf8mb4 -u <user> -p <database> < migration-community-notification-type.sql
--
-- Why: with MySQL, Hibernate may have created notification_type as a native
-- ENUM(...) column, and ddl-auto=update does not add new values to an
-- existing ENUM. Storing the type as VARCHAR(40) (the length the entity
-- declares) accepts this and every future NotificationType value.
--
-- Safe to re-run. The community_* tables are created by ddl-auto, so this
-- script is only needed on a database where user_notification already exists.
-- =============================================

ALTER TABLE user_notification
    MODIFY COLUMN notification_type VARCHAR(40) NOT NULL;
