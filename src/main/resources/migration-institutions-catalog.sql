-- =============================================
-- KnowLink - FEAT-CATALOGO-INSTITUCIONAL migration
-- Adds the institution catalog and scopes careers/subjects by institution.
--
-- Run once against the target database BEFORE deploying the version that
-- maps the new columns:
--   mysql --default-character-set=utf8mb4 -u <user> -p <database> < migration-institutions-catalog.sql
--
-- The script does NOT depend on DDL_AUTO: it checks information_schema and
-- only applies what is missing, so it is safe to re-run and also safe against
-- a fresh database where the application has not created its tables yet.
--
-- IMPORTANT: read the output. If there are orphan subjects (subjects.career_id
-- points to a career that no longer exists) the script prints them and aborts
-- before hardening the schema. Those rows need a human decision.
-- =============================================

-- 1. Institution catalog table.
CREATE TABLE IF NOT EXISTS institution (
    institution_id BINARY(16) NOT NULL,
    name VARCHAR(255) NOT NULL,
    PRIMARY KEY (institution_id),
    UNIQUE KEY uk_institution_name (name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 2. UTN FRVM is the institution that owns all pre-existing data.
INSERT INTO institution (institution_id, name)
SELECT UUID_TO_BIN(UUID()), 'UTN FRVM'
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM institution WHERE name = 'UTN FRVM');

SET @utn_id = (SELECT institution_id FROM institution WHERE name = 'UTN FRVM' LIMIT 1);

-- Table/column guards (information_schema is always available).
SET @career_exists = (
    SELECT COUNT(*) FROM information_schema.TABLES
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'career'
);
SET @subjects_exists = (
    SELECT COUNT(*) FROM information_schema.TABLES
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'subjects'
);
SET @career_institution_exists = (
    SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'career' AND COLUMN_NAME = 'institution_id'
);
SET @career_type_exists = (
    SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'career' AND COLUMN_NAME = 'type'
);
SET @subjects_institution_exists = (
    SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'subjects' AND COLUMN_NAME = 'institution_id'
);
SET @subjects_is_basic_exists = (
    SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'subjects' AND COLUMN_NAME = 'is_basic'
);

-- 3. career: institution_id + type.
SET @ddl = IF(@career_exists = 1 AND @career_institution_exists = 0,
    'ALTER TABLE career ADD COLUMN institution_id BINARY(16) NULL',
    'SELECT 1');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @ddl = IF(@career_exists = 1 AND @career_type_exists = 0,
    'ALTER TABLE career ADD COLUMN type VARCHAR(20) NOT NULL DEFAULT ''REGULAR''',
    'SELECT 1');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- 4. subjects: institution_id.
SET @ddl = IF(@subjects_exists = 1 AND @subjects_institution_exists = 0,
    'ALTER TABLE subjects ADD COLUMN institution_id BINARY(16) NULL',
    'SELECT 1');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- 5. Backfill existing data to UTN FRVM.
--    Two states count as "not assigned yet":
--      - NULL: the column was added by this script (step 3/4).
--      - 16 zero bytes: the column was added first by Hibernate (ddl-auto=update)
--        as NOT NULL, and MySQL filled the pre-existing rows with zeros instead
--        of NULL. Without this, those rows never reach UTN FRVM and the foreign
--        keys in step 10 fail AFTER the old unique indexes were already dropped.
SET @ddl = IF(@career_exists = 1 AND @career_institution_exists = 1,
    CONCAT('UPDATE career SET institution_id = UNHEX(''', HEX(@utn_id), ''') WHERE institution_id IS NULL OR institution_id = UNHEX(REPEAT(''0'', 32))'),
    'SELECT 1');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @ddl = IF(@subjects_exists = 1 AND @subjects_institution_exists = 1,
    CONCAT('UPDATE subjects SET institution_id = UNHEX(''', HEX(@utn_id), ''') WHERE institution_id IS NULL OR institution_id = UNHEX(REPEAT(''0'', 32))'),
    'SELECT 1');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- 6. SHARED career ("Materias Compartidas") for UTN FRVM.
SET @ddl = IF(@career_exists = 1 AND @career_institution_exists = 1 AND @career_type_exists = 1,
    CONCAT(
        'INSERT INTO career (career_id, name, institution_id, type) ',
        'SELECT UUID_TO_BIN(UUID()), ''Materias Compartidas'', UNHEX(''', HEX(@utn_id), '''), ''SHARED'' ',
        'FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM career ',
        'WHERE institution_id = UNHEX(''', HEX(@utn_id), ''') AND type = ''SHARED'')'
    ),
    'SELECT 1');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @shared_career_id = NULL;
SET @ddl = IF(@career_exists = 1 AND @career_institution_exists = 1 AND @career_type_exists = 1,
    CONCAT('SELECT career_id INTO @shared_career_id FROM career ',
        'WHERE institution_id = UNHEX(''', HEX(@utn_id), ''') AND type = ''SHARED'' LIMIT 1'),
    'SELECT 1');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- 7. Move every basic subject to the SHARED career (they used to hang from an
--    arbitrary career used only to satisfy the old NOT NULL FK).
SET @ddl = IF(@subjects_exists = 1 AND @subjects_is_basic_exists = 1 AND @shared_career_id IS NOT NULL,
    CONCAT('UPDATE subjects SET career_id = UNHEX(''', HEX(@shared_career_id), ''') WHERE is_basic = 1'),
    'SELECT 1');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- 8. Orphan subjects: subjects.career_id has no FK, so some rows may point to a
--    career that no longer exists. Hardening the schema is a human decision.
SET @orphan_count = 0;
SET @ddl = IF(@subjects_exists = 1 AND @career_exists = 1,
    'SELECT COUNT(*) INTO @orphan_count FROM subjects s LEFT JOIN career c ON c.career_id = s.career_id WHERE s.career_id IS NOT NULL AND c.career_id IS NULL',
    'SET @orphan_count = 0');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @ddl = IF(@orphan_count > 0,
    'SELECT s.subject_id, s.name, s.career_id FROM subjects s LEFT JOIN career c ON c.career_id = s.career_id WHERE s.career_id IS NOT NULL AND c.career_id IS NULL',
    'SELECT ''No orphan subjects found'' AS orphan_check');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- Abort with a visible error if orphans exist: the referenced table does not
-- exist on purpose, and the mysql client stops on the first error.
SET @ddl = IF(@orphan_count > 0,
    'SELECT * FROM MIGRATION_ABORTED_fix_orphan_subjects_career_id_then_rerun',
    'SELECT 1');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- 9. Replace the global single-column UNIQUE(name) indexes with the composite
--    (institution_id, name) constraint. Hibernate autogenerates the index names,
--    so they are discovered through information_schema instead of assumed.
SET @career_single_unique_indexes = (
    SELECT GROUP_CONCAT(CONCAT('DROP INDEX `', INDEX_NAME, '`') SEPARATOR ', ')
    FROM (
        SELECT INDEX_NAME
        FROM information_schema.STATISTICS
        WHERE TABLE_SCHEMA = DATABASE()
          AND TABLE_NAME = 'career'
          AND NON_UNIQUE = 0
        GROUP BY INDEX_NAME
        HAVING COUNT(*) = 1
           AND MAX(COLUMN_NAME) = 'name'
           AND INDEX_NAME <> 'PRIMARY'
    ) AS single_column_indexes
);
SET @ddl = IF(@career_exists = 1 AND @career_single_unique_indexes IS NOT NULL,
    CONCAT('ALTER TABLE career ', @career_single_unique_indexes),
    'SELECT 1');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @subjects_single_unique_indexes = (
    SELECT GROUP_CONCAT(CONCAT('DROP INDEX `', INDEX_NAME, '`') SEPARATOR ', ')
    FROM (
        SELECT INDEX_NAME
        FROM information_schema.STATISTICS
        WHERE TABLE_SCHEMA = DATABASE()
          AND TABLE_NAME = 'subjects'
          AND NON_UNIQUE = 0
        GROUP BY INDEX_NAME
        HAVING COUNT(*) = 1
           AND MAX(COLUMN_NAME) = 'name'
           AND INDEX_NAME <> 'PRIMARY'
    ) AS single_column_indexes
);
SET @ddl = IF(@subjects_exists = 1 AND @subjects_single_unique_indexes IS NOT NULL,
    CONCAT('ALTER TABLE subjects ', @subjects_single_unique_indexes),
    'SELECT 1');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @career_composite_exists = (
    SELECT COUNT(*) FROM information_schema.STATISTICS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'career'
      AND INDEX_NAME = 'uk_career_institution_name'
);
SET @ddl = IF(@career_exists = 1 AND @career_institution_exists = 1 AND @career_composite_exists = 0,
    'ALTER TABLE career ADD CONSTRAINT uk_career_institution_name UNIQUE (institution_id, name)',
    'SELECT 1');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @subjects_composite_exists = (
    SELECT COUNT(*) FROM information_schema.STATISTICS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'subjects'
      AND INDEX_NAME = 'uk_subject_institution_name'
);
SET @ddl = IF(@subjects_exists = 1 AND @subjects_institution_exists = 1 AND @subjects_composite_exists = 0,
    'ALTER TABLE subjects ADD CONSTRAINT uk_subject_institution_name UNIQUE (institution_id, name)',
    'SELECT 1');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- 10. institution_id becomes NOT NULL and gets its foreign keys.
SET @ddl = IF(@career_exists = 1 AND @career_institution_exists = 1,
    'ALTER TABLE career MODIFY institution_id BINARY(16) NOT NULL',
    'SELECT 1');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @fk_career_exists = (
    SELECT COUNT(*) FROM information_schema.TABLE_CONSTRAINTS
    WHERE CONSTRAINT_SCHEMA = DATABASE()
      AND TABLE_NAME = 'career'
      AND CONSTRAINT_NAME = 'fk_career_institution'
);
SET @ddl = IF(@career_exists = 1 AND @career_institution_exists = 1 AND @fk_career_exists = 0,
    'ALTER TABLE career ADD CONSTRAINT fk_career_institution FOREIGN KEY (institution_id) REFERENCES institution (institution_id)',
    'SELECT 1');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @ddl = IF(@subjects_exists = 1 AND @subjects_institution_exists = 1,
    'ALTER TABLE subjects MODIFY institution_id BINARY(16) NOT NULL',
    'SELECT 1');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @fk_subjects_exists = (
    SELECT COUNT(*) FROM information_schema.TABLE_CONSTRAINTS
    WHERE CONSTRAINT_SCHEMA = DATABASE()
      AND TABLE_NAME = 'subjects'
      AND CONSTRAINT_NAME = 'fk_subject_institution'
);
SET @ddl = IF(@subjects_exists = 1 AND @subjects_institution_exists = 1 AND @fk_subjects_exists = 0,
    'ALTER TABLE subjects ADD CONSTRAINT fk_subject_institution FOREIGN KEY (institution_id) REFERENCES institution (institution_id)',
    'SELECT 1');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- 11. is_basic is no longer mapped by the new code, but the column is kept for a
--     later cleanup PR so rollback to the previous version stays possible.
SET @ddl = IF(@subjects_exists = 1 AND @subjects_is_basic_exists = 1,
    'ALTER TABLE subjects MODIFY is_basic BIT(1) NOT NULL DEFAULT 0',
    'SELECT 1');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- 12. Verification.
SET @ddl = IF(@career_exists = 1 AND @subjects_exists = 1 AND @career_institution_exists = 1 AND @subjects_institution_exists = 1,
    'SELECT i.name AS institution,
            (SELECT COUNT(*) FROM career c WHERE c.institution_id = i.institution_id) AS careers,
            (SELECT COUNT(*) FROM career c WHERE c.institution_id = i.institution_id AND c.type = ''SHARED'') AS shared_careers,
            (SELECT COUNT(*) FROM subjects s WHERE s.institution_id = i.institution_id) AS subjects
     FROM institution i
     ORDER BY i.name',
    'SELECT ''Catalog tables were not present; nothing to verify yet'' AS verification');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @ddl = IF(@subjects_exists = 1 AND @subjects_institution_exists = 1,
    'SELECT COUNT(*) AS subjects_without_institution FROM subjects WHERE institution_id IS NULL',
    'SELECT ''subjects table not present'' AS subjects_without_institution');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- Expected: subjects_without_institution = 0 and exactly one shared career per
-- institution present in the catalog.