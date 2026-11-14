# US-06 · Búsqueda unificada — Ajustes de QA (SPEC BACKEND)

## 0. Metadata

| Campo | Valor |
|---|---|
| Historia | US-06 (Búsqueda unificada), ajustes surgidos de QA |
| Módulo | `tutors` (búsqueda) |
| Repo / rama base | `TetraCodeTeam/knowlink-backend` · `dev` |
| Ejecuta | OpenCode + DeepSeek V4 Flash |
| Spec par | `us-06-ajustes-busqueda-frontend.spec.md` (el frontend depende de este) |
| Tipo | Corrección + 1 endpoint nuevo de solo lectura. Sin cambios de esquema SQL. |

## 1. Contexto y causa raíz (verificada sobre `dev`)

Observaciones de QA con la base vacía de tutores:

1. **Una materia sin tutores no aparece en la búsqueda.** Hoy el único endpoint es `GET /api/v1/tutors/search/{query}`, que devuelve solo `TutorSearchResponse[]`. El frontend deriva las "materias" desde los tutores devueltos (`deriveMateriasFromTutors`). Si ningún tutor dicta la materia, no existe en la respuesta. El backend **sí** tiene el catálogo (`subjects`, entidad `Subject`, repo `ISubjectRepository`), pero no lo expone para búsqueda.
2. **Los resultados no están ordenados.** En `TutorProfileServiceImpl#searchTutor` los resultados se acumulan en un `LinkedHashMap` (primero los tutores que matchean por materia, en el orden que devuelva la base, y después los que matchean por nombre). No hay ningún `ORDER BY` ni sort. Por eso, buscando "a", sale Claudia antes que Ana.

## 2. Decisiones confirmadas

- Una materia del catálogo debe aparecer en las sugerencias aunque **no tenga tutores**.
- El alcance es la **institución del alumno** (mismo criterio que `searchTutor`: `userProfileLookupService.findInstitutionIdByUserId`).
- Se incluyen materias de **todas las carreras** de esa institución (incluida la carrera compartida), igual que el match de tutores por materia.
- Orden de resultados (tutores y materias): **alfabético**, con prioridad a los que **empiezan** con el texto buscado (ver §4). Es una decisión de producto propuesta; si se prefiere alfabético puro, se elimina el ranking y queda solo el desempate.
- El contrato de `GET /api/v1/tutors/search/{query}` **no cambia de forma**: solo cambia el orden de la lista.

## 3. Criterios de aceptación (Gherkin)

```gherkin
Escenario: Una materia sin tutores aparece al buscar
  Dado un alumno de la institución I
  Y la materia "Álgebra" existe en I y ningún tutor la dicta
  Cuando busca materias con el texto "alg"
  Entonces la respuesta incluye { name: "Álgebra", career: <carrera> }

Escenario: La búsqueda de materias se limita a la institución del alumno
  Dado una materia "Álgebra" en la institución J distinta de la del alumno
  Cuando el alumno busca "alg"
  Entonces la materia de J no aparece

Escenario: La búsqueda de materias ignora mayúsculas y acentos
  Cuando el alumno busca "algebra"
  Entonces aparece "Álgebra"

Escenario: Materias ordenadas con prioridad a las que empiezan con el texto
  Dado las materias "Base de Datos", "Álgebra", "Análisis", "Cálculo" en la institución
  Cuando el alumno busca "a"
  Entonces el orden es "Álgebra", "Análisis", "Base de Datos", "Cálculo"
  # primero las que empiezan con "a" (alfabético), luego las que solo lo contienen (alfabético)

Escenario: Tutores ordenados alfabéticamente con prioridad a los que empiezan con el texto
  Dado los tutores "Claudia Pereyra", "Elena Ruiz", "Agustina Díaz", "Ana López"
  Cuando el alumno busca "a" en /api/v1/tutors/search/a
  Entonces el orden es "Agustina Díaz", "Ana López", "Claudia Pereyra", "Elena Ruiz"

Escenario: Un tutor cuyo apellido empieza con el texto va antes que uno que solo lo contiene
  Dado "Claudia Pereyra" y "Mariana Soto"
  Cuando busca "per"
  Entonces "Claudia Pereyra" va antes que cualquier tutor que solo contenga "per" dentro de una palabra

Escenario: Tutores que matchean solo por materia
  Cuando busca "alg" y los tutores matchean únicamente por la materia
  Entonces van después de los que matchean por nombre y entre sí en orden alfabético por nombre completo

Escenario: Sin filtros ni materias, el contrato no cambia
  Cuando se llama a /api/v1/tutors/search/{query} sin filtros
  Entonces la respuesta tiene la misma forma que antes (solo cambia el orden)
```

## 4. Regla de orden (única, compartida)

Normalizar texto = minúsculas + quitar diacríticos (`java.text.Normalizer` NFD + eliminar `\p{M}`) + `trim`.

Ranking de un nombre contra la query normalizada:

| Rank | Condición |
|---|---|
| 0 | el nombre normalizado **empieza** con la query |
| 1 | alguna **otra palabra** del nombre empieza con la query (ej. apellido) |
| 2 | la query está **contenida** en el nombre pero no al inicio de una palabra |
| 3 | el nombre no contiene la query (el tutor matcheó solo por materia) |

Orden final: `rank` ascendente y, dentro del mismo rank, nombre con `Collator` de `new Locale("es", "AR")` con `Collator.PRIMARY` (insensible a acentos y mayúsculas). Desempate estable final: `userId` / `subjectId` para que el orden sea determinístico.

- Para tutores se rankea `fullName`. Para materias, `name`.
- Una sola clase utilitaria `final` con constructor privado (patrón de `TutorSearchSpecifications`), con dos métodos públicos: `rank(String name, String query)` y `comparator(String query, Function<T,String> nameExtractor)`.

## 5. Cambios por capa

### 5.1 Utilitario de ranking (nuevo)
- Ubicación: `com.knowlink.api.tutors.utils` (o el paquete utilitario ya existente en el módulo; verificar en `docs/base-standards.md`). Nombre sugerido: `SearchRanking`.
- Sin estado, sin dependencias de Spring.

### 5.2 `TutorProfileServiceImpl#searchTutor` (modificar)
- Después de armar `groupedResults` y aplicar disponibilidad, y antes de mapear a `TutorSearchResponse`, **ordenar** los valores con el comparador del §4 usando el `fullName` del tutor.
- Para eso, recorrer `groupedResults.values()` y ordenar la lista resultante (o ordenar la lista ya mapeada por `TutorSearchResponse::fullName`). Preferir ordenar **antes** del `countVisibleTutorRatings` para no consultar de más; si se ordena después de mapear, usar `TutorSearchResponse.fullName()`.
- **No** tocar el filtrado, la disponibilidad ni `validateMinRating`.

### 5.3 Búsqueda de materias del catálogo (nuevo)

**Repositorio** — `ISubjectRepository`:

```java
@Query("""
        SELECT s FROM Subject s
        LEFT JOIN FETCH s.career
        WHERE s.institution.institutionId = :institutionId
          AND LOWER(s.name) LIKE LOWER(CONCAT('%', :query, '%'))
        """)
List<Subject> searchByInstitutionAndNameContaining(
        @Param("institutionId") UUID institutionId, @Param("query") String query);
```

- `LEFT JOIN FETCH` a propósito: `subjects.career_id` no tiene FK y `open-in-view` está deshabilitado (ver comentario en `TutorSearchMapper#careerName`). Una carrera huérfana no debe romper la búsqueda ni excluir la materia.
- Mismo criterio de matching que `TutorSearchSpecifications.subjectNameContains` (contains, case-insensitive). No agregar manejo de `%`/`_` distinto del existente.
- El orden lo aplica el service (§4), no el `ORDER BY`.

**Servicio** — `ISubjectService` / `SubjectServiceImpl`:

```java
List<SubjectSummary> searchForStudent(UUID studentUserId, String query);
```

- `@Transactional(readOnly = true)`.
- Resolver la institución con `IUserProfileLookupService#findInstitutionIdByUserId`; si no existe, lanzar `ResourceNotFoundException("STUDENT_PROFILE_NOT_FOUND", "No encontramos tu perfil de alumno.", ...)` con **el mismo código y mensaje** que `searchTutor`.
- Mapear a `com.knowlink.api.tutors.data.models.SubjectSummary(name, career)` (record existente, ya usado por `TutorSearchResponse`). `career` = nombre de la carrera o `""` si es null.
- Aplicar el orden del §4.
- Inyectar `IUserProfileLookupService` por constructor (`@RequiredArgsConstructor`). Verificar que no cree dependencia circular.

**Controller** — `ISubjectController` + `SubjectControllerImpl`:

```java
@GetMapping("/search/{query}")
@Operation(summary = "Buscar materias del catálogo",
        description = "Devuelve las materias de la institución del alumno cuyo nombre contiene el texto buscado, "
                + "tengan o no tutores. Orden alfabético con prioridad a las que empiezan con el texto.")
@ApiResponse(responseCode = "200", description = "Materias encontradas (lista vacía si no hay)")
@ApiResponse(responseCode = "403", description = "Acceso denegado")
@ApiResponse(responseCode = "404", description = "Perfil de alumno no encontrado")
@ResponseStatus(OK)
@PreAuthorize("hasRole('STUDENT')")
List<SubjectSummary> searchSubjects(
        @Parameter(description = "Texto a buscar en el nombre de la materia") @PathVariable String query,
        @Parameter(hidden = true) @AuthenticationPrincipal UserPrincipal principal);
```

- Los otros endpoints de `ISubjectController` son públicos; este es **protegido** (`/api/v1/**` con rol STUDENT) porque necesita la institución del alumno. Confirmar en la config de seguridad que `/api/v1/subjects/search/**` no queda en `permitAll` por un patrón más amplio.
- Respuesta: `List<SubjectSummary>` directa, sin envoltorio. Mismo estilo que `/tutors/search`.

## 6. Dependencias y advertencias

- **No** cambiar la firma ni el JSON de `TutorSearchResponse` / `SubjectSummary`.
- `ISubjectController` está mapeado en `/api/v1/subjects`. La ruta nueva `/search/{query}` no colisiona con `@GetMapping` (sin path) ni con `/basic`.
- Los tests existentes de búsqueda (`TutorSearchControllerTest`, `TutorSearchFilterControllerTest`, `TutorSearchInstitutionIsolationTest`, `TutorProfileServiceImplTest`) pueden asumir el orden de inserción. Revisarlos: si alguno depende de ese orden, ajustar la expectativa al orden nuevo (no relajar la aserción a "contiene").
- US-49 (filtros) sigue igual: los filtros aplican solo a tutores. La búsqueda de materias del catálogo **no** recibe filtros.

## 7. Tests requeridos

Siguiendo la convención de los tests actuales de `tutors` (H2, MockMvc donde ya se use):

1. `SubjectServiceImplTest`: materia sin tutores aparece; aislamiento por institución; case/accent-insensitive; orden con ranking; carrera null → `career = ""`; alumno sin perfil → `ResourceNotFoundException`.
2. `SubjectSearchControllerTest`: 200 con lista; 200 con `[]`; 403 sin rol STUDENT; 401 sin autenticar.
3. `SearchRankingTest` (unit): casos del §3 (a → Agustina, Ana, Claudia, Elena; acentos; apellido con rank 1; rank 3 al final).
4. `TutorProfileServiceImplTest`: orden de `searchTutor` para query "a"; los que matchean solo por materia van después y alfabéticos.

## 8. "No hacer"

- No crear una tabla, columna ni migración SQL.
- No agregar un campo `active` a `Subject` ni filtrar el catálogo por "tiene tutores".
- No mezclar materias dentro de `TutorSearchResponse` ni cambiar el contrato de `/tutors/search`.
- No ordenar con `ORDER BY` de SQL para el ranking (el collation de MySQL y H2 difiere y el ranking por palabra no se expresa bien en JPQL).
- No usar `.get()` sobre `Optional` (usar `orElseThrow`).
- No usar `System.out.println` (usar `@Slf4j`).
- No exponer entidades JPA en la respuesta.

## 9. Definition of Done

- [x] `GET /api/v1/subjects/search/{query}` implementado, documentado con `@Operation`/`@ApiResponse`, protegido con `hasRole('STUDENT')`.
- [x] Materias del catálogo sin tutores aparecen; aislamiento por institución verificado.
- [x] `searchTutor` devuelve los tutores ordenados según §4.
- [x] Utilitario de ranking compartido, con tests unitarios.
- [x] Tests de §7 en verde; tests previos de búsqueda ajustados solo en el orden esperado.
- [x] `./mvnw test` en verde.
- [x] Sin cambios en el JSON de `TutorSearchResponse`.
