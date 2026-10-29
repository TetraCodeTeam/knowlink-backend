-- =============================================
-- Demo data only. Do not use in production.
-- KnowLink - Seed: administrador de prueba para el panel /api/v1/admin/**
--
--   Email:      admin.demo@knowlink.test
--   Contrasena: Admin123!
--   Rol:        ADMIN
--   Estado:     ACTIVE
--
-- Idempotente: si el email ya existe no inserta ni modifica nada.
-- En produccion los administradores se crean con KNOWLINK_ADMIN_ACCOUNTS_*
-- (AdminSeeder) y su contrasena nunca se guarda en el repositorio.
--
-- Uso:
--   mysql --default-character-set=utf8mb4 -u <user> -p <database> < seed-admin-demo.sql
-- =============================================

INSERT INTO users (user_id, full_name, email, password, role, account_status, created_at)
SELECT UNHEX(REPLACE(UUID(), '-', '')),
       'Administrador Demo',
       'admin.demo@knowlink.test',
       '$2a$10$yJleJFTDhCADv0hxVMug5eIU4odBRpabx/rWb40mfOjP..gEPZAN2',
       'ADMIN',
       'ACTIVE',
       NOW(6)
WHERE NOT EXISTS (
    SELECT 1 FROM users WHERE email = 'admin.demo@knowlink.test'
);