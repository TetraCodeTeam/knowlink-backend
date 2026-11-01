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
-- Es re-ejecutable: borra y vuelve a crear solo lo que pertenece a los usuarios @comunidad.knowlink.test
-- (sus publicaciones, las respuestas y marcas que recibieron, y las notificaciones asociadas) y el ranking
-- del mes en curso, para que se regenere. No toca nada de otros usuarios.
--
-- Notas:
--   * Las fechas son relativas al momento de ejecucion y estan en hora de Cordoba (UTC-3). Como el JDBC URL del
--     backend usa serverTimezone=UTC, se guardan corridas segun la diferencia entre la hora local del servidor
--     MySQL y UTC (ver @shift abajo): asume que MySQL y el backend corren en la misma maquina.
--   * Los filtros "esta semana" (desde el lunes) y "este mes" dependen del dia en que se corra el seed: si es
--     lunes, "esta semana" coincide con hoy; si es dia 1, "este mes" coincide con hoy.
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
SET @week_start = @today - INTERVAL WEEKDAY(@today) DAY;           -- lunes de esta semana
SET @month_start = DATE_FORMAT(@today, '%Y-%m-01');                -- primer dia del mes
SET @pwd = '$2a$10$mIlh0.lvtcVeoAM/TX4nQeeL7NvVzaEMmdy/CtdszS8bMvH.7n7V6';  -- Password1!

-- hoy
SET @t0a = GREATEST(@now - INTERVAL 25 MINUTE, TIMESTAMP(@today));
SET @t0b = GREATEST(@now - INTERVAL 3 HOUR, TIMESTAMP(@today));
SET @t0c = GREATEST(@now - INTERVAL 9 HOUR, TIMESTAMP(@today));
-- esta semana (desde el lunes)
SET @w1 = GREATEST(@now - INTERVAL 1 DAY, TIMESTAMP(@week_start));
SET @w2 = GREATEST(@now - INTERVAL 2 DAY, TIMESTAMP(@week_start));
SET @w3 = GREATEST(@now - INTERVAL 4 DAY, TIMESTAMP(@week_start));
-- este mes
SET @m1 = GREATEST(@now - INTERVAL 9 DAY, TIMESTAMP(@month_start));
SET @m2 = GREATEST(@now - INTERVAL 14 DAY, TIMESTAMP(@month_start));
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

-- ---------------------------------------------------------------------------
-- 7. Resumen
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
