# US-20b — Calificación obligatoria post-sesión (Backend)

> Extensión de US-20. Pasar este archivo completo a OpenCode (DeepSeek V4 Flash) parado en `TetraCodeTeam/knowlink-backend`. Si el equipo prefiere otra numeración, renombrar el archivo y el ID.

## Metadata

| Campo | Valor |
|---|---|
| ID Historia | US-20b (extiende US-20; reemplaza su CA 4) |
| Épica | — |
| Story Points | 3 backend + 3 frontend (estimación) |
| Prioridad | A confirmar con el PO |
| Módulo | calificaciones (`ratings`), con seguridad, reservas y notificaciones |
| Historias relacionadas | US-20 (prompt post-sesión), US-18 (calificar), US-41 (confirmación con token), US-35 (notificaciones), US-51 (reclamos), US-14 (logout) |
| Repo / rama base | `TetraCodeTeam/knowlink-backend` — `feature/best-ui-changes` |
| Spec par | `us-20b-calificacion-obligatoria-frontend.spec.md` |
| Verificado contra | `knowlink-backend@23b977a` |

**Convención de idioma:** identificadores (clases, métodos, campos, paquetes, logs) en **inglés**. Textos al usuario (mensajes de error, notificaciones) en **español**. Swagger en español.

## Historia de usuario

Como alumno o tutor, quiero que la plataforma me exija calificar la sesión apenas termina la clase, para que ninguna sesión realizada quede sin calificación.

## Contexto: esto es un spec de cambios sobre US-20

La rama ya implementa US-20 (verificado en el código):

- `RatingServiceImpl.submitRating`: exige `COMPLETED`, participante, sin calificación previa (409 `RATING_ALREADY_SUBMITTED`) y **rechaza fuera de plazo con 422 `RATING_WINDOW_EXPIRED`** (`RatingWindow`, 24 h).
- `BookingServiceImpl.getDetail` devuelve `ratingStatus` (`NOT_AVAILABLE`/`PENDING`/`SUBMITTED`/`EXPIRED`) y `ratingDeadline`.
- `BookingNotificationListener` crea `RATING_ENABLED` con `expiresAt = RatingWindow.deadline(...)` y mensaje "Tenés 24 horas para calificar".
- `RatingSubmittedEvent` + `RatingNotificationListener` cierran la notificación al calificar.
- `COMPLETED` solo se alcanza en `BookingServiceImpl.confirmSession` (el tutor ingresa el token): puede ocurrir **antes** del fin de la clase o hasta 10 minutos después.

### Qué cambia

| # | Cambio | Decisión del PO |
|---|---|---|
| 1 | Se **elimina el vencimiento de 24 h** para calificar: el bloqueo persiste hasta calificar | Roles: alumno **y** tutor. Persiste hasta calificar |
| 2 | Nuevo `GET /api/v1/ratings/pending` (qué calificaciones bloquean al usuario) | Bloquea solo sesiones confirmadas con token (`COMPLETED`) cuyo horario ya terminó. `NOT_CONFIRMED`, canceladas, etc. no bloquean |
| 3 | **Refuerzo por API:** un usuario con calificación pendiente recibe **423 `RATING_REQUIRED`** en el resto de la API | "Aplicar refuerzo por API para evitar manipulación por UI" |
| 4 | Fecha de corte configurable y kill switch (`RATING_GATE_ENFORCED_FROM`) | Necesario para no bloquear por sesiones históricas |
| 5 | La notificación `RATING_ENABLED` se mantiene (sin vencimiento) | Se mantiene |

## Criterios de aceptación (Gherkin)

```gherkin
Feature: Calificación obligatoria post-sesión (API)

  Background:
    Given RATING_GATE_ENFORCED_FROM está configurada y anterior a la sesión "B"

  Scenario: El alumno queda bloqueado al terminar la clase
    Given la reserva "B" está COMPLETED
    And ya pasó el horario de fin de "B"
    And el alumno no calificó "B"
    When el alumno consulta GET /api/v1/ratings/pending
    Then recibe 200 con "B" en "blocking"
    When el alumno consulta cualquier otro endpoint protegido, por ejemplo GET /api/v1/bookings/mine
    Then recibe 423 con detail "RATING_REQUIRED"

  Scenario: El tutor queda bloqueado igual que el alumno
    Given la reserva "B" está COMPLETED y su horario terminó
    And el tutor no calificó "B"
    When el tutor consulta cualquier endpoint protegido
    Then recibe 423 con detail "RATING_REQUIRED"

  Scenario: No bloquea antes del fin de la clase
    Given el tutor confirmó "B" con el token antes del horario de fin
    When el alumno consulta GET /api/v1/ratings/pending antes del fin
    Then "blocking" está vacío
    And "nextBlockAt" es el fin de "B"
    And el alumno puede usar el resto de la API
    When llega el horario de fin
    Then el alumno queda bloqueado

  Scenario: Confirmación posterior al fin bloquea al confirmar
    Given el horario de "B" ya terminó
    When el tutor confirma "B" con el token (hasta 10 minutos después del fin)
    Then ambos pasan a estar bloqueados

  Scenario: Sesiones no confirmadas o canceladas no bloquean
    Given una reserva en BOOKED, IN_PROGRESS, NOT_CONFIRMED, CANCELLED, NOT_FULFILLED_BY_TUTOR o SESSION_NOT_HELD
    Then no aparece en "blocking" ni bloquea ningún endpoint

  Scenario: El bloqueo persiste hasta calificar
    Given pasaron más de 24 horas desde el fin de "B" y el usuario no calificó
    When el usuario consulta GET /api/v1/ratings/pending
    Then "B" sigue en "blocking"
    When el usuario envía POST /api/v1/bookings/{B}/ratings con score válido
    Then recibe 201
    And ya no hay 422 por plazo vencido

  Scenario: Endpoints que siguen disponibles estando bloqueado
    Given el usuario está bloqueado
    Then GET /api/v1/ratings/pending responde 200
    And POST /api/v1/bookings/{id}/ratings responde según su lógica (201, 400, 404 o 409)
    And POST /api/v1/auth/logout responde 204
    And los endpoints públicos (login, registro, etc.) no se ven afectados

  Scenario: Calificar desbloquea
    Given el usuario tiene una sola calificación pendiente
    When envía una calificación válida
    Then el siguiente request a un endpoint protegido responde normalmente

  Scenario: Varias pendientes, de la más antigua a la más reciente
    Given el usuario tiene "B1" (fin 10:00) y "B2" (fin 12:00) sin calificar
    When consulta GET /api/v1/ratings/pending
    Then "blocking" trae primero "B1" y después "B2"
    When califica "B1"
    Then sigue bloqueado por "B2"

  Scenario: Rol dual
    Given el usuario fue alumno en "B" y hoy opera con un token de rol TUTOR
    When consulta cualquier endpoint protegido
    Then recibe 423 igual
    And el item de "B" informa raterRole STUDENT

  Scenario: Solo cuenta lo que el usuario debe calificar
    Given la otra parte ya calificó "B" pero el usuario no
    Then el usuario sigue bloqueado
    Given el usuario ya calificó "B" pero la otra parte no
    Then el usuario no está bloqueado

  Scenario: Sesiones anteriores a la fecha de corte no bloquean
    Given una reserva COMPLETED sin calificar cuyo fin es anterior a RATING_GATE_ENFORCED_FROM
    Then no bloquea al usuario
    And sigue siendo calificable (ratingStatus PENDING) desde el historial

  Scenario: Kill switch
    Given RATING_GATE_ENFORCED_FROM está vacía o inválida
    Then ningún usuario es bloqueado y GET /api/v1/ratings/pending devuelve "blocking" vacío

  Scenario: Fail-open ante errores
    Given falla la verificación de bloqueo (excepción)
    Then se registra en el log
    And el request continúa sin bloqueo

  Scenario: El administrador nunca es bloqueado
    Given un usuario ADMIN
    Then ningún endpoint responde 423

  Scenario: Detalle de reserva sin vencimiento
    Given "B" está COMPLETED y pasaron más de 24 horas
    When el usuario consulta GET /api/v1/bookings/{B}
    Then ratingStatus es PENDING (o SUBMITTED si ya calificó)
    And ratingDeadline es null

  Scenario: Segundo envío bloqueado (sin cambios)
    Given el usuario ya calificó "B"
    When vuelve a enviar el POST
    Then recibe 409 "RATING_ALREADY_SUBMITTED" y el Rating original no cambia

  Scenario: La notificación ya no vence
    Given se confirma una sesión
    Then ambas partes reciben RATING_ENABLED con expiresAt null
    And el mensaje no menciona "24 horas"
    And la notificación sigue PENDING hasta que el usuario califica
```

## Stack confirmado

Java 21 + Spring Boot 3.5, Spring Security (JWT), Spring Data JPA, MySQL 8 (prod) / H2 (tests), Caffeine (ya está en el `pom.xml`). Sin colas, sin Flyway (esquema por Hibernate). **No hay columnas ni tablas nuevas.**

## Advertencias de dependencias (verificadas en el código)

1. **Reemplaza el CA 4 de US-20.** Hay que avisar al responsable de US-20/US-18. Se eliminan el 422 `RATING_WINDOW_EXPIRED`, `RatingWindow`, `RatingConstants.RATING_WINDOW` y `RatingWindowExpiredException`. **No se tocan** `BLIND_REVIEW_WINDOW`, `RatingQueryFragments.DEADLINE_ELAPSED` ni `RatingReputationServiceImpl` (revelado de calificaciones ocultas: otra regla).
2. **Despliegue no atómico.** `main` despliega backend (Render) y frontend (Vercel) por separado y hay un solo entorno. Por eso: `ratingDeadline` **se mantiene** en `BookingHistoryDetailResponse` (siempre `null`) y `RatingStatus.EXPIRED` **se mantiene** en el enum (nunca se emite). Se retiran en una historia posterior.
3. **Orden de despliegue obligatorio:** (1) backend con `RATING_GATE_ENFORCED_FROM` **vacía** (bloqueo apagado); (2) frontend; (3) recién entonces setear `RATING_GATE_ENFORCED_FROM` con la fecha/hora de corte (hora de Argentina) y reiniciar el servicio. Si el backend bloquea antes de que el frontend maneje el 423, los usuarios verían errores sueltos sin forma de salir.
4. **Migración de datos manual** (convención del equipo, a ejecutar antes de mergear a main). Las notificaciones `RATING_ENABLED` ya creadas tienen `expires_at` y pasarían a verse como vencidas y fuera del contador de no leídas:
   ```sql
   UPDATE user_notification SET expires_at = NULL WHERE notification_type = 'RATING_ENABLED';
   ```
5. **Sesiones históricas.** Hay reservas `COMPLETED` nunca calificadas (US-20 las dejó vencer). Sin fecha de corte, el primer deploy bloquearía a todos los usuarios que las tengan. Por eso existe `RATING_GATE_ENFORCED_FROM`: solo bloquean sesiones cuyo **fin** sea mayor o igual a esa fecha.
6. **Riesgo de encierro.** Si una reserva no se puede calificar por un dato inconsistente, el usuario queda bloqueado en toda la API. Mitigaciones: fail-open ante errores del chequeo, kill switch por variable de entorno y 423 que solo aplica a reservas `COMPLETED` con participantes válidos. Documentar el runbook en el README del repo.
7. **`submitRating` sigue aceptando calificar antes del fin de la clase** (reserva confirmada antes del horario de fin). Es intencional: el bloqueo recién empieza al fin, pero calificar antes es válido.
8. **`open-in-view: false`.** Armar el DTO dentro de `@Transactional(readOnly = true)` y traer relaciones con `JOIN FETCH` (alumno, tutor, `tutorSubject`, `subject`).
9. **Reloj.** Usar el `Clock` inyectado (como `RatingServiceImpl`), nunca `LocalDateTime.now()` directo, para poder testear con tiempo controlado. Los horarios de fin de día se guardan como `EndOfDay.TIME` (23:59:59.999999): la comparación `endTime <= ahora` ya funciona con ese valor, **no normalizar**.
10. **Caché global inadecuada.** `application.yaml` define `spring.cache.caffeine.spec: maximumSize=500,expireAfterWrite=10m` para todos los caches. No sirve para esto: usar un `Cache<UUID, Boolean>` de Caffeine propio dentro del servicio (ver "Caché negativa").

## Decisiones de diseño

| Decisión | Elegido | Alternativa descartada | Motivo |
|---|---|---|---|
| Dónde se aplica el bloqueo | `HandlerInterceptor` MVC registrado en `WebConfig` | `OncePerRequestFilter` | La excepción pasa por `GlobalExceptionHandler`; un `Filter` con `@Component` se registra dos veces en Boot |
| Código HTTP | **423 Locked** + `detail: "RATING_REQUIRED"` | 403 | El front trata 401/403 como "no autorizado" y el 401 cierra sesión; 423 es inequívoco |
| Ámbito | Por `userId`, ambos lados de la reserva | Por rol del token | Rol dual: el token cambia, el usuario no |
| Qué bloquea | `COMPLETED` + horario terminado + sin calificación del usuario + fin ≥ fecha de corte | Cualquier sesión terminada | `NOT_CONFIRMED` no tiene nada que calificar (400) |
| Respuesta de `pending` | Autocontenida (materia, otra parte, fecha, rol) | Que el front pida `GET /bookings/{id}` | El detalle también responde 423 mientras hay bloqueo |
| Caché | Solo resultados "no bloqueado", 15 s | Cachear todo / no cachear | El interceptor corre en cada request; los bloqueos nunca se cachean, así no hace falta invalidar al calificar |
| Errores del chequeo | Fail-open + log | Fail-closed | Un bug no puede dejar a todos sin acceso |
| Fecha de corte | Variable de entorno, vacía = apagado | Migrar el histórico | Evita bloqueo masivo y funciona como kill switch |

## Contrato

### `GET /api/v1/ratings/pending`

Autenticado (no está en `PUBLIC_WHITELIST`). Usa el `userId` del principal; el rol del token es irrelevante.

```json
{
  "blocking": [
    {
      "bookingId": "6f1c2d3e-0000-0000-0000-000000000001",
      "raterRole": "STUDENT",
      "subjectName": "Análisis Matemático I",
      "otherPartyFullName": "María Gómez",
      "sessionDate": "2026-11-07",
      "startTime": "18:00:00",
      "endTime": "19:00:00"
    }
  ],
  "nextBlockAt": "2026-11-07T20:00:00"
}
```

- `blocking`: de la sesión más antigua a la más reciente (`sessionDate`, `endTime`). Vacío si el bloqueo está apagado.
- `raterRole`: `Role.STUDENT` si el usuario es el alumno de la reserva, `Role.TUTOR` si es el tutor.
- `otherPartyFullName`: el tutor si `raterRole` es `STUDENT`, el alumno si es `TUTOR` (misma resolución que `BookingMapper.toDetail`).
- `nextBlockAt` (`LocalDateTime` sin zona, hora de Argentina): el fin más próximo entre las reservas `COMPLETED` sin calificación del usuario cuyo horario **todavía no terminó**. `null` si no hay.

### Error de bloqueo

```json
{ "status": 423, "message": "Tenés que calificar tu sesión para poder seguir usando KnowLink.", "detail": "RATING_REQUIRED" }
```

Mismo formato `ApiError` del proyecto. El body **no** incluye datos de la reserva: el front pide `GET /ratings/pending`.

### Endpoints que NUNCA responden 423

| Método | Ruta |
|---|---|
| GET | `/api/v1/ratings/pending` |
| POST | `/api/v1/bookings/{bookingId}/ratings` |
| POST | `/api/v1/auth/logout` |
| OPTIONS | cualquier ruta (preflight CORS) |

Las rutas fuera de `/api/v1/**` (actuator, Swagger) y las de `PUBLIC_WHITELIST` no pasan por el chequeo (no hay usuario autenticado o el interceptor no aplica).

## Configuración

```yaml
# application.yaml
rating-gate:
  enforced-from: ${RATING_GATE_ENFORCED_FROM:}   # ISO local, ej. 2026-11-10T00:00:00 (hora de Argentina). Vacío = apagado
  clear-cache-seconds: 15
```

`RatingGateProperties` (`@ConfigurationProperties("rating-gate")`, record o clase con Lombok):

- Enlazar `enforcedFrom` como **`String`** y parsear con `LocalDateTime.parse`. Vacío, en blanco o con formato inválido: `log.error` (si es inválido) y **bloqueo apagado**. No lanzar excepción al arrancar.
- Método `Optional<LocalDateTime> enforcedFromAsDateTime()` o equivalente; `boolean isEnabled()`.
- Documentar `RATING_GATE_ENFORCED_FROM` en la tabla de variables de entorno del `README.md` (junto a `JWT_EXPIRATION`) y en la sección de `.env` de `docs/base-standards.md`. El repo no tiene `.env.example`.

## Ubicación de clases / paquetes

```
com.knowlink.api.ratings
├── controllers/interfaces/IRatingGateController.java          // NUEVO  GET /api/v1/ratings/pending
├── controllers/implementations/RatingGateControllerImpl.java  // NUEVO
├── controllers/responses/PendingRatingsResponse.java          // NUEVO  record(List<PendingRatingItemResponse> blocking, LocalDateTime nextBlockAt)
├── controllers/responses/PendingRatingItemResponse.java       // NUEVO  record
├── gate/RatingGateProperties.java                             // NUEVO
├── gate/RatingGateInterceptor.java                            // NUEVO
├── services/interfaces/IRatingGateService.java                // NUEVO
├── services/implementations/RatingGateServiceImpl.java        // NUEVO
├── repositories/IRatingRepository.java                        // MODIFICA (3 queries)
├── services/implementations/RatingServiceImpl.java            // MODIFICA (quitar plazo)
├── utils/RatingWindow.java                                    // ELIMINAR
└── utils/RatingConstants.java                                 // MODIFICA (quitar RATING_WINDOW)

com.knowlink.api.exceptions
├── custom_exceptions/RatingRequiredException.java             // NUEVO
├── custom_exceptions/RatingWindowExpiredException.java        // ELIMINAR
└── GlobalExceptionHandler.java                                // MODIFICA (handler 423)

com.knowlink.api.config
└── WebConfig.java                                             // MODIFICA (registrar interceptor)

com.knowlink.api.bookings
└── services/implementations/BookingServiceImpl.java           // MODIFICA (ratingStatus sin vencimiento)

com.knowlink.api.notifications
└── listeners/BookingNotificationListener.java                 // MODIFICA (sin expiresAt, sin "24 horas")
```

## Cambios en detalle

### 1. Quitar el vencimiento (reemplaza US-20 CA 4)

- `RatingServiceImpl.submitRating`: eliminar el bloque de `RatingWindow.deadline/isOpen` y el `throw new RatingWindowExpiredException`. Orden de validaciones final: reserva existe → participante → `COMPLETED` → duplicado (409). `now` se sigue usando para armar el `Rating` y la lógica de revelado.
- `BookingServiceImpl`: eliminar `resolveRatingDeadline`. `resolveRatingStatus` queda: no `COMPLETED` → `NOT_AVAILABLE`; ya calificó → `SUBMITTED`; resto → `PENDING`. En `getDetail` y `setVirtualLink` pasar `null` como `ratingDeadline` a `BookingMapper.toDetail`. La firma del response **no cambia**.
- `BookingNotificationListener.handleSessionConfirmed`: `RATING_ENABLED` se envía con `expiresAt = null` y el mensaje pasa a `"Contanos cómo fue tu experiencia con {otra parte}."` (sin "Tenés 24 horas para calificar"). El título, `referenceId` y `dedupeKey` no cambian. Quitar el import de `RatingWindow`.
- Eliminar `RatingWindow`, `RatingConstants.RATING_WINDOW`, `RatingWindowExpiredException`, su test `RatingWindowTest` y el `@ApiResponse(422)` de `IRatingsController`.
- **No** tocar `NotificationActionStatus.EXPIRED` ni el mecanismo genérico `expiresAt` de notificaciones (queda disponible para otros tipos): solo `RATING_ENABLED` deja de usarlo.
- `RatingStatus.EXPIRED`: dejar el valor, marcar con `@Deprecated` y comentario "no se emite; se retira en una historia posterior".

### 2. Consultas nuevas en `IRatingRepository`

Hay precedente: `isBlindReviewDeadlineElapsed` ya consulta `Booking` desde este repositorio. El JPQL de abajo es **referencia**: adaptarlo al estilo del repo y validarlo con `@DataJpaTest` (no se ejecutó).

```java
// Reservas que bloquean al usuario: COMPLETED, ya terminaron, fin >= corte, sin calificación suya.
@Query("""
    SELECT b FROM Booking b
    JOIN FETCH b.student JOIN FETCH b.tutor
    JOIN FETCH b.tutorSubject ts JOIN FETCH ts.subject
    WHERE b.bookingStatus = :completed
      AND (b.student.userId = :userId OR b.tutor.userId = :userId)
      AND (b.sessionDate < :today OR (b.sessionDate = :today AND b.endTime <= :nowTime))
      AND (b.sessionDate > :fromDate OR (b.sessionDate = :fromDate AND b.endTime >= :fromTime))
      AND NOT EXISTS (SELECT 1 FROM Rating r
                      WHERE r.booking = b AND r.raterUser.userId = :userId)
    ORDER BY b.sessionDate ASC, b.endTime ASC
    """)
List<Booking> findBlockingBookings(UUID userId, BookingStatus completed, LocalDate today, LocalTime nowTime,
                                   LocalDate fromDate, LocalTime fromTime);

// Igual condición, solo existencia (para el interceptor).
boolean existsBlockingBooking(...mismos parámetros...);

// Próximo fin de una reserva COMPLETED sin calificación cuyo horario todavía no terminó.
// Devuelve fecha y hora de fin; el servicio arma el LocalDateTime y toma el mínimo.
List<Booking> findUpcomingUnratedCompleted(...);   // misma condición pero con fin > ahora, ordenado asc, limitado a 1
```

No agregar índices nuevos: los FK de `student_user_id`/`tutor_user_id` ya están indexados por MySQL y la restricción única `uk_rating_booking_rater` cubre el `NOT EXISTS`. Medir antes de optimizar.

### 3. `RatingGateServiceImpl`

```java
public interface IRatingGateService {
    /** Para el interceptor: usa caché negativa. */
    boolean isBlocked(UUID userId);

    /** Para el endpoint: siempre consulta, sin caché. */
    PendingRatingsResponse getPending(UUID userId);
}
```

- Dependencias por constructor: `IRatingRepository`, `RatingGateProperties`, `Clock`.
- Si el bloqueo está apagado: `isBlocked` → `false`; `getPending` → `blocking` vacío y `nextBlockAt` `null`, sin consultar la base.
- `getPending` en `@Transactional(readOnly = true)`; arma los items dentro de la transacción. `raterRole`: comparar `b.getStudent().getUserId().equals(userId)`.
- **Caché negativa:** campo `Cache<UUID, Boolean> clearCache = Caffeine.newBuilder().expireAfterWrite(Duration.ofSeconds(properties.clearCacheSeconds())).maximumSize(10_000).build()`.
  - `isBlocked`: si hay entrada en caché → `false`. Si no, consultar `existsBlockingBooking`; si da `false`, guardar en caché; si da `true`, **no guardar**.
  - Consecuencia aceptada: tras una confirmación o un fin de clase, el bloqueo puede tardar hasta 15 s en aplicarse por API. El front lo detecta con su polling de 30 s y con el primer 423.

### 4. `RatingGateInterceptor`

```java
@Component
@RequiredArgsConstructor
@Slf4j
public class RatingGateInterceptor implements HandlerInterceptor {

    private final IRatingGateService ratingGateService;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if (HttpMethod.OPTIONS.matches(request.getMethod()) || isExempt(request)) return true;

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof UserPrincipal principal)) return true;

        User user = principal.getUser();
        if (user.getRole() == Role.ADMIN) return true;

        boolean blocked;
        try {
            blocked = ratingGateService.isBlocked(user.getUserId());
        } catch (Exception e) {
            log.error("Rating gate check failed for user {}: {}", user.getUserId(), e.getMessage(), e);
            return true; // fail-open
        }
        if (blocked) throw new RatingRequiredException();
        return true;
    }
}
```

- `isExempt`: tres reglas con `AntPathMatcher` y método HTTP (ver tabla de endpoints exentos). Constantes en una clase `RatingGateExemptions` o dentro del interceptor.
- Registrar en `WebConfig` con un segundo `WebMvcConfigurer` (o extendiendo el existente): `registry.addInterceptor(ratingGateInterceptor).addPathPatterns("/api/v1/**")`.
- `RatingRequiredException extends RuntimeException` con `errorCode = "RATING_REQUIRED"` y `userMessage = "Tenés que calificar tu sesión para poder seguir usando KnowLink."` (mismo patrón que `UnprocessableEntityException`).
- `GlobalExceptionHandler`: nuevo `@ExceptionHandler(RatingRequiredException.class)` que devuelve `ResponseEntity.status(HttpStatus.LOCKED).body(new ApiError(423, userMessage, errorCode))`. Es la única excepción a "no agregar handlers": es un código HTTP nuevo.
- `log.debug` al bloquear; no `info` (se repetiría en cada request).

### 5. Swagger

- `IRatingGateController`: `@Operation(summary = "Calificaciones pendientes que bloquean al usuario")`, `@ApiResponse` 200 y 401.
- Documentar en la descripción del `@Tag` de calificaciones que el resto de la API responde 423 mientras haya calificaciones pendientes.

## Endpoints

| Método | Ruta | Cambio |
|---|---|---|
| GET | `/api/v1/ratings/pending` | **Nuevo** |
| POST | `/api/v1/bookings/{bookingId}/ratings` | Ya no responde 422 por plazo. 201 / 400 / 404 / 409 sin cambios |
| GET | `/api/v1/bookings/{bookingId}` | `ratingStatus` nunca `EXPIRED`; `ratingDeadline` siempre `null` |
| cualquier otro `/api/v1/**` autenticado | — | Puede responder **423 `RATING_REQUIRED`** si el usuario tiene calificaciones bloqueantes |

## No hacer

- No filtrar por el rol del token: el bloqueo es por `userId` y por ambos lados de la reserva.
- No cachear resultados "bloqueado"; solo "no bloqueado".
- No bloquear `OPTIONS`, endpoints públicos, a `ADMIN` ni los tres endpoints exentos.
- No usar 401 ni 403 para el bloqueo.
- No bloquear por reservas que no estén `COMPLETED` ni por reservas cuyo fin sea anterior a `RATING_GATE_ENFORCED_FROM`.
- No tocar la lógica de revelado de calificaciones (`BLIND_REVIEW_WINDOW`, `RatingQueryFragments`, `RatingReputationServiceImpl`).
- No tocar reclamos (US-51), `SESSION_COMPLETED` ni `SESSION_CONFIRMED`.
- No crear jobs `@Scheduled`; el bloqueo es derivado.
- No eliminar `ratingDeadline` del response ni `EXPIRED` del enum en esta historia (despliegue no atómico).
- No lanzar excepciones desde el chequeo del interceptor: fail-open con log.
- No exponer datos de la reserva en el body del 423.
- No guardar ni loguear el texto del comentario de la calificación.
- No agregar Flyway/Liquibase ni columnas nuevas.
- No usar `@Autowired` en campos ni `Optional.get()`.
- No usar `LocalDateTime.now()` sin `Clock` en la lógica nueva.

## Casos de prueba (tabla de la historia)

| # | Caso de prueba | Resultado esperado | ¿Pasó? / Notas |
|---|---|---|---|
| 1 | Alumno y tutor de una reserva `COMPLETED` cuyo horario terminó, sin calificar. Cada uno consulta `GET /bookings/mine`. | Ambos reciben 423 `RATING_REQUIRED`. `GET /ratings/pending` devuelve la reserva con su `raterRole`. | Sí / No |
| 2 | Uno de ellos envía `POST /bookings/{id}/ratings` con puntaje 4. Después repite `GET /bookings/mine`. | 201; el request siguiente responde 200. La otra parte sigue bloqueada hasta calificar. | Sí / No |
| 3 | Pasan más de 24 h sin calificar y se intenta calificar. | Sigue bloqueado; el POST responde 201 (no hay 422). | Sí / No |
| 4 | Reservas `NOT_CONFIRMED`, `CANCELLED`, `BOOKED` y una `COMPLETED` anterior a la fecha de corte. | Ninguna bloquea. | Sí / No |
| 5 | El tutor confirma con token antes del fin de la clase. | No bloquea antes del fin; `nextBlockAt` = fin; bloquea al llegar el fin. | Sí / No |
| 6 | Variable `RATING_GATE_ENFORCED_FROM` vacía. | Nadie es bloqueado. | Sí / No |

## Definition of Done

- [ ] `RatingWindow`, `RATING_WINDOW` y `RatingWindowExpiredException` eliminados; `grep` no encuentra referencias; el 422 sale de Swagger
- [ ] `submitRating` no rechaza por plazo; orden: reserva → participante → `COMPLETED` → duplicado
- [ ] `ratingStatus` sin `EXPIRED` y `ratingDeadline` siempre `null` (campos mantenidos en el contrato); `EXPIRED` marcado `@Deprecated`
- [ ] `RATING_ENABLED` con `expiresAt = null` y mensaje sin "24 horas"
- [ ] `GET /api/v1/ratings/pending` implementado con la forma del contrato, orden ascendente, `raterRole` y `nextBlockAt`
- [ ] `RatingGateInterceptor` registrado en `WebConfig` solo para `/api/v1/**`; 423 `RATING_REQUIRED` con formato `ApiError`; handler agregado
- [ ] Los tres endpoints exentos, `OPTIONS` y `ADMIN` nunca reciben 423
- [ ] Caché negativa de 15 s con `Cache` de Caffeine propio; los bloqueos no se cachean
- [ ] `RatingGateProperties` con `RATING_GATE_ENFORCED_FROM`; vacío o inválido = apagado, sin fallar al arrancar; documentada en README y `docs/base-standards.md`
- [ ] Fail-open verificado con test (el servicio lanza excepción y el request pasa)
- [ ] Tests unitarios `RatingGateServiceImplTest` (apagado, bloqueado, no bloqueado, caché negativa, `nextBlockAt`, ambos roles)
- [ ] Tests `@DataJpaTest` de las queries nuevas: alumno, tutor, calificó el usuario, calificó solo la otra parte, no terminó, estados no `COMPLETED`, anterior al corte, orden
- [ ] Tests del interceptor (MockMvc): 423, exentos, `OPTIONS`, `ADMIN`, apagado, fail-open, sin autenticar
- [ ] Test de integración (patrón de `LogoutFlowIntegrationTest`): confirmar sesión → avanzar el `Clock` más allá del fin → 423 → `GET /ratings/pending` → `POST /ratings` → 200; incluir rol dual
- [ ] Tests existentes actualizados: `BookingRatingServiceImplTest` (calificar tarde ahora es 201), `BookingServiceImplRatingStatusTest` (sin `EXPIRED`), `BookingNotificationListenerTest` (sin `expiresAt`, mensaje nuevo), tests de notificaciones que dependían del vencimiento de `RATING_ENABLED`; eliminado `RatingWindowTest`
- [ ] `UPDATE` de datos documentado y ejecutado antes de mergear a main
- [ ] Orden de despliegue y kill switch documentados (ver advertencia 3)
- [ ] Verificado a mano con el frontend: bloqueo al fin de clase, 423 sin ruido, desbloqueo al calificar, rol dual
- [ ] Commits en español con prefijo convencional (`feat:`, `fix:`, `test:`, `docs:`)

## Decisiones pendientes (confirmar antes de implementar)

1. **Fecha/hora de corte concreta** para `RATING_GATE_ENFORCED_FROM` (sugerencia: el momento del deploy, en hora de Argentina).
2. **Clases consecutivas:** un tutor bloqueado por la sesión A no puede confirmar con token la sesión B hasta calificar A (el token vence 10 minutos después del fin de B). Calificar toma segundos, pero confirmar que se acepta.
3. **Retiro de `ratingDeadline` y `RatingStatus.EXPIRED`** del contrato: historia posterior, una vez desplegado el frontend.
