# US-54 a US-58 — Comunidad académica (backend)

Módulo `com.knowlink.api.community`. Todos los endpoints requieren Bearer JWT y rol activo `STUDENT` o `TUTOR`
(`SecurityConfig`: `/api/v1/community/**`). El rol que se guarda en publicaciones y respuestas es el **rol activo**
(`user.getRole()`) al momento de escribir.

## Endpoints (`/api/v1/community`)

| Método | Ruta | US | Descripción |
|---|---|---|---|
| GET | `/posts?page&size` | 56 | Feed, de más reciente a más antigua. |
| GET | `/posts/mine?date&page&size` | 57 | Solo las del usuario; `date` (yyyy-MM-dd) filtra por día de creación. |
| GET | `/posts/{postId}` | 56 / 55 | Hilo: publicación + respuestas (destacada primero, luego por antigüedad). |
| POST | `/posts` (multipart `request` + `file?`) | 54 | Crea. No se puede editar, solo eliminar. |
| DELETE | `/posts/{postId}` | 57 | Baja lógica; solo el autor. |
| POST | `/posts/{postId}/resolve` | 55 / 57 | Solo el autor de un `HELP_REQUEST` abierto. |
| POST | `/posts/{postId}/replies` (multipart) | 55 | Responde. 422 `POST_CLOSED` si el hilo está resuelto. |
| PUT | `/posts/{postId}/replies/{replyId}` | 55 | Edita el texto; solo el autor. |
| DELETE | `/posts/{postId}/replies/{replyId}` | 55 | Baja lógica; solo el autor. |
| PUT / DELETE | `/posts/{postId}/replies/{replyId}/helpful` | 55 | Marca / quita "Esto me sirvió" (idempotente). 422 `OWN_REPLY_NOT_MARKABLE` si es propia. |
| GET | `/subjects` | 54 | Materias del catálogo, por nombre. |
| GET | `/podium` | 58 | `{ periodStart, podium (1-3), ranking (4-10) }`. |
| GET | `/podium/me` | 58 | `{ eligible, position, positionsToTopTen, progressPercent, … }`. |

## Reglas de negocio

- **Tipos:** `HELP_REQUEST` (descripción ≤ 500) y `SHARED_CONTENT` (≤ 2000). Cualquier rol puede crear cualquiera.
  Título ≤ 150, respuesta ≤ 2000. Los límites están en `CommunityConstants`.
- **Adjunto opcional** (uno por publicación o respuesta): PDF, JPG, JPEG, PNG o XLSX, hasta
  `knowlink.community.max-attachment-mb` (5). Se sube al bucket de materiales bajo `community/{userId}/`. La URL del
  adjunto es firmada (1 h); si el storage falla, `attachment.url` viene `null` y el feed sigue funcionando.
- **Resolver:** destaca la respuesta con más marcas (empate: la más antigua), ignorando las del propio autor y las que
  tienen 0 marcas. La respuesta destacada queda fijada en `featured_reply_id`. Después el hilo rechaza respuestas.
- **Tarjeta de vinculación** (`bookingCard`): solo en respuestas hechas como `TUTOR`. `includeBookingCard` por defecto
  `true`; `bookingCardQuestion` opcional (≤ 150). Solo se devuelve si el autor sigue siendo un tutor activo
  (`author.canBookClass`).
- **Notificación:** al responder se publica `CommunityReplyCreatedEvent`; `CommunityNotificationListener` crea una
  `COMMUNITY_REPLY` para el autor de la publicación (no si se responde a sí mismo). `referenceId` = `postId`.

## Ranking mensual (US-58)

- Se persiste en `community_tutor_ranking`; **una fila por tutor y período** (primer día del mes). Nada lo modifica
  entre actualizaciones.
- Lo genera `TutorRankingJob` (`0 5 0 1 * *`, zona Córdoba). Si al consultar todavía no existe el del mes en curso
  (primer arranque, job caído), se genera en ese momento (`generateIfAbsent` nunca recalcula un período existente).
- Puntaje = `10 × mejores aportes + 2 × marcas "Esto me sirvió"` (de respuestas como tutor, **mes anterior**)
  `+ 5 × calificación promedio vigente`. Solo participan tutores con cuenta activa y puntaje > 0. Desempate: más clases
  concretadas. Los pesos están en `CommunityConstants`.
- `GET /podium/me`: `eligible=false` si el usuario no tiene perfil de tutor activo; `position=null` si es elegible pero
  no suma puntaje; `positionsToTopTen=0` si ya está en el top 10.

## Despliegue

- Las tablas `community_*` las crea `ddl-auto`.
- **Antes de desplegar en una base MySQL existente**, correr `src/main/resources/migration-community-notification-type.sql`:
  Hibernate puede haber creado `user_notification.notification_type` como `ENUM(...)` y `ddl-auto=update` no agrega
  valores nuevos (`COMMUNITY_REPLY`).
