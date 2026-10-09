-- ============================================================
-- FEAT-CATALOGO-INSTITUCIONAL — migración manual de datos (MySQL 8)
--
-- ⚠️  ESTE SCRIPT NO SE EJECUTA AUTOMÁTICAMENTE.
--     Requiere revisión y aprobación de una persona del equipo
--     antes de correrlo sobre cualquier base con datos reales.
--
-- Contexto: el proyecto NO usa Flyway ni Liquibase; el esquema se
-- genera con hibernate.ddl-auto=update (dev/docker) y create-drop
-- (perfil test, H2). El script se ejecuta a mano sobre la base de
-- desarrollo/producción.
--
-- Orden operativo recomendado:
--   1. Backup de la base.
--   2. Deploy del código nuevo (ddl-auto=update agrega: tabla
--      `institution`, columnas `institution_id`/`type` en `career` y
--      `subjects`, y la tabla `subject_career`).
--   3. VERIFICAR los tipos de columna generados: los UUID deben ser
--      BINARY(16). Si Hibernate generó CHAR(32)/CHAR(36), ajustar los
--      UNHEX() de abajo en consecuencia.
--   4. Ejecutar este script INMEDIATAMENTE después del deploy, antes
--      de recibir tráfico, porque los pasos 5-8 corrigen valores que
--      ddl-auto deja en cero/cadena vacía en filas existentes.
--   5. Reiniciar la aplicación para verificar el arranque limpio.
--
-- Si la base está vacía (recién creada), NO hace falta este script:
-- los seeders (InstitutionSeeder/CareerSeeder/SubjectSeeder) crean
-- el catálogo inicial.
-- ============================================================

-- ------------------------------------------------------------
-- 1) Institución por defecto para los datos existentes
-- ------------------------------------------------------------
INSERT INTO institution (institution_id, name, created_at)
SELECT UNHEX(REPLACE(UUID(), '-', '')), 'UTN FRVM', UTC_TIMESTAMP()
WHERE NOT EXISTS (SELECT 1 FROM institution WHERE name = 'UTN FRVM');

-- ------------------------------------------------------------
-- 2) Carrera reservada "Materias Compartidas" de esa institución
--    (el sistema la identifica por type = 'COMPARTIDA', nunca por nombre)
-- ------------------------------------------------------------
INSERT INTO career (career_id, name, institution_id, type)
SELECT UNHEX(REPLACE(UUID(), '-', '')), 'Materias Compartidas', i.institution_id, 'COMPARTIDA'
FROM institution i
WHERE i.name = 'UTN FRVM'
  AND NOT EXISTS (
      SELECT 1 FROM career c
      WHERE c.institution_id = i.institution_id AND c.type = 'COMPARTIDA'
  );

-- ------------------------------------------------------------
-- 3) Backfill de institution_id en career
--    (ddl-auto puede dejar NULL o el valor implícito BINARY(16) cero)
-- ------------------------------------------------------------
UPDATE career c
JOIN institution i ON i.name = 'UTN FRVM'
SET c.institution_id = i.institution_id
WHERE c.institution_id IS NULL
   OR c.institution_id = UNHEX('00000000000000000000000000000000');

-- ------------------------------------------------------------
-- 4) Backfill de institution_id en subjects desde su carrera original
--    (antes de droppear subjects.career_id)
-- ------------------------------------------------------------
UPDATE subjects s
JOIN career c ON c.career_id = s.career_id
SET s.institution_id = c.institution_id
WHERE s.institution_id IS NULL
   OR s.institution_id = UNHEX('00000000000000000000000000000000');

-- Materias que quedaron sin carrera asociada (si las hubiera) van a la
-- institución por defecto; luego se asocian a la carrera reservada.
UPDATE subjects s
JOIN institution i ON i.name = 'UTN FRVM'
SET s.institution_id = i.institution_id
WHERE s.institution_id IS NULL
   OR s.institution_id = UNHEX('00000000000000000000000000000000');

-- ------------------------------------------------------------
-- 5) Corrección de columnas que ddl-auto creó NOT NULL con valores
--    implícitos en filas viejas (vacío/cero)
-- ------------------------------------------------------------
UPDATE career SET type = 'REGULAR'
WHERE type IS NULL OR type = '' OR type = '0'
   OR type = UNHEX('00000000000000000000000000000000');

-- ------------------------------------------------------------
-- 6) Volcado de la asociación legacy subjects.career_id a subject_career
-- ------------------------------------------------------------
INSERT INTO subject_career (subject_id, career_id)
SELECT s.subject_id, s.career_id
FROM subjects s
WHERE s.career_id IS NOT NULL
  AND NOT EXISTS (
      SELECT 1 FROM subject_career sc
      WHERE sc.subject_id = s.subject_id AND sc.career_id = s.career_id
  );

-- ------------------------------------------------------------
-- 7) Las materias "básicas" pasan a la carrera reservada
--    (antes colgaban de una carrera placeholder)
-- ------------------------------------------------------------
INSERT INTO subject_career (subject_id, career_id)
SELECT s.subject_id, c.career_id
FROM subjects s
JOIN institution i ON i.institution_id = s.institution_id
JOIN career c ON c.institution_id = i.institution_id AND c.type = 'COMPARTIDA'
WHERE s.is_basic = 1
  AND NOT EXISTS (
      SELECT 1 FROM subject_career sc
      WHERE sc.subject_id = s.subject_id AND sc.career_id = c.career_id
  );

-- ------------------------------------------------------------
-- 8) Constraints de unicidad nuevas + limpieza del modelo viejo
--    (ejecutar verificando antes con SHOW INDEX FROM ...)
-- ------------------------------------------------------------
-- 8a) unicidad (institution_id, name) en career — antes era unique global en name
ALTER TABLE career DROP INDEX UK4i26x57mopr9pseu6r3d1faia;   -- unique global legacy sobre career.name
ALTER TABLE career ADD UNIQUE KEY uk_career_institution_name (institution_id, name);

-- 8b) unicidad (institution_id, name) en subjects — antes era unique global en name
ALTER TABLE subjects DROP INDEX UKaodt3utnw0lsov4k9ta88dbpr;  -- unique global legacy sobre subjects.name
ALTER TABLE subjects ADD UNIQUE KEY uk_subject_institution_name (institution_id, name);

-- 8c) NOT NULL de las columnas nuevas (si ddl-auto las creó nullable)
ALTER TABLE career MODIFY institution_id BINARY(16) NOT NULL;
ALTER TABLE career MODIFY type VARCHAR(255) NOT NULL;
ALTER TABLE subjects MODIFY institution_id BINARY(16) NOT NULL;

-- 8d) se elimina la FK/columna legacy: la relación es N:N vía subject_career
ALTER TABLE subjects DROP FOREIGN KEY FK9ay6rjt9nij0xpff1rvvy631g;  -- FK legacy subjects.career_id → career
ALTER TABLE subjects DROP COLUMN career_id;

-- ------------------------------------------------------------
-- 9) Verificaciones manuales esperadas antes de dar por válido el
--    proceso (correrlas y revisar los resultados):
-- ------------------------------------------------------------
-- SELECT COUNT(*) FROM career WHERE institution_id IS NULL;            --> 0
-- SELECT COUNT(*) FROM subjects WHERE institution_id IS NULL;          --> 0
-- SELECT COUNT(*) FROM career WHERE type = 'COMPARTIDA' GROUP BY institution_id HAVING COUNT(*) > 1;  --> sin filas
-- SELECT COUNT(*) FROM subjects s LEFT JOIN subject_career sc ON sc.subject_id = s.subject_id WHERE sc.subject_id IS NULL;  --> 0
-- SHOW CREATE TABLE subjects;   --> sin columna career_id