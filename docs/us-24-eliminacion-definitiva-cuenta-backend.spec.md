# US-24 — Eliminación definitiva de cuenta (Backend Spec)

> **Lee primero:** `AGENTS.md` y `docs/base-standards.md`. Esta spec está escrita para ejecutarse **en orden, paso a paso**. No improvises fuera de lo indicado; si algo no coincide con el código real, detenete y marcalo en el PR en vez de inventar.

## 0. Metadata

| Campo | Valor |
|---|---|
| ID Historia | US-24 |
| Story Points | 1 en el tablero (la estimación real es mayor: ver §2) |
| Prioridad | Baja |
| Módulo | `users` |
| Tags | #RN-04 |
| Relacionadas | US-04 (desactivación), US-37 (perfil alumno), US-38 (perfil tutor), US-10 y US-11 (reservas) |
| Repo | `knowlink-backend` (frontend: `knowlink-frontend/docs/us-24-eliminacion-definitiva-cuenta-frontend.spec.md`) |

**Historia:** Como usuario registrado, quiero eliminar definitivamente mi cuenta, para dejar de usar la plataforma de forma permanente y que mis datos personales sean borrados.

---

## 1. Criterios de aceptación → cómo se cubren

| # | Criterio | Capa | Cobertura |
|---|---|---|---|
| 1 | Diálogo de confirmación explícita previo | **Frontend** | Backend además exige la **contraseña actual** en el request |
| 2 | Cuenta eliminada invisible para el resto | Backend | Estado `DELETED` + datos personales anonimizados + contenido público dado de baja (§4, Paso 6) + verificación de superficies visibles (Paso 9) |
| 3 | Cancelar no cambia nada | **Frontend** | Si no se envía el request, no hay cambio |
| 4 | Bloqueo con reservas activas pendientes, mensaje exacto | Backend + Frontend | `409` código `ACCOUNT_HAS_ACTIVE_BOOKINGS`, `message` = *"No podés dar de baja tu cuenta con reservas activas pendientes"* |
| 5 | Datos personales borrados de forma permanente e irrecuperable | Backend | Borrado/anonimización irreversible (§2.1). No hay soft-delete recuperable de datos personales |

---

## 2. Decisiones de diseño clave (leer antes de codear)

### 2.1 No se puede (ni se debe) hacer `DELETE FROM users`

`users.user_id` es FK de `Booking` (alumno y tutor), `FundsTransfer` (vía booking), `Rating` (rater/rated), `SessionClaim`, `AcademicMaterialReport`, `CommunityPost/Reply`, `Notification`, `TutorRankingEntry`, perfiles, etc. Borrar la fila:
- rompe las FK (o, con cascada, **destruye registros de la contraparte**: historial de clases, calificaciones recibidas, movimientos de dinero);
- es innecesario para cumplir AC #5, que exige borrar los **datos personales**, no el identificador interno.

**Estrategia: borrado de datos personales + lápida (*tombstone*) anonimizada.**

| Dato | Acción |
|---|---|
| `users`: `email`, `password`, `dni`, `phone_number`, `first_name`, `last_name`, `full_name`, `profile_picture_url` | **Sobrescribir de forma irreversible** (Paso 5). La fila queda como lápida con `account_status = DELETED`, `deleted_at = now` |
| `StudentProfile` / `TutorProfile`: foto, `institutional_id`, `address`, `biography`, vínculo MercadoPago | Se anulan / `mercadoPagoLinked=false`, `verified=false`. Se conservan solo campos no personales (carrera) por las FK `NOT NULL` |
| `TutorSubject.description` (texto libre) | Se anula; la materia queda `active=false` |
| Materiales del tutor (archivos) | Se eliminan del storage y de la base (reutilizando US-17) |
| Foto de perfil en storage | Se elimina (`IProfileImageService.deleteByUserId`) |
| Posts/respuestas de comunidad + adjuntos | Baja lógica (`deletedAt`, ya usado por el módulo) y adjuntos eliminados del storage; marcas de "útil" y `TutorRankingEntry` se borran |
| `Notification` (recipient), `Token` (confirmación/reset), `Hold` | Se borran |
| `Booking`, `FundsTransfer`, `Rating`, `SessionClaim`, `AcademicMaterialReport` | **Se conservan** (integridad financiera y de historial de la contraparte). Quedan asociados a la lápida, que ya **no contiene datos personales** |

Esto reemplaza a "borrar la cuenta" en el sentido físico: el `user_id` (UUID opaco) permanece, pero ningún dato permite identificar a la persona. El email y el DNI quedan **libres** para un registro futuro.

> **Pedir confirmación al equipo antes de mergear** (es una decisión de producto/legal, no técnica): (a) conservar `Booking/Rating/FundsTransfer/Claim` anonimizados; (b) los campos libres `Rating.comment` y `SessionClaim.comment` se conservan tal cual. Si el equipo decide lo contrario, anotarlo en el PR; no improvisar.

### 2.2 Qué ve la contraparte (AC #2)

En historiales de reservas, calificaciones o reclamos donde aparecía el usuario eliminado, la contraparte ve **"Usuario eliminado"** sin foto ni datos. Fuera de eso, el usuario no aparece en buscadores, perfil público, ranking, comunidad ni al reservar.

### 2.3 Bloqueos (AC #4 + extra)

Bloquea la eliminación, evaluando **tanto si el usuario es alumno como si es tutor** en la reserva (una cuenta puede tener ambos roles):

1. **Reservas activas** — `BookingConstants.ACTIVE_STATUSES` (`PENDING, BOOKED, IN_PROGRESS`). **Reutilizar la constante; no crear otra lista.** → `409 ACCOUNT_HAS_ACTIVE_BOOKINGS`, mensaje exacto del criterio 4.
2. **(Recomendado, fuera del texto de los criterios — confirmar con el equipo)** Fondos pendientes (`FundsStatus.HELD` o `SUSPENDED_BY_CLAIM` en reservas del usuario) o reclamos abiertos (`ClaimStatus.ACTIVE_STATUSES`) donde participa. → `409 ACCOUNT_HAS_PENDING_OBLIGATIONS`, mensaje *"No podés dar de baja tu cuenta con pagos o reclamos pendientes de resolución."* Si el equipo no lo aprueba, omitir el Paso 4.2 y su test.
3. Cuentas `ADMIN` **no** se pueden eliminar por este endpoint → `403`.

### 2.4 Autenticación del pedido

Acción irreversible: el endpoint exige la contraseña actual en el body (no alcanza con tener un JWT vigente). Contraseña incorrecta → `401` con el mismo mensaje que credenciales inválidas.

### 2.5 JWT de cuentas eliminadas — **corrección de seguridad obligatoria**

`JwtService.isTokenValid` solo compara `username` y expiración; `JwtAuthenticationFilter` **no** consulta `UserDetails.isEnabled()`. Es decir, hoy un JWT emitido antes de que la cuenta pase a `DELETED` (o `DEACTIVATED`) **seguiría autenticando** hasta que expire. Además, tras anonimizar el email, `loadUserByUsername(<email viejo>)` lanza `UsernameNotFoundException` dentro del filtro (que solo captura `JwtException`) → riesgo de `500`. Ver Paso 7.

---

## 3. Stack y reglas (del repo, no negociables)

- Java 21, Spring Boot 3.5.3, MySQL 8 (prod) / H2 (tests), `./mvnw`.
- Código (clases, métodos, variables, logs, comentarios) en **inglés**; mensajes al usuario final en **español**.
- Inyección por constructor (`@RequiredArgsConstructor`), `@Slf4j`, `orElseThrow()`, nunca `.get()`.
- Controllers sin lógica ni acceso a repositorios. `@Operation` + `@ApiResponses` en la interfaz.
- Datos que viven en storage externo (Supabase) **no son transaccionales**: borrarlos **después del commit** (Paso 8).
- Sin Flyway; esta historia **no agrega columnas** → no requiere migración SQL.
- Commits: `feat: descripción en español`.

---

## 4. Cambios paso a paso

### Paso 1 — Request DTO

Archivo nuevo: `.../users/controllers/requests/DeleteAccountRequest.java`

```java
public record DeleteAccountRequest(
        @NotBlank(message = "La contraseña es requerida") String password
) {}
```

### Paso 2 — Endpoint

`IUserController` + `UserControllerImpl` (solo delega):

```java
@PostMapping("/me/delete-account")
@Operation(summary = "Eliminar definitivamente mi cuenta",
        description = "Irreversible. Exige la contraseña actual. Anonimiza y borra los datos personales. "
                + "Rechaza con 409 si hay reservas activas pendientes.")
@ApiResponses({
        @ApiResponse(responseCode = "204", description = "Cuenta eliminada"),
        @ApiResponse(responseCode = "401", description = "Contraseña incorrecta"),
        @ApiResponse(responseCode = "403", description = "Cuenta no eliminable (ADMIN)"),
        @ApiResponse(responseCode = "409", description = "ACCOUNT_HAS_ACTIVE_BOOKINGS / ACCOUNT_HAS_PENDING_OBLIGATIONS")
})
@ResponseStatus(HttpStatus.NO_CONTENT)
void deleteMyAccount(@AuthenticationPrincipal UserPrincipal principal,
                     @RequestBody @Valid DeleteAccountRequest request);
```

Ruta protegida (**no** agregar a `PUBLIC_WHITELIST`). Se usa `POST` y no `DELETE` porque lleva body y algunos proxies lo descartan en `DELETE`. La implementación llama `accountDeletionService.deleteAccount(principal.getUser().getUserId(), request.password())`.

### Paso 3 — Servicio de orquestación

Archivos nuevos: `.../users/services/interfaces/IAccountDeletionService.java` y `.../implementations/AccountDeletionServiceImpl.java`.

```java
void deleteAccount(UUID userId, String rawPassword);
```

`deleteAccount` es `@Transactional` y ejecuta **en este orden** (todo o nada; si algo falla, rollback y la cuenta queda intacta):

1. `User user = userService.lockForUpdateOrThrowException(userId);` (ya existe).
2. `user.getRole() == ADMIN` → `AccessDeniedException` (ya tiene handler `403`).
3. `!passwordEncoder.matches(rawPassword, user.getPassword())` → `BadCredentialsException`.
4. **Bloqueos** (Paso 4).
5. **Recolectar rutas de storage** a borrar (fotos, materiales, adjuntos) **antes** de anular nada (Paso 8).
6. **Borrados/bajas** (Paso 6).
7. **Anonimizar** (Paso 5).
8. Publicar `AccountDeletedEvent(userId, storagePaths)` (Paso 8).
9. `log.info("Account permanently deleted for userId '{}'", userId);` — **nunca** loguear email, nombre ni DNI.

### Paso 4 — Bloqueos (en `IBookingRepository` y afines; solo lectura)

**4.1 Reservas activas.** En `IBookingRepository` agregar (si no existe algo equivalente):

```java
boolean existsByStudent_UserIdAndBookingStatusIn(UUID userId, List<BookingStatus> statuses);
boolean existsByTutor_UserIdAndBookingStatusIn(UUID userId, List<BookingStatus> statuses);
```

Si alguna es `true` con `BookingConstants.ACTIVE_STATUSES` → `throw new BusinessConflictException("ACCOUNT_HAS_ACTIVE_BOOKINGS", "No podés dar de baja tu cuenta con reservas activas pendientes", "userId '%s' has active bookings")`. (`BusinessConflictException` y su handler `409` ya existen.)

**4.2 (Recomendado, ver §2.3.2)** `existsBy…` análogos en `IFundsTransferRepository` (`booking.student/tutor.userId` + `FundsStatus.HELD, SUSPENDED_BY_CLAIM`) y en el repositorio de reclamos (`claimant` o reserva donde participa, `ClaimStatus.ACTIVE_STATUSES`). → `BusinessConflictException("ACCOUNT_HAS_PENDING_OBLIGATIONS", …)`.

### Paso 5 — Anonimizar la lápida `User`

En `UserServiceImpl` agregar `User anonymizeForDeletion(User user)` (invocado por el servicio del Paso 3), con constantes en `users/utils/Constants.java`:

```java
user.setEmail("deleted+" + user.getUserId() + "@knowlink.invalid"); // unique + NOT NULL; dominio .invalid (RFC 2606)
user.setPassword(passwordEncoder.encode(UUID.randomUUID().toString())); // irrecuperable
user.setFirstName(null);
user.setLastName(null);
user.setFullName(DELETED_USER_DISPLAY_NAME); // "Usuario eliminado"
user.setDni(null);
user.setPhoneNumber(null);
user.setProfilePictureUrl(null);
user.setAccountStatus(AccountStatus.DELETED);
user.setDeletedAt(LocalDateTime.now());
return userRepository.save(user);
```

Después, verificar con `grep` que **ningún** código asume `firstName/lastName/dni/phoneNumber` no nulos (mappers de historial, reclamos, admin); corregir con fallback a `fullName` solo donde haya riesgo de NPE y anotarlo en el PR.

### Paso 6 — Borrados y bajas

Cada módulo expone su operación (en su servicio o repositorio con `@Modifying`); no tocar repositorios ajenos desde `users` si ya existe un servicio del módulo.

| Qué | Cómo |
|---|---|
| `Hold` del alumno | borrar por `findByStudent_UserId` |
| `Notification` (recipient) | `deleteByRecipient_UserId` |
| `Token` (todos los tipos) | `tokenService.deleteTokensByUser(user)` y `deletePasswordResetTokensByUser(user)` (ya existen) |
| `CommunityReplyHelpful` del usuario; `TutorRankingEntry` del tutor | borrar por usuario |
| `CommunityPost` / `CommunityReply` del autor | baja lógica con `deletedAt = now` (mismo mecanismo del módulo); recolectar los `CommunityAttachment` para borrado en storage |
| `StudentProfile` | `profilePictureUrl = null`, `institutionalId = null` |
| `TutorProfile` | `biography = null`, `profilePictureUrl = null`, `institutionalId = null`, `address = null`, `mercadoPagoLinked = false`, `verified = false` |
| `TutorSubject` | `description = null` y `active = false` (flag de US-12) |
| Materiales del tutor | por cada material de sus `TutorSubject`: `IMaterialService.delete(materialId, tutorUserId)` (US-17). Si ese método rechaza por una regla de negocio distinta de "no encontrado", **detenete y marcalo en el PR** |

Los perfiles **no se eliminan** (FK `NOT NULL` desde `TutorSubject`/`Booking`); se vacían de datos personales.

### Paso 7 — `JwtAuthenticationFilter` (obligatorio)

Archivo: `.../security/filter/JwtAuthenticationFilter.java`. Dentro del `if (userEmail != null && …getAuthentication() == null)`:

```java
UserDetails userDetails;
try {
    userDetails = userDetailsService.loadUserByUsername(userEmail);
} catch (UsernameNotFoundException ex) {
    filterChain.doFilter(request, response); // unauthenticated -> GlobalAuthenticationEntryPoint answers 401
    return;
}
// ...
if (userDetails.isEnabled() && jwtService.isTokenValid(jwt, userDetails) && !isBlacklisted) { ... }
```

Efecto: tokens de cuentas `DELETED`, `DEACTIVATED` o sin confirmar dejan de autenticar de inmediato (también beneficia a US-04/US-40). Agregar test en `src/test/.../security/filter/`.

### Paso 8 — Borrado en storage después del commit

Evento nuevo `users/events/AccountDeletedEvent(UUID userId, List<String> storagePaths)` y listener `@TransactionalEventListener(phase = AFTER_COMMIT)` que, por cada ruta, llama a `ISupabaseStorageService.delete(...)` / `IProfileImageService.deleteByUserId(userId)` y a la eliminación de adjuntos de comunidad. Cada ruta en `try/catch`: un fallo se loguea con `log.warn("Storage cleanup failed for userId '{}'", userId, ex)` (sin la ruta si contiene el nombre del usuario) y **no** revierte la eliminación. Dejar en el PR constancia de que un fallo de storage puede dejar archivos huérfanos para limpieza manual.

### Paso 9 — Invisibilidad (AC #2): verificar y cerrar fugas

El estado `DELETED` ya excluye, por filtros existentes (`== ACTIVE`): ranking (`TutorRankingServiceImpl`, `ITutorRankingRepository`), "reservable" en comunidad (`CommunityMapper`) y reset de contraseña. **Comprobar con un test por cada superficie** que un tutor/alumno `DELETED` no aparece:

1. Búsqueda unificada / filtros de tutores (`TutorSearchSpecifications`): agregar filtro `user.accountStatus = ACTIVE` si falta.
2. Perfil público del tutor: `404` si `DELETED`.
3. Crear reserva / hold contra un tutor `DELETED`: rechazar.
4. Comunidad: posts/respuestas del eliminado no se listan (baja lógica); si quedan hilos ajenos con sus respuestas dadas de baja, no deben fallar.
5. Historiales de reservas de la contraparte: muestran "Usuario eliminado" sin foto.
6. `GET /api/v1/users/{userId}` de una lápida: `404`.

No modificar los filtros que ya funcionen; solo agregar los que falten.

---

## 5. Contrato final para el frontend

| Método | Ruta | Body | Respuesta |
|---|---|---|---|
| POST | `/api/v1/users/me/delete-account` | `{password}` | `204` sin body · `401` contraseña incorrecta · `403` ADMIN · `409` `detail=ACCOUNT_HAS_ACTIVE_BOOKINGS` (`message` = texto del criterio 4) · `409` `detail=ACCOUNT_HAS_PENDING_OBLIGATIONS` · `400` validación |

Errores con forma `{status, message, detail}` (`ApiError`).

---

## 6. Tests

| # | Caso | Esperado |
|---|---|---|
| 1 | Alumno sin reservas activas, contraseña OK | `204`; usuario `DELETED`; email = `deleted+<id>@knowlink.invalid`; `dni`, `phone`, nombres = `null`; `fullName` = "Usuario eliminado" |
| 2 | Contraseña incorrecta | `401`; **nada** cambia (usuario sigue `ACTIVE`, datos intactos) |
| 3 | Alumno con reserva `PENDING` / `BOOKED` / `IN_PROGRESS` | `409 ACCOUNT_HAS_ACTIVE_BOOKINGS`, mensaje exacto; nada cambia |
| 4 | Tutor con reserva activa como tutor | idem 3 |
| 5 | Cuenta dual (alumno+tutor), bloqueada solo por el rol tutor | `409` |
| 6 | Solo reservas `COMPLETED`/`CANCELLED`/`EXPIRED` | se elimina |
| 7 | ADMIN | `403` |
| 8 | (Si se aprobó §2.3.2) fondos `HELD` o reclamo abierto | `409 ACCOUNT_HAS_PENDING_OBLIGATIONS` |
| 9 | Perfiles | `TutorProfile` sin biography/address/foto/institutionalId, `mercadoPagoLinked=false`; materias `active=false`, `description=null` |
| 10 | Limpieza | no quedan `Notification`, `Token`, `Hold` del usuario; posts/respuestas con `deletedAt` |
| 11 | Conservación | `Booking`, `FundsTransfer`, `Rating` del usuario siguen existiendo (mismo conteo antes/después) |
| 12 | Rollback | si falla un paso intermedio (p. ej. `IMaterialService.delete` lanza), el usuario sigue `ACTIVE` y sin cambios |
| 13 | Login con el email original tras eliminar | `401` credenciales inválidas |
| 14 | Re-registro con el mismo email y DNI | permitido |
| 15 | JWT emitido antes de eliminar | `401` en un endpoint protegido (Paso 7) |
| 16 | Storage falla en el listener AFTER_COMMIT | la eliminación **no** se revierte; se loguea warning |
| 17 | Superficies del Paso 9 | cada una no muestra al eliminado |
| 18 | Idempotencia/concurrencia | dos requests simultáneos: uno `204`, el otro `401`/`404` (el JWT ya no autentica); nunca `500` |

Seguir el estilo de `LogoutFlowIntegrationTest` y `PasswordRecoveryControllerTest`.

---

## 7. Checklist de entrega

- [ ] `./mvnw test` verde.
- [ ] Sin migración SQL (no hay cambios de esquema); el PR lo aclara.
- [ ] Swagger muestra `POST /api/v1/users/me/delete-account`.
- [ ] Manual: crear usuario → eliminar → en la base la fila `users` no contiene email/DNI/teléfono/nombre reales; el historial del otro participante muestra "Usuario eliminado".
- [ ] Manual: con reserva `BOOKED` → `409` con el mensaje exacto.
- [ ] Decisiones pendientes del equipo anotadas en el PR: §2.1 (a)(b) y §2.3.2.
- [ ] Commit: `feat: eliminación definitiva de cuenta (US-24)`.
