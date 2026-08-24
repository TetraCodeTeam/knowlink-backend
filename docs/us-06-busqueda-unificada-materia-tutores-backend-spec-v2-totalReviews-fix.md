# US-06 · Búsqueda Unificada — SPEC BACKEND (fix puntual: `totalReviews` hardcodeado)

**Repo:** knowlink-backend · branch base: `Julian-SalvucciV3`
**Alcance:** bugfix acotado, no requiere nuevo endpoint ni cambio de contrato (`TutorSearchResponse` no cambia de forma).

---

## 0. Contexto

Al auditar `/api/v1/tutors/search/{query}` para US-06 se confirmó que el match por nombre de materia **y** por nombre de tutor ya está bien implementado en `TutorProfileServiceImpl#searchTutor`. El único problema real encontrado es de datos, en el mapeo de la respuesta.

## 1. Bug

**Archivo:** `src/main/java/com/knowlink/api/tutors/data/mappers/TutorSearchMapper.java`

```java
public static TutorSearchResponse from(List<TutorSubject> TutorSubjectList) {
    ...
    return new TutorSearchResponse(
            tutorProfile.getUser().getUserId(),
            tutorProfile.getUser().getFullName(),
            tutorProfile.getProfilePictureUrl(),
            tutorProfile.getAverageRating(),
            4, // o getTotalReviews() según tu entidad     <-- BUG
            TutorSubjectList.stream()
                    .map(mt -> new SubjectSummary(
                            mt.getSubject().getName(),
                            mt.getSubject().getCareer().getName()))
                    .distinct()
                    .sorted(Comparator.comparing(SubjectSummary::name))
                    .toList()
    );
}
```

`totalReviews` está fijo en `4` para **todos** los tutores devueltos por el buscador, sin importar cuántas reseñas visibles tenga cada uno realmente. Impacto: en el dropdown de búsqueda y en cualquier pantalla que consuma este endpoint, todas las cards de tutores muestran `(4 Reseñas)`.

`TutorProfileServiceImpl` ya resuelve esto correctamente para el perfil completo del tutor, usando:

```java
ratingRepository.findVisibleByRatedUserId(tutorUserId) // List<Rating>
```

Ahí el conteo real sale de `.size()` sobre esa lista (mapeada a `TutorReviewResponse` para el detalle, pero el conteo es el mismo dato).

---

## 2. Causa raíz

`TutorSearchMapper.from(...)` es un método **estático** sin acceso a ningún repositorio — no tiene forma de consultar la cantidad real de reseñas sin recibirla como parámetro o sin dejar de ser estático. Por eso quedó hardcodeado.

---

## 3. Fix propuesto

### Opción A (recomendada) — pasar el conteo ya resuelto desde el service

Dejar `TutorSearchMapper` estático, pero que reciba el conteo de reseñas ya calculado por tutor, en vez de resolverlo él mismo. Evita convertir el mapper en un bean con dependencias solo por este dato.

**`TutorSearchMapper.java`:**

```java
public static TutorSearchResponse from(List<TutorSubject> tutorSubjectList, int totalReviews) {

    if (tutorSubjectList == null || tutorSubjectList.isEmpty()) {
        throw new IllegalArgumentException("La lista de materias del tutor no puede estar vacía.");
    }

    TutorProfile tutorProfile = tutorSubjectList.get(0).getTutorProfile();

    return new TutorSearchResponse(
            tutorProfile.getUser().getUserId(),
            tutorProfile.getUser().getFullName(),
            tutorProfile.getProfilePictureUrl(),
            tutorProfile.getAverageRating(),
            totalReviews,
            tutorSubjectList.stream()
                    .map(mt -> new SubjectSummary(
                            mt.getSubject().getName(),
                            mt.getSubject().getCareer().getName()))
                    .distinct()
                    .sorted(Comparator.comparing(SubjectSummary::name))
                    .toList()
    );
}
```

**`TutorProfileServiceImpl.searchTutor(...)`** — al armar la respuesta final, resolver el conteo por cada grupo de `TutorSubject` antes de mapear:

```java
return groupedResults.values()
        .stream()
        .map(subjects -> {
            UUID tutorUserId = subjects.get(0).getTutorProfile().getUser().getUserId();
            int totalReviews = ratingRepository.findVisibleByRatedUserId(tutorUserId).size();
            return TutorSearchMapper.from(subjects, totalReviews);
        })
        .toList();
```

`ratingRepository` (`IRatingRepository`) ya está inyectado en `TutorProfileServiceImpl` (se usa en `getTutorProfile`), así que no hace falta agregar ninguna dependencia nueva a la clase.

### Opción B (alternativa, no recomendada por ahora)

Traer el conteo con una query agregada (`COUNT`) en una sola pasada para todos los tutores del resultado, en vez de una consulta por tutor dentro del `.map(...)`. Más performante si `groupedResults` puede tener muchos tutores, pero requiere un método nuevo en `IRatingRepository` (`countVisibleByRatedUserIdIn(List<UUID> userIds)` devolviendo un `Map<UUID, Long>`) y es más cambio para un bug que hoy no tiene evidencia de ser un problema de performance. **Dejar como nota para si el dataset de tutores crece y esto se vuelve N+1 relevante.**

---

## 4. Testing

- **Unit test de `TutorSearchMapper`:** actualizar la firma existente si hay tests directos del mapper (agregar el nuevo parámetro `totalReviews` y verificar que se propaga tal cual al `TutorSearchResponse`).
- **Test de integración (`TutorSearchControllerTest`):** agregar un caso donde un tutor sembrado tenga una cantidad conocida de `Rating` visibles (ej. 2) y otro tenga 0, y verificar en la respuesta JSON:
  ```java
  .andExpect(jsonPath("$[?(@.fullName == 'Ana García')].totalReviews").value(2))
  .andExpect(jsonPath("$[?(@.fullName == 'Carlos López')].totalReviews").value(0))
  ```
- Confirmar que el caso ya cubierto en el test existente (tutores sin reseñas) no rompe con el cambio — hoy probablemente no verifica `totalReviews` porque no importaba (estaba hardcodeado), revisar si hay asserts sobre ese campo que haya que actualizar.

---

## 5. Fuera de alcance

- No se toca `subjectNameContains`, `findByUser_FullNameContainingIgnoreCase`, ni ningún filtro de `TutorSearchFilters` — ya funcionan según lo verificado.
- No se agrega paginación ni cache de conteo de reseñas — si el volumen de tutores por búsqueda crece mucho, evaluar la Opción B más adelante.
