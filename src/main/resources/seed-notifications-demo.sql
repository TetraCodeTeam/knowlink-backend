-- =============================================
-- KnowLink - Seed: un ejemplo de cada tipo de notificacion (US-35)
-- Tutor : julianls783@gmail.com (Julián López Salvucci)
-- Alumno: test02.t02@gmail.com (Alumno test Apellido test)
-- Inserta 17 filas en user_notification por usuario (una por
-- cada valor de NotificationType), con textos identicos a los
-- que genera el backend desde la perspectiva de cada rol.
-- Es idempotente: re-ejecutar no duplica filas (dedupe_key
-- unica por destinatario).
-- =============================================

SET @tutor_id = (SELECT user_id FROM users WHERE email = 'julianls783@gmail.com');
SET @alumno_id = (SELECT user_id FROM users WHERE email = 'test02.t02@gmail.com');
SET @booking_tutor = UNHEX(REPLACE('3f2504e0-4f89-11d3-9a0c-0305e82c3301', '-', ''));
SET @booking_alumno = UNHEX(REPLACE('7c9e6679-7425-40de-944b-e07fc1f90ae7', '-', ''));

-- =============================================
-- Seccion 1: Tutor (julianls783@gmail.com)
-- =============================================

INSERT IGNORE INTO user_notification
  (notification_id, recipient_user_id, notification_type, resource_id, title, message, dedupe_key, created_at, read_at)
VALUES
  -- 1. Reserva creada (variante tutor)
  (UNHEX(REPLACE(UUID(), '-', '')), @tutor_id, 'BOOKING_CONFIRMED', @booking_tutor,
   'Nueva reserva confirmada',
   'Juan Perez reservó Física - Martes 21 de octubre a las 10:00.',
   CONCAT('BOOKING_CONFIRMED:', @booking_tutor), NOW() - INTERVAL 170 HOUR, NOW() - INTERVAL 160 HOUR),

  -- 2. Reserva cancelada por el alumno (con reembolso)
  (UNHEX(REPLACE(UUID(), '-', '')), @tutor_id, 'BOOKING_CANCELLED', @booking_tutor,
   'Clase cancelada por el alumno',
   'Juan Perez canceló la clase del Miércoles 22 de octubre. El alumno recibió el reembolso por política de cancelación.',
   CONCAT('BOOKING_CANCELLED:', @booking_tutor), NOW() - INTERVAL 150 HOUR, NOW() - INTERVAL 140 HOUR),

  -- 3. Recordatorio de token en clase en curso (variante tutor)
  (UNHEX(REPLACE(UUID(), '-', '')), @tutor_id, 'SESSION_CONFIRMATION_REMINDER', @booking_tutor,
   'Recordatorio de token. Clase en curso',
   'Tu clase de Física con Juan Perez está en curso. Ingresá el código para confirmar la sesión.',
   CONCAT('SESSION_CONFIRMATION_REMINDER:', @booking_tutor), NOW() - INTERVAL 130 HOUR, NOW() - INTERVAL 128 HOUR),

  -- 4. Sesion confirmada con codigo
  (UNHEX(REPLACE(UUID(), '-', '')), @tutor_id, 'SESSION_CONFIRMED', @booking_tutor,
   'Sesión confirmada',
   'La sesión de Física con Juan Perez fue confirmada.',
   CONCAT('SESSION_CONFIRMED:', @booking_tutor), NOW() - INTERVAL 125 HOUR, NOW() - INTERVAL 124 HOUR),

  -- 5. Token vencido, sesion no confirmada (variante tutor)
  (UNHEX(REPLACE(UUID(), '-', '')), @tutor_id, 'SESSION_NOT_CONFIRMED', @booking_tutor,
   'Token vencido. Sesión no confirmada',
   'El tiempo para ingresar el código de la sesión con Juan Perez venció. La reserva pasó al estado "No confirmada".',
   CONCAT('SESSION_NOT_CONFIRMED:', @booking_tutor), NOW() - INTERVAL 110 HOUR, NOW() - INTERVAL 108 HOUR),

  -- 6. Sesion completada
  (UNHEX(REPLACE(UUID(), '-', '')), @tutor_id, 'SESSION_COMPLETED', @booking_tutor,
   'Tu sesión de Física finalizó',
   'Si hubo algún inconveniente con la asistencia, tenés 24 horas para reclamar.',
   CONCAT('SESSION_COMPLETED:', @booking_tutor), NOW() - INTERVAL 70 HOUR, NOW() - INTERVAL 68 HOUR),

  -- 7. Habilitada calificacion (variante tutor)
  (UNHEX(REPLACE(UUID(), '-', '')), @tutor_id, 'RATING_ENABLED', @booking_tutor,
   'Calificá tu sesión de Física',
   'Contanos cómo fue tu experiencia con Juan Perez. Tenés 24 horas para calificar.',
   CONCAT('RATING_ENABLED:', @booking_tutor), NOW() - INTERVAL 69 HOUR, NOW() - INTERVAL 67 HOUR),

  -- 8. Fondos acreditados al tutor por sesion completada (3% de retencion)
  (UNHEX(REPLACE(UUID(), '-', '')), @tutor_id, 'FUNDS_TRANSFERRED', @booking_tutor,
   'Pago acreditado. Sesión completada',
   'Recibiste $9.991 por la sesión de Física con Juan Perez. (3 de retención aplicada)',
   CONCAT('FUNDS_TRANSFERRED:Liberacion por sesion completada:', @booking_tutor),
   NOW() - INTERVAL 60 HOUR, NOW() - INTERVAL 58 HOUR),

  -- 9. Fondos suspendidos por reclamo activo (variante tutor)
  (UNHEX(REPLACE(UUID(), '-', '')), @tutor_id, 'FUNDS_SUSPENDED', @booking_tutor,
   'Fondos retenidos. Disputa activa',
   'Los fondos de la sesión con Juan Perez están retenidos hasta que se resuelva el reclamo activo.',
   CONCAT('FUNDS_SUSPENDED:', @booking_tutor), NOW() - INTERVAL 40 HOUR, NOW() - INTERVAL 38 HOUR),

  -- 10. Fondos liberados tras resolver el reclamo
  (UNHEX(REPLACE(UUID(), '-', '')), @tutor_id, 'FUNDS_RELEASED', @booking_tutor,
   'Fondos liberados. Disputa resuelta',
   'El reclamo por la sesión del Martes 21 de octubre fue resuelto. Los fondos se procesarán en breve.',
   CONCAT('FUNDS_RELEASED:', @booking_tutor), NOW() - INTERVAL 30 HOUR, NOW() - INTERVAL 28 HOUR),

  -- 11. Actualizacion de validacion de materia (texto representativo)
  (UNHEX(REPLACE(UUID(), '-', '')), @tutor_id, 'SUBJECT_VALIDATION_UPDATED', @booking_tutor,
   'Validación de materia actualizada',
   'Tu solicitud de validación de Física fue aprobada.',
   CONCAT('SUBJECT_VALIDATION_UPDATED:', @booking_tutor), NOW() - INTERVAL 96 HOUR, NOW() - INTERVAL 90 HOUR),

  -- 12. Denuncia de material procesada (texto representativo)
  (UNHEX(REPLACE(UUID(), '-', '')), @tutor_id, 'MATERIAL_REPORT_PROCESSED', @booking_tutor,
   'Denuncia de material procesada',
   'La denuncia sobre tu material Apuntes de Física fue procesada.',
   CONCAT('MATERIAL_REPORT_PROCESSED:', @booking_tutor), NOW() - INTERVAL 80 HOUR, NOW() - INTERVAL 78 HOUR),

  -- 13. Validacion de tutor pendiente (admin, texto representativo)
  (UNHEX(REPLACE(UUID(), '-', '')), @tutor_id, 'ADMIN_VALIDATION_REQUEST_PENDING', @booking_tutor,
   'Validación de tutor pendiente',
   'Hay una nueva solicitud de validación de tutor pendiente de revisión.',
   CONCAT('ADMIN_VALIDATION_REQUEST_PENDING:', @booking_tutor), NOW() - INTERVAL 6 HOUR, NULL),

  -- 14. Nueva denuncia de material para admin (texto representativo)
  (UNHEX(REPLACE(UUID(), '-', '')), @tutor_id, 'ADMIN_MATERIAL_REPORT_RECEIVED', @booking_tutor,
   'Nueva denuncia de material',
   'Se recibió una nueva denuncia de material pendiente de revisión.',
   CONCAT('ADMIN_MATERIAL_REPORT_RECEIVED:', @booking_tutor), NOW() - INTERVAL 4 HOUR, NULL),

  -- 15. Reclamo de sesion iniciado (variante tutor: notifica al otro participante)
  (UNHEX(REPLACE(UUID(), '-', '')), @tutor_id, 'SESSION_CLAIM_UPDATE', @booking_tutor,
   'Nuevo reclamo de sesión',
   'Se inició un reclamo por tu sesión de Física.',
   CONCAT('SESSION_CLAIM_UPDATE:', @booking_tutor), NOW() - INTERVAL 26 HOUR, NULL),

  -- 16. Comentario nuevo en un reclamo
  (UNHEX(REPLACE(UUID(), '-', '')), @tutor_id, 'SESSION_CLAIM_COMMENT', @booking_tutor,
   'Nuevo comentario en un reclamo',
   'El tutor agregó un comentario al reclamo de la sesión de Física.',
   CONCAT('SESSION_CLAIM_COMMENT:', @booking_tutor), NOW() - INTERVAL 20 HOUR, NULL),

  -- 17. Denuncia de material recibida (variante tutor)
  (UNHEX(REPLACE(UUID(), '-', '')), @tutor_id, 'MATERIAL_REPORT_UPDATE', @booking_tutor,
   'Denuncia de material recibida',
   'Tu material Apuntes de Física recibió una denuncia.',
   CONCAT('MATERIAL_REPORT_UPDATE:', @booking_tutor), NOW() - INTERVAL 15 HOUR, NULL);

-- =============================================
-- Seccion 2: Alumno (test02.t02@gmail.com)
-- =============================================

INSERT IGNORE INTO user_notification
  (notification_id, recipient_user_id, notification_type, resource_id, title, message, dedupe_key, created_at, read_at)
VALUES
  -- 1. Reserva creada (variante alumno)
  (UNHEX(REPLACE(UUID(), '-', '')), @alumno_id, 'BOOKING_CONFIRMED', @booking_alumno,
   'Reserva confirmada',
   'Reservaste Física - Martes 21 de octubre a las 10:00.',
   CONCAT('BOOKING_CONFIRMED:', @booking_alumno), NOW() - INTERVAL 170 HOUR, NOW() - INTERVAL 160 HOUR),

  -- 2. Reserva cancelada por el tutor (con reembolso)
  (UNHEX(REPLACE(UUID(), '-', '')), @alumno_id, 'BOOKING_CANCELLED', @booking_alumno,
   'Clase cancelada por el tutor',
   'Julián López Salvucci canceló la clase del Miércoles 22 de octubre. El alumno recibió el reembolso por política de cancelación.',
   CONCAT('BOOKING_CANCELLED:', @booking_alumno), NOW() - INTERVAL 150 HOUR, NOW() - INTERVAL 140 HOUR),

  -- 3. Recordatorio de token en clase en curso (variante alumno)
  (UNHEX(REPLACE(UUID(), '-', '')), @alumno_id, 'SESSION_CONFIRMATION_REMINDER', @booking_alumno,
   'Recordatorio de token. Clase en curso',
   'Tu clase de Física con Julián López Salvucci está en curso. Entregá el código al tutor para confirmar la sesión.',
   CONCAT('SESSION_CONFIRMATION_REMINDER:', @booking_alumno), NOW() - INTERVAL 130 HOUR, NOW() - INTERVAL 128 HOUR),

  -- 4. Sesion confirmada con codigo
  (UNHEX(REPLACE(UUID(), '-', '')), @alumno_id, 'SESSION_CONFIRMED', @booking_alumno,
   'Sesión confirmada',
   'La sesión de Física con Julián López Salvucci fue confirmada.',
   CONCAT('SESSION_CONFIRMED:', @booking_alumno), NOW() - INTERVAL 125 HOUR, NOW() - INTERVAL 124 HOUR),

  -- 5. Token vencido, sesion no confirmada (variante alumno)
  (UNHEX(REPLACE(UUID(), '-', '')), @alumno_id, 'SESSION_NOT_CONFIRMED', @booking_alumno,
   'Token vencido. Sesión no confirmada',
   'El tiempo para ingresar el código de la sesión con Julián López Salvucci venció. La reserva pasó al estado "No confirmada".',
   CONCAT('SESSION_NOT_CONFIRMED:', @booking_alumno), NOW() - INTERVAL 110 HOUR, NOW() - INTERVAL 108 HOUR),

  -- 6. Sesion completada
  (UNHEX(REPLACE(UUID(), '-', '')), @alumno_id, 'SESSION_COMPLETED', @booking_alumno,
   'Tu sesión de Física finalizó',
   'Si hubo algún inconveniente con la asistencia, tenés 24 horas para reclamar.',
   CONCAT('SESSION_COMPLETED:', @booking_alumno), NOW() - INTERVAL 70 HOUR, NOW() - INTERVAL 68 HOUR),

  -- 7. Habilitada calificacion (variante alumno)
  (UNHEX(REPLACE(UUID(), '-', '')), @alumno_id, 'RATING_ENABLED', @booking_alumno,
   'Calificá tu sesión de Física',
   'Contanos cómo fue tu experiencia con Julián López Salvucci. Tenés 24 horas para calificar.',
   CONCAT('RATING_ENABLED:', @booking_alumno), NOW() - INTERVAL 69 HOUR, NOW() - INTERVAL 67 HOUR),

  -- 8. Pago del alumno con fondos retenidos (variante alumno)
  (UNHEX(REPLACE(UUID(), '-', '')), @alumno_id, 'FUNDS_TRANSFERRED', @booking_alumno,
   'Pago realizado. Reserva realizada',
   'Pagaste $10.300 por la sesión de Física con Julián López Salvucci. Los fondos quedarán retenidos hasta la finalización de la sesión.',
   CONCAT('FUNDS_TRANSFERRED:Fondos retenidos para reserva:', @booking_alumno),
   NOW() - INTERVAL 120 HOUR, NOW() - INTERVAL 118 HOUR),

  -- 9. Fondos suspendidos por reclamo activo (variante alumno)
  (UNHEX(REPLACE(UUID(), '-', '')), @alumno_id, 'FUNDS_SUSPENDED', @booking_alumno,
   'Fondos retenidos. Disputa activa',
   'Los fondos de la sesión con Julián López Salvucci están retenidos hasta que se resuelva el reclamo activo.',
   CONCAT('FUNDS_SUSPENDED:', @booking_alumno), NOW() - INTERVAL 40 HOUR, NOW() - INTERVAL 38 HOUR),

  -- 10. Fondos liberados tras resolver el reclamo
  (UNHEX(REPLACE(UUID(), '-', '')), @alumno_id, 'FUNDS_RELEASED', @booking_alumno,
   'Fondos liberados. Disputa resuelta',
   'El reclamo por la sesión del Martes 21 de octubre fue resuelto. Los fondos se procesarán en breve.',
   CONCAT('FUNDS_RELEASED:', @booking_alumno), NOW() - INTERVAL 30 HOUR, NOW() - INTERVAL 28 HOUR),

  -- 11. Actualizacion de validacion de materia (texto representativo)
  (UNHEX(REPLACE(UUID(), '-', '')), @alumno_id, 'SUBJECT_VALIDATION_UPDATED', @booking_alumno,
   'Validación de materia actualizada',
   'El estado de la validación de la materia Física cambió.',
   CONCAT('SUBJECT_VALIDATION_UPDATED:', @booking_alumno), NOW() - INTERVAL 96 HOUR, NOW() - INTERVAL 90 HOUR),

  -- 12. Denuncia de material procesada (el alumno fue el denunciante)
  (UNHEX(REPLACE(UUID(), '-', '')), @alumno_id, 'MATERIAL_REPORT_PROCESSED', @booking_alumno,
   'Denuncia de material procesada',
   'La denuncia que hiciste sobre el material Apuntes de Física fue procesada.',
   CONCAT('MATERIAL_REPORT_PROCESSED:', @booking_alumno), NOW() - INTERVAL 80 HOUR, NOW() - INTERVAL 78 HOUR),

  -- 13. Validacion de tutor pendiente (admin, texto representativo)
  (UNHEX(REPLACE(UUID(), '-', '')), @alumno_id, 'ADMIN_VALIDATION_REQUEST_PENDING', @booking_alumno,
   'Validación de tutor pendiente',
   'Hay una nueva solicitud de validación de tutor pendiente de revisión.',
   CONCAT('ADMIN_VALIDATION_REQUEST_PENDING:', @booking_alumno), NOW() - INTERVAL 6 HOUR, NULL),

  -- 14. Nueva denuncia de material para admin (texto representativo)
  (UNHEX(REPLACE(UUID(), '-', '')), @alumno_id, 'ADMIN_MATERIAL_REPORT_RECEIVED', @booking_alumno,
   'Nueva denuncia de material',
   'Se recibió una nueva denuncia de material pendiente de revisión.',
   CONCAT('ADMIN_MATERIAL_REPORT_RECEIVED:', @booking_alumno), NOW() - INTERVAL 4 HOUR, NULL),

  -- 15. Actualizacion del reclamo que el alumno inicio
  (UNHEX(REPLACE(UUID(), '-', '')), @alumno_id, 'SESSION_CLAIM_UPDATE', @booking_alumno,
   'Actualización de reclamo',
   'El estado de tu reclamo por la sesión de Física cambió a En revisión.',
   CONCAT('SESSION_CLAIM_UPDATE:', @booking_alumno), NOW() - INTERVAL 26 HOUR, NULL),

  -- 16. Comentario del tutor en el reclamo
  (UNHEX(REPLACE(UUID(), '-', '')), @alumno_id, 'SESSION_CLAIM_COMMENT', @booking_alumno,
   'Nuevo comentario en un reclamo',
   'El tutor agregó un comentario al reclamo de la sesión de Física.',
   CONCAT('SESSION_CLAIM_COMMENT:', @booking_alumno), NOW() - INTERVAL 20 HOUR, NULL),

  -- 17. Resultado de la denuncia de material que el alumno hizo
  (UNHEX(REPLACE(UUID(), '-', '')), @alumno_id, 'MATERIAL_REPORT_UPDATE', @booking_alumno,
   'Resultado de tu denuncia',
   'La revisión desestimó la denuncia.',
   CONCAT('MATERIAL_REPORT_UPDATE:', @booking_alumno), NOW() - INTERVAL 15 HOUR, NULL);