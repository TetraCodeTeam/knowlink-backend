# Mejoras UX — Notificaciones, Mis reclamos y Agenda del tutor (Backend)

## Metadata

| Campo | Valor |
|---|---|
| ID Historia | — (mejoras sobre US-35, US-51, US-50 y US-52) |
| Módulos | notifications, claims, tutors.schedule |
| Repo / rama | `TetraCodeTeam/knowlink-backend`, rama base `dev` |
| Spec par | `knowlink-frontend/docs/mejoras-notificaciones-reclamos-agenda-frontend.spec.md` |
| Historias relacionadas | US-35 (centro de notificaciones), US-20 (calificación), US-51 (reclamo de sesión), US-50 (admin reclamos), US-52 (agenda semanal) |
| Verificado contra | `knowlink-backend@dev` (2026-11-07) |

**Convención de idioma:** identificadores, logs y nombres de tests en **inglés**; textos visibles para el usuario y Swagger en **español**. Reglas generales en `docs/base-standards.md` y `CLAUDE.md` (inyección por constructor, DTOs `record`, `@Slf4j`, sin `System.out`).

---

## Resumen: qué cambia en el backend y qué no

| # | Pedido | ¿Backend? | Sección |
|---|---|---|---|
| N1 | Botón "Marcar todo como leído" | **No.** `PATCH /api/v1/notifications/read-all` ya existe (`NotificationController.markAllAsRead`) | — |
| N2 | Mensajes largos / ampliar contenido | **No** (solo UI). `message` admite hasta 500 caracteres | — |
| N3 | Recordatorio de token sin "Ver más" | **No** (solo UI, se decide por tipo) | — |
| N4 | Llegan notificaciones de reclamar pero no de calificar | **Sí** | B1 (+ B2 opcional) |
| N5 | Redirección personalizada | **No.** `referenceId` ya viaja (bookingId o claimId); el destino se arma en el frontend | — |
| R1 | Link a "No confirmadas" desde "Antes de reclamar" | **No** (solo UI) | — |
| R2 | "Turno #N" asociado a los reclamos pendientes del admin | **Sí** | B3 |
| A1 | Resumen de agenda refleje bloques libres de 1 hora | **Sí** | B4 |

---

## B1 — Notificación de calificar (`RATING_ENABLED`) que "no llega" (N4)

### Hallazgos verificados en el código

1. **Calificar exige sesión confirmada con token.** `BookingNotificationListener.handleSessionConfirmed` es el único lugar que crea `RATING_ENABLED`, y se dispara solo desde `BookingServiceImpl.confirmSession` (estado → `COMPLETED`). `RatingServiceImpl.submitRating` también exige `COMPLETED`. Una sesión que termina `NOT_CONFIRMED` **nunca** genera notificación de calificar.
2. **Reclamar sí se notifica aunque no haya confirmación.** `BookingCompletionNotificationJob` publica `SessionCompletionNotificationEvent` al terminar la sesión para estados `BOOKED`, `IN_PROGRESS`, `COMPLETED` y `NOT_CONFIRMED` → `SESSION_COMPLETED` ("tenés 24 horas para reclamar"). Y reclamar solo es posible si la sesión **no** fue confirmada (`ClaimDeadlineCalculator.isConfirmed`).
   → El patrón "recibo reclamar y no calificar" es exactamente el de una sesión **no confirmada** (comportamiento actual por diseño).
3. **Bug real: las notificaciones de calificar vencidas desaparecen.** `RATING_ENABLED` se crea con `expiresAt` (24 h). Los listados filtran así:
   - `UNREAD` → `findUnreadVisibleByRecipient` excluye `expiresAt < now`.
   - `READ` → `findByRecipient_UserIdAndRecipientRoleAndReadAtIsNotNull` exige `readAt != null`.
   - La UI solo tiene las pestañas `UNREAD` y `READ` (el filtro `ALL` no se usa).
   Una `RATING_ENABLED` que venció sin leerse no cae en ninguna de las dos pestañas: el usuario nunca vio el aviso y no queda rastro.

### Diagnóstico para confirmar la causa con datos reales

Ejecutar en la base de dev reemplazando `:userId`:

```sql
SELECT n.notification_type, n.created_at, n.read_at, n.expires_at,
       b.booking_status, b.confirmed_at
FROM user_notification n
JOIN booking b ON b.booking_id = n.resource_id
WHERE n.recipient_user_id = :userId
  AND n.notification_type IN ('SESSION_COMPLETED', 'SESSION_CONFIRMED',
                              'SESSION_NOT_CONFIRMED', 'RATING_ENABLED')
ORDER BY n.created_at DESC;
```

- `booking_status = NOT_CONFIRMED` y sin fila `RATING_ENABLED` → causa 1/2 (no confirmada).
- Fila `RATING_ENABLED` con `read_at IS NULL` y `expires_at < NOW()` → causa 3 (vencida e invisible).

### Cambios

**1. `NotificationRepository`**: agregar la query complementaria exacta de `findUnreadVisibleByRecipient` (que usa `expiresAt >= :now`):

```java
@Query(value = """
        SELECT n FROM Notification n
        WHERE n.recipient.userId = :userId
          AND n.recipientRole = :recipientRole
          AND (n.readAt IS NOT NULL
               OR (n.expiresAt IS NOT NULL AND n.expiresAt < :now))
        """,
        countQuery = """
        SELECT COUNT(n) FROM Notification n
        WHERE n.recipient.userId = :userId
          AND n.recipientRole = :recipientRole
          AND (n.readAt IS NOT NULL
               OR (n.expiresAt IS NOT NULL AND n.expiresAt < :now))
        """)
Page<Notification> findReadOrExpiredByRecipient(
        @Param("userId") UUID userId,
        @Param("recipientRole") Role recipientRole,
        @Param("now") LocalDateTime now,
        Pageable pageable);
```

**2. `NotificationServiceImpl.getNotifications`**: en el `switch`, `case READ` pasa a usar `findReadOrExpiredByRecipient(userId, activeRole, LocalDateTime.now(clock), safePageable)`. `UNREAD` y `ALL` no cambian. Con esto `UNREAD ∪ READ = ALL` y ninguna notificación queda invisible.

**3. `NotificationMapper.toResponse`**: una notificación vencida se considera leída para la UI.

```java
boolean expired = notification.getExpiresAt() != null && now.isAfter(notification.getExpiresAt());
// ...
notification.getReadAt() != null || expired,   // campo `read`
```

`readAt` se mantiene tal cual (null si nunca se leyó). `resolveActionStatus` no cambia: una `RATING_ENABLED` vencida y no resuelta sigue devolviendo `EXPIRED`, que el frontend ya renderiza como "Venció el plazo".

**4. Contrato**: `NotificationResponse` no cambia de forma. Actualizar el `@Operation` de `list` indicando que `status=READ` incluye las notificaciones vencidas.

### Reglas que NO se tocan

- `unread-count` sigue excluyendo vencidas (el badge no debe inflarse con avisos que ya no sirven).
- La calificación sigue requiriendo `COMPLETED`. Permitir calificar sesiones no confirmadas cambia reputación y liberación de fondos (US-15/US-20) y queda fuera de este spec (ver "Preguntas abiertas").

---

## B2 — `SESSION_COMPLETED` solo cuando todavía se puede reclamar (opcional, prioridad baja)

**Problema:** si el tutor confirma con token antes de que termine la sesión, el job igual publica `SessionCompletionNotificationEvent` y ambos reciben "tenés 24 horas para reclamar", pero `ClaimEligibilityResponse` devolverá `SESSION_ALREADY_CONFIRMED` (el reclamo está bloqueado).

**Cambio:** en `BookingCompletionNotificationJob.run`, después de `setCompletionNotificationSent(true)` + `save`, no publicar el evento si la sesión ya no es reclamable:

```java
if (claimDeadlineCalculator.isConfirmed(booking)
        || !claimDeadlineCalculator.isClaimableStatus(booking)) {
    continue; // se marca como procesada igual, para no reevaluarla cada minuto
}
eventPublisher.publishEvent(new SessionCompletionNotificationEvent(booking));
```

Inyectar `ClaimDeadlineCalculator` (ya es `@Component`; ubicado en `com.knowlink.api.claims.utils`). Los estados reclamables son `BOOKED`, `IN_PROGRESS`, `NOT_CONFIRMED`.

---

## B3 — "Turno #N" del reclamo, asociado a la cola de pendientes del admin (R2)

### Hallazgo verificado

`ClaimSupportDetailsServiceImpl.getReviewProgress` calcula `queuePosition` con `SessionClaimRepository.countOpenClaimsInQueueThrough`, que cuenta **todos los reclamos** `OPEN` creados antes. Pero la cola que ve el admin (`IAdminSessionClaimRepository.findAdminPage`) lista **una fila por disputa** (solo el reclamo representante: `disputeId IS NULL OR claimId = disputeId`), `OPEN` primero y por `createdAt ASC`; y `assignIfOpen` mueve a `IN_REVIEW` la disputa completa. Si alumno y tutor reclaman la misma reserva, hoy esa disputa ocupa **dos** lugares en el "turno" aunque el admin la ve como **un** caso.

### Nuevo significado

`queuePosition` = lugar de la disputa del usuario entre las **disputas pendientes (`OPEN`)**, ordenadas por antigüedad de la disputa. Baja cuando un admin toma un caso (pasa a `IN_REVIEW`) o lo resuelve. Se agrega `queueTotal` (cantidad de disputas pendientes) para mostrar "Turno 2 de 5". Solo aplica con estado `OPEN`; en `IN_REVIEW` y `RESOLVED` ambos valen `null`.

> Los admins pueden tomar cualquier caso, no necesariamente el primero. Por eso el número es "casos pendientes por delante como máximo"; la UI debe presentarlo como posición estimada.

### Cambios

**1. `SessionClaimRepository`**: reemplazar `countOpenClaimsInQueueThrough` (verificado: solo la usa `ClaimSupportDetailsServiceImpl`, se puede eliminar) por:

```java
@Query("""
        SELECT COUNT(c) FROM SessionClaim c
        WHERE c.status = com.knowlink.api.claims.data.enums.ClaimStatus.OPEN
          AND (c.disputeId IS NULL OR c.claimId = c.disputeId)
          AND (c.createdAt < :createdAt
               OR (c.createdAt = :createdAt AND c.claimId <= :claimId))
        """)
long countOpenDisputesInQueueThrough(
        @Param("createdAt") LocalDateTime createdAt,
        @Param("claimId") UUID claimId);

@Query("""
        SELECT COUNT(c) FROM SessionClaim c
        WHERE c.status = com.knowlink.api.claims.data.enums.ClaimStatus.OPEN
          AND (c.disputeId IS NULL OR c.claimId = c.disputeId)
        """)
long countOpenDisputes();
```

**2. `ClaimReviewProgressResponse`**: agregar `Integer queueTotal` al final del `record` (campo nuevo, nullable; no rompe a los consumidores existentes del frontend).

```java
public record ClaimReviewProgressResponse(
        String currentPhase,
        Integer queuePosition,
        Integer queueTotal,
        LocalDateTime responseDueAt,
        long remainingMinutes) {}
```

Verificado: el único `new ClaimReviewProgressResponse(` está en `ClaimSupportDetailsServiceImpl.getReviewProgress` (no hay usos en tests).

**3. `ClaimSupportDetailsServiceImpl.getReviewProgress`**: usar el reclamo **representante** de la disputa. Si `claim.getDisputeId() != null` y es distinto de `claim.getClaimId()`, cargar el representante con `sessionClaimRepository.findById(claim.getDisputeId())` y usar su `createdAt` y `claimId` (si no existe, caer al reclamo actual). Así, el reclamo de la contraparte hereda el mismo turno que la disputa.

```java
boolean open = claim.getStatus() == ClaimStatus.OPEN;
SessionClaim representative = resolveRepresentative(claim);
Integer queuePosition = open
        ? Math.toIntExact(sessionClaimRepository.countOpenDisputesInQueueThrough(
                representative.getCreatedAt(), representative.getClaimId()))
        : null;
Integer queueTotal = open
        ? Math.toIntExact(sessionClaimRepository.countOpenDisputes())
        : null;
return new ClaimReviewProgressResponse(phase, queuePosition, queueTotal, dueAt, remainingMinutes);
```

Este servicio lo comparten el detalle del alumno (`StudentSessionClaimDetailResponse`) y el del tutor (`TutorSupportClaimDetailResponse`): ambos quedan corregidos sin más cambios.

---

## B4 — Resumen de la agenda: bloques libres de 1 hora (A1)

### Hallazgo verificado

`TutorWeeklyScheduleServiceImpl` calcula `freeBlocksCount = availabilityBlocks.size()`, es decir, la cantidad de **tramos** libres. Un bloque de disponibilidad de 09:00–12:00 cuenta como **1** aunque contenga 3 horas reservables. La duración mínima de una reserva es 1 hora (`BookingConstants.MIN_BOOKING_DURATION`), así que la unidad real es la hora.

### Nuevo significado

`summary.freeBlocksCount` = suma de **horas completas** libres de todos los tramos que se muestran (después de partir los bloques por reservas activas): `floor(duración del tramo en minutos / 60)`. Ejemplos:

| Tramos libres | Antes | Ahora |
|---|---|---|
| 09:00–12:00 | 1 | 3 |
| 09:00–10:00 + 11:30–14:00 | 2 | 1 + 2 = 3 |
| 09:00–09:45 | 1 | 0 (no entra una reserva mínima de 1 h) |
| Bloque de 08:00–12:00 con reserva 09:00–10:00 | 2 | 1 + 2 = 3 |

El tramo de "hoy" ya viene recortado por `PastTimeUtil.effectiveStartTime` (redondeo a 30 min), así que las horas ya pasadas no se cuentan. El nombre del campo se mantiene para no romper el contrato; la UI cambia la etiqueta (ver spec de frontend).

### Cambios en `TutorWeeklyScheduleServiceImpl`

Reemplazar el conteo por tramos por el conteo por horas, usando `EndOfDay.between` (soporta bloques que terminan a medianoche, guardados como `EndOfDay.TIME`):

```java
int freeHoursCount = availabilityBlocks.stream()
        .mapToInt(block -> (int) (EndOfDay.between(block.startTime(), block.endTime()).toMinutes() / 60))
        .sum();

WeeklyScheduleSummaryResponse summary = new WeeklyScheduleSummaryResponse(
        confirmedBookings.size(), freeHoursCount, nextClass);
```

Actualizar el comentario del código (hoy dice que cuenta tramos) y la nota de `docs/us-52-agenda-semanal-tutor-backend.md` ("`summary.freeBlocksCount` cuenta los tramos libres…") para que describa horas libres de 1 h.

**No se modifica** la lista `availabilityBlocks` (el calendario sigue mostrando un evento "Libre" por tramo) ni la regla de aviso mínimo (`minNoticeMinutes`): el conteo es consistente con lo que se dibuja en el calendario.

---

## Tests (extender los existentes, JUnit 5 + Mockito, nombres en inglés)

| Archivo | Casos nuevos |
|---|---|
| `notifications/repositories/NotificationRepositoryTest` | `findReadOrExpiredByRecipient` devuelve leídas y vencidas no leídas, y no devuelve no leídas vigentes ni sin `expiresAt`; `UNREAD` + `READ` cubren todas las filas sin solaparse |
| `notifications/data/mappers/NotificationMapperTest` | `read = true` con `expiresAt` pasado y `readAt = null`; `readAt` sigue `null`; `actionStatus = EXPIRED` en `RATING_ENABLED` vencida |
| `notifications/services/NotificationServiceImplTest` | `getNotifications(..., READ, ...)` incluye una `RATING_ENABLED` vencida sin leer |
| `notifications/controllers/NotificationControllerIntegrationTest` | `GET /notifications?status=READ` devuelve la vencida; `GET /unread-count` no la cuenta |
| `claims/services/implementations/ClaimSupportDetailsServiceImplTest` (**crear**, no existe hoy) | turno cuenta disputas y no reclamos (dos reclamos de la misma reserva = 1 lugar); el reclamo de la contraparte hereda el turno del representante; `queuePosition`/`queueTotal` son `null` en `IN_REVIEW` y `RESOLVED`; baja al pasar el caso anterior a `IN_REVIEW` |
| `tutors/schedule/services/TutorWeeklyScheduleServiceImplTest` | bloque 09:00–12:00 → 3; bloque 08:00–12:00 con reserva 09:00–10:00 → 1 + 2 = 3; tramo de 45 min → 0; bloque hasta `EndOfDay.TIME` cuenta hasta 24:00 |
| `bookings/jobs/BookingCompletionNotificationJobTest` (**crear**, solo B2) | no publica el evento con `confirmedAt != null` ni con estados no reclamables; sí con `NOT_CONFIRMED` |

Verificación final: `./mvnw test` en verde.

---

## Orden de implementación sugerido

1. B4 (aislado, cambio de una línea + tests).
2. B3 (repositorio → DTO → servicio → tests).
3. B1 (repositorio → servicio → mapper → tests).
4. B2 (opcional).
5. Actualizar la documentación mencionada (`docs/us-52-agenda-semanal-tutor-backend.md`).

## Preguntas abiertas

1. **¿Una sesión no confirmada debería poder calificarse?** Hoy no (`RatingServiceImpl` exige `COMPLETED`). Este spec lo mantiene; si el equipo quiere otro criterio, es una historia aparte porque afecta reputación y fondos.
2. **B2:** ¿se implementa ahora o queda como mejora posterior?
