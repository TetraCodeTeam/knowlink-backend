-- =============================================
-- KnowLink - Materia verificada por promedio de calificaciones
-- Adds the derived verification columns to tutor_subject and backfills them
-- from the ratings that actually count (see docs/plan-materia-verificada-por-promedio.md).
--
-- RUN THIS SCRIPT MANUALLY BEFORE MERGING TO main (Render deploys main with
-- ddl-auto, but ddl-auto only adds empty columns: it cannot backfill them):
--   mysql --default-character-set=utf8mb4 -u <user> -p <database> < migration-subject-verification-by-rating.sql
--
-- Counting ratings (same filters as the application):
--   rating.visible = TRUE (blind review window elapsed)
--   booking.booking_status = 'COMPLETED'
--   rating.rated_user_id = booking.tutor_id (ratings given to the tutor)
--   grouped by booking.tutor_subject_id (per subject, not per tutor)
--
-- average_rating stores the average truncated to 2 decimals so the displayed
-- value never claims more than the unrounded average used for `verified`
-- (the comparison is done on the exact total/count division, not on the column).
-- Safe to re-run: the UPDATEs are idempotent, the ALTER is not (run once).
-- =============================================

ALTER TABLE tutor_subject
    ADD COLUMN verified BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN average_rating DECIMAL(3, 2) NULL,
    ADD COLUMN rating_count INT NOT NULL DEFAULT 0;

-- Backfill: every subject starts without ratings, then the aggregated ones win.
UPDATE tutor_subject
SET verified = FALSE,
    average_rating = NULL,
    rating_count = 0;

UPDATE tutor_subject ts
LEFT JOIN (
    SELECT b.tutor_subject_id AS tutor_subject_id,
           SUM(r.score) AS total_score,
           COUNT(r.rating_id) AS rating_count
    FROM rating r
    JOIN booking b ON b.booking_id = r.booking_id
    WHERE r.visible = TRUE
      AND b.booking_status = 'COMPLETED'
      AND r.rated_user_id = b.tutor_id
    GROUP BY b.tutor_subject_id
) agg ON agg.tutor_subject_id = ts.tutor_subject_id
SET ts.rating_count = COALESCE(agg.rating_count, 0),
    ts.average_rating = CASE
        WHEN COALESCE(agg.rating_count, 0) = 0 THEN NULL
        ELSE TRUNCATE(agg.total_score / agg.rating_count, 2)
    END,
    ts.verified = CASE
        WHEN COALESCE(agg.rating_count, 0) < 1 THEN FALSE
        ELSE (agg.total_score / agg.rating_count) BETWEEN 4.5 AND 5.0
    END;

-- Optional but recommended (B1 cleanup): subjects that ended up ACTIVE only
-- because MaterialServiceImpl#findOrCreateTutorSubject created them that way
-- go back to PENDING unless the new average already verified them. Nothing
-- reads tutor_subject_status as "verified" anymore after this change.
UPDATE tutor_subject
SET tutor_subject_status = 'PENDING'
WHERE verified = FALSE
  AND tutor_subject_status = 'ACTIVE';

-- Tutor badge: derived = at least one verified AND active subject.
UPDATE tutor_profile tp
SET tp.verified = EXISTS (
    SELECT 1
    FROM tutor_subject ts
    WHERE ts.tutor_profile_id = tp.tutor_profile_id
      AND ts.verified = TRUE
      AND ts.active = TRUE
);