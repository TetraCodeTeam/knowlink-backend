# Mostrar la resolución del reclamo en el detalle de solicitudes (Backend)

> Pasar este archivo completo a OpenCode (DeepSeek V4 Flash) parado en `TetraCodeTeam/knowlink-backend`, rama `dev`. Spec par: `claim-resolution-detail-frontend.spec.md`.

## Metadata

| Campo | Valor |
|---|---|
| Tipo | Corrección / observación de QA |
| Módulos | `claims` (detalle compartido), `students/support`, `tutors/support`, `admin/claims` (notificación) |
| Historias relacionadas | US-50 (resolución admin), US-51 (reclamos), US-35 (notificaciones) |
| Repo / rama base | `TetraCodeTeam/knowlink-backend` — `dev` |

**Convención de idioma:** identificadores en **inglés**; textos al usuario en **español**; Swagger en español.

## Problema

Cuando el admin resuelve un reclamo, el alumno y el tutor no pueden leer el motivo de la resolución:

1. El detalle de la solicitud no lo devuelve. `StudentSessionClaimDetailResponse` y `TutorSupportClaimDetailResponse` no tienen ningún campo de resolución, aunque la entidad `SessionClaim` ya guarda `favoredParty`, `resolutionJustification` y `resolvedAt` (se completan en `AdminSessionClaimServiceImpl.resolve` → `resolveDispute`, para **todos** los reclamos de la disputa).
2. La única vía hoy es la notificación (`SessionClaimNotificationListener.buildMessage`), que mete la justificación completa en el mensaje. En la UI se corta por espacio.
3. **Riesgo adicional:** `Notification.message` es `length = 500` y la justificación admite hasta 1000 caracteres (`ResolveSessionClaimRequest`), más ~150 de texto fijo. Con una justificación larga el insert puede fallar y el listener lo captura con `catch` y solo loguea: la notificación se pierde sin avisar.

## Decisión

- La justificación se muestra en el **detalle** (fuente de verdad), no en la notificación.
- La notificación pasa a ser corta y lleva al detalle. El **email** conserva el texto completo.

## Cambios

### 1. Nuevo DTO `ClaimResolutionResponse`

Paquete `com.knowlink.api.claims.controllers.responses`, como `record`:

```java
public record ClaimResolutionResponse(
        ClaimFavoredParty favoredParty,   // TUTOR | STUDENT
        boolean inMyFavor,                // calculado según quién consulta
        String justification,             // resolutionJustification
        LocalDateTime resolvedAt) {}
```

No incluir datos del admin (nombre, id) ni contacto de la contraparte.

### 2. `IClaimSupportDetailsService` / `ClaimSupportDetailsServiceImpl`

Agregar `ClaimResolutionResponse getResolution(SessionClaim claim, Role viewerRole)`:

- Devuelve `null` si `claim.getStatus() != ClaimStatus.RESOLVED`, o si `favoredParty` o `resolutionJustification` son `null` (reclamos resueltos antes de US-50, sin datos).
- `inMyFavor = claim.getFavoredParty() == TUTOR` cuando `viewerRole == TUTOR`; análogo para `STUDENT`.
- `resolvedAt` desde `claim.getResolvedAt()`.

### 3. Detalles de alumno y tutor

- `StudentSessionClaimDetailResponse` y `TutorSupportClaimDetailResponse`: agregar al final el campo `ClaimResolutionResponse resolution` (nullable).
- `StudentSupportMapper.toSessionClaimDetails` y `TutorSupportMapper.toClaimDetails`: recibir el nuevo parámetro y pasarlo al constructor.
- `StudentSessionSupportService.getClaimDetails` (viewerRole `STUDENT`) y `TutorSessionSupportService.getClaimDetails` (viewerRole `TUTOR`): llamar a `claimSupportDetailsService.getResolution(claim, role)`.
- No cambiar los DTOs de listado (`StudentSessionClaimResponse`, `TutorSupportClaimResponse`).
- Actualizar `@Schema`/`@Operation` de Swagger si existen para esos endpoints.

### 4. `SessionClaimNotificationListener`

- `notifyParty` arma **dos** textos:
  - `inAppMessage` (corto, siempre < 500): `"El reclamo por la sesión de {materia} del {fecha} se resolvió a favor del {tutor|alumno}. {fundsOutcome} Abrí el detalle para leer la justificación."`
  - `emailMessage`: el texto actual completo (con `Justificación: ...`).
- La notificación in-app usa `inAppMessage`; el email sigue usando `emailMessage`.
- Mantener `dedupeKey`, `NotificationType.SESSION_CLAIM_UPDATE` y `referenceId` sin cambios.
- Como red de seguridad, truncar `inAppMessage` a 500 caracteres antes de `notifyUser`.

### 5. Sin migración

No hay columnas nuevas: `resolution_justification`, `favored_party` y `resolved_at` ya existen en `session_claim`.

## Criterios de aceptación

```gherkin
Scenario: El alumno ve la resolución en el detalle
  Given el reclamo está RESOLVED a favor del alumno con justificación "X"
  When el alumno consulta el detalle del reclamo
  Then recibe resolution.favoredParty = STUDENT, inMyFavor = true, justification = "X" y resolvedAt

Scenario: El tutor ve la resolución en el detalle
  Given el mismo reclamo
  When el tutor consulta el detalle
  Then recibe resolution.favoredParty = STUDENT, inMyFavor = false y la misma justificación

Scenario: Reclamo no resuelto
  Given el reclamo está OPEN o IN_REVIEW
  When cualquiera de las partes consulta el detalle
  Then resolution es null

Scenario: Justificación larga
  Given el admin resuelve con una justificación de 1000 caracteres
  Then se crea la notificación in-app para ambas partes (mensaje corto)
  And el email incluye la justificación completa
```

## Tests

| Archivo | Casos |
|---|---|
| `claims/services/implementations/ClaimSupportDetailsServiceImplTest` | `getResolution`: null si no está RESOLVED; null si falta justificación/favoredParty; `inMyFavor` correcto para TUTOR y STUDENT |
| `admin/claims/SessionClaimNotificationListenerTest` | el mensaje in-app no incluye la justificación y mide < 500; el email sí la incluye; con justificación de 1000 caracteres igual se crea la notificación |
| `claims/controllers/ClaimControllerIntegrationTest` (o el de support existente) | el detalle de un reclamo resuelto devuelve `resolution`; el de uno abierto devuelve `null` |
| Tests de mappers/servicios que construyen los DTOs de detalle | ajustar a la nueva firma |

Ejecutar `./mvnw test` (o el wrapper del repo) y dejar todo en verde.

## Fuera de alcance

- Cambios en el panel admin.
- Permitir editar la justificación después de resuelto.
- Reclamos de materiales (`MaterialReportCase.resolutionNote`): flujo distinto.
