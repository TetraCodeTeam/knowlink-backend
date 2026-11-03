-- =============================================================================
-- KnowLink - Seed: datos para probar la comunidad academica (US-54 a US-59)
--
-- Uso (MySQL 8, base de desarrollo; las tablas ya deben existir: arranca el backend una vez):
--   mysql --default-character-set=utf8mb4 -u <usuario> -p knowlink_db < docs/seeds/seed-community-demo.sql
--
-- Que crea (todo con emails @comunidad.knowlink.test, contrasena: Password1!):
--   * 8 alumnos y 12 tutores con perfil, carrera, materias ofrecidas y calificacion.
--       Alumno principal : matias.velasquez@comunidad.knowlink.test  (el del prototipo)
--       Tutor con podio   : sofia.albarracin@comunidad.knowlink.test  (puesto 1)
--       Tutor fuera del top 10: bruno.castro@...  (puesto 11, le faltan posiciones)
--       Tutor sin puntaje : elena.quiroga@...  (elegible, sin posicion)
--   * 37 publicaciones vivas + 1 eliminada, repartidas entre hoy, esta semana, este mes, el mes
--     anterior y mas atras (para US-59), con dudas y aportes, abiertas y resueltas, de alumnos y tutores,
--     de varias carreras y materias basicas, con editadas y con adjuntos.
--   * Respuestas de alumnos y tutores (con y sin tarjeta de reserva, editadas, una eliminada),
--     marcas "Esto me sirvio" y respuesta destacada en las publicaciones resueltas.
--   * Notificaciones COMMUNITY_REPLY para los autores de cada publicacion respondida.
--   * Actividad del MES ANTERIOR suficiente para el ranking (US-58): al consultar /podium por primera vez
--     el backend genera el ranking del mes en curso (11 tutores con puntaje, del 1 al 11).
--
-- Datos para ver TODAS las secciones del menu (navbar) de alumno y tutor (seccion 7 en adelante):
--   * Agenda ("Mi agenda"): bloques de disponibilidad de lunes a viernes durante 12 semanas para Sofia, Julian,
--       Luciana, Diego, Mateo y Florencia, mas el historico de horarios ofrecidos de las ultimas 12 semanas.
--   * Clases ("Mis clases"): ~80 reservas en todos los estados (reservadas, en curso, completadas, no confirmadas,
--       canceladas, vencidas, tutor ausente, sesion no realizada). Matias tiene al menos una de cada estado y
--       Sofia ~30 sesiones en 3 meses para que el panel de estadisticas tenga graficos.
--   * Estadisticas: calificaciones visibles (5, 4 y 3 estrellas), fondos retenidos / liberados / devueltos / suspendidos.
--   * Reclamos ("Mis reclamos" / "Soporte y solicitudes"): reclamos Abierto, En revision y Resuelto de Matias y de
--       Sofia (como reclamante y como reclamada), con comentarios.
--   * Material denunciado: materiales de varios tutores con denuncias Pendiente, En revision (con descargo del
--       tutor), Resuelto-Procedente (material dado de baja) y Resuelto-No procedente.
--   * Notificaciones de todos los tipos para Matias, Sofia, Julian y Luciana (leidas y sin leer).
--   Inicio de sesion principal: Matias (alumno) y Sofia (tutora), contrasena Password1!. Nota: Matias tiene una
--   clase sin calificar y Sofia otra, para probar el pop-up de calificacion.
--
-- Es re-ejecutable: borra y vuelve a crear solo lo que pertenece a los usuarios @comunidad.knowlink.test
-- (sus publicaciones, las respuestas y marcas que recibieron, y las notificaciones asociadas) y el ranking
-- del mes en curso, para que se regenere. No toca nada de otros usuarios.
--
-- Notas:
--   * Las fechas son relativas al momento de ejecucion y estan en hora de Cordoba (UTC-3). Como el JDBC URL del
--     backend usa serverTimezone=UTC, se guardan corridas segun la diferencia entre la hora local del servidor
--     MySQL y UTC (ver @shift abajo): asume que MySQL y el backend corren en la misma maquina.
--   * Los periodos del filtro son ventanas moviles (hoy, ultimos 7 dias, ultimos 30 dias): el seed deja
--     publicaciones de hoy, de hace 1 a 4 dias y de hace 9 y 14 dias.
--   * Los adjuntos tienen metadatos pero no existen en el bucket: se ven con nombre y tamano, sin enlace de
--     descarga (el backend lo tolera). Para probar subida y descarga reales, adjunta archivos desde la UI.
-- =============================================================================

SET NAMES utf8mb4;

-- ---------------------------------------------------------------------------
-- 0. Tiempo de referencia
-- ---------------------------------------------------------------------------
-- La app trabaja en hora de Cordoba (UTC-3) pero el JDBC URL declara serverTimezone=UTC: el driver convierte las
-- fechas del backend a UTC al guardarlas (y de vuelta al leerlas). Para que la app vea las horas correctas, las
-- fechas se calculan en hora de Cordoba y se guardan corridas @shift horas (3 en una maquina argentina).
SET @shift = TIMESTAMPDIFF(HOUR, NOW(), UTC_TIMESTAMP());
SET @now = UTC_TIMESTAMP() - INTERVAL 3 HOUR;                      -- hora de Cordoba, la que usa la app
SET @now_db = @now + INTERVAL @shift HOUR;                         -- la misma, como queda guardada
SET @today = DATE(@now);
SET @month_start = DATE_FORMAT(@today, '%Y-%m-01');                -- primer dia del mes
SET @pwd = '$2a$10$mIlh0.lvtcVeoAM/TX4nQeeL7NvVzaEMmdy/CtdszS8bMvH.7n7V6';  -- Password1!

-- hoy
SET @t0a = GREATEST(@now - INTERVAL 25 MINUTE, TIMESTAMP(@today));
SET @t0b = GREATEST(@now - INTERVAL 3 HOUR, TIMESTAMP(@today));
SET @t0c = GREATEST(@now - INTERVAL 9 HOUR, TIMESTAMP(@today));
-- ultimos 7 dias
SET @w1 = @now - INTERVAL 1 DAY;
SET @w2 = @now - INTERVAL 2 DAY;
SET @w3 = @now - INTERVAL 4 DAY;
-- ultimos 30 dias (mas de una semana atras)
SET @m1 = @now - INTERVAL 9 DAY;
SET @m2 = @now - INTERVAL 14 DAY;
-- mes anterior: siempre entre 8 y 30 dias antes del primer dia del mes, a las 10:00
SET @pm = TIMESTAMP(@month_start) + INTERVAL 10 HOUR;

-- ---------------------------------------------------------------------------
-- 1. Limpieza de una corrida anterior
-- ---------------------------------------------------------------------------
DELETE n FROM user_notification n
  JOIN community_post p ON n.resource_id = p.post_id
  JOIN users u ON u.user_id = p.author_id
 WHERE n.notification_type = 'COMMUNITY_REPLY' AND u.email LIKE '%@comunidad.knowlink.test';

DELETE h FROM community_reply_helpful h
  JOIN community_reply r ON r.reply_id = h.reply_id
  JOIN community_post p ON p.post_id = r.post_id
  JOIN users u ON u.user_id = p.author_id
 WHERE u.email LIKE '%@comunidad.knowlink.test';

DELETE r FROM community_reply r
  JOIN community_post p ON p.post_id = r.post_id
  JOIN users u ON u.user_id = p.author_id
 WHERE u.email LIKE '%@comunidad.knowlink.test';

DELETE p FROM community_post p
  JOIN users u ON u.user_id = p.author_id
 WHERE u.email LIKE '%@comunidad.knowlink.test';

-- Se regenera solo al consultar /podium, a partir de la actividad del mes anterior.
DELETE FROM community_tutor_ranking WHERE period_start = @month_start;

-- Reservas, fondos, calificaciones, reclamos, agenda, materiales y notificaciones de las secciones del menu.
-- Se borran las reservas de cualquier usuario @comunidad (incluidas las que hayas creado probando la UI), el
-- horario de sus tutores y SOLO los materiales sembrados por este script (storage_path 'materials/seed-demo/').
DROP TEMPORARY TABLE IF EXISTS old_booking;
CREATE TEMPORARY TABLE old_booking AS
SELECT b.booking_id
  FROM booking b
  JOIN users s ON s.user_id = b.student_id
  JOIN users t ON t.user_id = b.tutor_id
 WHERE s.email LIKE '%@comunidad.knowlink.test' OR t.email LIKE '%@comunidad.knowlink.test';

DROP TEMPORARY TABLE IF EXISTS old_tutor_profile;
CREATE TEMPORARY TABLE old_tutor_profile AS
SELECT tp.tutor_profile_id
  FROM tutor_profile tp JOIN users u ON u.user_id = tp.user_id
 WHERE u.email LIKE '%@comunidad.knowlink.test';

DROP TEMPORARY TABLE IF EXISTS old_material;
CREATE TEMPORARY TABLE old_material AS
SELECT academic_material_id FROM academic_material WHERE storage_path LIKE 'materials/seed-demo/%';

DELETE FROM user_notification WHERE dedupe_key LIKE 'seed-demo:%';

DELETE FROM session_claim_comment
 WHERE claim_id IN (SELECT claim_id FROM session_claim WHERE booking_id IN (SELECT booking_id FROM old_booking));
DELETE FROM claim_attachment
 WHERE claim_id IN (SELECT claim_id FROM session_claim WHERE booking_id IN (SELECT booking_id FROM old_booking));
DELETE FROM session_claim WHERE booking_id IN (SELECT booking_id FROM old_booking);
DELETE FROM funds_transfer WHERE booking_id IN (SELECT booking_id FROM old_booking);
DELETE FROM rating WHERE booking_id IN (SELECT booking_id FROM old_booking);
DELETE FROM booking_cancellation WHERE booking_id IN (SELECT booking_id FROM old_booking);
DELETE FROM booking WHERE booking_id IN (SELECT booking_id FROM old_booking);

DELETE FROM booking_hold
 WHERE time_slot_id IN (SELECT time_slot_id FROM time_slot
                         WHERE tutor_profile_id IN (SELECT tutor_profile_id FROM old_tutor_profile));
DELETE FROM time_slot WHERE tutor_profile_id IN (SELECT tutor_profile_id FROM old_tutor_profile);
DELETE FROM availability_block WHERE tutor_profile_id IN (SELECT tutor_profile_id FROM old_tutor_profile);
DELETE FROM availability_week_customization WHERE tutor_profile_id IN (SELECT tutor_profile_id FROM old_tutor_profile);
DELETE FROM offered_slot_history WHERE tutor_profile_id IN (SELECT tutor_profile_id FROM old_tutor_profile);

DELETE FROM material_defense_attachment
 WHERE academic_material_defense_id IN (
         SELECT academic_material_defense_id FROM academic_material_defense
          WHERE academic_material_id IN (SELECT academic_material_id FROM old_material));
DELETE FROM academic_material_defense WHERE academic_material_id IN (SELECT academic_material_id FROM old_material);
DELETE FROM academic_material_report WHERE academic_material_id IN (SELECT academic_material_id FROM old_material);
DELETE FROM material_report_case WHERE academic_material_id IN (SELECT academic_material_id FROM old_material);
DELETE FROM academic_material WHERE academic_material_id IN (SELECT academic_material_id FROM old_material);

-- ---------------------------------------------------------------------------
-- 2. Usuarios, perfiles y materias ofrecidas
-- ---------------------------------------------------------------------------
DROP TEMPORARY TABLE IF EXISTS tmp_user;
CREATE TEMPORARY TABLE tmp_user (
  email VARCHAR(120) PRIMARY KEY,
  full_name VARCHAR(120),
  role VARCHAR(10),
  career VARCHAR(120),
  dni VARCHAR(12),
  verified TINYINT DEFAULT 0,
  rating DOUBLE NULL,
  bio VARCHAR(300) NULL
);

INSERT INTO tmp_user (email, full_name, role, career, dni, verified, rating, bio) VALUES
  -- Alumnos
  ('matias.velasquez@comunidad.knowlink.test', 'Matías Velasquez',  'STUDENT', 'Ingeniería en Sistemas',    '91000001', 0, NULL, NULL),
  ('lucas.benitez@comunidad.knowlink.test',    'Lucas Benítez',     'STUDENT', 'Ingeniería en Sistemas',    '91000002', 0, NULL, NULL),
  ('martina.rossi@comunidad.knowlink.test',    'Martina Rossi',     'STUDENT', 'Ingeniería Química',        '91000003', 0, NULL, NULL),
  ('camila.torres@comunidad.knowlink.test',    'Camila Torres',     'STUDENT', 'Ingeniería Civil',          '91000004', 0, NULL, NULL),
  ('nicolas.ibarra@comunidad.knowlink.test',   'Nicolás Ibarra',    'STUDENT', 'Licenciatura en Administración', '91000005', 0, NULL, NULL),
  ('valentina.cruz@comunidad.knowlink.test',   'Valentina Cruz',    'STUDENT', 'Ingeniería en Sistemas',    '91000006', 0, NULL, NULL),
  ('joaquin.paz@comunidad.knowlink.test',      'Joaquín Paz',       'STUDENT', 'Contador Público',          '91000007', 0, NULL, NULL),
  ('agustina.luna@comunidad.knowlink.test',    'Agustina Luna',     'STUDENT', 'Ingeniería en Sistemas',    '91000008', 0, NULL, NULL),
  -- Tutores
  ('sofia.albarracin@comunidad.knowlink.test', 'Lic. Sofía Albarracín', 'TUTOR', 'Ingeniería en Sistemas', '92000001', 1, 4.9, 'Docente de algoritmos y estructuras de datos'),
  ('julian.mendez@comunidad.knowlink.test',    'Prof. Julián Méndez',   'TUTOR', 'Matemática',             '92000002', 1, 4.9, 'Profesor de análisis matemático y álgebra'),
  ('mateo.rivas@comunidad.knowlink.test',      'Mateo Rivas',           'TUTOR', 'Ingeniería Química',     '92000003', 1, 5.0, 'Ingeniero químico, especialista en termodinámica'),
  ('luciana.gomez@comunidad.knowlink.test',    'Luciana Gómez',         'TUTOR', 'Ingeniería en Sistemas', '92000004', 1, 4.9, 'Desarrolladora y tutora de programación'),
  ('ricardo.salas@comunidad.knowlink.test',    'Ricardo Salas',         'TUTOR', 'Ingeniería Civil',       '92000005', 0, 4.7, 'Ingeniero civil, estructuras y materiales'),
  ('paula.ferreyra@comunidad.knowlink.test',   'Paula Ferreyra',        'TUTOR', 'Licenciatura en Administración', '92000006', 0, 4.6, 'Licenciada en administración'),
  ('diego.montoya@comunidad.knowlink.test',    'Diego Montoya',         'TUTOR', 'Ingeniería en Sistemas', '92000007', 1, 4.8, 'Bases de datos y redes'),
  ('florencia.diaz@comunidad.knowlink.test',   'Florencia Díaz',        'TUTOR', 'Ingeniería en Sistemas', '92000008', 1, 4.8, 'Inteligencia artificial y machine learning'),
  ('tomas.herrera@comunidad.knowlink.test',    'Tomás Herrera',         'TUTOR', 'Contador Público',       '92000009', 0, 4.5, 'Contador público, costos y auditoría'),
  ('carolina.vega@comunidad.knowlink.test',    'Carolina Vega',         'TUTOR', 'Matemática',             '92000010', 0, 4.7, 'Licenciada en matemática'),
  ('bruno.castro@comunidad.knowlink.test',     'Bruno Castro',          'TUTOR', 'Ingeniería en Sistemas', '92000011', 0, 4.4, 'Sistemas operativos y arquitectura'),
  ('elena.quiroga@comunidad.knowlink.test',    'Elena Quiroga',         'TUTOR', 'Ingeniería Química',     '92000012', 0, NULL, 'Tutora recién llegada, todavía sin actividad');

INSERT IGNORE INTO users (user_id, full_name, email, password, dni, phone_number, role, account_status, created_at)
SELECT UUID_TO_BIN(UUID()), t.full_name, t.email, @pwd, t.dni, NULL, t.role, 'ACTIVE', NOW()
FROM tmp_user t;

INSERT INTO student_profile (student_profile_id, user_id, career_id, created_at)
SELECT UUID_TO_BIN(UUID()), u.user_id, c.career_id, NOW()
FROM tmp_user t
JOIN users u ON u.email = t.email
JOIN career c ON c.name = t.career
WHERE t.role = 'STUDENT'
  AND NOT EXISTS (SELECT 1 FROM student_profile sp WHERE sp.user_id = u.user_id);

INSERT INTO tutor_profile (tutor_profile_id, user_id, career_id, biography, mercado_pago_linked, verified, average_rating, created_at)
SELECT UUID_TO_BIN(UUID()), u.user_id, c.career_id, t.bio, b'0', t.verified, t.rating, NOW()
FROM tmp_user t
JOIN users u ON u.email = t.email
JOIN career c ON c.name = t.career
WHERE t.role = 'TUTOR'
  AND NOT EXISTS (SELECT 1 FROM tutor_profile tp WHERE tp.user_id = u.user_id);

DROP TEMPORARY TABLE IF EXISTS tmp_tutor_subject;
CREATE TEMPORARY TABLE tmp_tutor_subject (email VARCHAR(120), subject VARCHAR(120), price DECIMAL(10,2));

INSERT INTO tmp_tutor_subject (email, subject, price) VALUES
  ('sofia.albarracin@comunidad.knowlink.test', 'Algoritmos', 3500), ('sofia.albarracin@comunidad.knowlink.test', 'Estructuras de Datos', 3500),
  ('sofia.albarracin@comunidad.knowlink.test', 'Programación', 3200),
  ('julian.mendez@comunidad.knowlink.test', 'Análisis Matemático', 3000), ('julian.mendez@comunidad.knowlink.test', 'Álgebra', 3000),
  ('julian.mendez@comunidad.knowlink.test', 'Física', 3000),
  ('mateo.rivas@comunidad.knowlink.test', 'Fisicoquímica', 3300), ('mateo.rivas@comunidad.knowlink.test', 'Química', 3000),
  ('mateo.rivas@comunidad.knowlink.test', 'Balances de Masa y Energía', 3400),
  ('luciana.gomez@comunidad.knowlink.test', 'Programación', 3200), ('luciana.gomez@comunidad.knowlink.test', 'Algoritmos', 3300),
  ('ricardo.salas@comunidad.knowlink.test', 'Estática', 2800), ('ricardo.salas@comunidad.knowlink.test', 'Resistencia de Materiales', 2900),
  ('ricardo.salas@comunidad.knowlink.test', 'Hormigón Armado', 3000),
  ('paula.ferreyra@comunidad.knowlink.test', 'Contabilidad', 2600), ('paula.ferreyra@comunidad.knowlink.test', 'Economía', 2600),
  ('diego.montoya@comunidad.knowlink.test', 'Base de Datos', 3100), ('diego.montoya@comunidad.knowlink.test', 'Redes', 3100),
  ('florencia.diaz@comunidad.knowlink.test', 'Machine Learning', 3600), ('florencia.diaz@comunidad.knowlink.test', 'Inteligencia Artificial', 3600),
  ('tomas.herrera@comunidad.knowlink.test', 'Costos', 2500), ('tomas.herrera@comunidad.knowlink.test', 'Auditoría', 2600),
  ('carolina.vega@comunidad.knowlink.test', 'Álgebra', 2900), ('carolina.vega@comunidad.knowlink.test', 'Análisis Matemático', 2900),
  ('bruno.castro@comunidad.knowlink.test', 'Sistemas Operativos', 2800), ('bruno.castro@comunidad.knowlink.test', 'Arquitectura de Computadoras', 2800),
  ('elena.quiroga@comunidad.knowlink.test', 'Ciencia de los Materiales', 2700);

INSERT INTO tutor_subject (tutor_subject_id, tutor_profile_id, subject_id, description, price_per_hour,
                           compensation_type, tutor_subject_status, modality)
SELECT UUID_TO_BIN(UUID()), tp.tutor_profile_id, s.subject_id, CONCAT('Clases de ', s.name), ts.price,
       'PAID', 'ACTIVE', 'BOTH'
FROM tmp_tutor_subject ts
JOIN users u ON u.email = ts.email
JOIN tutor_profile tp ON tp.user_id = u.user_id
JOIN subjects s ON s.name = ts.subject
WHERE NOT EXISTS (SELECT 1 FROM tutor_subject x
                  WHERE x.tutor_profile_id = tp.tutor_profile_id AND x.subject_id = s.subject_id);

-- ---------------------------------------------------------------------------
-- 3. Publicaciones
--    H = pedido de ayuda (duda), S = contenido compartido (aporte)
-- ---------------------------------------------------------------------------
DROP TEMPORARY TABLE IF EXISTS tmp_post;
CREATE TEMPORARY TABLE tmp_post (
  k VARCHAR(4) PRIMARY KEY,
  author VARCHAR(120),
  ptype CHAR(1),
  subject VARCHAR(120),
  title VARCHAR(150),
  content TEXT,
  created_at DATETIME,
  pstatus VARCHAR(20) DEFAULT 'OPEN',
  resolved_after INT NULL,
  edited TINYINT DEFAULT 0,
  deleted TINYINT DEFAULT 0,
  att_name VARCHAR(200) NULL,
  att_type VARCHAR(100) NULL,
  att_size BIGINT NULL,
  post_id BINARY(16) NULL
);

INSERT INTO tmp_post (k, author, ptype, subject, title, content, created_at) VALUES
-- ===== HOY =====
('P01', 'lucas.benitez@comunidad.knowlink.test', 'H', 'Álgebra',
 '¿Cómo diagonalizar una matriz con autovalores repetidos cuando el subespacio no coincide con la multiplicidad?',
 'Estoy trabado con el ejercicio 4b de la guía 3. Obtengo λ = 2 con multiplicidad algebraica 2 pero solo un autovector linealmente independiente. ¿Eso significa directamente que no es diagonalizable o calculé mal el kernel de (A - 2I)?', @t0a),
('P02', 'sofia.albarracin@comunidad.knowlink.test', 'S', 'Algoritmos',
 'Guía rápida en 3 pasos para no confundir Big-O, Big-Omega y Big-Theta',
 CONCAT('Un error súper frecuente que veo en las tutorías antes de los parciales es usar O() para cotas ajustadas. Les dejo una regla mnemotécnica clara:', CHAR(10), CHAR(10),
        '1. **Big-O** es el techo: el peor caso asintótico.', CHAR(10),
        '2. **Big-Omega** es el piso: el mejor caso asintótico.', CHAR(10),
        '3. **Big-Theta** es cuando techo y piso coinciden: la cota ajustada.', CHAR(10), CHAR(10),
        'Si podés demostrar las dos cotas, decí Theta y listo. Adjunto un cheatsheet de una página con ejemplos de cada caso.'), @t0b),
('P03', 'martina.rossi@comunidad.knowlink.test', 'H', 'Análisis Matemático',
 'Duda con el cambio de variables a coordenadas cilíndricas en una integral triple',
 'Por qué en este cono $z = sqrt(x^2 + y^2)$ el jacobiano se incluye siempre antes de integrar dz dr dθ? ¡Ya me quedó claro gracias a la explicación de abajo!', @t0c),
('P04', 'matias.velasquez@comunidad.knowlink.test', 'H', 'Programación',
 '¿Conviene usar recursión o iteración para recorrer un árbol binario en Java?',
 'Estoy armando el recorrido in-order de un árbol binario de búsqueda para el TP. Con recursión queda muy corto pero me da miedo el stack overflow en árboles degenerados. ¿En qué casos conviene pasar a una versión iterativa con pila explícita?', @t0b),
('P05', 'matias.velasquez@comunidad.knowlink.test', 'H', 'Base de Datos',
 'No termino de entender la diferencia entre INNER JOIN y LEFT JOIN',
 'Tengo las tablas alumnos y cursos y quiero listar todos los alumnos, incluso los que no están inscriptos en ningún curso. ¿Cuál de los dos JOIN tengo que usar y por qué?', @t0c),
-- ===== ESTA SEMANA =====
('P06', 'camila.torres@comunidad.knowlink.test', 'H', 'Estática',
 'Diagrama de cuerpo libre de una viga con carga distribuida triangular',
 'No sé cómo reemplazar la carga triangular por una fuerza puntual equivalente. ¿A qué distancia del apoyo actúa la resultante y cómo calculo las reacciones?', @w1),
('P07', 'julian.mendez@comunidad.knowlink.test', 'S', 'Análisis Matemático',
 'Cómo memorizar las derivadas e integrales inmediatas sin sufrir',
 CONCAT('Armé una tabla con las 12 derivadas que aparecen en el 90% de los parciales y la forma de deducir las integrales inmediatas a partir de ellas. ',
        'La idea es no memorizar dos tablas sino una sola y recorrerla al revés.', CHAR(10), CHAR(10),
        'Por ejemplo: si $d/dx sen(x) = cos(x)$, entonces la integral de cos(x) es sen(x) + C. Con **cinco patrones** se resuelve casi todo.'), @w1),
('P08', 'nicolas.ibarra@comunidad.knowlink.test', 'H', 'Contabilidad',
 '¿Cómo se registra una amortización en el libro diario?',
 'Tengo que registrar la amortización anual de un rodado en el libro diario y no tengo claro qué cuenta va al debe y cuál al haber. ¿Alguien me da una mano con el asiento?', @w2),
('P09', 'valentina.cruz@comunidad.knowlink.test', 'H', 'Estructuras de Datos',
 '¿Cuándo conviene una tabla hash en lugar de un árbol AVL?',
 'Para la cátedra tengo que justificar la elección de la estructura para un diccionario de 100.000 palabras. Sé que el hash es O(1) promedio y el AVL O(log n), pero ¿qué otros criterios debería considerar?', @w2),
('P10', 'mateo.rivas@comunidad.knowlink.test', 'S', 'Fisicoquímica',
 'Resumen de termodinámica: primer y segundo principio con ejemplos',
 CONCAT('Les comparto el resumen que uso en mis clases. Incluye las ecuaciones clave, los signos convencionales para calor y trabajo y tres ejercicios resueltos paso a paso.', CHAR(10), CHAR(10),
        'Recordá que el **primer principio** habla de conservación de la energía y el **segundo** de la dirección en que ocurren los procesos.'), @w3),
('P11', 'joaquin.paz@comunidad.knowlink.test', 'H', 'Auditoría',
 '¿Qué papeles de trabajo son obligatorios en una auditoría de estados contables?',
 'Estoy preparando el trabajo práctico de auditoría y necesito saber cuáles son los papeles de trabajo mínimos que exige la norma y cómo se referencian entre sí.', @w3),
('P12', 'agustina.luna@comunidad.knowlink.test', 'H', 'Redes',
 '¿Cuál es la diferencia real entre TCP y UDP en una aplicación?',
 'Sé la teoría (orientado a conexión vs. no orientado) pero no tengo claro cuándo elegir uno u otro en un proyecto real, por ejemplo un chat o un juego online.', @w3),
('P13', 'matias.velasquez@comunidad.knowlink.test', 'H', 'Física',
 'Duda con el rozamiento en un plano inclinado con dos bloques',
 'Dos bloques unidos por una cuerda sobre un plano inclinado con rozamiento. No logro plantear las ecuaciones de Newton para cada bloque. ¿Cómo defino el sentido de la fricción?', @w3),
('P14', 'tomas.herrera@comunidad.knowlink.test', 'S', 'Costos',
 'Costos fijos y variables: cómo no confundirte en el parcial',
 'Los tres errores más comunes que veo al clasificar costos: confundir fijo con indirecto, olvidar los costos semivariables y no separar el punto de equilibrio en unidades y en pesos. Te dejo cómo evitarlos con un ejemplo corto.', @w2),
-- ===== ESTE MES =====
('P15', 'lucas.benitez@comunidad.knowlink.test', 'H', 'Arquitectura de Computadoras',
 '¿Cómo funciona el pipeline de una CPU y qué es un hazard de datos?',
 'Estudiando el pipeline de 5 etapas me perdí con los hazards. ¿Podrían explicarme con un ejemplo de dos instrucciones consecutivas por qué se producen y cómo se resuelven (forwarding o stall)?', @m1),
('P16', 'florencia.diaz@comunidad.knowlink.test', 'S', 'Machine Learning',
 'Cómo evitar el sobreajuste: 5 técnicas con ejemplos en Python',
 CONCAT('Resumen de las técnicas que más uso: **regularización L1/L2**, **dropout**, **early stopping**, **validación cruzada** y **aumento de datos**.', CHAR(10), CHAR(10),
        '```', CHAR(10), 'from sklearn.linear_model import Ridge', CHAR(10), 'modelo = Ridge(alpha=1.0).fit(X_train, y_train)', CHAR(10), '```'), @m1),
('P17', 'martina.rossi@comunidad.knowlink.test', 'H', 'Balances de Masa y Energía',
 'Balance de masa en un reactor con reciclo: ¿cómo planteo las ecuaciones?',
 'Tengo un reactor con una corriente de reciclo y no sé qué volumen de control tomar. ¿Planteo el balance global y después el del reactor, o al revés?', @m2),
('P18', 'camila.torres@comunidad.knowlink.test', 'H', 'Hormigón Armado',
 '¿Cómo se calcula la armadura mínima de una columna?',
 'En el práctico me piden verificar la cuantía mínima de armadura longitudinal de una columna cuadrada de 30x30. ¿Qué porcentaje de la sección tengo que usar según el reglamento?', @m2),
('P19', 'valentina.cruz@comunidad.knowlink.test', 'S', 'Programación',
 'Mis apuntes de Java: colecciones y streams, con ejemplos',
 'Armé una planilla con las colecciones más usadas (List, Set, Map), su complejidad y los métodos de streams equivalentes a los bucles clásicos. Espero que les sirva para el final.', @m2),
-- ===== MES ANTERIOR (alimenta el ranking) =====
('P20', 'lucas.benitez@comunidad.knowlink.test', 'H', 'Algoritmos',
 '¿Cómo demuestro que un algoritmo de ordenamiento es correcto?',
 'Me piden demostrar la corrección de insertion sort. ¿Cómo planteo el invariante del bucle y qué tengo que probar en la inicialización, el mantenimiento y la terminación?', @pm - INTERVAL 20 DAY),
('P21', 'martina.rossi@comunidad.knowlink.test', 'H', 'Estructuras de Datos',
 'Diferencia entre pila y cola con un ejemplo cotidiano',
 'Necesito explicarle a mi grupo la diferencia entre pila y cola con ejemplos fuera de la programación. ¿Se me ocurre la pila de platos, pero no encuentro uno bueno para la cola?', @pm - INTERVAL 18 DAY),
('P22', 'camila.torres@comunidad.knowlink.test', 'H', 'Programación',
 'Error de punteros nulos al recorrer una lista enlazada',
 'Mi método para recorrer la lista enlazada lanza NullPointerException cuando la lista está vacía. Adjunto el código. ¿Dónde está el error?', @pm - INTERVAL 15 DAY),
('P23', 'nicolas.ibarra@comunidad.knowlink.test', 'H', 'Análisis Matemático',
 'Límites indeterminados: cuándo usar L''Hôpital y cuándo no',
 'En el parcial me quedó un límite que se resuelve de dos formas y no sé cuándo es válido aplicar L''Hôpital. ¿Hay alguna regla práctica?', @pm - INTERVAL 17 DAY),
('P24', 'valentina.cruz@comunidad.knowlink.test', 'H', 'Álgebra',
 'Cómo hallar la base de un subespacio a partir de un sistema de ecuaciones',
 'Tengo un subespacio definido por un sistema homogéneo y tengo que dar una base y su dimensión. ¿Resuelvo el sistema y de ahí saco los vectores?', @pm - INTERVAL 12 DAY),
('P25', 'joaquin.paz@comunidad.knowlink.test', 'H', 'Programación',
 'Diferencia entre una clase abstracta y una interfaz',
 'Siempre me confundo: ¿cuándo uso una clase abstracta y cuándo una interfaz en Java? ¿Se pueden usar las dos juntas?', @pm - INTERVAL 14 DAY),
('P26', 'agustina.luna@comunidad.knowlink.test', 'H', 'Fisicoquímica',
 'Cómo calcular el cambio de entalpía de una reacción',
 'Tengo las entalpías de formación de reactivos y productos. ¿Cómo uso la ley de Hess para obtener el cambio de entalpía de la reacción global?', @pm - INTERVAL 13 DAY),
('P27', 'lucas.benitez@comunidad.knowlink.test', 'H', 'Álgebra',
 'Autovalores complejos: ¿cómo interpreto la parte imaginaria?',
 'Al calcular los autovalores de una matriz de rotación me dan complejos. ¿Qué significa geométricamente la parte imaginaria y se puede igual diagonalizar?', @pm - INTERVAL 10 DAY),
('P28', 'matias.velasquez@comunidad.knowlink.test', 'H', 'Base de Datos',
 'Normalización: ¿cuándo aplicar la tercera forma normal?',
 'Mi esquema ya está en 2FN. ¿Cómo detecto una dependencia transitiva y cuándo conviene descomponer la tabla en lugar de dejarla desnormalizada por rendimiento?', @pm - INTERVAL 9 DAY),
('P29', 'camila.torres@comunidad.knowlink.test', 'H', 'Resistencia de Materiales',
 '¿Cómo calculo la tensión normal máxima en una viga simplemente apoyada?',
 'Tengo una viga con carga puntual en el centro y sección rectangular. ¿Qué fórmula uso para la tensión máxima y en qué fibra ocurre?', @pm - INTERVAL 11 DAY),
('P30', 'nicolas.ibarra@comunidad.knowlink.test', 'H', 'Economía',
 'Oferta y demanda: ¿qué desplaza la curva y qué mueve a lo largo de ella?',
 'No logro distinguir entre un desplazamiento de la curva y un movimiento a lo largo de la curva. ¿Podrían darme un ejemplo de cada uno?', @pm - INTERVAL 16 DAY),
('P31', 'joaquin.paz@comunidad.knowlink.test', 'H', 'Costos',
 'Cómo calcular el punto de equilibrio con varios productos',
 'Con un solo producto el punto de equilibrio es directo, pero con tres productos y costos fijos compartidos no sé cómo ponderar el margen de contribución.', @pm - INTERVAL 19 DAY),
('P32', 'valentina.cruz@comunidad.knowlink.test', 'H', 'Machine Learning',
 'Diferencia entre aprendizaje supervisado y no supervisado',
 'Entiendo que uno usa etiquetas y el otro no, pero ¿en qué tipo de problema reales conviene cada uno y cómo evalúo el segundo sin etiquetas?', @pm - INTERVAL 9 DAY),
('P33', 'agustina.luna@comunidad.knowlink.test', 'H', 'Sistemas Operativos',
 '¿Qué es un deadlock y cómo se previene?',
 'En la cátedra vimos las cuatro condiciones de Coffman pero no entiendo cómo se rompen en la práctica. ¿Un ejemplo con semáforos?', @pm - INTERVAL 12 DAY),
-- ===== MAS ATRAS =====
('P34', 'lucas.benitez@comunidad.knowlink.test', 'H', 'Inglés',
 '¿Me ayudan a preparar la entrevista técnica en inglés?',
 'Tengo una entrevista técnica en inglés la semana que viene y me cuesta explicar conceptos de programación de forma oral. ¿Algún consejo o vocabulario clave?', @pm - INTERVAL 45 DAY),
('P35', 'martina.rossi@comunidad.knowlink.test', 'S', 'Química',
 'Tabla periódica: truco para memorizar los grupos',
 'Con una regla simple por columnas y una frase mnemotécnica me aprendí los grupos principales en una tarde. Se las comparto por si les sirve.', @pm - INTERVAL 50 DAY),
('P36', 'matias.velasquez@comunidad.knowlink.test', 'S', 'Redes',
 'Apuntes de Redes: el modelo OSI en una página',
 'Resumen visual del modelo OSI con un ejemplo de cómo viaja un paquete HTTP por cada capa. Pensado para repasar antes del parcial.', @pm - INTERVAL 30 DAY),
-- ===== ELIMINADA (no debe aparecer en ningun listado) =====
('P37', 'matias.velasquez@comunidad.knowlink.test', 'H', 'Redes',
 'Publicación eliminada de prueba (no debería verse)',
 'Esta publicación fue eliminada por su autor y no debe aparecer en el feed ni en Mis publicaciones.', @w2),
('P38', 'lucas.benitez@comunidad.knowlink.test', 'H', 'Física',
 '¿Cómo calculo la aceleración centrípeta de un satélite en órbita circular?',
 'Tengo la altura de la órbita y la masa de la Tierra. ¿Uso la ley de gravitación universal o la fórmula de aceleración centrípeta?', @m1);

-- Estado, ediciones, eliminadas y adjuntos
UPDATE tmp_post SET pstatus = 'RESOLVED',
  resolved_after = CASE k
    WHEN 'P03' THEN 180 WHEN 'P09' THEN 600 WHEN 'P13' THEN 300 WHEN 'P17' THEN 1500
    WHEN 'P20' THEN 1500 WHEN 'P21' THEN 900 WHEN 'P22' THEN 1200 WHEN 'P23' THEN 1000 WHEN 'P24' THEN 800
    WHEN 'P25' THEN 700 WHEN 'P26' THEN 900 WHEN 'P27' THEN 800 WHEN 'P28' THEN 1100 END
WHERE k IN ('P03','P09','P13','P17','P20','P21','P22','P23','P24','P25','P26','P27','P28');

UPDATE tmp_post SET edited = 1 WHERE k IN ('P04', 'P16');
UPDATE tmp_post SET deleted = 1 WHERE k = 'P37';

UPDATE tmp_post SET att_name = 'guia3_ejercicio_4b.png', att_type = 'image/png', att_size = 1480000 WHERE k = 'P01';
UPDATE tmp_post SET att_name = 'Cheatsheet_Complejidad_Algoritmica.pdf', att_type = 'application/pdf', att_size = 1258291 WHERE k = 'P02';
UPDATE tmp_post SET att_name = 'Resumen_Termodinamica.pdf', att_type = 'application/pdf', att_size = 2097152 WHERE k = 'P10';
UPDATE tmp_post SET att_name = 'Resumen_Streams.xlsx', att_type = 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet', att_size = 85000 WHERE k = 'P19';
UPDATE tmp_post SET att_name = 'Modelo_OSI.pdf', att_type = 'application/pdf', att_size = 640000 WHERE k = 'P36';

UPDATE tmp_post SET post_id = UUID_TO_BIN(UUID());

INSERT INTO community_post (post_id, author_id, author_role, post_type, post_status, subject_id, title, content,
                            attachment_path, attachment_file_name, attachment_content_type, attachment_size_bytes,
                            featured_reply_id, created_at, resolved_at, edited_at, deleted_at)
SELECT tp.post_id, u.user_id, u.role,
       CASE tp.ptype WHEN 'H' THEN 'HELP_REQUEST' ELSE 'SHARED_CONTENT' END,
       tp.pstatus, s.subject_id, tp.title, tp.content,
       IF(tp.att_name IS NULL, NULL, CONCAT('community/seed-demo/', REPLACE(UUID(), '-', ''), '-', tp.att_name)),
       tp.att_name, tp.att_type, tp.att_size,
       NULL, tp.created_at + INTERVAL @shift HOUR,
       IF(tp.resolved_after IS NULL, NULL, LEAST(@now, tp.created_at + INTERVAL tp.resolved_after MINUTE) + INTERVAL @shift HOUR),
       IF(tp.edited = 1, LEAST(@now, tp.created_at + INTERVAL 30 MINUTE) + INTERVAL @shift HOUR, NULL),
       IF(tp.deleted = 1, LEAST(@now, tp.created_at + INTERVAL 60 MINUTE) + INTERVAL @shift HOUR, NULL)
FROM tmp_post tp
JOIN users u ON u.email = tp.author
JOIN subjects s ON s.name = tp.subject;

-- ---------------------------------------------------------------------------
-- 4. Respuestas
--    El rol de la respuesta es el del usuario (tutores responden como TUTOR, alumnos como STUDENT).
--    delay = minutos despues de la publicacion; marks = cantidad de marcas "Esto me sirvio".
-- ---------------------------------------------------------------------------
DROP TEMPORARY TABLE IF EXISTS tmp_reply;
CREATE TEMPORARY TABLE tmp_reply (
  rk VARCHAR(6) PRIMARY KEY,
  pk VARCHAR(4),
  author VARCHAR(120),
  delay_min INT,
  content TEXT,
  marks INT DEFAULT 0,
  card TINYINT DEFAULT 1,
  question VARCHAR(150) NULL,
  edited TINYINT DEFAULT 0,
  deleted TINYINT DEFAULT 0,
  featured TINYINT DEFAULT 0,
  att_name VARCHAR(200) NULL,
  att_type VARCHAR(100) NULL,
  att_size BIGINT NULL,
  reply_id BINARY(16) NULL
);

INSERT INTO tmp_reply (rk, pk, author, delay_min, content, marks) VALUES
-- P01 (hoy, duda de Lucas, 4 respuestas: el autor responde despues de que otros contestaron)
('P01a', 'P01', 'julian.mendez@comunidad.knowlink.test', 10,
 'No necesariamente: la matriz es diagonalizable si y solo si la **multiplicidad geométrica** de cada autovalor coincide con la algebraica. Si para λ = 2 el núcleo de (A - 2I) tiene dimensión 1 y la multiplicidad algebraica es 2, entonces **no** es diagonalizable. Revisá el rango de (A - 2I): tendría que ser 1 para tener dos autovectores.', 3),
('P01b', 'P01', 'carolina.vega@comunidad.knowlink.test', 14,
 'Para confirmar si calculaste bien el kernel, escalonar (A - 2I) y contá las filas nulas: la cantidad de variables libres es la dimensión del subespacio propio.', 1),
('P01c', 'P01', 'valentina.cruz@comunidad.knowlink.test', 18,
 'A mí me pasó lo mismo con ese ejercicio. En la clase de consulta nos dijeron que es el caso típico de matriz no diagonalizable (forma de Jordan).', 0),
('P01d', 'P01', 'lucas.benitez@comunidad.knowlink.test', 22,
 'Gracias a los dos, ya lo vi: el rango de (A - 2I) me daba 2, así que no es diagonalizable.', 0),
-- P02 (aporte de Sofia, 4 respuestas)
('P02a', 'P02', 'agustina.luna@comunidad.knowlink.test', 5,
 '¡Muy buena guía! Justo la estaba necesitando para el parcial del viernes.', 2),
('P02b', 'P02', 'matias.velasquez@comunidad.knowlink.test', 9,
 'Una consulta: si un algoritmo es Theta(n log n), ¿también es correcto decir que es O(n^2)? Siempre me confundo con eso.', 0),
('P02c', 'P02', 'sofia.albarracin@comunidad.knowlink.test', 20,
 'Buena pregunta, Matías: sí, es correcto porque O es una cota superior (no necesariamente ajustada). Pero no es *informativo*: siempre conviene dar la cota más ajustada posible.', 3),
('P02d', 'P02', 'luciana.gomez@comunidad.knowlink.test', 12,
 'Excelente resumen. Agrego que en las entrevistas técnicas piden justamente esta distinción.', 4),
-- P03 (resuelta, destacada de Julian)
('P03a', 'P03', 'julian.mendez@comunidad.knowlink.test', 20,
 'El factor $r$ surge del determinante de la matriz jacobiana al pasar del elemento de área cartesiano (dx dy) al polar (r dr dθ). Al proyectar la sombra del cono sobre el plano XY, la deformación métrica depende del radio.', 5),
('P03b', 'P03', 'carolina.vega@comunidad.knowlink.test', 60,
 'Para verlo gráficamente: un sector circular de ángulo dθ a distancia r tiene un arco de largo r·dθ, por eso el área elemental crece con r.', 2),
('P03c', 'P03', 'nicolas.ibarra@comunidad.knowlink.test', 90, 'Gracias, me sirvió para mi práctico también.', 0),
-- P04 (de Matias, abierta y editada, con respuestas de otros)
('P04a', 'P04', 'luciana.gomez@comunidad.knowlink.test', 8,
 CONCAT('Depende del peor caso. La recursión es perfecta si el árbol está balanceado (altura O(log n)). En un árbol degenerado la altura es O(n) y podés tener StackOverflowError.', CHAR(10), CHAR(10),
        'Versión iterativa con pila explícita:', CHAR(10),
        '```', CHAR(10),
        'Deque<Nodo> pila = new ArrayDeque<>();', CHAR(10),
        'Nodo actual = raiz;', CHAR(10),
        'while (actual != null || !pila.isEmpty()) {', CHAR(10),
        '    while (actual != null) { pila.push(actual); actual = actual.izq; }', CHAR(10),
        '    actual = pila.pop();', CHAR(10),
        '    visitar(actual);', CHAR(10),
        '    actual = actual.der;', CHAR(10),
        '}', CHAR(10),
        '```'), 3),
('P04b', 'P04', 'sofia.albarracin@comunidad.knowlink.test', 15,
 'Sumo que en un ABB autobalanceado (AVL o rojinegro) la altura es logarítmica y la recursión no suele ser un problema. Para el TP, la versión recursiva está perfecta y es más legible.', 2),
('P04c', 'P04', 'lucas.benitez@comunidad.knowlink.test', 20,
 'Yo usé la iterativa en el TP y me pidieron justificar por qué: tené en cuenta el costo de mantener la pila.', 0),
-- P06 (hoy-semana, con adjunto en la respuesta)
('P06a', 'P06', 'ricardo.salas@comunidad.knowlink.test', 30,
 'La carga triangular se reemplaza por su resultante: **W = (1/2)·q·L**, aplicada a **L/3 del lado de la carga máxima**. Con eso planteás equilibrio de momentos en un apoyo y despejás la reacción del otro. Te adjunto el DCL resuelto.', 2),
('P06b', 'P06', 'nicolas.ibarra@comunidad.knowlink.test', 45, 'Me quedó la misma duda, gracias por el adjunto.', 0),
-- P07 (aporte de Julian)
('P07a', 'P07', 'matias.velasquez@comunidad.knowlink.test', 20, '¡Gracias por el tip! Con esa tabla sola ya repasé media materia.', 0),
('P07b', 'P07', 'martina.rossi@comunidad.knowlink.test', 30, 'Muy útil, lo guardé para el parcial.', 1),
-- P08
('P08a', 'P08', 'paula.ferreyra@comunidad.knowlink.test', 25,
 'El asiento de amortización es: **Amortización del ejercicio (Resultado negativo)** al Debe y **Amortización acumulada (Regularizadora del Activo)** al Haber. Así no tocás el valor de origen del rodado.', 3),
-- P09 (resuelta, destacada de Sofia)
('P09a', 'P09', 'sofia.albarracin@comunidad.knowlink.test', 40,
 'Criterios clave: **orden** (el AVL permite recorrer ordenado y buscar rangos; el hash no), **memoria**, **peor caso** (el hash puede degradarse a O(n) con colisiones) y **necesidad de claves ordenadas**. Para un diccionario sin rangos, el hash gana.', 4),
('P09b', 'P09', 'diego.montoya@comunidad.knowlink.test', 70,
 'También influye el factor de carga y la función hash. Para 100.000 palabras un hash con encadenamiento va muy bien.', 1),
-- P10 (aporte de Mateo)
('P10a', 'P10', 'lucas.benitez@comunidad.knowlink.test', 15, 'Gracias Mateo, ¡justo lo que buscaba!', 1),
('P10b', 'P10', 'matias.velasquez@comunidad.knowlink.test', 100, 'Una consulta: ¿el signo del trabajo cambia según la convención IUPAC?', 0),
-- P11
('P11a', 'P11', 'tomas.herrera@comunidad.knowlink.test', 35,
 'Los mínimos son: **programa de auditoría, planilla de planeamiento, cédulas sumarias y analíticas, carta de representación y memorando de conclusiones**. Cada cédula se referencia con un índice cruzado.', 2),
-- P12
('P12a', 'P12', 'diego.montoya@comunidad.knowlink.test', 20,
 'Regla práctica: si necesitás que **no se pierda nada** (archivos, pagos, login) usá TCP; si preferís **baja latencia aunque se pierdan paquetes** (juego online, streaming, voz) usá UDP.', 3),
('P12b', 'P12', 'bruno.castro@comunidad.knowlink.test', 50, 'Aclaro que muchos juegos implementan su propio control de confiabilidad sobre UDP.', 1),
('P12c', 'P12', 'matias.velasquez@comunidad.knowlink.test', 60, 'Genial, ahora entiendo por qué los chats usan TCP/WebSocket.', 0),
-- P13 (resuelta de Matias, destacada de Julian; incluye una respuesta eliminada)
('P13a', 'P13', 'julian.mendez@comunidad.knowlink.test', 25,
 'Planteá para cada bloque **ΣF = m·a** a lo largo del plano. La fricción siempre se **opone al movimiento (o a la tendencia)**: primero suponé el sentido del movimiento y recién después verificá que la aceleración te dé positiva.', 4),
('P13b', 'P13', 'carolina.vega@comunidad.knowlink.test', 60,
 'Si la cuerda está tensa, la aceleración de los dos bloques es la misma en módulo. Con eso eliminás la tensión sumando las dos ecuaciones.', 1),
('P13c', 'P13', 'lucas.benitez@comunidad.knowlink.test', 90, 'Esta respuesta fue eliminada por su autor.', 0),
-- P14
('P14a', 'P14', 'agustina.luna@comunidad.knowlink.test', 30, '¡Gran ejemplo! El tema de los semivariables siempre me jugaba en contra.', 1),
-- P15
('P15a', 'P15', 'bruno.castro@comunidad.knowlink.test', 40,
 'Un **hazard de datos** ocurre cuando una instrucción necesita un resultado que otra anterior todavía no escribió. Se resuelve con *forwarding* (adelantar el valor desde la etapa EX/MEM) y, si no alcanza, con un *stall* (burbuja).', 2),
-- P16 (aporte de Florencia, editada)
('P16a', 'P16', 'lucas.benitez@comunidad.knowlink.test', 22, 'Excelente resumen, gracias.', 1),
('P16b', 'P16', 'camila.torres@comunidad.knowlink.test', 40, '¿Early stopping y validación cruzada se pueden combinar?', 0),
-- P17 (resuelta, destacada de Mateo)
('P17a', 'P17', 'mateo.rivas@comunidad.knowlink.test', 60,
 'Empezá por el **balance global** del proceso (sin reciclo, porque el reciclo es una corriente interna) y después planteá el balance en el **reactor** y en el **mezclador**. Con los tres sistemas vas a poder despejar las incógnitas.', 3),
('P17b', 'P17', 'elena.quiroga@comunidad.knowlink.test', 120,
 'Tip: dibujá el diagrama de bloques con todas las corrientes y numeralas antes de plantear nada.', 0),
-- P18
('P18a', 'P18', 'ricardo.salas@comunidad.knowlink.test', 90,
 'Para columnas la cuantía mínima longitudinal suele ser del **1%** de la sección bruta (verificá el reglamento de tu cátedra). En una 30x30 son 9 cm² de acero.', 1),
-- P19
('P19a', 'P19', 'matias.velasquez@comunidad.knowlink.test', 15, 'Qué buen aporte, gracias Vale.', 0),
('P19b', 'P19', 'luciana.gomez@comunidad.knowlink.test', 30, 'Muy completo. Agregaría los collectors de Streams (groupingBy y partitioningBy).', 2),
-- P20..P28: resueltas el mes anterior, con la mejor respuesta de un tutor (ranking)
('P20a', 'P20', 'sofia.albarracin@comunidad.knowlink.test', 60,
 'Para insertion sort el invariante es: **al empezar cada iteración, A[0..i-1] está ordenado y contiene los mismos elementos que al comienzo**. Probás inicialización (i=1, un solo elemento), mantenimiento (insertar A[i] mantiene el orden) y terminación (i = n).', 5),
('P20b', 'P20', 'luciana.gomez@comunidad.knowlink.test', 120, 'Sumo que la terminación se prueba porque el for avanza i hasta n.', 0),
('P21a', 'P21', 'sofia.albarracin@comunidad.knowlink.test', 45,
 'Una **cola** es la fila del banco: el primero que llega es el primero que se atiende (FIFO). Una **pila** es la pila de platos: el último que apilás es el primero que sacás (LIFO).', 4),
('P22a', 'P22', 'sofia.albarracin@comunidad.knowlink.test', 50,
 'Te falta contemplar el caso de **lista vacía**: antes del bucle, preguntá si la cabeza es null y devolvé. Es el clásico NullPointerException de las listas enlazadas.', 3),
('P22b', 'P22', 'diego.montoya@comunidad.knowlink.test', 100, 'También revisá que el último nodo apunte a null y no a sí mismo.', 0),
('P23a', 'P23', 'julian.mendez@comunidad.knowlink.test', 70,
 'L''Hôpital solo se aplica a las indeterminaciones **0/0 e ∞/∞** y siempre que exista el límite del cociente de derivadas. Si tenés 0·∞ o ∞-∞, primero transformás a un cociente.', 6),
('P24a', 'P24', 'julian.mendez@comunidad.knowlink.test', 40,
 'Sí: resolvé el sistema, expresá la solución en función de las **variables libres** y los vectores que multiplican a cada libre forman una base. La dimensión es la cantidad de libres.', 4),
('P24b', 'P24', 'carolina.vega@comunidad.knowlink.test', 90, 'Chequeo rápido: dimensión = n - rango(A).', 0),
('P25a', 'P25', 'luciana.gomez@comunidad.knowlink.test', 45,
 'Usá una **interfaz** para definir un contrato (qué hace) y una **clase abstracta** cuando querés compartir estado o código base. Una clase puede implementar muchas interfaces pero extender una sola clase.', 7),
('P26a', 'P26', 'mateo.rivas@comunidad.knowlink.test', 50,
 'Por Hess: **ΔH° = Σ ΔHf°(productos) - Σ ΔHf°(reactivos)**, multiplicando cada término por su coeficiente estequiométrico. Cuidado con los elementos puros, que valen cero.', 9),
('P27a', 'P27', 'carolina.vega@comunidad.knowlink.test', 55,
 'Una rotación no tiene autovectores reales (salvo 0° y 180°) porque ninguna dirección se conserva. Los autovalores complejos **e^(±iθ)** codifican el ángulo de giro; sobre los complejos sí diagonaliza.', 6),
('P28a', 'P28', 'diego.montoya@comunidad.knowlink.test', 60,
 'La 3FN exige que ningún atributo no clave dependa de otro no clave. Si **cp → ciudad** y cp no es clave, es una dependencia transitiva: separá una tabla de códigos postales. La desnormalización solo se justifica con mediciones de rendimiento.', 4),
-- P29..P33: abiertas del mes anterior con respuestas de tutores sin ser destacadas (suman marcas al ranking)
('P29a', 'P29', 'ricardo.salas@comunidad.knowlink.test', 50,
 'La tensión máxima es **σ = M·y / I**, donde M es el momento flector máximo (P·L/4 en el centro), y la distancia a la fibra más alejada y I la inercia de la sección. Ocurre en la fibra superior (compresión) e inferior (tracción).', 8),
('P30a', 'P30', 'paula.ferreyra@comunidad.knowlink.test', 40,
 'Cambia **el precio** → te movés **a lo largo** de la curva (cambio en la cantidad demandada). Cambia **otro factor** (ingreso, gustos, precio de sustitutos) → se **desplaza** toda la curva.', 6),
('P31a', 'P31', 'tomas.herrera@comunidad.knowlink.test', 60,
 'Con varios productos usá el **margen de contribución ponderado** según la mezcla de ventas: PE = Costos fijos / MC promedio ponderado. Después repartís el resultado según la mezcla.', 5),
('P32a', 'P32', 'florencia.diaz@comunidad.knowlink.test', 70,
 '**Supervisado**: tenés la respuesta correcta (precio de una casa, spam/no spam). **No supervisado**: buscás estructura (clusters de clientes). Para evaluar clustering se usan métricas internas como la silueta.', 7),
('P33a', 'P33', 'bruno.castro@comunidad.knowlink.test', 55,
 'La forma más simple de prevenir un deadlock es **romper la espera circular** imponiendo un orden global para adquirir los semáforos.', 2);

-- Tutores: todas las respuestas ofrecen la tarjeta de reserva salvo las listadas
UPDATE tmp_reply SET card = 0 WHERE rk IN ('P01b', 'P03b', 'P09b', 'P13b', 'P12b', 'P22b', 'P24b', 'P20b', 'P17b');
UPDATE tmp_reply SET question = '¿Querés practicar complejidad con ejercicios de parciales?' WHERE rk = 'P02d';
UPDATE tmp_reply SET question = '¿Querés ver más estructuras de recorrido en una clase 1 a 1?' WHERE rk = 'P04a';
UPDATE tmp_reply SET question = '¿Necesitás repasar asientos contables paso a paso?' WHERE rk = 'P08a';
-- Los alumnos no tienen tarjeta de reserva
UPDATE tmp_reply SET card = 0 WHERE author IN (SELECT email FROM tmp_user WHERE role = 'STUDENT');

UPDATE tmp_reply SET edited = 1 WHERE rk IN ('P04c', 'P08a');
UPDATE tmp_reply SET deleted = 1 WHERE rk = 'P13c';
UPDATE tmp_reply SET featured = 1 WHERE rk IN ('P03a', 'P09a', 'P13a', 'P17a',
  'P20a', 'P21a', 'P22a', 'P23a', 'P24a', 'P25a', 'P26a', 'P27a', 'P28a');
UPDATE tmp_reply SET att_name = 'DCL_viga_triangular.png', att_type = 'image/png', att_size = 920000 WHERE rk = 'P06a';
UPDATE tmp_reply SET att_name = 'Resumen_Streams_ejemplos.pdf', att_type = 'application/pdf', att_size = 310000 WHERE rk = 'P19b';

UPDATE tmp_reply SET reply_id = UUID_TO_BIN(UUID());

INSERT INTO community_reply (reply_id, post_id, author_id, author_role, content,
                             attachment_path, attachment_file_name, attachment_content_type, attachment_size_bytes,
                             include_booking_card, booking_card_question, created_at, edited_at, deleted_at)
SELECT tr.reply_id, tp.post_id, u.user_id, u.role, tr.content,
       IF(tr.att_name IS NULL, NULL, CONCAT('community/seed-demo/', REPLACE(UUID(), '-', ''), '-', tr.att_name)),
       tr.att_name, tr.att_type, tr.att_size,
       IF(u.role = 'TUTOR', tr.card, 0), IF(u.role = 'TUTOR', tr.question, NULL),
       LEAST(@now, tp.created_at + INTERVAL tr.delay_min MINUTE) + INTERVAL @shift HOUR,
       IF(tr.edited = 1, LEAST(@now, tp.created_at + INTERVAL (tr.delay_min + 20) MINUTE) + INTERVAL @shift HOUR, NULL),
       IF(tr.deleted = 1, LEAST(@now, tp.created_at + INTERVAL (tr.delay_min + 30) MINUTE) + INTERVAL @shift HOUR, NULL)
FROM tmp_reply tr
JOIN tmp_post tp ON tp.k = tr.pk
JOIN users u ON u.email = tr.author;

-- Respuesta destacada de las publicaciones resueltas
UPDATE community_post p
JOIN tmp_post tp ON tp.post_id = p.post_id
JOIN tmp_reply tr ON tr.pk = tp.k AND tr.featured = 1
SET p.featured_reply_id = tr.reply_id;

-- ---------------------------------------------------------------------------
-- 5. Marcas "Esto me sirvio"
--    Por cada respuesta se eligen `marks` usuarios distintos del autor de la respuesta. Matias (el alumno
--    principal) queda afuera del reparto automatico para poder probar marcar y desmarcar desde la UI,
--    salvo en las respuestas de la lista explicita de abajo.
-- ---------------------------------------------------------------------------
INSERT INTO community_reply_helpful (helpful_id, reply_id, user_id, created_at)
SELECT UUID_TO_BIN(UUID()), x.reply_id, x.user_id, x.created_at
FROM (
  SELECT tr.reply_id, m.user_id,
         LEAST(@now_db, cr.created_at + INTERVAL 2 HOUR) AS created_at,
         ROW_NUMBER() OVER (PARTITION BY tr.reply_id ORDER BY MD5(CONCAT(tr.reply_id, m.user_id))) AS n,
         tr.marks
  FROM tmp_reply tr
  JOIN community_reply cr ON cr.reply_id = tr.reply_id
  JOIN users m ON m.email LIKE '%@comunidad.knowlink.test'
              AND m.email <> 'matias.velasquez@comunidad.knowlink.test'
              AND m.user_id <> cr.author_id
  WHERE tr.marks > 0 AND tr.deleted = 0
) x
WHERE x.n <= x.marks;

-- Matias ya marco estas respuestas (aparecen como "marcadas por mi"); en el resto puede marcar el
INSERT IGNORE INTO community_reply_helpful (helpful_id, reply_id, user_id, created_at)
SELECT UUID_TO_BIN(UUID()), tr.reply_id, u.user_id, LEAST(@now_db, cr.created_at + INTERVAL 30 MINUTE)
FROM tmp_reply tr
JOIN community_reply cr ON cr.reply_id = tr.reply_id
JOIN users u ON u.email = 'matias.velasquez@comunidad.knowlink.test'
WHERE tr.rk IN ('P02d', 'P04a', 'P13a', 'P09a');

-- ---------------------------------------------------------------------------
-- 6. Notificaciones de respuesta para los autores de las publicaciones (US-55)
--    Las de los ultimos 3 dias quedan sin leer.
-- ---------------------------------------------------------------------------
INSERT IGNORE INTO user_notification (notification_id, recipient_user_id, recipient_role, notification_type,
                                      resource_id, title, message, dedupe_key, created_at, read_at)
SELECT UUID_TO_BIN(UUID()), pa.user_id, pa.role, 'COMMUNITY_REPLY', p.post_id,
       'Nueva respuesta en tu publicación',
       CONCAT(ra.full_name, ' respondió en "',
              IF(CHAR_LENGTH(p.title) <= 100, p.title, CONCAT(TRIM(LEFT(p.title, 100)), '…')), '".'),
       CONCAT('community-reply:', LOWER(BIN_TO_UUID(r.reply_id))),
       r.created_at,
       IF(r.created_at >= @now_db - INTERVAL 3 DAY, NULL, LEAST(@now_db, r.created_at + INTERVAL 1 HOUR))
FROM community_reply r
JOIN community_post p ON p.post_id = r.post_id
JOIN users pa ON pa.user_id = p.author_id
JOIN users ra ON ra.user_id = r.author_id
JOIN tmp_post tp ON tp.post_id = p.post_id
WHERE r.deleted_at IS NULL
  AND p.deleted_at IS NULL
  AND r.author_id <> p.author_id;

-- ===========================================================================
-- DATOS PARA LAS SECCIONES DEL MENU (alumno y tutor)
-- ===========================================================================
SET @d = '@comunidad.knowlink.test';

-- Direccion de los tutores que dan clases presenciales (la muestra el detalle de la reserva)
UPDATE tutor_profile tp JOIN users u ON u.user_id = tp.user_id
   SET tp.address = 'Av. Sabattini 1450, Villa María, Córdoba'
 WHERE u.email = CONCAT('sofia.albarracin', @d) AND tp.address IS NULL;
UPDATE tutor_profile tp JOIN users u ON u.user_id = tp.user_id
   SET tp.address = 'Bv. Alvear 320, Villa María, Córdoba'
 WHERE u.email = CONCAT('julian.mendez', @d) AND tp.address IS NULL;

-- ---------------------------------------------------------------------------
-- 7. Agenda de los tutores ("Mi agenda")
--    Un bloque = un horario (time_slot) de lunes a viernes durante las proximas 12 semanas. Los bloques de la
--    primera semana son los "originales" y el resto las copias que genera la repeticion semanal.
-- ---------------------------------------------------------------------------
DROP TEMPORARY TABLE IF EXISTS tmp_n;
CREATE TEMPORARY TABLE tmp_n (n INT PRIMARY KEY);
INSERT INTO tmp_n (n)
SELECT a.d + 10 * b.d + 100 * c.d
FROM (SELECT 0 AS d UNION ALL SELECT 1 UNION ALL SELECT 2 UNION ALL SELECT 3 UNION ALL SELECT 4
      UNION ALL SELECT 5 UNION ALL SELECT 6 UNION ALL SELECT 7 UNION ALL SELECT 8 UNION ALL SELECT 9) a
CROSS JOIN (SELECT 0 AS d UNION ALL SELECT 1 UNION ALL SELECT 2 UNION ALL SELECT 3 UNION ALL SELECT 4
      UNION ALL SELECT 5 UNION ALL SELECT 6 UNION ALL SELECT 7 UNION ALL SELECT 8 UNION ALL SELECT 9) b
CROSS JOIN (SELECT 0 AS d UNION ALL SELECT 1) c;

-- Dias habiles de las ultimas 12 semanas (past = 1) y de las proximas 12 (past = 0)
DROP TEMPORARY TABLE IF EXISTS tmp_day;
CREATE TEMPORARY TABLE tmp_day (d DATE PRIMARY KEY, past TINYINT);
INSERT INTO tmp_day (d, past)
SELECT @today - INTERVAL 84 DAY + INTERVAL n DAY, IF(n < 84, 1, 0)
FROM tmp_n
WHERE n < 168 AND WEEKDAY(@today - INTERVAL 84 DAY + INTERVAL n DAY) < 5;

DROP TEMPORARY TABLE IF EXISTS tmp_block;
CREATE TEMPORARY TABLE tmp_block (tutor VARCHAR(120), start_t TIME, end_t TIME);
INSERT INTO tmp_block (tutor, start_t, end_t) VALUES
  (CONCAT('sofia.albarracin', @d), '09:00', '13:00'), (CONCAT('sofia.albarracin', @d), '15:00', '19:00'),
  (CONCAT('julian.mendez', @d),    '10:00', '14:00'), (CONCAT('julian.mendez', @d),    '16:00', '20:00'),
  (CONCAT('luciana.gomez', @d),    '14:00', '20:00'),
  (CONCAT('diego.montoya', @d),    '09:00', '12:00'), (CONCAT('diego.montoya', @d),    '17:00', '21:00'),
  (CONCAT('mateo.rivas', @d),      '08:00', '12:00'),
  (CONCAT('florencia.diaz', @d),   '15:00', '21:00');

INSERT INTO availability_block (availability_block_id, `date`, start_time, end_time, available, repeat_weekly,
                                auto_generated, tutor_profile_id)
SELECT UUID_TO_BIN(UUID()), d.d, p.start_t, p.end_t, 1, 1, IF(d.d < @today + INTERVAL 7 DAY, 0, 1), tp.tutor_profile_id
FROM tmp_day d
JOIN tmp_block p
JOIN users u ON u.email = p.tutor
JOIN tutor_profile tp ON tp.user_id = u.user_id
WHERE d.past = 0;

INSERT INTO time_slot (time_slot_id, `date`, start_time, end_time, status, tutor_profile_id, availability_block_id)
SELECT UUID_TO_BIN(UUID()), b.`date`, b.start_time, b.end_time, 'AVAILABLE', b.tutor_profile_id, b.availability_block_id
FROM availability_block b
JOIN tutor_profile tp ON tp.tutor_profile_id = b.tutor_profile_id
JOIN users u ON u.user_id = tp.user_id
WHERE u.email LIKE '%@comunidad.knowlink.test';

-- Horarios ofrecidos en las ultimas 12 semanas (el job diario los archiva aca): alimentan la ocupacion de las estadisticas.
DROP TEMPORARY TABLE IF EXISTS tmp_hist;
CREATE TEMPORARY TABLE tmp_hist (tutor VARCHAR(120), dow TINYINT, start_t TIME, end_t TIME);   -- dow: 0 = lunes
INSERT INTO tmp_hist (tutor, dow, start_t, end_t) VALUES
  (CONCAT('sofia.albarracin', @d), 1, '09:00', '11:00'), (CONCAT('sofia.albarracin', @d), 3, '09:00', '11:00'),
  (CONCAT('sofia.albarracin', @d), 0, '15:00', '17:00'), (CONCAT('sofia.albarracin', @d), 2, '15:00', '17:00'),
  (CONCAT('julian.mendez', @d),    0, '10:00', '12:00'), (CONCAT('julian.mendez', @d),    2, '10:00', '12:00'),
  (CONCAT('julian.mendez', @d),    4, '10:00', '12:00'),
  (CONCAT('luciana.gomez', @d),    1, '14:00', '17:00'), (CONCAT('luciana.gomez', @d),    3, '14:00', '17:00');

INSERT INTO offered_slot_history (offered_slot_history_id, tutor_profile_id, `date`, start_time, end_time)
SELECT UUID_TO_BIN(UUID()), tp.tutor_profile_id, d.d, h.start_t, h.end_t
FROM tmp_day d
JOIN tmp_hist h ON h.dow = WEEKDAY(d.d)
JOIN users u ON u.email = h.tutor
JOIN tutor_profile tp ON tp.user_id = u.user_id
WHERE d.past = 1;

-- ---------------------------------------------------------------------------
-- 8. Reservas ("Mis clases" del alumno y del tutor)
--    futuro = 1: la reserva usa el horario de un bloque de la agenda del tutor (pasa al dia habil siguiente si cae
--    en fin de semana). futuro = 0: horario propio ya ocupado. Prefijos: H = escenarios de Matias, S = escenarios
--    de Sofia, G = sesiones generadas para el historial y las estadisticas.
-- ---------------------------------------------------------------------------
DROP TEMPORARY TABLE IF EXISTS tmp_booking;
CREATE TEMPORARY TABLE tmp_booking (
  k VARCHAR(8) PRIMARY KEY,
  student VARCHAR(120),
  tutor VARCHAR(120),
  subject VARCHAR(120),
  sdate DATE,
  start_t TIME,
  mins INT DEFAULT 60,
  end_t TIME NULL,
  modality VARCHAR(10) DEFAULT 'VIRTUAL',
  status VARCHAR(30),
  future TINYINT DEFAULT 0,
  rating_mode VARCHAR(8) DEFAULT 'BOTH',      -- BOTH: se calificaron los dos | STUDENT: solo el alumno | NONE: nadie
  cancel_by VARCHAR(8) DEFAULT 'STUDENT',
  cancel_hours INT DEFAULT 48,
  fund VARCHAR(10) NULL,
  amount DECIMAL(10,2) NULL,
  booking_id BINARY(16) NULL,
  slot_id BINARY(16) NULL,
  student_id BINARY(16) NULL,
  tutor_id BINARY(16) NULL,
  tutor_profile_id BINARY(16) NULL,
  tutor_subject_id BINARY(16) NULL
);

INSERT INTO tmp_booking (k, student, tutor, subject, sdate, start_t, modality, status, future) VALUES
  -- Reservadas (futuras)
  ('H01', CONCAT('matias.velasquez', @d), CONCAT('sofia.albarracin', @d), 'Algoritmos',          @today + INTERVAL 1 DAY, '10:00', 'VIRTUAL',   'BOOKED', 1),
  ('H02', CONCAT('matias.velasquez', @d), CONCAT('julian.mendez', @d),    'Física',              @today + INTERVAL 3 DAY, '16:00', 'IN_PERSON', 'BOOKED', 1),
  ('H03', CONCAT('matias.velasquez', @d), CONCAT('diego.montoya', @d),    'Base de Datos',       @today + INTERVAL 6 DAY, '18:00', 'VIRTUAL',   'BOOKED', 1),
  ('H04', CONCAT('lucas.benitez', @d),    CONCAT('sofia.albarracin', @d), 'Estructuras de Datos', @today + INTERVAL 2 DAY, '15:00', 'VIRTUAL',   'BOOKED', 1),
  ('H05', CONCAT('martina.rossi', @d),    CONCAT('sofia.albarracin', @d), 'Algoritmos',          @today + INTERVAL 4 DAY, '16:00', 'VIRTUAL',   'BOOKED', 1),
  ('H06', CONCAT('valentina.cruz', @d),   CONCAT('sofia.albarracin', @d), 'Programación',        @today + INTERVAL 8 DAY, '09:00', 'IN_PERSON', 'BOOKED', 1),
  ('H07', CONCAT('camila.torres', @d),    CONCAT('julian.mendez', @d),    'Álgebra',             @today + INTERVAL 5 DAY, '17:00', 'VIRTUAL',   'BOOKED', 1),
  ('H08', CONCAT('agustina.luna', @d),    CONCAT('luciana.gomez', @d),    'Programación',        @today + INTERVAL 3 DAY, '15:00', 'VIRTUAL',   'BOOKED', 1),
  -- En curso: empezo hace 20 minutos
  ('H09', CONCAT('matias.velasquez', @d), CONCAT('luciana.gomez', @d),    'Programación',
         DATE(@now - INTERVAL 20 MINUTE), SEC_TO_TIME(FLOOR(TIME_TO_SEC(TIME(@now - INTERVAL 20 MINUTE)) / 60) * 60), 'VIRTUAL', 'IN_PROGRESS', 0),
  -- Completadas de Matias (H12 queda sin calificar; H14 solo la califico el alumno)
  ('H11', CONCAT('matias.velasquez', @d), CONCAT('sofia.albarracin', @d), 'Estructuras de Datos', @today - INTERVAL 2 DAY,  '12:00', 'VIRTUAL',   'COMPLETED', 0),
  ('H12', CONCAT('matias.velasquez', @d), CONCAT('julian.mendez', @d),    'Álgebra',             @today - INTERVAL 5 DAY,  '11:00', 'VIRTUAL',   'COMPLETED', 0),
  ('H13', CONCAT('matias.velasquez', @d), CONCAT('luciana.gomez', @d),    'Programación',        @today - INTERVAL 9 DAY,  '11:00', 'VIRTUAL',   'COMPLETED', 0),
  ('H14', CONCAT('matias.velasquez', @d), CONCAT('mateo.rivas', @d),      'Química',             @today - INTERVAL 12 DAY, '09:00', 'IN_PERSON', 'COMPLETED', 0),
  ('H15', CONCAT('matias.velasquez', @d), CONCAT('diego.montoya', @d),    'Redes',               @today - INTERVAL 20 DAY, '18:00', 'VIRTUAL',   'COMPLETED', 0),
  -- No confirmadas (con reclamo), tutor ausente, sesion no realizada, canceladas y vencida
  ('H16', CONCAT('matias.velasquez', @d), CONCAT('florencia.diaz', @d),   'Machine Learning',    @today - INTERVAL 4 DAY,  '19:00', 'VIRTUAL',   'NOT_CONFIRMED', 0),
  ('H17', CONCAT('matias.velasquez', @d), CONCAT('diego.montoya', @d),    'Base de Datos',       @today - INTERVAL 8 DAY,  '18:00', 'VIRTUAL',   'NOT_CONFIRMED', 0),
  ('H18', CONCAT('matias.velasquez', @d), CONCAT('mateo.rivas', @d),      'Fisicoquímica',       @today - INTERVAL 15 DAY, '10:00', 'VIRTUAL',   'NOT_FULFILLED_BY_TUTOR', 0),
  ('H19', CONCAT('matias.velasquez', @d), CONCAT('julian.mendez', @d),    'Física',              @today - INTERVAL 7 DAY,  '12:00', 'VIRTUAL',   'CANCELLED', 0),
  ('H20', CONCAT('matias.velasquez', @d), CONCAT('sofia.albarracin', @d), 'Programación',        @today - INTERVAL 3 DAY,  '13:00', 'VIRTUAL',   'CANCELLED', 0),
  ('H21', CONCAT('matias.velasquez', @d), CONCAT('luciana.gomez', @d),    'Algoritmos',          @today - INTERVAL 11 DAY, '12:00', 'VIRTUAL',   'SESSION_NOT_HELD', 0),
  ('H22', CONCAT('matias.velasquez', @d), CONCAT('florencia.diaz', @d),   'Inteligencia Artificial', @today - INTERVAL 6 DAY, '17:00', 'VIRTUAL', 'EXPIRED', 0),
  -- Escenarios de Sofia
  ('S01', CONCAT('lucas.benitez', @d),    CONCAT('sofia.albarracin', @d), 'Algoritmos',          @today - INTERVAL 6 DAY,  '11:00', 'VIRTUAL',   'NOT_CONFIRMED', 0),
  ('S02', CONCAT('valentina.cruz', @d),   CONCAT('sofia.albarracin', @d), 'Programación',        @today - INTERVAL 9 DAY,  '17:00', 'VIRTUAL',   'NOT_CONFIRMED', 0),
  ('S03', CONCAT('martina.rossi', @d),    CONCAT('sofia.albarracin', @d), 'Algoritmos',          @today - INTERVAL 22 DAY, '12:00', 'VIRTUAL',   'COMPLETED', 0),
  ('S04', CONCAT('agustina.luna', @d),    CONCAT('sofia.albarracin', @d), 'Estructuras de Datos', @today - INTERVAL 1 DAY, '17:00', 'VIRTUAL',   'COMPLETED', 0),
  ('S05', CONCAT('camila.torres', @d),    CONCAT('sofia.albarracin', @d), 'Estructuras de Datos', @today - INTERVAL 2 DAY, '17:00', 'VIRTUAL',   'CANCELLED', 0);

-- Sesiones generadas: Sofia (28 sesiones en ~3 meses), Julian (12) y Luciana (10). Los fines de semana pasan al viernes.
INSERT INTO tmp_booking (k, student, tutor, subject, sdate, start_t, modality, status, future)
SELECT CONCAT('GS', LPAD(g.n, 2, '0')),
       CONCAT(ELT(1 + g.n MOD 7, 'lucas.benitez', 'martina.rossi', 'camila.torres', 'valentina.cruz',
                  'agustina.luna', 'nicolas.ibarra', 'joaquin.paz'), @d),
       CONCAT('sofia.albarracin', @d),
       ELT(1 + g.n MOD 3, 'Algoritmos', 'Estructuras de Datos', 'Programación'),
       g.d - INTERVAL GREATEST(WEEKDAY(g.d) - 4, 0) DAY,
       ELT(1 + g.n MOD 4, '09:00', '10:00', '15:00', '16:00'),
       ELT(1 + g.n MOD 2, 'VIRTUAL', 'IN_PERSON'),
       CASE WHEN g.n MOD 9 = 4 THEN 'CANCELLED'
            WHEN g.n MOD 13 = 7 THEN 'NOT_CONFIRMED'
            WHEN g.n = 11 THEN 'SESSION_NOT_HELD'
            ELSE 'COMPLETED' END,
       0
FROM (SELECT n, @today - INTERVAL (4 + n * 3) DAY AS d FROM tmp_n WHERE n < 28) g;

INSERT INTO tmp_booking (k, student, tutor, subject, sdate, start_t, modality, status, future)
SELECT CONCAT('GJ', LPAD(g.n, 2, '0')),
       CONCAT(ELT(1 + g.n MOD 5, 'lucas.benitez', 'martina.rossi', 'camila.torres', 'valentina.cruz', 'agustina.luna'), @d),
       CONCAT('julian.mendez', @d),
       ELT(1 + g.n MOD 3, 'Análisis Matemático', 'Álgebra', 'Física'),
       g.d - INTERVAL GREATEST(WEEKDAY(g.d) - 4, 0) DAY,
       ELT(1 + g.n MOD 4, '14:00', '15:00', '16:00', '17:00'),
       'VIRTUAL',
       CASE WHEN g.n MOD 6 = 3 THEN 'CANCELLED' WHEN g.n = 8 THEN 'NOT_CONFIRMED' ELSE 'COMPLETED' END,
       0
FROM (SELECT n, @today - INTERVAL (6 + n * 7) DAY AS d FROM tmp_n WHERE n < 12) g;

INSERT INTO tmp_booking (k, student, tutor, subject, sdate, start_t, modality, status, future)
SELECT CONCAT('GL', LPAD(g.n, 2, '0')),
       CONCAT(ELT(1 + g.n MOD 5, 'martina.rossi', 'camila.torres', 'nicolas.ibarra', 'joaquin.paz', 'valentina.cruz'), @d),
       CONCAT('luciana.gomez', @d),
       ELT(1 + g.n MOD 2, 'Programación', 'Algoritmos'),
       g.d - INTERVAL GREATEST(WEEKDAY(g.d) - 4, 0) DAY,
       ELT(1 + g.n MOD 4, '14:00', '15:00', '16:00', '17:00'),
       'VIRTUAL',
       CASE WHEN g.n = 4 THEN 'CANCELLED' WHEN g.n = 7 THEN 'NOT_CONFIRMED' ELSE 'COMPLETED' END,
       0
FROM (SELECT n, @today - INTERVAL (5 + n * 8) DAY AS d FROM tmp_n WHERE n < 10) g;

-- Ajustes por escenario
UPDATE tmp_booking SET rating_mode = 'NONE'    WHERE k IN ('H12', 'S04');
UPDATE tmp_booking SET rating_mode = 'STUDENT' WHERE k IN ('H14', 'GS00');
UPDATE tmp_booking SET cancel_by = 'TUTOR', cancel_hours = 30 WHERE k = 'H20';
UPDATE tmp_booking SET cancel_hours = 72 WHERE k = 'H19';
UPDATE tmp_booking SET cancel_hours = 5  WHERE k = 'S05';      -- cancelacion tardia: el importe se transfiere al tutor

-- Las reservas futuras que caen en fin de semana pasan al lunes
UPDATE tmp_booking SET sdate = sdate + INTERVAL IF(WEEKDAY(sdate) >= 5, 7 - WEEKDAY(sdate), 0) DAY WHERE future = 1;
UPDATE tmp_booking SET end_t = LEAST(ADDTIME(start_t, SEC_TO_TIME(mins * 60)), '23:59:00');

-- Identificadores, importe (precio por hora de la materia) y destino de los fondos
UPDATE tmp_booking b
  JOIN users st ON st.email = b.student
  JOIN users tu ON tu.email = b.tutor
  JOIN tutor_profile tp ON tp.user_id = tu.user_id
  JOIN subjects s ON s.name = b.subject
  JOIN tutor_subject ts ON ts.tutor_profile_id = tp.tutor_profile_id AND ts.subject_id = s.subject_id
   SET b.student_id = st.user_id, b.tutor_id = tu.user_id, b.tutor_profile_id = tp.tutor_profile_id,
       b.tutor_subject_id = ts.tutor_subject_id, b.amount = ts.price_per_hour * b.mins / 60,
       b.booking_id = UUID_TO_BIN(UUID());

UPDATE tmp_booking
   SET fund = CASE WHEN status IN ('CANCELLED', 'EXPIRED') THEN NULL
                   WHEN status IN ('NOT_FULFILLED_BY_TUTOR', 'SESSION_NOT_HELD') THEN 'REFUNDED'
                   WHEN status = 'COMPLETED' AND sdate < @today - INTERVAL 2 DAY THEN 'RELEASED'
                   ELSE 'HELD' END;

-- Horario: el de un bloque de la agenda (reservas futuras) o uno propio ya ocupado (el resto)
UPDATE tmp_booking b
  JOIN time_slot ts ON ts.tutor_profile_id = b.tutor_profile_id AND ts.`date` = b.sdate
                   AND ts.start_time <= b.start_t AND ts.end_time >= b.end_t AND ts.availability_block_id IS NOT NULL
   SET b.slot_id = ts.time_slot_id
 WHERE b.future = 1;

UPDATE tmp_booking SET slot_id = UUID_TO_BIN(UUID()) WHERE future = 0;

-- Debe dar 0: si alguna reserva no encontro su materia o su bloque de agenda, el INSERT de abajo falla.
SELECT 'reservas sin horario o sin materia (debe ser 0)' AS dato, COUNT(*) AS cantidad
  FROM tmp_booking WHERE slot_id IS NULL OR tutor_subject_id IS NULL;

INSERT INTO time_slot (time_slot_id, `date`, start_time, end_time, status, tutor_profile_id, availability_block_id)
SELECT slot_id, sdate, start_t, end_t, 'OCCUPIED', tutor_profile_id, NULL
FROM tmp_booking
WHERE future = 0;

INSERT INTO booking (booking_id, created_at, amount, session_date, start_time, end_time, topic, modality,
                     virtual_session_link, confirmation_token, confirmation_token_expiration,
                     confirmation_token_attempts, confirmed_at, last_confirmation_reminder_at,
                     completion_notification_sent, booking_status, time_slot_id, tutor_subject_id, student_id, tutor_id)
SELECT b.booking_id,
       LEAST(@now - INTERVAL 2 HOUR, TIMESTAMP(b.sdate, b.start_t) - INTERVAL 3 DAY) + INTERVAL @shift HOUR,
       b.amount, b.sdate, b.start_t, b.end_t,
       ELT(1 + CRC32(b.k) MOD 5, 'Repaso para el parcial', 'Dudas del trabajo práctico', 'Preparación del final',
           'Ejercicios de la guía', 'Consulta puntual sobre el último tema'),
       b.modality,
       IF(b.modality = 'VIRTUAL', CONCAT('https://meet.google.com/seed-', LOWER(SUBSTRING(MD5(b.k), 1, 3)), '-',
                                         LOWER(SUBSTRING(MD5(b.k), 4, 4)), '-', LOWER(SUBSTRING(MD5(b.k), 8, 3))), NULL),
       NULL, NULL, 0,
       IF(b.status = 'COMPLETED', TIMESTAMP(b.sdate, b.start_t) + INTERVAL 4 MINUTE + INTERVAL @shift HOUR, NULL),
       NULL,
       IF(b.status IN ('BOOKED', 'IN_PROGRESS'), 0, 1),    -- 1 evita que el job genere otra notificacion SESSION_COMPLETED
       b.status, b.slot_id, b.tutor_subject_id, b.student_id, b.tutor_id
FROM tmp_booking b;

-- Cancelaciones: antes de 24 hs se reintegra al alumno; despues, el importe va al tutor
INSERT INTO booking_cancellation (cancellation_id, booking_id, cancelled_by, refund_destination, refund_policy,
                                  amount, hours_in_advance, created_at)
SELECT UUID_TO_BIN(UUID()), b.booking_id, b.cancel_by,
       IF(b.cancel_by = 'TUTOR' OR b.cancel_hours >= 24, 'STUDENT', 'TUTOR'),
       IF(b.cancel_by = 'TUTOR' OR b.cancel_hours >= 24, 'REFUND_TOTAL_STUDENT', 'TRANSFER_TOTAL_TUTOR'),
       b.amount, b.cancel_hours,
       TIMESTAMP(b.sdate, b.start_t) - INTERVAL b.cancel_hours HOUR + INTERVAL @shift HOUR
FROM tmp_booking b
WHERE b.status = 'CANCELLED';

-- ---------------------------------------------------------------------------
-- 9. Fondos (estadisticas del tutor y estado de cada clase)
--    El tutor recibe el importe menos el 3 % de retencion; si hay devolucion, el alumno recupera el 100 %.
-- ---------------------------------------------------------------------------
INSERT INTO funds_transfer (funds_transfer_id, booking_id, original_amount, system_retention_percentage,
                            transferred_amount, recipient, concept, funds_status, processed_at, blocking_claim_id)
SELECT UUID_TO_BIN(UUID()), b.booking_id, b.amount,
       IF(b.fund = 'REFUNDED', 0, 3),
       CASE b.fund WHEN 'RELEASED' THEN ROUND(b.amount * 0.97, 2) WHEN 'REFUNDED' THEN b.amount ELSE 0 END,
       IF(b.fund = 'REFUNDED', 'STUDENT', 'TUTOR'),
       CASE WHEN b.fund = 'RELEASED' THEN 'Liberacion por sesion completada'
            WHEN b.fund = 'REFUNDED' AND b.status = 'NOT_FULFILLED_BY_TUTOR' THEN 'Devolucion por tutor ausente'
            WHEN b.fund = 'REFUNDED' THEN 'Devolucion por sesion no realizada'
            ELSE 'Fondos retenidos para reserva' END,
       CASE b.fund WHEN 'RELEASED' THEN 'RELEASED_TO_TUTOR' WHEN 'REFUNDED' THEN 'REFUNDED_TO_STUDENT' ELSE 'HELD' END,
       IF(b.fund IN ('RELEASED', 'REFUNDED'),
          LEAST(UTC_TIMESTAMP(), TIMESTAMP(b.sdate, b.start_t) + INTERVAL 3 DAY + INTERVAL 3 HOUR), NULL),
       NULL
FROM tmp_booking b
WHERE b.fund IS NOT NULL;

-- ---------------------------------------------------------------------------
-- 10. Calificaciones (solo las visibles cuentan en las estadisticas del tutor)
-- ---------------------------------------------------------------------------
INSERT INTO rating (rating_id, booking_id, rated_user_id, rater_user_id, score, comment, rating_date, visible)
SELECT UUID_TO_BIN(UUID()), b.booking_id, b.tutor_id, b.student_id,
       ELT(1 + CRC32(b.k) MOD 6, 5, 5, 4, 5, 4, 3),
       ELT(1 + CRC32(b.k) MOD 5, 'Explica con mucha claridad y paciencia, súper recomendable.',
           'Me ayudó a destrabar los ejercicios del parcial.', NULL,
           'Muy buena clase, llegué preparado al examen.', 'Puntual y con material de apoyo muy útil.'),
       LEAST(@now_db, TIMESTAMP(b.sdate, b.end_t) + INTERVAL 90 MINUTE + INTERVAL @shift HOUR),
       IF(b.rating_mode = 'BOTH', 1, 0)
FROM tmp_booking b
WHERE b.status = 'COMPLETED' AND b.rating_mode IN ('BOTH', 'STUDENT');

INSERT INTO rating (rating_id, booking_id, rated_user_id, rater_user_id, score, comment, rating_date, visible)
SELECT UUID_TO_BIN(UUID()), b.booking_id, b.student_id, b.tutor_id,
       ELT(1 + CRC32(CONCAT(b.k, 't')) MOD 4, 5, 5, 4, 5),
       ELT(1 + CRC32(CONCAT(b.k, 't')) MOD 3, 'Alumno puntual y con las dudas bien preparadas.', NULL,
           'Muy buena predisposición para trabajar.'),
       LEAST(@now_db, TIMESTAMP(b.sdate, b.end_t) + INTERVAL 150 MINUTE + INTERVAL @shift HOUR),
       1
FROM tmp_booking b
WHERE b.status = 'COMPLETED' AND b.rating_mode = 'BOTH';

-- ---------------------------------------------------------------------------
-- 11. Reclamos de sesion ("Mis reclamos" del alumno y "Soporte y solicitudes" del tutor)
--     by_role: S = lo inicia el alumno, T = lo inicia el tutor
-- ---------------------------------------------------------------------------
DROP TEMPORARY TABLE IF EXISTS tmp_claim;
CREATE TEMPORARY TABLE tmp_claim (
  ck VARCHAR(4) PRIMARY KEY,
  bk VARCHAR(8),
  by_role CHAR(1),
  reason VARCHAR(40),
  status VARCHAR(20),
  comment VARCHAR(500),
  favored VARCHAR(10) NULL,
  justification VARCHAR(1000) NULL,
  claim_id BINARY(16) NULL,
  created DATETIME NULL,
  resolved DATETIME NULL,
  updated DATETIME NULL
);

INSERT INTO tmp_claim (ck, bk, by_role, reason, status, comment, favored, justification) VALUES
  ('C1', 'H16', 'S', 'TUTOR_COULD_NOT_ATTEND', 'OPEN',
   'La tutora no se conectó a la videollamada y no respondió mis mensajes durante la hora de la clase.', NULL, NULL),
  ('C2', 'H17', 'S', 'TUTOR_COULD_NOT_ATTEND', 'IN_REVIEW',
   'Esperé 20 minutos en la sala y nadie ingresó. Tengo captura de la espera.', NULL, NULL),
  ('C3', 'H18', 'S', 'TUTOR_COULD_NOT_ATTEND', 'RESOLVED',
   'El tutor no asistió a la sesión y no avisó con anticipación.', 'STUDENT',
   'Se verificó la ausencia del tutor en el registro de asistencia. Se devuelve el importe completo al alumno.'),
  ('C4', 'S01', 'T', 'STUDENT_COULD_NOT_ATTEND', 'OPEN',
   'El alumno no se presentó a la sesión ni avisó con anticipación.', NULL, NULL),
  ('C5', 'S02', 'S', 'TUTOR_COULD_NOT_ATTEND', 'IN_REVIEW',
   'No pude comunicarme con la tutora y la sesión se postergó sin aviso.', NULL, NULL),
  ('C6', 'S03', 'S', 'TUTOR_COULD_NOT_ATTEND', 'RESOLVED',
   'Considero que la clase no se dictó completa.', 'TUTOR',
   'El código de asistencia fue confirmado por ambas partes y la sesión se realizó. Los fondos se liberan a la tutora.');

UPDATE tmp_claim c JOIN tmp_booking b ON b.k = c.bk
   SET c.claim_id = UUID_TO_BIN(UUID()),
       c.created = LEAST(@now - INTERVAL 1 HOUR, TIMESTAMP(b.sdate, b.end_t) + INTERVAL 3 HOUR);
UPDATE tmp_claim
   SET resolved = IF(status = 'RESOLVED', LEAST(@now, created + INTERVAL 2 DAY), NULL),
       updated = IF(status = 'RESOLVED', LEAST(@now, created + INTERVAL 2 DAY), LEAST(@now, created + INTERVAL 1 DAY));

INSERT INTO session_claim (claim_id, booking_id, user_id, claimant_role, reason, comment, status, active_key,
                           dispute_id, automatic_outcome, assigned_admin_id, assigned_at, resolved_at, favored_party,
                           resolution_justification, version, created_at, updated_at)
SELECT c.claim_id, b.booking_id, IF(c.by_role = 'S', b.student_id, b.tutor_id), IF(c.by_role = 'S', 'STUDENT', 'TUTOR'),
       c.reason, c.comment, c.status,
       IF(c.status = 'RESOLVED', NULL,
          CONCAT(LOWER(BIN_TO_UUID(b.booking_id)), ':',
                 LOWER(BIN_TO_UUID(IF(c.by_role = 'S', b.student_id, b.tutor_id))))),
       c.claim_id,
       CASE c.reason WHEN 'TUTOR_COULD_NOT_ATTEND' THEN 'TUTOR_ABSENT_REFUND_TO_STUDENT'
                     WHEN 'STUDENT_COULD_NOT_ATTEND' THEN 'SESSION_COMPLETED_RELEASE_TO_TUTOR'
                     ELSE 'SESSION_NOT_HELD_REFUND_TO_STUDENT' END,
       NULL, NULL,
       IF(c.resolved IS NULL, NULL, c.resolved + INTERVAL @shift HOUR),
       c.favored, c.justification, 0,
       c.created + INTERVAL @shift HOUR, c.updated + INTERVAL @shift HOUR
FROM tmp_claim c
JOIN tmp_booking b ON b.k = c.bk;

-- Los fondos de las clases con reclamo abierto o en revision quedan suspendidos
UPDATE funds_transfer ft
  JOIN session_claim sc ON sc.booking_id = ft.booking_id AND sc.status <> 'RESOLVED'
  JOIN tmp_claim c ON c.claim_id = sc.claim_id
   SET ft.funds_status = 'SUSPENDED_BY_CLAIM', ft.blocking_claim_id = sc.claim_id
 WHERE ft.funds_status = 'HELD';

-- Conversacion dentro de los reclamos en revision (S = alumno, T = tutor)
DROP TEMPORARY TABLE IF EXISTS tmp_comment;
CREATE TEMPORARY TABLE tmp_comment (ck VARCHAR(4), author CHAR(1), message VARCHAR(500), mins INT);
INSERT INTO tmp_comment (ck, author, message, mins) VALUES
  ('C2', 'S', 'Quedo atento a la resolución. Tengo capturas de la sala vacía a la hora de la clase.', 60),
  ('C2', 'T', 'Tuve un corte de luz y avisé por el chat de la plataforma. Ofrezco reprogramar sin costo.', 300),
  ('C2', 'S', 'No recibí ningún mensaje en el chat, prefiero que se revise el reclamo.', 540),
  ('C5', 'S', 'Pido que se revise el historial del chat de la sesión.', 90),
  ('C5', 'T', 'Reprogramé por un imprevisto médico y avisé desde la sección de notificaciones.', 360);

INSERT INTO session_claim_comment (session_claim_comment_id, claim_id, author_user_id, message, created_at)
SELECT UUID_TO_BIN(UUID()), c.claim_id, IF(m.author = 'S', b.student_id, b.tutor_id), m.message,
       LEAST(@now_db, c.created + INTERVAL m.mins MINUTE + INTERVAL @shift HOUR)
FROM tmp_comment m
JOIN tmp_claim c ON c.ck = m.ck
JOIN tmp_booking b ON b.k = c.bk;

-- ---------------------------------------------------------------------------
-- 12. Material academico y denuncias ("Material denunciado" del alumno y "Soporte y solicitudes" del tutor)
-- ---------------------------------------------------------------------------
DROP TEMPORARY TABLE IF EXISTS tmp_material;
CREATE TEMPORARY TABLE tmp_material (
  mk VARCHAR(4) PRIMARY KEY,
  tutor VARCHAR(120),
  subject VARCHAR(120),
  name VARCHAR(255),
  file_name VARCHAR(255),
  size_b BIGINT,
  mtype VARCHAR(8),
  available TINYINT DEFAULT 1,
  material_id BINARY(16) NULL
);
INSERT INTO tmp_material (mk, tutor, subject, name, file_name, size_b, mtype, available) VALUES
  ('MS1', CONCAT('sofia.albarracin', @d),   'Algoritmos',           'Cheatsheet Big-O, Omega y Theta',          'cheatsheet-big-o.pdf',          214000, 'PDF',  1),
  ('MS2', CONCAT('sofia.albarracin', @d),   'Estructuras de Datos', 'Árboles y grafos - ejercicios resueltos', 'arboles-grafos-resueltos.pdf', 1850000, 'PDF',  1),
  ('MS3', CONCAT('sofia.albarracin', @d),   'Estructuras de Datos', 'Guía de pilas y colas',                    'guia-pilas-colas.pdf',          530000, 'PDF',  1),
  ('MJ1', CONCAT('julian.mendez', @d),      'Análisis Matemático',  'Tabla de derivadas e integrales',          'tabla-derivadas-integrales.pdf', 420000, 'PDF', 1),
  ('MJ2', CONCAT('julian.mendez', @d),      'Álgebra',              'Guía de autovalores y autovectores',       'guia-autovalores.pdf',          760000, 'PDF',  1),
  ('MM1', CONCAT('mateo.rivas', @d),        'Fisicoquímica',        'Resumen de termodinámica',                 'resumen-termodinamica.pdf',     1230000, 'PDF', 1),
  ('MD1', CONCAT('diego.montoya', @d),      'Redes',                'Apuntes de Redes - capa de transporte',    'apuntes-redes-transporte.pdf',  980000, 'PDF',  0),
  ('MF1', CONCAT('florencia.diaz', @d),     'Machine Learning',     'Comparativa de modelos',                   'comparativa-modelos.xlsx',      310000, 'XLSX', 1);

UPDATE tmp_material SET material_id = UUID_TO_BIN(UUID());

INSERT INTO academic_material (academic_material_id, tutor_subject_id, name, original_file_name, storage_path, file_url,
                               uploaded_at, size_in_bytes, reports_count, available, active, material_type)
SELECT m.material_id, ts.tutor_subject_id, m.name, m.file_name,
       CONCAT('materials/seed-demo/', REPLACE(UUID(), '-', ''), '-', m.file_name), NULL,
       @now_db - INTERVAL (30 + CRC32(m.mk) MOD 40) DAY, m.size_b, 0, m.available, 1, m.mtype
FROM tmp_material m
JOIN users u ON u.email = m.tutor
JOIN tutor_profile tp ON tp.user_id = u.user_id
JOIN subjects s ON s.name = m.subject
JOIN tutor_subject ts ON ts.tutor_profile_id = tp.tutor_profile_id AND ts.subject_id = s.subject_id;

-- Denuncias: rk R? = alumno denunciante. Un caso por material (PENDING / IN_REVIEW / REMOVED / DISMISSED).
DROP TEMPORARY TABLE IF EXISTS tmp_report;
CREATE TEMPORARY TABLE tmp_report (
  rk VARCHAR(4) PRIMARY KEY,
  mk VARCHAR(4),
  reporter VARCHAR(120),
  reason VARCHAR(30),
  description VARCHAR(500),
  status VARCHAR(20),
  resolution VARCHAR(20) NULL,
  days_ago INT,
  report_id BINARY(16) NULL,
  case_id BINARY(16) NULL
);
INSERT INTO tmp_report (rk, mk, reporter, reason, description, status, resolution, days_ago) VALUES
  ('RM1', 'MJ1', CONCAT('matias.velasquez', @d), 'PLAGIARISM',
   'Gran parte del contenido es idéntico a los apuntes de otra cátedra publicados en línea.', 'PENDING', NULL, 1),
  ('RM2', 'MM1', CONCAT('matias.velasquez', @d), 'FALSE_INFORMATION',
   'La fórmula del trabajo en el segundo principio tiene el signo incorrecto.', 'IN_REVIEW', NULL, 3),
  ('RM3', 'MD1', CONCAT('matias.velasquez', @d), 'INAPPROPRIATE_CONTENT',
   'El documento incluye contenido ajeno a la materia.', 'RESOLVED', 'UPHELD', 12),
  ('RM4', 'MF1', CONCAT('matias.velasquez', @d), 'OTHER',
   'El archivo no coincide con el título publicado.', 'RESOLVED', 'DISMISSED', 20),
  ('RS1', 'MS1', CONCAT('lucas.benitez', @d), 'PLAGIARISM',
   'Parece copiado de un libro sin citar la fuente.', 'PENDING', NULL, 2),
  ('RS2', 'MS2', CONCAT('martina.rossi', @d), 'FALSE_INFORMATION',
   'El ejercicio 7 tiene un error en la rotación del árbol AVL.', 'IN_REVIEW', NULL, 4),
  ('RS3', 'MS3', CONCAT('valentina.cruz', @d), 'OTHER',
   'El documento está incompleto, faltan las colas circulares.', 'RESOLVED', 'DISMISSED', 18);

UPDATE tmp_report SET report_id = UUID_TO_BIN(UUID()), case_id = UUID_TO_BIN(UUID());

INSERT INTO material_report_case (material_report_case_id, academic_material_id, status, assigned_admin_id, assigned_at,
                                  opened_at, resolved_at, resolution_note, reports_count, version)
SELECT r.case_id, m.material_id,
       CASE WHEN r.status = 'PENDING' THEN 'PENDING' WHEN r.status = 'IN_REVIEW' THEN 'IN_REVIEW'
            WHEN r.resolution = 'UPHELD' THEN 'REMOVED' ELSE 'DISMISSED' END,
       NULL, NULL,
       @now_db - INTERVAL r.days_ago DAY,
       IF(r.status = 'RESOLVED', LEAST(@now_db, @now_db - INTERVAL r.days_ago DAY + INTERVAL 4 DAY), NULL),
       CASE WHEN r.resolution = 'UPHELD' THEN 'El contenido infringía las políticas de la plataforma y fue dado de baja.'
            WHEN r.resolution = 'DISMISSED' THEN 'No se encontraron incumplimientos en el material.' ELSE NULL END,
       1, 0
FROM tmp_report r
JOIN tmp_material m ON m.mk = r.mk;

INSERT INTO academic_material_report (academic_material_report_id, academic_material_id, material_report_case_id,
                                      reporter_user_id, reviewed_by_user_id, reason, description, reported_at, status,
                                      resolution)
SELECT r.report_id, m.material_id, r.case_id, u.user_id, NULL, r.reason, r.description,
       @now_db - INTERVAL r.days_ago DAY, r.status, r.resolution
FROM tmp_report r
JOIN tmp_material m ON m.mk = r.mk
JOIN users u ON u.email = r.reporter;

UPDATE academic_material am
   SET am.reports_count = (SELECT COUNT(*) FROM academic_material_report x
                            WHERE x.academic_material_id = am.academic_material_id)
 WHERE am.storage_path LIKE 'materials/seed-demo/%';

-- Descargo de Sofia ante la denuncia en revision de su material
INSERT INTO academic_material_defense (academic_material_defense_id, academic_material_id, tutor_user_id, description,
                                       submitted_at)
SELECT UUID_TO_BIN(UUID()), m.material_id, u.user_id,
       'El ejercicio sigue la notación de la cátedra; adjunto la bibliografía de referencia. Si se confirma el error en la rotación del árbol, lo corrijo y vuelvo a subir el archivo.',
       @now_db - INTERVAL 2 DAY
FROM tmp_material m
JOIN users u ON u.email = m.tutor
WHERE m.mk = 'MS2';

-- ---------------------------------------------------------------------------
-- 13. Notificaciones (todos los tipos, leidas y sin leer)
--     kind: B = reserva, C = reclamo, R = denuncia de material, S = materia del tutor (ref = nombre de la materia)
-- ---------------------------------------------------------------------------
DROP TEMPORARY TABLE IF EXISTS tmp_notif;
CREATE TEMPORARY TABLE tmp_notif (
  nk VARCHAR(4) PRIMARY KEY,
  recipient VARCHAR(120),
  role VARCHAR(10),
  type VARCHAR(40),
  kind CHAR(1),
  ref VARCHAR(40) NULL,
  title VARCHAR(120),
  message VARCHAR(500),
  hours_ago INT,
  unread TINYINT,
  res BINARY(16) NULL
);

INSERT INTO tmp_notif (nk, recipient, role, type, kind, ref, title, message, hours_ago, unread) VALUES
  -- Matias (alumno)
  ('N01', CONCAT('matias.velasquez', @d), 'STUDENT', 'BOOKING_CONFIRMED', 'B', 'H01', 'Reserva confirmada',
   'Tu clase de Algoritmos con Lic. Sofía Albarracín fue confirmada.', 2, 1),
  ('N02', CONCAT('matias.velasquez', @d), 'STUDENT', 'SESSION_CONFIRMATION_REMINDER', 'B', 'H01', 'Recordatorio de tu clase',
   'Tenés una clase de Algoritmos con Lic. Sofía Albarracín. Recordá confirmar tu asistencia con el código de la sesión.', 5, 1),
  ('N03', CONCAT('matias.velasquez', @d), 'STUDENT', 'RATING_ENABLED', 'B', 'H12', 'Calificá tu sesión',
   'Ya podés calificar tu sesión de Álgebra con Prof. Julián Méndez.', 28, 1),
  ('N04', CONCAT('matias.velasquez', @d), 'STUDENT', 'SESSION_COMPLETED', 'B', 'H12', 'Sesión finalizada',
   'Tu sesión de Álgebra finalizó. Si hubo algún inconveniente, podés iniciar un reclamo.', 29, 0),
  ('N05', CONCAT('matias.velasquez', @d), 'STUDENT', 'SESSION_CLAIM_UPDATE', 'C', 'C2', 'Tu reclamo está en revisión',
   'Un mediador está revisando tu reclamo por la sesión de Base de Datos.', 20, 1),
  ('N06', CONCAT('matias.velasquez', @d), 'STUDENT', 'SESSION_CLAIM_COMMENT', 'C', 'C2', 'Nuevo comentario en tu reclamo',
   'Diego Montoya comentó en el reclamo por tu sesión de Base de Datos.', 30, 1),
  ('N07', CONCAT('matias.velasquez', @d), 'STUDENT', 'FUNDS_SUSPENDED', 'B', 'H16', 'Fondos suspendidos por reclamo',
   'Los fondos de tu sesión de Machine Learning quedan retenidos hasta que se resuelva el reclamo.', 90, 0),
  ('N08', CONCAT('matias.velasquez', @d), 'STUDENT', 'SESSION_NOT_CONFIRMED', 'B', 'H16', 'Sesión no confirmada',
   'La sesión de Machine Learning con Florencia Díaz no fue confirmada por ambas partes.', 94, 0),
  ('N09', CONCAT('matias.velasquez', @d), 'STUDENT', 'FUNDS_TRANSFERRED', 'B', 'H18', 'Devolución acreditada',
   'Se te devolvieron $3300.00 por la sesión de Fisicoquímica (tutor ausente).', 300, 0),
  ('N10', CONCAT('matias.velasquez', @d), 'STUDENT', 'SESSION_CLAIM_UPDATE', 'C', 'C3', 'Tu reclamo fue resuelto',
   'Tu reclamo por la sesión de Fisicoquímica fue resuelto a tu favor.', 310, 0),
  ('N11', CONCAT('matias.velasquez', @d), 'STUDENT', 'BOOKING_CANCELLED', 'B', 'H20', 'Clase cancelada',
   'Lic. Sofía Albarracín canceló la clase de Programación. Se te reintegró el importe.', 70, 0),
  ('N12', CONCAT('matias.velasquez', @d), 'STUDENT', 'MATERIAL_REPORT_UPDATE', 'R', 'RM2', 'Tu denuncia está en revisión',
   'Un administrador está revisando el material "Resumen de termodinámica".', 40, 1),
  ('N13', CONCAT('matias.velasquez', @d), 'STUDENT', 'MATERIAL_REPORT_PROCESSED', 'R', 'RM3', 'Denuncia procesada',
   'El material "Apuntes de Redes - capa de transporte" fue dado de baja. Gracias por tu aporte.', 200, 0),
  -- Sofia (tutora)
  ('N21', CONCAT('sofia.albarracin', @d), 'TUTOR', 'BOOKING_CONFIRMED', 'B', 'H04', 'Nueva reserva',
   'Lucas Benítez reservó una clase de Estructuras de Datos.', 3, 1),
  ('N22', CONCAT('sofia.albarracin', @d), 'TUTOR', 'BOOKING_CONFIRMED', 'B', 'H01', 'Nueva reserva',
   'Matías Velasquez reservó una clase de Algoritmos.', 2, 1),
  ('N23', CONCAT('sofia.albarracin', @d), 'TUTOR', 'SESSION_COMPLETED', 'B', 'S04', 'Sesión finalizada',
   'Tu sesión de Estructuras de Datos con Agustina Luna finalizó.', 20, 1),
  ('N24', CONCAT('sofia.albarracin', @d), 'TUTOR', 'RATING_ENABLED', 'B', 'S04', 'Calificá a tu alumno',
   'Ya podés calificar tu sesión de Estructuras de Datos con Agustina Luna.', 19, 1),
  ('N25', CONCAT('sofia.albarracin', @d), 'TUTOR', 'BOOKING_CANCELLED', 'B', 'S05', 'Clase cancelada',
   'Camila Torres canceló la clase de Estructuras de Datos con poca anticipación: el importe se transfiere a tu cuenta.', 45, 0),
  ('N26', CONCAT('sofia.albarracin', @d), 'TUTOR', 'FUNDS_SUSPENDED', 'B', 'S02', 'Fondos suspendidos por reclamo',
   'Los fondos de tu sesión de Programación quedan retenidos hasta que se resuelva el reclamo.', 200, 0),
  ('N27', CONCAT('sofia.albarracin', @d), 'TUTOR', 'SESSION_CLAIM_UPDATE', 'C', 'C5', 'Nuevo reclamo de sesión',
   'Se inició un reclamo por tu sesión de Programación.', 200, 1),
  ('N28', CONCAT('sofia.albarracin', @d), 'TUTOR', 'SESSION_CLAIM_COMMENT', 'C', 'C5', 'Nuevo comentario en un reclamo',
   'Valentina Cruz comentó en el reclamo por tu sesión de Programación.', 150, 1),
  ('N29', CONCAT('sofia.albarracin', @d), 'TUTOR', 'FUNDS_TRANSFERRED', 'B', 'S03', 'Fondos acreditados',
   'Se acreditaron $3395.00 por tu sesión de Algoritmos (liberación por sesión completada).', 500, 0),
  ('N30', CONCAT('sofia.albarracin', @d), 'TUTOR', 'SESSION_CLAIM_UPDATE', 'C', 'C6', 'Reclamo resuelto',
   'El reclamo por tu sesión de Algoritmos se resolvió a tu favor.', 520, 0),
  ('N31', CONCAT('sofia.albarracin', @d), 'TUTOR', 'MATERIAL_REPORT_UPDATE', 'R', 'RS2', 'Denuncia sobre tu material',
   'El material "Árboles y grafos - ejercicios resueltos" fue denunciado y está en revisión. Podés enviar tu descargo.', 60, 1),
  ('N32', CONCAT('sofia.albarracin', @d), 'TUTOR', 'MATERIAL_REPORT_PROCESSED', 'R', 'RS3', 'Denuncia desestimada',
   'La denuncia sobre "Guía de pilas y colas" fue desestimada.', 400, 0),
  ('N33', CONCAT('sofia.albarracin', @d), 'TUTOR', 'SUBJECT_VALIDATION_UPDATED', 'S', 'Programación', 'Materia aprobada',
   'Tu materia Programación fue aprobada y ya aparece en tu perfil.', 600, 0),
  ('N34', CONCAT('sofia.albarracin', @d), 'TUTOR', 'SESSION_CONFIRMATION_REMINDER', 'B', 'H04', 'Recordatorio de clase',
   'Tenés una clase próxima de Estructuras de Datos con Lucas Benítez.', 1, 1),
  -- Julian y Luciana (tutores)
  ('N41', CONCAT('julian.mendez', @d), 'TUTOR', 'BOOKING_CONFIRMED', 'B', 'H02', 'Nueva reserva',
   'Matías Velasquez reservó una clase de Física.', 4, 1),
  ('N42', CONCAT('julian.mendez', @d), 'TUTOR', 'FUNDS_TRANSFERRED', 'B', 'GJ02', 'Fondos acreditados',
   'Se acreditaron $2910.00 por tu sesión de Física (liberación por sesión completada).', 100, 0),
  ('N51', CONCAT('luciana.gomez', @d), 'TUTOR', 'BOOKING_CONFIRMED', 'B', 'H08', 'Nueva reserva',
   'Agustina Luna reservó una clase de Programación.', 6, 1),
  ('N52', CONCAT('luciana.gomez', @d), 'TUTOR', 'SESSION_COMPLETED', 'B', 'H13', 'Sesión finalizada',
   'Tu sesión de Programación con Matías Velasquez finalizó.', 210, 0);

UPDATE tmp_notif n JOIN tmp_booking b ON n.kind = 'B' AND b.k = n.ref SET n.res = b.booking_id;
UPDATE tmp_notif n JOIN tmp_claim c ON n.kind = 'C' AND c.ck = n.ref SET n.res = c.claim_id;
UPDATE tmp_notif n JOIN tmp_report r ON n.kind = 'R' AND r.rk = n.ref SET n.res = r.report_id;
UPDATE tmp_notif n
  JOIN users u ON u.email = n.recipient
  JOIN tutor_profile tp ON tp.user_id = u.user_id
  JOIN subjects s ON s.name = n.ref
  JOIN tutor_subject ts ON ts.tutor_profile_id = tp.tutor_profile_id AND ts.subject_id = s.subject_id
   SET n.res = ts.tutor_subject_id
 WHERE n.kind = 'S';

INSERT INTO user_notification (notification_id, recipient_user_id, recipient_role, notification_type, resource_id,
                               title, message, dedupe_key, created_at, read_at, expires_at)
SELECT UUID_TO_BIN(UUID()), u.user_id, n.role, n.type, n.res, n.title, n.message, CONCAT('seed-demo:', n.nk),
       @now_db - INTERVAL n.hours_ago HOUR,
       IF(n.unread = 1, NULL, @now_db - INTERVAL GREATEST(n.hours_ago - 1, 0) HOUR),
       IF(n.type = 'RATING_ENABLED', @now_db - INTERVAL n.hours_ago HOUR + INTERVAL 7 DAY, NULL)
FROM tmp_notif n
JOIN users u ON u.email = n.recipient;

-- ---------------------------------------------------------------------------
-- 14. Resumen de las secciones del menu
-- ---------------------------------------------------------------------------
SELECT 'reservas definidas / creadas' AS dato,
       CONCAT((SELECT COUNT(*) FROM tmp_booking), ' / ',
              (SELECT COUNT(*) FROM booking b JOIN users u ON u.user_id = b.student_id
                WHERE u.email LIKE '%@comunidad.knowlink.test')) AS cantidad
UNION ALL
SELECT CONCAT('   ', b.booking_status), COUNT(*)
  FROM booking b JOIN users u ON u.user_id = b.student_id
 WHERE u.email LIKE '%@comunidad.knowlink.test' GROUP BY b.booking_status
UNION ALL
SELECT 'calificaciones (visibles)', CONCAT(COUNT(*), ' (', SUM(r.visible), ')')
  FROM rating r JOIN users u ON u.user_id = r.rater_user_id WHERE u.email LIKE '%@comunidad.knowlink.test'
UNION ALL
SELECT 'fondos de las reservas', COUNT(*)
  FROM funds_transfer f JOIN booking b ON b.booking_id = f.booking_id JOIN users u ON u.user_id = b.student_id
 WHERE u.email LIKE '%@comunidad.knowlink.test'
UNION ALL
SELECT CONCAT('reclamos ', c.status), COUNT(*)
  FROM session_claim c JOIN tmp_claim t ON t.claim_id = c.claim_id GROUP BY c.status
UNION ALL
SELECT CONCAT('denuncias de material ', r.status), COUNT(*)
  FROM academic_material_report r JOIN tmp_report t ON t.report_id = r.academic_material_report_id GROUP BY r.status
UNION ALL
SELECT 'bloques de agenda futuros', COUNT(*)
  FROM availability_block b JOIN tutor_profile tp ON tp.tutor_profile_id = b.tutor_profile_id
  JOIN users u ON u.user_id = tp.user_id WHERE u.email LIKE '%@comunidad.knowlink.test'
UNION ALL
SELECT 'notificaciones del menu (sin leer)', CONCAT(COUNT(*), ' (', SUM(IF(read_at IS NULL, 1, 0)), ')')
  FROM user_notification WHERE dedupe_key LIKE 'seed-demo:%';

-- ---------------------------------------------------------------------------
-- 15. Resumen de la comunidad
-- ---------------------------------------------------------------------------
SELECT 'publicaciones vivas' AS dato, COUNT(*) AS cantidad
  FROM community_post p JOIN users u ON u.user_id = p.author_id
 WHERE u.email LIKE '%@comunidad.knowlink.test' AND p.deleted_at IS NULL
UNION ALL
SELECT CONCAT('   ', p.post_type, ' / ', p.post_status), COUNT(*)
  FROM community_post p JOIN users u ON u.user_id = p.author_id
 WHERE u.email LIKE '%@comunidad.knowlink.test' AND p.deleted_at IS NULL
 GROUP BY p.post_type, p.post_status
UNION ALL
SELECT 'respuestas vivas', COUNT(*)
  FROM community_reply r JOIN community_post p ON p.post_id = r.post_id JOIN users u ON u.user_id = p.author_id
 WHERE u.email LIKE '%@comunidad.knowlink.test' AND r.deleted_at IS NULL
UNION ALL
SELECT 'marcas "Esto me sirvio"', COUNT(*)
  FROM community_reply_helpful h JOIN community_reply r ON r.reply_id = h.reply_id
  JOIN community_post p ON p.post_id = r.post_id JOIN users u ON u.user_id = p.author_id
 WHERE u.email LIKE '%@comunidad.knowlink.test'
UNION ALL
SELECT 'notificaciones COMMUNITY_REPLY', COUNT(*)
  FROM user_notification n JOIN users u ON u.user_id = n.recipient_user_id
 WHERE n.notification_type = 'COMMUNITY_REPLY' AND u.email LIKE '%@comunidad.knowlink.test';

-- Debe coincidir con las publicaciones y respuestas definidas arriba: si falta alguna, el nombre de una
-- carrera o materia no existe en tu catalogo.
SELECT 'publicaciones definidas' AS dato, COUNT(*) AS cantidad FROM tmp_post
UNION ALL SELECT 'respuestas definidas', COUNT(*) FROM tmp_reply;
