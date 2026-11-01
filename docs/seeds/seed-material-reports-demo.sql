-- =============================================
-- Demo data only. Do not use in production.
-- KnowLink - Seed: denuncias de material para el panel /api/v1/admin/material-reports
--
-- Genera 4 casos con todos los estados y 8 denuncias:
--   PENDING   3 denuncias  (material PDF existente)
--   IN_REVIEW 2 denuncias  (material PNG existente, asignado a admin.demo)
--   REMOVED   2 denuncias  (material nuevo dado de baja)
--   DISMISSED 1 denuncia   (caso viejo del mismo PDF)
--
-- Requisitos:
--   - Haber ejecutado docs/seeds/seed-admin-demo.sql (admin.demo@knowlink.test).
--   - Que existan los alumnos juan.perez, maria.garcia, sofia.gomez,
--     diego.rios y test02.t02 (usuarios de prueba del entorno local).
--   - Que existan al menos los materiales "Balance-de-masa-y-energia.pdf"
--     y "qr1.png" (materiales de prueba ya cargados).
--
-- Idempotente: usa IDs fijos y no inserta dos veces el mismo registro.
-- Si falta algún usuario o material, omite las filas que dependen de él.
--
-- Uso:
--   mysql --default-character-set=utf8mb4 -u <user> -p <database> < seed-material-reports-demo.sql
-- =============================================

SET @admin_id = (SELECT user_id FROM users WHERE email = 'admin.demo@knowlink.test' LIMIT 1);
SET @juan_id = (SELECT user_id FROM users WHERE email = 'juan.perez@test.com' LIMIT 1);
SET @maria_id = (SELECT user_id FROM users WHERE email = 'maria.garcia@test.com' LIMIT 1);
SET @sofia_id = (SELECT user_id FROM users WHERE email = 'sofia.gomez@test.com' LIMIT 1);
SET @diego_id = (SELECT user_id FROM users WHERE email = 'diego.rios@test.com' LIMIT 1);
SET @test02_id = (SELECT user_id FROM users WHERE email = 'test02.t02@gmail.com' LIMIT 1);

SET @pdf_id = (SELECT academic_material_id FROM academic_material
               WHERE original_file_name = 'Balance-de-masa-y-energia.pdf' LIMIT 1);
SET @png_id = (SELECT academic_material_id FROM academic_material
               WHERE original_file_name = 'qr1.png' LIMIT 1);

-- Materia activa de carlos.lopez para colgar el material que se da de baja.
SET @carlos_ts_id = (SELECT ts.tutor_subject_id
                     FROM tutor_subject ts
                     JOIN tutor_profile tp ON tp.tutor_profile_id = ts.tutor_profile_id
                     JOIN users u ON u.user_id = tp.user_id
                     WHERE u.email = 'carlos.lopez@test.com'
                       AND ts.tutor_subject_status = 'ACTIVE'
                     ORDER BY ts.tutor_subject_id LIMIT 1);

-- Material nuevo (copia el archivo real del PDF para que la descarga funcione).
INSERT INTO academic_material
    (academic_material_id, active, available, material_type, name, original_file_name,
     reports_count, size_in_bytes, storage_path, uploaded_at, tutor_subject_id)
SELECT UNHEX(REPLACE('e1000000-0000-4000-8000-000000000003', '-', '')),
       0, 0, 'PDF', 'Guía de Base de Datos', 'guia-base-de-datos.pdf',
       0,
       (SELECT size_in_bytes FROM academic_material WHERE academic_material_id = @pdf_id),
       (SELECT storage_path FROM academic_material WHERE academic_material_id = @pdf_id),
       NOW(6) - INTERVAL 6 DAY,
       @carlos_ts_id
FROM DUAL
WHERE NOT EXISTS (
        SELECT 1 FROM academic_material
        WHERE academic_material_id = UNHEX(REPLACE('e1000000-0000-4000-8000-000000000003', '-', '')))
  AND @carlos_ts_id IS NOT NULL
  AND @pdf_id IS NOT NULL;

SET @new_material_id = (SELECT academic_material_id FROM academic_material
                        WHERE academic_material_id = UNHEX(REPLACE('e1000000-0000-4000-8000-000000000003', '-', '')));

-- Caso PENDING: 3 denuncias sobre el PDF existente.
INSERT INTO material_report_case
    (material_report_case_id, assigned_at, opened_at, reports_count, resolution_note,
     resolved_at, status, version, academic_material_id, assigned_admin_id)
SELECT UNHEX(REPLACE('a1000000-0000-4000-8000-000000000001', '-', '')),
       NULL, NOW(6) - INTERVAL 2 DAY, 3, NULL, NULL, 'PENDING', 0, @pdf_id, NULL
FROM DUAL
WHERE NOT EXISTS (
        SELECT 1 FROM material_report_case
        WHERE material_report_case_id = UNHEX(REPLACE('a1000000-0000-4000-8000-000000000001', '-', '')))
  AND @pdf_id IS NOT NULL;

-- Caso IN_REVIEW: 2 denuncias sobre el PNG, ya tomado por el admin demo.
INSERT INTO material_report_case
    (material_report_case_id, assigned_at, opened_at, reports_count, resolution_note,
     resolved_at, status, version, academic_material_id, assigned_admin_id)
SELECT UNHEX(REPLACE('b1000000-0000-4000-8000-000000000002', '-', '')),
       NOW(6) - INTERVAL 20 HOUR, NOW(6) - INTERVAL 1 DAY, 2, NULL, NULL, 'IN_REVIEW', 0,
       @png_id, @admin_id
FROM DUAL
WHERE NOT EXISTS (
        SELECT 1 FROM material_report_case
        WHERE material_report_case_id = UNHEX(REPLACE('b1000000-0000-4000-8000-000000000002', '-', '')))
  AND @png_id IS NOT NULL
  AND @admin_id IS NOT NULL;

-- Caso REMOVED: material dado de baja con nota de resolución.
INSERT INTO material_report_case
    (material_report_case_id, assigned_at, opened_at, reports_count, resolution_note,
     resolved_at, status, version, academic_material_id, assigned_admin_id)
SELECT UNHEX(REPLACE('c1000000-0000-4000-8000-000000000003', '-', '')),
       NOW(6) - INTERVAL 6 DAY, NOW(6) - INTERVAL 6 DAY, 2,
       'Se retira el material por contenido ofensivo (política de convivencia).',
       NOW(6) - INTERVAL 5 DAY, 'REMOVED', 0, @new_material_id, @admin_id
FROM DUAL
WHERE NOT EXISTS (
        SELECT 1 FROM material_report_case
        WHERE material_report_case_id = UNHEX(REPLACE('c1000000-0000-4000-8000-000000000003', '-', '')))
  AND @new_material_id IS NOT NULL
  AND @admin_id IS NOT NULL;

-- Caso DISMISSED: denuncia vieja del mismo PDF, desestimada.
INSERT INTO material_report_case
    (material_report_case_id, assigned_at, opened_at, reports_count, resolution_note,
     resolved_at, status, version, academic_material_id, assigned_admin_id)
SELECT UNHEX(REPLACE('d1000000-0000-4000-8000-000000000004', '-', '')),
       NOW(6) - INTERVAL 14 DAY, NOW(6) - INTERVAL 14 DAY, 1, NULL,
       NOW(6) - INTERVAL 12 DAY, 'DISMISSED', 0, @pdf_id, @admin_id
FROM DUAL
WHERE NOT EXISTS (
        SELECT 1 FROM material_report_case
        WHERE material_report_case_id = UNHEX(REPLACE('d1000000-0000-4000-8000-000000000004', '-', '')))
  AND @pdf_id IS NOT NULL
  AND @admin_id IS NOT NULL;

-- Denuncias del caso PENDING.
INSERT INTO academic_material_report
    (academic_material_report_id, description, reason, reported_at, academic_material_id,
     reporter_user_id, resolution, status, reviewed_by_user_id, material_report_case_id)
SELECT UNHEX(REPLACE('a2000000-0000-4000-8000-000000000001', '-', '')),
       'El archivo contiene lenguaje ofensivo en la página 3.',
       'INAPPROPRIATE_CONTENT', NOW(6) - INTERVAL 2 DAY, @pdf_id, @juan_id, NULL, 'PENDING', NULL,
       UNHEX(REPLACE('a1000000-0000-4000-8000-000000000001', '-', ''))
FROM DUAL
WHERE NOT EXISTS (
        SELECT 1 FROM academic_material_report
        WHERE academic_material_report_id = UNHEX(REPLACE('a2000000-0000-4000-8000-000000000001', '-', '')))
  AND @pdf_id IS NOT NULL AND @juan_id IS NOT NULL;

INSERT INTO academic_material_report
    (academic_material_report_id, description, reason, reported_at, academic_material_id,
     reporter_user_id, resolution, status, reviewed_by_user_id, material_report_case_id)
SELECT UNHEX(REPLACE('a2000000-0000-4000-8000-000000000002', '-', '')),
       'Parece una copia textual del apunte de la cátedra.',
       'PLAGIARISM', NOW(6) - INTERVAL 2 DAY + INTERVAL 2 HOUR, @pdf_id, @maria_id, NULL, 'PENDING', NULL,
       UNHEX(REPLACE('a1000000-0000-4000-8000-000000000001', '-', ''))
FROM DUAL
WHERE NOT EXISTS (
        SELECT 1 FROM academic_material_report
        WHERE academic_material_report_id = UNHEX(REPLACE('a2000000-0000-4000-8000-000000000002', '-', '')))
  AND @pdf_id IS NOT NULL AND @maria_id IS NOT NULL;

INSERT INTO academic_material_report
    (academic_material_report_id, description, reason, reported_at, academic_material_id,
     reporter_user_id, resolution, status, reviewed_by_user_id, material_report_case_id)
SELECT UNHEX(REPLACE('a2000000-0000-4000-8000-000000000003', '-', '')),
       'Los datos de la tabla 2 están mal y confunden.',
       'FALSE_INFORMATION', NOW(6) - INTERVAL 2 DAY + INTERVAL 5 HOUR, @pdf_id, @sofia_id, NULL, 'PENDING', NULL,
       UNHEX(REPLACE('a1000000-0000-4000-8000-000000000001', '-', ''))
FROM DUAL
WHERE NOT EXISTS (
        SELECT 1 FROM academic_material_report
        WHERE academic_material_report_id = UNHEX(REPLACE('a2000000-0000-4000-8000-000000000003', '-', '')))
  AND @pdf_id IS NOT NULL AND @sofia_id IS NOT NULL;

-- Denuncias del caso IN_REVIEW.
INSERT INTO academic_material_report
    (academic_material_report_id, description, reason, reported_at, academic_material_id,
     reporter_user_id, resolution, status, reviewed_by_user_id, material_report_case_id)
SELECT UNHEX(REPLACE('b2000000-0000-4000-8000-000000000001', '-', '')),
       'El material no corresponde a la materia.',
       'OTHER', NOW(6) - INTERVAL 1 DAY, @png_id, @test02_id, NULL, 'IN_REVIEW', NULL,
       UNHEX(REPLACE('b1000000-0000-4000-8000-000000000002', '-', ''))
FROM DUAL
WHERE NOT EXISTS (
        SELECT 1 FROM academic_material_report
        WHERE academic_material_report_id = UNHEX(REPLACE('b2000000-0000-4000-8000-000000000001', '-', '')))
  AND @png_id IS NOT NULL AND @test02_id IS NOT NULL;

INSERT INTO academic_material_report
    (academic_material_report_id, description, reason, reported_at, academic_material_id,
     reporter_user_id, resolution, status, reviewed_by_user_id, material_report_case_id)
SELECT UNHEX(REPLACE('b2000000-0000-4000-8000-000000000002', '-', '')),
       'Incluye capturas con datos personales.',
       'INAPPROPRIATE_CONTENT', NOW(6) - INTERVAL 1 DAY + INTERVAL 3 HOUR, @png_id, @diego_id, NULL, 'IN_REVIEW', NULL,
       UNHEX(REPLACE('b1000000-0000-4000-8000-000000000002', '-', ''))
FROM DUAL
WHERE NOT EXISTS (
        SELECT 1 FROM academic_material_report
        WHERE academic_material_report_id = UNHEX(REPLACE('b2000000-0000-4000-8000-000000000002', '-', '')))
  AND @png_id IS NOT NULL AND @diego_id IS NOT NULL;

-- Denuncias del caso REMOVED (resueltas a favor de la denuncia).
INSERT INTO academic_material_report
    (academic_material_report_id, description, reason, reported_at, academic_material_id,
     reporter_user_id, resolution, status, reviewed_by_user_id, material_report_case_id)
SELECT UNHEX(REPLACE('c2000000-0000-4000-8000-000000000001', '-', '')),
       'Contenido ofensivo hacia un grupo de estudiantes.',
       'INAPPROPRIATE_CONTENT', NOW(6) - INTERVAL 6 DAY, @new_material_id, @juan_id, 'UPHELD', 'RESOLVED',
       @admin_id, UNHEX(REPLACE('c1000000-0000-4000-8000-000000000003', '-', ''))
FROM DUAL
WHERE NOT EXISTS (
        SELECT 1 FROM academic_material_report
        WHERE academic_material_report_id = UNHEX(REPLACE('c2000000-0000-4000-8000-000000000001', '-', '')))
  AND @new_material_id IS NOT NULL AND @juan_id IS NOT NULL AND @admin_id IS NOT NULL;

INSERT INTO academic_material_report
    (academic_material_report_id, description, reason, reported_at, academic_material_id,
     reporter_user_id, resolution, status, reviewed_by_user_id, material_report_case_id)
SELECT UNHEX(REPLACE('c2000000-0000-4000-8000-000000000002', '-', '')),
       'El documento tiene insultos en varias páginas.',
       'INAPPROPRIATE_CONTENT', NOW(6) - INTERVAL 6 DAY + INTERVAL 4 HOUR, @new_material_id, @maria_id, 'UPHELD', 'RESOLVED',
       @admin_id, UNHEX(REPLACE('c1000000-0000-4000-8000-000000000003', '-', ''))
FROM DUAL
WHERE NOT EXISTS (
        SELECT 1 FROM academic_material_report
        WHERE academic_material_report_id = UNHEX(REPLACE('c2000000-0000-4000-8000-000000000002', '-', '')))
  AND @new_material_id IS NOT NULL AND @maria_id IS NOT NULL AND @admin_id IS NOT NULL;

-- Denuncia del caso DISMISSED.
INSERT INTO academic_material_report
    (academic_material_report_id, description, reason, reported_at, academic_material_id,
     reporter_user_id, resolution, status, reviewed_by_user_id, material_report_case_id)
SELECT UNHEX(REPLACE('d2000000-0000-4000-8000-000000000001', '-', '')),
       'Creo que el archivo está incompleto.',
       'OTHER', NOW(6) - INTERVAL 14 DAY, @pdf_id, @diego_id, 'DISMISSED', 'RESOLVED',
       @admin_id, UNHEX(REPLACE('d1000000-0000-4000-8000-000000000004', '-', ''))
FROM DUAL
WHERE NOT EXISTS (
        SELECT 1 FROM academic_material_report
        WHERE academic_material_report_id = UNHEX(REPLACE('d2000000-0000-4000-8000-000000000001', '-', '')))
  AND @pdf_id IS NOT NULL AND @diego_id IS NOT NULL AND @admin_id IS NOT NULL;

-- Sincroniza contadores con las denuncias realmente insertadas.
UPDATE material_report_case c
SET c.reports_count = (
        SELECT COUNT(*) FROM academic_material_report r
        WHERE r.material_report_case_id = c.material_report_case_id)
WHERE c.material_report_case_id IN (
    UNHEX(REPLACE('a1000000-0000-4000-8000-000000000001', '-', '')),
    UNHEX(REPLACE('b1000000-0000-4000-8000-000000000002', '-', '')),
    UNHEX(REPLACE('c1000000-0000-4000-8000-000000000003', '-', '')),
    UNHEX(REPLACE('d1000000-0000-4000-8000-000000000004', '-', '')));

UPDATE academic_material m
SET m.reports_count = (
        SELECT COUNT(*) FROM academic_material_report r
        WHERE r.academic_material_id = m.academic_material_id)
WHERE m.academic_material_id IN (
    @pdf_id, @png_id,
    UNHEX(REPLACE('e1000000-0000-4000-8000-000000000003', '-', '')));

-- Resumen para verificar la carga.
SELECT c.status, COUNT(*) AS cases, SUM(c.reports_count) AS reports
FROM material_report_case c
WHERE c.material_report_case_id IN (
    UNHEX(REPLACE('a1000000-0000-4000-8000-000000000001', '-', '')),
    UNHEX(REPLACE('b1000000-0000-4000-8000-000000000002', '-', '')),
    UNHEX(REPLACE('c1000000-0000-4000-8000-000000000003', '-', '')),
    UNHEX(REPLACE('d1000000-0000-4000-8000-000000000004', '-', '')))
GROUP BY c.status
ORDER BY c.status;