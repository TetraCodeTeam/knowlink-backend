# Spec Backend — Catálogo multi-institución (Institución → Carrera → Materia)

## Metadata

- **Feature ID:** FEAT-CATALOGO-INSTITUCIONAL (v2, reescrita sobre el código real)
- **Historias:** US-01, US-42, US-43, US-06 (+ US-49 y comunidad por arrastre)
- **Repo / rama base:** `knowlink-backend` · `dev` (commit `a772beb`)
- **Rama sugerida:** `feat/catalogo-institucional`
- **Alcance MVP:** 2 instituciones cargadas por el equipo (sin ABM de instituciones, sin alta autoservicio).
- **Par frontend:** `spec-frontend-catalogo-institucional.md` (el contrato de la sección 6 es el acuerdo entre ambos).

## 0. Qué cambió respecto de la v1 de este spec (y por qué)

La v1 se escribió sin ver el código. Al leer `dev` aparecen hechos que cambian el diseño:

| Hallazgo en el código | Consecuencia en el spec |
|---|---|
| Los IDs son `UUID` (no `Long`), todo en inglés, módulo `tutors` ya contiene `Career`, `Subject`, seeders y controllers | No se crea módulo `catalogo`: `Institution` vive en `com.knowlink.api.tutors` junto a `Career`/`Subject` |
| `Subject` ya tiene `@ManyToOne Career` (no nulo) e `isBasic`; las básicas cuelgan de una **carrera placeholder** (la primera que devuelva `findAll()`) | La idea de "Materias Compartidas" como carrera **reemplaza ese hack**: las básicas pasan a colgar de una carrera `SHARED` por institución. Se mantiene la relación 1-N existente (no N-a-N) |
| `Career.name` y `Subject.name` son `unique` **globales** | Pasan a ser únicos por institución (`UNIQUE (institution_id, name)`). Sin esto la segunda universidad no puede tener "Programación" |
| Registro, edición de perfil y `TutorSubjectAssembly` resuelven la carrera **por nombre** (`findByNameOrThrowException`) | Hay que resolver por `(institutionId, nombre)`; `findByName` global deja de ser seguro |
| `CommunityPostSpecifications.inCareer` usa el path JPA `"isBasic"` (string, no falla al compilar) | Si se elimina el campo, rompe **en runtime**. Se reescribe sobre `career.type` |
| El esquema se maneja con scripts SQL manuales en `src/main/resources/migration-*.sql` (sin Flyway/Liquibase) y `DDL_AUTO` viene de variable de entorno | Se entrega `migration-institutions-catalog.sql` siguiendo la convención. **Se ejecuta antes de mergear a `main`** (main despliega solo) |
| 37 archivos de test usan `Career.builder()`, 42 `Subject.builder()` y **27 archivos (38 usos)** `.isBasic(...)`; 3 tests llaman a `findByName` de los repos directamente | Plan de tests y fixture compartida (sección 9) |

## 1. Decisiones de diseño (cerradas)

1. **Una materia pertenece a exactamente una carrera.** Si esa carrera es `SHARED`, la materia es transversal a toda la institución. Esto elimina por diseño el problema "alguien la asocia a una carrera y otro no": no existe "sin carrera".
2. **Carrera `SHARED` = "Materias Compartidas"**, una por institución, identificada por `type = SHARED` (nunca por nombre).
3. **`isBasic` deja de ser dato** y pasa a ser derivado (`career.type == SHARED`). La API sigue exponiendo `isBasic` en `SubjectResponse` para no romper al frontend.
4. **`Subject.institution`** se guarda desnormalizado (además de `career.institution`) porque MySQL no permite un `UNIQUE` sobre columnas de dos tablas. Invariante: `subject.institution == subject.career.institution`, garantizada en el service.
5. **La institución es inmutable** para un perfil en el MVP (se deriva de `career.institution`). Editar perfil cambia la carrera solo dentro de la misma institución.
6. **La institución del usuario no se guarda en el perfil**: se deriva `profile → career → institution`.

## 2. Modelo de datos

Convenciones reales a respetar: PK `UUID` con `GenerationType.UUID`, columna `<entidad>_id`, Lombok `@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder`.

### 2.1 Nueva — `tutors/data/models/Institution.java`
| Campo | Columna | Notas |
|---|---|---|
| `UUID institutionId` | `institution_id` | PK |
| `String name` | `name` | `nullable=false, unique=true` (ej. "UTN FRVM", "UNVM") |

Tabla `institution`.

### 2.2 Nuevo enum — `tutors/data/enums/CareerType.java`
`REGULAR`, `SHARED`

### 2.3 Modificar — `Career.java`
- Agregar `@ManyToOne(fetch = LAZY) @JoinColumn(name = "institution_id", nullable = false) Institution institution`.
- Agregar `@Enumerated(EnumType.STRING) @Column(name = "type", nullable = false) @Builder.Default CareerType type = CareerType.REGULAR`.
- `name`: quitar `unique = true`; agregar a `@Table`: `uniqueConstraints = @UniqueConstraint(name = "uk_career_institution_name", columnNames = {"institution_id", "name"})`.

### 2.4 Modificar — `Subject.java`
- Agregar `@ManyToOne(fetch = LAZY) @JoinColumn(name = "institution_id", nullable = false) Institution institution`.
- `name`: quitar `unique = true`; `@Table(name = "subjects", uniqueConstraints = @UniqueConstraint(name = "uk_subject_institution_name", columnNames = {"institution_id", "name"}))`.
- **Eliminar el campo `isBasic`** y agregar el método derivado (no persistente, el acceso JPA es por campo porque `@Id` está en el campo):
  ```java
  public boolean isBasic() {
      return career != null && career.getType() == CareerType.SHARED;
  }
  ```
- La columna física `is_basic` **no se borra** en este PR (ver migración, paso 11).

## 3. Archivos a modificar / crear (rutas verificadas en `dev`)

Base: `src/main/java/com/knowlink/api/`

### Crear
| Archivo | Contenido |
|---|---|
| `tutors/data/models/Institution.java` | Entidad 2.1 |
| `tutors/data/enums/CareerType.java` | Enum 2.2 |
| `tutors/repositories/IInstitutionRepository.java` | `JpaRepository<Institution, UUID>`; `Optional<Institution> findByName(String)` |
| `tutors/services/interfaces/IInstitutionService.java` + `implementations/InstitutionServiceImpl.java` | `List<Institution> findAll()` (orden por nombre); `Institution findByIdOrThrowException(UUID)` → `ResourceNotFoundException("INSTITUTION_NOT_FOUND", "Institución no encontrada.", technical)` |
| `tutors/controllers/interfaces/IInstitutionController.java` + `implementations/InstitutionControllerImpl.java` | `GET /api/v1/institutions`, `@Tag(name = "Institutions", description = "Catálogo de instituciones")` (Swagger en español) |
| `tutors/controllers/responses/InstitutionResponse.java` | `record InstitutionResponse(UUID institutionId, String name)` |
| `tutors/config/InstitutionSeeder.java` | `@Order(0)`, ver sección 7 |
| `src/main/resources/migration-institutions-catalog.sql` | Sección 8 |

### Modificar
| Archivo | Cambio |
|---|---|
| `tutors/data/models/Career.java`, `Subject.java` | Sección 2 |
| `tutors/repositories/ICareerRepository.java` | Reemplazar `findByName` por `Optional<Career> findByInstitution_InstitutionIdAndName(UUID, String)`; agregar `List<Career> findByInstitution_InstitutionIdAndTypeOrderByName(UUID, CareerType)` y `Optional<Career> findByInstitution_InstitutionIdAndType(UUID, CareerType)` |
| `tutors/repositories/ISubjectRepository.java` | Ver 3.1 |
| `tutors/services/interfaces/ICareerService.java` + `implementations/CareerServiceImpl.java` | Ver 3.2 |
| `tutors/services/interfaces/ISubjectService.java` + `implementations/SubjectServiceImpl.java` | Ver 3.3 |
| `tutors/controllers/interfaces/ICareerController.java` + `implementations/CareerControllerImpl.java` | `GET /api/v1/careers?institutionId=` (obligatorio) |
| `tutors/controllers/interfaces/ISubjectController.java` + `implementations/SubjectControllerImpl.java` | `GET /api/v1/subjects/basic?institutionId=` (obligatorio); `GET /api/v1/subjects?careerId=` sin cambios de contrato |
| `tutors/config/CareerSeeder.java`, `SubjectSeeder.java` | Sección 7 |
| `tutors/services/implementations/TutorProfileServiceImpl.java` | `createProfile` y `updateProfile`: resolver carrera con institución (ver 4). `searchTutor`: filtro por institución (ver 5) |
| `students/services/implementations/StudentProfileServiceImpl.java` | `createProfile` y `updateProfile`: ídem |
| `tutors/data/specifications/TutorSearchSpecifications.java` | Agregar `subjectInInstitution(UUID)` |
| `tutors/data/mappers/TutorProfileMapper.java`, `students/data/mappers/StudentProfileMapper.java` | Completar los campos nuevos de las respuestas `Self` (sección 6.4) |
| `tutors/controllers/responses/TutorSelfProfileResponse.java`, `students/controllers/responses/StudentSelfProfileResponse.java` | Agregar `careerId`, `institutionId`, `institution` |
| `auth/controllers/requests/StudentRegistrationRequest.java`, `TutorRegistrationRequest.java` | Agregar `@NotNull(message = "Institution is required") UUID institutionId` (mensajes de validación en inglés) |
| `users/services/interfaces/IUserProfileLookupService.java` + `implementations/UserProfileLookupServiceImpl.java` | Agregar `Optional<UUID> findInstitutionIdByUserId(UUID userId)` (perfil de alumno primero, si no tutor) |
| `community/data/specifications/CommunityPostSpecifications.java` | Reescribir `inCareer` (ver 3.4) |
| `community/services/implementations/CommunityPostServiceImpl.java` (+ `ICommunityPostService`) | `getSubjects` acotado por institución; nuevo `getCareers` |
| `community/controllers/interfaces/ICommunityController.java` + `implementations/CommunityControllerImpl.java` | `getSubjects` recibe `principal`; nuevo `GET /api/v1/community/careers` |
| `security/utils/SecurityConstants.java` | Agregar `"/api/v1/institutions"` a `PUBLIC_WHITELIST` |

### 3.1 `ISubjectRepository`
Eliminar `findByName`, `findBasicSubjects`, `findBasicAndByCareerId`, `findNonBasicByCareerId`. Agregar:
```java
Optional<Subject> findByInstitution_InstitutionIdAndName(UUID institutionId, String name);

@Query("""
        SELECT s FROM Subject s
        WHERE s.institution.institutionId = :institutionId
          AND s.career.type = com.knowlink.api.tutors.data.enums.CareerType.SHARED
        ORDER BY s.name
        """)
List<Subject> findSharedByInstitutionId(@Param("institutionId") UUID institutionId);

/** Materias de una carrera + las compartidas de su misma institución (reemplaza findBasicAndByCareerId). */
@Query("""
        SELECT s FROM Subject s
        WHERE s.career.careerId = :careerId
           OR (s.career.type = com.knowlink.api.tutors.data.enums.CareerType.SHARED
               AND s.institution.institutionId =
                   (SELECT c.institution.institutionId FROM Career c WHERE c.careerId = :careerId))
        ORDER BY s.name
        """)
List<Subject> findSharedAndByCareerId(@Param("careerId") UUID careerId);

List<Subject> findByCareer_CareerIdOrderByName(UUID careerId);

List<Subject> findByInstitution_InstitutionIdOrderByName(UUID institutionId);
```

### 3.2 `ICareerService`
- Eliminar `findByNameOrThrowException(String)` y `findAll()` (evita uso accidental cross-institución).
- Agregar:
  - `Career findRegularByNameAndInstitutionOrThrowException(String name, UUID institutionId)` → solo carreras `REGULAR`; si no existe o es `SHARED` → `ResourceNotFoundException("CAREER_NOT_FOUND", "Carrera no encontrada.", technical)` (mismo código que hoy, el frontend ya lo maneja).
  - `List<Career> findRegularByInstitution(UUID institutionId)` → valida que la institución exista (`INSTITUTION_NOT_FOUND`).
  - `Career findOrCreateSharedCareer(Institution institution)` → idempotente; nombre fijo `"Materias Compartidas"`, `type = SHARED`. Lo usa el seeder.

### 3.3 `ISubjectService`
- `findByNameAndCareerOrThrowException(String name, Career career)`: **mantener firma y comportamiento actual** (hoy es *get-or-create*, a pesar del nombre), pero la búsqueda pasa a `findByInstitution_InstitutionIdAndName(career.getInstitution().getInstitutionId(), name)`; al crear, setear `institution = career.getInstitution()`.
- `findBasicSubjects()` → `List<Subject> findSharedByInstitutionId(UUID institutionId)`.
- `findByCareerId(UUID)` → usar `findByCareer_CareerIdOrderByName`.
- Nuevo `List<Subject> findByInstitutionId(UUID)` (lo usa comunidad).

### 3.4 `CommunityPostSpecifications.inCareer`
Hoy usa `subject.<Boolean>get("isBasic")`. Reemplazar por un criterio sobre el tipo de la carrera y la institución de la carrera filtrada, con subquery de Criteria:
```java
// subject.career.careerId = :careerId
// OR (subject.career.type = SHARED AND subject.institution.institutionId =
//        (SELECT c.institution.institutionId FROM Career c WHERE c.careerId = :careerId))
```
Actualizar también el Javadoc (la mención al "placeholder" deja de ser cierta).

## 4. Registro y edición de perfil

- `POST /api/v1/auth/register/student` y `/register/tutor`: nuevo campo `institutionId` (UUID, obligatorio). `career` sigue siendo el **nombre**; se resuelve con `findRegularByNameAndInstitutionOrThrowException(request.career(), request.institutionId())`.
- `TutorProfileServiceImpl.createProfile` / `StudentProfileServiceImpl.createProfile`: usar ese método. El resto del flujo (`TutorSubjectAssemblyServiceImpl.buildTutorSubjects(tutorProfile, career, ...)`) no cambia: ya recibe la `Career`, de la que ahora sale la institución.
- `PUT /api/v1/tutors/me/profile` y el update de alumno: los requests **no cambian** (`career` por nombre). La carrera se resuelve dentro de la institución **actual** del perfil: `profile.getCareer().getInstitution().getInstitutionId()`. La regla existente `ifCareerChangeBlockedBySubjectsThrowException` se mantiene.
- Rol dual: `StudentProfileServiceImpl` ya construye materias con `studentProfile.getCareer()` al activar rol tutor, y `createProfileFromTutorData` copia la carrera → la institución se hereda sola. **Sin cambios**.

## 5. Búsqueda (US-06 / US-49)

`TutorProfileServiceImpl.searchTutor(query, studentUserId, filters)` hoy recibe `studentUserId` y no lo usa para filtrar. Cambios:

1. Resolver `institutionId` con `userProfileLookupService.findInstitutionIdByUserId(studentUserId)` (el endpoint es `@PreAuthorize("hasRole('STUDENT')")`, así que el usuario tiene perfil de alumno). Si no hay → `ResourceNotFoundException("STUDENT_PROFILE_NOT_FOUND", ...)`.
2. Rama por materia: `subjectSpec = subjectSpec.and(TutorSearchSpecifications.subjectInInstitution(institutionId))` — `subject.institution.institutionId == :id`.
3. Rama por nombre de tutor (`findByUser_FullNameContainingIgnoreCaseAndUser_AccountStatus`): descartar los `TutorProfile` cuyo `career.institution.institutionId` no coincida (filtro en memoria sobre la lista ya traída, igual que los demás filtros de esa rama).
4. **No** se agrega parámetro de institución al endpoint: el alcance sale siempre del usuario autenticado. Contrato de `GET /api/v1/tutors/search/{query}` sin cambios.

## 6. Contrato HTTP (acuerdo con el frontend)

### 6.1 `GET /api/v1/institutions` — público
```json
[{ "institutionId": "uuid", "name": "UNVM" }, { "institutionId": "uuid", "name": "UTN FRVM" }]
```
### 6.2 `GET /api/v1/careers?institutionId={uuid}` — público
Devuelve solo carreras `REGULAR` (la carrera `SHARED` **nunca** se expone acá). Forma sin cambios: `[{ "careerId", "name" }]`. `404 INSTITUTION_NOT_FOUND` si no existe. Falta de parámetro → `400 MISSING_PARAMETER` (ya lo resuelve `GlobalExceptionHandler.handleMissingParameter`; no requiere código nuevo).
### 6.3 Materias
- `GET /api/v1/subjects/basic?institutionId={uuid}` (público): materias de la carrera `SHARED` de esa institución. `SubjectResponse` igual que hoy (`isBasic: true`, derivado).
- `GET /api/v1/subjects?careerId={uuid}` (público): materias propias de esa carrera (`isBasic: false`). Sin cambios de contrato.
### 6.4 Respuestas `Self` — campos nuevos
`TutorSelfProfileResponse` y `StudentSelfProfileResponse` agregan `UUID careerId`, `UUID institutionId`, `String institution` (nombre). Se conserva `String career` (nombre). Motivo: el frontend hoy busca la carrera propia comparando nombres normalizados contra *todas* las carreras; con dos instituciones que tengan "Ingeniería en Sistemas" esa búsqueda es ambigua.
### 6.5 Registro
Ambos requests de registro: `+ institutionId`. Errores: `404 CAREER_NOT_FOUND` (carrera inexistente en esa institución o `SHARED`), `404 INSTITUTION_NOT_FOUND`.
### 6.6 Comunidad (autenticado)
- `GET /api/v1/community/careers` → `[{careerId, name}]` carreras `REGULAR` de la institución del usuario (vía `findInstitutionIdByUserId`; sin perfil → lista vacía).
- `GET /api/v1/community/subjects?careerId=`: con `careerId` → `findSharedAndByCareerId`; sin `careerId` → `findByInstitutionId(institución del usuario)` (hoy devuelve **todo** el catálogo, que con dos instituciones filtraría materias ajenas).

## 7. Seeders (carga manual del MVP)

Hoy `CareerSeeder` y `SubjectSeeder` salen con `if (count() > 0) return`. Con ese guard, en una base ya migrada **nunca se cargaría la segunda universidad**. Rediseño:

- `InstitutionSeeder` (`@Order(0)`): crea "UTN FRVM" y "UNVM" si faltan (por nombre).
- `CareerSeeder` (`@Order(1)`): por institución, `findOrCreate` de cada carrera por `(institución, nombre)` + `careerService.findOrCreateSharedCareer(institution)`. Idempotente ítem a ítem, sin guard global.
- `SubjectSeeder` (`@Order(2)`): materias compartidas → carrera `SHARED` de la institución (**se elimina la carrera placeholder**); materias de carrera → su carrera. `saveIfAbsent` por `(institución, nombre)`; setear `institution` siempre.
- Estructura de datos sugerida: un `Map<String, InstitutionCatalog>` (record con `careers`, `sharedSubjects`, `subjectsByCareer`) por nombre de institución.
- **UTN FRVM**: conservar las listas actuales tal cual (no perder datos de producción).
- **UNVM**: 2 carreras con 2–3 materias propias y 2 compartidas. **El contenido real lo define el equipo**; los nombres pueden repetir los de UTN (justamente lo que el `UNIQUE` compuesto permite).
- Orden de ejecución: Institution(0) → Career(1) → Subject(2) → Admin(3, existente).

## 8. Migración SQL — `src/main/resources/migration-institutions-catalog.sql`

Seguir al pie de la letra el estilo de `migration-user-first-last-name.sql`: encabezado con instrucciones de ejecución, **idempotente** (chequeos contra `information_schema` + `PREPARE/EXECUTE`), independiente de `DDL_AUTO`, con consulta de verificación al final. IDs en `BINARY(16)` (`UUID_TO_BIN(UUID())`, igual que los seeds).

Pasos:
1. `CREATE TABLE IF NOT EXISTS institution (institution_id BINARY(16) PRIMARY KEY, name VARCHAR(255) NOT NULL UNIQUE)`.
2. Insertar "UTN FRVM" si no existe.
3. `career`: agregar `institution_id BINARY(16) NULL` y `type VARCHAR(20) NOT NULL DEFAULT 'REGULAR'` (si no existen).
4. `subjects`: agregar `institution_id BINARY(16) NULL`.
5. Backfill: `UPDATE career/subjects SET institution_id = <UTN FRVM> WHERE institution_id IS NULL OR institution_id = UNHEX(REPEAT('0', 32))`. El segundo caso cubre bases donde `ddl-auto=update` agregó la columna como `NOT NULL` antes de correr el script: MySQL rellena las filas previas con 16 bytes en cero en lugar de `NULL`.
6. Crear la carrera `SHARED` de UTN FRVM ("Materias Compartidas") si no existe.
7. **Reasignar** las materias con `is_basic = 1` a esa carrera `SHARED` (hoy cuelgan de una carrera arbitraria).
8. **Huérfanos:** `subjects.career_id` no tiene FK (lo reconoce el comentario de `TutorSearchMapper.careerName`). Antes de endurecer, contar `subjects` con `career_id` inexistente; si hay, el script **imprime el listado y aborta** sin continuar (decisión humana).
9. Índices: buscar por `information_schema.STATISTICS` los índices únicos de **una sola columna** `name` en `career` y `subjects` (los nombres los autogenera Hibernate, no asumir `UK_...`), eliminarlos, y crear `uk_career_institution_name` y `uk_subject_institution_name`.
10. `institution_id` pasa a `NOT NULL` en ambas tablas y se agregan las FK.
11. `subjects.is_basic`: **no borrar** — `ALTER ... MODIFY is_basic BIT(1) NOT NULL DEFAULT 0`. Así el código nuevo (que ya no escribe esa columna) puede insertar, y el rollback a la versión anterior sigue siendo posible. El `DROP COLUMN` queda para un PR de limpieza posterior.
12. Verificación: cantidad de carreras/materias por institución, `SHARED` por institución, materias sin institución (debe ser 0).

## 9. Tests

Hay que tocar tests existentes porque `institution` es obligatorio (`H2` con `create-drop` aplica los `NOT NULL`/`UNIQUE`):
- **37** archivos con `Career.builder()`, **42** con `Subject.builder()`, y **27 archivos / 38 usos** de `.isBasic(true|false)` (p. ej. `MaterialRealUploadIntegrationTest`, `AcademicMaterialReportRepositoryTest`, `AdminSessionClaimAssignConcurrencyTest`, `UserAccountDeletionIntegrationTest`…). `grep -rn "isBasic\|Career.builder\|Subject.builder" src/test` da la lista completa.
- Crear una fixture compartida `src/test/java/com/knowlink/api/testsupport/CatalogFixtures.java` con `institution(name)`, `career(institution, name)`, `sharedCareer(institution)`, `subject(career, name)` (arma entidades; cada test las persiste con su repo) y migrar los tests a ella en una pasada mecánica **antes** de tocar lógica. Reemplazo de `.isBasic(true)` → materia sobre `sharedCareer`.
- **Tests que llaman `findByName` de los repositorios directamente** y dejan de compilar al eliminarlo: `TutorSearchControllerTest`, `TutorSearchFilterControllerTest`, `TutorWeeklyScheduleControllerTest`. Usan el patrón `findByName(...).orElseGet(() -> save(...))` para reutilizar datos sembrados; pasar a buscar por `(institución, nombre)` con una institución de fixture.
- **Mocks de `ICareerService`**: `TutorProfileServiceImplTest` y `TutorSubjectActivationServiceTest` (inyectan `careerService`); actualizarlos al método nuevo.
- Los seeders corren también en los tests `@SpringBootTest`: al reescribirlos, confirmar que esos tests no dependen de la carrera placeholder.

Tests nuevos:
- `CareerServiceImpl`: carrera `SHARED` rechazada en registro; misma carrera en otra institución → `CAREER_NOT_FOUND`; `findOrCreateSharedCareer` idempotente.
- `SubjectServiceImpl`: mismo nombre en dos instituciones coexiste; en la misma institución devuelve la existente; al crear, hereda `institution`.
- `ISubjectRepository` (H2): `findSharedByInstitutionId`, `findSharedAndByCareerId` no mezclan instituciones.
- Controllers: `GET /institutions`, `/careers?institutionId`, `/subjects/basic?institutionId`, `/community/careers`, `/community/subjects`; los públicos sin token.
- Búsqueda: un alumno de UNVM no ve tutores ni materias de UTN FRVM (rama por materia **y** por nombre de tutor).
- `CommunityPostSpecifications.inCareer` con materias compartidas de la misma institución y de otra.
- Seeders: segunda ejecución no duplica; con una base "ya migrada" (solo UTN) igual carga UNVM.

## 10. Gherkin de aceptación

```gherkin
Feature: Catálogo académico por institución

  Scenario: Cada institución tiene una única carrera compartida
    Given las instituciones "UTN FRVM" y "UNVM" cargadas por el seeder
    When se ejecuta el seeder dos veces
    Then cada institución tiene exactamente una carrera de tipo SHARED
    And no se duplican carreras ni materias

  Scenario: Una materia compartida es transversal solo dentro de su institución
    Given "Análisis Matemático" pertenece a la carrera SHARED de "UTN FRVM"
    When un tutor de "Ingeniería Civil" de "UTN FRVM" consulta sus materias disponibles
    Then ve "Análisis Matemático"
    And un tutor de "UNVM" no la ve

  Scenario: El mismo nombre de materia puede existir en dos instituciones
    Given existe la materia "Programación" en "UTN FRVM"
    When el equipo carga "Programación" en "UNVM"
    Then ambas existen como materias distintas

  Scenario: La carrera compartida no se ofrece al registrarse
    When un visitante consulta GET /api/v1/careers?institutionId=<UTN FRVM>
    Then la respuesta no incluye "Materias Compartidas"

  Scenario: No se puede registrar una carrera de otra institución
    Given "Ingeniería Civil" existe solo en "UTN FRVM"
    When un visitante se registra con institutionId de "UNVM" y career "Ingeniería Civil"
    Then recibe 404 con detail "CAREER_NOT_FOUND"

  Scenario: Registro de alumno exige institución
    When se envía el registro de alumno sin institutionId
    Then recibe 400

  Scenario: La búsqueda se acota a la institución del alumno
    Given un alumno de "UNVM"
    And un tutor de "UTN FRVM" que dicta "Programación"
    When el alumno busca "Programación"
    Then el tutor de "UTN FRVM" no aparece

  Scenario: La búsqueda por nombre de tutor también se acota
    Given un alumno de "UNVM" y un tutor "Carlos Lopez" de "UTN FRVM"
    When el alumno busca "Carlos"
    Then no hay resultados

  Scenario: Editar perfil mantiene la institución
    Given un tutor de "UTN FRVM" sin materias registradas
    When cambia su carrera a otra carrera de "UTN FRVM"
    Then el cambio se acepta
    And al intentar una carrera que existe solo en "UNVM" recibe 404 "CAREER_NOT_FOUND"

  Scenario: Las respuestas de perfil propio informan institución y carrera por id
    When un tutor consulta GET /api/v1/tutors/me/profile
    Then la respuesta incluye careerId, institutionId e institution
```

## 11. Advertencias de dependencia (leer antes de implementar)

1. **Orden de despliegue.** `main` se despliega solo a Render. Ejecutar `migration-institutions-catalog.sql` en la base de producción **antes** de mergear. Si `DDL_AUTO=update` en producción, Hibernate intentaría agregar `institution_id NOT NULL` sobre filas existentes y fallaría. Confirmar el valor real de `DDL_AUTO` en Render (no figura en `render.yaml`; en `docker-compose.yml` local es `update`).
2. **Contrato en dos repos.** El frontend envía `institutionId` en el registro y consume los campos nuevos de la sección 6. Mergear backend primero, o proteger el frontend hasta que el backend esté desplegado.
3. **Path JPA por string** `"isBasic"` en `CommunityPostSpecifications` (ver 3.4): no lo detecta el compilador; buscar con `grep -rn '"isBasic"' src/main`.
4. **`findByNameAndCareerOrThrowException` crea materias.** Hoy un tutor puede introducir un nombre inexistente y se crea una `Subject` nueva bajo su carrera. Se conserva (fuera de alcance), pero tener presente que contradice la idea de "catálogo cargado solo por el equipo".

## 12. No hacer

- No crear ABM de instituciones, carreras ni materias (alta autoservicio, endpoints admin): la carga del MVP es por seeder/SQL.
- No identificar la carrera compartida por nombre en ninguna consulta ni validación: siempre `type = SHARED`.
- No exponer la carrera `SHARED` en `GET /careers` ni permitirla en el registro/edición de perfil.
- No implementar relación N-a-N Materia–Carrera.
- No agregar un parámetro de institución a `GET /tutors/search/{query}`: sale del usuario autenticado.
- No permitir cambiar la institución desde `PUT .../profile`.
- No borrar la columna `is_basic` en este PR.
- No usar `findByName` global de `Career`/`Subject` (se elimina para evitar cruces).
- No cambiar el comportamiento *get-or-create* de `SubjectServiceImpl` (solo su alcance por institución).
- No agregar verificación por dominio de correo ni rol de admin institucional (fuera del MVP).
- No cambiar `SessionClaim`, `Booking`, materiales ni pagos.

## 13. Fuera de alcance — decisiones abiertas para el equipo

- **Acceso cruzado por URL/ID:** el perfil público de un tutor y la reserva por `tutorSubjectId` no verifican institución; solo la búsqueda y los catálogos están acotados.
- **Visibilidad del feed de comunidad:** el spec acota los catálogos de comunidad (carreras/materias), pero no decide si los posts de una institución son visibles para la otra.
- **Una materia en dos carreras (no todas):** con relación 1-N debe vivir en `SHARED` o duplicarse por carrera. Si el equipo la necesita, evaluar N-a-N en una iteración posterior.
- **Cambio de institución de un usuario:** no soportado en el MVP.

## 14. Definition of Done

- [ ] `Institution`, `CareerType` creados; `Career` y `Subject` con institución, `UNIQUE` compuestos y sin `isBasic` persistente
- [ ] Repositorios y services sin `findByName` global; `ICareerService`/`ISubjectService` actualizados
- [ ] `GET /institutions` (público, en `SecurityConstants`), `/careers?institutionId`, `/subjects/basic?institutionId`, `/community/careers`, `/community/subjects` con Swagger en español (`@Operation`, `@ApiResponse`, `@Tag`)
- [ ] Registro de alumno y tutor con `institutionId`; edición de perfil dentro de la institución actual
- [ ] Respuestas `Self` con `careerId`, `institutionId`, `institution`
- [ ] Búsqueda acotada por institución (rama materia y rama nombre de tutor)
- [ ] `CommunityPostSpecifications.inCareer` reescrito (sin `"isBasic"` string)
- [ ] Seeders idempotentes por institución (UTN FRVM conservada + UNVM mínima), sin carrera placeholder
- [ ] `migration-institutions-catalog.sql` idempotente, probado sobre una copia con datos reales y con una base vacía
- [ ] `CatalogFixtures` creada y tests existentes migrados; `./mvnw verify` en verde
- [ ] Tests nuevos de la sección 9
- [ ] Revisión humana de la migración antes de ejecutarla en producción
