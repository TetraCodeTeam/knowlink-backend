# US-50 — Resolución única por disputa + rebase de la PR #117 (Backend)

> **Para ejecutar con:** OpenCode + DeepSeek V4 Flash
> **Repo:** `TetraCodeTeam/knowlink-backend` — base `dev`
> **Leer primero:** `CLAUDE.md` y `docs/base-standards.md`

## 0. Metadata

| Campo | Valor |
|---|---|
| Historia | US-50 Gestionar reclamos de sesiones (admin) |
| Relacionadas | US-51 (reclamo de sesión), US-41 (confirmación con token), US-15 (fondos retenidos), US-27 (denuncias de material, mismo criterio) |
| Módulos | `claims`, `payments`, `tutors.support`, `admin.claims` |
| Alcance | Solo backend |
| Rama de trabajo parte A | `fix/us-50-single-dispute-resolution` (desde `origin/dev`) |
| Rama de trabajo parte B | la rama de la PR #117 (`gh pr view 117 --json headRefName -q .headRefName`) |

El trabajo tiene **dos partes y debe hacerse en este orden**:

- **Parte A:** cerrar en `dev` el camino que todavía permite resolver reclamos de una misma sesión por separado.
- **Parte B:** rebasar la PR #117 sobre `dev` quitándole la lógica de reasignación de bloqueo (`reassignBlockingClaim`) y resolviendo sus conflictos.

---

## 1. Contexto

Tutor y alumno pueden abrir un reclamo cada uno sobre la misma reserva. Hay **un solo pago retenido** (`FundsTransfer`), así que tiene que haber **una sola decisión** (a favor del tutor o del alumno), con **una sola justificación**, que cierre ambos reclamos y mueva los fondos una vez.

### Estado actual de `dev` (verificado leyendo el código)

Ya existe el modelo de disputa:

- `SessionClaim.disputeId`: el primer reclamo de la reserva es el representante (`disputeId == claimId`); el segundo hereda el `disputeId` (`SessionClaimServiceImpl.existingDisputeId`).
- `IAdminSessionClaimRepository.assignIfOpen(disputeKey, admin, now)`: pasa a `IN_REVIEW` y asigna al mismo admin **todos** los reclamos `OPEN` de la disputa.
- `AdminSessionClaimServiceImpl.resolve`: una decisión + una justificación; `resolveDispute(disputeKey, …)` cierra todos los reclamos de la disputa; `FundsResolutionService.resolveByAdminDecision(booking, favoredParty, disputeKey)` mueve los fondos una sola vez y exige `blockingClaimId == disputeKey`.
- `FundsLedgerService.markSuspendedByClaim` es idempotente: si los fondos ya están suspendidos conserva el bloqueo original, que es el representante de la disputa.
- `FundsResolutionService.retryAfterClaimResolution` **ya** lanza `BusinessConflictException(CLAIM_STILL_OPEN)` si quedan reclamos `OPEN` o `IN_REVIEW`.

### Huecos que quedan en `dev`

1. **`SessionClaimServiceImpl.updateStatus`** (llamado por `PATCH` de `AdminSupportControllerImpl.updateClaimStatus`) toma **un solo reclamo por `claimId`**, ignora la disputa y permite `OPEN → RESOLVED` e `IN_REVIEW → RESOLVED` sin favorecido, sin justificación, sin verificar admin y sin mover fondos. Con eso se pueden cerrar los reclamos por separado y romper el invariante `blockingClaimId == disputeId`.
2. **Reclamo tardío:** si el segundo reclamo se crea cuando la disputa ya está `IN_REVIEW`, nace `OPEN` y sin admin asignado, aunque la disputa ya la tomó un admin.
3. **`SessionClaimServiceImpl.hasActiveClaim`** cuenta solo `OPEN` (`existsByBooking_BookingIdAndStatus(…, OPEN)`). Cuando un admin toma la disputa (`IN_REVIEW`) devuelve `false` y `BookingServiceImpl.confirmSession` (línea ~304) deja de bloquear la confirmación con token. La PR #117 lo corrige (commit `30c1303`); **no duplicar ese cambio en la parte A** (ver 5.3).

### La PR #117 (3 commits sobre `7d4687f`)

| Commit | Contenido | Decisión |
|---|---|---|
| `4d9de63` | Tests del bucket de materiales (`SupabaseStorageServiceImplTest`, `TutorMaterialSupportServiceTest`) | **Conservar** |
| `30c1303` | `hasActiveClaim` cuenta `OPEN` + `IN_REVIEW` | **Conservar** |
| `b747aa0` | `reassignBlockingClaim`, reasignación dentro de `updateStatus`, 409 con `DuplicateResourceException` en el retry, `claimantRole` en soporte, mensajes de notificación | **Conservar solo `claimantRole` y la anotación Swagger 409; descartar el resto** |

Motivo: `b747aa0` parte del modelo de reclamos independientes (busca "el otro reclamo" por `bookingId` y mueve el bloqueo). Eso contradice el modelo de disputa de `dev`.

---

## 2. Decisiones de diseño

- **D1.** La unidad de decisión es la **disputa**, no el reclamo. Cada reclamo conserva su motivo y comentario; comparten resultado, justificación y movimiento de fondos.
- **D2.** Solo `AdminSessionClaimServiceImpl.assign` y `.resolve` pueden pasar un reclamo a `IN_REVIEW` / `RESOLVED`. `updateStatus` deja de permitir esas transiciones.
- **D3.** El bloqueo de fondos **nunca se reasigna**: queda apuntando al representante de la disputa hasta que se resuelve. Por eso `reassignBlockingClaim` no se necesita.
- **D4.** Un reclamo creado sobre una disputa que ya tiene admin nace `IN_REVIEW` con **ese mismo admin**.
- **D5.** `retryAfterClaimResolution` queda como herramienta de contingencia y no se modifica.

---

## 3. Criterios de aceptación (Gherkin)

```gherkin
Característica: Resolución única de reclamos de una misma sesión

  Escenario: Tomar la solicitud asigna ambos reclamos al mismo admin
    Dado una reserva con un reclamo OPEN del alumno y uno OPEN del tutor, mismo disputeId
    Cuando el admin A toma el reclamo del alumno
    Entonces ambos reclamos pasan a IN_REVIEW asignados al admin A

  Escenario: Una única decisión cierra ambos reclamos
    Dado la disputa tomada por el admin A
    Cuando el admin A resuelve a favor del tutor con una justificación
    Entonces ambos reclamos quedan RESOLVED con el mismo favoredParty y la misma justificación
    Y los fondos se mueven una sola vez hacia el tutor

  Escenario: No se puede resolver un reclamo suelto por el endpoint de estado
    Dado una disputa con dos reclamos activos
    Cuando se envía PATCH de estado RESOLVED sobre uno de los reclamos
    Entonces la respuesta es 409
    Y ambos reclamos mantienen su estado
    Y los fondos siguen en SUSPENDED_BY_CLAIM

  Escenario: No se puede tomar un reclamo suelto por el endpoint de estado
    Dado un reclamo OPEN de una disputa
    Cuando se envía PATCH de estado IN_REVIEW
    Entonces la respuesta es 409

  Escenario: Reclamo tardío sobre una disputa ya tomada
    Dado una disputa con un reclamo IN_REVIEW asignado al admin A
    Cuando la otra parte crea su reclamo sobre la misma sesión
    Entonces el reclamo nuevo hereda el disputeId
    Y nace IN_REVIEW asignado al admin A

  Escenario: Reclamo sobre una sesión sin disputa activa
    Dado una reserva sin reclamos activos
    Cuando una parte crea un reclamo
    Entonces nace OPEN, sin admin y con disputeId igual a su claimId

  Escenario: Dos admins resuelven a la vez
    Dado una disputa IN_REVIEW
    Cuando dos admins envían la resolución simultáneamente
    Entonces una se aplica y la otra recibe 409 (caso ya resuelto o no asignado)

  Escenario: El tutor no puede confirmar con token mientras la disputa está en revisión
    Dado un reclamo IN_REVIEW sobre la reserva
    Cuando el tutor intenta confirmar la sesión con token
    Entonces se lanza ConfirmationBlockedByClaimException

  Escenario: Reintento de fondos con reclamos activos
    Dado una reserva con algún reclamo OPEN o IN_REVIEW
    Cuando se llama a retryAfterClaimResolution
    Entonces responde 409 CLAIM_STILL_OPEN y los fondos siguen suspendidos
```

---

## 4. Stack confirmado y advertencias

- Java 21, Spring Boot 3.5, MySQL 8, Spring Data JPA, Lombok, SpringDoc, JUnit 5 + Mockito + AssertJ.
- Inyección por constructor; excepciones custom lanzadas desde `services/` y manejadas en `exceptions/`; `@Slf4j`; `orElseThrow(...)`, nunca `.get()`.
- **No hay migración de base de datos** en este spec: no se agregan columnas ni cambia el esquema.
- **Advertencia:** este spec se armó leyendo el código, sin poder compilar ni correr Maven. Antes de dar por buenas las firmas, ejecutar `./mvnw -q -DskipTests compile`.
- **Advertencia:** `BusinessConflictException` se usa con tres argumentos `(código, mensaje al usuario, mensaje de log)`. Confirmar el constructor en la clase antes de usarlo.
- **Advertencia:** el frontend no parece usar el `PATCH` de estado de reclamos. Verificar con `grep` en `knowlink-frontend` antes de cambiar su comportamiento.

---

## 5. Parte A — cambios en `dev`

```bash
git fetch origin
git checkout -b fix/us-50-single-dispute-resolution origin/dev
```

### 5.1 `SessionClaimServiceImpl.updateStatus` — bloquear `IN_REVIEW` y `RESOLVED`

Archivo: `src/main/java/com/knowlink/api/claims/services/implementations/SessionClaimServiceImpl.java`

Reemplazar el cuerpo posterior a la carga del reclamo. Conservar la validación de transición (`canTransitionTo` → `ValidationException`) y el retorno si el estado no cambia. Para cualquier otra transición permitida (solo quedan `IN_REVIEW` y `RESOLVED`), lanzar conflicto:

```java
private static final String CLAIM_STATUS_MANAGED_BY_DISPUTE = "CLAIM_STATUS_MANAGED_BY_DISPUTE";

// dentro de updateStatus, después de validar la transición y del "if (currentStatus == status) return;"
throw new BusinessConflictException(
        CLAIM_STATUS_MANAGED_BY_DISPUTE,
        "Este reclamo se gestiona junto con el de la otra parte. Usá la gestión de reclamos de administración.",
        "Claim " + claimId + " status cannot be changed individually; use admin assign/resolve");
```

- Eliminar el código que quedó inalcanzable (`claim.setStatus`, `setActiveKey(null)`, `save` y las dos notificaciones de `updateStatus`). No tocar las notificaciones de `create` ni los listeners de resolución.
- Marcar el endpoint `PATCH` en `IAdminSupportController` con `@Deprecated` y agregar `@ApiResponse(responseCode = "409", ...)`. **No** eliminarlo en este cambio.
- Limpiar imports que queden sin uso.

### 5.2 `SessionClaimServiceImpl.create` — reclamo tardío hereda admin

Refactorizar `existingDisputeId` para devolver el reclamo representante y reutilizarlo:

```java
private Optional<SessionClaim> activeDisputeRepresentative(UUID bookingId) {
    return sessionClaimRepository.findFirstByBooking_BookingIdAndStatusInOrderByCreatedAtAsc(
            bookingId, ClaimStatus.ACTIVE_STATUSES);
}
```

En `create`, antes de `saveAndFlush`:

```java
Optional<SessionClaim> representative = activeDisputeRepresentative(bookingId);
UUID disputeId = representative
        .map(r -> r.getDisputeId() != null ? r.getDisputeId() : r.getClaimId())
        .orElse(null);
ClaimStatus initialStatus = ClaimStatus.OPEN;
User inheritedAdmin = null;
LocalDateTime assignedAt = null;
if (representative.isPresent()
        && representative.get().getStatus() == ClaimStatus.IN_REVIEW
        && representative.get().getAssignedAdmin() != null) {
    initialStatus = ClaimStatus.IN_REVIEW;
    inheritedAdmin = representative.get().getAssignedAdmin();
    assignedAt = LocalDateTime.now();
}
```

Y en el `builder`: `.status(initialStatus)`, `.assignedAdmin(inheritedAdmin)`, `.assignedAt(assignedAt)`, `.disputeId(disputeId)`. Verificar que `SessionClaim` tenga esos campos en el builder (`assignedAdmin`, `assignedAt` existen según `assignIfOpen`).

Notas:
- `create` ya bloquea la reserva (`findBookingForUpdateOrThrow`), así que no hay carrera entre las dos partes.
- No notificar al admin en este cambio (queda como pregunta abierta, sección 9).
- `markSuspendedByClaim` es idempotente: no tocar.

### 5.3 Lo que NO va en la parte A

- `hasActiveClaim` (`OPEN` + `IN_REVIEW`): llega por el commit `30c1303` de la PR #117. **Alternativa** si la PR se demora: aplicar el mismo cambio en una rama aparte (`existsByBooking_BookingIdAndStatusIn(bookingId, ClaimStatus.ACTIVE_STATUSES)` y el Javadoc de `ISessionClaimService`). Si se aplica antes, al rebasar la PR el hunk de `30c1303` se resuelve quedándose con la versión que ya está en `dev` (es idéntica).
- Agrupación por `disputeId` en `TutorSupportServiceImpl.getSupport`: ya está implementada, fuera de alcance.

### 5.4 Tests de la parte A

Archivo: `src/test/java/com/knowlink/api/claims/services/implementations/SessionClaimServiceImplTest.java`

- `updateStatus_toResolved_throwsConflictAndDoesNotPersist`: verifica `BusinessConflictException` con `CLAIM_STATUS_MANAGED_BY_DISPUTE`, `verify(sessionClaimRepository, never()).save(any())`, `verifyNoInteractions(fundsLedgerService)`.
- `updateStatus_toInReview_throwsConflict`.
- `updateStatus_sameStatus_returnsWithoutChanges`.
- `updateStatus_invalidTransition_throwsValidationException` (ej. `RESOLVED → OPEN`).
- `create_whenRepresentativeInReview_inheritsAdminAndStatus`.
- `create_whenRepresentativeOpen_staysOpenWithoutAdmin`.
- `create_firstClaim_opensDisputeWithOwnId`.

Archivo: `src/test/java/com/knowlink/api/admin/claims/AdminSessionClaimServiceImplTest.java`

- Confirmar que existe un test que verifica que `resolve` llama una sola vez a `resolveByAdminDecision` y a `resolveDispute`. Si no existe, agregarlo.

Comando: `./mvnw -q test -Dtest='SessionClaimServiceImplTest,AdminSessionClaimServiceImplTest,FundsResolutionService*Test'`

Abrir la PR de la parte A contra `dev` y **mergearla antes de la parte B**.

---

## 6. Parte B — rebase de la PR #117

### 6.1 Preparación

```bash
git fetch origin
BRANCH=$(gh pr view 117 --json headRefName -q .headRefName)
git checkout "$BRANCH"
git branch backup/pr-117-before-rebase     # respaldo local
git rebase origin/dev
```

No usar `git rebase -i`. Si aparece un conflicto, resolver, luego `git add <archivos>` y `git rebase --continue`.

### 6.2 Resolución de conflictos

La simulación del merge de la PR sobre el `dev` actual dio **conflicto de contenido en dos archivos** y fusión automática (sin conflicto, pero hay que revisarla a mano) en otros dos.

#### Conflicto 1 — `claims/repositories/SessionClaimRepository.java`

Cada lado agregó un método de búsqueda a la misma zona.

- `HEAD` (dev): `findFirstByBooking_BookingIdAndStatusInOrderByCreatedAtAsc(UUID bookingId, Collection<ClaimStatus> statuses)`
- PR: `findFirstByBooking_BookingIdAndClaimIdNotAndStatusInOrderByCreatedAtAsc(UUID bookingId, UUID claimId, Collection<ClaimStatus> statuses)`

**Resolución:** quedarse **solo con el de `dev`** (lo usan `create` y el modelo de disputa). Eliminar el de la PR: solo lo usaba la reasignación que se descarta.

#### Conflicto 2 — `payments/services/FundsResolutionService.java` (dos hunks)

**Hunk de imports**

- `HEAD`: importa `BusinessConflictException` y `FundsExecutionException`.
- PR: importa `DuplicateResourceException`.

**Resolución:** dejar los imports de `HEAD`. Quitar `DuplicateResourceException`, que ya no se usa en este archivo.

**Hunk de `retryAfterClaimResolution`**

- `HEAD`: `existsByBooking_BookingIdAndStatusIn(…, ACTIVE_STATUSES)` → `BusinessConflictException(CLAIM_STILL_OPEN, CLAIM_STILL_OPEN_MESSAGE, …)`.
- PR: misma consulta, pero lanza `DuplicateResourceException("BOOKING_HAS_ACTIVE_CLAIMS", …)` y agrega un comentario sobre reclamos independientes.

**Resolución:** quedarse **con `HEAD` completo** y descartar el comentario de la PR. `dev` ya tiene la verificación y el código de error correcto.

#### Fusiones sin conflicto que hay que corregir a mano

**`claims/services/implementations/SessionClaimServiceImpl.java`**

Tras el rebase quedan mezclados el código de `dev` y el de la PR. Dejar el archivo como lo deja la parte A, con estas particularidades:

- `hasActiveClaim` con `existsByBooking_BookingIdAndStatusIn(bookingId, ClaimStatus.ACTIVE_STATUSES)` (de `30c1303`): **conservar**.
- Eliminar el bloque `if (status == ClaimStatus.RESOLVED) { … findFirst…ClaimIdNot… reassignBlockingClaim … }`.
- Eliminar el método privado `statusChangeMessage` y volver a las notificaciones originales **solo si** el método sigue existiendo; si la parte A ya las eliminó, no reponerlas.

**`payments/services/FundsLedgerService.java`**

- Eliminar por completo el método `reassignBlockingClaim` y su Javadoc.

#### Archivos de la PR que se conservan tal cual

- `tutors/support/controllers/responses/TutorSupportClaimResponse.java`: campo `Role claimantRole` (después de `reason`).
- `tutors/support/data/mappers/TutorSupportMapper.java`: pasa `claim.getClaimantRole()`.
- `payments/controllers/implementations/AdminFundsControllerImpl.java`: `@ApiResponse` 409 (coincide con lo que ya lanza `dev`).
- `claims/services/interfaces/ISessionClaimService.java`: Javadoc de `hasActiveClaim` (activo = pendiente o en revisión).
- Tests de bucket: `SupabaseStorageServiceImplTest`, `TutorMaterialSupportServiceTest`.

Si agregar `claimantRole` al `record` rompe algún constructor en tests o mappers, actualizarlos pasando el nuevo argumento en la posición correcta.

### 6.3 Tests de la PR a eliminar o adaptar

| Archivo | Acción |
|---|---|
| `payments/services/FundsLedgerServiceTest.java` | Eliminar los tests de `reassignBlockingClaim` (+53 líneas de la PR) |
| `payments/services/FundsResolutionServiceTest.java` | Eliminar `retryAfterClaimResolution_counterpartClaimStillActive_…` y el import de `DuplicateResourceException`; `FundsResolutionServiceAdminDecisionTest` ya cubre el 409 `CLAIM_STILL_OPEN` |
| `claims/services/implementations/SessionClaimServiceImplTest.java` | Eliminar `updateStatus_resolvedWhileCounterpartClaimActive_reassignsFundsBlock`, `updateStatus_resolvedWithoutOtherActiveClaim_doesNotReassignFundsBlock`, `updateStatus_inReview_doesNotLookForOtherClaims` y los dos `updateStatus_*_messagesNameWhoseClaimItIs` (usan código eliminado). Conservar los tests de `hasActiveClaim` con `IN_REVIEW`, si los hay, y los tests de la parte A |

### 6.4 Verificación posterior al rebase

```bash
grep -rn "reassignBlockingClaim" src                         # debe quedar vacío
grep -rn "BOOKING_HAS_ACTIVE_CLAIMS" src                     # debe quedar vacío
grep -rn "ClaimIdNotAndStatusIn" src                         # debe quedar vacío
grep -rn "DuplicateResourceException" src/main/java/com/knowlink/api/payments/services/FundsResolutionService.java   # debe quedar vacío
grep -n "ACTIVE_STATUSES" src/main/java/com/knowlink/api/claims/services/implementations/SessionClaimServiceImpl.java # hasActiveClaim incluido
./mvnw -q test
```

Si todo pasa:

```bash
git push --force-with-lease origin "$BRANCH"
```

`--force-with-lease` es necesario porque el rebase reescribe la historia. Avisar a quien más use esa rama.

---

## 7. No hacer

- No reintroducir `reassignBlockingClaim` ni ninguna lógica que mueva `blockingClaimId` entre reclamos.
- No resolver reclamos por `bookingId` o `claimId` suelto: siempre por `disputeKey`.
- No eliminar el endpoint `PATCH` de estado en este cambio: solo bloquear las transiciones y deprecarlo.
- No tocar `AdminSessionClaimServiceImpl.assign` ni `.resolve`, ni `resolveByAdminDecision`, salvo para agregar tests.
- No cambiar `retryAfterClaimResolution`.
- No agregar columnas ni migraciones SQL.
- No usar `git rebase -i`, `git push --force` (sin `--with-lease`) ni `git reset --hard` sobre la rama de la PR sin el respaldo local.
- No mergear la PR #117 antes de la parte A.

---

## 8. Definition of Done

- [ ] `updateStatus` rechaza `IN_REVIEW` y `RESOLVED` con 409 `CLAIM_STATUS_MANAGED_BY_DISPUTE`.
- [ ] Un reclamo creado sobre una disputa `IN_REVIEW` nace `IN_REVIEW` con el mismo admin.
- [ ] `hasActiveClaim` cuenta `OPEN` e `IN_REVIEW`.
- [ ] `claimantRole` presente en `TutorSupportClaimResponse` y en el mapper.
- [ ] No quedan referencias a `reassignBlockingClaim`, `BOOKING_HAS_ACTIVE_CLAIMS` ni a `findFirst…ClaimIdNot…`.
- [ ] Tests nuevos de las partes A y B en verde y `./mvnw -q test` completo en verde.
- [ ] La PR de la parte A está mergeada en `dev` antes del push de la parte B.
- [ ] La PR #117 queda mergeable contra `dev` sin conflictos.
- [ ] Documentación Swagger actualizada (409 en el `PATCH` de estado y en el reintento de fondos).

---

## 9. Preguntas abiertas

1. ¿Se notifica al admin asignado cuando llega un reclamo tardío a su disputa? (hoy no hay aviso).
2. ¿Los listeners de resolución (`SessionClaimNotificationListener`) aclaran de quién es cada reclamo cuando ambas partes reciben el resultado?
3. ¿Se elimina el `PATCH` de estado de reclamos en un cambio posterior, o se deja deprecado?
4. ¿Los reclamos activos pueden retirarse? Si sí, falta definir qué pasa con una disputa que queda con un solo reclamo.
