# Plan de implementación — Materia verificada por promedio de calificaciones

| Campo | Valor |
|---|---|
| Repos | `knowlink-backend` (rama `dev`, HEAD `046b405`, PR #117) · `knowlink-frontend` (rama `dev`, HEAD `7b77a43`, PR #77) |
| Ejecuta | OpenCode + DeepSeek V4 Flash |
| Historias relacionadas | US-22 / US-25 (validación de materia), US-08 (calificaciones), US-49 (filtro "Tutor Verificado"), US-12 (activar/desactivar materia) |
| Origen | Advertencia B1: `MaterialServiceImpl#findOrCreateTutorSubject` crea `TutorSubject` con `tutorSubjectStatus = ACTIVE` y la materia aparece como "verificada" sin validación |

## 1. Regla de negocio

Una materia de un tutor (`TutorSubject`) está **verificada** si el promedio de sus calificaciones está entre **4.5 y 5.0 (inclusive)**. Si el promedio baja de 4.5, la materia **deja de estar verificada**. Si vuelve a subir, se verifica de nuevo. Es una propiedad **derivada**: nadie la setea a mano ni se obtiene por crear la materia.

Calificaciones que cuentan (alineado con las queries actuales de `IRatingRepository`):
- `rating.visible = true` (pasó la ventana de reseña ciega de 24 h).
- `booking.bookingStatus = COMPLETED`.
- `rating.ratedUser = booking.tutor` (solo reseñas hacia el tutor; las del tutor como alumno no cuentan).
- Agrupadas por `booking.tutorSubject` (por materia, no por tutor).

## 2. Hallazgos de la revisión de `dev`

1. **Hoy la verificación es el enum `TutorSubjectStatus` y nada la hace transitar.** `TutorSubjectMapper` crea en `PENDING`; no existe código que pase a `ACTIVE` (US-25 no está implementada) salvo `findOrCreateTutorSubject` (B1). Es decir: hoy la única forma de que una materia salga "verificada" es el bug.
2. **El promedio hoy es solo por tutor.** `RatingReputationServiceImpl.refreshTutorAverage` actualiza `TutorProfile.averageRating`. Ya existe `findAverageGroupedBySubject`, pero agrupa por `Subject` y se usa para el historial (US-08); no persiste nada por materia.
3. **Puntos de recálculo existentes** (hay que engancharse ahí, en la misma transacción): `RatingServiceImpl` (cuando la reseña queda visible) y `RatingReputationServiceImpl.revealExpiredRatings` (job cada 60 s) → ambos terminan en `refreshTutorAverages(List<Rating>)`.
4. **Consumidores de `tutorSubjectStatus == ACTIVE` que hoy lo usan con significados distintos** (riesgo de regresión si el estado pasa a ser dinámico):
   - `TutorSearchSpecifications.subjectValidated()` y `TutorProfileServiceImpl.subjectsVisibleForSearch` → filtro "Tutor Verificado" (US-49). Es el uso correcto.
   - `TutorStatisticsCalculator` (líneas ~146, 317, 341): usa `status != ACTIVE` como proxy de "materia desactivada" (`deactivated` en `SubjectPerformanceResponse`). Con la regla nueva, una materia no verificada se mostraría como desactivada. **Debe usar `TutorSubject.isActive()`.**
   - `ITutorRankingRepository.findActiveSubjectNames`: filtra por `ACTIVE` para listar materias del tutor en el ranking. Debe pasar a `ts.active = true` (visibilidad), no verificación.
5. **`TutorProfile.verified` nunca se pone en `true`** (solo `AccountDeletionServiceImpl` lo pone en `false`). El badge de ranking/comunidad (`RankedTutorResponse.verified`, `CommunityAuthorResponse.verified`) y el fallback del frontend (`isSubjectVerified` en `getTutorProfile.ts`) dependen de él. Hay que definirlo (ver decisiones abiertas).
6. **Contrato con el frontend:** `TutorSubjectResponse.verificationStatus` (`"PENDING" | "ACTIVE" | "REJECTED"`) lo consumen `SubjectItem`, perfil propio del tutor y el filtro. Cambiarlo rompería el frontend; se mantiene compatible.
7. **Migraciones:** el repo usa SQL manual (`src/main/resources/migration-*.sql`) y `ddl-auto` por variable de entorno; hay que entregar el script para ejecutar **antes** de mergear a `main` (deploy automático a Render).

## 3. Decisiones de diseño (propuestas)

| Tema | Decisión |
|---|---|
| Dónde vive el estado | Columnas nuevas en `tutor_subject`: `verified BOOLEAN NOT NULL DEFAULT FALSE`, `average_rating DECIMAL(3,2) NULL`, `rating_count INT NOT NULL DEFAULT 0`. Persistido (no calculado en cada query) porque la búsqueda (US-49) filtra por verificado con `Specification` y paginación. |
| `tutorSubjectStatus` | **No se borra ni se redefine ahora.** Queda como campo legado; deja de ser fuente de verdad de "verificado". `findOrCreateTutorSubject` y el mapper crean en `PENDING`. Retirarlo es un cleanup posterior. |
| Contrato `verificationStatus` | Se mantiene el campo; valor derivado: `verified ? "ACTIVE" : "PENDING"`. Se agregan campos nuevos aditivos: `verified`, `averageRating`, `ratingCount`. |
| Umbrales | Propiedades configurables: `knowlink.verification.min-average=4.5`, `knowlink.verification.max-average=5.0`, `knowlink.verification.min-ratings=1`. Clase `SubjectVerificationProperties`. |
| Comparación | Sobre el promedio **sin redondear** (`BigDecimal`, `>= 4.5`). Evita que 4.46 muestre "4.5" y no verifique (o al revés). |
| Recalculo | Servicio nuevo `ISubjectVerificationService#recalculateForTutor(UUID tutorUserId)`, idempotente (recalcula desde los ratings, no incrementa). Última escritura gana; no hace falta lock. |
| Desactivar materia (US-12) | `active=false` **no** modifica `verified` (consistente con el spec de US-12: el estado de validación se conserva). |

## 4. Decisiones abiertas (confirmar antes de ejecutar)

1. **Mínimo de reseñas.** ~~Decisión abierta.~~ **Confirmado: `min-ratings = 1`** (`knowlink.verification.min-ratings`).
2. **`TutorProfile.verified` (badge del ranking/comunidad).** ~~Decisión abierta.~~ **Confirmado: derivado** = el tutor tiene **al menos una materia verificada y activa**. Se recalcula en `SubjectVerificationServiceImpl.refreshTutorBadge` (tras cada recálculo) y también al desactivar/reactivar una materia (`TutorProfileServiceImpl.refreshVerifiedBadge`), porque desactivar la última materia verificada tiene que limpiar el badge sin esperar a la próxima calificación.
3. **Notificar al tutor** cuando gana/pierde la verificación. ~~Decisión abierta.~~ **Confirmado: incluido** (Fase 4). `SUBJECT_VALIDATION_UPDATED` ya existe en `NotificationType`; se envía con `dedupeKey = SUBJECT_VALIDATION_UPDATED:<tutorSubjectId>:<VERIFIED|UNVERIFIED>`.

## 5. Plan por fases

### Fase 0 — Preparación (sin cambios de comportamiento)
- Crear rama `feat/subject-verification-by-rating` desde `dev` en ambos repos.
- Correr la suite actual del backend y anotar la línea base de tests rojos preexistentes.

### Fase 1 — Backend: modelo, migración y cálculo
1. `TutorSubject`: agregar `verified`, `averageRating`, `ratingCount` (con `@Builder.Default` donde aplique).
2. `src/main/resources/migration-subject-verification-by-rating.sql` (MySQL 8):
   - `ALTER TABLE tutor_subject ADD COLUMN verified ..., ADD COLUMN average_rating ..., ADD COLUMN rating_count ...`.
   - Backfill: calcular promedio/conteo por `tutor_subject` desde `rating` + `booking` con los mismos filtros de la sección 1, y setear `verified` según el umbral.
   - Corregir datos contaminados por B1: `UPDATE tutor_subject SET tutor_subject_status = 'PENDING'` donde `verified = FALSE` y `status = 'ACTIVE'` (opcional, para coherencia del campo legado).
   - Documentar en el encabezado: ejecutar antes de mergear a `main`.
3. `IRatingRepository`: nueva query de proyección `findSubjectAggregatesByTutorId(tutorId)` agrupando por `ts.tutorSubjectId` (AVG, COUNT) con los filtros de la sección 1. No reutilizar `findAverageGroupedBySubject` (agrupa por `Subject`, devuelve para el historial).
4. `SubjectVerificationProperties` + valores en `application.yaml`.
5. `ISubjectVerificationService` / `SubjectVerificationServiceImpl` en `tutors/services`: 
   - carga las materias del tutor, aplica agregados (sin ratings → `average=null`, `count=0`, `verified=false`), evalúa `count >= minRatings && avg >= min && avg <= max`, guarda solo si cambió algo, loguea transiciones (`@Slf4j`).
6. `RatingReputationServiceImpl.refreshTutorAverage`: después de actualizar `averageRating` del perfil, invocar `recalculateForTutor`. Cubre automáticamente el flujo de `RatingServiceImpl` y el job de revelado.
7. Revisar con `grep` cualquier otro camino que borre u oculte ratings (`ratingRepository.delete*`, `setVisible(false)`, `deleteByTutor_UserId` del ranking, eliminación de cuenta) y llamar al recálculo si corresponde.

### Fase 2 — Backend: cerrar B1 y reapuntar consumidores
1. `MaterialServiceImpl#findOrCreateTutorSubject` (~línea 248): crear con `tutorSubjectStatus = PENDING` y `verified = false`. Sacar el aviso de "fuera de alcance".
2. `TutorSearchSpecifications.subjectValidated()` → `cb.isTrue(root.get("verified"))`. Actualizar el Javadoc (ya no es US-25 sino promedio).
3. `TutorProfileServiceImpl.subjectsVisibleForSearch` → `TutorSubject::isVerified`.
4. `TutorStatisticsCalculator`: reemplazar `getTutorSubjectStatus() != ACTIVE` / `== ACTIVE` por `!isActive()` / `isActive()` (líneas ~146, 317, 341; confirmar el contexto de la 146).
5. `ITutorRankingRepository.findActiveSubjectNames`: `ts.active = true` en lugar del status.
6. `TutorSubjectMapper` / `TutorSubjectResponse` / `TutorProfileMapper`: `verificationStatus = verified ? "ACTIVE" : "PENDING"`; agregar `verified`, `averageRating`, `ratingCount` (record, campos al final).
7. `TutorProfile.verified` según decisión abierta #2 (derivado al recalcular).
8. Swagger: `@Operation`/`@ApiResponse` actualizados donde cambie el response (convención del repo).

### Fase 3 — Frontend (cambio chico, sin ruptura)
- `tutor-api.types.ts`, `TutorSubjectResponse.ts`, `tutor-own-profile.interface.ts`: agregar `verified`, `averageRating`, `ratingCount` (opcionales para no romper contra backend viejo).
- `getTutorProfile.ts#isSubjectVerified`: priorizar `m.verified` si viene; mantener el fallback actual.
- Opcional UX en el perfil propio del tutor: mostrar "Promedio 4.3 · Necesitás 4.5 para verificarte" (usa `averageRating` y `ratingCount`).
- No cambiar el filtro `verifiedOnly` ni la query string (`search-filters.utils.ts`): el contrato HTTP no cambia.
- Sin deuda nueva de cache: si se invalida algo tras calificar, verificar que la key de React Query del perfil/búsqueda coincida (hallazgo previo de US-06).

### Fase 4 — Opcional
- [x] Notificación `SUBJECT_VALIDATION_UPDATED` al ganar/perder verificación (confirmada en la sección 4).
- [ ] Retiro del enum `TutorSubjectStatus` y del `verificationStatus` legado cuando el frontend migre a `verified`. **Fuera de alcance de este PR** (el contrato HTTP no puede romperse).

## 6. Criterios de aceptación (Gherkin)

```gherkin
Escenario: Materia sin calificaciones
  Dado un tutor con una materia sin calificaciones visibles
  Entonces la materia no está verificada

Escenario: Alcanza el umbral
  Dado una materia con calificaciones visibles de promedio 4.5
  Entonces la materia queda verificada

Escenario: Promedio perfecto
  Dado una materia con promedio 5.0
  Entonces la materia está verificada

Escenario: Promedio justo debajo del umbral
  Dado una materia con promedio 4.49
  Entonces la materia no está verificada

Escenario: Pierde la verificación
  Dado una materia verificada con promedio 4.6
  Cuando se hace visible una calificación que baja el promedio a 4.4
  Entonces la materia deja de estar verificada

Escenario: Recupera la verificación
  Dado una materia no verificada con promedio 4.4
  Cuando el promedio sube a 4.5
  Entonces la materia vuelve a estar verificada

Escenario: Materia creada al subir material (B1)
  Dado un tutor que sube material de una materia que no tenía cargada
  Entonces la TutorSubject creada no está verificada

Escenario: Aislamiento por materia
  Dado un tutor con la materia A (promedio 4.8) y la materia B (promedio 3.9)
  Entonces solo la materia A está verificada

Escenario: Calificaciones que no cuentan
  Dado calificaciones ocultas (ventana de 24 h), de reservas no COMPLETED, o hechas por el tutor como alumno
  Entonces no afectan el promedio ni la verificación

Escenario: Filtro de búsqueda
  Dado "Tutor Verificado" activo
  Entonces solo aparecen tutores cuya materia buscada está verificada

Escenario: Desactivar no verifica ni desverifica
  Dado una materia verificada que el tutor desactiva
  Entonces conserva verified = true y no aparece en la búsqueda (active = false)
```

## 7. Tests

Backend (JUnit, siguiendo los paquetes existentes `ratings` y `tutors`):
- `SubjectVerificationServiceImplTest`: bordes 4.49 / 4.5 / 5.0, sin ratings, `min-ratings` > 1, transición hacia arriba y hacia abajo, no persiste si no hay cambios.
- Test de repositorio (H2) de `findSubjectAggregatesByTutorId`: filtros de visibilidad, `COMPLETED`, rated = tutor, dos materias del mismo tutor.
- Integración: `RatingServiceImpl` (reseña visible dispara el recálculo) y `revealExpiredRatings` (job).
- `MaterialServiceImplTest`: materia nueva creada con `verified = false`.
- Regresión: `TutorStatisticsCalculator` — materia activa no verificada **no** figura como desactivada; búsqueda `verifiedOnly`; ranking `findActiveSubjectNames`.
- Nota: H2 vs MySQL — mantener la agregación en JPQL (sin funciones de dialecto), como ya hace el repo.

Frontend: test de `isSubjectVerified` (prioridad de `verified`, fallback) y render de `SubjectItem`.

## 8. "No hacer"

- No agregar `INACTIVE` ni nuevos valores a `TutorSubjectStatus`.
- No calcular la verificación en el frontend ni en cada request de búsqueda.
- No usar el promedio global del tutor (`TutorProfile.averageRating`) para verificar materias.
- No contar calificaciones ocultas, de reservas no completadas, ni reseñas cruzadas.
- No modificar la lógica de reseña ciega ni el job de revelado más allá del enganche al recálculo.
- No romper el contrato `verificationStatus` ni los query params de US-49.
- No ejecutar la migración desde `ddl-auto`; es un script manual previo al merge a `main`.
- No tocar el módulo de claims/US-50 en este cambio.

## 9. Definition of Done

- [x] Script de migración probado en una copia con datos (MySQL 9 local, BD descartable `knowlink_migration_test`, ya eliminada).
      Backfill verificado caso por caso: 4.5 exacto verifica, 5.0 verifica, **4.49 no verifica** (449/100),
      13/3 queda `4.33` truncado, sin ratings / rating oculto / booking cancelado / rating cruzado no cuentan,
      contaminación B1 (`ACTIVE` sin verificar) vuelve a `PENDING`, badge = 1 sólo con materia verificada **y activa**.
      Los `UPDATE` corrieron dos veces más sin cambios (idempotentes); el `ALTER` es de ejecución única.
- [x] `findOrCreateTutorSubject` ya no genera materias verificadas (test `upload_createsMissingTutorSubjectAsUnverifiedAndPending`).
- [x] Verificación se recalcula al hacerse visible una reseña: flujo directo (`SubjectVerificationIntegrationTest#submittingAVisibleRatingVerifiesTheSubject`) y job (`#revealJobVerifiesTheSubjectOnceTheRatingBecomesVisible`).
- [x] Búsqueda, perfil, estadísticas y ranking consistentes (specs, mapper, `TutorStatisticsCalculator`, `findActiveSubjectNames` reapuntados a `verified`/`isActive`).
- [x] Contrato HTTP retrocompatible: `verificationStatus` sigue existiendo (derivado de `verified`), query params de US-49 sin cambios; Swagger (`verifiedOnly`) actualizado.
- [x] Tests de la sección 7 en verde: backend **797 tests, 0 fallos** (línea base Fase 0: 782) / frontend **582 tests, 0 fallos** (línea base: 573), `tsc -b` y `eslint` en verde.
      *Nota:* `PasswordRecoveryControllerTest#requestResetEmail_tokenExpiration` es un flake preexistente ajeno a este cambio
      (carrera de reloj entre `TokenServiceImpl` y el `@PrePersist` de `Token`: 2 de 3 corridas pasan). No se tocó por estar fuera de alcance.
- [ ] PR a `dev` en backend y frontend; nota en el PR del backend: ejecutar la migración antes de llegar a `main`.
