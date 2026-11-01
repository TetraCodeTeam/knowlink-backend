# SPEC — US-49: Filtrar resultados de búsqueda — BACKEND

> **Uso:** pasar este archivo completo a OpenCode (DeepSeek V4 Flash) parado en `TetraCodeTeam/knowlink-backend`.
> **Reemplaza** a `docs/us-49-filtrar-busqueda-backend.spec.md` (versión anterior, escrita antes de que existiera el módulo de búsqueda). Guardarlo con ese mismo nombre.
> **Rama de trabajo:** crear `feat/us-49-search-filters` desde `dev`.
> **Verificado contra:** `knowlink-backend@dev` (commit `be6ab65`, 2026-10-31).

## Metadata

| Campo | Valor |
|---|---|
| ID | US-49 |
| Módulo | búsqueda (código en `com.knowlink.api.tutors`, no hay módulo `busqueda`) |
| Story Points | 5 (esta parte backend es chica: son brechas sobre código ya existente) |
| Prioridad | MEDIA |
| Tags | #RN-06 #RN-07 |
| Relacionadas | US-06A (búsqueda base), US-07 (perfil tutor), US-22/US-25 (validación de materia), US-08 (calificaciones) |
| Spec hermano | `us-49-filtrar-busqueda-frontend.spec.md` (repo `knowlink-frontend`) |

## Historia

Como alumno, quiero refinar los resultados de búsqueda usando filtros, para acotar las opciones según mis preferencias y disponibilidad.

## Criterios de aceptación (Gherkin) — alcance backend

```gherkin
Feature: Filtrar resultados de búsqueda de tutores

  Scenario: AC-1 — Filtros disponibles
    Given un alumno autenticado que busca "Sistemas"
    Then puede enviar en el mismo request cualquier subconjunto de estos filtros:
      | modality | VIRTUAL | IN_PERSON | BOTH |
      | compensation | FREE | PAID |
      | daysOfWeek | uno o más días (MONDAY..SUNDAY) |
      | timeFrame | MORNING | AFTERNOON | EVENING |
      | verifiedOnly | true |
      | minRating | 1 a 5 |

  Scenario: AC-1 — "Tutor verificado" es por materia buscada
    Given el tutor A con la materia "Sistemas Operativos" en estado de validación ACTIVE
    And el tutor B con la materia "Sistemas Operativos" en estado PENDING o REJECTED
    When el alumno busca "Sistemas Operativos" con verifiedOnly=true
    Then solo aparece el tutor A

  Scenario: AC-2 — Todos los filtros se combinan con AND
    Given filtros modality=VIRTUAL, compensation=PAID y minRating=4
    Then solo aparecen tutores que cumplen los tres a la vez
    And dentro de un mismo filtro de varios valores (daysOfWeek) los valores se combinan con OR

  Scenario: AC-2 — Disponibilidad día + franja sobre el mismo bloque
    Given filtros daysOfWeek=TUESDAY y timeFrame=MORNING
    Then el tutor aparece solo si tiene UN bloque disponible y futuro que sea martes Y se solape con la mañana
    And no alcanza con un bloque de martes a la tarde más otro de lunes a la mañana

  Scenario: AC-4 — Sin filtros equivale a la búsqueda base
    Given un request sin ningún parámetro de filtro
    Then la respuesta es idéntica a la de US-06A

  Scenario: AC-5 — Filtros sin match
    Given una búsqueda base con resultados
    When los filtros no matchean a ningún tutor
    Then responde 200 con lista vacía []
    And el frontend decide el mensaje (ver spec frontend); no se agrega envoltorio ni campo nuevo
```

> AC-3 (filtros destacados) y AC-4 (quitar / limpiar filtros) son de UI. El backend solo debe aceptar cualquier subconjunto de filtros, incluido ninguno.

---

## Estado actual del código (verificado en `dev`) — NO rehacer lo que ya existe

Ya implementado, funcionando y con tests; **no tocar salvo lo indicado en "Cambios requeridos"**:

| Pieza | Archivo |
|---|---|
| Endpoint `GET /api/v1/tutors/search/{query}` con `@PreAuthorize("hasRole('STUDENT')")` | `tutors/controllers/interfaces/ITutorController.java` · `implementations/TutorControllerImpl.java` |
| Record de filtros | `tutors/data/models/TutorSearchFilters.java` |
| Specifications JPA (`subjectNameContains`, `subjectActive`, `modalityIn`, `compensationIn`, `tutorVerified`, `minAverageRating`) y helpers en memoria (`matchesModality`, `matchesCompensation`, `matchesAvailability`, `timingOverlaps`) | `tutors/data/specifications/TutorSearchSpecifications.java` |
| Orquestación | `TutorProfileServiceImpl#searchTutor(String, UUID, TutorSearchFilters)` (~línea 193) |
| Enum de franjas (MORNING 6-12, AFTERNOON 12-18, EVENING 18-23:59) | `tutors/data/enums/TimeFrame.java` |
| Validación `minRating` fuera de 1–5 → `ValidationException` → 400 | `TutorProfileServiceImpl#validateMinRating` |
| Enum inválido en query param → 400 `VALIDATION_ERROR` | `GlobalExceptionHandler#handleTypeMismatch` |
| Semántica de modalidad: `VIRTUAL`/`IN_PERSON` también matchean materias `BOTH`; `BOTH` solo matchea `BOTH` | `TutorSearchSpecifications#modalityIn` |
| Calificación: usa `TutorProfile.averageRating`, la misma columna que mantiene `RatingReputationServiceImpl` y que muestra el perfil | — |
| Tests | `TutorSearchFilterControllerTest`, `TutorSearchSpecificationsTest`, `TutorSearchControllerTest`, `TutorProfileServiceImplTest` |

Convenciones del repo a respetar (`AGENTS.md`, `docs/base-standards.md`): código en **inglés**; mensajes al usuario, Swagger y `@DisplayName` en **español**; inyección por constructor; sin lógica en controllers; `@Operation`/`@ApiResponse` actualizados; `./mvnw` (nunca `mvn`); commits `feat:`/`fix:`/`test:` en español.

---

## Brechas a resolver (lo que SÍ hay que hacer)

### B1 — "Tutor verificado" debe ser por materia (corrige un bug de criterio) · PRIORIDAD ALTA

**Problema:** hoy `verifiedOnly` filtra por `TutorProfile.verified`, un booleano **global** que ningún código de producción escribe (solo lo setea `seed-tutor-profiles.sql`). El AC-1 pide "insignia de validación académica **en la materia buscada**".

**Fuente de verdad correcta:** `TutorSubject.tutorSubjectStatus == TutorSubjectStatus.ACTIVE` (estado de validación US-25/US-22; ver `docs/us-12-desactivar-reactivar-materia-backend.spec.md`, sección "estado de validación"). No confundir con `TutorSubject.active` (visibilidad, US-12), que es otro flag.

Cambios:
1. `TutorSearchSpecifications`: reemplazar `tutorVerified()` por `subjectValidated()` → `cb.equal(root.get("tutorSubjectStatus"), TutorSubjectStatus.ACTIVE)`. Actualizar el Javadoc de la clase (hoy dice que verified se evalúa sobre el tutor).
2. `searchTutor`: en la rama de materias, usar `subjectValidated()` cuando `verifiedOnly == true`.
3. `searchTutor`, rama de **coincidencia por nombre de tutor** (`nameMatches`): con `verifiedOnly == true`, quedarse solo con las `TutorSubject` activas **y** con `tutorSubjectStatus == ACTIVE`; si no queda ninguna, `continue`. Reemplaza el check `tutorProfile.isVerified()`.
4. No borrar ni modificar `TutorProfile.verified`, su columna ni el seed (otras pantallas lo exponen). Solo dejar de usarlo para buscar.
5. Tests existentes que siembran `verified` (`TutorSearchFilterControllerTest#tutor(...)`, `TutorSearchSpecificationsTest#seedData`): sembrar en su lugar `tutorSubjectStatus` (`ACTIVE` = verificado, `PENDING` = no verificado) y renombrar el test `tutorVerified_...` → `subjectValidated_...`.

> ⚠️ Advertencia (no corregir acá, solo reportar al final en el resumen): `MaterialServiceImpl#findOrCreateTutorSubject` (~línea 258) crea `TutorSubject` nuevos directamente en `ACTIVE`, lo que los haría "verificados" sin pasar por validación. Es un problema preexistente fuera del alcance de US-49.

### B2 — Disponibilidad: varios días + franja horaria (hoy solo 1 día y sin franja)

El diseño de UI permite elegir **varios días** (chips L M X J V S D) y el AC-1 pide "día de la semana **o franja horaria**". Hoy el endpoint solo expone `dayOfWeek` (un valor) y el servicio llama `matchesAvailability(block, day, null)` (nunca usa la franja aunque `TimeFrame` existe).

Cambios:
1. `TutorSearchFilters`: reemplazar `DayOfWeek dayOfWeek` por `Set<DayOfWeek> daysOfWeek` y agregar `TimeFrame timeFrame`. Orden final del record: `modality, compensation, daysOfWeek, timeFrame, verifiedOnly, minRating`. `hasAvailability()` = `(daysOfWeek != null && !daysOfWeek.isEmpty()) || timeFrame != null`. Normalizar `null` → `Set.of()` en un compact constructor.
2. `ITutorController#searchTutor` / `TutorControllerImpl`: reemplazar `@RequestParam DayOfWeek dayOfWeek` por `@RequestParam(required = false) List<DayOfWeek> daysOfWeek` y agregar `@RequestParam(required = false) TimeFrame timeFrame`. Spring acepta `?daysOfWeek=TUESDAY&daysOfWeek=WEDNESDAY` y `?daysOfWeek=TUESDAY,WEDNESDAY`. Convertir a `Set` (`EnumSet`/`LinkedHashSet`) al armar `TutorSearchFilters`. Actualizar `@Parameter(description=...)` en español.
3. `TutorSearchSpecifications#matchesAvailability(block, Set<DayOfWeek> days, TimeFrame frame)`: día ∈ `days` (si `days` no está vacío) **y** franja solapada (si `frame != null`), **evaluado sobre el mismo bloque**. Mantener `timingOverlaps` sin cambios.
4. Solo cuentan bloques **futuros y libres**. Hoy `findAvailableByTutorProfileId` trae también bloques pasados. Agregar en `IAvailabilityBlockRepository`:
   ```java
   @Query("""
       SELECT ab FROM AvailabilityBlock ab
       WHERE ab.tutorProfile.tutorProfileId IN :tutorProfileIds
         AND ab.available = true
         AND (ab.date > :today OR (ab.date = :today AND ab.endTime > :nowTime))
       """)
   List<AvailabilityBlock> findUpcomingAvailableByTutorProfileIds(
           @Param("tutorProfileIds") Collection<UUID> tutorProfileIds,
           @Param("today") LocalDate today,
           @Param("nowTime") LocalTime nowTime);
   ```
   Reutiliza el mismo criterio de "futuro" que `findInRange` (ya existente en ese repo).
5. **Evitar N+1:** hoy `tutorHasAvailability` hace una query por tutor. En `searchTutor`, cuando `filters.hasAvailability()`, juntar primero los `tutorProfileId` candidatos, hacer **una sola** llamada a `findUpcomingAvailableByTutorProfileIds`, agrupar por `tutorProfileId` y consultar ese mapa. Borrar `tutorHasAvailability`.
6. Fecha/hora actual: usar el bean `java.time.Clock` si ya está definido como `@Bean` (lo usan `AdminMaterialReportServiceImpl` y `MaterialReportIntakeServiceImpl`; verificar con `grep -rn "Clock" src/main/java/**/config`). Inyectarlo en `TutorProfileServiceImpl` por constructor. Si **no** hay bean, no crear uno global: usar `LocalDate.now()`/`LocalTime.now()` y dejar un comentario.

### B3 — Compatibilidad
Ningún cliente consume todavía `dayOfWeek` (el frontend no tiene filtros), por lo que se **reemplaza** sin dejar alias. Buscar con `grep -rn "dayOfWeek" src/` y actualizar todas las referencias (tests incluidos).

---

## Contrato final del endpoint

```
GET /api/v1/tutors/search/{query}
    ?modality=VIRTUAL|IN_PERSON|BOTH
    &compensation=FREE|PAID
    &daysOfWeek=MONDAY&daysOfWeek=TUESDAY        (o MONDAY,TUESDAY)
    &timeFrame=MORNING|AFTERNOON|EVENING
    &verifiedOnly=true
    &minRating=4
Auth: Bearer JWT, rol STUDENT
200 -> TutorSearchResponse[]   (sin cambios en el shape; [] si no hay match)
400 -> { status, message, detail:"VALIDATION_ERROR" }  (enum inválido o minRating fuera de 1–5)
403 -> rol no STUDENT
```

`TutorSearchResponse` **no cambia** (`tutorId, fullName, photoProfile, averageRating, totalReviews, subjects[{name, career}]`).

Decisión AC-5 confirmada: sin envoltorio ni `filtersApplied`; el frontend conoce qué filtros envió.

## Tests (perfil `test`, H2)

Actualizar/agregar en los tests existentes (no crear clases nuevas salvo que haga falta):

- `TutorSearchSpecificationsTest`
  - `subjectValidated` devuelve solo materias con `tutorSubjectStatus = ACTIVE` (PENDING y REJECTED excluidas).
  - `matchesAvailability` con varios días (OR entre días) y con día+franja sobre el mismo bloque; caso negativo: día correcto en bloque A y franja correcta en bloque B → `false` para cada bloque por separado.
- `TutorSearchFilterControllerTest`
  - `verifiedOnly=true` busca "X": tutor con materia X ACTIVE aparece; tutor con X PENDING no; tutor con ACTIVE en **otra** materia pero PENDING en X **no** aparece (clave del criterio "en la materia buscada").
  - `verifiedOnly=true` en la búsqueda por **nombre de tutor**: respeta el estado de validación.
  - `daysOfWeek=TUESDAY&daysOfWeek=WEDNESDAY` devuelve tutores de cualquiera de los dos días; `daysOfWeek=TUESDAY,WEDNESDAY` (forma con coma) da el mismo resultado.
  - `timeFrame=MORNING` solo; `daysOfWeek` + `timeFrame` combinados (mismo bloque).
  - Un bloque disponible **pasado** no hace aparecer al tutor; un bloque `available=false` tampoco.
  - AND completo: modalidad + compensación + verificado + minRating + día.
  - `daysOfWeek=FUNDAY` → 400; `timeFrame=NIGHT` → 400.
  - Regresión: sin filtros, mismo resultado que antes.
- `TutorProfileServiceImplTest`: si mockea `IAvailabilityBlockRepository`, adaptar a `findUpcomingAvailableByTutorProfileIds` y verificar que se invoca **una sola vez** por búsqueda (anti N+1).

Comando: `./mvnw test -Dtest='TutorSearch*Test,TutorProfileServiceImplTest'` y al final `./mvnw verify -B` completo.

## No hacer

- No crear un módulo `busqueda/` ni mover código de `tutors/`.
- No agregar envoltorio/`filtersApplied` a la respuesta.
- No agregar paginación (no está en US-49 ni en US-06A actual).
- No tocar `TutorProfile.verified`, `seed-tutor-profiles.sql`, `TutorSubject.active`, ni el flujo de validación (US-22/US-25).
- No tocar `MaterialServiceImpl` (solo reportar la advertencia de B1).
- No refactorizar `searchTutor` más allá de lo necesario para B1/B2 (es largo, pero fuera de alcance reestructurarlo).
- No construir queries con concatenación de strings; seguir con `Specification`.
- No modificar `knowlink-frontend`.
- No agregar dependencias al `pom.xml`.

## Definition of Done

- [ ] `verifiedOnly` filtra por `tutorSubjectStatus == ACTIVE` de la materia buscada, en ambas ramas (materia y nombre de tutor).
- [ ] `daysOfWeek` (lista, OR) y `timeFrame` funcionan y se combinan sobre el mismo bloque; solo bloques futuros y libres.
- [ ] Una sola query de disponibilidad por búsqueda (sin N+1).
- [ ] `dayOfWeek` eliminado en código, Swagger y tests; sin referencias colgadas.
- [ ] `@Operation`/`@Parameter` actualizados en español.
- [ ] Sin filtros → resultado idéntico al previo (test de regresión verde).
- [ ] `./mvnw verify -B` pasa completo.
- [ ] Resumen final de OpenCode incluye la advertencia sobre `MaterialServiceImpl#findOrCreateTutorSubject`.
