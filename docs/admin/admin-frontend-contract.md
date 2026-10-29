# Contrato de API — Panel de administración

| Campo | Valor |
|---|---|
| Consumidores | Frontend `admin-01` (login/badges), `admin-02` (home), `admin-03` (denuncias de material), `admin-04` (reclamos de sesión) |
| Backend | `knowlink-backend` — paquete `com.knowlink.api.admin` |
| Base URL local | `http://localhost:8080` |
| Prefijo | `/api/v1` |
| Autenticación | JWT (`Authorization: Bearer <token>`), rol `ADMIN` |
| Última actualización | 29/10/2026 |

Este documento es la fuente de verdad del contrato HTTP del panel. Cualquier cambio de campos, códigos de error o semántica debe reflejarse acá y en Swagger (`/swagger-ui.html`).

---

## 1. Autenticación y autorización

### 1.1 Login

Los administradores usan el mismo endpoint que el resto de los roles. **No necesitan perfil de alumno ni de tutor.**

```http
POST /api/v1/auth/login
Content-Type: application/json
```

```json
{
  "email": "admin.demo@knowlink.test",
  "password": "Admin123!"
}
```

`200 OK`:

```json
{
  "userId": "5a813c90-d344-11f1-b9e1-489ebde51489",
  "email": "admin.demo@knowlink.test",
  "token": "eyJhbGciOiJIUzI1NiJ9...",
  "role": "ADMIN"
}
```

- `token` se envía **sin** el prefijo `Bearer`; el header completo es `Authorization: Bearer <token>`.
- El rol efectivo sale del JWT, nunca del cliente.
- El frontend debe guardar el token y redirigir al panel solo cuando `role === "ADMIN"`. Cualquier otro rol debe ver el flujo normal de alumno/tutor.
- Un `401` en login significa credenciales inválidas o cuenta no activa; un `400 VALIDATION_ERROR` significa que el rol del usuario exige un perfil activo que no existe (no aplica a `ADMIN`).

### 1.2 Permisos de los endpoints admin

Todas las rutas `/api/v1/admin/**` exigen `hasRole('ADMIN')`.

| Situación | HTTP | `detail` |
|---|---|---|
| Sin header `Authorization` o token inválido | `401` | `Se requiere autenticación para acceder a este recurso` (texto, no código) |
| Token de `STUDENT` o `TUTOR` | `403` | `ACCESS_DENIED` |
| Token de `ADMIN` | `200` | — |

### 1.3 Cuentas de administrador

- **Producción/staging:** `AdminSeeder` crea las cuentas al arrancar desde variables de entorno `KNOWLINK_ADMIN_ACCOUNTS_0_EMAIL`, `KNOWLINK_ADMIN_ACCOUNTS_0_PASSWORD`, `KNOWLINK_ADMIN_ACCOUNTS_0_FULL_NAME` (y `_1_`, `_2_`, …). Es idempotente: un email existente no se modifica ni se duplica.
- **Local/demo:** `docs/seeds/seed-admin-demo.sql` inserta `admin.demo@knowlink.test` / `Admin123!` (idempotente). **No usar en producción.**

---

## 2. Convenciones generales

### 2.1 Formato

- Todas las respuestas son JSON UTF-8, sin envoltorio `data`.
- UUID como string canónico (`8-4-4-4-12`).
- `LocalDate` → `YYYY-MM-DD` (`2026-10-28`).
- `LocalDateTime` → `YYYY-MM-DDTHH:mm:ss[.SSSSSS]`, **sin offset**, en la zona de la aplicación (`America/Argentina/Cordoba`).
- Decimales (`heldAmount`) como número JSON.
- Porcentajes con **un decimal** (`Double`), salvo `volumeSplit`, que usa enteros.
- Los campos que no aplican son `null`, nunca `0` ni `""` (salvo que se indique lo contrario).
- **No hay caché HTTP** en los endpoints admin: cada request consulta la base.

### 2.2 Paginación

| Parámetro | Default | Reglas |
|---|---|---|
| `page` | `0` | Base 0. Valores negativos se normalizan a `0`. |
| `size` | `10` | `<= 0` → `10`. Máximo `50`: los valores mayores se recortan a `50` (no devuelven error). |

Envelope (`PagedResponse`):

```json
{
  "content": [],
  "page": 0,
  "size": 10,
  "totalElements": 0,
  "totalPages": 0,
  "hasNext": false
}
```

`statusCounts` **ignora** el filtro `status`, pero **respeta** `from`/`to` y `assignedToMe`. Sirve para pintar las pestañas de estado.

### 2.3 Rango de fechas

Parámetros `from` y `to` (ISO `YYYY-MM-DD`) disponibles en los dos listados.

| Caso | Resultado |
|---|---|
| Ambos ausentes | Últimos 30 días **incluyendo hoy** (`hoy - 29` … `hoy`). |
| Solo uno presente | `400 VALIDATION_ERROR` — `Indicá ambas fechas del rango.` |
| `from > to` | `400 VALIDATION_ERROR` — `La fecha desde no puede ser posterior a la fecha hasta.` |
| Más de 366 días | `400 VALIDATION_ERROR` — `El rango de fechas no puede superar los 366 días.` |

La ventana en base es `[from 00:00:00, to+1 00:00:00)` en `America/Argentina/Cordoba`. Se filtra por `openedAt` (denuncias de material) y por `createdAt` (reclamos de sesión).

### 2.4 Orden de los listados

| Listado | Orden |
|---|---|
| Denuncias de material | `reportsCount` DESC, luego `openedAt` ASC (las más denunciadas primero). |
| Reclamos de sesión | `OPEN` → `IN_REVIEW` → `RESOLVED`, luego `createdAt` ASC (los más viejos primero). |

### 2.5 Formato de error

Todos los errores usan `ApiError`:

```json
{
  "status": 409,
  "message": "Otro administrador ya tomó esta solicitud.",
  "detail": "CASE_ALREADY_TAKEN"
}
```

- `message`: texto en español listo para `toast.error` / diálogo. **Mostrarlo tal cual.**
- `detail`: código estable para lógica de UI (excepto `401` y `500`, donde es un texto).
- Nunca se exponen stack traces, `technicalMessage` ni datos personales.

---

## 3. Badges del sidebar (`admin-01`)

```http
GET /api/v1/admin/navigation/badges
Authorization: Bearer <token>
```

`200 OK`:

```json
{
  "pendingMaterialReports": 3,
  "pendingSessionClaims": 2
}
```

Semántica:

- `pendingMaterialReports`: casos en estado `PENDING`, **de todos los tiempos**, sin importar quién está asignado ni el rango de fechas del listado.
- `pendingSessionClaims`: reclamos en estado `OPEN`, **de todos los tiempos**. Se cuenta una sola vez por disputa (reserva).
- `IN_REVIEW` **no** cuenta como pendiente.
- El badge se muestra solo si el valor es `> 0`.
- Refrescar al enfocar la ventana y cada ~60 s; invalidar después de tomar o resolver una solicitud.

---

## 4. Home del administrador (`admin-02`)

```http
GET /api/v1/admin/dashboard/summary?range=WEEK
Authorization: Bearer <token>
```

| Parámetro | Valores | Default |
|---|---|---|
| `range` | `WEEK`, `MONTH` | `WEEK` |

Un valor desconocido devuelve `400 VALIDATION_ERROR` (`El parámetro 'range' no tiene un valor válido.`).

`200 OK`:

```json
{
  "sessionClaims": {
    "resolvedThisWeek": 12,
    "resolvedDeltaPct": 20.0,
    "newThisWeek": 4,
    "pendingCount": 2,
    "oldestPendingDays": 6
  },
  "materialReports": {
    "resolvedThisWeek": 5,
    "resolvedDeltaPct": null,
    "newThisWeek": 3,
    "pendingCount": 3,
    "mostReportedPendingFile": {
      "caseId": "0b0f5f0e-4a3e-4c39-9a55-1a2b3c4d5e6f",
      "materialId": "9a8b7c6d-5e4f-4a3b-8c2d-1e0f9a8b7c6d",
      "fileName": "apunte-final.pdf",
      "reportsCount": 6
    }
  },
  "performance": {
    "range": "WEEK",
    "avgResolutionHours": 18.5,
    "avgResolutionDeltaPct": 12.3,
    "unresolvedPct": 22.5,
    "unresolvedLevel": "ACCEPTABLE",
    "volumeSplit": {
      "claimsPct": 60,
      "reportsPct": 40
    }
  }
}
```

### 4.1 Tarjetas semanales (`sessionClaims`, `materialReports`)

Estos dos bloques son **siempre** ventanas móviles de 7 días y **no** cambian con `range` (solo `performance` lo hace).

| Campo | Fórmula |
|---|---|
| `resolvedThisWeek` | Casos resueltos en `[ahora - 7 días, ahora)`. |
| `resolvedDeltaPct` | Variación % contra los 7 días anteriores. `null` si la base es `0` (no hay comparación). Un valor negativo indica menos resoluciones que la semana previa. |
| `newThisWeek` | Casos **creados** en los últimos 7 días que **hoy siguen pendientes** (`PENDING` / `OPEN`). |
| `pendingCount` | Total de pendientes de todos los tiempos (mismo valor que el badge). |
| `oldestPendingDays` | Días completos desde la creación del reclamo `OPEN` más antiguo. `null` si no hay pendientes. |
| `mostReportedPendingFile` | Caso `PENDING` con más denuncias (desempate: `openedAt` más antiguo). `null` si no hay pendientes. |

### 4.2 Panel de rendimiento (`performance`)

| Campo | Fórmula |
|---|---|
| `range` | Eco del parámetro (`WEEK` = 7 días, `MONTH` = 30 días). |
| `avgResolutionHours` | Promedio ponderado de horas de resolución de reclamos y denuncias resueltos en el período. `null` si no hubo resoluciones. |
| `avgResolutionDeltaPct` | `(promedioAnterior - promedioActual) / promedioAnterior * 100`. **Positivo = se resolvió más rápido.** `null` si falta alguno de los promedios o el anterior es `0`. |
| `unresolvedPct` | `casos abiertos creados en el período / total creados en el período * 100`, redondeado a 1 decimal. `0.0` si no hubo volumen. |
| `unresolvedLevel` | Semáforo calculado en backend (tabla siguiente). |
| `volumeSplit` | Reparto porcentual entero de los casos nuevos del período entre reclamos y denuncias. Suma `100` cuando hay volumen; `0/0` cuando no hay casos. Se usa el método del mayor resto. |

Umbrales de `unresolvedLevel` (no recalcular en el frontend):

| `unresolvedPct` | Nivel | Etiqueta UI |
|---|---|---|
| `< 15` | `UNDER_CONTROL` | Bajo control |
| `15 – 29.9` | `ACCEPTABLE` | Aceptable |
| `30 – 59.9` | `RISKY` | Riesgoso |
| `>= 60` | `CRITICAL` | Crítico |

Reglas de presentación:

- `null` en `resolvedDeltaPct`, `avgResolutionHours` o `avgResolutionDeltaPct` → mostrar `—` (sin comparación), nunca `NaN`, `Infinity` ni `0 %`.
- `pendingCount === 0` → `mostReportedPendingFile` es `null`; mostrar "Sin pendientes por ahora".
- No calcular porcentajes, deltas ni niveles en el frontend: el backend es la única fuente.
- Sin caché: `staleTime: 0` y refetch al enfocar; invalidar después de cada acción del panel.

---

## 5. Denuncias de material (`admin-03`)

Base: `/api/v1/admin/material-reports`. El identificador de ruta y de caché es **`caseId`** (no `materialId`).

### 5.1 Endpoints

| Método | Ruta | Body | Respuesta |
|---|---|---|---|
| `GET` | `/api/v1/admin/material-reports` | — | `MaterialReportCaseListResponse` |
| `GET` | `/api/v1/admin/material-reports/{caseId}` | — | `MaterialReportCaseDetailResponse` |
| `POST` | `/api/v1/admin/material-reports/{caseId}/assign` | — | `MaterialReportCaseDetailResponse` |
| `POST` | `/api/v1/admin/material-reports/{caseId}/remove` | `{ "reason": "..." }` (opcional) | `MaterialReportCaseDetailResponse` |
| `POST` | `/api/v1/admin/material-reports/{caseId}/dismiss` | — | `MaterialReportCaseDetailResponse` |
| `GET` | `/api/v1/admin/material-reports/{caseId}/download-url` | — | `{ "url": "...", "expiresInSeconds": 3600 }` |

### 5.2 Listado

```http
GET /api/v1/admin/material-reports?status=PENDING&assignedToMe=false&from=2026-10-01&to=2026-10-28&page=0&size=10
```

| Parámetro | Valores | Default |
|---|---|---|
| `status` | `PENDING`, `IN_REVIEW`, `REMOVED`, `DISMISSED` | todos |
| `assignedToMe` | `true` / `false` | `false` |
| `from`, `to` | `YYYY-MM-DD` | últimos 30 días |
| `page`, `size` | ver 2.2 | `0`, `10` (máx. 50) |

`200 OK`:

```json
{
  "page": {
    "content": [
      {
        "caseId": "0b0f5f0e-4a3e-4c39-9a55-1a2b3c4d5e6f",
        "materialId": "9a8b7c6d-5e4f-4a3b-8c2d-1e0f9a8b7c6d",
        "fileName": "apunte-final.pdf",
        "materialType": "PDF",
        "subjectName": "Algoritmos",
        "tutorName": "Tutor Demo",
        "reportsCount": 6,
        "status": "PENDING",
        "openedAt": "2026-10-27T09:15:00",
        "assignedAdmin": null,
        "assignedToMe": false,
        "resolvedAt": null,
        "resolutionNote": null
      }
    ],
    "page": 0,
    "size": 10,
    "totalElements": 1,
    "totalPages": 1,
    "hasNext": false
  },
  "statusCounts": {
    "pending": 3,
    "inReview": 1,
    "removed": 2,
    "dismissed": 4
  }
}
```

- `materialType`: `PDF | JPG | JPEG | XLSX | PNG`.
- `assignedAdmin`: `{ "adminId": "uuid", "fullName": "..." }` o `null`.
- `resolutionNote`: motivo de la baja; `null` cuando se desestimó.
- `statusCounts` no cambia con `status` (sí con `from`/`to` y `assignedToMe`).

### 5.3 Detalle

```json
{
  "summary": { "...": "mismo objeto del listado" },
  "tutor": {
    "fullName": "Tutor Demo",
    "career": "Ingeniería en Sistemas",
    "dni": "30111222",
    "institutionalId": "TUT-2021-004"
  },
  "material": {
    "fileName": "apunte-final.pdf",
    "materialType": "PDF",
    "sizeInBytes": 1048576,
    "uploadedAt": "2026-10-19T10:00:00"
  },
  "reports": [
    {
      "reason": "PLAGIARISM",
      "reportedAt": "2026-10-27T09:15:00",
      "description": "Es una copia de un libro."
    }
  ],
  "permissions": {
    "canTake": true,
    "canResolve": false
  }
}
```

- `reports[].reason`: `INAPPROPRIATE_CONTENT | PLAGIARISM | FALSE_INFORMATION | OTHER`.
- `reports[].description` puede ser `null`.
- **La identidad de los denunciantes no se expone** (ni en el detalle ni en el listado): no intentar mostrarla.
- `permissions.canTake` = `status === "PENDING"`.
- `permissions.canResolve` = `status === "IN_REVIEW"` **y** el caso está asignado al administrador autenticado. Usar estos booleanos en lugar de recalcular la regla.

### 5.4 Tomar (`assign`)

Sin body. `200` con el detalle actualizado (`status = IN_REVIEW`, `assignedAdmin` = admin actual, `assignedToMe = true`).

Errores: `404 MATERIAL_REPORT_CASE_NOT_FOUND`, `409 CASE_ALREADY_TAKEN` (otro admin la tomó), `409 CASE_NOT_PENDING` (ya estaba resuelta).

### 5.5 Dar de baja (`remove`)

```http
POST /api/v1/admin/material-reports/{caseId}/remove
Content-Type: application/json

{ "reason": "Copyright" }
```

- Body **opcional**. `reason` se recorta; vacío o solo espacios → `null`. Máximo **500** caracteres.
- Efectos: el material sale del catálogo y de la descarga de alumno/tutor; el caso pasa a `REMOVED`; todas las denuncias del caso se resuelven como `UPHELD`; `resolutionNote` guarda el motivo.
- `200` con el detalle actualizado.
- Errores: `403 NOT_ASSIGNED_TO_ADMIN`, `409 CASE_NOT_IN_REVIEW` (nadie la tomó), `409 CASE_ALREADY_RESOLVED`, `404 MATERIAL_REPORT_CASE_NOT_FOUND`.

### 5.6 Desestimar (`dismiss`)

Sin body. El material **se mantiene disponible** y el caso pasa a `DISMISSED` con `resolutionNote = null`.

Errores: los mismos que `remove` (`403`, `409`, `404`).

### 5.7 URL de descarga

`200`:

```json
{
  "url": "https://<supabase>/.../apunte-final.pdf?token=...",
  "expiresInSeconds": 3600
}
```

- Abrir la URL en una pestaña nueva; **no** cachearla ni loguearla.
- `404 MATERIAL_REPORT_CASE_NOT_FOUND` si el caso no existe.
- `404 FILE_NOT_FOUND` si el archivo ya no está en el storage.

### 5.8 Máquina de estados y reglas de UI

```
PENDING --assign--> IN_REVIEW --remove--> REMOVED
                          \----dismiss---> DISMISSED
```

- `REMOVED` y `DISMISSED` son finales: no se puede volver a tomar ni resolver.
- Modo lectura cuando `canTake === false && canResolve === false`.
- Desestimar **no lleva nota**: el diálogo no necesita campo de texto.
- Dar de baja lleva un motivo opcional (máx. 500).
- El botón "Recurso" usa `/download-url` y abre la URL firmada.
- Invalidar listado, detalle y badges después de `assign`, `remove` o `dismiss`.

---

## 6. Reclamos de sesión (`admin-04`)

Base: `/api/v1/admin/session-claims`. El identificador es **`claimId`** (UUID). Un reclamo puede agrupar varias denuncias de la misma reserva: el backend expone **una sola fila por disputa** (el `claimId` representativo) y aplica las mutaciones a toda la disputa.

### 6.1 Endpoints

| Método | Ruta | Body | Respuesta |
|---|---|---|---|
| `GET` | `/api/v1/admin/session-claims` | — | `SessionClaimListResponse` |
| `GET` | `/api/v1/admin/session-claims/{claimId}` | — | `SessionClaimDetailResponse` |
| `POST` | `/api/v1/admin/session-claims/{claimId}/assign` | — | `SessionClaimDetailResponse` |
| `GET` | `/api/v1/admin/session-claims/{claimId}/contact-info` | — | `ClaimContactInfoResponse` |
| `POST` | `/api/v1/admin/session-claims/{claimId}/resolve` | `{ "favoredParty": "...", "justification": "..." }` | `SessionClaimDetailResponse` |

### 6.2 Listado

```http
GET /api/v1/admin/session-claims?status=OPEN&assignedToMe=false&from=2026-10-01&to=2026-10-28&page=0&size=10
```

| Parámetro | Valores | Default |
|---|---|---|
| `status` | `OPEN`, `IN_REVIEW`, `RESOLVED` | todos |
| `assignedToMe` | `true` / `false` | `false` |
| `from`, `to` | `YYYY-MM-DD` | últimos 30 días |
| `page`, `size` | ver 2.2 | `0`, `10` (máx. 50) |

`200 OK`:

```json
{
  "page": {
    "content": [
      {
        "claimId": "3f1c2b4a-5d6e-4f70-8a91-b2c3d4e5f607",
        "subjectName": "Algoritmos",
        "tutorName": "Tutor Demo",
        "studentName": "Alumno Demo",
        "sessionDate": "2026-10-26",
        "status": "OPEN",
        "openedAt": "2026-10-27T11:40:00",
        "assignedAdmin": null,
        "assignedToMe": false,
        "resolvedAt": null,
        "favoredParty": null,
        "resolutionJustification": null
      }
    ],
    "page": 0,
    "size": 10,
    "totalElements": 1,
    "totalPages": 1,
    "hasNext": false
  },
  "statusCounts": {
    "open": 2,
    "inReview": 1,
    "resolved": 5
  }
}
```

### 6.3 Detalle

```json
{
  "summary": { "...": "mismo objeto del listado" },
  "heldAmount": 1000.00,
  "automaticOutcome": {
    "code": "TUTOR_ABSENT_REFUND_TO_STUDENT",
    "label": "Tutor ausente – reintegro al alumno"
  },
  "tutor": {
    "fullName": "Tutor Demo",
    "reasonCode": "I_COULD_NOT_ATTEND",
    "reasonLabel": "Yo no pude asistir",
    "message": "Se me cortó internet."
  },
  "student": {
    "fullName": "Alumno Demo",
    "reasonCode": "TUTOR_COULD_NOT_ATTEND",
    "reasonLabel": "El tutor no pudo asistir",
    "message": null
  },
  "permissions": {
    "canTake": false,
    "canViewContact": false,
    "canResolve": false
  }
}
```

- `heldAmount`: monto retenido original de la reserva. `0` en sesiones gratuitas (sin `FundsTransfer`).
- `automaticOutcome.code`: `SESSION_COMPLETED_RELEASE_TO_TUTOR`, `TUTOR_ABSENT_REFUND_TO_STUDENT`, `SESSION_NOT_HELD_REFUND_TO_STUDENT`; puede ser `null`.
- `tutor` / `student`: declaración de cada parte. Si esa parte no denunció, `reasonCode`, `reasonLabel` y `message` son `null`.
- `message` `null` → mostrar "No se registró un mensaje".
- `permissions.canTake` = `status === "OPEN"`.
- `permissions.canViewContact` = `status === "IN_REVIEW"` y asignado al admin actual.
- `permissions.canResolve` = misma regla que `canViewContact`.
- **No hay datos de contacto en el detalle ni en el listado**: solo en `/contact-info`.

### 6.4 Tomar (`assign`)

Sin body. `200` con el detalle actualizado (`status = IN_REVIEW`, `assignedAdmin` = admin actual, `assignedToMe = true`, `canViewContact`/`canResolve` en `true`).

Errores: `404 CLAIM_NOT_FOUND`, `409 CASE_ALREADY_TAKEN` (otro admin la tomó), `409 CASE_ALREADY_RESOLVED`.

### 6.5 Datos de contacto (`contact-info`)

```http
GET /api/v1/admin/session-claims/{claimId}/contact-info
```

`200 OK` con `Cache-Control: no-store`:

```json
{
  "tutor": {
    "fullName": "Tutor Demo",
    "email": "tutor.demo@knowlink.test",
    "phoneNumber": "3515550000",
    "dni": "30111222",
    "institutionalId": "TUT-2021-004",
    "career": "Ingeniería en Sistemas"
  },
  "student": {
    "fullName": "Alumno Demo",
    "email": "alumno.demo@knowlink.test",
    "phoneNumber": "3515551111",
    "dni": "40111222",
    "institutionalId": "ALU-2023-118",
    "career": "Ingeniería en Sistemas"
  }
}
```

- Solo disponible cuando el reclamo está `IN_REVIEW` y asignado al admin actual.
- El acceso queda auditado (`CONTACT_INFO_VIEWED`).
- En el frontend: `gcTime: 0`, sin persistencia, y limpiar al cerrar el diálogo. No incluir estos datos en logs ni en analytics.
- Errores: `409 CASE_NOT_IN_REVIEW` (todavía `OPEN` o ya `RESOLVED`), `403 NOT_ASSIGNED_TO_ADMIN`, `404 CLAIM_NOT_FOUND`.

### 6.6 Resolver (`resolve`)

```http
POST /api/v1/admin/session-claims/{claimId}/resolve
Content-Type: application/json

{
  "favoredParty": "TUTOR",
  "justification": "El tutor envió la grabación de la clase."
}
```

| Campo | Reglas |
|---|---|
| `favoredParty` | Obligatorio: `TUTOR` o `STUDENT`. |
| `justification` | Obligatorio, no vacío, máximo **1000** caracteres. Se recorta. |

Efectos (todo en una transacción):

| `favoredParty` | Fondos | Estado final de la reserva |
|---|---|---|
| `TUTOR` | `RELEASED_TO_TUTOR` (el tutor recibe el 97 %; retención del 3 %) | `COMPLETED` |
| `STUDENT` y la denuncia del alumno fue `TUTOR_COULD_NOT_ATTEND` | `REFUNDED_TO_STUDENT` (100 %) | `NOT_FULFILLED_BY_TUTOR` |
| `STUDENT` en cualquier otro caso | `REFUNDED_TO_STUDENT` (100 %) | `SESSION_NOT_HELD` |

- Sesión gratuita: no hay fondos que mover; `heldAmount = 0` y la reserva igual recibe su estado final. El diálogo debe adaptar el texto ("no hay fondos para transferir").
- La respuesta `200` es el detalle actualizado (`status = RESOLVED`, `favoredParty`, `resolutionJustification`, `resolvedAt`).
- Errores: `400 VALIDATION_ERROR`, `409 CASE_NOT_IN_REVIEW` ("Tomá la solicitud antes de resolverla."), `409 CASE_ALREADY_RESOLVED`, `403 NOT_ASSIGNED_TO_ADMIN`, `409 BOOKING_IMMUTABLE` (los fondos ya están en estado terminal), `409 FUNDS_STATE_MISMATCH` (los fondos no están suspendidos por este reclamo), `502 FUNDS_EXECUTION_FAILED`.
- Ante `502`, **no** cerrar el diálogo: mostrar el mensaje y permitir reintentar. El reclamo sigue `IN_REVIEW` y no se movió dinero.

### 6.7 Máquina de estados y reglas de UI

```
OPEN --assign--> IN_REVIEW --resolve--> RESOLVED
```

- Un reclamo `OPEN` no se puede resolver directamente: primero hay que tomarlo (`409 CASE_NOT_IN_REVIEW`).
- `RESOLVED` es final: no se puede editar, revertir ni volver a resolver.
- Modo lectura cuando `canResolve === false`.
- Botones de resultado: "A favor del tutor" / "A favor del alumno" (`favoredParty = TUTOR | STUDENT`) + justificación obligatoria.
- "Citar ambas partes" **no existe** en este alcance: solo se consultan datos de contacto para coordinar fuera de la plataforma.
- No calcular montos ni retenciones en el frontend.
- Invalidar listado, detalle y badges después de `assign` y `resolve`.

---

## 7. Catálogo de errores

`detail` es el valor estable para la lógica del frontend. `message` es el texto a mostrar.

| HTTP | `detail` | Cuándo | Acción sugerida |
|---|---|---|---|
| `400` | `VALIDATION_ERROR` | Enum desconocido (`status`, `range`), rango de fechas inválido, body inválido, campo requerido faltante o demasiado largo. | Mostrar `message`; no reintentar. |
| `400` | `MISSING_PARAMETER` | Falta un query param obligatorio. | Corregir la llamada. |
| `401` | texto `Se requiere autenticación para acceder a este recurso` | Sin token o token inválido. | Redirigir al login. |
| `401` | `TOKEN_EXPIRED` | Token vencido. | Redirigir al login. |
| `403` | `ACCESS_DENIED` | El usuario no es `ADMIN`. | Redirigir a su home. |
| `403` | `NOT_ASSIGNED_TO_ADMIN` | El caso está asignado a otro administrador. | Refrescar el detalle y pasar a modo lectura. |
| `404` | `MATERIAL_REPORT_CASE_NOT_FOUND` | La denuncia no existe. | Volver al listado. |
| `404` | `FILE_NOT_FOUND` | El archivo denunciado ya no está en el storage. | Avisar que no se puede descargar. |
| `404` | `CLAIM_NOT_FOUND` | El reclamo no existe. | Volver al listado. |
| `404` | `SESSION_NOT_FOUND` | La reserva asociada no existe (dato inconsistente). | Reintentar más tarde / escalar. |
| `404` | `ADMIN_NOT_FOUND` | El administrador autenticado no existe (dato inconsistente). | Cerrar sesión y volver a entrar. |
| `409` | `CASE_ALREADY_TAKEN` | Otro admin tomó la solicitud. | Refrescar; pasar a modo lectura. |
| `409` | `CASE_NOT_PENDING` | La denuncia ya no está pendiente. | Refrescar el listado. |
| `409` | `CASE_NOT_IN_REVIEW` | Se intentó resolver sin tomarla, o consultar contacto fuera de revisión. | Tomar la solicitud primero. |
| `409` | `CASE_ALREADY_RESOLVED` | La solicitud ya fue resuelta. | Refrescar; modo lectura. |
| `409` | `BOOKING_IMMUTABLE` | Los fondos de la reserva ya están en estado terminal. | Mostrar `message`; no reintentar. |
| `409` | `FUNDS_STATE_MISMATCH` | Los fondos no están suspendidos por este reclamo. | Mostrar `message`; escalar. |
| `502` | `FUNDS_EXECUTION_FAILED` | Falló el movimiento de fondos. | Mantener el diálogo y permitir reintentar. |
| `500` | texto genérico | Error inesperado. | Mostrar `message`; permitir reintentar. |

Códigos de validación de body (`400`): el campo `message` trae `campo: mensaje` (por ejemplo `justification: Justification must be at most 1000 characters`) y `detail` siempre es `VALIDATION_ERROR`.

---

## 8. Reglas de integración (React Query)

Claves sugeridas:

```ts
['admin', 'badges']
['admin', 'dashboard', range]
['admin', 'material-reports', filters]
['admin', 'material-reports', caseId]
['admin', 'session-claims', filters]
['admin', 'session-claims', claimId]
['admin', 'session-claims', claimId, 'contact-info']
```

- `staleTime: 0` en todos los endpoints admin; no hay caché de servidor.
- Badges: `refetchInterval: 60_000` y `refetchOnWindowFocus: true`.
- Invalidar `['admin', 'badges']` y `['admin', 'dashboard']` después de `assign`, `remove`, `dismiss` o `resolve`.
- Invalidar el listado y el detalle del módulo afectado después de cada mutación.
- `contact-info`: `gcTime: 0`, `staleTime: 0`, sin `persistQueryClient`.
- `502` en `resolve`: no invalidar nada; el backend hizo rollback.
- No calcular porcentajes, niveles, deltas ni montos en el cliente.
- `assignedToMe` afecta tanto a `content` como a `statusCounts`.
- `size` máximo `50`; si el diseño pide 4 por página, enviar `size=4`.

---

## 9. Datos de prueba y documentación

- **Swagger UI:** `http://localhost:8080/swagger-ui.html` (OpenAPI JSON en `/api-docs`) (tags "Admin - Denuncias de material", "Admin reclamos de sesión", "Admin navegación y home").
- **Admin demo local:** `docs/seeds/seed-admin-demo.sql`
  - Email: `admin.demo@knowlink.test`
  - Contraseña: `Admin123!`
  - Rol: `ADMIN`, estado `ACTIVE`. El script es idempotente y no debe usarse en producción.
- **Variables de entorno de producción:** `KNOWLINK_ADMIN_ACCOUNTS_<n>_EMAIL`, `KNOWLINK_ADMIN_ACCOUNTS_<n>_PASSWORD`, `KNOWLINK_ADMIN_ACCOUNTS_<n>_FULL_NAME`.

---

## 10. Historial de cambios

| Fecha | Cambio |
|---|---|
| 29/10/2026 | Versión inicial: auth admin, badges, home, denuncias de material (US-23) y reclamos de sesión (US-50). |