# US-40 — Reactivar cuenta desactivada (Backend Spec)

> **Lee primero:** `AGENTS.md` y `docs/base-standards.md`. Esta spec está escrita para ejecutarse **en orden, paso a paso**. No improvises fuera de lo indicado; si algo no coincide con el código real, detenete y marcalo en el PR en vez de inventar.

## 0. Metadata

| Campo | Valor |
|---|---|
| ID Historia | US-40 |
| Story Points | 5 (total; backend ≈ 60 %) |
| Prioridad | Baja |
| Módulo | `users` / `auth` |
| Relacionadas | US-04 (desactivación temporal de cuenta), US-02 (inicio de sesión) |
| Repo | `knowlink-backend` (frontend: `knowlink-frontend/docs/us-40-reactivar-cuenta-frontend.spec.md`) |

**Historia:** Como usuario que desactivó temporalmente su cuenta, quiero poder reactivarla fácilmente, para retomar mi actividad en la plataforma con todos mis datos intactos.

---

## 1. Criterios de aceptación → cómo se cubren

| # | Criterio | Capa | Cobertura |
|---|---|---|---|
| 1 | Login con cuenta desactivada → pantalla intermedia "Tu cuenta está desactivada. ¿Querés reactivarla?" | Backend + Frontend | `POST /auth/login` responde `403` con `detail = ACCOUNT_DEACTIVATED` **solo si la contraseña es correcta**. La pantalla es del frontend |
| 2 | Confirmar → reactiva, restaura datos, redirige al dashboard como login normal | Backend + Frontend | Nuevo `POST /auth/reactivate` (re-verifica credenciales, reactiva y devuelve `AuthResponse` con JWT). Redirección: frontend |
| 3 | Mensaje "¡Tu cuenta fue reactivada correctamente. ¡Bienvenido de nuevo!" | **Frontend** | Toast tras el `200`. Backend no envía el texto |
| 4 | Cancelar → sigue desactivada, vuelve al login, sin cambios | **Frontend** | Si no se llama a `/auth/reactivate`, no hay cambio |
| 5 | Se recuperan clases, materiales y calificaciones sin pérdida | Backend | Solo cambia `users.account_status`. **Prohibido** borrar, copiar o recrear datos (ver §2.3) |

---

## 2. Decisiones de diseño clave (leer antes de codear)

### 2.1 `INACTIVE` ya significa "sin confirmar email" → agregar `DEACTIVATED`

Hoy `AccountStatus` es `ACTIVE | INACTIVE | DELETED`, y `INACTIVE` es el estado por defecto de un usuario recién registrado que **aún no confirmó su email** (`User.accountStatus` default `INACTIVE`; `UserServiceImpl.verifyNewUser` lo pasa a `ACTIVE`). Si US-04 reutilizara `INACTIVE` para "desactivada por el usuario", un usuario sin confirmar podría "reactivarse" saltándose la verificación de email.

**Se agrega un valor nuevo `DEACTIVATED`** (desactivada voluntariamente, reversible). Todo el código existente compara contra `ACTIVE` o contra `DELETED`, así que `DEACTIVATED` queda automáticamente excluido de buscadores, ranking, comunidad, reset de contraseña, etc. **No modificar esas comparaciones.**

> **Coordinación con US-04:** al momento de escribir esta spec, US-04 **no está implementada** (no existe endpoint de desactivar cuenta; solo existe la desactivación de *materias*, US-12). Si US-04 ya está mergeada cuando implementes esto, verificá qué estado usa:
> - Si ya usa `DEACTIVATED`: omití el Paso 1.
> - Si usa `INACTIVE`: **detenete** y marcalo en el PR; hay que migrar US-04 a `DEACTIVATED` antes de seguir.
> - Si no existe: hacé el Paso 1 y dejá anotado en el PR que US-04 debe setear `DEACTIVATED`.

### 2.2 Sin enumeración de cuentas

Spring Security (`DaoAuthenticationProvider`) ejecuta `isEnabled()` **antes** de verificar la contraseña. Si se dejara que `authenticate()` lance `DisabledException`, cualquiera podría saber que un email está desactivado sin conocer la contraseña. Por eso el login debe **verificar la contraseña a mano** cuando la cuenta está `DEACTIVATED` y recién ahí revelar el estado. Con contraseña incorrecta → `BadCredentialsException` (mismo `401` que cualquier credencial inválida).

`UserPrincipal.isEnabled()` **no se cambia** (sigue siendo `status == ACTIVE`). Ojo: hasta el Paso 7 de US-24, `JwtAuthenticationFilter` **no** consulta `isEnabled()`, así que un JWT emitido antes de desactivar la cuenta **sigue autenticando hasta vencer**. Ese arreglo debe mergearse **antes o junto con US-04**; recién con él el token viejo deja de autenticar y el request termina en `401`.

### 2.3 Cómo se reactiva (sin token intermedio)

El frontend no guarda la contraseña de forma persistente. Para simplificar, `POST /auth/reactivate` recibe **de nuevo** `email + password (+ targetRole)`, los re-verifica y reactiva. No hay token de reactivación ni estado en servidor. (Alternativa descartada: token JWT efímero con claim `purpose=reactivation`; más piezas para una historia de prioridad baja.)

### 2.4 Qué NO hacer

- No agregar columnas ni tablas. No tocar `Booking`, `AcademicMaterial`, `Rating`, `TutorProfile`, `StudentProfile`, `TutorSubject`.
- No dejar que `/auth/reactivate` funcione para `INACTIVE` (sin confirmar) ni `DELETED`.
- No cambiar `PUBLIC_WHITELIST` más que lo indicado.

---

## 3. Stack y reglas (del repo, no negociables)

- Java 21, Spring Boot 3.5.3, MySQL 8 (prod) / H2 (tests), Maven wrapper (`./mvnw`).
- Código (clases, métodos, variables, logs, comentarios) en **inglés**; mensajes al usuario final en **español**.
- Inyección por constructor con `@RequiredArgsConstructor`. `@Slf4j`. `orElseThrow()` siempre, nunca `.get()`.
- Controllers sin lógica ni acceso a repositorios. `@Operation` + `@ApiResponses` en la interfaz `IAuthController`.
- Sin Flyway: el proyecto usa `ddl-auto` + scripts `src/main/resources/migration-*.sql`.
- Commits: `feat: descripción en español`.

---

## 4. Cambios paso a paso

### Paso 1 — Enum `AccountStatus`

Archivo: `src/main/java/com/knowlink/api/users/data/enums/AccountStatus.java`

```java
public enum AccountStatus {
    ACTIVE,
    INACTIVE,     // registered, email not confirmed yet
    DEACTIVATED,  // voluntarily deactivated by the user (US-04), reversible (US-40)
    DELETED
}
```

Agregar al **final de la lista conocida, antes de `DELETED` es válido** porque la columna es `EnumType.STRING` (no ordinal).

### Paso 2 — Migración SQL (obligatoria)

Hibernate 6 sobre MySQL puede haber creado `users.account_status` como `ENUM('ACTIVE','INACTIVE','DELETED')`; `ddl-auto=update` **no** modifica columnas existentes y un `UPDATE`/`INSERT` con `DEACTIVATED` fallaría con *"Data truncated"*.

Crear `src/main/resources/migration-user-account-status-deactivated.sql` siguiendo el estilo de `migration-token-type.sql` (cabecera comentada, idempotente, re-ejecutable):

```sql
-- KnowLink - US-40 migration
-- Allows users.account_status = 'DEACTIVATED'.
-- Run once BEFORE deploying: mysql ... < migration-user-account-status-deactivated.sql
-- Safe to re-run. Existing rows are not modified.
ALTER TABLE users
    MODIFY COLUMN account_status VARCHAR(20) NOT NULL DEFAULT 'INACTIVE';

SELECT COLUMN_NAME, DATA_TYPE, IS_NULLABLE, COLUMN_DEFAULT
FROM information_schema.COLUMNS
WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'users' AND COLUMN_NAME = 'account_status';
```

(`VARCHAR` es compatible con `EnumType.STRING` y evita el problema para siempre. En H2 de tests `ddl-auto` crea la columna desde cero; no hace falta nada.)

### Paso 3 — Excepción `AccountDeactivatedException` (403)

Archivo nuevo: `.../exceptions/custom_exceptions/AccountDeactivatedException.java`, mismo patrón que `BusinessConflictException`:

```java
public class AccountDeactivatedException extends RuntimeException {
    public static final String ERROR_CODE = "ACCOUNT_DEACTIVATED";
    private final String userMessage;

    public AccountDeactivatedException(String technicalMessage) {
        super(technicalMessage);
        this.userMessage = "Tu cuenta está desactivada.";
    }
    public String getErrorCode() { return ERROR_CODE; }
    public String getUserMessage() { return userMessage; }
}
```

Handler en `GlobalExceptionHandler` (mismo estilo que `handleSessionNotParticipant`):

```java
@ExceptionHandler(AccountDeactivatedException.class)
public ResponseEntity<ApiError> handleAccountDeactivated(AccountDeactivatedException ex) {
    logger.info(ex.getMessage());
    ApiError error = new ApiError(HttpStatus.FORBIDDEN.value(), ex.getUserMessage(), ex.getErrorCode());
    return ResponseEntity.status(HttpStatus.FORBIDDEN).body(error);
}
```

Contrato resultante: `403 {"status":403,"message":"Tu cuenta está desactivada.","detail":"ACCOUNT_DEACTIVATED"}`.

### Paso 4 — `IUserService` / `UserServiceImpl`

Agregar a la interfaz y la implementación:

```java
Optional<User> findOptionalByEmail(String email);   // @Transactional(readOnly = true), userRepository.findByEmail(email.trim())
User reactivateAccount(UUID userId);                // @Transactional
```

`reactivateAccount`:

1. `User user = lockForUpdateOrThrowException(userId);` (ya existe; evita doble click / requests concurrentes).
2. Si `accountStatus != DEACTIVATED` → `throw new BusinessConflictException("ACCOUNT_NOT_DEACTIVATED", "La cuenta no está desactivada.", "user '%s' has status '%s'")`.
3. `user.setAccountStatus(AccountStatus.ACTIVE); return userRepository.save(user);`
4. `log.info("Account reactivated for userId '{}'", userId);`
5. Si US-04 agregó un campo tipo `deactivatedAt`, ponerlo en `null` aquí. Si no existe, no lo crees.

No tocar ninguna otra entidad (AC #5).

### Paso 5 — `IAuthValidationService` / `AuthValidationServiceImpl`

Agregar (inyectar `PasswordEncoder`; es el mismo bean que usa `UserServiceImpl`):

```java
/** Reveals the deactivated state ONLY when the password is correct (no account enumeration). */
void ifDeactivatedAccountThrowExceptionOrBadCredentials(User user, String rawPassword);
```

Lógica:

```java
if (user.getAccountStatus() != AccountStatus.DEACTIVATED) return;
if (!passwordEncoder.matches(rawPassword, user.getPassword())) {
    throw new BadCredentialsException("invalid credentials");
}
throw new AccountDeactivatedException(
        String.format("login attempt on deactivated userId '%s'", user.getUserId()));
```

### Paso 6 — `AuthServiceImpl.login` y nuevo `reactivate`

Archivo: `.../auth/services/implementations/AuthServiceImpl.java` (+ `IAuthService`).

**6.1 `login`:** antes de `authenticationManager.authenticate(...)` agregar:

```java
userService.findOptionalByEmail(request.email())
        .ifPresent(u -> authValidationService.ifDeactivatedAccountThrowExceptionOrBadCredentials(u, request.password()));
```

Para emails inexistentes o cuentas no deactivadas el flujo sigue **idéntico** al actual (no cambiar el comportamiento de `INACTIVE`/`DELETED`/`ACTIVE`).

**6.2 Extraer** la parte final de `login` a un método privado reutilizable (sin cambiar su comportamiento):

```java
private AuthResponse issueSession(User user, Role requestedRole) {
    Role targetRole = requestedRole != null ? requestedRole : user.getRole();
    authValidationService.ifUserLacksProfileForTargetRoleThrowException(user, targetRole);
    if (!targetRole.equals(user.getRole())) {
        user = userService.updateUserRole(user.getUserId(), targetRole);
    }
    String token = jwtService.generateToken(user);
    return new AuthResponse(user.getUserId(), user.getEmail(), token, user.getRole());
}
```

**6.3 `reactivate(ReactivateAccountRequest request)`**, `@Transactional`:

```java
User user = userService.findOptionalByEmail(request.email())
        .orElseThrow(() -> new BadCredentialsException("invalid credentials"));
if (!passwordEncoder.matches(request.password(), user.getPassword())) {
    throw new BadCredentialsException("invalid credentials");
}
switch (user.getAccountStatus()) {
    case DEACTIVATED -> user = userService.reactivateAccount(user.getUserId());
    case ACTIVE -> { /* idempotent: double click or already reactivated */ }
    case INACTIVE -> throw new DisabledException("account not confirmed");
    case DELETED -> throw new LockedException("account deleted");
}
return issueSession(user, request.targetRole());
```

(`BadCredentialsException`/`DisabledException`/`LockedException` ya tienen handler `401` en `GlobalExceptionHandler`; no crear nuevos.) Inyectar `PasswordEncoder` en `AuthServiceImpl`.

> Si `issueSession` falla (p. ej. falta perfil para `targetRole`), la transacción hace rollback y la cuenta **sigue desactivada**. Es el comportamiento deseado.

### Paso 7 — Request DTO

Archivo nuevo: `.../auth/controllers/requests/ReactivateAccountRequest.java` (mismos campos y mensajes de validación que `LoginRequest`):

```java
public record ReactivateAccountRequest(
        @NotBlank(message = "El email es requerido") @Email(message = "El email no es válido") String email,
        @NotBlank(message = "La contraseña es requerida") String password,
        Role targetRole
) {}
```

### Paso 8 — Controller

`IAuthController` + `AuthControllerImpl` (solo delega a `authService.reactivate(request)`):

- `POST /api/v1/auth/reactivate`, body `ReactivateAccountRequest`, `200` → `AuthResponse`.
- `@Operation(summary = "Reactivar cuenta desactivada")`, `@ApiResponses`: `200` ok; `400` validación; `401` credenciales inválidas / cuenta sin confirmar / eliminada; `409` no aplica (no usar).
- Seguir el mismo estilo de anotaciones de `login` (`@Valid @RequestBody`).

### Paso 9 — Seguridad

`security/utils/SecurityConstants.java` → agregar a `PUBLIC_WHITELIST` justo debajo de `/api/v1/auth/login`:

```java
"/api/v1/auth/reactivate",
```

Verificar también `config/SecurityConfig` por si las rutas públicas se repiten allí; si es así, agregarla en el mismo lugar que `/api/v1/auth/login`.

---

## 5. Contrato final para el frontend

| Método | Ruta | Body | Respuesta |
|---|---|---|---|
| POST | `/api/v1/auth/login` | `{email,password,targetRole?}` | `200 AuthResponse` · `401` credenciales inválidas · **`403` `detail=ACCOUNT_DEACTIVATED`** (cuenta desactivada y contraseña correcta) |
| POST | `/api/v1/auth/reactivate` | `{email,password,targetRole?}` | `200 AuthResponse` (JWT nuevo) · `401` inválidas/sin confirmar/eliminada · `400` validación |

---

## 6. Tests (JUnit 5 + Mockito en `AuthServiceImplTest`; H2 para los de integración si ya existen)

Seguir el estilo de `src/test/java/com/knowlink/api/auth/services/AuthServiceImplTest.java`.

| # | Caso | Esperado |
|---|---|---|
| 1 | login, `DEACTIVATED` + contraseña correcta | `AccountDeactivatedException`; `authenticate` no se invoca; no se emite token |
| 2 | login, `DEACTIVATED` + contraseña incorrecta | `BadCredentialsException` (no revela estado) |
| 3 | login, email inexistente / `ACTIVE` | comportamiento previo sin cambios |
| 4 | reactivate, `DEACTIVATED` + credenciales OK | estado → `ACTIVE`, devuelve `AuthResponse` con token |
| 5 | reactivate, contraseña incorrecta | `BadCredentialsException`; estado sigue `DEACTIVATED` (`save` nunca llamado) |
| 6 | reactivate, email inexistente | `BadCredentialsException` |
| 7 | reactivate, ya `ACTIVE` | idempotente: devuelve `AuthResponse`, sin error |
| 8 | reactivate, `INACTIVE` (sin confirmar) | `DisabledException`; estado no cambia |
| 9 | reactivate, `DELETED` | `LockedException` |
| 10 | reactivate con `targetRole` sin perfil | excepción de `ifUserLacksProfileForTargetRole…`; rollback (sigue `DEACTIVATED`) |
| 11 | `UserServiceImpl.reactivateAccount` en estado ≠ `DEACTIVATED` | `BusinessConflictException` `ACCOUNT_NOT_DEACTIVATED` |
| 12 | Tras reactivar, `UserPrincipal.isEnabled()` | `true` |

Prueba de regresión AC #5: no existe ningún `save`/`delete` sobre repos de bookings, materiales, ratings o perfiles en `reactivate` (`verifyNoInteractions` si el test mockea esos repos, o test de integración que cuente filas antes/después).

---

## 7. Checklist de entrega

- [ ] `./mvnw test` verde.
- [ ] Migración `migration-user-account-status-deactivated.sql` incluida y mencionada en el PR (hay que correrla en MySQL **antes** de desplegar).
- [ ] Swagger muestra `POST /api/v1/auth/reactivate`.
- [ ] Manual: poner un usuario en `DEACTIVATED` por SQL (`UPDATE users SET account_status='DEACTIVATED' WHERE email=...`) → login `403 ACCOUNT_DEACTIVATED` → `/auth/reactivate` `200` → ya puede usar endpoints protegidos con el token nuevo.
- [ ] Manual: usuario `INACTIVE` (sin confirmar) sigue recibiendo el error actual en login y `401` en `/auth/reactivate`.
- [ ] PR indica el estado de US-04 (§2.1).
- [ ] Commit: `feat: reactivar cuenta desactivada (US-40)`.
