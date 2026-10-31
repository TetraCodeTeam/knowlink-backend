# US-03 — Edición de datos personales (Backend)

> Spec generado a partir de la exploración real de `TetraCodeTeam/knowlink-backend`, rama `dev` (commit `ca46b79`). Pensado para ejecutarse con **OpenCode + DeepSeek V4 Flash**: pasos literales, rutas exactas y verificación por paso. No improvises fuera de lo que dice este documento.

## Antes de empezar (obligatorio)

1. Leer `AGENTS.md` y `docs/base-standards.md` del repo (estructura de paquetes, inyección por constructor, DTOs como `record`, mensajes de validación Jakarta en inglés, mensajes al usuario y Swagger en español).
2. Crear la rama desde `dev`: `git checkout dev && git pull && git checkout -b feat/edit-personal-data`.
3. Correr la línea base **antes de tocar nada**: `./mvnw test -q`. Si ya falla, anotarlo y NO arreglarlo en esta historia.
4. Trabajar en este orden exacto: **modelo/migración → utils → repository → validation → service (users) → service/DTO/mapper/controller (students) → service/DTO/mapper/controller (tutors) → tests**.

## Metadata

| Campo | Valor |
|---|---|
| ID Historia | US-03 |
| Épica | — |
| Story Points | 5 |
| Prioridad | Baja |
| Módulo | usuarios |
| Tags | #RN-01 |
| Repo / rama | `knowlink-backend`, rama `feat/edit-personal-data` (base = `dev`) |
| Relacionadas | US-37 (perfil alumno), US-38 (perfil tutor), US-39 (perfil administrador) |
| Spec hermano | `us-03-edicion-datos-personales-frontend.spec.md` (repo `knowlink-frontend`) |

## Historia de usuario

Como usuario registrado, quiero poder modificar mis datos personales, para mantener mi perfil actualizado.

Descripción adicional: edición de perfil para todos los roles.

## Alcance backend

Los criterios 3 y 4 (leyenda junto al correo y resaltado de campos / botón guardar deshabilitado) son de UI y viven en el spec del frontend. El backend cubre:

| CA | Qué implementa el backend |
|---|---|
| 1 | Permite modificar **todos** los datos cargados al registrarse (nombre, apellido, DNI, celular, carrera, legajo; y en tutor también biografía y dirección) **excepto el correo**: el request ni siquiera lo recibe |
| 2 | La respuesta del `PUT` devuelve el perfil ya actualizado y `GET /me/profile` devuelve los datos nuevos al instante |
| 3 | (UI) El backend nunca modifica el email aunque venga en el body |
| 4 | Validación servidor (400) como red de seguridad de los campos obligatorios |

### Decisiones de diseño (tomadas en este spec; ver "Puntos a confirmar con el PO")

- **Un endpoint por rol**, con el mismo estilo de las rutas existentes `/me/profile`:
  - `PUT /api/v1/students/me/profile` (rol STUDENT) → devuelve `StudentSelfProfileResponse`.
  - `PUT /api/v1/tutors/me/profile` (rol TUTOR) → devuelve `TutorSelfProfileResponse`.
- **ADMIN queda fuera de esta historia**: el admin se crea con un seeder (solo nombre, email, contraseña; sin DNI/celular/carrera) y no existe pantalla de perfil de admin. Se cubre en US-39.
- **No se edita**: email, contraseña (ya existe el flujo de reset), foto de perfil (ya existen `POST /students/me/profile-picture` y `POST /tutors/me/profile-picture`) ni las materias del tutor (US-48 / US-53).
- **Nombre y apellido por separado**: hoy `User` solo guarda `full_name` (= nombre + " " + apellido al registrar). Para poder precargar el formulario se agregan las columnas `first_name` y `last_name` (nullable) y `full_name` se mantiene siempre sincronizado (lo usan la búsqueda, reservas, notificaciones, etc.).
- **Se elimina el endpoint legacy** `PUT /api/v1/users/{userId}` (ver "Hechos verificados").

## Criterios de aceptación (Gherkin)

```gherkin
Feature: Edición de datos personales

  Scenario: Alumno modifica sus datos
    Given un usuario autenticado con rol STUDENT y perfil de alumno
    When envía PUT /api/v1/students/me/profile con firstName, lastName, dni, phoneNumber, career e institutionalId válidos
    Then responde 200 con StudentSelfProfileResponse con los valores nuevos
    And GET /api/v1/students/me/profile devuelve los mismos valores nuevos
    And fullName queda igual a "<firstName> <lastName>"

  Scenario: Tutor modifica sus datos
    Given un usuario autenticado con rol TUTOR y perfil de tutor
    When envía PUT /api/v1/tutors/me/profile con los datos comunes más biography y address
    Then responde 200 con TutorSelfProfileResponse con los valores nuevos
    And GET /api/v1/tutors/me/profile devuelve los valores nuevos

  Scenario: El correo no se puede modificar
    When el request incluye un campo "email" con otro valor
    Then responde 200 y el email del usuario NO cambia (el campo se descarta)

  Scenario: Campo obligatorio faltante
    When firstName, lastName, dni, phoneNumber o career viene ausente o en blanco
    Then responde 400 y NO se modifica ningún dato

  Scenario: DNI con formato inválido
    When dni no cumple ^\d{7,8}$
    Then responde 400 y NO se modifica ningún dato

  Scenario: DNI de otro usuario
    Given existe otro usuario (no eliminado) con ese DNI
    When el usuario autenticado intenta guardarlo
    Then responde 409 con detail DUPLICATE_DNI y NO se modifica ningún dato

  Scenario: Conservar el propio DNI
    When el usuario envía su mismo DNI actual
    Then responde 200 (no es un duplicado)

  Scenario: Carrera inexistente
    When career no corresponde a ninguna carrera del catálogo
    Then responde 404 con detail CAREER_NOT_FOUND

  Scenario: Tutor con materias no puede cambiar de carrera
    Given el tutor tiene al menos una materia registrada
    When envía una career distinta a la actual
    Then responde 400 y NO se modifica ningún dato

  Scenario: Dirección obligatoria con clases presenciales
    Given el tutor tiene una materia con modalidad IN_PERSON o BOTH
    When envía address vacía o en blanco
    Then responde 400 y NO se modifica ningún dato

  Scenario: Biografía demasiado larga
    When biography supera 300 caracteres
    Then responde 400

  Scenario: Rol incorrecto
    When un TUTOR llama a PUT /api/v1/students/me/profile (o un STUDENT a /tutors/me/profile)
    Then responde 403

  Scenario: Sin autenticación
    When se llama sin token
    Then responde 401

  Scenario: Usuario registrado antes de esta historia (sin first_name/last_name)
    Given un usuario con full_name = "Juan Perez" y first_name/last_name nulos
    When consulta GET /me/profile
    Then firstName = "Juan" y lastName = "Perez"
```

## Hechos verificados en el código (no re-investigar)

- **`User`** (`com.knowlink.api.users.data.models.User`, tabla `users`): hoy tiene `fullName` (`full_name`), `email` (unique), `password`, `dni` (unique), `phoneNumber` (`phone_number`), `profilePictureUrl`, `role`, `accountStatus`. **No** tiene `firstName`/`lastName`. Tiene `@Builder`, `@Getter`, `@Setter`.
- **Registro**: `UserMapper.toStudentUser` y `toTutorUser` arman `fullName = firstName + " " + lastName`. `AdminSeeder` crea admins solo con `fullName`.
- **Perfiles**: `StudentProfile` (`career`, `institutionalId`, `profilePictureUrl`) y `TutorProfile` (`career`, `institutionalId`, `biography` TEXT, `address`, `subjects`) son 1:1 con `User`. Un usuario puede tener **ambos** perfiles (rol dual); cada perfil tiene su propia `career`/`institutionalId`.
- **Respuestas actuales**: `StudentSelfProfileResponse(userId, fullName, email, phoneNumber, career, profilePictureUrl, role, hasTutorProfile)` y `TutorSelfProfileResponse(userId, fullName, email, phoneNumber, career, profilePictureUrl, biography, address, mercadoPagoLinked, averageRating, hasStudentProfile, subjects)`. Solo se construyen en `StudentProfileMapper.toSelfProfileResponse` y `TutorProfileMapper.toSelfProfileResponse` (no hay otros `new` de estos records, ni en tests).
- **Endpoint legacy peligroso**: `PUT /api/v1/users/{userId}` (`IUserController.updateUser`, `UserServiceImpl.updateUser`, `UpdateUserRequest(firstName, lastName, profilePicture)`) **no tiene `@PreAuthorize` ni chequea que `{userId}` sea el usuario autenticado** → cualquier usuario autenticado puede renombrar a otro. Además no valida DNI/celular ni toca los perfiles. `UpdateUserRequest` y `updateUser` no se usan en ningún otro lugar del código ni de los tests (re-verificar con `grep -rn "UpdateUserRequest\|updateUser(" src`; `updateUserRole` es otro método y NO se toca). El frontend tampoco lo consume (solo existe la función sin uso en `users.api.ts`).
- **Resolución de perfiles**: `IStudentProfileValidationService.getStudentProfileOrThrow(UUID userId)` y `ITutorProfileValidationService.findTutorProfileOrThrowException(UUID tutorUserId)` ya resuelven el perfil desde el `userId` autenticado (lanzan `ResourceNotFoundException`).
- **Carrera**: `ICareerService.findByNameOrThrowException(String name)` lanza `ResourceNotFoundException("CAREER_NOT_FOUND", "Carrera no encontrada.", …)` → 404.
- **DNI**: `IUserRepository.existsByDni(String)` cuenta también al propio usuario → no sirve para editar; hay que agregar una variante que excluya al usuario. `UserValidationService.ifDniAlreadyExistsThrowException` lanza `DuplicateResourceException("DUPLICATE_DNI", "Este DNI ya está registrado.", …)` → 409 con `detail=DUPLICATE_DNI`. La columna `dni` es `unique`; ante una carrera de condiciones `GlobalExceptionHandler` ya responde 409 (`DataIntegrityViolationException`).
- **Errores**: `ValidationException(message)` → 400 `VALIDATION_ERROR`; `MethodArgumentNotValidException` → 400 `message="Invalid parameters"`, `detail="campo: mensaje"`. Formato `ApiError {status, message, detail}`.
- **JSON**: `ApplicationConfig` configura `FAIL_ON_UNKNOWN_PROPERTIES=false` → un campo `email` en el body no rompe y se descarta (basta con no declararlo en el record).
- **JWT**: los claims son `sub=email`, `role`, `userId`. Como el email no cambia, **el token sigue siendo válido tras editar**; no hay que reemitirlo.
- **Seguridad**: `anyRequest().authenticated()` + `@PreAuthorize` por método. `/api/v1/students/**` y `/api/v1/tutors/**` ya están cubiertos; **no tocar `SecurityConfig`** ni `SecurityConstants`. La identidad se toma SIEMPRE de `@AuthenticationPrincipal UserPrincipal`, nunca de un id en la ruta.
- **Cache**: no hay ningún `@Cacheable`/`@CacheEvict`. Persistir en una transacción alcanza (CA 2). **No agregar cache.**
- **Esquema**: Hibernate con `DDL_AUTO` (en `docker-compose.yml` = `update`; los tests usan H2 `create-drop`). El repo guarda scripts manuales `src/main/resources/migration-*.sql` idempotentes para entornos con `DDL_AUTO=none|validate` (ver `migration-user-notification-rating-columns.sql` como modelo de estilo).
- `TutorProfileServiceImplTest` está comentado entero; no lo uses ni lo reactives.

## Diseño de los endpoints

```
PUT /api/v1/students/me/profile      (rol STUDENT)
PUT /api/v1/tutors/me/profile        (rol TUTOR)
```

Request alumno:

```json
{
  "firstName": "María José",
  "lastName": "García",
  "dni": "40123456",
  "phoneNumber": "+54 9 3534138000",
  "career": "Ingeniería en Sistemas",
  "institutionalId": "12345"
}
```

Request tutor: los mismos 6 campos **más** `"biography": "..."` (opcional, ≤ 300) y `"address": "..."` (opcional salvo regla de presencialidad).

Response `200`: el perfil propio completo ya actualizado (mismas formas que `GET /me/profile`, **ampliadas** con `firstName`, `lastName`, `dni`, `institutionalId`):

```json
{
  "userId": "…", "fullName": "María José García",
  "firstName": "María José", "lastName": "García",
  "email": "x@y.com", "phoneNumber": "+54 9 3534138000", "dni": "40123456",
  "career": "Ingeniería en Sistemas", "institutionalId": "12345",
  "profilePictureUrl": null, "role": "STUDENT", "hasTutorProfile": false
}
```

Códigos: 200, 400 (datos inválidos), 401, 403 (rol), 404 (carrera inexistente), 409 (DNI de otro usuario).

Reglas:
- `PUT` reemplaza los campos editables; los obligatorios vienen siempre (el front los manda).
- Strings se normalizan con `trim()`. `institutionalId`, `biography` y `address` en blanco se guardan como `null`.
- Dual-role: `firstName`, `lastName`, `dni`, `phoneNumber` viven en `User` (compartidos por ambos roles). `career` e `institutionalId` se editan **en el perfil del rol con el que se llama** y NO se sincronizan con el otro perfil.

## Archivos a crear / modificar

Todos bajo `src/main/java/com/knowlink/api/` salvo los indicados.

| # | Acción | Archivo |
|---|---|---|
| 1 | MODIFICAR | `users/data/models/User.java` |
| 2 | CREAR | `src/main/resources/migration-user-first-last-name.sql` |
| 3 | CREAR | `users/utils/UserNameUtils.java` |
| 4 | MODIFICAR | `users/data/mappers/UserMapper.java` |
| 5 | MODIFICAR | `users/repositories/IUserRepository.java` |
| 6 | MODIFICAR | `users/validation/IUserValidationService.java` y `UserValidationService.java` |
| 7 | MODIFICAR | `users/services/interfaces/IUserService.java` y `implementations/UserServiceImpl.java` |
| 8 | MODIFICAR | `users/controllers/interfaces/IUserController.java` y `implementations/UserControllerImpl.java` (quitar endpoint legacy) |
| 9 | ELIMINAR | `users/controllers/requests/UpdateUserRequest.java` |
| 10 | CREAR | `students/controllers/requests/UpdateStudentProfileRequest.java` |
| 11 | MODIFICAR | `students/controllers/responses/StudentSelfProfileResponse.java` |
| 12 | MODIFICAR | `students/data/mappers/StudentProfileMapper.java` |
| 13 | MODIFICAR | `students/services/interfaces/IStudentProfileService.java` y `implementations/StudentProfileServiceImpl.java` |
| 14 | MODIFICAR | `students/controllers/interfaces/IStudentController.java` y `implementations/StudentControllerImpl.java` |
| 15 | CREAR | `tutors/controllers/requests/UpdateTutorProfileRequest.java` |
| 16 | MODIFICAR | `tutors/controllers/responses/TutorSelfProfileResponse.java` |
| 17 | MODIFICAR | `tutors/data/mappers/TutorProfileMapper.java` |
| 18 | MODIFICAR | `tutors/validations/ITutorProfileValidationService.java` y `TutorProfileValidationServiceImpl.java` |
| 19 | MODIFICAR | `tutors/services/interfaces/ITutorProfileService.java` y `implementations/TutorProfileServiceImpl.java` |
| 20 | MODIFICAR | `tutors/controllers/interfaces/ITutorController.java` y `implementations/TutorControllerImpl.java` |
| 21 | CREAR | tests (ver sección Tests) |

### 1. `User` (MODIFICAR)

Agregar, junto a `fullName`:

```java
@Column(name = "first_name")
private String firstName;

@Column(name = "last_name")
private String lastName;
```

No tocar nada más (el `@Builder` los incorpora solo).

### 2. Migración manual (CREAR)

`src/main/resources/migration-user-first-last-name.sql`. Copiar el **estilo exacto** de `migration-user-notification-rating-columns.sql` (cabecera con comentario "US-03 migration", idempotente con `information_schema.COLUMNS` + `PREPARE/EXECUTE`, verificación final). Agrega a la tabla `users` dos columnas `first_name VARCHAR(255) NULL` y `last_name VARCHAR(255) NULL`. **No hacer backfill** (los NULL son válidos: el código resuelve con fallback, ver paso 3). Aclarar en el comentario que con `DDL_AUTO=update` el script no es necesario.

### 3. `UserNameUtils` (CREAR)

Paquete `com.knowlink.api.users.utils` (junto a `Constants`). Clase final con constructor privado y 3 métodos estáticos, sin dependencias de Spring:

```java
public final class UserNameUtils {

    private UserNameUtils() {}

    /** "first last" normalizado (trim), sin espacios dobles en los extremos. */
    public static String buildFullName(String firstName, String lastName) {
        return (firstName.trim() + " " + lastName.trim()).trim();
    }

    public static String firstNameOf(User user) {
        if (hasStructuredName(user)) return user.getFirstName().trim();
        String fullName = user.getFullName() == null ? "" : user.getFullName().trim();
        int idx = fullName.indexOf(' ');
        return idx < 0 ? fullName : fullName.substring(0, idx);
    }

    public static String lastNameOf(User user) {
        if (hasStructuredName(user)) return user.getLastName().trim();
        String fullName = user.getFullName() == null ? "" : user.getFullName().trim();
        int idx = fullName.indexOf(' ');
        return idx < 0 ? "" : fullName.substring(idx + 1).trim();
    }

    private static boolean hasStructuredName(User user) {
        return user.getFirstName() != null && !user.getFirstName().isBlank()
                && user.getLastName() != null && !user.getLastName().isBlank();
    }
}
```

(Fallback para usuarios previos o admins: primer token = nombre, resto = apellido. Es solo para precargar el formulario; al guardar, el usuario confirma/corrige y quedan las columnas completas.)

### 4. `UserMapper` (MODIFICAR)

En `toStudentUser` y `toTutorUser` agregar `.firstName(request.firstName().trim())`, `.lastName(request.lastName().trim())` y cambiar `.fullName(...)` a `UserNameUtils.buildFullName(request.firstName(), request.lastName())`. No tocar `toResponse`.

### 5. `IUserRepository` (MODIFICAR)

Agregar **un** método (mismo estilo JPQL del existente `existsByDni`):

```java
@Query("SELECT CASE WHEN COUNT(u) > 0 THEN TRUE ELSE FALSE END FROM User u WHERE u.dni = :dni AND u.userId <> :userId AND u.accountStatus != 'DELETED'")
boolean existsByDniAndUserIdNot(@Param("dni") String dni, @Param("userId") UUID userId);
```

### 6. Validación de DNI (MODIFICAR interfaz + impl)

`IUserValidationService`:

```java
void ifDniBelongsToAnotherUserThrowException(String dni, UUID userId);
```

`UserValidationService` (mismo error que el registro):

```java
@Override
public void ifDniBelongsToAnotherUserThrowException(String dni, UUID userId) {
    if (userRepository.existsByDniAndUserIdNot(dni, userId)) {
        throw new DuplicateResourceException(
                "DUPLICATE_DNI",
                "Este DNI ya está registrado.",
                String.format("user con dni '%s' ya pertenece a otro usuario (editor userId '%s')", dni, userId));
    }
}
```

### 7. `IUserService` / `UserServiceImpl` (MODIFICAR)

- **Quitar** `User updateUser(UUID userId, UpdateUserRequest request)` (interfaz e impl) y el `import` de `UpdateUserRequest`.
- **Agregar**:

```java
User updatePersonalData(UUID userId, String firstName, String lastName, String dni, String phoneNumber);
```

```java
@Override
@Transactional
public User updatePersonalData(UUID userId, String firstName, String lastName, String dni, String phoneNumber) {
    User user = findByIdOrThrowException(userId);

    String newDni = dni.trim();
    if (!newDni.equals(user.getDni())) {
        userValidationService.ifDniBelongsToAnotherUserThrowException(newDni, userId);
    }

    user.setFirstName(firstName.trim());
    user.setLastName(lastName.trim());
    user.setFullName(UserNameUtils.buildFullName(firstName, lastName));
    user.setDni(newDni);
    user.setPhoneNumber(phoneNumber.trim());
    // El email NUNCA se toca aquí.
    return userRepository.save(user);
}
```

(Agregar `import com.knowlink.api.users.utils.UserNameUtils;`. No tocar `updateUserRole`, que se mantiene.)

### 8-9. Eliminar endpoint legacy (MODIFICAR + ELIMINAR)

- `IUserController`: borrar el método `updateUser` (`@PutMapping("/{userId}")` con su `@Operation`/`@ResponseStatus`) y el `import` de `UpdateUserRequest`. Si `PutMapping` queda sin usar, el import `org.springframework.web.bind.annotation.*` ya es comodín: no hay que tocarlo.
- `UserControllerImpl`: borrar el método `updateUser` y el `import` de `UpdateUserRequest`.
- Borrar el archivo `UpdateUserRequest.java`.
- Ejecutar `grep -rn "UpdateUserRequest" src` → no debe quedar ninguna referencia. Si alguna aparece (test o clase), **parar y avisar** en lugar de improvisar.

### 10. `UpdateStudentProfileRequest` (CREAR)

Paquete `com.knowlink.api.students.controllers.requests` (junto a `ActivateTutorRoleRequest`). Mensajes Jakarta **en inglés**, mismas reglas que `StudentRegistrationRequest`:

```java
package com.knowlink.api.students.controllers.requests;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record UpdateStudentProfileRequest(

        @NotBlank(message = "First name is required")
        @Size(max = 100, message = "First name cannot exceed 100 characters")
        String firstName,

        @NotBlank(message = "Last name is required")
        @Size(max = 100, message = "Last name cannot exceed 100 characters")
        String lastName,

        @NotBlank(message = "DNI is required")
        @Pattern(regexp = "^\\d{7,8}$", message = "DNI must be a valid 7 or 8 digit number")
        String dni,

        @NotBlank(message = "Phone number is required")
        @Size(max = 30, message = "Phone number cannot exceed 30 characters")
        String phoneNumber,

        @NotBlank(message = "Career is required")
        String career,

        @Size(max = 50, message = "Institutional ID cannot exceed 50 characters")
        String institutionalId
) {}
```

**No incluir `email`** (CA 1/3).

### 11-12. Respuesta y mapper del alumno (MODIFICAR)

`StudentSelfProfileResponse` queda así (orden exacto; solo se agregan 4 campos):

```java
public record StudentSelfProfileResponse(
        UUID userId,
        String fullName,
        String firstName,
        String lastName,
        String email,
        String phoneNumber,
        String dni,
        String career,
        String institutionalId,
        String profilePictureUrl,
        Role role,
        boolean hasTutorProfile
) {}
```

`StudentProfileMapper.toSelfProfileResponse(User, StudentProfile, boolean)` pasa a construirlo con ese orden, usando `UserNameUtils.firstNameOf(user)`, `UserNameUtils.lastNameOf(user)`, `user.getDni()` y `profile.getInstitutionalId()`. Es el único sitio que construye este record.

### 13. Servicio del alumno (MODIFICAR interfaz + impl)

`IStudentProfileService`:

```java
StudentSelfProfileResponse updateProfile(UUID userId, UpdateStudentProfileRequest request);
```

`StudentProfileServiceImpl`: inyectar `IUserService userService` (campo `private final`; la clase ya usa `@RequiredArgsConstructor`; **no hay dependencia circular**: `UserServiceImpl` no depende de servicios de perfiles). Agregar:

```java
@Override
@Transactional
public StudentSelfProfileResponse updateProfile(UUID userId, UpdateStudentProfileRequest request) {
    StudentProfile profile = studentProfileValidationService.getStudentProfileOrThrow(userId);
    Career career = careerService.findByNameOrThrowException(request.career().trim());

    User user = userService.updatePersonalData(
            userId, request.firstName(), request.lastName(), request.dni(), request.phoneNumber());

    profile.setCareer(career);
    profile.setInstitutionalId(blankToNull(request.institutionalId()));
    profile = studentProfileRepository.save(profile);

    boolean hasTutorProfile = userProfileLookupService.hasTutorProfile(userId);
    return studentProfileMapper.toSelfProfileResponse(user, profile, hasTutorProfile);
}

private String blankToNull(String value) {
    return value == null || value.isBlank() ? null : value.trim();
}
```

Importante: la carrera se resuelve **antes** de escribir el usuario, así un 404 de carrera no deja datos a medias (además todo es una sola `@Transactional`, que hace rollback ante cualquier excepción). Agregar los imports faltantes.

### 14. Controlador del alumno (MODIFICAR interfaz + impl)

En `IStudentController`, junto a `getMyProfile` (agregar imports `PutMapping` y `UpdateStudentProfileRequest`; `Valid`, `RequestBody`, `AuthenticationPrincipal`, `PreAuthorize` ya existen):

```java
@PutMapping("/me/profile")
@Operation(summary = "Editar mis datos personales (alumno)",
        description = "Modifica nombre, apellido, DNI, celular, carrera y legajo del alumno autenticado. "
                + "El correo electrónico no se puede modificar. Devuelve el perfil actualizado.")
@ApiResponses({
        @ApiResponse(responseCode = "200", description = "Perfil actualizado"),
        @ApiResponse(responseCode = "400", description = "Datos inválidos (campo obligatorio faltante o DNI con formato inválido)"),
        @ApiResponse(responseCode = "401", description = "No autenticado"),
        @ApiResponse(responseCode = "403", description = "Acceso denegado"),
        @ApiResponse(responseCode = "404", description = "Perfil o carrera no encontrados"),
        @ApiResponse(responseCode = "409", description = "El DNI ya pertenece a otro usuario")
})
@ResponseStatus(OK)
@PreAuthorize("hasRole('STUDENT')")
StudentSelfProfileResponse updateMyProfile(
        @Valid @RequestBody UpdateStudentProfileRequest request,
        @Parameter(hidden = true) @AuthenticationPrincipal UserPrincipal principal);
```

En `StudentControllerImpl` (solo delega):

```java
@Override
public StudentSelfProfileResponse updateMyProfile(UpdateStudentProfileRequest request, UserPrincipal principal) {
    return studentProfileService.updateProfile(principal.getUser().getUserId(), request);
}
```

### 15. `UpdateTutorProfileRequest` (CREAR)

Paquete `com.knowlink.api.tutors.controllers.requests` (junto a `UpdateMinNoticeMinutesRequest`). Los mismos 6 campos y anotaciones que el del alumno, más:

```java
        @Size(max = 300, message = "Biography cannot exceed 300 characters")
        String biography,

        @Size(max = 255, message = "Address cannot exceed 255 characters")
        String address
```

**No incluir `email` ni `subjects`.**

### 16-17. Respuesta y mapper del tutor (MODIFICAR)

`TutorSelfProfileResponse`: agregar `firstName`, `lastName` (después de `fullName`), `dni` (después de `phoneNumber`) e `institutionalId` (después de `career`). Orden exacto resultante:

```java
public record TutorSelfProfileResponse(
        UUID userId,
        String fullName,
        String firstName,
        String lastName,
        String email,
        String phoneNumber,
        String dni,
        String career,
        String institutionalId,
        String profilePictureUrl,
        String biography,
        String address,
        boolean mercadoPagoLinked,
        Double averageRating,
        boolean hasStudentProfile,
        List<TutorSubjectResponse> subjects
) {}
```

`TutorProfileMapper.toSelfProfileResponse(TutorProfile, boolean)` se actualiza a ese orden (mismas fuentes que en el alumno: `UserNameUtils` y `tutorProfile.getInstitutionalId()`). Es el único sitio que construye este record. No tocar el resto del mapper.

### 18. Validación del tutor (MODIFICAR interfaz + impl)

`ITutorProfileValidationService` agregar (sin tocar los existentes):

```java
void ifCareerChangeBlockedBySubjectsThrowException(TutorProfile tutorProfile, Career newCareer);
void ifAddressRequiredForInPersonSubjectsThrowException(TutorProfile tutorProfile, String address);
```

`TutorProfileValidationServiceImpl`:
- `ifCareerChangeBlockedBySubjectsThrowException`: si `!tutorProfile.getCareer().getCareerId().equals(newCareer.getCareerId())` y `!tutorProfile.getSubjects().isEmpty()` → `throw new ValidationException("No podés cambiar tu carrera mientras tengas materias registradas. Eliminá tus materias primero.")`. (Las materias pertenecen a la carrera del tutor.)
- `ifAddressRequiredForInPersonSubjectsThrowException`: si algún `TutorSubject` de `tutorProfile.getSubjects()` tiene `getModality()` igual a `Modality.IN_PERSON` o `Modality.BOTH` y `address == null || address.isBlank()` → `throw new ValidationException("La dirección es obligatoria para clases presenciales.")`.
- Agregar imports (`Career`, `Modality`, `ValidationException`, `TutorSubject`) y no tocar los métodos existentes.

### 19. Servicio del tutor (MODIFICAR interfaz + impl)

`ITutorProfileService`:

```java
TutorSelfProfileResponse updateProfile(UUID tutorUserId, UpdateTutorProfileRequest request);
```

`TutorProfileServiceImpl` (ya inyecta `userService`, `careerService`, `tutorProfileValidationService`, `tutorProfileRepository`, `userProfileLookupService`, `tutorProfileMapper`), junto a `getSelfProfile`:

```java
@Override
@Transactional
public TutorSelfProfileResponse updateProfile(UUID tutorUserId, UpdateTutorProfileRequest request) {
        TutorProfile tutorProfile = tutorProfileValidationService.findTutorProfileOrThrowException(tutorUserId);
        Career career = careerService.findByNameOrThrowException(request.career().trim());

        tutorProfileValidationService.ifCareerChangeBlockedBySubjectsThrowException(tutorProfile, career);
        tutorProfileValidationService.ifAddressRequiredForInPersonSubjectsThrowException(
                        tutorProfile, request.address());

        userService.updatePersonalData(tutorUserId, request.firstName(), request.lastName(),
                        request.dni(), request.phoneNumber());

        tutorProfile.setCareer(career);
        tutorProfile.setInstitutionalId(blankToNull(request.institutionalId()));
        tutorProfile.setBiography(blankToNull(request.biography()));
        tutorProfile.setAddress(blankToNull(request.address()));
        tutorProfile = tutorProfileRepository.save(tutorProfile);

        boolean hasStudentProfile = userProfileLookupService.hasStudentProfile(tutorUserId);
        return tutorProfileMapper.toSelfProfileResponse(tutorProfile, hasStudentProfile);
}

private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
}
```

(Todas las validaciones corren **antes** de escribir. Si ya existe un método privado `blankToNull` en la clase, reutilizarlo en lugar de duplicarlo.) Reglas: no tocar `subjects`, `mercadoPagoLinked`, `verified`, `averageRating`, `minNoticeMinutes` ni `profilePictureUrl`.

### 20. Controlador del tutor (MODIFICAR interfaz + impl)

En `ITutorController`, junto a `getMyProfile` (agregar import de `UpdateTutorProfileRequest`; `PutMapping`, `Valid`, `RequestBody`, `Parameter` ya están importados):

```java
@PutMapping("/me/profile")
@Operation(summary = "Editar mis datos personales (tutor)",
        description = "Modifica nombre, apellido, DNI, celular, carrera, legajo, biografía y dirección del tutor autenticado. "
                + "El correo electrónico y las materias no se modifican aquí. Devuelve el perfil actualizado.")
@ApiResponses({
                @ApiResponse(responseCode = "200", description = "Perfil actualizado"),
                @ApiResponse(responseCode = "400", description = "Datos inválidos (campo obligatorio, DNI, biografía > 300, dirección requerida con clases presenciales o cambio de carrera con materias)"),
                @ApiResponse(responseCode = "401", description = "No autenticado"),
                @ApiResponse(responseCode = "403", description = "Acceso denegado"),
                @ApiResponse(responseCode = "404", description = "Perfil o carrera no encontrados"),
                @ApiResponse(responseCode = "409", description = "El DNI ya pertenece a otro usuario")
})
@ResponseStatus(OK)
@PreAuthorize("hasRole('TUTOR')")
TutorSelfProfileResponse updateMyProfile(
        @Valid @RequestBody UpdateTutorProfileRequest request,
        @Parameter(hidden = true) @AuthenticationPrincipal UserPrincipal principal);
```

En `TutorControllerImpl`:

```java
@Override
public TutorSelfProfileResponse updateMyProfile(UpdateTutorProfileRequest request, UserPrincipal principal) {
    return this.tutorProfileService.updateProfile(principal.getUser().getUserId(), request);
}
```

## Tests (CREAR)

Seguir el **patrón exacto de `TutorWeeklyScheduleControllerTest`** (`src/test/java/com/knowlink/api/tutors/controllers/`): `@SpringBootTest @AutoConfigureMockMvc @ActiveProfiles("test") @Transactional`, repos `@Autowired`, helper `asUser(User)` con `authentication(new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities()))`, seed por repositorios con **emails y DNI únicos por clase de test** (p. ej. `ana.edit.profile@test.com`, DNI `30111222`). `@DisplayName` con prefijo `CP-003.N`. Body con `ObjectMapper` o `String` JSON y `.contentType(MediaType.APPLICATION_JSON)`.

### `src/test/java/com/knowlink/api/students/controllers/StudentProfileUpdateControllerTest.java`

Seed: 2 carreras, un `User` STUDENT con `StudentProfile` (career A), y un **segundo** usuario con DNI conocido (para el 409).

| ID | Verifica |
|---|---|
| CP-003.1 | 200: actualiza los 6 campos; la respuesta trae valores nuevos, `fullName == "<first> <last>"`; `GET /api/v1/students/me/profile` devuelve lo mismo (CA 2) |
| CP-003.2 | 200: body con `"email":"otro@x.com"` → el email del usuario en base NO cambia (releer con `userRepository`) |
| CP-003.3 | 400 (uno por request): `firstName`, `lastName`, `dni`, `phoneNumber`, `career` ausentes o `"   "`; la base queda intacta |
| CP-003.4 | 400: dni `"123"`, `"123456789"` y `"12a45678"` |
| CP-003.5 | 409 `detail=DUPLICATE_DNI` con el DNI del otro usuario; la base queda intacta |
| CP-003.6 | 200: enviar el propio DNI actual (no es duplicado) |
| CP-003.7 | 404 `CAREER_NOT_FOUND`; el nombre/DNI NO cambian (rollback/orden de validación) |
| CP-003.8 | 200: `institutionalId` en blanco → queda `null` |
| CP-003.9 | 403: usuario con rol TUTOR; 401: request sin autenticación |
| CP-003.10 | Usuario con `fullName="Juan Perez"` y `firstName/lastName` nulos → `GET /me/profile` devuelve `firstName="Juan"`, `lastName="Perez"` |

### `src/test/java/com/knowlink/api/tutors/controllers/TutorProfileUpdateControllerTest.java`

Seed (copiar de `TutorWeeklyScheduleControllerTest`): carrera, materia, `User` TUTOR, `TutorProfile`, un `TutorSubject` **VIRTUAL**; segundo usuario con DNI conocido.

| ID | Verifica |
|---|---|
| CP-003.11 | 200: actualiza datos comunes + `biography` + `address`; `GET /api/v1/tutors/me/profile` refleja los valores nuevos |
| CP-003.12 | 400: `biography` de 301 caracteres; 200 con 300 |
| CP-003.13 | 200: `address` en blanco con solo materias VIRTUAL → `address` queda `null` |
| CP-003.14 | 400: `address` en blanco cuando el tutor tiene una materia `IN_PERSON` (y otra con `BOTH`); la base queda intacta |
| CP-003.15 | 400: cambiar `career` teniendo materias; la base queda intacta |
| CP-003.16 | 200: cambiar `career` en un tutor **sin** materias |
| CP-003.17 | 409 `DUPLICATE_DNI` con el DNI de otro usuario |
| CP-003.18 | 403: usuario con rol STUDENT |
| CP-003.19 | Rol dual: usuario con `StudentProfile` **y** `TutorProfile`; editar nombre/celular por `/tutors/me/profile` → `GET /students/me/profile` (autenticado como ese mismo usuario con rol STUDENT) devuelve el nombre/celular nuevos |
| CP-003.20 | Las materias, `mercadoPagoLinked` y `profilePictureUrl` del tutor no cambian tras el PUT |

### `src/test/java/com/knowlink/api/users/utils/UserNameUtilsTest.java` (unit, sin Spring)

`buildFullName` con espacios extra; `firstNameOf`/`lastNameOf` con columnas completas; fallback con `fullName="María José García"` (→ `"María"` / `"José García"`); `fullName` de una sola palabra (→ apellido `""`); `fullName` nulo (→ `""`/`""`).

## No hacer

- No agregar el campo `email` a ningún request (CA 1/3) ni modificar el email en ningún servicio.
- No reemitir ni invalidar el JWT (el email no cambia, el token sigue siendo válido).
- No editar contraseña, foto de perfil ni materias en estos endpoints.
- No implementar edición de ADMIN (US-39).
- No sincronizar `career`/`institutionalId` entre `StudentProfile` y `TutorProfile`.
- No agregar `@Cacheable`/`@CacheEvict`.
- No tocar `SecurityConfig`, `SecurityConstants` ni `GlobalExceptionHandler`.
- No tomar el id del usuario desde la ruta o el body: solo del `UserPrincipal`.
- No reutilizar `existsByDni` para validar la edición (cuenta al propio usuario) ni modificar `ifDniAlreadyExistsThrowException` (lo usa el registro).
- No hacer backfill de `first_name`/`last_name` ni cambiar `AdminSeeder`.
- No reactivar ni editar `TutorProfileServiceImplTest` (está comentado).
- Mensajes Jakarta en inglés; mensajes que viajan al usuario (`ValidationException`, `ResourceNotFoundException.userMessage`, `DuplicateResourceException.userMessage`) en español; Swagger en español.
- No usar `Optional.get()`, `@Autowired` en campos ni `System.out.println`.

## Verificación (correr y pegar el resultado)

```bash
grep -rn "UpdateUserRequest" src                                     # sin resultados
./mvnw test -Dtest='StudentProfileUpdateControllerTest,TutorProfileUpdateControllerTest,UserNameUtilsTest'
./mvnw verify -B                                                     # lo que corre el CI; igual o mejor que la línea base
```

Chequeo manual en Swagger (`/swagger-ui.html`): aparecen `PUT /api/v1/students/me/profile` (tag "Students") y `PUT /api/v1/tutors/me/profile` (tag "Tutors") con descripción en español, y ya **no** aparece `PUT /api/v1/users/{userId}`.

## Puntos a confirmar con el PO (informar en el PR, no bloquean)

1. **Cambio de DNI**: el criterio dice "todos los campos que ingresó al registrarse", por eso el DNI es editable (con control de unicidad). Si el DNI debe considerarse inmutable (documento de identidad), basta con quitarlo del request y del formulario.
2. **Cambio de carrera del tutor con materias** se bloquea con 400 (las materias pertenecen a la carrera). Es una restricción agregada que el criterio no menciona.
3. **Foto de perfil**: se sigue editando con los endpoints existentes de subida (multipart), no con este `PUT`.
4. **ADMIN**: sin edición hasta US-39.

## Commits (convención del repo: prefijo en inglés + descripción en español)

```
feat: agrega columnas de nombre y apellido al usuario
feat: agrega edición de datos personales del alumno
feat: agrega edición de datos personales del tutor
fix: elimina endpoint de actualización de usuario sin control de propietario
test: agrega tests de edición de datos personales
```

## Definition of Done

- [ ] `PUT /api/v1/students/me/profile` y `PUT /api/v1/tutors/me/profile` implementados con `@Operation`/`@ApiResponses` en español
- [ ] El email nunca se modifica aunque venga en el body
- [ ] Obligatorios (nombre, apellido, DNI, celular, carrera) validados con 400; DNI con formato 7–8 dígitos
- [ ] DNI de otro usuario → 409 `DUPLICATE_DNI`; el propio DNI se puede reenviar
- [ ] Tutor: biografía ≤ 300, dirección obligatoria con clases presenciales, cambio de carrera bloqueado con materias
- [ ] `full_name` siempre sincronizado con `first_name`/`last_name`; fallback para usuarios previos
- [ ] Respuestas ampliadas con `firstName`, `lastName`, `dni`, `institutionalId`
- [ ] Endpoint legacy `PUT /api/v1/users/{userId}` y `UpdateUserRequest` eliminados
- [ ] Migración `migration-user-first-last-name.sql` agregada
- [ ] Tests CP-003.1 a CP-003.20 y `UserNameUtilsTest` en verde y `./mvnw verify -B` sin regresiones
- [ ] Sin cambios en `SecurityConfig`, `GlobalExceptionHandler`, `bookings`
- [ ] Code review aprobado
