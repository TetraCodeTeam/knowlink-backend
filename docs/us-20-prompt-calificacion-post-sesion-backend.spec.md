# US-20 — Recibir prompt de calificación post-sesión (Backend)

## Metadata

| Campo | Valor |
|---|---|
| ID Historia | US-20 |
| Épica | — |
| Story Points | 3 (backend + frontend combinados) |
| Prioridad | BAJA |
| Módulo | calificaciones (con notificaciones y reservas) |
| Historias relacionadas | US-18 (calificar sesión), US-35 (centro de notificaciones), US-41 (confirmación de sesión) |
| Repo / rama | `TetraCodeTeam/knowlink-backend` — `feature/notifications-center` |
| Spec par | `us-20-prompt-calificacion-post-sesion-frontend.spec.md` |
| Verificado contra | `knowlink-backend@0ef502f` (`feature/notifications-center`) |

**Convención de idioma:** identificadores de código (clases, enums, métodos, campos, paquetes, logs) en **inglés**. Textos orientados al usuario (título/mensaje de la notificación, mensajes de excepción) en **español**. Swagger en español.

---

## Contexto: esto es un spec de brechas

La rama **ya implementa buena parte de US-20** (con US-18 y US-35). Este spec no rehace eso: fija lo que ya cumple, corrige lo que contradice los criterios y agrega lo que falta.

### Lo que ya existe (verificado en el código)

- `BookingNotificationListener.handleSessionCompleted` crea una notificación `RATING_ENABLED` para alumno y tutor con título `"Calificá tu sesión de {materia}"` y mensaje `"Contanos cómo fue tu experiencia con {otra parte}. Tenés 24 horas para calificar."`, `referenceId = bookingId`, `dedupeKey = "RATING_ENABLED:{bookingId}"`.
- `POST /api/v1/bookings/{bookingId}/ratings` (`RatingsControllerImpl` → `RatingServiceImpl.submitRating`): exige reserva en `COMPLETED`, valida que el usuario participe, `score` 1–5 obligatorio (`CreateRatingRequest`), `comment` opcional (máx. 2000), y responde **409 `RATING_ALREADY_SUBMITTED`** si el usuario ya calificó (restricción única `uk_rating_booking_rater`).
- `GET /api/v1/bookings/{bookingId}` devuelve el detalle (otra parte, materia, fecha) que el formulario necesita.
- Idempotencia de notificación por `(destinatario, dedupeKey)` en `NotificationServiceImpl.createNotification`.

### Brechas contra los criterios

| CA | Estado | Brecha |
|---|---|---|
| 1 — Notificación a ambas partes con "Calificá tu sesión de [Materia]" | **Parcial** | Se emite desde `SessionCompletionNotificationEvent`, que `BookingCompletionNotificationJob` publica **al terminar el horario de la clase** para reservas en `BOOKED`, `IN_PROGRESS`, `COMPLETED` y `NOT_CONFIRMED`, sin importar si se confirmó. Además `resolveSubjectName` puede devolver `null` ("…de null"). |
| 2 — Solo si la sesión fue realizada; nunca en canceladas | **Incumple** | Hoy se envía a sesiones aún no confirmadas o `NOT_CONFIRMED`; el alumno abre el formulario y `submitRating` responde 400 "Solo podés calificar una sesión realizada". Las canceladas sí quedan excluidas (el job no las toma). |
| 3 — Si ya calificó, no se reenvía ni queda pendiente | **Parcial** | No se reenvía (dedupe ✓), pero la notificación sigue **sin leer** con acción "Calificar" aunque ya calificó. El front lo tapa con `localStorage` (por navegador, no por usuario real). |
| 4 — Vigencia de 24 h; luego expira y queda sin calificación | **Incumple** | `submitRating` **no valida plazo**: `RatingServiceImpl` acepta calificaciones pasadas las 24 h (hay un test que lo afirma: `ratingAfterTwentyFourHoursIsImmediatelyVisible`). La notificación no tiene vencimiento. El único control de 24 h está en el front. |
| 5 — Enlace directo al formulario | Cumple en front | El backend ya entrega `type` + `referenceId` (= bookingId). Ver spec de front. |
| 6 — Datos de sesión + puntaje 1–5 + comentario opcional; sin puntaje no se envía | Cumple | `CreateRatingRequest` (`@NotNull @Min(1) @Max(5)`). |
| 7 — Confirmación y formulario bloqueado | Cumple en back | 409 ante segundo intento. Falta que el **detalle de la reserva informe si ya calificó**, para no depender de `localStorage`. |

---

## Criterios de aceptación (Gherkin)

```gherkin
Feature: Prompt de calificación post-sesión (API)

  Scenario: Sesión realizada habilita la calificación para ambas partes (CA 1)
    Given una reserva "B" que pasa a COMPLETED al confirmarse con el token
    When se confirma la sesión
    Then el alumno y el tutor reciben una notificación RATING_ENABLED
    And el título es "Calificá tu sesión de {materia}"
    And referenceId es el bookingId de "B"
    And cada notificación indica que vence 24 horas después del fin de la sesión o de su confirmación, lo que ocurra último

  Scenario: Sesión no confirmada o cancelada no genera prompt (CA 2)
    Given una reserva en estado CANCELLED, NOT_CONFIRMED, BOOKED o IN_PROGRESS
    When termina el horario de la clase o se cancela
    Then ninguna de las partes recibe RATING_ENABLED

  Scenario: Sin duplicados (CA 3)
    Given ya existe RATING_ENABLED para (destinatario, "B")
    When se vuelve a publicar el evento de confirmación
    Then no se crea una segunda notificación

  Scenario: Calificar marca la notificación como resuelta (CA 3)
    Given "U" tiene RATING_ENABLED sin leer para "B"
    When "U" envía POST /api/v1/bookings/{B}/ratings con score válido dentro del plazo
    Then la notificación de "U" queda leída y con actionStatus COMPLETED
    And el conteo de no leídas de "U" baja en 1
    And la notificación de la otra parte no cambia

  Scenario: El plazo vence (CA 4)
    Given pasaron más de 24 horas desde el fin de la sesión o su confirmación
    And "U" no calificó
    When "U" envía POST /api/v1/bookings/{B}/ratings
    Then recibe 422 con detail "RATING_WINDOW_EXPIRED"
    And no se crea ningún Rating
    And la notificación de "U" se informa con actionStatus EXPIRED
    And esa notificación deja de contar como no leída

  Scenario: Plazo inclusivo
    Given pasaron exactamente 24 horas
    When "U" envía el POST
    Then la calificación se acepta

  Scenario: Detalle de la reserva informa el estado de calificación (CA 7)
    When "U" consulta GET /api/v1/bookings/{B}
    Then la respuesta incluye ratingStatus y ratingDeadline
    And ratingStatus es SUBMITTED si "U" ya calificó, EXPIRED si venció sin calificar, PENDING si puede calificar, NOT_AVAILABLE si la reserva no está COMPLETED

  Scenario: Validaciones del formulario (CA 6)
    When "U" envía POST sin score, o con score 0 o 6
    Then recibe 400 y no se crea el Rating
    When "U" envía score válido sin comentario
    Then recibe 201 y el Rating se crea con comment null

  Scenario: Segundo envío bloqueado (CA 7)
    Given "U" ya calificó "B"
    When "U" vuelve a enviar el POST
    Then recibe 409 con detail "RATING_ALREADY_SUBMITTED" y el Rating original no cambia

  Scenario: Un fallo al notificar no rompe el flujo de negocio
    Given falla la creación de la notificación RATING_ENABLED
    Then se registra en el log
    And la confirmación de la sesión y el registro de la calificación permanecen intactos
```

---

## Stack confirmado

Java 21 + Spring Boot 3.5, Spring Data JPA, MySQL 8 (prod) / H2 (tests). Eventos con `ApplicationEventPublisher` + `@TransactionalEventListener(AFTER_COMMIT)` + `@Async("taskExecutor")` (mismo patrón que `BookingNotificationListener`). Sin colas, sin Flyway (esquema por Hibernate, `ddl-auto: ${DDL_AUTO}`).

---

## Advertencias de dependencias (verificadas en el código)

1. **`DDL_AUTO` y las columnas nuevas.** Se agregan `expires_at` y `resolved_at` a la tabla `user_notification` ya existente. Con `update` Hibernate las crea; si en Render está en `validate` o `none`, hay que hacer el `ALTER TABLE` a mano **antes** de desplegar. Ambas columnas son nullable: las filas viejas siguen siendo válidas.
2. **Las notificaciones `RATING_ENABLED` ya creadas** (emitidas por el flujo viejo) quedan con `expires_at = NULL`. El mapper las trata como `PENDING` sin vencimiento. Aceptable en una base de pruebas; si se quiere limpiar, un `UPDATE` único setea `expires_at` desde la reserva.
3. **Cambia la semántica de US-18.** Hoy `submitRating` acepta calificar pasadas las 24 h (la calificación queda visible al instante). CA 4 exige lo contrario. Hay que **avisar al responsable de US-18** y actualizar los tests `ratingAfterTwentyFourHoursIsImmediatelyVisible` y `ratingAtExactTwentyFourHourBoundaryIsVisible` (el segundo sigue valiendo: el borde es inclusivo). `RatingReputationServiceImpl.revealExpiredRatings` y `isBlindReviewDeadlineElapsed` **no se tocan**: siguen revelando calificaciones ocultas cuando se cumple el plazo.
4. **"Vencimiento automático del plazo" (CA 2).** Hoy el único camino a `COMPLETED` es `BookingServiceImpl.confirmSession` (token del tutor). Al vencer el plazo del token, `BookingConfirmationExpirationJob` pasa a `NOT_CONFIRMED`, estado que `submitRating` rechaza. Este spec interpreta que **solo `COMPLETED` habilita la calificación**. Si el PO quiere decir otra cosa (p. ej. calificar también sesiones `NOT_CONFIRMED`), el criterio cambia y hay que rehacer CA 2 y `submitRating`. **Confirmar con el PO.**
5. **Reloj.** `RatingServiceImpl` usa `Clock` inyectado; el resto del módulo de notificaciones usa `LocalDateTime.now(AppTimeZone.ZONE)`. Para esta historia usar `Clock` donde se decide el plazo (testeable) y `AppTimeZone.ZONE` solo al construir el `Clock` de producción, como ya está.
6. **La confirmación puede ocurrir antes del fin de la clase** (`validateCanConfirmSession` permite desde `sessionStart` hasta `confirmationTokenExpiration` = fin + 10 min). Por eso el inicio del plazo es `max(fin de sesión, confirmedAt)`; coincide con el criterio que ya usa `RatingQueryFragments.DEADLINE_ELAPSED`.
7. **No leer relaciones LAZY fuera de transacción** (`open-in-view: false`). `BookingSnapshot` se arma en `BookingNotificationContext` con `@Transactional(readOnly = true)`.
8. **Seguridad:** `SecurityConfig` ya exige autenticación fuera de `PUBLIC_WHITELIST`; no se agregan endpoints nuevos.

---

## Decisiones de diseño

| Decisión | Elegido | Alternativa descartada | Motivo |
|---|---|---|---|
| Disparador | `SessionConfirmedEvent` (reserva ya `COMPLETED`) | Seguir con `SessionCompletionNotificationEvent` del job | El job dispara por horario, antes de saber si se confirmó. Con el evento de confirmación el estado es `COMPLETED` y el formulario funciona. |
| Inicio del plazo | `max(fin de sesión, confirmedAt)` + 24 h | 24 h desde la creación de la notificación | Es la misma regla que ya usa el backend para revelar calificaciones y la que usa el front hoy (`getRatingDeadline`); una sola fuente de verdad. |
| Vencimiento | **Derivado** (`expiresAt` vs. `now`), sin job | Job que marque vencidas | Sin trabajo programado nuevo; el estado es consistente en cada lectura. |
| Estado de la acción | Campo `actionStatus` calculado en la respuesta (`NONE`/`PENDING`/`COMPLETED`/`EXPIRED`) | Que el front deduzca por fechas | El front no necesita reglas de negocio ni reloj propio. |
| Cierre al calificar | Evento `RatingSubmittedEvent` + listener `AFTER_COMMIT` | Llamar al servicio de notificaciones dentro de la transacción de `submitRating` | Un fallo de notificación no debe revertir la calificación (mismo criterio que US-35). |
| Estado de calificación en detalle | `ratingStatus` + `ratingDeadline` en `BookingHistoryDetailResponse` | Seguir con `localStorage` en el front | `localStorage` es por navegador: otro dispositivo vuelve a ofrecer calificar y recién ahí falla con 409. |
| Notificación vencida | Se **conserva** en el historial como `EXPIRED` y **no cuenta como no leída** | Borrarla | CA 4 pide que "la opción expire", no que desaparezca; evita un badge permanente. |
| Error de plazo vencido | `RatingWindowExpiredException extends UnprocessableEntityException` → 422 | `ValidationException` (400 genérico) | Mismo patrón que `ClaimDeadlineExceededException` y permite un `detail` estable para el front. |

---

## Modelo de datos

### `Notification` (tabla `user_notification`) — dos columnas nuevas

`com.knowlink.api.notifications.data.models.Notification`

| Campo (código) | Columna | Tipo | Notas |
|---|---|---|---|
| `expiresAt` | `expires_at` | `LocalDateTime` | nullable. `null` = no vence. Solo lo informa `RATING_ENABLED`. |
| `resolvedAt` | `resolved_at` | `LocalDateTime` | nullable. Se informa cuando el usuario completó la acción (hoy: calificó). |

Sin índices nuevos (el volumen es mínimo y el filtro ya parte de `recipient_user_id`).

### Enums nuevos

```java
// com.knowlink.api.notifications.data.enums.NotificationActionStatus
public enum NotificationActionStatus { NONE, PENDING, COMPLETED, EXPIRED }

// com.knowlink.api.ratings.data.enums.RatingStatus
public enum RatingStatus { NOT_AVAILABLE, PENDING, SUBMITTED, EXPIRED }
```

### Constante

`RatingConstants`: `public static final Duration RATING_WINDOW = Duration.ofHours(24);` — **separada** de `BLIND_REVIEW_WINDOW` (hoy valen lo mismo pero son reglas distintas).

---

## Ubicación de clases / paquetes

```
com.knowlink.api.ratings
├── data/enums/RatingStatus.java                          // NUEVO
├── events/RatingSubmittedEvent.java                      // NUEVO  record(UUID bookingId, UUID raterUserId)
├── utils/RatingWindow.java                               // NUEVO  cálculo único del plazo
└── services/implementations/RatingServiceImpl.java       // MODIFICA

com.knowlink.api.notifications
├── data/enums/NotificationActionStatus.java              // NUEVO
├── data/models/Notification.java                         // MODIFICA (expiresAt, resolvedAt)
├── data/mappers/NotificationMapper.java                  // MODIFICA (actionStatus, expiresAt)
├── controllers/responses/NotificationResponse.java       // MODIFICA
├── repositories/NotificationRepository.java              // MODIFICA
├── services/interfaces/INotificationService.java         // MODIFICA
├── services/implementations/NotificationServiceImpl.java // MODIFICA
└── listeners/
    ├── BookingNotificationListener.java                  // MODIFICA
    ├── BookingSnapshot.java                              // MODIFICA (endTime, confirmedAt)
    ├── BookingNotificationContext.java                   // MODIFICA
    └── RatingNotificationListener.java                   // NUEVO

com.knowlink.api.exceptions.custom_exceptions
└── RatingWindowExpiredException.java                     // NUEVO

com.knowlink.api.bookings
├── controllers/responses/BookingHistoryDetailResponse.java // MODIFICA (ratingStatus, ratingDeadline)
├── data/mappers/BookingMapper.java                         // MODIFICA (toDetail)
└── services/implementations/BookingServiceImpl.java        // MODIFICA (getDetail)
```

---

## Cambios en detalle

### 1. Disparador y mensaje (CA 1, CA 2)

En `BookingNotificationListener`:

- **Quitar** el bloque `RATING_ENABLED` de `handleSessionCompleted` (deja solo `SESSION_COMPLETED`, que es el aviso de reclamo y no se modifica).
- **Agregar** en `handleSessionConfirmed` el envío de `RATING_ENABLED` a alumno y tutor, además de los `SESSION_CONFIRMED` actuales. `SessionConfirmedEvent` se publica en `BookingServiceImpl.confirmSession` después de pasar la reserva a `COMPLETED`, por lo que la condición de CA 2 se cumple por construcción.
- Título: `"Calificá tu sesión de " + subjectName`. Si `subjectName` es `null`: `"Calificá tu sesión"` (nunca imprimir "null").
- Mensaje (sin cambios de redacción): `"Contanos cómo fue tu experiencia con {otra parte}. Tenés 24 horas para calificar."`
- `referenceId = bookingId`, `dedupeKey = "RATING_ENABLED:" + bookingId` (ya lo da `snapshot.dedupeKeyFor`).
- `expiresAt = RatingWindow.deadline(snapshot.sessionDate(), snapshot.endTime(), snapshot.confirmedAt())`.

`BookingSnapshot` suma `LocalTime endTime` y `LocalDateTime confirmedAt`; `BookingNotificationContext.toSnapshot` los carga (`booking.getEndTime()`, `booking.getConfirmedAt()`).

> Nota de redacción: el CA dice "mensaje"; en el modelo actual lo que dice "Calificá tu sesión de [Materia]" es el **título** (campo `title`) y el cuerpo es el `message`. Se mantiene así.

### 2. Cálculo único del plazo

```java
// com.knowlink.api.ratings.utils.RatingWindow
public static LocalDateTime deadline(LocalDate sessionDate, LocalTime endTime, LocalDateTime confirmedAt) {
    LocalDateTime sessionEnd = LocalDateTime.of(sessionDate, endTime);
    LocalDateTime start = (confirmedAt != null && confirmedAt.isAfter(sessionEnd)) ? confirmedAt : sessionEnd;
    return start.plus(RatingConstants.RATING_WINDOW);
}
public static boolean isOpen(LocalDateTime deadline, LocalDateTime now) { return !now.isAfter(deadline); } // inclusivo
```

Es la **única** implementación del plazo: la usan el listener, `submitRating` y el detalle de reserva. Clase utilitaria final, constructor privado.

### 3. Enforcement del plazo (CA 4)

En `RatingServiceImpl.submitRating` el orden de validaciones queda así (el duplicado va antes que el plazo: si ya calificó, siempre es 409):

1. validar participante y `COMPLETED` (sin cambios)
2. si ya calificó → 409 `RATING_ALREADY_SUBMITTED` (sin cambios)
3. **nuevo:** si `!RatingWindow.isOpen(deadline, now)` → `throw new RatingWindowExpiredException("Booking " + bookingId + " rating window closed for user " + userId)`
4. resto igual

```java
public class RatingWindowExpiredException extends UnprocessableEntityException {
    public RatingWindowExpiredException(String technicalMessage) {
        super("RATING_WINDOW_EXPIRED",
              "Ya pasó el plazo para calificar esta sesión (24 horas desde que finalizó).",
              technicalMessage);
    }
}
```

`UnprocessableEntityException` ya está manejada en `GlobalExceptionHandler`; **no agregar handlers**. Al terminar el submit, publicar `RatingSubmittedEvent(bookingId, userId)` con `ApplicationEventPublisher` (inyectarlo en `RatingServiceImpl`).

### 4. Cerrar la notificación al calificar (CA 3)

`RatingNotificationListener`:

```java
@Async("taskExecutor")
@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
public void handleRatingSubmitted(RatingSubmittedEvent event) {
    try {
        notificationService.resolveNotification(
            event.raterUserId(), NotificationType.RATING_ENABLED, event.bookingId());
    } catch (Exception e) {
        log.error("Failed to resolve rating notification for booking {}: {}", event.bookingId(), e.getMessage(), e);
    }
}
```

`INotificationService` suma:

```java
/** Marca como leída y resuelta la notificación (destinatario, tipo, resourceId). Idempotente; si no existe, no hace nada. */
void resolveNotification(UUID recipientUserId, NotificationType type, UUID resourceId);

/** Igual que notifyUser(...) pero con vencimiento. */
void notifyUser(UUID recipientUserId, NotificationType type, String title, String message,
                UUID referenceId, String dedupeKey, LocalDateTime expiresAt);
```

- `resolveNotification`: `@Transactional(propagation = REQUIRES_NEW)`. Busca `findByRecipient_UserIdAndTypeAndResourceId`; si `resolvedAt == null` setea `resolvedAt = now` y, si `readAt == null`, `readAt = now`. Si no hay fila, `log.debug` y retorna. Filtra **siempre** por el `userId` del evento.
- El `notifyUser` de 6 parámetros delega en el nuevo con `expiresAt = null`; `createNotification` recibe `expiresAt`.

### 5. Respuesta de notificaciones y conteo

`NotificationResponse` suma **al final** (no rompe el contrato actual):

```java
LocalDateTime expiresAt,
NotificationActionStatus actionStatus
```

`NotificationMapper` (inyectar `Clock`):

```java
actionStatus = type != RATING_ENABLED ? NONE
             : resolvedAt != null      ? COMPLETED
             : (expiresAt != null && now.isAfter(expiresAt)) ? EXPIRED
             : PENDING;
```

Conteo y filtro `UNREAD`: una notificación vencida y sin resolver **no cuenta** como no leída. En `NotificationRepository` reemplazar la derivación `countByRecipient_UserIdAndReadAtIsNull` por una `@Query` equivalente más la condición `AND (n.expiresAt IS NULL OR n.expiresAt >= :now)`, y lo mismo en `findByRecipient_UserIdAndReadAtIsNull(userId, pageable)`; el service pasa `LocalDateTime.now(AppTimeZone.ZONE)`. `markAllAsRead` y los métodos de soporte (`SUPPORT_TYPES`) **no cambian**. Si esto se considera demasiado invasivo para 3 SP, la alternativa es dejar el conteo como está y marcar `readAt` de las vencidas desde `NotificationServiceImpl` al listar; se pierde exactitud del badge. Se recomienda la `@Query`.

Repository agrega: `Optional<Notification> findByRecipient_UserIdAndTypeAndResourceId(UUID userId, NotificationType type, UUID resourceId);`

### 6. Detalle de reserva con estado de calificación (CA 7)

`BookingHistoryDetailResponse` suma al final:

```java
RatingStatus ratingStatus,
LocalDateTime ratingDeadline   // null si ratingStatus == NOT_AVAILABLE
```

Cálculo (en `BookingServiceImpl.getDetail`, que ya tiene `userId`; `setVirtualLink` también construye este response y debe pasar los mismos valores):

| Condición | `ratingStatus` |
|---|---|
| reserva no `COMPLETED` | `NOT_AVAILABLE` |
| el usuario ya calificó (`ratingRepository.existsByBooking_BookingIdAndRaterUser_UserId`) | `SUBMITTED` |
| plazo vencido | `EXPIRED` |
| resto | `PENDING` |

`IRatingRepository` suma `boolean existsByBooking_BookingIdAndRaterUser_UserId(UUID bookingId, UUID raterUserId);`. `BookingMapper.toDetail` recibe los dos valores ya calculados (no consulta repositorios).

---

## Endpoints

No se crean endpoints nuevos. Cambian respuestas existentes (solo se **agregan** campos):

| Método | Ruta | Cambio |
|---|---|---|
| GET | `/api/v1/notifications` | cada elemento suma `expiresAt` y `actionStatus` |
| GET | `/api/v1/notifications/unread-count` | no cuenta `RATING_ENABLED` vencidas |
| GET | `/api/v1/bookings/{bookingId}` | suma `ratingStatus` y `ratingDeadline` |
| POST | `/api/v1/bookings/{bookingId}/ratings` | nuevo 422 `RATING_WINDOW_EXPIRED`; agregar `@ApiResponse(responseCode = "422", ...)` en `IRatingsController` |

Error en formato del proyecto: `{ "status": 422, "message": "Ya pasó el plazo para calificar esta sesión (24 horas desde que finalizó).", "detail": "RATING_WINDOW_EXPIRED" }`.

---

## No hacer

- No enviar `RATING_ENABLED` desde `SessionCompletionNotificationEvent` / `BookingCompletionNotificationJob` ni para estados distintos de `COMPLETED`.
- No tocar `SESSION_COMPLETED` (aviso de reclamo, US-51) ni `SESSION_CONFIRMED`.
- No crear jobs `@Scheduled` para vencer notificaciones: el vencimiento es derivado.
- No modificar `RatingReputationServiceImpl`, `isBlindReviewDeadlineElapsed` ni la lógica de visibilidad de calificaciones.
- No guardar ni loguear el texto del comentario de la calificación en notificaciones, logs ni eventos.
- No aceptar `userId`, `recipientId` ni `bookingId` de otro usuario para resolver notificaciones: se usa siempre el `userId` autenticado / del evento.
- No duplicar el cálculo del plazo fuera de `RatingWindow`.
- No relanzar excepciones desde el listener ni acceder a relaciones LAZY fuera de transacción.
- No usar `@Autowired` en campos ni `Optional.get()`.
- No enviar mails ni push desde esta historia.
- No agregar endpoints a `PUBLIC_WHITELIST`.
- No agregar Flyway/Liquibase.

---

## Casos de prueba (tabla de la historia)

| # | Caso de prueba | Resultado esperado | ¿Pasó? / Notas |
|---|---|---|---|
| 1 | Alumno y tutor de una reserva que se confirma con token (`COMPLETED`). Otra reserva `CANCELLED` y otra `NOT_CONFIRMED` al mismo tiempo. | Alumno y tutor de la reserva `COMPLETED` reciben cada uno **una** notificación "Calificá tu sesión de {materia}". La reserva cancelada y la no confirmada no generan ninguna. Reintentar el evento no crea duplicados. | Sí / No |
| 2 | El alumno califica con puntaje 4 dentro de las 24 h; luego intenta enviar otra vez. | 201 la primera vez; la notificación del alumno pasa a leída y `actionStatus = COMPLETED` y el conteo de no leídas baja en 1; la del tutor sigue `PENDING`. El segundo intento da 409 `RATING_ALREADY_SUBMITTED`. | Sí / No |
| 3 | El tutor no califica y se superan las 24 h; intenta calificar después. | 422 `RATING_WINDOW_EXPIRED`; no existe `Rating` del tutor para esa reserva; su notificación figura `EXPIRED` y no suma al conteo de no leídas. | Sí / No |

---

## Definition of Done

- [ ] `Notification` con `expiresAt` y `resolvedAt`; confirmado el valor de `DDL_AUTO` en Render (o `ALTER TABLE` aplicado)
- [ ] `RatingWindow` es la única implementación del plazo y la usan listener, `submitRating` y detalle de reserva
- [ ] `RATING_ENABLED` sale solo de `handleSessionConfirmed`; quitado de `handleSessionCompleted`; título sin "null" cuando falta la materia
- [ ] `submitRating` rechaza fuera de plazo con 422 `RATING_WINDOW_EXPIRED`; el borde de 24 h exactas se acepta; orden de validaciones: participante → `COMPLETED` → duplicado → plazo
- [ ] `RatingSubmittedEvent` publicado y `RatingNotificationListener` (`AFTER_COMMIT`, `@Async`) cierra la notificación del usuario correcto, idempotente, sin relanzar excepciones
- [ ] `NotificationResponse` expone `expiresAt` y `actionStatus` calculado; conteo y filtro `UNREAD` excluyen `RATING_ENABLED` vencidas
- [ ] `BookingHistoryDetailResponse` expone `ratingStatus` y `ratingDeadline` (también en el response de `setVirtualLink`)
- [ ] Swagger actualizado (`@ApiResponse` 422 en `IRatingsController`)
- [ ] Tests unitarios `BookingNotificationListenerTest`: `sessionCompleted_...` ya **no** espera `RATING_ENABLED`; `sessionConfirmed_...` sí lo espera para ambas partes con `expiresAt`; materia nula
- [ ] Tests unitarios `RatingServiceImplTest` / `BookingRatingServiceImplTest`: dentro de plazo, borde inclusivo, vencido (422, no se guarda), duplicado antes que plazo, confirmación posterior al fin de sesión mueve el plazo; actualizar `ratingAfterTwentyFourHoursIsImmediatelyVisible`
- [ ] Tests de `RatingWindow` (con y sin `confirmedAt`, confirmación antes y después del fin)
- [ ] Tests de `NotificationServiceImplTest`: `resolveNotification` (propia, ya resuelta, inexistente, de otro usuario no se toca)
- [ ] Test de repository (`@DataJpaTest`, H2): conteo y listado `UNREAD` excluyen vencidas; `findByRecipient_UserIdAndTypeAndResourceId`
- [ ] Test de integración: confirmar sesión → ambos ven `RATING_ENABLED` `PENDING` → uno califica → su notificación `COMPLETED` y `unread-count` baja; el otro sigue `PENDING`; avanzar el `Clock` 24 h + 1 min → `EXPIRED` y POST 422
- [ ] Verificado a mano que ningún mensaje contiene datos sensibles
- [ ] Commits en español con prefijo convencional (`feat:`, `fix:`, `test:`, `docs:`)

---

## Decisiones pendientes (confirmar antes de implementar)

1. **Significado de "vencimiento automático del plazo"** (CA 2). Este spec asume que solo `COMPLETED` habilita calificar (ver advertencia 4). Confirmar con el PO.
2. **Cambio de US-18**: pasar a rechazar calificaciones después de 24 h. Confirmar con quien tiene US-18.
3. **Anchor del plazo**: `max(fin de sesión, confirmedAt)` + 24 h. Alternativa más simple: solo fin de sesión + 24 h (la confirmación tardía puede dar hasta 10 min menos de plazo).
4. **Notificación vencida fuera del badge** (sección 5): confirmar que es el comportamiento deseado.
