-- =============================================
-- KnowLink - Seed: Perfiles de Tutor + Materias
-- =============================================

-- Tutor Profile: Carlos Lopez (Ingenieria en Sistemas)
INSERT IGNORE INTO tutor_profile (tutor_profile_id, user_id, career_id, biography, mercado_pago_linked, verified, created_at)
SELECT UUID_TO_BIN(UUID()), u.user_id, c.career_id, 'Profesor de programacion y bases de datos', b'0', b'0', NOW()
FROM users u, career c
WHERE u.email = 'carlos.lopez@test.com' AND c.name = 'Ingenieria en Sistemas';

-- Tutor Profile: Ana Martinez (Ciencias de la Computacion)
INSERT IGNORE INTO tutor_profile (tutor_profile_id, user_id, career_id, biography, mercado_pago_linked, verified, created_at)
SELECT UUID_TO_BIN(UUID()), u.user_id, c.career_id, 'Especialista en algoritmos y estructuras de datos', b'0', b'0', NOW()
FROM users u, career c
WHERE u.email = 'ana.martinez@test.com' AND c.name = 'Ciencias de la Computación';

-- Tutor Profile: Pedro Fernandez (Ingenieria en Sistemas)
INSERT IGNORE INTO tutor_profile (tutor_profile_id, user_id, career_id, biography, mercado_pago_linked, verified, created_at)
SELECT UUID_TO_BIN(UUID()), u.user_id, c.career_id, 'Docente de inteligencia artificial y machine learning', b'0', b'0', NOW()
FROM users u, career c
WHERE u.email = 'pedro.fernandez@test.com' AND c.name = 'Ingenieria en Sistemas';

-- Materias para Carlos Lopez (Programacion, Base de Datos, Estructuras de Datos)
INSERT IGNORE INTO tutor_subject (tutor_subject_id, tutor_profile_id, subject_id, description, price_per_hour, compensation_type, tutor_subject_status, modality)
SELECT UUID_TO_BIN(UUID()), tp.tutor_profile_id, s.subject_id, CONCAT('Clases de ', s.name), 2500.00, 'PAID', 'ACTIVE', 'BOTH'
FROM tutor_profile tp
JOIN users u ON u.user_id = tp.user_id
JOIN subjects s ON s.career_id = tp.career_id
WHERE u.email = 'carlos.lopez@test.com'
  AND s.name IN ('Programación', 'Base de Datos', 'Estructuras de Datos');

-- Materias para Ana Martinez (Teoria de la Computacion, Compiladores)
INSERT IGNORE INTO tutor_subject (tutor_subject_id, tutor_profile_id, subject_id, description, price_per_hour, compensation_type, tutor_subject_status, modality)
SELECT UUID_TO_BIN(UUID()), tp.tutor_profile_id, s.subject_id, CONCAT('Clases de ', s.name), 3000.00, 'PAID', 'ACTIVE', 'VIRTUAL'
FROM tutor_profile tp
JOIN users u ON u.user_id = tp.user_id
JOIN subjects s ON s.career_id = tp.career_id
WHERE u.email = 'ana.martinez@test.com'
  AND s.name IN ('Teoría de la Computación', 'Compiladores');

-- Materias para Pedro Fernandez (Inteligencia Artificial, Machine Learning, Sistemas Operativos)
INSERT IGNORE INTO tutor_subject (tutor_subject_id, tutor_profile_id, subject_id, description, price_per_hour, compensation_type, tutor_subject_status, modality)
SELECT UUID_TO_BIN(UUID()), tp.tutor_profile_id, s.subject_id, CONCAT('Clases de ', s.name), 3500.00, 'PAID', 'ACTIVE', 'BOTH'
FROM tutor_profile tp
JOIN users u ON u.user_id = tp.user_id
JOIN subjects s ON s.career_id = tp.career_id
WHERE u.email = 'pedro.fernandez@test.com'
  AND s.name IN ('Inteligencia Artificial', 'Machine Learning', 'Sistemas Operativos');
