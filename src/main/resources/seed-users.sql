-- =============================================
-- KnowLink - Seed: 2 Alumnos + 3 Tutores
-- Contrasena para todos: Password1!
-- =============================================

-- Alumnos (STUDENT)
INSERT IGNORE INTO users (user_id, full_name, email, password, dni, phone_number, role, account_status, created_at)
VALUES
  (UUID(), 'Juan Perez',      'juan.perez@test.com',      '$2a$10$mIlh0.lvtcVeoAM/TX4nQeeL7NvVzaEMmdy/CtdszS8bMvH.7n7V6', '40123456', '1155551111', 'STUDENT', 'ACTIVE', NOW()),
  (UUID(), 'Maria Garcia',    'maria.garcia@test.com',    '$2a$10$mIlh0.lvtcVeoAM/TX4nQeeL7NvVzaEMmdy/CtdszS8bMvH.7n7V6', '41234567', '1155552222', 'STUDENT', 'ACTIVE', NOW());

-- Tutores (TUTOR)
INSERT IGNORE INTO users (user_id, full_name, email, password, dni, phone_number, role, account_status, created_at)
VALUES
  (UUID(), 'Carlos Lopez',        'carlos.lopez@test.com',        '$2a$10$mIlh0.lvtcVeoAM/TX4nQeeL7NvVzaEMmdy/CtdszS8bMvH.7n7V6', '38234567', '1155553333', 'TUTOR', 'ACTIVE', NOW()),
  (UUID(), 'Ana Martinez',        'ana.martinez@test.com',        '$2a$10$mIlh0.lvtcVeoAM/TX4nQeeL7NvVzaEMmdy/CtdszS8bMvH.7n7V6', '39345678', '1155554444', 'TUTOR', 'ACTIVE', NOW()),
  (UUID(), 'Pedro Fernandez',     'pedro.fernandez@test.com',     '$2a$10$mIlh0.lvtcVeoAM/TX4nQeeL7NvVzaEMmdy/CtdszS8bMvH.7n7V6', '37456789', '1155555555', 'TUTOR', 'ACTIVE', NOW());
