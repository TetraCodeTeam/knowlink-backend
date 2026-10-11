# US-08 — Ajustes al historial de reseñas del tutor (Backend)

## Metadata

| Campo | Valor |
|---|---|
| ID | US-08 (Ver historial de calificaciones de tutor) |
| Tipo | Corrección sobre código ya implementado |
| Reportado por | Lohana |
| Repo / rama | `TetraCodeTeam/knowlink-backend`, rama `feat/tutor-califications-sumary` |
| Alcance | Solo backend. El frontend consume el campo nuevo `author` (spec aparte). |

> Este documento reemplaza al spec anterior de US-08 en lo que respecta a nombres: el spec original asumía nombres genéricos, y el código real usa `GET /api/v1/tutors/{tutorId}/ratings`, ids `UUID` y campos en inglés.

## Problemas reportados

1. No llega el avatar ni el nombre de quien hizo la reseña.
2. Las reseñas cruzadas (un tutor que también es alumno y reseña a otro tutor) no funcionan bien.

## Diagnóstico (verificado en el código de la rama)

### Problema 1: falta el autor en la respuesta

- `RatingCommentResponse` (paquete `tutors.controllers.responses`) solo tiene `id`, `subjectId`, `subjectName`, `score`, `comment`, `ratingDate`. No hay ningún dato del autor.
- `TutorRatingHistoryServiceImpl.toComment(...)` no lee `rating.getRaterUser()`.
- Las queries `findCompletedComments` y `findCompletedCommentsBySubject` (`IRatingRepository`) no hacen `JOIN FETCH r.raterUser`, así que aunque se mapeara, el autor se cargaría lazy (riesgo de N+1 o `LazyInitializationException`).
- Los datos existen: `User.fullName` y `User.profilePictureUrl`. El avatar se mantiene sincronizado en `User` tanto para alumnos como para tutores (ambos `uploadProfilePicture` actualizan `User.profilePictureUrl`), así que alcanza con leerlo de `User`.

### Problema 2: las reseñas cruzadas se mezclan en el historial

`Rating` es bidireccional: `raterUser` califica a `ratedUser` dentro de un `Booking`, que tiene `tutor` y `student`. Las cuatro queries del historial filtran solo por `r.ratedUser.userId = :tutorId`:

- `findOverallSummaryByTutorId`
- `findAverageGroupedBySubject`
- `findCompletedComments`
- `findCompletedCommentsBySubject` (más sus `countQuery`)

Ninguna exige `b.tutor.userId = :tutorId`. Consecuencia: si el tutor A reserva como alumno una clase con el tutor B y B lo califica (A es `ratedUser`), esa reseña aparece en el historial de A *como tutor*, con la materia de B, y suma a su promedio. Es el efecto "cruzado" roto.

Para contrastar: `findVisibleTutorRatings`, `calculateVisibleTutorAverage` y `countVisibleTutorRatings` sí incluyen `AND r.booking.tutor.userId = :tutorUserId`. Las del historial quedaron sin esa condición.

## Cambios a realizar

### 1. Filtrar por rol de tutor en las 4 queries (`IRatingRepository`)

Agregar `AND b.tutor.userId = :tutorId` en cada una, incluidas las `countQuery`. Con `ratedUser = tutorId` y `booking.tutor = tutorId`, el autor queda determinado como el alumno de la reserva. No agregar otros filtros por rol.

### 2. Traer el autor en las queries de comentarios

En `findCompletedComments` y `findCompletedCommentsBySubject` agregar `JOIN FETCH r.raterUser` (las `countQuery` no llevan fetch). Mantener el orden `ORDER BY r.ratingDate DESC, r.ratingId DESC`.

### 3. Nuevo DTO de autor

Crear `RatingAuthorResponse` en `tutors.controllers.responses`:

```java
public record RatingAuthorResponse(
        UUID userId,
        String fullName,
        String profilePictureUrl) {}
```

### 4. Extender `RatingCommentResponse`

Agregar el campo `author` de tipo `RatingAuthorResponse` (por ejemplo después de `subjectName`) y completarlo en `TutorRatingHistoryServiceImpl.toComment(...)` desde `rating.getRaterUser()`:

- `userId` ← `getUserId()`
- `fullName` ← `getFullName()`
- `profilePictureUrl` ← `getProfilePictureUrl()` (puede ser `null`)

Respuesta resultante de un comentario:

```json
{
  "id": "…",
  "subjectId": "…",
  "subjectName": "Sistemas y Organizaciones",
  "author": {
    "userId": "…",
    "fullName": "Ciro Gutierrez",
    "profilePictureUrl": "https://…"
  },
  "score": 4,
  "comment": "Clases muy estructuradas.",
  "ratingDate": "2026-09-28T19:30:00"
}
```

## Criterios de aceptación (Gherkin)

```gherkin
Feature: Autor de la reseña en el historial del tutor

  Scenario: El comentario incluye nombre y avatar del autor
    Given un alumno con foto de perfil calificó a un tutor con comentario en una sesión completada
    When se consulta GET /api/v1/tutors/{tutorId}/ratings
    Then el comentario trae author.fullName y author.profilePictureUrl del alumno

  Scenario: Autor sin foto de perfil
    Given un alumno sin foto de perfil calificó a un tutor
    When se consulta el historial del tutor
    Then author.profilePictureUrl es null y author.fullName llega igual

Feature: Reseñas cruzadas

  Scenario: Un tutor que actúa como alumno reseña a otro tutor
    Given el tutor A completó una sesión como alumno con el tutor B
    And A calificó a B con comentario
    When se consulta el historial del tutor B
    Then la reseña aparece en comentarios y suma al promedio y a la cantidad de su materia
    And author corresponde a A

  Scenario: La reseña que recibe un tutor siendo alumno no aparece en su historial como tutor
    Given el tutor A completó una sesión como alumno con el tutor B
    And B calificó a A como alumno
    When se consulta el historial del tutor A
    Then esa calificación no aparece en comentarios ni suma al promedio, a subjects ni a totalRatings
```

## Tests a agregar o ajustar

- `IRatingRepositoryTest`: con datos donde un mismo usuario es tutor en un booking y alumno en otro, verificar que las 4 queries solo cuentan lo recibido como tutor.
- `TutorRatingHistoryServiceImplTest`: `toComment` mapea `author` (con y sin foto).
- `TutorRatingHistoryControllerIntegrationTest`: la respuesta JSON incluye `comments.content[].author` y el caso de reseña cruzada.
- Revisar `RatingHistorySmokeTest` por si valida el contrato anterior sin `author`.

## No hacer

- No modificar la entidad `Rating` ni la lógica de creación de calificaciones (`RatingServiceImpl`, `RatingMapper`).
- No tocar `findVisibleTutorRatings`, `calculateVisibleTutorAverage` ni `countVisibleTutorRatings` (ya filtran por tutor).
- No exponer del autor más que `userId`, `fullName` y `profilePictureUrl` (nada de email, DNI ni teléfono).
- No resolver el autor a través de `StudentProfile` o `TutorProfile`: se lee de `User`, que sirve para cualquier rol.
- No agregar dependencias ni caché.
- No cambiar el orden de los comentarios ni los parámetros `subjectId`, `page` y `size`.

## Advertencia (fuera de alcance, para revisar con el equipo)

`profilePictureUrl` se guarda como **URL firmada con vencimiento de 7 días** (`generateSignedUrl(imagePath, 60 * 60 * 24 * 7)`). Pasada esa semana, el avatar de las reseñas puede devolver un error de acceso aunque el campo llegue bien. Si aparece "el avatar no carga" después de este arreglo, esa es la causa más probable. Una solución sería firmar la URL al momento de leer, o usar un bucket público, y debería decidirse aparte.

## Nota: criterio COMPLETED (fuera de alcance)

- Verificado en código: las 4 queries del historial exigen `b.bookingStatus = COMPLETED`;
  las de reputación (`findVisibleTutorRatings`, `calculateVisibleTutorAverage`,
  `countVisibleTutorRatings`) no filtran `bookingStatus`.
- Verificado en código: una calificación solo se crea sobre una sesión COMPLETED
  (`RatingServiceImpl.submitRating` lanza `ValidationException` si `bookingStatus != COMPLETED`)
  y un COMPLETED no puede pasar a CANCELLED (la cancelación solo permite `BOOKED`).
  En el flujo normal ambos criterios coinciden.
- Pendiente de verificar (no se corrige acá): si una reserva COMPLETED puede cambiar de
  estado después de existir una calificación (camino claims/funds:
  `FundsResolutionService.resolve` puede setear `SESSION_NOT_HELD`/`NOT_FULFILLED_BY_TUTOR`
  salvo transfer terminal). Si ocurriera, el promedio/cantidad del perfil contaría una
  reseña que el historial oculta.

## Impacto pre-merge (avisar al equipo)

El filtro `b.tutor.userId = :tutorId` saca del historial las reseñas recibidas siendo alumno:
promedios y totales de algunos tutores van a bajar o cambiar. Es el comportamiento correcto;
conviene avisarlo antes del merge.

## Definition of Done

- [x] Las 4 queries del historial filtran por `b.tutor.userId = :tutorId` (incluidas las `countQuery`)
- [x] Las queries de comentarios traen `raterUser` con `JOIN FETCH`
- [x] `RatingAuthorResponse` creado y `RatingCommentResponse` incluye `author`
- [x] `toComment` completa el autor desde `User`
- [x] Tests de repositorio, servicio e integración cubren autor (con y sin foto) y reseñas cruzadas
- [x] Sin cambios en la creación de calificaciones ni en las queries de reputación visibles
- [x] Build y tests en verde
