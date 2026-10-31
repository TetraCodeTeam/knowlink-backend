# US-53 — Editar materia ofrecida (Backend)

> Spec generado a partir de la exploración real de `TetraCodeTeam/knowlink-backend`, rama `dev` (commit `92498d1`). Pensado para ejecutarse con **OpenCode + DeepSeek V4 Flash**: pasos literales, rutas exactas y verificación por paso. No improvises fuera de lo que dice este documento.

## Antes de empezar (obligatorio)

1. Leer `AGENTS.md` y `docs/base-standards.md` del repo (estructura de paquetes, inyección por constructor, DTOs como `record`, mensajes de validación en inglés, Swagger en español).
2. Crear la rama desde `dev`: `git checkout dev && git pull && git checkout -b feat/edit-tutor-subject`.
3. Correr la línea base **antes de tocar nada**: `./mvnw test -q`. Si ya falla, anotarlo y NO arreglarlo en esta historia.
4. Trabajar en este orden exacto: **data/DTO → repository → validation → service → controller → tests**.

## Metadata

| Campo | Valor |
|---|---|
| ID Historia | US-53 |
| Épica | — |
| Story Points | 3 |
| Prioridad | Baja |
| Repo / rama | `knowlink-backend`, rama `feat/edit-tutor-subject` (base = `dev`) |
| Relacionadas | US-48 (registrar materia), US-25 (validación — el estado se conserva), US-38 (perfil tutor) |
| Spec hermano | `us-53-editar-materia-frontend.spec.md` (repo `knowlink-frontend`) |

## Historia de usuario

Como tutor, quiero poder modificar el precio y la modalidad de una materia que ya registré, para ajustar mi oferta cuando cambian mis condiciones de disponibilidad o mis tarifas.

## Alcance backend

Los criterios 1 y 6 (ícono de editar y resaltado de campos) son de UI y viven en el spec del frontend. El backend cubre:

| CA | Qué implementa el backend |
|---|---|
| 2 | Cambiar `compensationType` (FREE/PAID) y, si es PAID, `pricePerHour` > 0 |
| 3 | Cambiar `modality` (VIRTUAL / IN_PERSON / BOTH) |
| 4 | El nombre de la materia **no se puede modificar**: el request ni siquiera lo recibe |
| 5 | Cambios visibles de inmediato en perfil propio, perfil público y búsqueda |
| 6 | Validación servidor (400) como red de seguridad del bloqueo de UI |
| 7 | Las reservas ya confirmadas no cambian |

## Criterios de aceptación (Gherkin)

```gherkin
Feature: Editar materia ofrecida por el tutor

  Background:
    Given el tutor está autenticado con rol TUTOR
    And tiene una materia registrada (TutorSubject) con modalidad VIRTUAL y precio 5000

  Scenario: Modificar precio y modalidad
    When envía PUT /api/v1/tutors/subjects/{tutorSubjectId} con compensationType=PAID, pricePerHour=8000, modality=BOTH
    Then responde 200 con la materia actualizada (mismo tutorSubjectId y mismo subjectName)
    And GET /api/v1/tutors/me/profile devuelve el nuevo precio y la nueva modalidad

  Scenario: Pasar de pago a gratuito
    When envía compensationType=FREE con cualquier pricePerHour
    Then responde 200
    And la materia queda con compensationType=FREE y pricePerHour=null

  Scenario: Precio inválido en materia paga
    When envía compensationType=PAID con pricePerHour=0, negativo o null
    Then responde 400 con code VALIDATION_ERROR o detalle del campo
    And la materia NO se modifica

  Scenario: Modalidad ausente
    When envía el request sin modality
    Then responde 400 y la materia NO se modifica

  Scenario: El nombre de la materia no es editable
    When el request incluye un campo subjectName
    Then el campo se ignora y el subjectName de la respuesta sigue siendo el original

  Scenario: Materia de otro tutor
    Given existe una materia que pertenece a otro tutor
    When el tutor autenticado intenta editarla
    Then responde 404 con code TUTOR_SUBJECT_NOT_FOUND
    And la materia ajena NO se modifica

  Scenario: Materia inexistente
    When el tutorSubjectId no existe
    Then responde 404 con code TUTOR_SUBJECT_NOT_FOUND

  Scenario: Rol incorrecto
    Given el usuario autenticado tiene rol STUDENT
    When intenta editar una materia
    Then responde 403

  Scenario: Se conserva el estado de validación (US-25)
    Given la materia tiene tutorSubjectStatus=ACTIVE (o PENDING, o REJECTED)
    When el tutor la edita
    Then tutorSubjectStatus queda exactamente igual que antes

  Scenario: Reservas ya confirmadas no se ven afectadas
    Given existe una Booking BOOKED con amount=1200.00 y modality=IN_PERSON sobre esa materia
    When el tutor cambia la materia a VIRTUAL y precio 9000
    Then la Booking conserva amount=1200.00 y modality=IN_PERSON
```

## Hechos verificados en el código (no re-investigar)

- **Entidad** `com.knowlink.api.tutors.data.models.TutorSubject`: campos `tutorSubjectId (UUID)`, `tutorProfile`, `subject`, `description`, `pricePerHour (BigDecimal, precision 10, scale 2)`, `compensationType`, `tutorSubjectStatus`, `modality`. Tiene `@Setter`.
- **Enums** (`com.knowlink.api.tutors.data.enums`): `Modality {VIRTUAL, IN_PERSON, BOTH}`, `CompensationType {FREE, PAID}`, `TutorSubjectStatus {PENDING, ACTIVE, REJECTED}`.
- **Hoy NO existe** endpoint de edición. Solo existe `POST /api/v1/tutors/subjects` (`ITutorController.createTutorSubject`, rol TUTOR).
- **CA 7 ya está garantizado por el modelo**: `Booking` guarda su propio `amount` (precio calculado en la reserva, incluye fee) y su propio `modality` al crearse (`BookingServiceImpl`, líneas ~128-170). Editar `TutorSubject` no toca esas columnas. **No hay que modificar nada en `bookings`.** Solo hay que demostrarlo con un test.
- **CA 5**: `@EnableCaching` está en `KnowLinkApplication` pero **no hay ningún `@Cacheable`/`@CacheEvict`** en el proyecto. Perfil y búsqueda leen directo de la base. Con persistir en una transacción alcanza. **No agregar cache ni eviction.**
- **`ITutorProfileValidationService.findTutorProfileOrThrowException(UUID tutorUserId)`** resuelve el `TutorProfile` desde el `userId` autenticado (lanza `ResourceNotFoundException`).
- **`ITutorProfileValidationService.ifPaidSubjectHasInvalidPriceThrowException(TutorSubjectRequest)`** existe pero recibe el request de **alta** (paquete `auth`, incluye `subjectName`). **No se reutiliza ni se modifica** (lo usa el alta y el registro de tutor). Se agrega un método nuevo.
- **Respuesta**: `TutorSubjectResponse` y `TutorProfileMapper.toSubjectResponse(TutorSubject)` ya existen. Se reutilizan sin cambios.
- **Errores**: `ResourceNotFoundException(errorCode, userMessage, technicalMessage)` → 404; `ValidationException(message)` → 400 con code `VALIDATION_ERROR`; `MethodArgumentNotValidException` → 400 con `message="Invalid parameters"` y `detail="campo: mensaje"`. Formato `{ status, message, detail }` (`ApiError`).
- **Seguridad**: `anyRequest().authenticated()` en `SecurityConfig` + `@PreAuthorize("hasRole('TUTOR')")` por método. `/api/v1/tutors/**` ya está cubierto, **no tocar `SecurityConfig`**.
- `ITutorProfileService` hoy tiene `createTutorSubject(UUID tutorUserId, TutorSubjectRequest request)`; la implementación es `TutorProfileServiceImpl` (`@Service @RequiredArgsConstructor`, ya inyecta `tutorSubjectRepository`, `tutorProfileValidationService`, `tutorProfileMapper`).
- `TutorProfileServiceImplTest` está **comentado entero** (`/* ... */`). No lo uses ni lo reactives.

## Diseño del endpoint

```
PUT /api/v1/tutors/subjects/{tutorSubjectId}
```

- Misma base que el alta (`POST /api/v1/tutors/subjects`) → consistente con `ITutorController`.
- `PUT` porque se reemplazan los 3 campos editables; los 3 son obligatorios en el body (el front siempre los manda).
- Protegido con `@PreAuthorize("hasRole('TUTOR')")`.
- Request:

```json
{ "modality": "BOTH", "compensationType": "PAID", "pricePerHour": 8000.00 }
```

- Response `200 OK`: `TutorSubjectResponse` (ya existente):

```json
{
  "tutorSubjectId": "…", "subjectName": "Álgebra", "modality": "BOTH",
  "compensationType": "PAID", "pricePerHour": 8000.00,
  "verificationStatus": "ACTIVE", "averageRating": null, "reviewCount": null
}
```

- Códigos: 200, 400 (datos inválidos), 403 (rol), 404 (materia inexistente **o ajena**).
- **Materia ajena → 404, no 403** (no revelar que el id existe). Reutilizar el code `TUTOR_SUBJECT_NOT_FOUND` (ya usado en `BookingServiceImpl`).

## Archivos a crear / modificar

Todos bajo `src/main/java/com/knowlink/api/tutors/` salvo los tests.

| # | Acción | Archivo |
|---|---|---|
| 1 | CREAR | `controllers/requests/UpdateTutorSubjectRequest.java` |
| 2 | MODIFICAR | `repositories/ITutorSubjectRepository.java` |
| 3 | MODIFICAR | `validations/ITutorProfileValidationService.java` |
| 4 | MODIFICAR | `validations/TutorProfileValidationServiceImpl.java` |
| 5 | MODIFICAR | `services/interfaces/ITutorProfileService.java` |
| 6 | MODIFICAR | `services/implementations/TutorProfileServiceImpl.java` |
| 7 | MODIFICAR | `controllers/interfaces/ITutorController.java` |
| 8 | MODIFICAR | `controllers/implementations/TutorControllerImpl.java` |
| 9 | CREAR | `src/test/java/com/knowlink/api/tutors/controllers/TutorSubjectUpdateControllerTest.java` |

### 1. `UpdateTutorSubjectRequest` (CREAR)

Paquete `com.knowlink.api.tutors.controllers.requests` (junto a `UpdateMinNoticeMinutesRequest`, NO en `auth`). Record con mensajes **en inglés**:

```java
package com.knowlink.api.tutors.controllers.requests;

import com.knowlink.api.tutors.data.enums.CompensationType;
import com.knowlink.api.tutors.data.enums.Modality;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public record UpdateTutorSubjectRequest(

        @NotNull(message = "Modality is required")
        Modality modality,

        @NotNull(message = "Compensation type is required")
        CompensationType compensationType,

        @Digits(integer = 8, fraction = 2, message = "Price must have at most 8 integer digits and 2 decimals")
        BigDecimal pricePerHour
) {}
```

- **No incluir `subjectName`** (CA 4). El `ObjectMapper` del proyecto tiene `FAIL_ON_UNKNOWN_PROPERTIES=false` (`config/ApplicationConfig.java`), así que un body con `subjectName` no rompe y el campo se descarta.
- La regla "PAID exige precio > 0" NO va con anotación (depende de otro campo): va en el validador (paso 4).
- `@Digits` evita un error 500 por overflow de `DECIMAL(10,2)`.

### 2. `ITutorSubjectRepository` (MODIFICAR)

Agregar **un** método derivado (import `java.util.Optional`):

```java
Optional<TutorSubject> findByTutorSubjectIdAndTutorProfile_TutorProfileId(UUID tutorSubjectId, UUID tutorProfileId);
```

No tocar los métodos existentes.

### 3-4. Validación (MODIFICAR interfaz + impl)

En `ITutorProfileValidationService` agregar (sin tocar los existentes; añadir imports de `TutorSubject`, `CompensationType`, `BigDecimal`):

```java
TutorSubject findTutorSubjectOfTutorOrThrowException(TutorProfile tutorProfile, UUID tutorSubjectId);
void ifPaidPriceIsInvalidThrowException(CompensationType compensationType, BigDecimal pricePerHour);
```

En `TutorProfileValidationServiceImpl`:
- Inyectar `ITutorSubjectRepository tutorSubjectRepository` (campo `private final`; la clase ya usa `@RequiredArgsConstructor`).
- `findTutorSubjectOfTutorOrThrowException`: `tutorSubjectRepository.findByTutorSubjectIdAndTutorProfile_TutorProfileId(tutorSubjectId, tutorProfile.getTutorProfileId()).orElseThrow(() -> new ResourceNotFoundException("TUTOR_SUBJECT_NOT_FOUND", "La materia seleccionada no existe.", String.format("TutorSubject '%s' not found for TutorProfile '%s'", tutorSubjectId, tutorProfile.getTutorProfileId())))`.
- `ifPaidPriceIsInvalidThrowException`: si `compensationType == PAID` y (`pricePerHour == null` o `pricePerHour.signum() <= 0`) → `throw new ValidationException("El precio por hora debe ser mayor a cero.")`.

### 5-6. Servicio (MODIFICAR interfaz + impl)

`ITutorProfileService`:

```java
TutorSubjectResponse updateTutorSubject(UUID tutorUserId, UUID tutorSubjectId, UpdateTutorSubjectRequest request);
```

`TutorProfileServiceImpl` (junto a `createTutorSubject`, mismo estilo):

```java
@Override
@Transactional
public TutorSubjectResponse updateTutorSubject(UUID tutorUserId, UUID tutorSubjectId,
                UpdateTutorSubjectRequest request) {
        TutorProfile tutorProfile = tutorProfileValidationService.findTutorProfileOrThrowException(tutorUserId);

        TutorSubject tutorSubject = tutorProfileValidationService
                        .findTutorSubjectOfTutorOrThrowException(tutorProfile, tutorSubjectId);

        tutorProfileValidationService.ifPaidPriceIsInvalidThrowException(
                        request.compensationType(), request.pricePerHour());

        tutorSubject.setModality(request.modality());
        tutorSubject.setCompensationType(request.compensationType());
        tutorSubject.setPricePerHour(
                        request.compensationType() == CompensationType.FREE ? null : request.pricePerHour());

        TutorSubject saved = tutorSubjectRepository.save(tutorSubject);
        return tutorProfileMapper.toSubjectResponse(saved);
}
```

Reglas del servicio:
- Cambiar **solo** `modality`, `compensationType` y `pricePerHour`.
- **No tocar** `tutorSubjectStatus` (US-25), `subject`, `description`, `academicMaterials`, `tutorProfile`.
- FREE ⇒ `pricePerHour = null` (evita arrastrar un precio viejo; el front también manda `null`).
- Añadir imports faltantes (`CompensationType`, `UpdateTutorSubjectRequest`).
- No inyectar `IBookingRepository` ni nada de `bookings`.

### 7-8. Controlador (MODIFICAR interfaz + impl)

En `ITutorController`, junto a `createTutorSubject` (imports de `PutMapping`, `PathVariable`, `Valid`, `Parameter` ya existen; agregar el de `UpdateTutorSubjectRequest`):

```java
@PutMapping("/subjects/{tutorSubjectId}")
@Operation(summary = "Editar una materia dictada por el tutor",
        description = "Modifica modalidad, tipo de compensación y precio de una materia ya registrada del tutor autenticado. "
                + "El nombre de la materia y el estado de validación no se modifican. "
                + "Las reservas ya confirmadas conservan las condiciones con las que se reservaron.")
@ApiResponses({
                @ApiResponse(responseCode = "200", description = "Materia actualizada"),
                @ApiResponse(responseCode = "400", description = "Datos inválidos (modalidad ausente o precio inválido en materia paga)"),
                @ApiResponse(responseCode = "403", description = "Acceso denegado"),
                @ApiResponse(responseCode = "404", description = "La materia no existe o no pertenece al tutor")
})
@ResponseStatus(OK)
@PreAuthorize("hasRole('TUTOR')")
TutorSubjectResponse updateTutorSubject(
        @Parameter(description = "Id de la materia del tutor (tutorSubjectId)") @PathVariable UUID tutorSubjectId,
        @Valid @RequestBody UpdateTutorSubjectRequest request,
        @Parameter(hidden = true) @AuthenticationPrincipal UserPrincipal principal);
```

En `TutorControllerImpl` (sin lógica de negocio, solo delega):

```java
@Override
public TutorSubjectResponse updateTutorSubject(UUID tutorSubjectId, UpdateTutorSubjectRequest request,
                UserPrincipal principal) {
    return this.tutorProfileService.updateTutorSubject(principal.getUser().getUserId(), tutorSubjectId, request);
}
```

## Tests (CREAR)

Archivo: `src/test/java/com/knowlink/api/tutors/controllers/TutorSubjectUpdateControllerTest.java`.

**Copiar el patrón exacto de `TutorWeeklyScheduleControllerTest`** (mismo paquete): `@SpringBootTest @AutoConfigureMockMvc @ActiveProfiles("test") @Transactional`, repos `@Autowired`, helpers `asTutor()` / `asUser(Role)` con `authentication(new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities()))`, seed con `careerRepository`/`subjectRepository`/`userRepository`/`tutorProfileRepository`/`tutorSubjectRepository`. Usar **emails únicos** en el seed (p. ej. `ana.edit.subject@test.com`) para no chocar con otros tests. `@DisplayName` con prefijo `CP-053.N`.

Casos mínimos (uno por escenario Gherkin):

| ID | Verifica |
|---|---|
| CP-053.1 | 200: cambia a PAID 8000 + BOTH; la respuesta trae los nuevos valores, mismo `tutorSubjectId` y mismo `subjectName` |
| CP-053.2 | 200: pasa a FREE → respuesta con `compensationType=FREE` y `pricePerHour` nulo (`jsonPath("$.pricePerHour").isEmpty()`) |
| CP-053.3 | 400: PAID con `pricePerHour` 0, negativo y null (tres requests) y la entidad queda sin cambios (releer con `tutorSubjectRepository`) |
| CP-053.4 | 400: body sin `modality` |
| CP-053.5 | 200: body con `subjectName` extra → el `subjectName` no cambia |
| CP-053.6 | 404 `TUTOR_SUBJECT_NOT_FOUND`: materia de **otro** tutor, y la ajena queda intacta |
| CP-053.7 | 404: UUID inexistente |
| CP-053.8 | 403: usuario con rol STUDENT |
| CP-053.9 | `tutorSubjectStatus` se conserva (probar con `PENDING` y con `ACTIVE`) |
| CP-053.10 | `Booking` preexistente conserva `amount` y `modality` tras la edición (CA 7). Para armarla necesitás `TimeSlot`, estudiante y los repos de `bookings`/`timeslot`; mirar cómo otros tests crean una `Booking` (`grep -rln "Booking.builder" src/test`) y reutilizar ese seed. Si armar el seed es desproporcionado, hacer el test a nivel repositorio (guardar Booking → editar TutorSubject → releer Booking) |
| CP-053.11 | CA 5: tras el PUT, `GET /api/v1/tutors/me/profile` (`asTutor()`) devuelve la materia con los valores nuevos |

Para el body usar `ObjectMapper` o un `String` JSON con `.contentType(MediaType.APPLICATION_JSON)`.

## No hacer

- No modificar `Booking`, `BookingServiceImpl`, `BookingMapper` ni nada del paquete `bookings` (CA 7 ya se cumple por snapshot).
- No agregar `@Cacheable`/`@CacheEvict` (no hay cache que invalidar).
- No modificar `TutorSubjectRequest` ni `ifPaidSubjectHasInvalidPriceThrowException` (los usa el alta y `AuthServiceImpl`).
- No permitir editar `subject`, `description`, `tutorSubjectStatus`, `academicMaterials`.
- No crear entidades ni tablas nuevas. No hay migraciones (el esquema lo gestiona Hibernate; no se agregan columnas).
- No tocar `SecurityConfig` ni `GlobalExceptionHandler`.
- No reactivar ni editar `TutorProfileServiceImplTest` (está comentado).
- No hardcodear mensajes en inglés para el usuario: los mensajes que viajan al front van en español (`ResourceNotFoundException.userMessage`, `ValidationException`); las anotaciones Jakarta van en inglés.
- No usar `Optional.get()`, `@Autowired` en campos ni `System.out.println`.

## Verificación (correr y pegar el resultado)

```bash
./mvnw test -Dtest=TutorSubjectUpdateControllerTest     # tests nuevos en verde
./mvnw verify -B                                        # lo que corre el CI; debe quedar igual o mejor que la línea base
```

Chequeo manual en Swagger (`/swagger-ui.html`): el endpoint `PUT /api/v1/tutors/subjects/{tutorSubjectId}` aparece bajo el tag "Tutors" con descripción en español.

## Commits (convención del repo: prefijo en inglés + descripción en español)

```
feat: agrega endpoint para editar materia ofrecida del tutor
test: agrega tests de edición de materia del tutor
```

## Definition of Done

- [ ] `PUT /api/v1/tutors/subjects/{tutorSubjectId}` implementado, con `@Operation`/`@ApiResponses` en español
- [ ] Solo modifica `modality`, `compensationType`, `pricePerHour`; FREE guarda precio `null`
- [ ] PAID con precio nulo/≤ 0 → 400; modalidad ausente → 400
- [ ] Materia ajena o inexistente → 404 `TUTOR_SUBJECT_NOT_FOUND`; rol STUDENT → 403
- [ ] `tutorSubjectStatus` y `subjectName` intactos
- [ ] Booking previa conserva `amount` y `modality` (test)
- [ ] Sin cambios en `bookings`, `SecurityConfig`, `GlobalExceptionHandler`
- [ ] Tests CP-053.1 a CP-053.11 en verde y `./mvnw verify -B` sin regresiones
- [ ] Code review aprobado
