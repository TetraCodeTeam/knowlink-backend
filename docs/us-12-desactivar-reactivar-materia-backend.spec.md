# US-12 — Desactivar / Reactivar materia ofrecida (Backend Spec)

> **Lee primero:** `AGENTS.md` y `docs/base-standards.md`. Esta spec está escrita para ejecutarse **en orden, paso a paso**. No improvises fuera de lo indicado; si algo no coincide con el código real, detenete y marcalo en el PR en vez de inventar.

## 0. Metadata

| Campo | Valor |
|---|---|
| ID Historia | US-12 |
| Story Points | 3 |
| Prioridad | Baja |
| Módulo | `tutors` |
| Relacionadas | US-48 (registrar materia), US-25 (solicitud de validación), US-38 (perfil tutor) |
| Repo | `knowlink-backend` |

**Historia:** Como tutor, quiero desactivar temporalmente una materia que ofrezco y reactivarla después, para dejar de recibir reservas sin perder la información ni tener que eliminarla y volver a registrarla.

---

## 1. Criterios de aceptación → cómo se cubren

| # | Criterio | Capa | Cobertura |
|---|---|---|---|
| 1 | Desactivar una materia individual sin afectar las demás | Backend | Endpoint por `tutorSubjectId`; solo modifica esa fila |
| 2 | Diálogo de confirmación previo | **Frontend** | Backend no interviene (ver spec frontend) |
| 3 | Cancelar el diálogo no cambia nada | **Frontend** | Si no se envía el request, no hay cambio |
| 4 | No desactivar con reservas activas pendientes + acceso a "Mis reservas" | Backend + Frontend | Backend responde `409` con código `SUBJECT_HAS_ACTIVE_BOOKINGS`; el frontend arma el link |
| 5 | Se conservan insignia Verificado y calificaciones | Backend | Se usa un flag **nuevo** `active`; no se toca `tutorSubjectStatus`, `TutorProfile.verified` ni `Rating` |
| 6 | Reactivar en cualquier momento | Backend | Endpoint `reactivate` |
| 7 | Materia desactivada invisible en buscador y perfil público | Backend | Filtros por `active = true` en búsqueda, perfil público y reserva |

---

## 2. Decisión de diseño clave (leer antes de codear)

`TutorSubjectStatus` hoy es `PENDING | ACTIVE | REJECTED` y representa el **estado de validación** (US-25), expuesto como `verificationStatus` en `TutorSubjectResponse`.

**NO agregar `INACTIVE` a ese enum.** Si se hiciera, al desactivar se perdería el estado de validación (viola AC #5 y la nota "US-25 — estado se conserva"). En su lugar se agrega un campo booleano **independiente** `active` en `TutorSubject`.

Dos ejes ortogonales:

| Eje | Campo | Valores | Dueño |
|---|---|---|---|
| Validación | `tutorSubjectStatus` | PENDING / ACTIVE / REJECTED | US-25 |
| Oferta (esta historia) | `active` | true / false | US-12 |

Visible para alumnos ⇔ `active == true` (la regla actual sobre `tutorSubjectStatus` **no cambia**: hoy el buscador ya muestra materias en cualquier estado de validación).

---

## 3. Stack y reglas (del repo, no negociables)

- Java 21, Spring Boot 3.5.3, MySQL 8 (prod) / H2 (tests), Maven wrapper (`./mvnw`).
- Código (clases, métodos, variables, logs, comentarios) en **inglés**; mensajes al usuario final en **español**.
- Inyección por constructor con `@RequiredArgsConstructor`. `@Slf4j` para logs. `orElseThrow()` siempre, nunca `.get()`.
- Estructura existente: `controllers/{interfaces,implementations,requests,responses}`, `services/{interfaces,implementations}`, `validations`, `repositories`, `data/{models,enums,mappers}`.
- Controllers sin lógica de negocio; no acceder a repositorios desde el controller.
- `@Operation` + `@ApiResponses` en cada endpoint (en la interfaz `ITutorController`).
- Commits: `feat: descripción en español`.

---

## 4. Cambios paso a paso

### Paso 1 — Entidad `TutorSubject`

Archivo: `src/main/java/com/knowlink/api/tutors/data/models/TutorSubject.java`

Agregar el campo (después de `modality`):

```java
import org.hibernate.annotations.ColumnDefault;

@Column(name = "active", nullable = false)
@ColumnDefault("true")
@Builder.Default
private boolean active = true;
```

**Importante:** el proyecto usa `ddl-auto` (sin Flyway/Liquibase). `@ColumnDefault("true")` es obligatorio: sin él, Hibernate crearía `active BIT NOT NULL` y las filas existentes quedarían en `false` (todas las materias actuales desaparecerían del buscador). El default también cubre los `INSERT` de `seed-tutor-profiles.sql`, que no especifican `active`; **no modificar los seeds**.

### Paso 2 — Repositorio `ITutorSubjectRepository`

Archivo: `.../tutors/repositories/ITutorSubjectRepository.java`

Agregar:

```java
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;
import java.util.Optional;

@Lock(LockModeType.PESSIMISTIC_WRITE)
@Query("""
        SELECT ts FROM TutorSubject ts
        WHERE ts.tutorSubjectId = :tutorSubjectId
        AND ts.tutorProfile.tutorProfileId = :tutorProfileId
        """)
Optional<TutorSubject> findOwnedByIdForUpdate(
        @Param("tutorSubjectId") UUID tutorSubjectId,
        @Param("tutorProfileId") UUID tutorProfileId);

@Lock(LockModeType.PESSIMISTIC_WRITE)
@Query("SELECT ts FROM TutorSubject ts WHERE ts.tutorSubjectId = :tutorSubjectId")
Optional<TutorSubject> findByIdForUpdate(@Param("tutorSubjectId") UUID tutorSubjectId);

@Query("""
        SELECT ts FROM TutorSubject ts
        WHERE ts.tutorProfile.tutorProfileId = :tutorProfileId
        AND ts.active = true
        """)
List<TutorSubject> findActiveByTutorProfileId(@Param("tutorProfileId") UUID tutorProfileId);
```

El método existente `findByTutorProfileId` **se mantiene sin cambios** (lo usan `MaterialServiceImpl` y la vista propia del tutor).

### Paso 3 — Repositorio `IBookingRepository`

Archivo: `.../bookings/repositories/IBookingRepository.java`

Agregar (solo lectura):

```java
boolean existsByTutorSubject_TutorSubjectIdAndBookingStatusIn(
        UUID tutorSubjectId, List<BookingStatus> statuses);
```

Estados que bloquean la desactivación: `BookingConstants.ACTIVE_STATUSES` (`PENDING, BOOKED, IN_PROGRESS`). Reutilizar esa constante; **no** crear otra lista.

### Paso 4 — Excepción de conflicto (409)

Hoy solo existe `DuplicateResourceException` (409) con semántica de duplicado. Crear una excepción propia con el mismo patrón:

Archivo nuevo: `.../exceptions/custom_exceptions/BusinessConflictException.java`

```java
package com.knowlink.api.exceptions.custom_exceptions;

public class BusinessConflictException extends RuntimeException {

    private final String errorCode;
    private final String userMessage;

    public BusinessConflictException(String errorCode, String userMessage, String technicalMessage) {
        super(technicalMessage);
        this.errorCode = errorCode;
        this.userMessage = userMessage;
    }

    public String getErrorCode() { return errorCode; }

    public String getUserMessage() { return userMessage; }
}
```

En `GlobalExceptionHandler` agregar (mismo estilo que `handleDuplicate`):

```java
@ExceptionHandler(BusinessConflictException.class)
public ResponseEntity<ApiError> handleBusinessConflict(BusinessConflictException ex) {
    logger.warn(ex.getMessage());
    ApiError error = new ApiError(HttpStatus.CONFLICT.value(), ex.getUserMessage(), ex.getErrorCode());
    return ResponseEntity.status(HttpStatus.CONFLICT).body(error);
}
```

`ApiError` es `(status, message, detail)` y `detail` lleva el código de error. **No modificar `ApiError`.**

### Paso 5 — DTO de respuesta `TutorSubjectResponse`

Archivo: `.../tutors/controllers/responses/TutorSubjectResponse.java`

Agregar `boolean active` **al final** del record:

```java
public record TutorSubjectResponse(
        UUID tutorSubjectId,
        String subjectName,
        String modality,
        String compensationType,
        BigDecimal pricePerHour,
        String verificationStatus, // "PENDING" | "ACTIVE" | "REJECTED" — estado de validación (US-25)
        Double averageRating,
        Integer reviewCount,
        boolean active             // US-12: materia ofrecida (true) o desactivada (false)
) {}
```

Actualizar el único constructor existente en `TutorProfileMapper.toSubjectResponse` (agregar `tutorSubject.isActive()` como último argumento). Verificado: ese es el único `new TutorSubjectResponse(` del código.

### Paso 6 — Validaciones (`ITutorProfileValidationService` + impl)

Agregar a la interfaz y a `TutorProfileValidationServiceImpl`. Inyectar `IBookingRepository` en la impl (agregar el campo `final`; `@RequiredArgsConstructor` lo toma).

```java
void ifSubjectHasActiveBookingsThrowException(TutorSubject tutorSubject);
```

```java
@Override
public void ifSubjectHasActiveBookingsThrowException(TutorSubject tutorSubject) {
    boolean hasActiveBookings = bookingRepository
            .existsByTutorSubject_TutorSubjectIdAndBookingStatusIn(
                    tutorSubject.getTutorSubjectId(), BookingConstants.ACTIVE_STATUSES);

    if (hasActiveBookings) {
        throw new BusinessConflictException(
                "SUBJECT_HAS_ACTIVE_BOOKINGS",
                "No podés desactivar esta materia porque tiene reservas activas. "
                        + "Cancelalas o esperá a que se completen.",
                String.format("TutorSubject '%s' has active bookings",
                        tutorSubject.getTutorSubjectId()));
    }
}
```

> Si inyectar `IBookingRepository` en esta clase genera dependencia circular (no debería), mover el chequeo al service y dejar un comentario. Verificar con `./mvnw test`.

### Paso 7 — Servicio (`ITutorProfileService` + `TutorProfileServiceImpl`)

Agregar a la interfaz:

```java
TutorSubjectResponse deactivateTutorSubject(UUID tutorUserId, UUID tutorSubjectId);
TutorSubjectResponse reactivateTutorSubject(UUID tutorUserId, UUID tutorSubjectId);
```

Implementación en `TutorProfileServiceImpl` (agregar al final, antes de `activateStudentRole` o después; mismo estilo):

```java
@Override
@Transactional
public TutorSubjectResponse deactivateTutorSubject(UUID tutorUserId, UUID tutorSubjectId) {
    TutorSubject tutorSubject = findOwnedTutorSubjectForUpdateOrThrow(tutorUserId, tutorSubjectId);

    if (!tutorSubject.isActive()) {
        return tutorProfileMapper.toSubjectResponse(tutorSubject); // idempotent
    }

    tutorProfileValidationService.ifSubjectHasActiveBookingsThrowException(tutorSubject);

    tutorSubject.setActive(false);
    TutorSubject saved = tutorSubjectRepository.save(tutorSubject);
    log.info("TutorSubject {} deactivated by tutor user {}", tutorSubjectId, tutorUserId);
    return tutorProfileMapper.toSubjectResponse(saved);
}

@Override
@Transactional
public TutorSubjectResponse reactivateTutorSubject(UUID tutorUserId, UUID tutorSubjectId) {
    TutorSubject tutorSubject = findOwnedTutorSubjectForUpdateOrThrow(tutorUserId, tutorSubjectId);

    if (tutorSubject.isActive()) {
        return tutorProfileMapper.toSubjectResponse(tutorSubject); // idempotent
    }

    tutorSubject.setActive(true);
    TutorSubject saved = tutorSubjectRepository.save(tutorSubject);
    log.info("TutorSubject {} reactivated by tutor user {}", tutorSubjectId, tutorUserId);
    return tutorProfileMapper.toSubjectResponse(saved);
}

private TutorSubject findOwnedTutorSubjectForUpdateOrThrow(UUID tutorUserId, UUID tutorSubjectId) {
    TutorProfile tutorProfile = tutorProfileValidationService.findTutorProfileOrThrowException(tutorUserId);
    return tutorSubjectRepository
            .findOwnedByIdForUpdate(tutorSubjectId, tutorProfile.getTutorProfileId())
            .orElseThrow(() -> new ResourceNotFoundException(
                    "TUTOR_SUBJECT_NOT_FOUND",
                    "La materia no existe o no te pertenece.",
                    String.format("TutorSubject '%s' not found for tutor user '%s'",
                            tutorSubjectId, tutorUserId)));
}
```

Notas:
- Agregar `@Slf4j` a la clase si no lo tiene (hoy no lo tiene) y el import `lombok.extern.slf4j.Slf4j`.
- Una materia de **otro** tutor devuelve `404` (no `403`) para no revelar su existencia.
- Deactivate/reactivate son **idempotentes** (200 con el estado actual). No lanzar error si ya está en el estado pedido.
- No tocar `tutorSubjectStatus`, `TutorProfile.verified`, `averageRating`, `Rating`, `AcademicMaterial` ni `Booking`.

### Paso 8 — Visibilidad para alumnos (AC #7) en `TutorProfileServiceImpl`

Tres puntos, todos en el mismo archivo:

**8.a — Perfil público `getTutorProfile`:** cambiar
`tutorSubjectRepository.findByTutorProfileId(...)` → `tutorSubjectRepository.findActiveByTutorProfileId(...)`.

**8.b — Búsqueda por materia `searchTutor`:** al inicio, después de `subjectSpec`, componer con la nueva spec:

```java
Specification<TutorSubject> subjectSpec = TutorSearchSpecifications.subjectNameContains(query)
        .and(TutorSearchSpecifications.subjectActive());
```

y en `TutorSearchSpecifications` agregar:

```java
public static Specification<TutorSubject> subjectActive() {
    return (root, cq, cb) -> cb.isTrue(root.get("active"));
}
```

**8.c — Búsqueda por nombre del tutor (segundo bloque de `searchTutor`, `nameMatches`):** este camino usa `tutorProfile.getSubjects()` (incluye inactivas). Reemplazar por una lista filtrada:

```java
List<TutorSubject> activeSubjects = tutorProfile.getSubjects().stream()
        .filter(TutorSubject::isActive)
        .toList();
if (activeSubjects.isEmpty()) {
    continue; // el tutor no tiene materias ofrecidas → no aparece
}
```

y usar `activeSubjects` en lugar de `tutorProfile.getSubjects()` en el resto del bloque (el `isEmpty()` inicial y `List<TutorSubject> subjects = ...`). Así, un tutor con **todas** sus materias desactivadas deja de aparecer en el buscador, y uno con algunas solo muestra las activas.

**Vista propia del tutor (`getSelfProfile` → `toSelfProfileResponse`): NO filtrar.** El tutor debe ver sus materias desactivadas (con `active: false`) para poder reactivarlas.

### Paso 9 — Bloquear nuevas reservas (defensa en profundidad + AC #1 "no puede recibir nuevas reservas")

Archivo: `.../bookings/services/implementations/BookingServiceImpl.java` (~línea 110).

Reemplazar `tutorSubjectRepository.findById(request.tutorSubjectId())` por `tutorSubjectRepository.findByIdForUpdate(request.tutorSubjectId())` (mismo `orElseThrow` existente). Inmediatamente después agregar:

```java
if (!tutorSubject.isActive()) {
    throw new BusinessConflictException(
            "TUTOR_SUBJECT_INACTIVE",
            "Esta materia ya no está disponible para reservas.",
            "TutorSubject " + tutorSubject.getTutorSubjectId() + " is inactive");
}
```

El `FOR UPDATE` serializa contra la desactivación (Paso 7): si el tutor desactiva y un alumno confirma a la vez, o la reserva se crea antes (y entonces la desactivación ve la reserva y devuelve 409), o la desactivación gana (y la reserva recibe `TUTOR_SUBJECT_INACTIVE`). Sin el lock quedaría una ventana de carrera.

Actualizar los tests existentes de `BookingServiceImpl` que hagan `when(tutorSubjectRepository.findById(...))` para que mockeen `findByIdForUpdate` y devuelvan un `TutorSubject` con `active = true` (el builder ya lo deja en `true` por defecto).

### Paso 10 — Endpoints (`ITutorController` + `TutorControllerImpl`)

En `ITutorController` (junto a `createTutorSubject`; mismo estilo de anotaciones):

```java
@PatchMapping("/subjects/{tutorSubjectId}/deactivate")
@Operation(summary = "Desactivar una materia ofrecida",
        description = "Marca la materia como inactiva: deja de aparecer en el buscador y en el perfil público y no admite nuevas reservas. "
                + "Conserva calificaciones, materiales y estado de validación. Falla con 409 si tiene reservas activas. Idempotente.")
@ApiResponses({
        @ApiResponse(responseCode = "200", description = "Materia desactivada (o ya lo estaba)"),
        @ApiResponse(responseCode = "401", description = "No autenticado"),
        @ApiResponse(responseCode = "403", description = "Acceso denegado"),
        @ApiResponse(responseCode = "404", description = "La materia no existe o no pertenece al tutor"),
        @ApiResponse(responseCode = "409", description = "La materia tiene reservas activas (SUBJECT_HAS_ACTIVE_BOOKINGS)")
})
@ResponseStatus(OK)
@PreAuthorize("hasRole('TUTOR')")
TutorSubjectResponse deactivateTutorSubject(
        @Parameter(description = "ID de la materia del tutor (tutorSubjectId)") @PathVariable UUID tutorSubjectId,
        @Parameter(hidden = true) @AuthenticationPrincipal UserPrincipal principal);

@PatchMapping("/subjects/{tutorSubjectId}/reactivate")
@Operation(summary = "Reactivar una materia desactivada",
        description = "Vuelve a ofrecer la materia: reaparece en el buscador y en el perfil público. Idempotente.")
@ApiResponses({
        @ApiResponse(responseCode = "200", description = "Materia reactivada (o ya estaba activa)"),
        @ApiResponse(responseCode = "401", description = "No autenticado"),
        @ApiResponse(responseCode = "403", description = "Acceso denegado"),
        @ApiResponse(responseCode = "404", description = "La materia no existe o no pertenece al tutor")
})
@ResponseStatus(OK)
@PreAuthorize("hasRole('TUTOR')")
TutorSubjectResponse reactivateTutorSubject(
        @Parameter(description = "ID de la materia del tutor (tutorSubjectId)") @PathVariable UUID tutorSubjectId,
        @Parameter(hidden = true) @AuthenticationPrincipal UserPrincipal principal);
```

Agregar `import org.springframework.web.bind.annotation.PatchMapping;`.

En `TutorControllerImpl` (una línea de delegación cada uno, sin lógica):

```java
@Override
public TutorSubjectResponse deactivateTutorSubject(UUID tutorSubjectId, UserPrincipal principal) {
    return this.tutorProfileService.deactivateTutorSubject(principal.getUser().getUserId(), tutorSubjectId);
}

@Override
public TutorSubjectResponse reactivateTutorSubject(UUID tutorSubjectId, UserPrincipal principal) {
    return this.tutorProfileService.reactivateTutorSubject(principal.getUser().getUserId(), tutorSubjectId);
}
```

`SecurityConfig` usa `anyRequest().authenticated()` y `@PreAuthorize`; **no hace falta** tocar la whitelist.

---

## 5. Contrato de API

Base: `/api/v1/tutors`. Auth: JWT, rol `TUTOR`. Sin body en ambos requests.

| Método | Ruta | Descripción |
|---|---|---|
| PATCH | `/subjects/{tutorSubjectId}/deactivate` | Desactiva la materia |
| PATCH | `/subjects/{tutorSubjectId}/reactivate` | Reactiva la materia |

**200 OK** (ambos):

```json
{
  "tutorSubjectId": "5b1c2c9e-…",
  "subjectName": "Base de Datos",
  "modality": "BOTH",
  "compensationType": "PAID",
  "pricePerHour": 2500.00,
  "verificationStatus": "ACTIVE",
  "averageRating": null,
  "reviewCount": null,
  "active": false
}
```

**Errores** (formato `ApiError`: `{status, message, detail}`):

| Código | `detail` | Caso |
|---|---|---|
| 401 | `TOKEN_*` / `UNAUTHORIZED` | Sin token |
| 403 | — | Rol distinto de TUTOR |
| 404 | `TUTOR_SUBJECT_NOT_FOUND` | No existe o es de otro tutor |
| 404 | `TUTOR_PROFILE_NOT_FOUND` | El usuario no tiene perfil de tutor |
| 409 | `SUBJECT_HAS_ACTIVE_BOOKINGS` | Desactivar con reservas `PENDING/BOOKED/IN_PROGRESS` |
| 409 | `TUTOR_SUBJECT_INACTIVE` | (en `POST` de reservas) materia inactiva |

Cambios en contratos existentes: `TutorSubjectResponse` suma `active` (aditivo, no rompe consumidores). `GET /tutors/me/profile` devuelve **todas** las materias con `active`; `GET /tutors/{userId}/profile` y `GET /tutors/search/{query}` devuelven **solo** las activas.

---

## 6. Escenarios Gherkin (base de los tests)

```gherkin
Feature: Desactivar y reactivar una materia ofrecida

  Background:
    Given el tutor autenticado ofrece las materias "Base de Datos" y "Programación", ambas activas

  Scenario: Desactivar una materia sin afectar las demás (AC 1)
    When el tutor desactiva "Base de Datos"
    Then la respuesta es 200 con active=false
    And "Programación" sigue con active=true

  Scenario: Desactivar con reservas activas (AC 4)
    Given "Base de Datos" tiene una reserva BOOKED
    When el tutor desactiva "Base de Datos"
    Then la respuesta es 409 con detail "SUBJECT_HAS_ACTIVE_BOOKINGS"
    And la materia sigue con active=true

  Scenario Outline: Solo las reservas en estado activo bloquean
    Given "Base de Datos" tiene una reserva en estado <estado>
    When el tutor desactiva "Base de Datos"
    Then la respuesta es <resultado>
    Examples:
      | estado      | resultado |
      | PENDING     | 409       |
      | BOOKED      | 409       |
      | IN_PROGRESS | 409       |
      | COMPLETED   | 200       |
      | CANCELLED   | 200       |
      | EXPIRED     | 200       |

  Scenario: Se conservan validación y calificaciones (AC 5)
    Given "Base de Datos" tiene tutorSubjectStatus ACTIVE y el tutor es verified=true con calificaciones
    When el tutor la desactiva y luego la reactiva
    Then tutorSubjectStatus sigue ACTIVE, verified sigue true y las calificaciones no cambian

  Scenario: Reactivar (AC 6)
    Given "Base de Datos" está desactivada
    When el tutor la reactiva
    Then la respuesta es 200 con active=true

  Scenario: Invisible para alumnos (AC 7)
    Given "Base de Datos" está desactivada
    When un alumno busca "Base de Datos"
    Then el tutor no aparece en los resultados
    When un alumno abre el perfil público del tutor
    Then la materia no aparece en su lista de materias

  Scenario: Tutor con todas las materias desactivadas
    Given ambas materias están desactivadas
    When un alumno busca por el nombre del tutor
    Then el tutor no aparece en los resultados

  Scenario: El tutor sigue viendo sus materias desactivadas
    When el tutor consulta GET /tutors/me/profile
    Then recibe ambas materias, la desactivada con active=false

  Scenario: Materia de otro tutor
    When el tutor intenta desactivar una materia que pertenece a otro tutor
    Then la respuesta es 404 TUTOR_SUBJECT_NOT_FOUND

  Scenario: Idempotencia
    Given "Base de Datos" ya está desactivada
    When el tutor la desactiva otra vez
    Then la respuesta es 200 con active=false

  Scenario: Nueva reserva sobre materia desactivada
    Given "Base de Datos" está desactivada
    When un alumno intenta confirmar una reserva sobre ella
    Then la respuesta es 409 con detail "TUTOR_SUBJECT_INACTIVE"
```

---

## 7. Tests a escribir

Seguir el estilo existente (`TutorProfileServiceImplTest` con Mockito; `TutorSearchControllerTest` para controller/integración con H2). **Los tests existentes deben seguir pasando.**

**Unitarios — `TutorProfileServiceImpl`** (archivo nuevo `TutorSubjectActivationServiceTest.java` en `src/test/java/com/knowlink/api/tutors/services/` o ampliando el test existente):
- deactivate: marca `active=false` y no modifica `tutorSubjectStatus`.
- deactivate: con reservas activas → `BusinessConflictException` con code `SUBJECT_HAS_ACTIVE_BOOKINGS` y **no** llama a `save`.
- deactivate: ya inactiva → devuelve sin `save` (idempotente).
- reactivate: marca `active=true`; ya activa → idempotente.
- materia ajena / inexistente → `ResourceNotFoundException` `TUTOR_SUBJECT_NOT_FOUND`.
- `searchTutor` / `getTutorProfile`: no incluyen materias inactivas; tutor con todas inactivas no aparece (ambos caminos: por materia y por nombre).

**Specification:** test de `subjectActive()` junto a los existentes en `src/test/java/.../tutors/data/specifications` (H2).

**Controller/integración:** 200 en ambos endpoints; 401 sin token; 403 con rol STUDENT; 404 con id ajeno; 409 con reserva `BOOKED`; el JSON incluye `active`.

**Reservas:** en `BookingServiceImpl`, caso materia inactiva → `TUTOR_SUBJECT_INACTIVE`; ajustar mocks de `findById` → `findByIdForUpdate`.

Comando: `./mvnw test` debe pasar completo.

---

## 8. Advertencias (encontradas leyendo el código real)

1. **`ddl-auto` sin migraciones.** Ver Paso 1: sin `@ColumnDefault("true")` se desactivarían todas las materias existentes en producción. Verificar tras el primer deploy que las filas previas tengan `active = 1`.
2. **"Reservas activas pendientes" (AC 4).** Se interpreta como `BookingConstants.ACTIVE_STATUSES` = `PENDING, BOOKED, IN_PROGRESS` (incluye `PENDING`, que es una reserva en proceso de pago). Si el equipo quiere bloquear solo `BOOKED`/`IN_PROGRESS`, cambiar la constante usada en el Paso 6 — es el único lugar. **Confirmar con el equipo.**
3. **Holds.** `Hold` no referencia a `TutorSubject` (es del `TimeSlot`), por lo que un hold abierto no bloquea la desactivación; el alumno que intente confirmar luego recibirá `TUTOR_SUBJECT_INACTIVE` (Paso 9). Comportamiento aceptado.
4. **`MaterialServiceImpl.findOrCreateTutorSubject`** crea `TutorSubject` con `ACTIVE` vía builder; con `@Builder.Default active = true` queda activa. No requiere cambios. Subir material a una materia desactivada se permite (no se bloquea en esta historia).
5. **`TutorSubjectResponse.averageRating/reviewCount`** hoy son `null` (calificaciones por materia pendientes). AC 5 se cumple porque las calificaciones viven en `Rating`/`TutorProfile.averageRating`, que esta historia no toca.
6. **Estado de validación PENDING/REJECTED.** Una materia puede desactivarse/reactivarse en cualquier estado de validación; reactivar no cambia ese estado.
7. **US-25** (solicitud de validación) no está implementada en el repo. Esta spec no depende de ella: solo garantiza no tocar `tutorSubjectStatus`.

---

## 9. No hacer

- No agregar valores a `TutorSubjectStatus` (ni `INACTIVE` ni `DEACTIVATED`).
- No borrar (ni soft-delete con otra columna) `TutorSubject`, `AcademicMaterial` ni `Rating`.
- No modificar `TutorProfile.verified`, `averageRating`, ni las entidades `Booking`/`Hold`.
- No cancelar reservas automáticamente al desactivar (el requisito es *impedir* la desactivación).
- No filtrar `active` en `findByTutorProfileId` ni en la vista propia (`/me/profile`).
- No cambiar la regla de visibilidad por `tutorSubjectStatus`.
- No modificar `ApiError`, `SecurityConfig`, `SecurityConstants` ni los scripts `seed-*.sql`.
- No crear nuevas tablas ni entidades JPA.
- No poner lógica de negocio en el controller ni acceder a repositorios desde él.
- No usar `Optional.get()`, `System.out.println` ni `@Autowired` en campos.
- No agregar un body/request DTO a los endpoints (el actor sale del JWT).

---

## 10. Orden de implementación sugerido (checkpoints)

1. Pasos 1, 5 → compilar (`./mvnw -q compile`).
2. Pasos 2, 3, 4, 6, 7 → compilar y escribir tests unitarios del servicio → `./mvnw test`.
3. Paso 8 → tests de búsqueda y perfil público → `./mvnw test`.
4. Paso 9 → ajustar tests de booking → `./mvnw test`.
5. Paso 10 → tests de controller → `./mvnw test`.
6. Commits en español, ej.: `feat: agrega desactivar y reactivar materia ofrecida (US-12)`.

## 11. Definition of Done

- [ ] `TutorSubject.active` con `@ColumnDefault("true")`; filas existentes quedan activas
- [ ] `PATCH /api/v1/tutors/subjects/{id}/deactivate` y `/reactivate` con `@Operation`/`@ApiResponses`
- [ ] 409 `SUBJECT_HAS_ACTIVE_BOOKINGS` usando `BookingConstants.ACTIVE_STATUSES`
- [ ] `tutorSubjectStatus`, `verified` y `Rating` intactos tras desactivar/reactivar
- [ ] Búsqueda (ambos caminos) y perfil público excluyen inactivas; `/me/profile` las incluye con `active`
- [ ] Creación de reserva rechaza materias inactivas (`TUTOR_SUBJECT_INACTIVE`) usando lock pesimista
- [ ] `TutorSubjectResponse.active` documentado y mapeado
- [ ] Tests unitarios, de specification, de controller y de booking pasando (`./mvnw test`)
- [ ] Sin cambios fuera del alcance listado en la sección 9
- [ ] Code review aprobado
