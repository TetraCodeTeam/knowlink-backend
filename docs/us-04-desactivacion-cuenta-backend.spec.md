# US-04 — Desactivación temporal de la cuenta (Backend Spec)

> **Lee primero:** `AGENTS.md` y `docs/base-standards.md`. Esta spec está escrita para ejecutarse **en orden, paso a paso**. No improvises fuera de lo indicado; si algo no coincide con el código real de `dev`, detenete y marcalo en el PR en vez de inventar.
> Verificada contra la rama `dev` de `TetraCodeTeam/knowlink-backend` (02/11/2026).

## 0. Metadata

| Campo | Valor |
|---|---|
| ID Historia | US-04 |
| Story Points | 2 |
| Prioridad | BAJA |
| Módulo | `users` (+ cambios transversales de visibilidad en `tutors`, `bookings`, `materials`, `community`, `security`, `auth`) |
| Tags | #RN-04 |
| Relacionadas | US-04b (eliminación definitiva), US-40 (reactivar cuenta), US-37 (perfil alumno), US-38 (perfil tutor), US-10 y US-11 (reservas activas como restricción) |
| Repo | `knowlink-backend` — rama base `dev` |

**Historia:** Como usuario registrado, quiero poder desactivar temporalmente mi cuenta, para dejar de usar la plataforma por un tiempo manteniendo mi información para cuando quiera volver.

---

## 1. Criterios de aceptación (Gherkin) → cómo se cubren

```gherkin
Escenario 1: Confirmación explícita antes de ejecutar
  Dado que soy un usuario registrado
  Cuando solicito desactivar mi cuenta
  Entonces se presenta un diálogo de confirmación explícita antes de ejecutarse
  # FRONTEND. El backend solo ejecuta cuando recibe el PATCH; no existe "paso previo" en la API.

Escenario 2: Una cuenta desactivada es invisible para el resto
  Dado que desactivé mi cuenta
  Entonces no debo poder visualizarme en ninguna parte de la plataforma para el resto de usuarios
  # BACKEND. Ver sección 6 (inventario de superficies de visibilidad).

Escenario 3: Cancelar no cambia nada
  Dado que se muestra el diálogo de confirmación
  Cuando cancelo la acción
  Entonces mi cuenta permanece activa sin ningún cambio
  # FRONTEND. Si no se envía el request, el backend no cambia nada.

Escenario 4: Bloqueo por reservas activas pendientes
  Dado que tengo reservas activas pendientes (como alumno o como tutor)
  Cuando intento dar de baja mi cuenta
  Entonces el sistema responde 409 con el mensaje
       "No podés dar de baja tu cuenta con reservas activas pendientes"
  Y mi cuenta permanece activa
```

| # | Criterio | Capa | Cobertura |
|---|---|---|---|
| 1 | Diálogo de confirmación | Frontend | Ver spec frontend |
| 2 | Invisible para el resto | **Backend** | Nuevo estado `DEACTIVATED` + filtros en todas las superficies públicas (sección 6) |
| 3 | Cancelar no cambia nada | Frontend | Sin request no hay cambio |
| 4 | Bloqueo por reservas activas | **Backend** | `409` con `detail = ACCOUNT_HAS_ACTIVE_BOOKINGS` y el mensaje exacto |

---

## 2. Stack confirmado (no negociable)

- Java 21, Spring Boot 3.5.3, Maven wrapper (`./mvnw`), MySQL 8 (prod) / H2 (tests, perfil `test`).
- **Sin Flyway/Liquibase**: el esquema lo maneja `ddl-auto` (`${DDL_AUTO}`) + scripts `src/main/resources/migration-*.sql` ejecutados a mano.
- Código (clases, métodos, variables, logs, comentarios) en **inglés**; mensajes al usuario final en **español rioplatense** (voseo: "No podés…").
- Inyección por constructor (`@RequiredArgsConstructor`), `@Slf4j`, `orElseThrow()` (nunca `.get()`), `@Operation` + `@ApiResponse` en cada endpoint nuevo (en la interfaz del controller).
- Estructura de módulo: `controllers/{interfaces,implementations,requests,responses}`, `services/{interfaces,implementations}`, `repositories`, `data/{models,enums,mappers}`, `validation`.
- Controllers sin lógica de negocio y sin acceso a repositories.
- Commits: `feat: descripción en español` (un commit por paso lógico).

---

## 3. Hallazgos del código actual (leer antes de codear)

1. **`AccountStatus` ya existe** (`users/data/enums/AccountStatus.java`): `ACTIVE, INACTIVE, DELETED`.
   **`INACTIVE` NO significa "desactivada por el usuario"**: es el valor por defecto de `User` (`@Builder.Default = INACTIVE`) y representa **cuenta registrada pero sin verificar el mail**. `UserServiceImpl.verifyNewUser` pasa `INACTIVE → ACTIVE` y `resendConfirmationEmail`/`ifUserIsAlreadyActiveThrowException` dependen de ello.
   **Decisión:** agregar un valor **nuevo** `DEACTIVATED`. **Prohibido reutilizar `INACTIVE`** (una cuenta desactivada se reactivaría al "verificar" un token viejo, y la lógica de reenvío de verificación la trataría como no verificada).
2. **`UserPrincipal.isEnabled()`** devuelve `accountStatus == ACTIVE` → un `DEACTIVATED` ya queda "disabled" en el login (`DisabledException`) **antes** de validar la contraseña, y `GlobalExceptionHandler.handleDisabled` responde *"You must confirm your account before logging in"* (mensaje equivocado para este caso).
3. **`JwtAuthenticationFilter` NO consulta `isEnabled()`**: carga el `UserDetails` y, si el token es válido, autentica. Hoy un JWT emitido antes de desactivar **seguiría funcionando** (otras pestañas/dispositivos). Hay que corregirlo.
4. `CustomUserDetailsServiceImpl` usa `userRepository.findByEmail`, que excluye solo `DELETED` → un `DEACTIVATED` sí se carga (correcto, lo necesitamos para distinguirlo).
5. **Superficies que ya filtran por `ACTIVE`** y funcionan solas con el nuevo estado (solo agregar test): `ITutorRankingRepository` (podio, líneas ~36 y ~79 del service), `NotificationServiceImpl` (admins), `sendResetPasswordEmail` / `resetPassword` (solo `ACTIVE`).
6. **Superficies que hoy NO filtran** y exponen al usuario (se corrigen en la sección 6): búsqueda de tutores, perfil público, historial de calificaciones, calendario/slots, holds, stream SSE de slots, materiales, autores en comunidad, `GET /api/v1/users/{userId}`.
7. `existsByEmail` / `existsByDni` excluyen solo `DELETED` → una cuenta `DEACTIVATED` **sigue ocupando** email y DNI (correcto: el usuario volverá con esa cuenta). No tocar.
8. `BookingConstants.ACTIVE_STATUSES = [PENDING, BOOKED, IN_PROGRESS]` ya existe y es la definición vigente de "reserva activa" (la usa US-12). **Reutilizarla, no crear otra lista.**
9. `BusinessConflictException(errorCode, userMessage, technicalMessage)` ya existe y `GlobalExceptionHandler` la mapea a `409`. Reutilizarla.
10. `IUserRepository.findByIdForUpdate` / `IUserService.lockForUpdateOrThrowException` (lock pesimista sobre `users`) ya existen y los usan `HoldServiceImpl` y `BookingServiceImpl`.
11. Un usuario puede tener **doble rol** (alumno + tutor). La desactivación es de la **cuenta completa** y la restricción de reservas debe revisar **ambos** roles.

---

## 4. Contrato del endpoint

`PATCH /api/v1/users/me/deactivate` — JWT requerido, sin body.

| Aspecto | Valor |
|---|---|
| Roles permitidos | `STUDENT`, `TUTOR`. `ADMIN` → `403` (las cuentas admin no se autodesactivan) |
| Éxito | `204 No Content`. La cuenta pasa a `DEACTIVATED`, se guarda `deactivatedAt` |
| Idempotencia | Llamar sobre una cuenta ya `DEACTIVATED` no es posible (el filtro JWT la rechaza con `401` antes de llegar) |

Errores (`ApiError`: `{status, message, detail}`):

| Status | `detail` | `message` | Cuándo |
|---|---|---|---|
| 409 | `ACCOUNT_HAS_ACTIVE_BOOKINGS` | `No podés dar de baja tu cuenta con reservas activas pendientes` | Reserva activa como alumno o tutor, hold vigente del alumno, o hold vigente sobre slots del tutor |
| 409 | `ACCOUNT_HAS_ACTIVE_CLAIMS` | `No podés dar de baja tu cuenta con reclamos abiertos` | Reclamo en `OPEN`/`IN_REVIEW` como reclamante, alumno o tutor |
| 409 | `ACCOUNT_HAS_RETAINED_FUNDS` | `No podés dar de baja tu cuenta con fondos retenidos pendientes` | Fondos `HELD`/`SUSPENDED_BY_CLAIM` en reservas del usuario |
| 403 | — | (handler existente de acceso denegado) | Rol `ADMIN` |
| 401 | — | (existente) | Token ausente/inválido/blacklisteado, o cuenta no `ACTIVE` |

> El `message` del 409 debe ser **exactamente** `No podés dar de baja tu cuenta con reservas activas pendientes` (sin punto final, es el texto del criterio 4).

Login de una cuenta desactivada (cambio en `POST /api/v1/auth/login`):

| Status | `detail` | `message` | Cuándo |
|---|---|---|---|
| 403 | `ACCOUNT_DEACTIVATED` | `Tu cuenta está desactivada. Podés reactivarla para volver a usar KnowLink.` | Credenciales **correctas** pero cuenta `DEACTIVATED` |
| 401 | (existente) | `Invalid credentials` | Contraseña incorrecta (no se revela el estado de la cuenta) |

> La reactivación en sí es **US-40**. Esta historia solo deja el código `ACCOUNT_DEACTIVATED` como punto de enganche.

---

## 5. Cambios paso a paso

### Paso 1 — Enum `AccountStatus`

Archivo: `users/data/enums/AccountStatus.java`

```java
public enum AccountStatus {
    ACTIVE,
    INACTIVE,     // registered but email not verified yet
    DELETED,
    DEACTIVATED   // temporarily deactivated by the user (US-04); reversible (US-40)
}
```

**No reordenar** los valores existentes (el enum se persiste como `STRING`, pero evitá confusiones).

### Paso 2 — Entidad `User`

Archivo: `users/data/models/User.java`. Agregar junto a `deletedAt`:

```java
@Column(name = "deactivated_at")
private LocalDateTime deactivatedAt;
```

Nullable, sin default. Lo setea el service al desactivar; US-40 lo limpiará al reactivar.

### Paso 3 — Migración SQL

Archivo nuevo: `src/main/resources/migration-user-account-deactivation.sql`. Seguir el estilo de `migration-user-first-last-name.sql` (comentario de cabecera, idempotente, con verificación final).

Hay que cubrir **dos** cosas:

1. Columna `users.deactivated_at DATETIME(6) NULL` (agregar solo si no existe, vía `information_schema`).
2. Tipo de `users.account_status`: Hibernate 6 mapea `@Enumerated(STRING)` en MySQL como `ENUM(...)` nativo, y `ddl-auto=update` **no** agrega valores a un ENUM ya creado → sin este paso, `UPDATE ... 'DEACTIVATED'` falla con *"Data truncated"*. El script debe:
   - leer `DATA_TYPE` de `account_status` en `information_schema.COLUMNS`;
   - si es `enum`, ejecutar `ALTER TABLE users MODIFY COLUMN account_status ENUM('ACTIVE','INACTIVE','DELETED','DEACTIVATED') NOT NULL`;
   - si es `varchar`, no hacer nada (`SELECT 1`).
   - Conservar el `DEFAULT`/`NOT NULL` actuales (verificar con `SHOW COLUMNS FROM users LIKE 'account_status'` y reflejarlo en el script).

Cabecera obligatoria: "Run once BEFORE deploying the version that maps `DEACTIVATED`". **Dejar anotado en el PR** que hay que correrlo en la base de producción antes de mergear a `main` (un solo entorno: todo lo que llega a `main` se despliega solo).

Los tests (H2, `ddl-auto` crea el esquema) no necesitan el script.

### Paso 4 — Repositorios (solo lectura)

**`IBookingRepository`** (`bookings/repositories`): agregar

```java
boolean existsByStudent_UserIdAndBookingStatusIn(UUID studentUserId, List<BookingStatus> statuses);
boolean existsByTutor_UserIdAndBookingStatusIn(UUID tutorUserId, List<BookingStatus> statuses);
```

**`IHoldRepository`** (`bookings/repositories`): agregar

```java
boolean existsByStudent_UserIdAndExpiresAtAfter(UUID studentUserId, LocalDateTime now);

@Query("""
        SELECT CASE WHEN COUNT(h) > 0 THEN true ELSE false END
        FROM Hold h
        WHERE h.expiresAt > :now
        AND h.timeSlot.tutorProfileId IN (
            SELECT tp.tutorProfileId FROM TutorProfile tp WHERE tp.user.userId = :tutorUserId)
        """)
boolean existsActiveHoldOnTutorSlots(@Param("tutorUserId") UUID tutorUserId,
                                     @Param("now") LocalDateTime now);
```

**`IUserRepository`**: no hace falta método nuevo (se usa `findByIdForUpdate`).

### Paso 5 — Validación de reservas activas

Interfaz `users/validation/IUserValidationService` + impl `UserValidationService`. Agregar:

```java
void ifUserHasActiveBookingsThrowException(UUID userId);
```

Implementación (inyectar `IBookingRepository`, `IHoldRepository` y `Clock`; el repo ya usa `Clock` en `TutorProfileServiceImpl`, reutilizá el bean):

```java
boolean blocked =
        bookingRepository.existsByStudent_UserIdAndBookingStatusIn(userId, BookingConstants.ACTIVE_STATUSES)
     || bookingRepository.existsByTutor_UserIdAndBookingStatusIn(userId, BookingConstants.ACTIVE_STATUSES)
     || holdRepository.existsByStudent_UserIdAndExpiresAtAfter(userId, now)
     || holdRepository.existsActiveHoldOnTutorSlots(userId, now);

if (blocked) {
    throw new BusinessConflictException(
            "ACCOUNT_HAS_ACTIVE_BOOKINGS",
            "No podés dar de baja tu cuenta con reservas activas pendientes",
            String.format("user '%s' has active bookings or holds", userId));
}
```

> Si inyectar `IBookingRepository` en `UserValidationService` produce dependencia circular, dejá la lógica en `UserServiceImpl` (privada) en lugar de forzar `@Lazy`; anotalo en el PR.

### Paso 6 — Service `IUserService` / `UserServiceImpl`

```java
void deactivateAccount(UUID userId);
```

```java
@Override
@Transactional
public void deactivateAccount(UUID userId) {
    User user = lockForUpdateOrThrowException(userId);      // pessimistic lock on users row
    if (user.getAccountStatus() != AccountStatus.ACTIVE) {
        throw new BusinessConflictException("ACCOUNT_NOT_ACTIVE",
                "Tu cuenta no está activa.", "user '%s' status is %s".formatted(userId, user.getAccountStatus()));
    }
    userValidationService.ifUserHasActiveBookingsThrowException(userId);
    user.setAccountStatus(AccountStatus.DEACTIVATED);
    user.setDeactivatedAt(LocalDateTime.now(clock));
    userRepository.save(user);
    log.info("Account deactivated for userId '{}'", userId);
}
```

Notas:
- **Orden: lock primero, validación después.** Eso serializa contra `HoldServiceImpl`/`BookingServiceImpl` (ver Paso 8): o la reserva entra antes y la desactivación se bloquea, o la desactivación entra antes y la reserva se rechaza.
- No tocar `tutor_profile`, `student_profile`, materias, bloques de disponibilidad, materiales, calificaciones ni reservas históricas: **se conserva toda la información** (objetivo de la historia). La invisibilidad se logra **filtrando por `accountStatus`**, no borrando ni modificando datos.
- No enviar mail en esta historia.

### Paso 7 — Controller

`users/controllers/interfaces/IUserController.java`:

```java
@PatchMapping("/me/deactivate")
@PreAuthorize("hasAnyRole('STUDENT','TUTOR')")
@Operation(summary = "Desactivar temporalmente mi cuenta",
        description = "La cuenta deja de ser visible para el resto de usuarios y no se puede iniciar sesión "
                + "hasta reactivarla (US-40). Falla con 409 si hay reservas activas pendientes.")
@ApiResponse(responseCode = "204", description = "Cuenta desactivada")
@ApiResponse(responseCode = "409", description = "ACCOUNT_HAS_ACTIVE_BOOKINGS: hay reservas activas pendientes")
@ApiResponse(responseCode = "403", description = "Rol no permitido")
@ResponseStatus(HttpStatus.NO_CONTENT)
void deactivateMyAccount(@AuthenticationPrincipal UserPrincipal principal);
```

Impl en `UserControllerImpl`: `userService.deactivateAccount(principal.getUser().getUserId());` (patrón idéntico a `TutorControllerImpl.getMyProfile`). `@EnableMethodSecurity` ya está activo en `SecurityConfig`. **Convención del repo:** los `@PreAuthorize` existentes (p. ej. `AdminMaterialReportControllerImpl`) van en la clase/método de la **implementación**; si al probar el de la interfaz no se aplica, moverlo a `UserControllerImpl.deactivateMyAccount`.

**Spring Security:** `SecurityConfig` termina en `.anyRequest().authenticated()`, así que `/api/v1/users/me/deactivate` queda autenticado sin tocar la config. **No agregarlo a `PUBLIC_WHITELIST`.** Verificar que el matcher `/api/v1/users/*/verify-account` de la whitelist **no** matchea `/api/v1/users/me/deactivate` (no lo hace; agregar test).

Ojo con el mapeo: ya existe `@GetMapping("/{userId}")`; `PATCH /me/deactivate` no colisiona porque cambia el método HTTP y el segmento literal `me` tiene precedencia.

### Paso 8 — Rechazar sesiones y reservas de cuentas no activas

**8.1 `JwtAuthenticationFilter`** — autenticar solo si el usuario está habilitado:

```java
if (jwtService.isTokenValid(jwt, userDetails) && !isBlacklisted && userDetails.isEnabled()) {
```

Con esto un JWT previo de una cuenta `DEACTIVATED` (o `DELETED`) deja de autenticar y el request termina en `401` por `GlobalAuthenticationEntryPoint`. No hace falta blacklistear el `jti`.

**8.2 `AuthServiceImpl.login`** — distinguir "desactivada" solo cuando la contraseña es correcta. Inyectar `PasswordEncoder` y `IUserService` (ya está):

```java
try {
    authenticationManager.authenticate(new UsernamePasswordAuthenticationToken(request.email(), request.password()));
} catch (DisabledException ex) {
    User candidate = userService.findUserByEmailOrThrowException(request.email());
    if (candidate.getAccountStatus() == AccountStatus.DEACTIVATED
            && passwordEncoder.matches(request.password(), candidate.getPassword())) {
        throw new AccountDeactivatedException();
    }
    if (candidate.getAccountStatus() == AccountStatus.DEACTIVATED) {
        throw new BadCredentialsException("Bad credentials");   // do not reveal the status
    }
    throw ex;                                                   // INACTIVE (unverified): existing behavior
}
```

Crear `exceptions/custom_exceptions/AccountDeactivatedException` (patrón de las otras: `errorCode`, `userMessage`) y su handler en `GlobalExceptionHandler` → `403`, `ApiError(403, "Tu cuenta está desactivada. Podés reactivarla para volver a usar KnowLink.", "ACCOUNT_DEACTIVATED")`.

**8.3 `HoldServiceImpl.createHold` (línea ~55) y `BookingServiceImpl`**: no permitir reservar con un tutor que no esté `ACTIVE`. Ver Paso 9 (método `findActiveTutorProfileOrThrowException`). Además, para cerrar la carrera contra la desactivación del tutor, **tomar el lock del usuario tutor antes de validar su estado**:

```java
User tutor = userService.lockForUpdateOrThrowException(tutorUserId);   // before reading its status
// then validate tutor.getAccountStatus() == ACTIVE (inside findActiveTutorProfileOrThrowException or right after)
```

Orden de locks para evitar deadlock: **primero el tutor, después el alumno** (el alumno ya se bloquea en la línea ~72). Documentarlo con un comentario en inglés. `deactivateAccount` solo toma el lock de **su propio** usuario, así que no hay ciclos.

### Paso 9 — Método de validación para perfiles públicos de tutor

`tutors/validations/ITutorProfileValidationService` + impl: agregar

```java
TutorProfile findActiveTutorProfileOrThrowException(UUID tutorUserId);
```

Misma excepción que `findTutorProfileOrThrowException` (`ResourceNotFoundException("TUTOR_PROFILE_NOT_FOUND", "Este tutor no está registrado.", ...)`) cuando el perfil no existe **o** `profile.getUser().getAccountStatus() != AccountStatus.ACTIVE`. Respuesta idéntica en ambos casos: **no revelar que existe una cuenta desactivada**.

`findTutorProfileOrThrowException` **se mantiene sin cambios** para los flujos de autoservicio del propio tutor (editar perfil, materias, disponibilidad, estadísticas) y de admin.

---

## 6. Inventario de superficies de visibilidad (criterio 2)

> Regla: un usuario con `accountStatus != ACTIVE` no debe ser descubrible ni consultable por **otros usuarios**. Los datos NO se borran. Los endpoints de **admin** (`/api/v1/admin/**`) quedan **exentos** (el administrador debe poder ver reclamos/denuncias sobre cuentas desactivadas).

| # | Superficie | Archivo (ref.) | Cambio |
|---|---|---|---|
| 1 | Búsqueda por materia | `TutorProfileServiceImpl.searchTutor` + `TutorSearchSpecifications` | Agregar `Specification` `tutorAccountActive()`: `cb.equal(root.get("tutorProfile").get("user").get("accountStatus"), AccountStatus.ACTIVE)` y combinarla con `subjectActive()` |
| 2 | Búsqueda por nombre | `ITutorProfileRepository.findByUser_FullNameContainingIgnoreCase` (usado en `searchTutor`) | Reemplazar su uso por `findByUser_FullNameContainingIgnoreCaseAndUser_AccountStatus(query, AccountStatus.ACTIVE)` (nuevo método derivado). Dejar el viejo si otro código lo usa; si no, eliminarlo |
| 3 | Perfil público `GET /tutors/{userId}/profile` | `TutorProfileServiceImpl.getTutorProfile` (línea ~79) | Usar `findActiveTutorProfileOrThrowException` → `404` |
| 4 | Historial de calificaciones `GET /tutors/{tutorId}/ratings` | `TutorRatingHistoryServiceImpl` (línea ~40) | Usar `findActiveTutorProfileOrThrowException` → `404`. Además, en `toComment`, **enmascarar al autor** si `rater.getAccountStatus() != ACTIVE` (ver #9) |
| 5 | Calendario de slots del tutor | `BookingCalendarServiceImpl.getCalendar` (línea ~47) | `findActiveTutorProfileOrThrowException` |
| 6 | Stream SSE de slots | `events/controllers/BookingEventsController` (línea ~34) | `findActiveTutorProfileOrThrowException` |
| 7 | Crear hold / reserva | `HoldServiceImpl` (línea ~55), `BookingServiceImpl` | `findActiveTutorProfileOrThrowException` + lock (Paso 8.3) |
| 8 | Materiales para alumnos | `MaterialServiceImpl` (~105, `findActiveBySubjectId`), `MaterialAccessServiceImpl` (~68, `findAccessibleByTutorAndSubjectIds`), `IAcademicMaterialRepository` | Agregar `AND m.tutorSubject.tutorProfile.user.accountStatus = com.knowlink.api.users.data.enums.AccountStatus.ACTIVE` a ambas queries. **No** filtrar `findAvailableByTutorProfileId` ni las queries del propio tutor (US-16/US-17) |
| 9 | Autores en comunidad y reseñas | `community/data/mappers/CommunityMapper` (`tutorAuthor`, `studentAuthor`, ~línea 232) y `TutorRatingHistoryServiceImpl.toComment` | Si `user.getAccountStatus() != ACTIVE`: **enmascarar identidad** → `fullName = "Usuario desactivado"`, `profilePictureUrl = null`, `careerName = null`, `averageRating = null`, `verified = false`, `canBookClass = false` (campos de `CommunityAuthorResponse`; en `CommunityMapper` el flag se calcula hoy como la variable local `bookable`). Se conserva el contenido (posts, respuestas, reseñas) para no alterar promedios ni hilos. Centralizar en un helper `UserNameUtils.DEACTIVATED_DISPLAY_NAME` (constante) |
| 10 | `GET /api/v1/users/{userId}` | `IUserController.getUserById` / `UserControllerImpl` | Hoy cualquier autenticado lee cualquier usuario. Agregar `@AuthenticationPrincipal UserPrincipal principal`; si el usuario pedido está `DEACTIVATED` y `principal` no es el mismo usuario ni `ADMIN` → `ResourceNotFoundException("USER_NOT_FOUND", ...)`. Pasar por `IUserService.findVisibleByIdOrThrowException(UUID targetId, UserPrincipal requester)` (nuevo) |
| 11 | Podio / ranking | `ITutorRankingRepository`, `TutorRankingServiceImpl` | **Ya filtra `ACTIVE`.** Solo agregar test |
| 12 | Historial de reservas **propias** (contraparte) | `bookings/**`, notificaciones | **Decisión: no se enmascara.** La contraparte de una sesión ya realizada forma parte del registro de las propias transacciones del usuario (necesario para reclamos US-51 y calificaciones). Queda documentado como decisión (sección 8) |

Tests de integración con H2 deben cubrir cada fila 1–10 (ver sección 9).

---

## 7. Ubicación de clases (resumen)

| Qué | Dónde |
|---|---|
| `DEACTIVATED`, `deactivatedAt` | `users/data/enums/AccountStatus`, `users/data/models/User` |
| Migración | `src/main/resources/migration-user-account-deactivation.sql` |
| Endpoint | `users/controllers/{interfaces/IUserController,implementations/UserControllerImpl}` |
| Lógica | `users/services/{interfaces/IUserService,implementations/UserServiceImpl}` |
| Validación de reservas | `users/validation/{IUserValidationService,UserValidationService}` |
| Excepción de login | `exceptions/custom_exceptions/AccountDeactivatedException` + `exceptions/GlobalExceptionHandler` |
| Filtro JWT | `security/filter/JwtAuthenticationFilter` |
| Login | `auth/services/implementations/AuthServiceImpl` |
| Perfil público activo | `tutors/validations/{ITutorProfileValidationService,TutorProfileValidationServiceImpl}` |
| Búsqueda | `tutors/data/specifications/TutorSearchSpecifications`, `tutors/repositories/ITutorProfileRepository` |
| Materiales | `materials/repositories/IAcademicMaterialRepository` |
| Enmascarado | `community/data/mappers/CommunityMapper`, `tutors/services/implementations/TutorRatingHistoryServiceImpl`, `users/utils/UserNameUtils` |
| Reservas | `bookings/repositories/{IBookingRepository,IHoldRepository}` |

---

## 8. Decisiones de diseño (quedan registradas; confirmar con el equipo si no coinciden)

1. **Estado nuevo `DEACTIVATED`**, separado de `INACTIVE` (sin verificar). Razón en sección 3.1.
2. **"Reserva activa pendiente"** = reserva en `PENDING/BOOKED/IN_PROGRESS` **como alumno o como tutor**, más hold vigente. Sesiones `COMPLETED`, `CANCELLED`, `EXPIRED`, `NOT_CONFIRMED`, etc. **no** bloquean.
3. **Invisibilidad por filtrado, no por borrado.** Se conservan perfil, materias, disponibilidad, materiales, reseñas y reservas históricas para que US-40 pueda reactivar sin pérdida.
4. **Contenido de terceros asociado a la cuenta (posts, respuestas, reseñas):** se mantiene pero con identidad enmascarada ("Usuario desactivado"). Alternativa descartada por ahora: ocultar también el contenido (implica tocar todas las queries del feed y recalcular promedios).
5. **Historial de reservas propias:** la contraparte se sigue viendo (ver fila 12).
6. **Sin mail de confirmación** de desactivación en esta historia.
7. **Actualización aprobada:** reclamos de sesión abiertos (`ClaimStatus.ACTIVE_STATUSES`) y fondos retenidos (`FundsStatus.HELD` / `SUSPENDED_BY_CLAIM`) de sesiones ya completadas **sí** bloquean la desactivación. Se responde `409` con `ACCOUNT_HAS_ACTIVE_CLAIMS` o `ACCOUNT_HAS_RETAINED_FUNDS` según corresponda. Si un reclamo queda abierto, el admin sigue pudiendo resolverlo porque `/admin/**` está exento.

---

## 9. Tests (JUnit 5 + Spring Boot Test + H2; estilo de `src/test/java/com/knowlink/api/users` y `.../auth`)

**`UserServiceImpl` (unit, Mockito):**
- `deactivateAccount` con cuenta `ACTIVE` sin reservas → pasa a `DEACTIVATED`, setea `deactivatedAt`, guarda.
- Con reserva activa como **alumno** → `BusinessConflictException` `ACCOUNT_HAS_ACTIVE_BOOKINGS`, el estado **no cambia**, no se llama a `save`.
- Con reserva activa como **tutor** → igual.
- Con hold vigente del alumno / hold vigente sobre slots del tutor → igual.
- Con reservas solo `COMPLETED`/`CANCELLED`/`EXPIRED` → desactiva.
- Cuenta `INACTIVE` o ya `DEACTIVATED` → `ACCOUNT_NOT_ACTIVE`.

**Controller / integración (`@SpringBootTest` + `MockMvc`):**
- `PATCH /api/v1/users/me/deactivate` con alumno → `204`; luego un request con el **mismo JWT** → `401` (criterio de seguridad del Paso 8.1).
- Con reservas activas → `409`, body `detail = ACCOUNT_HAS_ACTIVE_BOOKINGS`, `message` **exactamente** `No podés dar de baja tu cuenta con reservas activas pendientes`.
- Con rol `ADMIN` → `403`. Sin token → `401`.
- Login con credenciales correctas de cuenta `DEACTIVATED` → `403` `ACCOUNT_DEACTIVATED`; con contraseña incorrecta → `401 Invalid credentials` (sin revelar estado); cuenta `INACTIVE` → comportamiento previo.

**Visibilidad (una prueba por fila de la sección 6):**
- Tutor desactivado con materias activas: **no** aparece en `GET /tutors/search/{query}` (por materia **ni** por nombre); `GET /tutors/{id}/profile` y `/ratings` → `404 TUTOR_PROFILE_NOT_FOUND`; calendario/SSE/hold → `404`.
- Sus materiales **no** aparecen en el acceso de alumnos (US-26) y sí siguen en la vista del propio tutor tras reactivar manualmente en el test.
- Un post/respuesta/reseña de una cuenta desactivada se devuelve con `"Usuario desactivado"` y sin foto.
- `GET /api/v1/users/{id}` de un desactivado: `404` para otro alumno; `200` para ADMIN.
- Podio no incluye tutores desactivados (regresión).
- Datos conservados: tras desactivar, `tutor_profile`, `tutor_subject`, ratings y `availability_block` del usuario siguen existiendo sin cambios.

**Concurrencia (si el tiempo da; si no, anotarlo en el PR):** hold creándose sobre un tutor mientras éste se desactiva → exactamente uno de los dos gana, nunca ambos.

---

## 10. "No hacer"

- ❌ **No reutilizar `AccountStatus.INACTIVE`** para "desactivada".
- ❌ No borrar ni modificar `tutor_profile`, `student_profile`, `tutor_subject`, materiales, ratings, bloques de disponibilidad, reservas ni tokens. No usar `deleted_at` (eso es US-04b).
- ❌ No implementar reactivación (US-40) ni eliminación definitiva (US-04b). No crear endpoint `reactivate`.
- ❌ No tocar `findTutorProfileOrThrowException` (lo usan los flujos de autoservicio y admin); crear el método nuevo `findActiveTutorProfileOrThrowException`.
- ❌ No filtrar los endpoints de `/api/v1/admin/**`.
- ❌ No agregar el endpoint a `PUBLIC_WHITELIST`.
- ❌ No hardcodear el mensaje del 409 fuera de una constante (`users/utils/Constants`: `ACCOUNT_HAS_ACTIVE_BOOKINGS_CODE` y `_MESSAGE`, mismo estilo que `RESET_LINK_INVALID_*`).
- ❌ No lógica de negocio en el controller ni acceso a repositories desde él.
- ❌ No revelar el estado `DEACTIVATED` en login sin contraseña correcta.
- ❌ No `Optional.get()`, no `@Autowired` en campos, no `System.out.println`.

---

## 11. Definition of Done

- [ ] `AccountStatus.DEACTIVATED` + `User.deactivatedAt` + script `migration-user-account-deactivation.sql` idempotente (corrido en local sobre MySQL 8 y verificado con `SHOW COLUMNS`).
- [ ] `PATCH /api/v1/users/me/deactivate` documentado en Swagger (`@Operation` + `@ApiResponse` 204/403/409).
- [ ] 409 con el mensaje **exacto** del criterio 4 y `detail = ACCOUNT_HAS_ACTIVE_BOOKINGS`; la cuenta no cambia.
- [ ] Reservas activas revisadas para **ambos roles** + holds.
- [ ] JWT previo de cuenta desactivada → `401` (filtro corregido).
- [ ] Login de cuenta desactivada → `403 ACCOUNT_DEACTIVATED` solo con contraseña correcta.
- [ ] Las 10 superficies de la sección 6 filtradas/enmascaradas, con test cada una.
- [ ] Nada de datos borrados; admin sigue viendo todo.
- [ ] Tests nuevos verdes y suite existente sin regresiones: `./mvnw test`.
- [ ] Código en inglés, mensajes de usuario en español; sin `any`/casteos inseguros/`Optional.get()`.
- [ ] Commits `feat: ...` en español; PR a `dev` que menciona el script SQL a correr **antes** de llegar a `main` y los riesgos de la decisión 7.
