# US-50 — Resolver disputas de sesión escaladas por el administrador (Backend)

## Metadata

| Campo | Valor |
|---|---|
| ID Historia | US-50 |
| Módulo backend | `com.knowlink.api.admin.claims` (+ ajustes puntuales en `payments`) |
| Repo / rama base | `TetraCodeTeam/knowlink-backend` / `main` |
| Relacionadas | US-46 (soporte tutor), US-47 (reclamos alumno), **US-51 (realizar reclamo de sesión)**, US-15 (liberación de fondos), US-35 (notificaciones), US-30 (badge), US-33 (home), Admin 00 (base) |
| Depende de | `admin-00` y de que **exista la entidad de reclamo** (US-46/47/51) |
| Stack | Java 21 · Spring Boot 3.5.3 · JPA/MySQL · `IEmailService` (Brevo) · ledger de fondos (`payments`) |
| Consumidor | Frontend `admin-04-reclamos-sesiones` |

---

## 1. Estado actual verificado y advertencias de dependencia

- `payments` ya tiene el soporte de suspensión: `FundsStatus.SUSPENDED_BY_CLAIM`, `FundsTransfer.blockingClaimId`, `FundsLedgerService.markSuspendedByClaim(bookingId, claimId)` y, en `FundsResolutionService.resolve`, la salida temprana "Booking {} has active claim, funds resolution suspended". Cuando hay un reclamo, el booking **no recibe estado final** y los fondos quedan en `SUSPENDED_BY_CLAIM`.
- `FundsResolutionService.retryAfterClaimResolution` solo **desbloquea** (`SUSPENDED_BY_CLAIM → HELD`, `blockingClaimId = null`); **no decide a quién van los fondos**. Es lo que expone hoy `POST /api/v1/admin/bookings/{bookingId}/funds/retry`. US-50 necesita una resolución explícita (tutor o alumno), que **no existe**.
- `FundsTransfer` tiene restricción única por `booking_id`. `FundsLedgerService.releaseToTutor` y `refundToStudent` **construyen y guardan una `FundsTransfer` nueva**: usarlas sobre una reserva que ya tiene su fila (`HELD`/`SUSPENDED_BY_CLAIM`) violaría la restricción. ⚠️ Verificar el comportamiento real antes de reutilizarlas; la resolución del admin debe **actualizar la fila existente** (ver 4.6).
- `BookingStatus` ya incluye `COMPLETED`, `NOT_FULFILLED_BY_TUTOR` y `SESSION_NOT_HELD` (estados finales posibles).
- `ImmutableBookingException` (que lanza `FundsResolutionService` cuando los fondos ya están en estado terminal) **no tiene handler** en `GlobalExceptionHandler`: hoy un reintento caería en el catch-all (500). Este spec agrega el handler (409, `detail: "BOOKING_IMMUTABLE"`, mensaje seguro en español).
- `User` tiene `fullName`, `email`, `phoneNumber`, `dni`; `TutorProfile`/`StudentProfile` tienen `institutionalId` (legajo) y `career`.
- ⚠️ **La entidad de reclamo no está en `main`.** Existe trabajo de US-51 (reclamos de tutor/alumno) en ramas como `feat/submit-session-claims` / `feat/tutor-califications-sumary`. **Antes de implementar hay que confirmar con quien lo desarrolló** el nombre, el paquete y los campos reales de la entidad. Este spec asume el nombre `SessionClaim` y define el **contrato mínimo** que necesita el admin (sección 3).
- ⚠️ **Pasarela de pago:** `main` solo tiene ledger interno. El equipo está pasando a Mercado Pago (se retiene y, según el resultado, se ordena pagar al tutor o reintegrar al alumno). La ejecución real del movimiento en el paso de resolución debe ir por **la misma abstracción que use US-15/US-44 en la rama con la pasarela** (ver 4.6). Si todavía no existe, este spec funciona sobre el ledger y deja el punto de enganche.
- ⚠️ **Tutorías gratuitas:** si la reserva no tiene `FundsTransfer` (clase sin pago), no hay fondos que mover. Ver escenario "Reclamo sobre sesión gratuita".

---

## 2. Criterios de aceptación (Gherkin)

```gherkin
Feature: Resolución de reclamos de sesión por el administrador

  Background:
    Given un usuario con rol ADMIN autenticado

  Scenario: Listado paginado por estado
    When consulta los reclamos
    Then recibe los reclamos escalados, paginados
    And puede filtrar por OPEN (Pendiente), IN_REVIEW o RESOLVED, o ver todos
    And recibe los contadores por estado

  Scenario: Rango de fechas y asignación
    When no indica rango
    Then se listan los reclamos abiertos en los últimos 30 días
    When indica "from" y "to"
    Then se aplica ese rango
    When consulta con assignedToMe = true
    Then solo recibe los asignados a su cuenta

  Scenario: Datos de cada entrada
    Then cada entrada incluye materia, tutor, alumno, fecha de la sesión y estado
    And el administrador asignado si corresponde

  Scenario: Detalle del reclamo
    When consulta un reclamo
    Then recibe además el monto retenido
    And el resultado automático que registró el sistema (por ejemplo "Sesión no realizada — reintegro al alumno")
    And el motivo que seleccionó cada parte

  Scenario: Mensaje de cada parte
    Given que el tutor dejó un mensaje y el alumno no
    Then el detalle incluye el mensaje del tutor
    And el mensaje del alumno es null (el frontend muestra "No se registró un mensaje")

  Scenario: Tomar solicitud
    Given un reclamo OPEN
    When el administrador lo toma
    Then pasa a IN_REVIEW asignado a su cuenta
    And los fondos siguen retenidos (SUSPENDED_BY_CLAIM)
    And otro administrador recibe 409 CASE_ALREADY_TAKEN si intenta tomarlo

  Scenario: Reclamo asignado a otro administrador
    Given un reclamo IN_REVIEW asignado a otro administrador
    When un administrador distinto lo consulta
    Then lo ve en modo lectura (permissions.canResolve = false, canViewContact = false)
    And si intenta resolverlo o ver el contacto recibe 403 NOT_ASSIGNED_TO_ADMIN

  Scenario: Consultar datos de contacto
    Given un reclamo IN_REVIEW asignado a mí
    When pido los datos de contacto
    Then recibo nombre, teléfono, email e identificación de ambas partes
    And la acción queda auditada
    And la respuesta no se cachea (Cache-Control: no-store)
    And consultarlos no es requisito para resolver

  Scenario: Datos de contacto antes de tomar el reclamo
    Given un reclamo OPEN
    When pido los datos de contacto
    Then recibo 409 CASE_NOT_IN_REVIEW

  Scenario: Resolver a favor del tutor
    Given un reclamo IN_REVIEW asignado a mí
    When resuelvo a favor del tutor con una justificación
    Then se transfieren los fondos retenidos al tutor (con la retención del sistema)
    And el reclamo pasa a RESOLVED con favoredParty = TUTOR, la justificación y resolvedAt
    And el booking pasa a COMPLETED
    And se notifica a ambas partes con el resultado y la justificación

  Scenario: Resolver a favor del alumno
    When resuelvo a favor del alumno con una justificación
    Then se reintegran los fondos retenidos al alumno
    And el reclamo pasa a RESOLVED con favoredParty = STUDENT
    And se notifica a ambas partes

  Scenario: Justificación obligatoria
    When intento resolver sin justificación o solo con espacios
    Then recibo 400 VALIDATION_ERROR
    And el reclamo y los fondos no cambian

  Scenario: Parte a favor obligatoria
    When intento resolver sin indicar favoredParty
    Then recibo 400 VALIDATION_ERROR

  Scenario: Resolución irreversible
    Given un reclamo RESOLVED
    When se intenta resolver o tomar de nuevo
    Then recibo 409 CASE_ALREADY_RESOLVED
    And el resultado, los fondos y la justificación no cambian

  Scenario: Falla la ejecución de los fondos
    Given que la transferencia o el reintegro falla
    Then la operación completa se revierte
    And el reclamo sigue IN_REVIEW asignado a mí y puedo reintentar
    And recibo 502 FUNDS_EXECUTION_FAILED

  Scenario: Reintento sin duplicar movimientos
    When reintento la resolución tras una falla
    Then no se mueven los fondos dos veces

  Scenario: Reclamo sobre sesión gratuita
    Given una reserva sin fondos retenidos
    When resuelvo el reclamo
    Then el reclamo pasa a RESOLVED sin mover fondos
    And heldAmount es 0 en el detalle

  Scenario: Justificación visible para ambas partes
    Given un reclamo RESOLVED
    When el tutor o el alumno consultan su reclamo (US-46/US-47)
    Then ven el resultado (a favor de quién), la justificación y la fecha

  Scenario: Sin reclamos para los filtros
    Then content es una lista vacía y totalElements = 0
```

---

## 3. Contrato mínimo con la entidad de reclamo (US-46/47/51)

El módulo admin **no crea reclamos**; los lee y los resuelve. Necesita que `SessionClaim` (nombre a confirmar) tenga:

| Campo | Tipo | Para qué |
|---|---|---|
| `claimId` | `UUID` | Identificador (`claimId` en las rutas) |
| `booking` | `Booking` | Sesión, materia, tutor, alumno, monto |
| `status` | `SessionClaimStatus { OPEN, IN_REVIEW, RESOLVED }` | Coincide con `ClaimStatus` del frontend |
| `openedAt` | `LocalDateTime` | Filtro de fechas, orden y antigüedad |
| `automaticOutcome` | `AutomaticOutcome` | "Resultado automático que registró el sistema" (3.1) |
| Declaración del tutor | `reason` + `message` (nullable) | Motivo y mensaje opcional |
| Declaración del alumno | `reason` + `message` (nullable) | Ídem |
| **Nuevos (los agrega este spec):** `assignedAdmin`, `assignedAt`, `resolvedAt`, `favoredParty`, `resolutionJustification`, `@Version` | ver 3.2 | Gestión del admin |

> ⚠️ **Modelado de las dos declaraciones.** US-50 habla del mensaje "que dejó cada parte al iniciar la disputa", pero la regla de US-51 acordada es "un reclamo activo por sesión". Se recomienda **un único `SessionClaim` por reserva** con dos declaraciones (`tutorStatement`, `studentStatement`, cada una `reason` + `message`), donde cada parte completa la suya. Si en la rama existen dos reclamos separados por reserva, el admin debe agruparlos en una única "disputa" por `bookingId`; hay que decidirlo **antes** de implementar, porque cambia las consultas.

### 3.1 Resultado automático

Calculado y **persistido al crear el reclamo** (US-46/47/51), a partir de la misma lógica que `FundsResolutionService.resolve(tutorPresent, studentPresent)` habría aplicado:

```java
public enum AutomaticOutcome {
    SESSION_COMPLETED_RELEASE_TO_TUTOR,    // "Sesión realizada — liberación al tutor"
    TUTOR_ABSENT_REFUND_TO_STUDENT,        // "Tutor ausente — reintegro al alumno"
    SESSION_NOT_HELD_REFUND_TO_STUDENT     // "Sesión no realizada — reintegro al alumno"
}
```

El admin devuelve `code` y `label` (español). Si el reclamo no lo trae (reclamos previos), `automaticOutcome = null` y el frontend oculta la fila.

### 3.2 Campos que agrega este spec

```java
@ManyToOne(fetch = LAZY) @JoinColumn(name = "assigned_admin_id") User assignedAdmin;
@Column(name = "assigned_at") LocalDateTime assignedAt;
@Column(name = "resolved_at") LocalDateTime resolvedAt;
@Enumerated(EnumType.STRING) @Column(name = "favored_party") ClaimFavoredParty favoredParty;   // TUTOR | STUDENT
@Column(name = "resolution_justification", length = 1000) String resolutionJustification;
@Version Long version;
```

Índices sugeridos: `(status)`, `(status, opened_at)`, `(resolved_at)`.

```java
public enum ClaimFavoredParty { TUTOR, STUDENT }
```

---

## 4. Endpoints

Prefijo `/api/v1/admin/session-claims`, controller con `@PreAuthorize("hasRole('ADMIN')")` (Admin 00).

| Método | Ruta | Descripción |
|---|---|---|
| `GET` | `/` | Listado paginado |
| `GET` | `/{claimId}` | Detalle |
| `POST` | `/{claimId}/assign` | Tomar solicitud |
| `GET` | `/{claimId}/contact-info` | Datos de contacto de ambas partes |
| `POST` | `/{claimId}/resolve` | Resolver |

### 4.1 Listado

Parámetros: `status` (`OPEN | IN_REVIEW | RESOLVED`, opcional), `assignedToMe` (default `false`), `from`/`to` (sobre `openedAt`), `page`, `size`.

Orden fijo (el criterio no lo define; propuesta): **OPEN primero, luego IN_REVIEW, luego RESOLVED; dentro de cada grupo el más antiguo primero** (`openedAt ASC`).

```java
public record SessionClaimListItemResponse(
        UUID claimId, String subjectName,
        String tutorName, String studentName,
        LocalDate sessionDate,
        SessionClaimStatus status,
        LocalDateTime openedAt,
        AssignedAdminResponse assignedAdmin,   // null si no hay
        boolean assignedToMe,
        LocalDateTime resolvedAt,
        ClaimFavoredParty favoredParty,        // null hasta RESOLVED
        String resolutionJustification         // null hasta RESOLVED
) {}

public record SessionClaimListResponse(
        PagedResponse<SessionClaimListItemResponse> page,
        ClaimStatusCountsResponse statusCounts
) {}

public record ClaimStatusCountsResponse(long open, long inReview, long resolved) {}
```

`statusCounts`: mismo rango y mismo `assignedToMe`, **ignorando `status`** (igual criterio que en US-23). El badge del sidebar, en cambio, cuenta todos los `OPEN` sin filtros.

### 4.2 Detalle

```java
public record SessionClaimDetailResponse(
        SessionClaimListItemResponse summary,
        BigDecimal heldAmount,                  // monto retenido; 0 si la sesión era gratuita
        AutomaticOutcomeResponse automaticOutcome,   // null si no se registró
        ClaimPartyStatementResponse tutor,
        ClaimPartyStatementResponse student,
        ClaimPermissionsResponse permissions
) {}

public record AutomaticOutcomeResponse(AutomaticOutcome code, String label) {}

public record ClaimPartyStatementResponse(
        String fullName,
        String reasonCode, String reasonLabel,   // motivo que seleccionó (label en español)
        String message                            // null => "No se registró un mensaje"
) {}

public record ClaimPermissionsResponse(boolean canTake, boolean canViewContact, boolean canResolve) {}
```

- `canTake` = `OPEN`. `canViewContact` y `canResolve` = `IN_REVIEW` **y** asignado al admin que consulta.
- `heldAmount` sale de `FundsTransfer.originalAmount` o, si no hay fila, `0`.

### 4.3 Tomar solicitud — `POST /{claimId}/assign`

Sin body. Actualización condicional atómica de Admin 00 (4.7) sobre `status = OPEN`. `200` con el detalle; `404` si no existe; `409 CASE_ALREADY_TAKEN` / `CASE_NOT_PENDING`. **No toca los fondos**: siguen `SUSPENDED_BY_CLAIM`. Registrar `CASE_TAKEN` en auditoría.

### 4.4 Datos de contacto — `GET /{claimId}/contact-info`

Solo si `IN_REVIEW` y asignado al admin que consulta: si el reclamo está `OPEN` o `RESOLVED` → `409 CASE_NOT_IN_REVIEW`; si está asignado a otro → `403 NOT_ASSIGNED_TO_ADMIN`.

```java
public record ClaimContactInfoResponse(PartyContactResponse tutor, PartyContactResponse student) {}

public record PartyContactResponse(
        String fullName, String email, String phoneNumber,   // phoneNumber puede ser null
        String dni,                                           // User.dni
        String institutionalId,                               // legajo
        String career                                         // null si no aplica
) {}
```

- Header `Cache-Control: no-store`. Registrar `CONTACT_INFO_VIEWED` en auditoría (admin, reclamo, fecha). **Nunca** loguear los valores.
- No hay estado que cambie ni condición previa para resolver: es una consulta opcional.

### 4.5 Resolver — `POST /{claimId}/resolve`

```java
public record ResolveSessionClaimRequest(
        @NotNull(message = "Favored party is required") ClaimFavoredParty favoredParty,
        @NotBlank(message = "Justification is required")
        @Size(max = 1000, message = "Justification must be at most 1000 characters") String justification
) {}
```

`200` con el `SessionClaimDetailResponse` actualizado (`RESOLVED`).

### 4.6 Algoritmo de resolución (una sola transacción)

```
@Transactional
resolve(claimId, adminUserId, request):
  1. claim = findByIdForUpdate(claimId)                 // SELECT … FOR UPDATE (PESSIMISTIC_WRITE)
       - no existe                          → ResourceNotFoundException (CLAIM_NOT_FOUND)
       - status == RESOLVED                  → 409 CASE_ALREADY_RESOLVED
       - status == OPEN                      → 409 CASE_NOT_IN_REVIEW
       - assignedAdmin != adminUserId        → 403 NOT_ASSIGNED_TO_ADMIN
  2. justification = request.justification().trim(); si queda vacía → ValidationException
  3. fundsResolutionService.resolveByAdminDecision(booking, request.favoredParty(), claimId)
       - lanza FundsExecutionException si falla → revierte todo
  4. booking.bookingStatus = finalStatusFor(favoredParty, claim)     // ver tabla
  5. claim.status = RESOLVED; resolvedAt = now; favoredParty; resolutionJustification = justification
  6. publicar SessionClaimResolvedEvent; auditar CLAIM_RESOLVED
```

**`FundsResolutionService.resolveByAdminDecision(Booking booking, ClaimFavoredParty favored, UUID claimId)`** (método nuevo):

| Situación | Acción |
|---|---|
| No hay `FundsTransfer` para la reserva (sesión gratuita) | No mueve fondos; retorna sin error |
| Hay fila y su estado es terminal (`RELEASED_TO_TUTOR`/`REFUNDED_TO_STUDENT`) | `ImmutableBookingException` → no se duplica (cubre el reintento) |
| Hay fila y no está en `SUSPENDED_BY_CLAIM`, o su `blockingClaimId` no es `claimId` | `BusinessConflictException("FUNDS_STATE_MISMATCH")` |
| `favored == TUTOR` | **Actualizar la fila existente**: `recipient = TUTOR`, `transferredAmount = originalAmount − retención%`, `fundsStatus = RELEASED_TO_TUTOR`, `concept = "Liberación por resolución de reclamo a favor del tutor"`, `processedAt = now` |
| `favored == STUDENT` | **Actualizar la fila existente**: `recipient = STUDENT`, `systemRetentionPercentage = 0`, `transferredAmount = originalAmount`, `fundsStatus = REFUNDED_TO_STUDENT`, `concept = "Devolución por resolución de reclamo a favor del alumno"`, `processedAt = now` |

- Reutilizar el cálculo de retención de `FundsLedgerService` (extraerlo a un método privado/compartido; no duplicar el porcentaje).
- **No** llamar a `releaseToTutor`/`refundToStudent` tal como están (insertan una fila nueva y chocarían con `uk_funds_transfer_booking`), salvo que se verifique que ya actualizan la fila existente.
- Conservar `blockingClaimId` como referencia de auditoría (no limpiarlo) al resolver; la consulta de suspensión se hace por estado, no por este campo.
- **Pasarela (Mercado Pago):** si en la rama de integración la liberación/reintegro real se ordena a la pasarela, la llamada va **dentro de este método** (o detrás del puerto que ya use US-15/US-44), con **clave de idempotencia = `claimId`**. Si la pasarela falla: lanzar `FundsExecutionException` → rollback → `502 FUNDS_EXECUTION_FAILED`, el reclamo sigue `IN_REVIEW`.

**Estado final del booking** (decisión provisional, confirmar con el equipo):

| `favoredParty` | `BookingStatus` |
|---|---|
| `TUTOR` | `COMPLETED` |
| `STUDENT` | `NOT_FULFILLED_BY_TUTOR` si el motivo del alumno indica ausencia del tutor; en cualquier otro caso `SESSION_NOT_HELD` |

**Guard en el endpoint existente:** `POST /api/v1/admin/bookings/{bookingId}/funds/retry` (`AdminFundsControllerImpl`) debe rechazar (`409 CLAIM_STILL_OPEN`) si la reserva tiene un reclamo `OPEN`/`IN_REVIEW`, para que no se pueda desbloquear fondos por fuera de US-50. Queda como herramienta de recuperación manual; el frontend **no** lo usa.

---

## 5. Notificaciones

```java
public record SessionClaimResolvedEvent(
        UUID claimId, UUID bookingId,
        UUID tutorUserId, UUID studentUserId,
        ClaimFavoredParty favoredParty, String justification,
        String subjectName, LocalDate sessionDate, BigDecimal movedAmount   // movedAmount = transferredAmount; 0 si sesión gratuita
) {}
```

Se publica dentro de la transacción y se consume `AFTER_COMMIT` (como `UserNotificationListener`). `IEmailService` incorpora un método de negocio nuevo (p. ej. `sendClaimResolvedEmail`) con plantilla en `EmailBuilder`:

- **Ambas partes** reciben: materia y fecha de la sesión, **a favor de quién** se resolvió, **la justificación textual**, y qué pasó con los fondos ("se liberaron al tutor" / "se reintegraron al alumno").
- Cada parte ve solo lo que le corresponde: **no** se incluyen teléfono, DNI ni email de la contraparte.
- Fallo de mail: log y continuar (la resolución ya está confirmada).
- Punto de enganche para el centro in-app (US-35), no presente en `main`.

**Impacto en US-46/US-47 (tutor y alumno):** sus detalles de reclamo deben exponer, cuando `RESOLVED`, `resolution { favoredParty, justification, resolvedAt }`. Es parte del criterio 9 ("visible para ambas partes… queda registrada de forma permanente"). Este cambio se hace en los endpoints existentes de esas historias.

---

## 6. Códigos de error

| HTTP | `detail` | Cuándo |
|---|---|---|
| 400 | `VALIDATION_ERROR` | Falta `favoredParty`, justificación vacía o > 1000, rango inválido, `status` desconocido |
| 403 | `NOT_ASSIGNED_TO_ADMIN` | Resolver o ver contacto de un reclamo asignado a otro |
| 403 | `ACCESS_DENIED` | Rol distinto de ADMIN |
| 404 | `CLAIM_NOT_FOUND` | Reclamo inexistente |
| 409 | `CASE_ALREADY_TAKEN` / `CASE_NOT_PENDING` | Tomar un reclamo ya tomado o cerrado |
| 409 | `CASE_NOT_IN_REVIEW` | Contacto o resolución sin haberlo tomado |
| 409 | `CASE_ALREADY_RESOLVED` | Resolver o tomar un reclamo ya resuelto |
| 409 | `FUNDS_STATE_MISMATCH` | Estado de fondos incompatible con la resolución |
| 409 | `BOOKING_IMMUTABLE` | Fondos ya en estado terminal (handler nuevo para `ImmutableBookingException`) |
| 409 | `CLAIM_STILL_OPEN` | `funds/retry` sobre una reserva con reclamo abierto |
| 502 | `FUNDS_EXECUTION_FAILED` | Falla al mover los fondos (con rollback) |

El handler de `FundsExecutionException` devuelve siempre un mensaje genérico seguro en español ("No pudimos ejecutar la transferencia de fondos. El reclamo sigue en revisión; reintentá."), sin detalles de la pasarela.

## 7. Estructura de paquetes

```
com.knowlink.api.admin.claims/
├── controllers/
│   ├── interfaces/IAdminSessionClaimController.java
│   ├── implementations/AdminSessionClaimControllerImpl.java
│   ├── requests/ResolveSessionClaimRequest.java
│   └── responses/…                            # records de la sección 4
├── services/
│   ├── interfaces/IAdminSessionClaimService.java
│   └── implementations/AdminSessionClaimServiceImpl.java
├── repositories/ISessionClaimMetricsRepository.java   # puerto de lectura para US-33
├── data/enums/ClaimFavoredParty.java
└── listeners/SessionClaimNotificationListener.java

payments/services/FundsResolutionService.java    # + resolveByAdminDecision(...)
payments/controllers/implementations/AdminFundsControllerImpl.java   # + guard CLAIM_STILL_OPEN
<paquete de la entidad SessionClaim>             # + campos de 3.2 (según rama de US-51)
```

La entidad `SessionClaim`, su repositorio y `SessionClaimStatus` **permanecen donde los haya definido US-46/47/51**; este spec solo agrega campos y consultas (`IAdminSessionClaimRepository` en `admin/claims/repositories` con las consultas del listado y del bloqueo `FOR UPDATE`).

## 8. Impacto en el frontend (`admin-04`)

- Usar `claimId` y los estados `OPEN | IN_REVIEW | RESOLVED`.
- Resultado: botones **"A favor del tutor" / "A favor del alumno"** (`favoredParty = TUTOR | STUDENT`), justificación obligatoria (máx. 1000).
- **"Citar ambas partes" desaparece del alcance:** US-50 solo prevé consultar los datos de contacto para coordinar fuera de la plataforma.
- El detalle muestra el **resultado automático** y el **motivo** de cada parte; el mensaje vacío (`null`) se muestra como "No se registró un mensaje".
- "Consultar datos de contacto" solo cuando `permissions.canViewContact`; no hace falta para resolver.
- Modo lectura para reclamos de otro admin: usar `permissions`.
- Filtro de fechas = el botón de calendario del diseño (`from`/`to`, por defecto 30 días).
- Sesión gratuita: `heldAmount = 0`; el diálogo de confirmación debe adaptar su texto ("no hay fondos para transferir").
- Los datos de contacto no se cachean (`gcTime: 0`) y la respuesta ya viene con `no-store`.
- Error `502 FUNDS_EXECUTION_FAILED`: mantener el diálogo y permitir reintentar.

## 9. No hacer

- No crear ni modificar el flujo de alta de reclamos (US-46/47/51).
- No usar `retryAfterClaimResolution` ni `POST …/funds/retry` como mecanismo de resolución.
- No llamar a `releaseToTutor`/`refundToStudent` sin verificar que actualizan la fila existente.
- No resolver sin lock/condición atómica ni permitir que dos admins resuelvan el mismo reclamo.
- No permitir editar, revertir ni volver a resolver un reclamo `RESOLVED`.
- No marcar el reclamo `RESOLVED` si falló el movimiento de fondos.
- No incluir datos de contacto en el listado, el detalle ni los mails; solo en `/contact-info`.
- No loguear ni cachear datos personales (DNI, teléfono, email).
- No calcular montos ni retenciones en el frontend.
- No duplicar el porcentaje de retención: reutilizar el de `FundsLedgerService`.
- No exponer detalles de la pasarela ni `technicalMessage` en las respuestas.

## 10. Tests

- `AdminSessionClaimServiceTest`: listado (rango por defecto, `assignedToMe`, orden OPEN→IN_REVIEW→RESOLVED), `statusCounts` ignorando `status`, permisos por estado y por asignación.
- Concurrencia: dos admins hacen `assign` a la vez → un solo ganador.
- `resolve` a favor del tutor: fondos `RELEASED_TO_TUTOR` con 97 % (retención configurada), booking `COMPLETED`, claim `RESOLVED` con justificación y fecha, evento publicado.
- `resolve` a favor del alumno: fondos `REFUNDED_TO_STUDENT` con 100 %, booking según tabla, evento publicado.
- Validaciones: sin `favoredParty`, justificación vacía/espacios/> 1000, reclamo `OPEN`, asignado a otro, ya `RESOLVED` → códigos correctos y **sin cambios** en claim, fondos ni booking.
- Falla de fondos: `FundsExecutionException` → rollback; claim sigue `IN_REVIEW`; reintento exitoso no duplica movimientos (`ImmutableBookingException` no se dispara en el primer intento válido).
- Reserva sin `FundsTransfer` (gratuita): resuelve sin mover fondos y `heldAmount = 0`.
- `FundsResolutionServiceTest`: `resolveByAdminDecision` actualiza la fila existente (no inserta), valida `blockingClaimId`, respeta la restricción única.
- `contact-info`: solo `IN_REVIEW` y asignado; `Cache-Control: no-store`; auditoría registrada; 409/403 en los otros casos.
- Listener: ambos reciben mail con resultado y justificación; ninguno recibe datos de contacto de la contraparte.
- `AdminFundsController`: `funds/retry` rechazado con `CLAIM_STILL_OPEN` si hay reclamo abierto.
- Controller (MockMvc): 401/403/404/409/502, validación de body.

## 11. Definition of Done

- [ ] `./mvnw verify -B` en verde
- [ ] Modelo de la entidad de reclamo acordado con US-46/47/51 (una vs. dos declaraciones por reserva) y campos de la sección 3.2 agregados
- [ ] `automaticOutcome` persistido al crear reclamos (o `null` documentado para los previos)
- [ ] Los 5 endpoints documentados en Swagger (español) y probados
- [ ] `resolveByAdminDecision` implementado sobre la fila existente, idempotente y transaccional (verificada la restricción única)
- [ ] Integración con la pasarela de pago (o su punto de enganche) definida junto con US-15/US-44
- [ ] Guard `CLAIM_STILL_OPEN` en `funds/retry`
- [ ] Notificación por mail a ambas partes con resultado y justificación, sin datos de contacto
- [ ] Detalles de US-46/US-47 muestran la resolución cuando `RESOLVED`
- [ ] Auditoría (o logs estructurados) de toma, consulta de contacto y resolución, sin datos personales
- [ ] Puerto `ISessionClaimMetricsRepository` entregado para US-33
- [ ] Contrato publicado al frontend (`admin-04` actualizado)
