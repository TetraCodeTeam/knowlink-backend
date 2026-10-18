# US-46 — Contrato de integración para frontend

Este documento describe los endpoints y reglas que consume la pantalla **Soporte y solicitudes** del tutor. Las rutas y campos reflejan los controladores y DTOs del backend. Todas las rutas requieren `Authorization: Bearer <token>`; las rutas de tutor exigen rol `TUTOR` y las administrativas, `ADMIN`.

La pestaña **Validaciones** del prototipo no está incluida en esta US. Las respuestas usan nombres de campos y enums en inglés; el front traduce los códigos a las etiquetas en español indicadas acá.

## 1. Endpoints

| Método | Ruta | Rol | Uso |
|---|---|---|---|
| `GET` | `/api/v1/notifications/support/unread-count` | Autenticado | Badge de novedades no leídas de soporte. |
| `GET` | `/api/v1/tutors/support` | Tutor | Carga resumen, reclamos y materiales denunciados. Al consultarlo, marca como leídas las novedades de soporte. |
| `GET` | `/api/v1/tutors/support/material-policy` | Tutor | Contenido y límites del modal de normativa. |
| `GET` | `/api/v1/tutors/support/session-claims/{claimId}` | Tutor dueño del reclamo | Detalle, fondos, progreso, adjuntos y comentarios. |
| `POST` | `/api/v1/tutors/support/session-claims/{claimId}/comments` | Tutor dueño del reclamo | Agrega un comentario. |
| `GET` | `/api/v1/tutors/support/materials/{materialId}` | Tutor dueño del material | Detalle de denuncias, revisión y descargo. |
| `POST` | `/api/v1/tutors/support/materials/{materialId}/defense` | Tutor dueño del material | Envía descargo y evidencias como `multipart/form-data`. |
| `GET` | `/api/v1/bookings/{bookingId}/claims/eligibility` | Participante autenticado | Consulta disponibilidad y motivos de reclamo antes de mostrar el formulario. |
| `POST` | `/api/v1/bookings/{bookingId}/claims` | Participante autenticado | Crea un reclamo de sesión (`multipart/form-data`). |
| `GET` | `/api/v1/notifications` | Autenticado | Lista notificaciones del usuario. |
| `GET` | `/api/v1/notifications/unread-count` | Autenticado | Badge global de todos los módulos; no usar como contador exclusivo de soporte. |
| `PATCH` | `/api/v1/notifications/{notificationId}/read` | Autenticado | Marca una notificación propia como leída. |
| `PATCH` | `/api/v1/notifications/read-all` | Autenticado | Marca todas las notificaciones propias como leídas. |
| `POST` | `/api/v1/materials/{materialId}/reports` | Alumno | Registra una denuncia de material. |
| `GET` | `/api/v1/admin/support/materials/{materialId}` | Admin | Consulta el caso de un material denunciado por ID. |
| `PATCH` | `/api/v1/admin/support/session-claims/{claimId}` | Admin | Avanza el estado de un reclamo. |
| `PATCH` | `/api/v1/admin/support/material-reports/{reportId}` | Admin | Avanza el estado de una denuncia y registra su resolución. |

Las rutas administrativas disponibles en esta US consultan y actualizan casos por ID; no se agrega una ruta para listar la cola administrativa.

## 2. Flujo de la pantalla del tutor

1. Antes de cargar la sección, consultar `GET /api/v1/notifications/support/unread-count` para mostrar el badge. Devuelve `{ "unreadCount": 2 }`.
2. Consultar `GET /api/v1/tutors/support`. El endpoint marca como leídas las notificaciones de soporte (`SESSION_CLAIM_UPDATE`, `SESSION_CLAIM_COMMENT`, `MATERIAL_REPORT_UPDATE`); por eso el contador se consulta antes de abrir/cargar la sección.
3. Mostrar `sessionClaims` en la pestaña Reclamos y `reportedMaterials` en Materiales denunciados. Ambas colecciones pueden venir vacías (`[]`).
4. Al seleccionar un reclamo o material, consultar su endpoint de detalle con el ID de la fila.
5. Actualizar la vista después de un comentario o descargo con la respuesta de la mutación, o volver a consultar el detalle.

El contador específico agrupa varias notificaciones del mismo caso en una sola novedad: los eventos `SESSION_CLAIM_UPDATE` y `SESSION_CLAIM_COMMENT` comparten el recurso reclamo; `MATERIAL_REPORT_UPDATE` agrupa por material. El endpoint global `/notifications/unread-count` incluye otros módulos.

## 3. Respuesta del listado de soporte

`GET /api/v1/tutors/support` devuelve:

```json
{
  "summary": {
    "sessionClaimCount": 3,
    "reportedMaterialCount": 2,
    "materialsInReviewCount": 1
  },
  "sessionClaims": [
    {
      "claimId": "<uuid>",
      "bookingId": "<uuid>",
      "subjectName": "Análisis Matemático 2",
      "studentName": "Bruno Peralta",
      "sessionDate": "2026-07-15",
      "sessionStartTime": "18:00:00",
      "sessionEndTime": "19:00:00",
      "reason": "STUDENT_COULD_NOT_ATTEND",
      "status": "OPEN",
      "createdAt": "2026-07-15T19:05:00",
      "updatedAt": "2026-07-15T19:05:00"
    }
  ],
  "reportedMaterials": [
    {
      "materialId": "<uuid>",
      "name": "Guía de integrales",
      "originalFileName": "guia.pdf",
      "format": "PDF",
      "sizeInBytes": 1887436,
      "subjectName": "Análisis Matemático 2",
      "uploadedAt": "2026-07-10T16:42:00",
      "available": false,
      "reportCount": 2,
      "status": "PENDING",
      "lastReportedAt": "2026-07-19T14:10:00"
    }
  ]
}
```

`sessionClaimCount` cuenta reclamos del tutor en todos sus estados. `reportedMaterialCount` cuenta materiales distintos denunciados, no denuncias individuales. `materialsInReviewCount` cuenta materiales cuyo estado agregado es `IN_REVIEW`.

El estado agregado de un material es `IN_REVIEW` si alguna denuncia está en revisión; si no, `PENDING` si queda alguna pendiente; si todas están resueltas, `RESOLVED`.

## 4. Reclamos de sesión

### Elegibilidad y alta

`GET /api/v1/bookings/{bookingId}/claims/eligibility` devuelve `canClaim`, `claimableUntil`, `blockReason` y `allowedReasons`. Si `canClaim` es `false`, mostrar el estado indicado y no permitir el envío. `allowedReasons` depende del rol:

| Rol | Código | Etiqueta sugerida |
|---|---|---|
| Alumno | `TUTOR_COULD_NOT_ATTEND` | El tutor no pudo asistir |
| Alumno | `I_COULD_NOT_ATTEND` | Yo no pude asistir |
| Tutor | `STUDENT_COULD_NOT_ATTEND` | El alumno no pudo asistir |
| Tutor | `I_COULD_NOT_ATTEND` | Yo no pude asistir |

`POST /api/v1/bookings/{bookingId}/claims` recibe multipart con una parte JSON `request` (`reason` obligatorio, `comment` opcional de hasta 500 caracteres) y cero o más partes `files`. En FormData se repite la clave `files` por cada archivo. Se aceptan hasta 3 archivos, de hasta 5 MB cada uno: PDF, JPG/JPEG o PNG; validar extensión y MIME. El backend valida de nuevo todo, incluso si el front deshabilita el botón.

Reglas de elegibilidad: el usuario debe participar de la sesión, esta debe haber terminado según el horario, no haber sido confirmada con token, estar en un estado que admita reclamos y dentro de las 24 horas desde el fin programado. El usuario no puede tener otro reclamo activo para esa sesión. Los fondos asociados quedan suspendidos mientras el reclamo esté activo.

Ejemplo de envío: agregar `request` como un `Blob` con tipo `application/json`; agregar cada evidencia con la misma clave `files`.

```javascript
const formData = new FormData();
formData.append("request", new Blob([JSON.stringify({ reason, comment })], { type: "application/json" }));
files.forEach((file) => formData.append("files", file));
```

### Estados

| Código | Etiqueta | Transiciones administrativas permitidas |
|---|---|---|
| `OPEN` | Abierto / Pendiente | `IN_REVIEW` o `RESOLVED` |
| `IN_REVIEW` | En revisión | `RESOLVED` |
| `RESOLVED` | Resuelto | Sin transiciones posteriores |

La actualización administrativa envía `PATCH /api/v1/admin/support/session-claims/{claimId}` con `{"status":"IN_REVIEW"}` o `{"status":"RESOLVED"}`. Repetir el estado actual es idempotente. Este endpoint cambia el estado; la resolución económica de fondos sigue el flujo administrativo existente.

### Detalle del reclamo

`GET /api/v1/tutors/support/session-claims/{claimId}` incluye:

- `student`: `fullName`, `profilePictureUrl`, `careerName`.
- Materia/carrera (`subjectName`, `careerName`), sesión (`sessionDate`, `sessionStartTime`, `sessionEndTime`), `reason`, `status`, rol/nombre del reclamante, `declaration`, `createdAt` y `updatedAt`.
- `funds`: `originalAmount`, `protectedAmount`, `protectedPercentage`, `status`, `currency`. `status` puede ser `null` si la sesión no tiene transferencia asociada.
- `reviewProgress`: `currentPhase` (`MEDIATOR_ASSIGNMENT_QUEUED`, `IN_REVIEW` o `RESOLVED`), `queuePosition` (solo se informa mientras esté `OPEN`), `responseDueAt` y `remainingMinutes`.
- `attachments`: lista de `{id, fileName, contentType, sizeBytes, signedUrl}`.
- `comments`: lista cronológica de `{id, authorName, authorRole, comment, createdAt}`.

`POST /api/v1/tutors/support/session-claims/{claimId}/comments` recibe `{"comment":"..."}`. Es obligatorio, se recortan espacios y admite hasta 2000 caracteres. El reclamo debe pertenecer al tutor y no estar resuelto; si ya fue resuelto responde `409` con `detail: "CLAIM_ALREADY_RESOLVED"`. La respuesta es el comentario creado; el alumno recibe una notificación.

## 5. Materiales denunciados

El descargo admite hasta 5 archivos de 10 MB cada uno y el servidor permite solicitudes multipart de hasta 60 MB en total.

### Detalle y descargo

`GET /api/v1/tutors/support/materials/{materialId}` devuelve los campos de la fila más `careerName`, `previewUrl` firmado, `reviewTeamName`, `reviewerName`, `reports` y `defense`.

Cada reporte incluye `reportId`, `reason`, `description`, `reportedAt`, `status` y `resolution`. `defense` incluye `submitted`, `description`, `submittedAt` y `attachments` (`attachmentId`, `fileName`, `contentType`, `sizeInBytes`, `signedUrl`). `previewUrl` y las URLs firmadas de evidencias duran una hora; pedir de nuevo el detalle para obtener enlaces nuevos.

`POST /api/v1/tutors/support/materials/{materialId}/defense` recibe `multipart/form-data`:

- Parte `request`: JSON `{ "description": "..." }`; descripción opcional de hasta 2000 caracteres.
- Partes repetibles `files`: opcionales individualmente. Debe enviarse una descripción o al menos un archivo.
- Hasta 5 evidencias, 10 MB por archivo: PDF, PNG o XLSX. El backend valida extensión, MIME, tamaño y que el archivo no esté vacío.

Solo se permite presentar descargo mientras exista al menos una denuncia sin resolver. Se admite un descargo por ciclo de revisión; si ingresan nuevas denuncias después del último descargo, se puede presentar otro. Errores de ciclo cerrado o duplicado responden `409`.

Ejemplo de envío de descargo: agregar `request` como un `Blob` JSON de tipo `application/json`; repetir la parte `files` por cada evidencia. La parte `files` puede omitirse si se envía una descripción.

```javascript
const formData = new FormData();
formData.append("request", new Blob([JSON.stringify({ description })], { type: "application/json" }));
files.forEach((file) => formData.append("files", file));
```

### Política y denuncias de alumnos

`GET /api/v1/tutors/support/material-policy` es la fuente de contenido del modal. Devuelve `acceptedFormats`, `maxDescriptionLength`, `maxDefenseAttachments`, `maxDefenseAttachmentSizeBytes`, `permittedContentPolicy`, `reportReasons`, `reviewProcess`, `upheldOutcome`, `dismissedOutcome` y `reporterNotification`. Usar esos valores para textos/límites configurables.

Los alumnos denuncian mediante `POST /api/v1/materials/{materialId}/reports` con JSON:

```json
{
  "reason": "PLAGIARISM",
  "description": "El archivo contiene ejercicios publicados sin atribución."
}
```

Motivos: `INAPPROPRIATE_CONTENT`, `PLAGIARISM`, `FALSE_INFORMATION`, `OTHER`. `description` admite hasta 2000 caracteres; es obligatoria para `OTHER`. Cada alumno puede denunciar un material una sola vez y necesita acceso al material. Una denuncia suspende preventivamente el material para alumnos. Otros alumnos con acceso previo todavía pueden denunciarlo mientras el caso siga en revisión.

Al resolver cada denuncia, admin envía `PATCH /api/v1/admin/support/material-reports/{reportId}` con `{"status":"RESOLVED","resolution":"UPHELD"}` o `{"status":"RESOLVED","resolution":"DISMISSED"}`. `resolution` es obligatoria exactamente cuando `status` es `RESOLVED`.

| Estado de denuncia | Etiqueta |
|---|---|
| `PENDING` | Pendiente |
| `IN_REVIEW` | En revisión |
| `RESOLVED` | Resuelto |

Transiciones: `PENDING → IN_REVIEW → RESOLVED`; también se permite `PENDING → RESOLVED`. No se permiten retrocesos. El material queda no disponible mientras quede alguna denuncia abierta; al cerrar todas, permanece dado de baja si alguna resolución fue `UPHELD`, y vuelve a estar disponible si todas fueron `DISMISSED`.

## 6. Códigos de bloqueo, errores y fechas

Para `POST /api/v1/materials/{materialId}/reports`, se devuelve `400` si falta el motivo, la descripción supera 2000 caracteres o `OTHER` no tiene descripción; `403` si el alumno no tiene acceso, intenta denunciar su propio material o ya no está permitido denunciarlo; `404` si el material no existe; `409` con `detail: "MATERIAL_ALREADY_REPORTED"` si el alumno ya lo denunció.

`blockReason` de elegibilidad puede ser `SESSION_NOT_PARTICIPANT`, `SESSION_ALREADY_CONFIRMED`, `SESSION_NOT_CLAIMABLE`, `SESSION_NOT_FINALIZED`, `CLAIM_DEADLINE_EXCEEDED` o `ACTIVE_CLAIM_EXISTS`.

Para alta de reclamo, el backend responde `400` por datos/motivo ausentes o adjuntos inválidos; `403` si el usuario no participa; `404` si no existe la sesión; `409` si ya tiene reclamo activo; `422` si la sesión no es reclamable, venció el plazo o el motivo no corresponde al rol. Errores con cuerpo `ApiError` tienen forma `{"status":422,"message":"...","detail":"CLAIM_DEADLINE_EXCEEDED"}`. Mostrar `message`; para lógica de UI, preferir `detail` cuando contenga el código de negocio.

`LocalDate` se serializa como `YYYY-MM-DD`, `LocalTime` como hora local y `LocalDateTime` sin sufijo de zona (`Z`/offset). El backend usa la zona `America/Argentina/Cordoba`; no convertir los campos locales como si fueran UTC.

Para pintar bloqueos de elegibilidad, `blockReason` no trae etiqueta. Mapeos sugeridos: `SESSION_NOT_PARTICIPANT` → «La sesión no pertenece a tu cuenta»; `SESSION_ALREADY_CONFIRMED` → «La sesión ya fue confirmada»; `SESSION_NOT_CLAIMABLE` → «Esta sesión no admite reclamos»; `SESSION_NOT_FINALIZED` → «La sesión todavía no finalizó»; `CLAIM_DEADLINE_EXCEEDED` → «Venció el plazo para reclamar»; `ACTIVE_CLAIM_EXISTS` → «Ya hay un reclamo activo para esta sesión».

En respuestas `409` del descargo, `detail` identifica `MATERIAL_REVIEW_COMPLETED` o `MATERIAL_DEFENSE_ALREADY_SUBMITTED`. El detalle de un material inexistente, sin denuncias o ajeno al tutor responde `404`.

## 7. Base de datos al desplegar

En el entorno local actual `.env` configura `DDL_AUTO=update`. No es necesario borrar ni recrear la base para esta US: Hibernate agrega las tablas/campos nuevos y conserva los registros existentes. Se agregan tablas para notificaciones, comentarios de reclamo, descargos y adjuntos de descargo; a denuncias existentes se les incorporan estado, resolución y revisor con compatibilidad para valores nulos antiguos.

El repositorio no usa Flyway/Liquibase. En entornos compartidos o producción, respaldar la base y aplicar una migración revisada antes del despliegue; no usar `create`/`create-drop` sobre datos que se quieran conservar. `update` es la configuración local comprobada, no reemplaza una estrategia de migración versionada para producción.
