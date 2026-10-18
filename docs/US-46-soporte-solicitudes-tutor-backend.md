# US-46 — Soporte y solicitudes como tutor (backend)

## Alcance implementado

La API da soporte a las pestañas **Reclamos de sesiones** y **Materiales denunciados**. La pestaña
«Validaciones» del prototipo queda fuera de este alcance, según la decisión confirmada para esta
implementación.

Los reclamos usan los estados `OPEN`, `IN_REVIEW` y `RESOLVED`, que se presentan como «Abierto»,
«En revisión» y «Resuelto». Se conserva `OPEN` para el estado inicial del endpoint de reclamos ya
existente. Las denuncias individuales usan `PENDING`, `IN_REVIEW` y `RESOLVED`.

## Endpoints

| Método | Ruta | Acceso | Descripción |
|---|---|---|---|
| `GET` | `/api/v1/tutors/support` | Tutor | Devuelve `sessionClaims` y `reportedMaterials`; al consultarlo se marcan como leídas las novedades de soporte. |
| `GET` | `/api/v1/tutors/support/material-policy` | Tutor | Devuelve la normativa y los límites de evidencia para el modal de política. |
| `GET` | `/api/v1/tutors/support/session-claims/{claimId}` | Tutor propietario | Detalle del reclamo, fondos, evidencia firmada y progreso de revisión. |
| `POST` | `/api/v1/tutors/support/session-claims/{claimId}/comments` | Tutor propietario | Agrega un comentario al historial mientras el reclamo siga activo. |
| `GET` | `/api/v1/tutors/support/materials/{materialId}` | Tutor propietario | Detalle del archivo, denuncias, auditor y descargo presentado. |
| `POST` | `/api/v1/tutors/support/materials/{materialId}/defense` | Tutor propietario | Presenta explicación y evidencias (multipart `request` + `files`). |
| `GET` | `/api/v1/notifications` | Usuario autenticado | Lista sus notificaciones. |
| `GET` | `/api/v1/notifications/unread-count` | Usuario autenticado | Devuelve `unreadCount` global de notificaciones no leídas de todos los módulos. |
| `GET` | `/api/v1/notifications/support/unread-count` | Usuario autenticado | Devuelve `unreadCount` de casos de soporte con novedades no leídas, agrupando eventos por reclamo/material. |
| `PATCH` | `/api/v1/notifications/{notificationId}/read` | Usuario autenticado | Marca una notificación propia como leída. |
| `PATCH` | `/api/v1/notifications/read-all` | Usuario autenticado | Marca todas sus notificaciones como leídas. |
| `PATCH` | `/api/v1/admin/support/session-claims/{claimId}` | Admin | Cambia el estado del reclamo. |
| `GET` | `/api/v1/admin/support/materials/{materialId}` | Admin | Consulta denuncias, archivo y descargo para revisar el caso. |
| `PATCH` | `/api/v1/admin/support/material-reports/{reportId}` | Admin | Cambia estado; al resolver exige `resolution=UPHELD` o `DISMISSED` y registra al revisor. |

El tutor crea reclamos con el endpoint existente
`POST /api/v1/bookings/{bookingId}/claims`; la sesión debe cumplir las reglas de elegibilidad de
US-51. Las denuncias de materiales se agrupan por material, muestran el total de denuncias y el
estado agregado: «En revisión» si alguna está en revisión; si no, «Pendiente» si alguna sigue
pendiente; de lo contrario, «Resuelto». Se conservan también los materiales con denuncias resueltas,
para que el tutor pueda consultar su resultado.

Las colecciones vacías se devuelven como listas vacías; el cliente presenta «Sin registros por ahora».

El listado agrega un resumen con cantidad de reclamos, materiales denunciados y materiales en revisión.
El badge de novedades de esta sección usa `GET /api/v1/notifications/support/unread-count`; el endpoint sin `/support`
cuenta también las notificaciones de otros módulos. Cada fila incluye los datos necesarios para identificar el caso
(fechas y horario de sesión; archivo, formato, tamaño, materia, fecha de subida y disponibilidad).
Las notificaciones de soporte se marcan como vistas al abrir la sección; las notificaciones de otras
secciones no se modifican.

El tutor puede abrir el detalle de un reclamo para ver identidad y carrera del alumno, materia y
carrera asociada, motivo, declaración inicial, adjuntos con URL firmada, fondos protegidos y avance
de revisión. Puede agregar comentarios mientras el reclamo siga activo; el alumno recibe una
notificación. La cola informa posición entre reclamos abiertos y vencimiento de respuesta calculado
con `knowlink.support.claim-response-hours` (default 24 horas).

El detalle de un material incluye sus denuncias individuales (motivo, descripción, fecha, estado y
resultado), el revisor asignado si ya comenzó la revisión, un enlace firmado de lectura del archivo y
el estado del descargo del tutor. El tutor puede presentar una explicación y hasta cinco evidencias
PDF/PNG/XLSX de hasta 10 MB por archivo. Las evidencias se guardan en el bucket existente `materials`,
bajo `material-defense/{materialId}/`.

`GET /api/v1/tutors/support/material-policy` devuelve formatos, motivos, límites y el texto de la
normativa mostrado en el prototipo.

## Retención y estados

`OPEN` e `IN_REVIEW` se consideran estados activos: bloquean un segundo reclamo del mismo usuario
para esa sesión y evitan que US-15 libere o devuelva los fondos. Al resolver un reclamo se limpia su
`active_key`, permitiendo un reclamo posterior si la sesión vuelve a ser elegible. La resolución
económica final sigue el flujo administrativo de fondos existente.

Los cambios de estado son progresivos: reclamos `OPEN → IN_REVIEW → RESOLVED` (también se permite
resolver directamente desde `OPEN`) y denuncias `PENDING → IN_REVIEW → RESOLVED` (también se permite
resolver directamente desde `PENDING`). Los cambios repetidos al mismo estado son idempotentes;
no se permiten transiciones hacia atrás.

Al recibirse la primera denuncia, el material se suspende preventivamente (`available=false`) para los
alumnos. Otros alumnos con acceso todavía pueden denunciarlo durante esa revisión; sus reportes se
consolidan en el mismo caso. Al resolver cada denuncia, el administrador informa `resolution=UPHELD` o `DISMISSED`.
Cuando todas las denuncias del material están resueltas, queda fuera de plataforma si alguna fue
confirmada; si todas se desestimaron, vuelve a estar disponible. Los alumnos no pueden listar ni
descargar material suspendido; el tutor propietario conserva el acceso para preparar su descargo.

## Notificaciones y compatibilidad de datos

Se persisten notificaciones asociadas a cada usuario. Se genera una novedad para la contraparte al
iniciarse un reclamo y para los participantes ante cambios de estado; los comentarios del tutor
notifican al alumno. Al resolverse una denuncia, reciben una notificación tanto el tutor como el
alumno que la realizó. El contador agrupa notificaciones no leídas por tipo y elemento, por lo que
varias actualizaciones del mismo reclamo/material cuentan como un solo ítem. Al consultar
`/api/v1/tutors/support`, las notificaciones de soporte se consideran vistas.

No hay Flyway/Liquibase en el proyecto. Los campos nuevos de estado de denuncias admiten `NULL` para
compatibilidad con filas preexistentes; al leerlas se interpretan como `PENDING`. Las nuevas
denuncias se crean explícitamente en `PENDING`. La tabla de notificaciones se crea con el mecanismo
de esquema configurado por Spring/JPA.
