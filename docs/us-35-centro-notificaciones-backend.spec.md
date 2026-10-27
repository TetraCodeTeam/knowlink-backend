# US-35 — Centro de notificaciones (Backend)

## Metadata

| Campo | Valor |
|---|---|
| ID Historia | US-35 |
| Épica | — |
| Story Points | 8 (backend + frontend combinados) |
| Prioridad | BAJA |
| Módulo | notificaciones (navegación) |
| Historias relacionadas | US-28 / US-29 / US-30 (navbar por rol), US-10 (reserva), US-11 (cancelación), US-15 (liberación de fondos), US-18 (calificación), US-22 (validación de materia), US-24 / US-27 (material y denuncias), US-41 (confirmación de sesión) |
| Repo | `TetraCodeTeam/knowlink-backend` |
| Spec par | `us-35-centro-notificaciones-frontend.spec.md` |
| Verificado contra | `knowlink-backend@feature/notifications-center` (2026-10-24) |

**Convención de idioma:** identificadores de código (clases, enums, métodos, campos, paquetes, logs) en **inglés**. Textos orientados al usuario (el `message` de cada notificación, mensajes de excepción) en **español**. Swagger (`@Operation`, `@Tag`) en español por decisión de equipo.

---

## Actualización 2026-10-24 — notificaciones separadas por rol

Una cuenta dual (STUDENT/TUTOR) comparte `userId`, por lo que el feed se separa además por el **rol activo**:

- `user_notification` agrega `recipient_role` (`Role`, NOT NULL) y el rol pasa a formar parte de los índices.
- El rol destinatario se decide en el origen: reservas y fondos usan la rama del evento (`booking.student` → `STUDENT`, `booking.tutor` → `TUTOR`); soporte usa el rol del reclamante; `notifyAdmins` escribe `ADMIN`.
- `GET /notifications`, `GET /unread-count`, `GET /support/unread-count`, `PATCH /read-all` y el marcado masivo de soporte filtran por `principal.getUser().getRole()`.
- `unread-count` devuelve `{ "count", "otherRoleCount" }`: `count` son las no leídas del rol activo y `otherRoleCount` las del otro rol STUDENT/TUTOR (0 para ADMIN).
- `markAsRead` no cambia: sigue validando pertenencia por `recipient_user_id`.
- No hay Flyway: antes de desplegar se ejecuta `src/main/resources/migration-user-notification-recipient-role.sql`, que agrega `recipient_role`, hace backfill de las filas existentes (tipo administrativo, lado de la reserva o rol del destinatario) y recrea los índices. No borra notificaciones y no depende de `DDL_AUTO`.

---

## Contexto de dominio

Hoy el backend **no tiene ningún módulo de notificaciones persistidas**. Lo que existe son eventos de dominio publicados con `ApplicationEventPublisher` (`bookings/events`) y mails vía `EmailServiceImpl` (Brevo). Esta historia agrega:

1. Una entidad `Notification` **por usuario destinatario**, persistida en MySQL.
2. Una API REST para listar, contar no leídas y marcar como leída.
3. Un punto de entrada único, `INotificationService`, que cualquier módulo usa para crear notificaciones.
4. Listeners que traducen los eventos de dominio **ya existentes** a notificaciones.

Las historias US-11 y US-15 asumen un "módulo de notificaciones ya existente" (`com.knowlink.api.notificacion`, `NotificationService`). **No existe**: esta historia lo crea. Los specs de esas historias no se modifican; su integración se cierra acá (ver catálogo de eventos).

---

## Criterios de aceptación (Gherkin)

```gherkin
Feature: Centro de notificaciones (API)

  Background:
    Given un usuario autenticado "U" con rol STUDENT, TUTOR o ADMIN

  Scenario: Conteo de no leídas por rol activo (CA 1)
    Given "U" tiene 3 notificaciones sin leer de su rol activo y 2 leídas
    When consulta GET /api/v1/notifications/unread-count
    Then recibe { "count": 3, "otherRoleCount": 0 }
    And si no tiene ninguna sin leer recibe { "count": 0 }

  Scenario: Cuenta dual ve solo el rol activo
    Given "U" tiene rol activo STUDENT y notificaciones STUDENT y TUTOR sin leer
    When consulta GET /api/v1/notifications y GET /api/v1/notifications/unread-count
    Then el listado y "count" solo incluyen las notificaciones STUDENT
    And "otherRoleCount" informa las no leídas TUTOR

  Scenario: Listado ordenado de más reciente a más antigua (CA 2)
    Given "U" tiene notificaciones creadas en distintos momentos
    When consulta GET /api/v1/notifications
    Then recibe una página de notificaciones ordenadas por createdAt descendente
    And cada elemento indica si está leída o no

  Scenario: Contenido de cada notificación (CA 3 y 4)
    When "U" consulta una notificación
    Then recibe type, message en español, createdAt, read y referenceId
    And con type y referenceId el cliente puede resolver el enlace a la sección relacionada

  Scenario: Marcar como leída al abrirla (CA 5)
    Given "U" tiene una notificación sin leer "N"
    When invoca PATCH /api/v1/notifications/{N}/read
    Then "N" queda con readAt informado
    And el conteo de no leídas de "U" baja en 1
    And repetir la llamada sobre "N" responde 204 sin cambiar readAt (idempotente)

  Scenario: Aislamiento entre usuarios
    Given "N" pertenece a otro usuario
    When "U" invoca PATCH /api/v1/notifications/{N}/read
    Then recibe 404 con detail "NOTIFICATION_NOT_FOUND"
    And "N" no se modifica
    And GET /api/v1/notifications nunca incluye notificaciones de otros usuarios

  Scenario: Aislamiento entre roles del mismo usuario
    Given "N" es una notificación TUTOR de "U"
    When "U" consulta GET /api/v1/notifications con rol activo STUDENT
    Then "N" no aparece en el listado ni suma en "count"
    And sí suma en "otherRoleCount"

  Scenario: Reserva confirmada (CA 6 — US-10)
    When se crea una reserva
    Then el alumno y el tutor reciben una notificación BOOKING_CONFIRMED

  Scenario: Reserva cancelada (CA 6 — US-11)
    When una de las partes cancela una reserva confirmada
    Then ambas partes reciben una notificación BOOKING_CANCELLED

  Scenario: Eventos de sesión (CA 6 — US-41)
    When se publica SessionConfirmationReminderEvent, SessionNotConfirmedEvent o SessionCompletionNotificationEvent
    Then ambas partes reciben la notificación de tipo correspondiente

  Scenario: Transferencia de fondos ejecutada (CA 6 — US-15)
    When el ledger registra una liberación o devolución final
    Then ambas partes reciben FUNDS_TRANSFERRED con monto y concepto en el mensaje

  Scenario: Notificaciones a administradores (CA 7)
    When un módulo invoca notifyAdmins(...)
    Then cada usuario con rol ADMIN y cuenta activa recibe su propia notificación

  Scenario: Un fallo al notificar no rompe el flujo de negocio
    Given falla la persistencia de una notificación
    When el evento de dominio ya fue confirmado (commit)
    Then el error se registra en el log
    And la operación de negocio original permanece intacta

  Scenario: Sin duplicados por reintento
    Given ya existe una notificación con el mismo (destinatario, dedupeKey)
    When se intenta crear otra igual
    Then no se crea una segunda
```

---

## Stack confirmado

- Java 21 + Spring Boot 3.5.3, Spring Data JPA, MySQL 8 (producción) / H2 (perfil `test`).
- Eventos: `ApplicationEventPublisher` + `@TransactionalEventListener(AFTER_COMMIT)` + `@Async("taskExecutor")` — **mismo patrón** que `UserNotificationListener` y `AttendanceConfirmationListener`.
- Sin colas, sin WebSocket/SSE, sin Firebase. El cliente consulta por polling (decisión de diseño, ver más abajo).
- Sin Flyway/Liquibase en el proyecto: el esquema lo genera Hibernate (`ddl-auto: ${DDL_AUTO}`).

---

## Advertencias de dependencias (verificadas en el código)

1. **IDs son `UUID`**, no `Long` (convención del proyecto; los specs viejos con `Long` no aplican).
2. **No hay Flyway.** La tabla `user_notification` nace por `ddl-auto`. Antes de desplegar con `recipient_role` se ejecuta `src/main/resources/migration-user-notification-recipient-role.sql`: agrega la columna como NULL, hace backfill, aplica NOT NULL y recrea los índices. Es idempotente, funciona con `DDL_AUTO=none`, `validate` o `update` y no borra filas.
3. **Eventos que faltan.** No existe evento de "reserva creada" ni de "transferencia de fondos procesada". Se crean en esta historia (ver catálogo).
4. **`BookingCancelledEvent` no informa quién canceló.** Se extiende con `cancelledByUserId`. Único publicador: `BookingCancellationServiceImpl` (~línea 95). Nota: su campo `tutorProfileId` en realidad recibe `booking.getTutor().getUserId()`; no tocarlo acá.
5. **`SessionConfirmationTokenGeneratedEvent` lleva el token en claro (`rawToken`).** El listener de notificaciones **no debe** suscribirse a este evento ni persistir el token jamás.
6. **Módulos que todavía no existen en el backend:** validación de materias (US-22), material/denuncias (US-24/27), calificaciones (US-18), administración (solo existe `AdminFundsControllerImpl`). No se crean eventos especulativos: se deja el `NotificationType` definido y cada historia llama a `INotificationService` cuando se implemente.
7. **Revisar `FundsLedgerService`:** tiene `@Value("")` en `systemRetentionPercentage` (valor vacío, sin propiedad). Si esa inyección no está resuelta, `releaseToTutor` / `refundToStudent` fallan y `FundsTransferProcessedEvent` nunca se publicaría. No es parte de esta historia, pero bloquea probar `FUNDS_TRANSFERRED` de punta a punta.
8. **Seguridad:** `SecurityConfig` ya exige autenticación en todo lo que no está en `PUBLIC_WHITELIST`. Los endpoints nuevos **no** se agregan a la whitelist. No usar `@PreAuthorize` por rol: cualquier rol autenticado accede y el filtrado es por `userId` + rol activo (`principal.getUser().getRole()`).

---

## Decisiones de diseño

| Decisión | Elegido | Alternativa descartada | Motivo |
|---|---|---|---|
| Entrega al cliente | **Polling** (el front consulta `unread-count` cada 30 s) | SSE (existe en `events/`) | `EventSource` no envía el header `Authorization`; el SSE actual es público por tutor. Para una historia de prioridad BAJA, polling es suficiente y simple. |
| Texto del mensaje | Se **guarda ya armado en español** al crear | Guardar plantilla + parámetros | Es un snapshot histórico: si cambia un nombre o un texto, la notificación vieja no debe cambiar. |
| Enlace (CA 4) | Backend entrega `type` + `referenceId`; **el front resuelve la ruta** | Guardar `actionPath` en la fila | Las rutas viven en el front y cambian (hoy muchas son `UnderConstructionPage`). Una ruta guardada quedaría obsoleta. |
| Estado leída | `readAt` nullable | Booleano `read` | Da la fecha de lectura gratis; `read` se deriva en el response. |
| Destinatario + rol | `(recipient_id, recipient_role)` (una fila por usuario y rol) | Difusión con tabla de lecturas | Una cuenta dual (STUDENT/TUTOR) necesita feeds separados sin mezclar badges ni listados. |
| Marcar todas como leídas | **Incluido** (`PATCH /read-all`, acotado al rol activo) | - | Extensión 2026-10-24 para limpiar el badge del rol activo sin afectar al otro. |

---

## Modelo de datos

### Enum `NotificationType`
`com.knowlink.api.notifications.data.enums.NotificationType` — se persiste como `STRING`, así que agregar valores después no rompe filas existentes.

```java
public enum NotificationType {
    // Conectados en esta historia (el evento ya existe o se crea acá)
    BOOKING_CONFIRMED,
    BOOKING_CANCELLED,
    SESSION_CONFIRMATION_REMINDER,
    SESSION_NOT_CONFIRMED,
    SESSION_COMPLETED,
    FUNDS_TRANSFERRED,

    // Definidos acá; los conecta cada historia dueña con INotificationService
    SUBJECT_VALIDATION_UPDATED,          // US-22
    MATERIAL_REPORT_PROCESSED,           // US-24 / US-27
    RATING_ENABLED,                      // US-18
    ADMIN_VALIDATION_REQUEST_PENDING,    // US-22 (CA 7)
    ADMIN_MATERIAL_REPORT_RECEIVED       // US-27 (CA 7)
}
```

### Entidad `Notification`
Tabla `user_notification`.

| Campo (código) | Columna | Tipo | Notas |
|---|---|---|---|
| `notificationId` | `notification_id` | UUID | PK, `GenerationType.UUID` |
| `recipient` | `recipient_user_id` | `User` (`@ManyToOne(fetch = LAZY)`) | not null |
| `recipientRole` | `recipient_role` | `Role` (`EnumType.STRING`, length 10) | not null; rol al que pertenece la notificación |
| `type` | `notification_type` | `NotificationType` (`EnumType.STRING`, length 40) | not null |
| `resourceId` | `resource_id` | UUID | nullable; id de la entidad relacionada (booking, etc.) |
| `title` | `title` | `String` (length 120) | nullable; español |
| `message` | `message` | `String` (length 500) | not null, español |
| `dedupeKey` | `dedupe_key` | `String` (length 150) | nullable |
| `createdAt` | `created_at` | `LocalDateTime` | not null, `LocalDateTime.now(AppTimeZone.ZONE)` |
| `readAt` | `read_at` | `LocalDateTime` | nullable; `null` = no leída |

Índices y restricciones:
- `idx_user_notification_recipient_created` → `(recipient_user_id, recipient_role, created_at)` - listado por rol.
- `idx_user_notification_recipient_read` → `(recipient_user_id, recipient_role, read_at)` - conteo por rol.
- `uk_user_notification_recipient_dedupe` → único `(recipient_user_id, dedupe_key)` (MySQL permite varios `NULL`).
- Migración: `src/main/resources/migration-user-notification-recipient-role.sql` agrega `recipient_role`, hace backfill por tipo/reserva/rol y recrea los índices; no borra filas y es idempotente.

---

## Ubicación de clases / paquetes

```
com.knowlink.api.notifications
├── controllers
│   ├── interfaces/INotificationController.java        // contrato + Swagger + @RequestMapping
│   ├── implementations/NotificationControllerImpl.java
│   └── responses/
│       ├── NotificationResponse.java                   // record
│       └── UnreadCountResponse.java                    // record
├── data
│   ├── models/Notification.java
│   ├── enums/NotificationType.java
│   └── mappers/NotificationMapper.java
├── repositories/INotificationRepository.java
├── services
│   ├── interfaces/INotificationService.java
│   └── implementations/NotificationServiceImpl.java
└── listeners
    ├── BookingNotificationListener.java                // eventos de bookings
    └── FundsNotificationListener.java                  // evento de payments

com.knowlink.api.bookings.events
└── BookingCreatedEvent.java                            // NUEVO
com.knowlink.api.payments.events
└── FundsTransferProcessedEvent.java                    // NUEVO (el paquete events no existe en payments)
```

### Cambios en código existente

| Archivo | Cambio |
|---|---|
| `BookingServiceImpl.createBooking` (línea ~74; la reserva se arma con `BookingMapper`, estado `BOOKED`) | Publicar `new BookingCreatedEvent(booking.getBookingId())` con `applicationEventPublisher` (ya inyectado) al terminar de persistir. |
| `BookingCancelledEvent` | Agregar el componente `UUID cancelledByUserId` al final del record. |
| `BookingCancellationServiceImpl` (~línea 95) | Pasar `currentUserId` como nuevo argumento. |
| `FundsLedgerService.releaseToTutor` / `refundToStudent` | Tras `save(transfer)`, publicar `FundsTransferProcessedEvent`. Inyectar `ApplicationEventPublisher`. |
| `IUserRepository` | Agregar `List<User> findAllByRoleAndAccountStatus(Role role, AccountStatus status)` (usar el valor activo real de `AccountStatus`). |

---

## Contrato de eventos y mensajes

Todos los listeners: `@Async("taskExecutor")` + `@TransactionalEventListener(phase = AFTER_COMMIT)`, `try/catch` que **solo loguea** (`log.error`), nunca relanza. Como los eventos de bookings llevan la entidad `Booking` ya cargada, **no** acceder a relaciones LAZY sin cargarlas: re-leer la reserva por `bookingId` dentro de un método `@Transactional(readOnly = true)` (o usar un fetch join) antes de leer `student` / `tutor` / nombres.

Fechas en mensajes: `dd/MM/yyyy` y `HH:mm`. Montos: `NotificationTextFormats.amount` con `DecimalFormat("#,##0.00", es-AR)`: siempre dos decimales (`$323,10`, `$2.100,00`).

| Evento origen | Destinatarios | Tipo | Mensaje (ejemplo) | `referenceId` | `dedupeKey` |
|---|---|---|---|---|---|
| `BookingCreatedEvent` (nuevo) | alumno | `BOOKING_CONFIRMED` | "Tu reserva con {tutor} para el {fecha} a las {hora} fue confirmada." | bookingId | `BOOKING_CONFIRMED:{bookingId}` |
| | tutor | `BOOKING_CONFIRMED` | "{alumno} reservó una clase para el {fecha} a las {hora}." | bookingId | idem |
| `BookingCancelledEvent` | quien canceló | `BOOKING_CANCELLED` | "Cancelaste la clase del {fecha} a las {hora}." | bookingId | `BOOKING_CANCELLED:{bookingId}` |
| | la otra parte | `BOOKING_CANCELLED` | "{quien canceló} canceló la clase del {fecha} a las {hora}." | bookingId | idem |
| `SessionConfirmationReminderEvent` | tutor | `SESSION_CONFIRMATION_REMINDER` | "Recordá confirmar la clase con {alumno} ingresando su código." | bookingId | `SESSION_CONFIRMATION_REMINDER:{bookingId}` |
| | alumno | `SESSION_CONFIRMATION_REMINDER` | "Recordá compartir tu código de confirmación con {tutor}." | bookingId | idem |
| `SessionNotConfirmedEvent` | ambos | `SESSION_NOT_CONFIRMED` | "La clase del {fecha} no fue confirmada." | bookingId | `SESSION_NOT_CONFIRMED:{bookingId}` |
| `SessionCompletionNotificationEvent` | ambos | `SESSION_COMPLETED` | "La clase del {fecha} finalizó. Si hubo algún problema, podés hacer un reclamo." | bookingId | `SESSION_COMPLETED:{bookingId}` |
| `FundsTransferProcessedEvent` (nuevo) | ambos | `FUNDS_TRANSFERRED` | "Se procesó una transferencia de {monto}: {concepto}." | bookingId | `FUNDS_TRANSFERRED:{concepto}:{bookingId}` |

> El reparto tutor/alumno del recordatorio sale de `BookingServiceImpl.confirmSession` (lo ejecuta el tutor) y `getConfirmationToken` (lo consulta el alumno). **Confirmar contra el spec de US-41** antes de fijar los textos; el contrato (tipo + ambos destinatarios + dedupe) no cambia.

`FundsTransferProcessedEvent`:
```java
public record FundsTransferProcessedEvent(
        UUID bookingId,
        FundsRecipient recipient,
        BigDecimal originalAmount,
        BigDecimal transferredAmount,
        BigDecimal retentionPercentage,
        FundsConcept concept
) {}
```
`BookingCreatedEvent`: `public record BookingCreatedEvent(UUID bookingId) {}`. `FundsConcept` (`payments/domain`) es el enum con el que se elige el mensaje; su `label()` se persiste en `funds_transfer.concept`. Los listeners hacen `switch` sobre el enum y nunca comparan el texto del concepto.

### Tipos que conectan otras historias (no implementar acá)

| Tipo | Quién lo dispara | Destinatario | Cómo |
|---|---|---|---|
| `SUBJECT_VALIDATION_UPDATED` | US-22, al alcanzar el promedio mínimo | tutor | `notifyUser(...)` |
| `MATERIAL_REPORT_PROCESSED` | US-24 / US-27 | quien denunció (y tutor afectado si corresponde) | `notifyUser(...)` |
| `RATING_ENABLED` | US-18, al habilitarse la calificación | las partes habilitadas | `notifyUser(...)` |
| `ADMIN_VALIDATION_REQUEST_PENDING` | US-22, nueva solicitud | todos los ADMIN | `notifyAdmins(...)` |
| `ADMIN_MATERIAL_REPORT_RECEIVED` | US-27, nueva denuncia | todos los ADMIN | `notifyAdmins(...)` |

---

## Servicio

```java
public interface INotificationService {

    /** Notificación de soporte ligada a un recurso. */
    void createSupportNotification(UUID recipientUserId, Role recipientRole, NotificationType type,
                                   UUID resourceId, String title, String message);

    /** Crea una notificación para un usuario en un rol. Idempotente por (recipient, dedupeKey). */
    void notifyUser(UUID recipientUserId, Role recipientRole, NotificationType type, String title,
                    String message, UUID referenceId, String dedupeKey);

    /** Crea una notificación para cada ADMIN activo (CA 7). */
    void notifyAdmins(NotificationType type, String title, String message,
                      UUID referenceId, String dedupeKey);

    PagedResponse<NotificationResponse> getNotifications(UUID userId, Role activeRole,
            NotificationReadFilter readFilter, Pageable pageable);

    UnreadCountResponse getUnreadCount(UUID userId, Role activeRole);

    long countUnreadSupportForUser(UUID userId, Role activeRole);

    void markAsRead(UUID userId, UUID notificationId);

    void markAllAsRead(UUID userId, Role activeRole);

    void markSupportAsRead(UUID userId, Role activeRole);
}
```

Reglas de `NotificationServiceImpl`:
- `notifyUser` / `notifyAdmins`: `@Transactional(propagation = Propagation.REQUIRES_NEW)` (se llaman desde listeners AFTER_COMMIT).
- Si `dedupeKey != null` y existe `(recipient, dedupeKey)` → no hace nada (log `debug`). Capturar `DataIntegrityViolationException` por carrera contra la restricción única y tratarlo igual.
- `notifyAdmins` en cero admins: no es error; log `warn` y retorna. Para admins, `dedupeKey` se usa tal cual por cada destinatario.
- `getNotifications`: orden fijo `createdAt DESC, notificationId DESC` (desempate estable); **ignora** cualquier `sort` que mande el cliente. `size` máximo **50** (si pide más, se recorta; default 20).
- `markAsRead`: busca por `(notificationId, recipient.userId)` con `orElseThrow(() -> new ResourceNotFoundException("NOTIFICATION_NOT_FOUND", "La notificación no existe.", "Notification " + id + " not found for user " + userId))`. Si `readAt == null` lo setea con `LocalDateTime.now(AppTimeZone.ZONE)`; si ya estaba leída no hace nada. `@Transactional`.
- Todo acceso filtra por el `userId` del `UserPrincipal` y el rol activo (`principal.getUser().getRole()`); **nunca** por un id o rol recibido del cliente.

### Repository

```java
Page<Notification> findByRecipient_UserIdAndRecipientRole(
        UUID userId, Role recipientRole, Pageable pageable);

Page<Notification> findByRecipient_UserIdAndRecipientRoleAndReadAtIsNull(
        UUID userId, Role recipientRole, Pageable pageable);

Page<Notification> findByRecipient_UserIdAndRecipientRoleAndReadAtIsNotNull(
        UUID userId, Role recipientRole, Pageable pageable);

List<Notification> findByRecipient_UserIdAndRecipientRoleAndReadAtIsNull(
        UUID userId, Role recipientRole);

List<Notification> findByRecipient_UserIdAndRecipientRoleAndTypeInAndReadAtIsNull(
        UUID userId, Role recipientRole, List<NotificationType> types);

long countByRecipient_UserIdAndRecipientRoleAndReadAtIsNull(UUID userId, Role recipientRole);

Optional<Notification> findByNotificationIdAndRecipient_UserId(UUID notificationId, UUID userId);

boolean existsByRecipient_UserIdAndDedupeKey(UUID userId, String dedupeKey);
```
El `Pageable` lo arma el service con `Sort.by(DESC, "createdAt").and(Sort.by(DESC, "notificationId"))`.

---

## Endpoints

Prefijo `/api/v1/notifications`. Cualquier rol autenticado. Usar `@AuthenticationPrincipal UserPrincipal principal` y `principal.getUser().getUserId()` (como `BookingCancellationControllerImpl`). `@Tag(name = "Notificaciones", ...)`, `@Operation` + `@ApiResponse` en cada uno.

| Método | Ruta | Descripción | Respuesta |
|---|---|---|---|
| GET | `/api/v1/notifications?page=0&size=20&status=ALL&unreadOnly=false` | Página de notificaciones del rol activo, más recientes primero. `page` base 0. | 200 `PagedResponse<NotificationResponse>` |
| GET | `/api/v1/notifications/unread-count` | No leídas del rol activo (`count`) y del otro rol (`otherRoleCount`). | 200 `UnreadCountResponse` |
| GET | `/api/v1/notifications/support/unread-count` | No leídas de soporte del rol activo. | 200 `long` |
| PATCH | `/api/v1/notifications/{notificationId}/read` | Marca como leída. Idempotente. | 204 sin cuerpo; 404 `NOTIFICATION_NOT_FOUND` |
| PATCH | `/api/v1/notifications/read-all` | Marca todas las del rol activo como leídas. | 204 sin cuerpo |

```java
public record NotificationResponse(
        UUID notificationId,
        NotificationType type,
        String title,
        String message,
        UUID referenceId,
        boolean read,
        LocalDateTime createdAt,
        LocalDateTime readAt
) {}

public record UnreadCountResponse(long count, long otherRoleCount) {}
```

Errores en formato del proyecto: `{ "status": 404, "message": "La notificación no existe.", "detail": "NOTIFICATION_NOT_FOUND" }`. `ResourceNotFoundException` ya está manejada en `GlobalExceptionHandler`; no agregar handlers nuevos. Un id de otro usuario responde **404** (no 403) para no revelar existencia.

---

## No hacer

- No guardar ni loguear `rawToken` ni ningún dato sensible (token, DNI, email, teléfono) en `message`.
- No suscribirse a `SessionConfirmationTokenGeneratedEvent`.
- No crear eventos ni listeners para US-18, US-22, US-24 o US-27 (sus módulos no existen en el backend).
- No agregar los endpoints a `PUBLIC_WHITELIST`.
- No usar `@PreAuthorize` por rol en estos endpoints (cualquier rol autenticado; filtrar por usuario y rol activo).
- No aceptar `userId` ni `recipientId` por path, query o body.
- No acceder al repository desde el controller ni poner lógica en el controller.
- No relanzar excepciones desde los listeners ni dejar que un fallo de notificación afecte la transacción del flujo de negocio.
- No leer relaciones LAZY de la `Booking` del evento fuera de una transacción (`open-in-view` está en `false`).
- No implementar SSE, WebSocket ni colas.
- No enviar mails desde esta historia (los mails siguen su propio flujo con `IEmailService`).
- No usar `@Autowired` en campos ni `Optional.get()`.
- No agregar Flyway/Liquibase para esta tabla.
- No implementar borrado/archivado de notificaciones. `PATCH /read-all` y el marcado masivo de soporte sí están incluidos.

---

## Fuera de alcance / decisiones pendientes

1. **Marcado masivo:** implementado para notificaciones generales (`PATCH /read-all`) y soporte; siempre acotado al rol activo.
2. **Retención / limpieza** de notificaciones viejas: hay un precedente (`PastAvailabilityCleanupJob`) si más adelante se decide.
3. **Código de confirmación disponible:** hoy nadie consume `SessionConfirmationTokenGeneratedEvent`. Si se quiere avisar "ya podés ver tu código", se agrega un tipo nuevo **sin** incluir el código en el mensaje.

---

## Definition of Done

- [x] `recipient_role` NOT NULL, índices y queries filtran por rol activo en listado, conteo y marcado masivo
- [x] Las notificaciones se crean con el rol de la rama de origen (reserva/fondos/soporte) y ADMIN para administradores
- [x] `unread-count` devuelve `count` del rol activo y `otherRoleCount` del otro rol STUDENT/TUTOR (0 para ADMIN)
- [x] Tests de separación por rol y cuenta dual (CP-06) en service, repository e integración
- [x] Migración idempotente en `src/main/resources` (agrega `recipient_role`, backfill sin borrar filas) y seeds anonimizados en `docs/seeds/`
- [ ] `Notification`, `NotificationType`, repository, mapper, service (interface + impl), controller (interface + impl) y responses creados con la estructura del módulo
- [x] Índices y restricción única definidos en la entidad; la migración crea el esquema sin depender de `DDL_AUTO`
- [ ] `BookingCreatedEvent` y `FundsTransferProcessedEvent` creados y publicados desde sus puntos de origen
- [ ] `BookingCancelledEvent` extendido con `cancelledByUserId` y publicador actualizado (sin romper compilación de otros usos)
- [ ] `BookingNotificationListener` y `FundsNotificationListener` cubren los 6 tipos del catálogo, ambos destinatarios, mensajes en español
- [ ] Listeners con `AFTER_COMMIT` + `@Async("taskExecutor")`, sin relanzar excepciones, sin acceso LAZY fuera de transacción
- [ ] `notifyAdmins` resuelve ADMIN activos y es idempotente por destinatario
- [ ] Los endpoints documentados con `@Operation` / `@ApiResponse`, sin entrada en `PUBLIC_WHITELIST`
- [ ] Orden `createdAt DESC` con desempate estable; `size` limitado a 50
- [ ] Aislamiento entre usuarios verificado (listado, conteo y `read` ajeno → 404)
- [ ] Tests unitarios de `NotificationServiceImpl`: crear, dedupe, `notifyAdmins` (con y sin admins), `markAsRead` (propia, ya leída, ajena), conteo, `size` máximo
- [ ] Test de repository (`@DataJpaTest`, H2): orden descendente, `unreadOnly`, conteo, unicidad `(recipient, dedupeKey)`
- [ ] Tests de listeners: un evento → notificación para cada destinatario correcta; una excepción en el service no se propaga
- [ ] Test de integración: crear reserva → `GET /notifications` del alumno y del tutor muestra `BOOKING_CONFIRMED`; `PATCH …/read` baja `unread-count`
- [ ] Verificado a mano que ningún mensaje de notificación contiene el token de confirmación
- [ ] Commits en español con prefijo convencional (`feat:`, `test:`, `docs:`)