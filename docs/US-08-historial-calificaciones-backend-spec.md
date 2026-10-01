# US-08 — Ver historial de calificaciones de tutor (Backend)

## Metadata

| Campo | Valor |
|---|---|
| ID | US-08 |
| Título | Ver historial de calificaciones de tutor |
| Sprint | — |
| Épica | — |
| Story Points | 3 |
| Prioridad | BAJA |
| Módulo | búsqueda |
| Tags | #RN-27 |
| Relacionadas | US-07 (perfil tutor), US-18 (calificar sesión), US-06B (filtro por calificación) |
| Rama sugerida | `feat/tutor-rating-history` |
| Alcance de este documento | Solo backend (`knowlink-backend`). El frontend va en un spec aparte. |

## Historia

Como alumno, quiero ver las calificaciones y comentarios que otros alumnos dejaron sobre un tutor, discriminados por materia, para tomar una decisión informada sobre con quién aprender.

## Stack confirmado

- Java 21 + Spring Boot 3.5
- MySQL 8
- Spring Data JPA
- Sin dependencias nuevas

## Advertencias de dependencias (verificar ANTES de implementar)

1. **US-18 (calificar sesión)** es quien genera los datos. Antes de escribir código, revisar en el repo si ya existe la entidad/tabla de calificaciones (puntaje + comentario + sesión). **No crear una entidad de calificación nueva si ya existe.** Si US-18 todavía no está mergeada, detenerse y avisar: este spec solo *lee* calificaciones.
   **RESUELTO:** la entidad `Rating` existe en `tutors/data/models/Rating.java` (con `booking`, `ratedUser`, `score`, `comment`, `ratingDate`, `visible`). US-18 aún no inserta datos (no hay endpoint POST de ratings), por lo que se **decidió implementar igual**: el endpoint es de solo lectura y hoy devuelve el estado vacío de CA4; empezará a mostrar datos cuando US-18 mergee. Sin duplicar la entidad.
2. La calificación debe poder llegar a **tutor** y **materia** a través de la sesión. Verificar que la entidad de sesión tenga relación con tutor y con materia (reutilizar el mismo criterio que usa US-26 con `subjectId`).
3. **US-06B / US-06** (búsqueda) ya calculan un promedio de calificación para filtrar y mostrar. Reutilizar la misma query/servicio de promedio si existe, para que el promedio del perfil y el de la búsqueda no diverjan. (Recordar que en la auditoría de US-06 se encontró `totalReviews` hardcodeado: no repetir ese error.)
4. **US-07 (perfil tutor)**: el endpoint nuevo se consume desde el perfil público; no modificar el endpoint de perfil existente.

## Criterios de aceptación (Gherkin)

```gherkin
Feature: Historial de calificaciones de un tutor

  Background:
    Given existe un tutor con sesiones completadas y calificadas en distintas materias

  Scenario: CA1 - Promedio de calificaciones desglosado por materia
    When un alumno consulta el historial de calificaciones del tutor
    Then la respuesta incluye una entrada por cada materia con calificaciones
    And cada entrada contiene subjectId, name, average y count
    And el promedio de cada materia se calcula solo con las calificaciones de esa materia

  Scenario: CA2 - Cada comentario asociado a su materia
    When un alumno consulta el historial de calificaciones del tutor
    Then cada comentario incluye el subjectId y el subjectName de la materia de la sesión en la que se realizó

  Scenario: CA3 - Comentarios ordenados del más reciente al más antiguo
    When un alumno consulta el historial de calificaciones del tutor
    Then los comentarios se devuelven ordenados por fecha de calificación descendente

  Scenario: CA4 - Tutor sin sesiones calificadas
    Given un tutor que aún no tiene sesiones completadas con calificación
    When un alumno consulta el historial de calificaciones del tutor
    Then la respuesta es 200 con promedios por materia vacíos, comentarios vacíos y totalRatings = 0
    And el mensaje "Aún sin calificaciones" lo renderiza el frontend a partir de ese estado

  Scenario: Tutor inexistente
    When se consulta el historial de un tutorId que no existe
    Then la respuesta es 404
```

## Contrato del endpoint

```
GET /api/v1/tutors/{tutorId}/ratings
```

> **Adaptado al código real (decidido en implementación):** prefijo `/api/v1`, segmento en inglés e IDs `UUID` (mismo ajuste que hizo US-26 con `materiaId Long` a `subjectId UUID`). `tutorId` es el `userId` del usuario con rol TUTOR, el mismo criterio que el perfil de US-07. Todos los identificadores son `UUID` y el contrato (clases, campos JSON y path) está en inglés.

Query params opcionales:

| Param | Tipo | Default | Descripción |
|---|---|---|---|
| `subjectId` | UUID | — | Filtra los comentarios a una materia (los promedios por materia se devuelven siempre completos) |
| `page` | int | 0 | Página de comentarios |
| `size` | int | 10 | Tamaño de página (máx. 50) |

Respuesta `200`:

```json
{
  "tutorId": "6f1c9d3e-1a2b-4c8d-9e0f-112233445566",
  "averageRating": 4.6,
  "totalRatings": 14,
  "subjects": [
    { "subjectId": "3b8f2a10-0000-4000-8000-000000000003", "name": "Análisis Matemático I", "average": 4.8, "count": 9 },
    { "subjectId": "7c1d4e20-0000-4000-8000-000000000007", "name": "Física I", "average": 4.2, "count": 5 }
  ],
  "comments": {
    "content": [
      {
        "id": "a1010000-0000-4000-8000-000000000101",
        "subjectId": "3b8f2a10-0000-4000-8000-000000000003",
        "subjectName": "Análisis Matemático I",
        "score": 5,
        "comment": "Explica muy claro.",
        "ratingDate": "2026-09-28T19:30:00"
      }
    ],
    "page": 0,
    "size": 10,
    "totalElements": 14,
    "totalPages": 2
  }
}
```

Reglas:

- Promedios redondeados a 1 decimal; `null` en `averageRating` si no hay calificaciones.
- Una calificación sin comentario (solo puntaje) cuenta para el promedio pero **no** aparece en `comments.content`.
- `totalRatings` es el conteo real (query), nunca un valor fijo.
- Solo se consideran calificaciones de sesiones **completadas** y con `visible = true` (consistente con el perfil de US-07).
- `totalRatings` cuenta todas las calificaciones (con y sin comentario); `comments.totalElements` cuenta solo las que tienen comentario (menor o igual).
- Los promedios por materia se devuelven siempre completos, aunque se pase `subjectId`.
- Sin `subjectId` válido para ese tutor: devolver lista vacía de comentarios, no error.

## Ubicación de clases / paquetes

> No pude acceder al repo desde el chat (GitHub bloquea la lectura automática). Ajustar nombres de paquete al layout real; la regla es **seguir el patrón de los módulos existentes** (p. ej. el de US-26).

- `controller`: `ITutorRatingHistoryController` + `TutorRatingHistoryControllerImpl` (nuevo, en `tutors/controllers/{interfaces,implementations}`, `@PreAuthorize("hasRole STUDENT")`)
- `service`: `ITutorRatingHistoryService` + `TutorRatingHistoryServiceImpl` - arma el resumen y la pagina de comentarios (`@Transactional(readOnly = true)`)
- `repository`: queries nuevas en `IRatingRepository` (JPQL `GROUP BY` por materia; página ordenada `ratingDate DESC, ratingId DESC`; conteos y promedios resueltos en BD)
- `proyecciones`: `OverallRatingSummary`, `SubjectRatingSummary` en `tutors/data/projections/`
- `dto` (records): `TutorRatingHistoryResponse`, `SubjectAverageResponse`, `RatingCommentResponse`, `PagedCommentsResponse`

Notas de implementación:

- Los promedios y el conteo se resuelven en base de datos (`AVG`, `COUNT`), no trayendo todas las filas a memoria.
- Orden de comentarios con desempate por `id DESC` para que la paginación sea estable.
- Endpoint de solo lectura (`@Transactional(readOnly = true)`).
- Seguridad: el perfil del tutor es visible para alumnos autenticados; seguir la misma configuración de acceso que el endpoint de perfil de US-07.

## Decisiones abiertas (confirmar con el equipo)

1. **Anonimato del comentarista:** este spec **no expone** nombre ni id del alumno que calificó (la historia no lo pide). Confirmado en implementación: no se expone.
2. **Escala de puntaje:** se asume la que defina US-18 (típicamente 1–5). No re-validar el rango acá.

## No hacer

- No crear ni modificar la entidad/tabla de calificaciones (es responsabilidad de US-18).
- No modificar el endpoint de perfil de US-07 ni la lógica de búsqueda de US-06/US-06B.
- No exponer datos personales del alumno que calificó.
- No devolver el mensaje "Aún sin calificaciones" desde el backend (es texto de UI).
- No hardcodear totales ni promedios.
- No agregar caché ni dependencias nuevas.
- No implementar edición ni borrado de calificaciones.

## Casos de prueba

| # | Caso de prueba | Resultado esperado | ¿Pasó? / Notas |
|---|---|---|---|
| 1 | Tutor con calificaciones en 2 materias; consultar el historial | `subjects` trae 2 entradas con promedio y cantidad correctos por materia (CA1) | Sí |
| 2 | Revisar los comentarios devueltos | Cada comentario trae `subjectId` y `subjectName` de su sesión (CA2) | Sí |
| 3 | Tutor con 3+ comentarios en fechas distintas | Orden descendente por fecha, más reciente primero (CA3) | Sí |
| 4 | Tutor sin sesiones calificadas | 200, listas vacías, `totalRatings` = 0, `averageRating` = null (CA4) | Sí |
| 5 | `tutorId` inexistente | 404 | Sí |
| 6 | `subjectId` como filtro | Solo comentarios de esa materia; `subjects` sigue completo | Sí |
| 7 | Calificación sin comentario | Cuenta en el promedio, no aparece en `comments` | Sí |

## Definition of Done

- [x] Endpoint `GET /api/v1/tutors/{tutorId}/ratings` implementado según contrato
- [x] Verificada la existencia de la entidad de US-18 y reutilizada sin duplicarla
- [x] Promedios y conteos calculados en BD (sin valores hardcodeados)
- [x] Comentarios ordenados por fecha DESC con desempate por id y paginados
- [x] 404 para tutor inexistente; estado vacío correcto para tutor sin calificaciones
- [x] Tests unitarios de servicio (promedio por materia, orden, vacío, filtro por materia)
- [x] Test de integración del endpoint (casos 1 a 7)
- [x] Sin cambios en US-06/06B/07/18
- [x] Sin dependencias nuevas; build y tests en verde
- [ ] PR hacia la rama de integración con descripción que referencia US-08 y #RN-27 (pendiente de commit/PR)
