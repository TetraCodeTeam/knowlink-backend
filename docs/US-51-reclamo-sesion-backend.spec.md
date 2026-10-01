# US-51 — Realizar reclamo de sesión (BACKEND)

> **Estado: implementado.** Este documento refleja el contrato final implementado en `knowlink-backend`
> (paquete `com.knowlink.api.claims`). Donde el spec original proponía nombres en español
> (`/api/sesiones/...`, `puedeReclamar`, `MotivoReclamo`), se adoptó la convención del repo:
> rutas bajo `/api/v1/bookings`, campos y enums en inglés, y solo mensajes de usuario final en español.

## 1. Metadata

| Campo | Valor |
|---|---|
| ID | US-51 |
| Título | Realizar reclamo de sesión |
| Story Points | 5 |
| Prioridad | BAJA |
| Módulo | `claims` (nuevo) — integra con reservas/sesiones, fondos (ledger) y storage Supabase |
| Relacionadas | US-41 (confirmación de sesión), US-15 (fondos retenidos / ledger interno), US-26 (materiales/adjuntos) |
| Alcance de este documento | Solo backend. El popup, la notificación "Tu sesión de [Materia] finalizó" y el filtro "Realizadas" de "Mis clases" van en el spec de frontend |

**Historia:** Como usuario (alumno o tutor), quiero iniciar un reclamo sobre una sesión finalizada, para reportar inconvenientes con la asistencia y que los fondos de la sesión queden retenidos hasta que un administrador resuelva el caso. **Hasta 3 adjuntos (pdf/jpg/png) como evidencia.**

## 2. Criterios de aceptación (Gherkin)

```gherkin
Característica: Realizar reclamo de sesión

  Antecedentes:
    Dado un usuario autenticado (alumno o tutor)

  Escenario: Reclamo exitoso con motivo y sin comentario
    Dado una sesión propia finalizada hace menos de 24 horas
    Y que el usuario no tiene un reclamo activo para esa sesión
    Cuando envía el reclamo con motivo "El alumno no pudo asistir"
    Entonces el sistema crea el reclamo en estado OPEN
    Y los fondos de la sesión quedan retenidos
    Y responde 201 con los datos del reclamo

  Escenario: Reclamo exitoso con comentario opcional
    Dado una sesión propia finalizada hace menos de 24 horas
    Cuando envía el reclamo con motivo "Yo no pude asistir" y un comentario
    Entonces el comentario se guarda junto con el reclamo (normalizado con trim)

  Escenario: Reclamo con adjuntos
    Dado una sesión propia finalizada hace menos de 24 horas
    Cuando envía hasta 3 archivos pdf/jpg/png de hasta 5 MB cada uno
    Entonces los archivos se suben al bucket "claimEvidence" y quedan asociados al reclamo
    Y el participante puede listarlos con URL firmada de lectura (1 hora)

  Escenario: Motivo obligatorio
    Cuando envía el reclamo sin motivo o con un motivo fuera del catálogo
    Entonces responde 400 y no se crea ningún reclamo

  Escenario: Plazo vencido
    Dado una sesión propia finalizada hace más de 24 horas
    Cuando intenta reclamarla
    Entonces responde 422 con código CLAIM_DEADLINE_EXCEEDED
    Y la sesión figura como no reclamable en el historial

  Escenario: Sesión no finalizada
    Dado una sesión propia que aún no finalizó
    Cuando intenta reclamarla
    Entonces responde 422 con código SESSION_NOT_FINALIZED

  Escenario: Sesión ajena
    Dado una sesión en la que el usuario no es alumno ni tutor
    Cuando intenta reclamarla
    Entonces responde 403 con código SESSION_NOT_PARTICIPANT y no se crea ningún reclamo

  Escenario: Reclamo activo duplicado
    Dado que el usuario ya tiene un reclamo activo para esa sesión
    Cuando intenta crear otro
    Entonces responde 409 con código ACTIVE_CLAIM_EXISTS
    Y el mensaje "Ya tenés una disputa activa para esta sesión"

  Escenario: Consulta de elegibilidad (para decidir si el front abre el popup)
    Cuando el usuario consulta la elegibilidad de una sesión
    Entonces recibe canClaim, claimableUntil y, si no puede, el blockReason
```

## 3. Stack y decisiones finales

- Java 21 + Spring Boot 3.5 + H2 en tests / MySQL en dev (Spring Data JPA, Bean Validation, Security existente).
- Sin proveedores externos: la retención de fondos usa el **ledger interno** de US-15.
- **Reloj:** bean `java.time.Clock` inyectable (`Clock.system(AppTimeZone.ZONE)`), zona fija
  `America/Argentina/Cordoba` (`com.knowlink.api.shared.utils.AppTimeZone`). No UTC.
- **Plazo:** `knowlink.claims.deadline-hours` (default 24), límite **inclusivo** (`ahora <= fin + 24h`).
- **Adjuntos:** `knowlink.claims.max-attachments: 3`, `knowlink.claims.max-attachment-mb: 5`.
- **Migraciones:** no hay Flyway/Liquibase; esquema por `ddl-auto` (create-drop en tests).
  Constraints/índices se declaran en las anotaciones JPA (`@Table`/`@UniqueConstraint`/`@Index`).
- **Storage:** bucket nuevo **`claimEvidence`** (los materiales de US-26 siguen en `materials`).
  El bucket debe crearse manualmente en Supabase (pasos pendientes, ver §10).

## 4. Advertencias de dependencias (resueltas)

1. **Estado "finalizada":** = `BookingStatus.COMPLETED` solamente (`ClaimDeadlineCalculator.isFinalized`).
2. **Fondos retenidos (US-15):** `FundsLedgerService.markSuspendedByClaim(bookingId, claimId)` —
   **idempotente**: si la transferencia ya está `SUSPENDED_BY_CLAIM` conserva el `blockingClaimId`
   original y no vuelve a guardar. Sesión gratuita (sin `FundsTransfer`) → no-op sin error.
3. **Colisión con la liberación automática (US-15):** `FundsResolutionService.resolve(...)` arranca con
   un guard: si existe reclamo `OPEN` para la reserva, **no** libera ni devuelve fondos (y no toca la reserva).
4. **Listado "Mis clases":** `BookingHistoryItemResponse` ahora incluye `canClaim` y `claimableUntil`;
   `BookingServiceImpl.getHistory` evalúa la página con **una sola consulta** batch
   (`SessionClaimRepository.findClaimedBookingIdsByUser`), sin N+1.
5. **Notificación "Tu sesión finalizó":** el backend valida el plazo siempre en el endpoint;
   el link del front no habilita reclamar fuera de plazo.

## 5. Modelo de datos

### 5.1 Tabla `session_claim`

| Columna | Tipo | Notas |
|---|---|---|
| claim_id | UUID PK | `GenerationType.UUID` |
| booking_id | UUID NOT NULL | FK a la reserva existente; índice `idx_session_claim_booking_id` |
| user_id | UUID NOT NULL | FK al usuario que reclama; índice compuesto `idx_session_claim_user_status (user_id, status)` |
| claimant_role | VARCHAR(10) NOT NULL | `STUDENT` / `TUTOR` (calculado en backend, nunca del cliente) |
| reason | VARCHAR(40) NOT NULL | enum `ClaimReason` |
| comment | VARCHAR(500) NULL | opcional; trim; vacío → NULL |
| status | VARCHAR(20) NOT NULL | enum `ClaimStatus`: `OPEN`, `RESOLVED` (la transición a `RESOLVED` queda fuera de esta US) |
| active_key | VARCHAR(100) NULL | `"{bookingId}:{userId}"` mientras está activo; **UNIQUE** `uk_session_claim_active_key` |
| created_at / updated_at | DATETIME(6) NOT NULL | por `@PrePersist`/`@PreUpdate` en zona `AppTimeZone` |

- `UNIQUE (active_key)`: MySQL no tiene índices únicos parciales; garantiza "un reclamo activo por
  usuario y sesión" ante doble click o requests concurrentes (los `NULL` no colisionan).
- La violación única se captura como `DataIntegrityViolationException` → 409 `ACTIVE_CLAIM_EXISTS`
  con el mensaje exacto.

### 5.2 Tabla `claim_attachment`

| Columna | Tipo | Notas |
|---|---|---|
| attachment_id | UUID PK | |
| claim_id | UUID NOT NULL | FK a `session_claim`; índice `idx_claim_attachment_claim_id` |
| storage_path | VARCHAR(500) NOT NULL | path en el bucket `claimEvidence` |
| original_file_name | VARCHAR(255) NOT NULL | nombre original |
| content_type | VARCHAR(100) NOT NULL | `application/pdf`, `image/jpeg`, `image/png` |
| size_in_bytes | BIGINT NOT NULL | |
| created_at | DATETIME(6) NOT NULL | |

### 5.3 Enums (paquete `com.knowlink.api.claims.data.enums`)

```java
public enum ClaimReason { STUDENT_COULD_NOT_ATTEND, I_COULD_NOT_ATTEND }
public enum ClaimStatus { OPEN, RESOLVED }  // RESOLVED queda fuera de esta US
public enum ClaimBlockReason { SESSION_NOT_PARTICIPANT, SESSION_NOT_FINALIZED, CLAIM_DEADLINE_EXCEEDED, ACTIVE_CLAIM_EXISTS }
```

> "El alumno no pudo asistir" es ambiguo cuando reclama el propio alumno: se guarda el motivo literal
> más `claimantRole`, y la interpretación queda a cargo del administrador al resolver.

## 6. API

Base: `/api/v1/bookings/{bookingId}/claims`. Todos los endpoints requieren autenticación;
el usuario sale del token (`@AuthenticationPrincipal UserPrincipal`), nunca del body.

### 6.1 `POST /api/v1/bookings/{bookingId}/claims` — crear reclamo

`Content-Type: multipart/form-data` con dos partes:

| Parte | Requerida | Contenido |
|---|---|---|
| `request` | sí | JSON: `{ "reason": "STUDENT_COULD_NOT_ATTEND", "comment": "Esperé 15 minutos y no se conectó" }` |
| `files` | no | hasta 3 archivos `pdf` / `jpg` / `jpeg` / `png`, ≤ 5 MB c/u |

Validaciones (`@Valid`):
- `reason`: `@NotNull(message = "reason is required")`, valor del enum.
- `comment`: opcional, `@Size(max = 500, message = "comment must be at most 500 characters")`.

Respuesta `201 Created`:
```json
{
  "id": "0b1f6f9e-...",
  "bookingId": "6c86354e-...",
  "reason": "STUDENT_COULD_NOT_ATTEND",
  "comment": "Esperé 15 minutos y no se conectó",
  "status": "OPEN",
  "claimantRole": "STUDENT",
  "createdAt": "2026-10-01T09:12:00",
  "attachments": [
    { "id": "…", "fileName": "evidencia.pdf", "contentType": "application/pdf", "sizeBytes": 183211 }
  ]
}
```

Errores (formato `ApiError { status, message, detail }` del `@ControllerAdvice` existente):

| HTTP | detail (código) | message | Cuándo |
|---|---|---|---|
| 400 | `VALIDATION_ERROR` | error de campo / `Cuerpo de la solicitud inválido.` | motivo ausente, comentario > 500, JSON malformado en `request` |
| 400 | `VALIDATION_ERROR` | mensajes de adjuntos en español | > 3 archivos, archivo vacío, > 5 MB |
| 400 | `FORMAT_NOT_ALLOWED` | `El formato del archivo no está permitido` | extensión/content-type fuera de pdf/jpg/jpeg/png |
| 401 | — | — | sin autenticar |
| 403 | `SESSION_NOT_PARTICIPANT` | `No participás de esta sesión.` | el usuario no es alumno ni tutor |
| 404 | `SESSION_NOT_FOUND` | `La sesión no existe.` | la reserva no existe |
| 409 | `ACTIVE_CLAIM_EXISTS` | **`Ya tenés una disputa activa para esta sesión`** | reclamo activo previo del usuario o carrera ganada por otro request |
| 422 | `SESSION_NOT_FINALIZED` | `La sesión todavía no finalizó.` | estado ≠ `COMPLETED` |
| 422 | `CLAIM_DEADLINE_EXCEEDED` | `Ya pasó el plazo para reclamar esta sesión (24 horas desde que finalizó).` | fuera del plazo |
| 500 | `UNEXPECTED` | `Unexpected error` | falla la retención u otro error → **rollback total** (sin reclamo huérfano) |

> El 400 por JSON malformado sale del handler nuevo `HttpMessageNotReadableException` (Spring);
> el 422 sale del handler nuevo `UnprocessableEntityException` (base de las dos excepciones de plazo/estado).

### 6.2 `GET /api/v1/bookings/{bookingId}/claims/eligibility` — ¿puede reclamar?

Respuesta `200`:
```json
{
  "canClaim": false,
  "claimableUntil": "2026-10-02T15:00:00",
  "blockReason": "ACTIVE_CLAIM_EXISTS"
}
```
- `blockReason` ∈ `SESSION_NOT_PARTICIPANT | SESSION_NOT_FINALIZED | CLAIM_DEADLINE_EXCEEDED | ACTIVE_CLAIM_EXISTS | null`.
- `claimableUntil` = `fin + 24h` (null si la sesión no está finalizada).
- Mismo método de dominio que el POST (`evaluate`), solo lectura: no crea nada.

### 6.3 `GET /api/v1/bookings/{bookingId}/claims/{claimId}/attachments` — adjuntos

Respuesta `200` (lista; URL firmada de lectura con vencimiento de 1 hora):
```json
[
  { "id": "…", "fileName": "evidencia.pdf", "contentType": "application/pdf", "sizeBytes": 183211,
    "signedUrl": "https://…/claimEvidence/…?token=…" }
]
```
- Participantes (alumno/tutor) y `ADMIN` pueden leer; otros → 403 `SESSION_NOT_PARTICIPANT`.
- Reclamo inexistente → 404 `CLAIM_NOT_FOUND`.

### 6.4 Orden de validación (igual en POST y elegibilidad — un solo método `evaluate`)

1. La sesión existe → 404 `SESSION_NOT_FOUND`.
2. El usuario participa de la sesión → 403 `SESSION_NOT_PARTICIPANT`.
3. La sesión está finalizada (`COMPLETED`) → 422 `SESSION_NOT_FINALIZED`.
4. Dentro de plazo (`ahora <= fin + 24h`, inclusivo) → 422 `CLAIM_DEADLINE_EXCEEDED`.
5. Sin reclamo activo propio → 409 `ACTIVE_CLAIM_EXISTS`.
6. (POST) validar adjuntos → 400.

### 6.5 Cambio en el listado de sesiones realizadas (`GET /api/v1/bookings/mine`)

Cada ítem de `BookingHistoryItemResponse` ahora incluye:
```json
{ "canClaim": true, "claimableUntil": "2026-10-02T15:00:00" }
```
- `canClaim` = finalizada + dentro de plazo + sin reclamo activo del usuario.
- Resuelto con **una sola consulta batch** por página (`findClaimedBookingIdsByUser`), sin N+1.

## 7. Implementación

### 7.1 Estructura creada

```
com.knowlink.api.claims/
├── controllers/
│   ├── interfaces/IClaimController.java            (@RequestMapping "/api/v1/bookings", @Operation/@ApiResponse en español)
│   ├── implementations/ClaimControllerImpl.java
│   ├── requests/CreateClaimRequest.java
│   └── responses/{ClaimResponse, ClaimEligibilityResponse, ClaimAttachmentResponse, ClaimAttachmentUrlResponse}
├── data/
│   ├── enums/{ClaimReason, ClaimStatus, ClaimBlockReason}
│   └── models/{SessionClaim, ClaimAttachment}
├── repositories/{SessionClaimRepository, ClaimAttachmentRepository}
├── services/
│   ├── interfaces/ISessionClaimService.java
│   └── implementations/SessionClaimServiceImpl.java
└── utils/ClaimDeadlineCalculator.java
```

Excepciones nuevas en `exceptions.custom_exceptions`:
`UnprocessableEntityException` (base 422), `SessionNotFinalizedException`, `ClaimDeadlineExceededException`,
`SessionNotParticipantException` (403). `GlobalExceptionHandler` suma los handlers 403/422/400.

Modificaciones en código existente (mínimas):
- `FundsLedgerService.markSuspendedByClaim` → idempotente; `FundsResolutionService` → guard por reclamo `OPEN`.
- `BookingHistoryItemResponse` + `BookingMapper.toListItem` → `canClaim`/`claimableUntil`; `BookingServiceImpl.getHistory` → evaluación batch.
- `ISupabaseStorageService`/impl → sobrecargas `upload(file, bucket, folder)`, `generateSignedUrl(bucket, path, seconds)`, `delete(bucket, path)` (bucket `materials` intacto).
- `ApplicationConfig` → bean `Clock`; `application.yaml` → bloque `knowlink.claims`.

### 7.2 Reglas del servicio (`SessionClaimServiceImpl.create`)

- Validar en el orden de 6.4 con un único método `evaluate` (compartido con elegibilidad y listado).
- Determinar `claimantRole` comparando el `userId` del token con alumno/tutor de la reserva.
- Persistir con `saveAndFlush` y capturar `DataIntegrityViolationException` sobre `active_key` → 409 con mensaje exacto.
- **Adjuntos:** validar (cantidad, vacío, tamaño, extensión + MIME) **antes** de persistir; subir a `claimEvidence`;
  persistir `claimAttachment` y retener fondos en la **misma transacción**; si algo falla después de subir,
  se borran los archivos ya subidos (`delete`) y se propaga → rollback.
- Plazo con `ClaimDeadlineCalculator` (reloj inyectable, límite inclusivo).
- Normalizar `comment`: `trim()`, vacío → `null`. No loguear el contenido del comentario.
- Si ya hay un reclamo `OPEN` de la contraparte sobre la misma sesión, **se permite** crear el propio
  (la regla es por usuario) y la retención no se duplica (idempotencia de `markSuspendedByClaim`).

## 8. No hacer

- No implementar la resolución del reclamo ni el panel de administración (`OPEN → RESUELTO`, liberar o
  devolver fondos): es otra US.
- No tocar la lógica de liberación/devolución de US-15 más allá del guard por reclamo `OPEN` y la
  retención idempotente.
- No integrar Mercado Pago ni ningún proveedor de pago externo.
- No enviar mails ni notificaciones nuevas (el link lo maneja el front).
- No confiar en `userId`, `role` ni fechas enviadas por el cliente.
- **Adjuntos:** solo los 3 archivos del reclamo, en el bucket `claimEvidence`; no reutilizar ni modificar
  la estructura de adjuntos de US-26 ni el upload de US-16.
- No resolver el plazo "en el front": la validación de las 24 h es autoridad del backend.
- No consultar reclamos sesión por sesión en el listado (N+1).
- No crear un mecanismo de migraciones nuevo ni cambiar las convenciones de errores del proyecto.

## 9. Casos de prueba

Ejecutados con `.\mvnw.cmd test` — todos en verde.

| # | Caso de prueba | Resultado esperado | ¿Pasó? / Notas |
|---|---|---|---|
| 1 | Alumno reclama sesión propia finalizada hace 1 h, sin comentario | 201, `OPEN`, `claimantRole=STUDENT`, fondos retenidos | Sí / CP-01 + unitario |
| 2 | Tutor reclama sesión propia finalizada hace 1 h, con comentario | 201, comentario trimmeado, `claimantRole=TUTOR` | Sí / CP-24 + unitario |
| 3 | Reclamo sin motivo | 400, no se crea reclamo | Sí / CP-09 |
| 4 | Motivo fuera del catálogo | 400, no se crea reclamo | Sí / unitario (`ClaimReason` tipado + 400) |
| 5 | Comentario de 501 caracteres | 400 | Sí / CP-10 |
| 6 | Reclamo exactamente en el límite de las 24 h | 201 | Sí / unitario (`create_atExactDeadline`) |
| 7 | Reclamo a las 24 h + 1 min | 422 `CLAIM_DEADLINE_EXCEEDED` | Sí / CP-07 + unitario |
| 8 | Reclamo sobre sesión aún en curso | 422 `SESSION_NOT_FINALIZED` | Sí / CP-06 |
| 9 | Usuario que no participa reclama | 403, no se crea reclamo | Sí / CP-04 |
| 10 | Sesión inexistente | 404 `SESSION_NOT_FOUND` | Sí / CP-05 |
| 11 | Segundo reclamo del mismo usuario con el primero `OPEN` | 409, mensaje exacto | Sí / CP-03 |
| 12 | Dos creaciones concurrentes del mismo usuario y sesión | Uno crea, otro 409; un solo reclamo en BD | Sí / CP-23 (transacción retenida + `active_key` UNIQUE) |
| 13 | La contraparte reclama la misma sesión ya reclamada por el otro | 201; retención no se duplica (`blockingClaimId` original) | Sí / CP-24 |
| 14 | Sesión gratuita (sin `FundsTransfer`) | 201, sin movimiento en el ledger | Sí / CP-02 |
| 15 | Falla la retención de fondos | 500 y rollback: no queda reclamo en BD; transfer intacta | Sí / CP-22 |
| 16 | `GET elegibilidad` dentro de plazo, sin reclamo | `canClaim=true`, `claimableUntil` correcto | Sí / CP-14 |
| 17 | `GET elegibilidad` con reclamo activo / vencida / sin finalizar | `canClaim=false` con el `blockReason` correspondiente | Sí / CP-15, CP-16, CP-17 |
| 18 | Listado `/mine`: sesiones dentro y fuera de plazo | `canClaim` true / false y `claimableUntil` presente | Sí / CP-21 |
| 19 | Liberación automática de fondos sobre sesión con reclamo `OPEN` | No libera ni devuelve los fondos | Sí / unitario `FundsResolutionServiceTest` (caso 19) |
| 20 | Request sin autenticar | 401 | Sí / CP-08 |
| 21 | Adjuntos válidos (pdf + png) | 201 con `attachments`, subidos a `claimEvidence` | Sí / CP-01 + unitario |
| 22 | Más de 3 adjuntos / vacío / > 5 MB / formato no permitido | 400 (mensajes en español / `FORMAT_NOT_ALLOWED`) | Sí / CP-12, CP-13 + unitarios |
| 23 | Lectura de adjuntos: participante / ajeno / reclamo inexistente | 200 con signedUrl / 403 / 404 `CLAIM_NOT_FOUND` | Sí / CP-18, CP-19, CP-20 |
| 25 | **E2E subida real**: reclamo con PDF contra el bucket `claimEvidence` (storage real, sin mock) | 201, objeto presente en el bucket, signed URL devuelve los bytes, tras `delete` el endpoint directo del storage responde ≠200 (polling) y re-firmar falla con 400 (nota: la signed URL previa puede seguir sirviendo contenido en cache hasta expirar el token; no usarla como sonda post-borrado), fondos `SUSPENDED_BY_CLAIM` | Sí / `ClaimAttachmentRealUploadIntegrationTest` (se salta si no hay credenciales en `.env`) |

Suites: `SessionClaimServiceImplTest` (25), `ClaimControllerIntegrationTest` (24),
`ClaimAttachmentRealUploadIntegrationTest` (1, E2E real contra Supabase), `FundsLedgerServiceTest` (3),
`FundsResolutionServiceTest` (9, incluye caso 19).

## 10. Definition of Done

- [x] Entidad, enums, repositorio, servicio y controller implementados en `com.knowlink.api.claims`.
- [x] `UNIQUE (active_key)` e índices declarados en JPA (ddl-auto; sin mecanismo de migraciones nuevo).
- [x] `POST …/claims`, `GET …/claims/eligibility` y `GET …/claims/{id}/attachments` operativos con los códigos de la sección 6.
- [x] Orden de validación 6.4 compartido entre POST, elegibilidad y listado (un solo método `evaluate`).
- [x] Listado `/mine` devuelve `canClaim` y `claimableUntil` sin N+1.
- [x] Retención de fondos idempotente y en la misma transacción que el reclamo (con limpieza de subidas y rollback).
- [x] Liberación automática de US-15 respeta reclamos `OPEN` (`FundsResolutionService` guard).
- [x] Plazo configurable (`knowlink.claims.deadline-hours`) y reloj inyectable (`Clock` en zona Córdoba).
- [x] Adjuntos: ≤ 3, ≤ 5 MB, pdf/jpg/jpeg/png, bucket `claimEvidence`, signed URL 1 h.
- [x] Tests unitarios del servicio (límite del plazo, ajena, no finalizada, duplicado, gratuita, adjuntos, N+1).
- [x] Tests de integración: concurrencia (caso 12), rollback por falla de retención (caso 15), contraparte (13).
- [x] Mensaje exacto "Ya tenés una disputa activa para esta sesión" en el 409.
- [x] Casos de la sección 9 ejecutados y en verde (`.\mvnw.cmd test`).
- [x] **Bucket `claimEvidence` creado** en Supabase (verificado: acepta `application/pdf` y restringe otros MIME con 415).
- [x] **E2E real de subida** (`ClaimAttachmentRealUploadIntegrationTest`): upload → signed URL → delete, con credenciales desde `.env` y gate `@EnabledIf` para máquinas sin credenciales.
- [ ] **Frontend:** spec de frontend de US-51 consume estos contratos (campos/rutas en inglés, incluye adjuntos).