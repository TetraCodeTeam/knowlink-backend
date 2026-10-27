-- =============================================
-- KnowLink - US-19 migration
-- Creates offered_slot_history, the archive of past unbooked time slots that
-- PastAvailabilityCleanupJob moves out of time_slot every night. It is the
-- denominator ("offered hours") of the occupancy panel for past periods.
--
-- Only needed when DDL_AUTO is none or validate; with update/create the
-- application creates the table by itself. Run once BEFORE deploying:
--   mysql --default-character-set=utf8mb4 -u <user> -p <database> < migration-tutor-statistics-offered-slot-history.sql
--
-- History starts the first night the new job runs: time slots deleted by the
-- previous version of the job cannot be recovered, so occupancy for periods
-- before the deploy is computed only from the slots that still existed.
--
-- Safe to re-run.
-- =============================================

CREATE TABLE IF NOT EXISTS offered_slot_history (
    offered_slot_history_id BINARY(16) NOT NULL,
    tutor_profile_id        BINARY(16) NOT NULL,
    date                    DATE       NOT NULL,
    start_time              TIME(6)    NOT NULL,
    end_time                TIME(6)    NOT NULL,
    PRIMARY KEY (offered_slot_history_id),
    KEY idx_offered_slot_history_tutor_date (tutor_profile_id, date)
) ENGINE = InnoDB;
