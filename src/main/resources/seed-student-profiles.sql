-- =============================================
-- KnowLink - Seed: Perfiles de Alumno
-- =============================================

-- Student Profile: Juan Perez (Ingenieria en Sistemas)
INSERT IGNORE INTO student_profile (student_profile_id, user_id, career_id, created_at)
SELECT UUID_TO_BIN(UUID()), u.user_id, c.career_id, NOW()
FROM users u, career c
WHERE u.email = 'juan.perez@test.com' AND c.name = 'Ingenieria en Sistemas';

-- Student Profile: Maria Garcia (Medicina)
INSERT IGNORE INTO student_profile (student_profile_id, user_id, career_id, created_at)
SELECT UUID_TO_BIN(UUID()), u.user_id, c.career_id, NOW()
FROM users u, career c
WHERE u.email = 'maria.garcia@test.com' AND c.name = 'Medicina';
