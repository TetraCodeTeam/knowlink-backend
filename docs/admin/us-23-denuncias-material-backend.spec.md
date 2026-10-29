# US-23 — Gestionar denuncias de material inapropiado (Backend)

## Metadata

| Campo | Valor |
|---|---|
| ID Historia | US-23 |
| Módulo backend | `com.knowlink.api.admin.materialreports` |
| Repo / rama base | `TetraCodeTeam/knowlink-backend` / `main` |
| Relacionadas | US-16 y US-17 (material), **US-27 y US-47 (denuncia del alumno)**, US-30 (badge), US-33 (home), US-46 (seguimiento del tutor), Admin 00 (base) |
| Depende de | `admin-00` (seguridad, rango de fechas, excepciones de conflicto, asignación atómica) |
| Stack | Java 21 · Spring Boot 3.5.3 · JPA/MySQL · Supabase Storage (URL firmada) · `IEmailService` (Brevo) |
| Consumidor | Frontend `admin-03-denuncias-material` |

---

## 1. Estado actual verificado y advertencias de dependencia

- `AcademicMaterial` (`tutors/data/models`) ya tiene `reportsCount` (Integer), `available` (boolean), `active` (boolean), `materialType` (`PDF | XLSX | PNG`), `storagePath`, `name`, `originalFileName`, `tutorSubject`. Los listados de material (`IAcademicMaterialRepository`) filtran por `active = true` y `available = true`.
- `MaterialServiceImpl.getDownloadUrl` ya genera URL firmada de Supabase (`generateSignedUrl(storagePath, 3600)`), pero con control de acceso pensado para alumno/tutor. **No sirve tal cual para el admin** (ver 4.6).
- `User` tiene `fullName`, `dni`, `phoneNumber`; `TutorProfile` tiene `career`, `institutionalId` (legajo).
- **No existe en `main` ninguna entidad de denuncia** (`MaterialReport`) ni el flujo del alumno que denuncia (US-27, US-47). El contador `reportsCount` existe pero nadie lo incrementa todavía.
- ⚠️ **Coordinación con US-27/US-47:** este spec define las entidades `MaterialReportCase` y `MaterialReport` y un servicio de ingreso (`IMaterialReportIntakeService`). El flujo del alumno **debe** crear sus denuncias a través de ese servicio. Si US-27 ya tiene una entidad propia en otra rama, hay que unificar antes de implementar y ajustar este spec.
- ⚠️ Los **motivos** de denuncia (`MaterialReportReason`) deben coincidir con los que ofrece US-27. Los valores de abajo son provisionales.
- ⚠️ `reportsCount` es un contador desnormalizado de `AcademicMaterial`. **El administrador no depende de él**: la cantidad de denuncias de un caso se calcula con los `MaterialReport` del caso. No modificarlo desde acá salvo que US-27 defina su semántica.

---

## 2. Criterios de aceptación (Gherkin)

```gherkin
Feature: Gestión de denuncias de material por el administrador

  Background:
    Given un usuario con rol ADMIN autenticado

  Scenario: Listado paginado por estado
    When consulta las denuncias
    Then recibe los archivos con al menos una denuncia, uno por caso, paginados
    And puede filtrar por estado: PENDING, IN_REVIEW, REMOVED, DISMISSED o todos
    And recibe los contadores por estado (para el contador de "Pendiente")

  Scenario: Rango de fechas por defecto
    When no indica rango
    Then se listan los casos abiertos en los últimos 30 días
    When indica "from" y "to"
    Then se listan los casos abiertos dentro de ese rango

  Scenario: Solo asignadas a mí
    When consulta con assignedToMe = true
    Then solo recibe casos asignados a su cuenta

  Scenario: Datos de cada entrada
    Then cada entrada incluye: archivo, tipo, materia, tutor, cantidad de denuncias, estado y fecha de la primera denuncia
    And si está IN_REVIEW incluye el administrador asignado

  Scenario: Orden de prioridad
    Then el listado se ordena por cantidad de denuncias descendente
    And a igual cantidad, el caso abierto hace más tiempo va primero

  Scenario: Detalle del caso
    When consulta un caso
    Then recibe nombre, carrera, DNI y legajo institucional del tutor que cargó el archivo
    And el archivo, la materia y todas las denuncias con motivo, fecha y descripción si existe
    And no recibe la identidad de quienes denunciaron

  Scenario: Descargar el archivo antes de decidir
    When pide la URL de descarga
    Then recibe una URL firmada con vencimiento
    And puede hacerlo en cualquier estado del caso, también si no lo tomó

  Scenario: Tomar solicitud
    Given un caso PENDING
    When el administrador lo toma
    Then el caso pasa a IN_REVIEW asignado a su cuenta
    And otro administrador no puede tomarlo (409 CASE_ALREADY_TAKEN)

  Scenario: Lectura de un caso asignado a otro administrador
    Given un caso IN_REVIEW asignado a otro administrador
    When un administrador distinto lo consulta
    Then lo ve en modo lectura (permissions.canResolve = false)
    And si intenta resolverlo recibe 403 NOT_ASSIGNED_TO_ADMIN

  Scenario: Dar de baja
    Given un caso IN_REVIEW asignado a mí
    When doy de baja el material con un motivo opcional
    Then el material deja de estar disponible para todos los usuarios de inmediato
    And el caso pasa a REMOVED con resolvedAt y, si hubo motivo, resolutionNote
    And se notifica al tutor que lo cargó (motivo incluido si hubo) y a los denunciantes que su reporte fue procesado

  Scenario: Dar de baja sin motivo
    When doy de baja sin enviar motivo
    Then resolutionNote es null
    And el mail al tutor no incluye motivo

  Scenario: Desestimar
    Given un caso IN_REVIEW asignado a mí
    When lo desestimo
    Then el material permanece disponible sin cambios
    And el caso pasa a DISMISSED ("No procedente")
    And se notifica a los involucrados del resultado

  Scenario: Reapertura por denuncias posteriores
    Given un caso DISMISSED sobre un material
    When un alumno vuelve a denunciar ese material
    Then se crea un caso nuevo PENDING con la nueva denuncia
    And el caso anterior queda como historial

  Scenario: Nuevas denuncias sobre un caso abierto
    Given un caso PENDING o IN_REVIEW
    When llega otra denuncia del mismo material
    Then se agrega al caso existente y aumenta su cantidad de denuncias

  Scenario: Caso ya resuelto
    Given un caso REMOVED o DISMISSED
    When se intenta tomar o resolver
    Then recibe 409 CASE_NOT_PENDING / CASE_ALREADY_RESOLVED
    And el estado no cambia

  Scenario: Resolución unificada
    Given un caso con 4 denuncias
    When se da de baja o se desestima
    Then las 4 denuncias quedan resueltas con la misma decisión

  Scenario: Sin denuncias para los filtros
    Then content es una lista vacía y totalElements = 0
```

---

## 3. Modelo de datos

### 3.1 `MaterialReportCase` (nueva) — una gestión por archivo

```java
@Entity
@Table(name = "material_report_case", indexes = {
    @Index(name = "idx_mrc_status", columnList = "status"),
    @Index(name = "idx_mrc_status_opened", columnList = "status, opened_at"),
    @Index(name = "idx_mrc_resolved_at", columnList = "resolved_at"),
    @Index(name = "idx_mrc_material", columnList = "academic_material_id")
})
public class MaterialReportCase {
    @Id @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "material_report_case_id", updatable = false, nullable = false)
    private UUID materialReportCaseId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "academic_material_id", nullable = false)
    private AcademicMaterial academicMaterial;

    @Enumerated(EnumType.STRING) @Column(nullable = false)
    private MaterialReportCaseStatus status;            // PENDING, IN_REVIEW, REMOVED, DISMISSED

    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "assigned_admin_id")
    private User assignedAdmin;
    @Column(name = "assigned_at") private LocalDateTime assignedAt;

    @Column(name = "opened_at", nullable = false) private LocalDateTime openedAt;     // fecha de la primera denuncia
    @Column(name = "resolved_at") private LocalDateTime resolvedAt;
    @Column(name = "resolution_note", length = 500) private String resolutionNote;     // solo REMOVED, opcional

    @Version private Long version;
}
```

Reglas: **a lo sumo un caso abierto (`PENDING`/`IN_REVIEW`) por material** (se garantiza en el servicio de ingreso, con consulta + restricción lógica; no se puede con un índice único parcial en MySQL 8.1, así que el ingreso corre en transacción y usa bloqueo pesimista sobre el material).

### 3.2 `MaterialReport` (nueva) — cada denuncia

```java
@Entity @Table(name = "material_report")
public class MaterialReport {
    @Id @GeneratedValue(strategy = GenerationType.UUID) UUID materialReportId;
    @ManyToOne(fetch = LAZY, optional = false) MaterialReportCase reportCase;
    @ManyToOne(fetch = LAZY, optional = false) User reporter;           // nunca se expone al admin ni al tutor
    @Enumerated(EnumType.STRING) MaterialReportReason reason;           // provisional, alinear con US-27
    @Column(columnDefinition = "TEXT") String description;              // nullable
    @Column(nullable = false) LocalDateTime createdAt;
}
```

Restricción sugerida: un mismo alumno no puede denunciar dos veces el mismo caso abierto (lo define US-27; el índice único `(report_case_id, reporter_id)` lo hace cumplir).

### 3.3 Enums (`admin/materialreports/data/enums`)

```java
public enum MaterialReportCaseStatus { PENDING, IN_REVIEW, REMOVED, DISMISSED }
public enum MaterialReportReason { PLAGIARISM, INAPPROPRIATE_CONTENT, COPYRIGHT_INFRINGEMENT, WRONG_CONTENT, OTHER } // provisional
```

### 3.4 Servicio de ingreso (contrato con US-27/US-47)

```java
public interface IMaterialReportIntakeService {
    /** Registra una denuncia: la agrega al caso abierto del material o abre uno nuevo (PENDING). */
    MaterialReport registerReport(UUID materialId, UUID reporterUserId,
                                  MaterialReportReason reason, String description);
}
```

- Si el material no existe o `active = false` → `ResourceNotFoundException`.
- Si existe un caso `PENDING`/`IN_REVIEW` → se agrega la denuncia a ese caso.
- Si no hay caso abierto → crea `MaterialReportCase` en `PENDING` con `openedAt = now`.
- Es la **única** puerta de entrada de denuncias; las historias del alumno la invocan.

---

## 4. Endpoints

Prefijo `/api/v1/admin/material-reports`, controller con `@PreAuthorize("hasRole('ADMIN')")` (Admin 00). Identificador: **`caseId`**.

| Método | Ruta | Descripción |
|---|---|---|
| `GET` | `/` | Listado paginado de casos |
| `GET` | `/{caseId}` | Detalle |
| `POST` | `/{caseId}/assign` | Tomar solicitud |
| `POST` | `/{caseId}/remove` | Dar de baja el material |
| `POST` | `/{caseId}/dismiss` | Desestimar |
| `GET` | `/{caseId}/download-url` | URL firmada para auditar el archivo |

### 4.1 Listado

Parámetros: `status` (opcional, uno de los 4 estados), `assignedToMe` (boolean, default `false`), `from`/`to` (`LocalDate`, `DateRangeResolver`), `page`, `size`.

Filtro de fechas sobre `openedAt`. Orden fijo: `reportsCount DESC, openedAt ASC`.

```java
public record MaterialReportCaseListItemResponse(
        UUID caseId, UUID materialId,
        String fileName, MaterialType materialType,
        String subjectName, String tutorName,
        int reportsCount,
        MaterialReportCaseStatus status,
        LocalDateTime openedAt,            // primera denuncia; el frontend calcula "Hace 2 horas"
        AssignedAdminResponse assignedAdmin,   // null si no hay
        boolean assignedToMe,
        LocalDateTime resolvedAt,          // null si abierto
        String resolutionNote              // solo REMOVED con motivo
) {}

public record AssignedAdminResponse(UUID adminId, String fullName) {}

public record MaterialReportCaseListResponse(
        PagedResponse<MaterialReportCaseListItemResponse> page,
        StatusCountsResponse statusCounts
) {}

public record StatusCountsResponse(long pending, long inReview, long removed, long dismissed) {}
```

- `statusCounts` se calcula con el **mismo rango de fechas y el mismo `assignedToMe`** pero **ignorando `status`**, para que el contador de la pestaña "Pendiente" sea coherente con el listado. (El badge del sidebar, en cambio, ignora fechas: ver US-30.)
- `fileName`: `originalFileName` si existe, si no `name`.
- Para el desempate usar subconsulta de conteo; no cargar las denuncias en memoria.

### 4.2 Detalle

```java
public record MaterialReportCaseDetailResponse(
        MaterialReportCaseListItemResponse summary,
        TutorOwnerResponse tutor,
        MaterialInfoResponse material,
        List<MaterialReportItemResponse> reports,   // más recientes primero
        PermissionsResponse permissions
) {}

public record TutorOwnerResponse(String fullName, String career, String dni, String institutionalId) {}
public record MaterialInfoResponse(String fileName, MaterialType materialType, Long sizeInBytes, LocalDateTime uploadedAt) {}
public record MaterialReportItemResponse(MaterialReportReason reason, LocalDateTime reportedAt, String description) {} // description null si no hay
public record PermissionsResponse(boolean canTake, boolean canResolve) {}
```

- `canTake` = estado `PENDING`. `canResolve` = estado `IN_REVIEW` **y** asignado al administrador que consulta. El frontend usa estos booleanos en vez de recalcular la regla (criterio 9).
- **No** se incluye `reporter` en ninguna respuesta.
- DNI y legajo salen de `User.dni` y `TutorProfile.institutionalId`; solo se devuelven a ADMIN.

### 4.3 Tomar solicitud — `POST /{caseId}/assign`

Sin body. Usa la actualización condicional atómica de Admin 00 (4.7). Respuestas: `200` con `MaterialReportCaseDetailResponse`; `404` si no existe; `409 CASE_ALREADY_TAKEN` si ya estaba `IN_REVIEW`; `409 CASE_NOT_PENDING` si estaba cerrado. Registrar `CASE_TAKEN` en auditoría.

### 4.4 Dar de baja — `POST /{caseId}/remove`

```java
public record RemoveMaterialRequest(
        @Size(max = 500, message = "Reason must be at most 500 characters") String reason  // opcional
) {}
```

En **una transacción**:
1. Validar con update condicional: `status = IN_REVIEW AND assignedAdmin = :me`. Si no: `403 NOT_ASSIGNED_TO_ADMIN` o `409 CASE_ALREADY_RESOLVED`.
2. Caso → `REMOVED`, `resolvedAt = now`, `resolutionNote = reason?.trim()` (vacío → `null`).
3. Material → `active = false` **y** `available = false` (⚠️ verificar la semántica de `available` en US-16/US-26 antes de fijarla; mínimo imprescindible: `active = false`, que ya excluye al material de los listados).
4. Publicar `MaterialReportResolvedEvent` (ver 5). Registrar `MATERIAL_REMOVED`.

Efectos sobre el resto del sistema (verificar con tests): el material desaparece de `listBySubject` y de las búsquedas, y **su descarga deja de funcionar para alumnos y tutores**: `MaterialServiceImpl.getDownloadUrl` ya consulta con `findByAcademicMaterialIdAndActiveTrue`, así que `active = false` alcanza y no hay que tocarlo. Por eso mismo el admin necesita su propio camino de descarga (4.6): tras la baja, ese método respondería 404. El archivo **no se borra de Supabase** (decisión: conservarlo para auditoría; un borrado físico posterior sería otra historia). Respuesta `200` con el detalle actualizado.

### 4.5 Desestimar — `POST /{caseId}/dismiss`

Sin body. Misma validación atómica. Caso → `DISMISSED`, `resolvedAt = now`, `resolutionNote = null`. El material **no se toca**. Publica el evento. Respuesta `200` con el detalle. *(Los criterios de aceptación no piden una nota al desestimar; el texto de la entrada "Contenido válido bajo revisión académica" lo arma el frontend a partir del estado.)*

### 4.6 Descarga — `GET /{caseId}/download-url`

```java
public record DownloadUrlResponse(String url, int expiresInSeconds) {}   // 3600
```

- Nuevo método en un servicio del módulo (`IAdminMaterialDownloadService`) que reutiliza `ISupabaseStorageService.generateSignedUrl(storagePath, 3600)`. **No** reutilizar `MaterialServiceImpl.getDownloadUrl` (su control de acceso es para alumno/tutor y rechazaría materiales dados de baja).
- Permitido en cualquier estado del caso y para cualquier administrador (criterio 7: se descarga **antes** de decidir, es decir, antes o después de tomar). Si el objeto no existe en storage → `404` con `detail: "FILE_NOT_FOUND"`.
- No loguear ni persistir la URL. Registrar `MATERIAL_DOWNLOADED` (admin, caso).

---

## 5. Notificaciones

Evento publicado tras resolver (se consume `AFTER_COMMIT`):

```java
public record MaterialReportResolvedEvent(
        UUID caseId, MaterialReportCaseStatus resolution,   // REMOVED o DISMISSED
        UUID tutorUserId, String materialName, String removalReason,   // removalReason solo si REMOVED y no vacío
        Set<UUID> reporterUserIds                                       // denunciantes distintos
) {}
```

Listener `MaterialReportNotificationListener` + métodos nuevos en `IEmailService` (Facade) con plantillas en `EmailBuilder`:

| Destinatario | `REMOVED` | `DISMISSED` |
|---|---|---|
| Tutor que cargó el archivo | "Tu material «X» fue retirado por incumplimiento de las normas." + "Motivo: …" solo si hubo | "Tu material «X» fue revisado y se mantiene disponible." |
| Cada denunciante (sin repetir) | "Procesamos tu reporte sobre «X»: el material fue retirado." | "Procesamos tu reporte sobre «X»: no encontramos infracciones." |

- Nunca se revela al tutor quién denunció, ni los motivos individuales.
- Si un envío falla: log y seguir (comportamiento ya definido para `IEmailService`).
- El evento deja el punto de enganche para el centro de notificaciones in-app (US-35), que no está en `main`.

---

## 6. Códigos de error

| HTTP | `detail` | Cuándo |
|---|---|---|
| 400 | `VALIDATION_ERROR` | Rango inválido, `reason` demasiado largo, `status` desconocido |
| 403 | `NOT_ASSIGNED_TO_ADMIN` | Resolver un caso asignado a otro admin |
| 403 | `ACCESS_DENIED` | Rol distinto de ADMIN |
| 404 | `MATERIAL_REPORT_CASE_NOT_FOUND` | Caso inexistente (`ResourceNotFoundException` de 3 parámetros) |
| 404 | `FILE_NOT_FOUND` | El archivo ya no está en storage |
| 409 | `CASE_ALREADY_TAKEN` / `CASE_NOT_PENDING` / `CASE_ALREADY_RESOLVED` | Estados incompatibles |

## 7. Estructura de paquetes

```
com.knowlink.api.admin.materialreports/
├── controllers/
│   ├── interfaces/IAdminMaterialReportController.java
│   ├── implementations/AdminMaterialReportControllerImpl.java
│   ├── requests/RemoveMaterialRequest.java
│   └── responses/…                       # records de la sección 4
├── services/
│   ├── interfaces/{IAdminMaterialReportService, IMaterialReportIntakeService, IAdminMaterialDownloadService}.java
│   └── implementations/…
├── repositories/{IMaterialReportCaseRepository, IMaterialReportRepository, IMaterialReportMetricsRepository}.java
├── data/
│   ├── models/{MaterialReportCase, MaterialReport}.java
│   ├── enums/{MaterialReportCaseStatus, MaterialReportReason}.java
│   └── mappers/MaterialReportCaseMapper.java
└── listeners/MaterialReportNotificationListener.java
```

`IMaterialReportMetricsRepository` es el puerto de lectura que consume el dashboard (US-33).

## 8. Impacto en el frontend (`admin-03`)

- Usar **`caseId`** (no `materialId`) en rutas y claves de caché.
- Estados reales del backend: `PENDING | IN_REVIEW | REMOVED | DISMISSED`.
- Filtro de fechas: el botón de calendario del diseño es **el filtro de rango** (por defecto 30 días); enviar `from`/`to`.
- Descarga: el campo "Recurso" llama a `/download-url` y abre la URL firmada.
- Modo lectura: usar `permissions.canTake` / `permissions.canResolve`.
- Desestimar **no lleva nota**: el diálogo no necesita campo de texto. Dar de baja lleva `reason` opcional (máx. 500).
- `statusCounts` viene en el mismo response del listado.

## 9. No hacer

- No devolver entidades JPA ni exponer `reporter` (identidad de los denunciantes).
- No borrar el archivo de Supabase al dar de baja.
- No reutilizar `getDownloadUrl` de alumno/tutor para el admin.
- No resolver la asignación leyendo y guardando: usar el update condicional.
- No dejar que el administrador elija entre baja y desestimar sin estar asignado.
- No crear denuncias desde este módulo: lo hace `IMaterialReportIntakeService` (US-27/US-47).
- No tocar `AcademicMaterial.reportsCount` (semántica de US-27).
- No exponer DNI/legajo en endpoints que no sean el detalle admin.
- No loguear la URL firmada ni datos personales.

## 10. Tests

- `MaterialReportIntakeServiceTest`: agrega a caso abierto; abre caso nuevo tras `DISMISSED`; rechaza material inactivo.
- `AdminMaterialReportServiceTest`: listado (filtros, rango por defecto, orden por cantidad y desempate), `statusCounts` ignorando `status`, permisos `canTake`/`canResolve`.
- Concurrencia: dos hilos hacen `assign` → un solo ganador, el otro `CASE_ALREADY_TAKEN`.
- `remove`: caso `REMOVED`, material `active = false`, evento publicado, motivo vacío → `null`, descarga de alumno bloqueada.
- `dismiss`: material intacto, caso `DISMISSED`, evento publicado.
- Resolver con otro admin → 403; resolver caso ya cerrado → 409.
- Listener: contenido del mail según resolución y con/sin motivo; sin repetir denunciantes; nunca incluye identidad de denunciantes.
- Controller (MockMvc): 401/403/404/409, validación de `reason` > 500.
- Repositorio (`@DataJpaTest`): filtros por rango y por `assignedToMe`.

## 11. Definition of Done

- [ ] `./mvnw verify -B` en verde
- [ ] Entidades `MaterialReportCase`/`MaterialReport` e `IMaterialReportIntakeService` acordados con US-27/US-47
- [ ] Los 6 endpoints documentados en Swagger (español) y probados
- [ ] Baja inmediata: el material desaparece de listados y de la descarga de alumno/tutor
- [ ] Notificaciones por mail al tutor y a los denunciantes sin filtrar identidades
- [ ] Auditoría (o logs estructurados) de toma, descarga, baja y desestimación
- [ ] Puerto `IMaterialReportMetricsRepository` entregado para US-33
- [ ] Contrato publicado al frontend (`admin-03` actualizado)
