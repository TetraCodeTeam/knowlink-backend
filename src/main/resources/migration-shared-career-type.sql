-- =============================================
-- KnowLink - Shared career type migration
-- Normalizes the legacy 'COMPARTIDA' value stored in career.type to 'SHARED'.
--
-- Run once against the target database BEFORE starting the version that
-- seeds the shared career through CareerType.SHARED:
--   mysql --default-character-set=utf8mb4 -u <user> -p <database> < migration-shared-career-type.sql
--
-- Why: CareerType used to expose COMPARTIDA and now only knows REGULAR and
-- SHARED. Hibernate reads unknown enum values as null, so the seeder never
-- found the existing "Materias Compartidas" row and tried to insert a new
-- one, which fails against uk_career_institution_name (institution_id, name).
--
-- Safe to re-run: the UPDATE only matches rows that still hold the legacy
-- value. REGULAR rows are not touched.
-- =============================================

UPDATE career
SET type = 'SHARED'
WHERE type = 'COMPARTIDA';
