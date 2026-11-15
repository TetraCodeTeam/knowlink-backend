-- =============================================
-- KnowLink - profile picture public URLs
--
-- Profile pictures used to be stored as signed Supabase URLs with a 7 day
-- expiry, so every avatar died one week after the upload. They are now stored
-- as permanent public URLs:
--
--   before: {SUPABASE_URL}/storage/v1/object/sign/{bucket}/{path}?token=...
--   after:  {SUPABASE_URL}/storage/v1/object/public/{bucket}/{path}
--
-- Run once against the target database BEFORE deploying the version that
-- writes public URLs:
--   mysql --default-character-set=utf8mb4 -u <user> -p <database> < migration-profile-picture-public-url.sql
--
-- Prerequisite: the profilePhotos bucket must exist and be PUBLIC, otherwise
-- every avatar returns 403. Create it from the Supabase dashboard (Storage ->
-- New bucket -> name "profilePhotos" -> Public bucket) or override the name
-- with SUPABASE_PROFILE_PHOTOS_BUCKET.
--
-- Also clears the dead blob:http://... URLs written by the old registration
-- form, which never left the browser. Those users simply fall back to their
-- initials until they upload a picture again.
--
-- Safe to re-run: rows already public (or already NULL) are skipped.
-- =============================================

-- 1. Signed URL -> public URL, dropping the token.
UPDATE users
SET profile_picture_url = REGEXP_REPLACE(
        REGEXP_REPLACE(profile_picture_url, '/storage/v1/object/sign/', '/storage/v1/object/public/'),
        '\?token=.*$',
        '')
WHERE profile_picture_url LIKE '%/storage/v1/object/sign/%';

UPDATE student_profile
SET profile_picture_url = REGEXP_REPLACE(
        REGEXP_REPLACE(profile_picture_url, '/storage/v1/object/sign/', '/storage/v1/object/public/'),
        '\?token=.*$',
        '')
WHERE profile_picture_url LIKE '%/storage/v1/object/sign/%';

UPDATE tutor_profile
SET profile_picture_url = REGEXP_REPLACE(
        REGEXP_REPLACE(profile_picture_url, '/storage/v1/object/sign/', '/storage/v1/object/public/'),
        '\?token=.*$',
        '')
WHERE profile_picture_url LIKE '%/storage/v1/object/sign/%';

-- 2. Dead blob URLs from the old registration form.
UPDATE users
SET profile_picture_url = NULL
WHERE profile_picture_url LIKE 'blob:%';

UPDATE student_profile
SET profile_picture_url = NULL
WHERE profile_picture_url LIKE 'blob:%';

UPDATE tutor_profile
SET profile_picture_url = NULL
WHERE profile_picture_url LIKE 'blob:%';

-- 3. Verification: no signed or blob URLs must remain.
SELECT 'users' AS tbl, profile_picture_url
FROM users
WHERE profile_picture_url IS NOT NULL
  AND (profile_picture_url LIKE '%/object/sign/%' OR profile_picture_url LIKE 'blob:%')
UNION ALL
SELECT 'student_profile', profile_picture_url
FROM student_profile
WHERE profile_picture_url IS NOT NULL
  AND (profile_picture_url LIKE '%/object/sign/%' OR profile_picture_url LIKE 'blob:%')
UNION ALL
SELECT 'tutor_profile', profile_picture_url
FROM tutor_profile
WHERE profile_picture_url IS NOT NULL
  AND (profile_picture_url LIKE '%/object/sign/%' OR profile_picture_url LIKE 'blob:%');