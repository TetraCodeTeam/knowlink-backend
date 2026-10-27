-- =============================================
-- Demo data only: replace the .test emails below with real users from your local database if needed.
-- KnowLink - Seed: clase terminada reclamable (US-51)
-- Alumno: alumno.demo@knowlink.test
-- Tutor : tutor.demo@knowlink.test
-- La clase ya finalizo por horario hoy y nunca se
-- confirmo con codigo, por lo que el alumno puede
-- reclamarla durante 24 h desde su hora de fin.
-- =============================================

SET @alumno_id = (SELECT user_id FROM users WHERE email = 'alumno.demo@knowlink.test');
SET @tutor_id = (SELECT user_id FROM users WHERE email = 'tutor.demo@knowlink.test');
SET @tutor_profile_id = (SELECT tutor_profile_id FROM tutor_profile WHERE user_id = @tutor_id);
-- materia ofertada del tutor (la de menor precio: Fisica, 10000/h)
SET @tutor_subject_id = (SELECT tutor_subject_id
                         FROM tutor_subject
                         WHERE tutor_profile_id = @tutor_profile_id
                         ORDER BY price_per_hour ASC
                         LIMIT 1);

SET @sesion_fecha = CURDATE();
SET @hora_inicio = '10:00:00';
SET @hora_fin = '11:00:00';

SET @ya_existe = (SELECT COUNT(*)
                  FROM booking
                  WHERE student_id = @alumno_id
                    AND tutor_id = @tutor_id
                    AND session_date = @sesion_fecha
                    AND start_time = @hora_inicio);

SET @slot_id = UNHEX(REPLACE(UUID(), '-', ''));
SET @booking_id = UNHEX(REPLACE(UUID(), '-', ''));
SET @transfer_id = UNHEX(REPLACE(UUID(), '-', ''));

-- Horario del tutor para esa clase
INSERT INTO time_slot (time_slot_id, date, start_time, end_time, status, tutor_profile_id, availability_block_id)
SELECT @slot_id, @sesion_fecha, '09:00:00', '12:00:00', 'AVAILABLE', @tutor_profile_id, NULL
WHERE @ya_existe = 0;

-- Clase terminada por horario, sin confirmar con codigo
INSERT INTO booking (booking_id, created_at, amount, session_date, start_time, end_time, topic, modality,
                     virtual_session_link, confirmation_token, confirmation_token_expiration,
                     confirmation_token_attempts, confirmed_at, last_confirmation_reminder_at,
                     completion_notification_sent, booking_status, time_slot_id, tutor_subject_id,
                     student_id, tutor_id)
SELECT @booking_id, NOW(6), 10300.00, @sesion_fecha, @hora_inicio, @hora_fin,
       'Resolucion de ejercicios de Fisica', 'VIRTUAL',
       'https://meet.jit.si/knowlink-fisica-demo', NULL, NULL, 0, NULL, NULL, b'0',
       'BOOKED', @slot_id, @tutor_subject_id, @alumno_id, @tutor_id
WHERE @ya_existe = 0;

-- Fondos retenidos de la reserva (ledger interno US-15): el reclamo los pasa a SUSPENDED_BY_CLAIM
INSERT INTO funds_transfer (funds_transfer_id, booking_id, original_amount, system_retention_percentage,
                            transferred_amount, recipient, concept, funds_status, processed_at,
                            blocking_claim_id)
SELECT @transfer_id, b.booking_id, b.amount, 3.00, 0.00, 'TUTOR', 'Fondos retenidos para reserva',
       'HELD', NULL, NULL
FROM booking b
WHERE b.booking_id = @booking_id
  AND @ya_existe = 0
  AND NOT EXISTS (SELECT 1 FROM funds_transfer ft WHERE ft.booking_id = b.booking_id);