# US-30 + US-33 — Badges del navbar y Home del administrador (Backend)

## Metadata

| Campo | Valor |
|---|---|
| Historias | **US-30** Navbar en modo administrador (módulo navegación) · **US-33** Home modo administrador (módulo home) |
| Módulo backend | `com.knowlink.api.admin.dashboard` |
| Repo / rama base | `TetraCodeTeam/knowlink-backend` / `main` |
| Relacionadas | US-23 (denuncias de material), US-50 (reclamos de sesiones), Admin 00 (base) |
| Depende de | `admin-00` (seguridad, `DateRangeResolver`), `us-23` y `us-50` (entidades `MaterialReportCase` y `SessionClaim` con estado y fechas) |
| Consumidor | Frontend `admin-01` (badges del sidebar) y `admin-02` (Home) |

> Solo **lectura**: este módulo no escribe nada. Es un conjunto de consultas agregadas sobre las colas de US-23 y US-50.

---

## 1. Dependencias y advertencias

- **No se puede implementar completo hasta que existan las entidades de US-23 y US-50.** Se pueden construir los endpoints, DTOs y tests con las interfaces de repositorio (`countByStatus…`) mientras tanto, pero las consultas JPQL dependen de los campos definidos en esos specs: estado, fecha de apertura (`openedAt`/`createdAt`) y fecha de resolución (`resolvedAt`).
- Convención de estados usados acá (ver specs 03 y 04):
  - Denuncias: `PENDING`, `IN_REVIEW`, `REMOVED`, `DISMISSED` (resueltas = `REMOVED` o `DISMISSED`).
  - Reclamos: `OPEN` (Pendiente), `IN_REVIEW`, `RESOLVED`.
- Zona horaria: toda ventana temporal se calcula con `AppTimeZone.ZONE` (`America/Argentina/Cordoba`).
- **Sin caché** (ni Caffeine ni otra): el frontend invalida estas consultas justo después de tomar o resolver una solicitud y necesita ver el dato nuevo.

---

## 2. Criterios de aceptación (Gherkin)

### US-30 — Badges

```gherkin
Feature: Badges numéricos del navbar de administrador

  Scenario: Hay tareas pendientes
    Given 3 denuncias PENDING y 2 reclamos OPEN
    When el administrador consulta los badges
    Then recibe pendingMaterialReports = 3 y pendingSessionClaims = 2

  Scenario: No hay tareas pendientes
    Given que no hay denuncias PENDING ni reclamos OPEN
    Then ambos contadores son 0

  Scenario: Los contadores no dependen de filtros de fecha ni de asignación
    Given un reclamo OPEN abierto hace 45 días
    Then se cuenta igual en pendingSessionClaims

  Scenario: Las tareas en revisión no cuentan
    Given una denuncia IN_REVIEW asignada a otro administrador
    Then no se suma al badge de nadie

  Scenario: Acceso
    Given un usuario STUDENT o TUTOR
    When consulta los badges
    Then recibe 403
```

### US-33 — Home

```gherkin
Feature: Resumen de la Home del administrador

  Scenario: Bloque de reclamos de sesiones
    When el administrador consulta el resumen
    Then el bloque de reclamos incluye resueltas de la semana, su variación porcentual contra la semana previa,
         nuevas de la semana que siguen pendientes y cuántos días espera el reclamo pendiente más antiguo

  Scenario: Bloque de denuncias de material
    Then el bloque de denuncias incluye resueltas de la semana, su variación, nuevas de la semana que siguen pendientes
    And el archivo pendiente con mayor cantidad de reportes sin revisar y esa cantidad

  Scenario: Sin pendientes en una categoría
    Given que no hay reclamos OPEN
    Then pendingCount = 0 y oldestPendingDays = null
    And el frontend muestra "Sin pendientes por ahora"
    Given que no hay denuncias PENDING
    Then pendingCount = 0 y mostReportedPendingFile = null

  Scenario: Variación sin base de comparación
    Given que la semana previa no tuvo resoluciones
    Then resolvedDeltaPct = null (no se divide por cero)

  Scenario: Rendimiento semanal
    When pide range = WEEK
    Then avgResolutionHours, su variación, unresolvedPct y volumeSplit se calculan sobre los últimos 7 días
    And las variaciones comparan contra los 7 días inmediatamente anteriores

  Scenario: Rendimiento mensual
    When pide range = MONTH
    Then se calculan sobre los últimos 30 días y se comparan contra los 30 días inmediatamente anteriores

  Scenario: Nivel de tareas sin resolver
    Then unresolvedLevel es UNDER_CONTROL si unresolvedPct < 15
    And ACCEPTABLE si 15 <= unresolvedPct < 30
    And RISKY si 30 <= unresolvedPct < 60
    And CRITICAL si unresolvedPct >= 60

  Scenario: Distribución del volumen
    Given 6 casos nuevos de reclamos y 4 de denuncias en el período
    Then volumeSplit = { claimsPct: 60, reportsPct: 40 }
    And la suma siempre es 100 cuando hay volumen
    And es { 0, 0 } si no hubo casos nuevos

  Scenario: Rango inválido
    When range no es WEEK ni MONTH
    Then recibe 400
```

---

## 3. Endpoints

Prefijo `/api/v1/admin`, controller con `@PreAuthorize("hasRole('ADMIN')")` (Admin 00).

| Método | Ruta | Descripción |
|---|---|---|
| `GET` | `/api/v1/admin/navigation/badges` | Contadores de pendientes para el sidebar |
| `GET` | `/api/v1/admin/dashboard/summary?range=WEEK\|MONTH` | Resumen de la Home (`range` por defecto `WEEK`) |

### 3.1 Badges

```java
public record AdminNavigationBadgesResponse(
        int pendingMaterialReports,   // MaterialReportCase en PENDING (sin ventana de fechas)
        int pendingSessionClaims      // SessionClaim en OPEN (sin ventana de fechas)
) {}
```

Se cuentan **todas** las pendientes, independientemente del filtro de 30 días de los listados, de quién sea el administrador y de la asignación. El frontend las refresca al enfocar la ventana y cada ~60 s.

### 3.2 Resumen

```java
public enum DashboardRange { WEEK, MONTH }

public enum UnresolvedLevel { UNDER_CONTROL, ACCEPTABLE, RISKY, CRITICAL }

public record DashboardSummaryResponse(
        SessionClaimsBlock sessionClaims,
        MaterialReportsBlock materialReports,
        PerformanceBlock performance
) {}

public record SessionClaimsBlock(
        int resolvedThisWeek,
        Double resolvedDeltaPct,        // null si la semana previa tuvo 0 resueltas
        int newThisWeek,                // creadas en los últimos 7 días que siguen OPEN
        int pendingCount,               // todas las OPEN
        Integer oldestPendingDays       // null si pendingCount = 0
) {}

public record MaterialReportsBlock(
        int resolvedThisWeek,
        Double resolvedDeltaPct,
        int newThisWeek,                // casos abiertos en los últimos 7 días que siguen PENDING
        int pendingCount,
        MostReportedFile mostReportedPendingFile   // null si pendingCount = 0
) {}

public record MostReportedFile(UUID caseId, UUID materialId, String fileName, int reportsCount) {}

public record PerformanceBlock(
        DashboardRange range,
        Double avgResolutionHours,      // null si no hubo resoluciones en el período
        Double avgResolutionDeltaPct,   // positivo = más rápido que el período anterior; null sin base
        double unresolvedPct,           // 0–100, 1 decimal
        UnresolvedLevel unresolvedLevel,
        VolumeSplit volumeSplit
) {}

public record VolumeSplit(int claimsPct, int reportsPct) {}
```

Los nombres `resolvedThisWeek`/`newThisWeek` se mantienen porque son los que propuso el spec de frontend `admin-02`; los bloques de las dos tarjetas son **siempre semanales** (US-33 criterio 2) y solo el panel de rendimiento responde a `range` (criterio 7).

---

## 4. Definiciones de cálculo (fuente única de verdad)

**Ventanas** (todas con `AppTimeZone.ZONE`, intervalos `[inicio, fin)`):

| Nombre | Definición |
|---|---|
| `last7` | `[ahora − 7 días, ahora)` |
| `prev7` | `[ahora − 14 días, ahora − 7 días)` |
| `last30` | `[ahora − 30 días, ahora)` |
| `prev30` | `[ahora − 60 días, ahora − 30 días)` |
| Período actual del panel | `last7` si `WEEK`, `last30` si `MONTH` |
| Período anterior | `prev7` si `WEEK`, `prev30` si `MONTH` |

Se usan **ventanas móviles** y no semana calendario: comparar "lunes a hoy" contra una semana completa distorsionaría la variación los primeros días. *(Decisión del spec: confirmar con el equipo si prefieren semana calendario.)*

**Fórmulas**

| Métrica | Fórmula |
|---|---|
| Resueltas | Casos con `resolvedAt` dentro de la ventana y estado final (`RESOLVED`, `REMOVED`, `DISMISSED`) |
| `resolvedDeltaPct` | `(actual − anterior) / anterior × 100`, redondeo 1 decimal; `null` si `anterior = 0` |
| `newThisWeek` | Casos con fecha de apertura en `last7` cuyo estado actual es `OPEN`/`PENDING` |
| `oldestPendingDays` | `floor((ahora − openedAt del reclamo OPEN más antiguo) / 24 h)`; `null` si no hay |
| `mostReportedPendingFile` | Caso `PENDING` con mayor cantidad de `MaterialReport`; desempate: el más antiguo |
| `avgResolutionHours` | Promedio de `(resolvedAt − openedAt)` en horas de los casos **de ambos tipos** resueltos en el período; 1 decimal |
| `avgResolutionDeltaPct` | `(promedioAnterior − promedioActual) / promedioAnterior × 100` (positivo = más rápido); `null` si falta alguno |
| `unresolvedPct` | `casos abiertos en el período cuyo estado actual NO es final / casos abiertos en el período × 100`; `0` si no hubo casos; 1 decimal |
| `unresolvedLevel` | Umbrales de US-33 criterio 8 (límite inferior inclusivo: 15, 30 y 60 pertenecen al nivel superior) |
| `volumeSplit` | Casos abiertos en el período por tipo / total × 100, redondeado por **mayor resto** para que sumen 100 |

> **Decisión a confirmar:** US-33 no define exactamente qué es "porcentaje de tareas sin resolver". Se propone el cálculo de arriba (tareas **creadas en el período** que siguen sin resolver) porque se mueve con el filtro Semana/30 días. Alternativa: backlog total actual sobre total histórico, que casi no cambia con el filtro.

---

## 5. Diseño e implementación

### Ubicación

```
com.knowlink.api.admin.dashboard/
├── controllers/
│   ├── interfaces/IAdminDashboardController.java       # contrato + @Operation/@ApiResponse en español
│   ├── implementations/AdminDashboardControllerImpl.java
│   └── responses/                                        # los records de arriba (un archivo por record)
├── services/
│   ├── interfaces/IAdminDashboardService.java
│   └── implementations/AdminDashboardServiceImpl.java   # orquesta; no contiene SQL
└── (sin entidades propias)
```

- `AdminDashboardServiceImpl` depende de dos **puertos de lectura** definidos por cada módulo de cola, para no acoplar el dashboard a las entidades:
  - `IMaterialReportMetricsRepository` (en `materialreports/repositories`): `countPending()`, `countResolvedBetween(from, to)`, `countOpenedBetweenWithStatus(from, to, status)`, `findMostReportedPending()`, `avgResolutionSeconds(from, to)`, `countOpenedBetween(from, to)`, `countOpenedBetweenUnresolved(from, to)`.
  - `ISessionClaimMetricsRepository` (en `claims/repositories`): análogo + `findOldestPendingOpenedAt()`.
- Consultas con `COUNT`/`AVG` agregados en la base. **Sin cargar entidades ni iterar en memoria.**
- Los cálculos puros (variación %, nivel, reparto por mayor resto) van en una clase utilitaria sin Spring (`DashboardMath`) para testearlos sin base de datos.
- Redondeo: `BigDecimal` con `RoundingMode.HALF_UP`, 1 decimal.
- Un solo request del frontend alimenta toda la Home: **un endpoint** `summary`, no uno por tarjeta.

### Rendimiento

- Índices recomendados (agregarlos en las entidades con `@Table(indexes = …)` de US-23/US-50): `(status)`, `(status, opened_at)` y `(resolved_at)` en `material_report_case` y en la tabla de reclamos.
- Las consultas son conteos acotados por ventana; no deberían requerir paginación.

---

## 6. Códigos de error

| HTTP | `detail` | Cuándo |
|---|---|---|
| 400 | `VALIDATION_ERROR` | `range` inválido |
| 401 | — | Sin token |
| 403 | `ACCESS_DENIED` | Rol distinto de ADMIN |

## 7. Impacto en el frontend

- **Admin 01 (sidebar):** el badge queda definido = `pendingMaterialReports` y `pendingSessionClaims`; se muestra solo si el valor es > 0.
- **Admin 02 (Home):** ajustar el contrato: el bloque de denuncias trae `mostReportedPendingFile` (nombre y cantidad de reportes del archivo más urgente) en vez de "1 archivo con 6 reportes"; si `pendingCount = 0` mostrar "Sin pendientes por ahora"; las etiquetas de nivel pasan a **Bajo control / Aceptable / Riesgoso / Crítico** (las mismas cuatro que define el criterio 8), con los umbrales ya resueltos acá.

## 8. No hacer

- No cachear ni materializar estas métricas.
- No calcular porcentajes ni niveles en el frontend: el backend es la fuente única.
- No dividir por cero ni devolver `NaN`/`Infinity`; usar `null`.
- No contar casos "En revisión" como pendientes.
- No exponer datos personales de tutores o alumnos en estos endpoints.
- No iterar entidades en memoria para calcular promedios.
- No agregar endpoints de escritura en este módulo.

## 9. Tests

- `DashboardMathTest`: variación (base 0 → `null`), niveles en los bordes 14.9 / 15 / 29.9 / 30 / 59.9 / 60 / 100, reparto por mayor resto (6/4 → 60/40; 1/1 → 50/50; 1/2 → 33/67; 0/0 → 0/0; siempre suma 100 si hay volumen), TTR con signo.
- `AdminDashboardServiceImplTest` (Mockito sobre los puertos): bloque sin pendientes, con pendientes, cambio de `range`.
- Test de repositorio (`@DataJpaTest`, H2): conteos por ventana con casos en los bordes de `last7`/`prev7`.
- `AdminDashboardControllerTest` (MockMvc): 200 con ADMIN, 403 con STUDENT/TUTOR, 401 sin token, 400 con `range` inválido.

## 10. Definition of Done

- [ ] `./mvnw verify -B` en verde
- [ ] `GET /navigation/badges` y `GET /dashboard/summary` documentados en Swagger (español)
- [ ] Fórmulas de la sección 4 cubiertas por tests, incluidos los bordes de umbrales y de ventanas
- [ ] Sin N+1 ni carga de entidades para métricas (verificado en logs SQL)
- [ ] Índices agregados en las entidades de casos
- [ ] Decisiones abiertas (ventana móvil vs. calendario, fórmula de "sin resolver") respondidas o anotadas en el PR
- [ ] Contrato publicado al frontend (admin-01/admin-02 actualizados)
