# Admin 00 — Base transversal del módulo `admin` (Backend)

## Metadata

| Campo | Valor |
|---|---|
| Módulo | `admin` (nuevo) — infraestructura compartida por US-30, US-33, US-23 y US-50 |
| Rol | ADMIN |
| Repo / rama base | `TetraCodeTeam/knowlink-backend` / `main` |
| Stack confirmado | Java 21 · Spring Boot 3.5.3 · Spring Security + JWT (jjwt 0.11.5) · Spring Data JPA · MySQL 8.1 · Supabase Storage · Brevo (vía `IEmailService`) |
| Se implementa | **Primero.** Los otros tres specs de backend dependen de este |
| Consumidor | `knowlink-frontend`, specs `admin-01` a `admin-04` |

**Convención de idioma** (de `docs/base-standards.md`): identificadores, mensajes de validación Jakarta y nombres de clases en **inglés**; textos para el usuario final, `message` de errores y Swagger (`@Operation`, `@Tag`) en **español**.

---

## 1. Estado actual verificado en `main`

- `Role.ADMIN` ya existe en `security/enums/Role.java`. `UserPrincipal` expone la autoridad `ROLE_ADMIN`.
- `SecurityConfig` tiene `@EnableMethodSecurity` y `anyRequest().authenticated()`. Precedente de uso: `AdminFundsControllerImpl` (`/api/v1/admin/bookings/{bookingId}/funds/retry`) con `@PreAuthorize("hasRole('ADMIN')")`.
- **El login de un ADMIN funciona sin cambios**: `AuthValidationServiceImpl.ifUserLacksProfileForTargetRoleThrowException` solo valida perfil para `TUTOR` y `STUDENT`. `AuthResponse` devuelve `role: ADMIN`, que es lo que necesita el frontend (`Admin 01`).
- **No existe ninguna forma de crear un administrador**: el registro solo crea alumnos y tutores y no hay seed de usuarios admin.
- `GlobalExceptionHandler` ya maneja `AccessDeniedException` (403), `ResourceNotFoundException` (404, 3 parámetros), `DuplicateResourceException` (409) y `ValidationException` (400). **No hay una excepción de conflicto de negocio genérica** (usar `DuplicateResourceException` para "ya fue tomado" sería semánticamente incorrecto).
- `PagedResponse<T>` (`shared/responses`) ya define `content, page, size, totalElements, totalPages, hasNext`. `AppTimeZone.ZONE` = `America/Argentina/Cordoba`.
- Notificaciones: solo existe envío por mail (Facade `IEmailService` + eventos `@TransactionalEventListener`). **No existe centro de notificaciones in-app** en `main`.
- **No existen** en `main` las entidades de denuncia de material ni de reclamo de sesión (ver specs 03 y 04). `AcademicMaterial` ya tiene `reportsCount`, `available` y `active`; `FundsTransfer` ya tiene `blockingClaimId` y `FundsStatus.SUSPENDED_BY_CLAIM`.
- `docs/base-standards.md` dice "`ADMIN` todavía no está implementado": actualizar ese párrafo al terminar esta historia.

---

## 2. Alcance

1. Crear administradores de forma controlada (`AdminSeeder`).
2. Estructura del paquete `admin` y reglas de seguridad comunes.
3. Piezas transversales reutilizables: filtro de fechas, paginación, excepciones de conflicto, "tomar solicitud" sin condición de carrera, eventos de notificación, auditoría mínima.

No incluye endpoints de negocio (specs 02, 03, 04).

---

## 3. Criterios de aceptación (Gherkin)

```gherkin
Feature: Base del módulo de administración

  Scenario: Creación de administradores al iniciar la aplicación
    Given variables de configuración con email, contraseña y nombre de uno o más administradores
    And no existe un usuario con ese email
    When la aplicación inicia
    Then se crea un usuario con rol ADMIN, AccountStatus ACTIVE y contraseña hasheada
    And se registra en el log "Admin seeded" sin imprimir la contraseña

  Scenario: Seeder idempotente
    Given que el administrador ya existe
    When la aplicación vuelve a iniciar
    Then no se modifica ni se duplica el usuario

  Scenario: Sin configuración de administradores
    Given que no hay cuentas admin configuradas
    When la aplicación inicia
    Then no falla y se registra una advertencia

  Scenario: Login de administrador
    Given un usuario ADMIN activo
    When inicia sesión en /auth/login
    Then recibe un token y role "ADMIN"
    And no se exige perfil de alumno ni de tutor

  Scenario: Acceso restringido
    Given un usuario con rol STUDENT o TUTOR
    When invoca cualquier endpoint bajo /api/v1/admin/**
    Then recibe 403
    Given una petición sin token
    Then recibe 401

  Scenario: Rango de fechas por defecto
    When un endpoint de listado admin se invoca sin "from" ni "to"
    Then se aplica el rango de los últimos 30 días (inclusive del día de hoy, zona Córdoba)

  Scenario: Rango de fechas inválido
    When "from" es posterior a "to"
    Then recibe 400 con detail "VALIDATION_ERROR"

  Scenario: Dos administradores toman la misma solicitud a la vez
    Given una solicitud PENDING
    When dos administradores intentan tomarla simultáneamente
    Then exactamente uno la toma
    And el otro recibe 409 con detail "CASE_ALREADY_TAKEN"
```

---

## 4. Diseño

### 4.1 `AdminSeeder`

Siguiendo el estándar de seeds de `base-standards.md` (`CommandLineRunner`, idempotente, `<modulo>/config/<Entidad>Seeder.java`):

```java
// com.knowlink.api.admin.config.AdminSeeder
@Component
@Order(3) // después de los seeders de catálogo
@RequiredArgsConstructor
@Slf4j
public class AdminSeeder implements CommandLineRunner { ... }
```

- Configuración por `@ConfigurationProperties(prefix = "knowlink.admin")` → `AdminSeedProperties` con `List<AdminAccount> accounts` (`email`, `password`, `fullName`). En `application-*.yaml` solo referencias a variables de entorno (`KNOWLINK_ADMIN_ACCOUNTS_0_EMAIL`, `_PASSWORD`, `_FULL_NAME`, y `_1_` en adelante). **Nunca** credenciales en el repo ni en los `.sql` de seed.
- Por cada cuenta: si `userRepository.findByEmail` existe, saltear; si no, crear `User` con `role = ADMIN`, `accountStatus = ACTIVE`, `password = passwordEncoder.encode(...)`, `fullName`. `dni` y `phoneNumber` quedan `null`.
- `fullName` es lo que el frontend muestra como "Asignado a: Moderador …".
- Si `accounts` está vacío: `log.warn` y continuar.

### 4.2 Estructura de paquetes

Mismo molde que el resto (`interfaces/` + `implementations/` en controllers y services):

```
com.knowlink.api.admin/
├── config/                 # AdminSeeder, AdminSeedProperties
├── common/
│   ├── DateRangeResolver.java        # default 30 días, valida from<=to
│   ├── DateRange.java                # record (LocalDate from, LocalDate to) + toDateTimes(zone)
│   ├── audit/                        # AdminAuditLog (entity), AdminAuditAction (enum), IAdminAuditService
│   └── events/                       # eventos de dominio de admin (ver specs 03 y 04)
├── dashboard/              # spec 02 (US-30 + US-33)
├── materialreports/        # spec 03 (US-23)
└── claims/                 # spec 04 (US-50)
```

Cada subpaquete funcional replica `controllers/{interfaces,implementations,requests,responses}`, `services/{interfaces,implementations}`, `repositories`, `data/{models,enums}`.

### 4.3 Seguridad

- Todo controller admin: `@RequestMapping("/api/v1/admin/...")` y `@PreAuthorize("hasRole('ADMIN')")` **a nivel de clase**.
- Reglas que dependen de la identidad (por ejemplo "solo el asignado puede resolver") se validan en el **service** con el `userId` del `UserPrincipal`/`JwtAuthenticationDetails`, nunca confiando en datos enviados por el cliente.
- No agregar `requestMatchers` especiales en `SecurityConfig`: ya cubre todo bajo `/api/v1/**` con autenticación.

### 4.4 Rango de fechas

```java
public record DateRange(LocalDate from, LocalDate to) {}
```

`DateRangeResolver.resolve(LocalDate from, LocalDate to)`:
- Ambos `null` → `to = hoy (AppTimeZone.ZONE)`, `from = to.minusDays(29)` (30 días incluyendo hoy).
- Solo uno informado → `ValidationException("Indicá ambas fechas del rango.")`.
- `from > to` → `ValidationException("La fecha desde no puede ser posterior a la fecha hasta.")`.
- Rango máximo propuesto: 366 días (evita consultas enormes); excederlo → `ValidationException`.
- Conversión a consulta: `from.atStartOfDay()` inclusive y `to.plusDays(1).atStartOfDay()` exclusivo.
- Qué fecha se filtra en cada listado (apertura del caso) se define en los specs 03 y 04.

### 4.5 Paginación

- Parámetros `page` (0-based, default 0) y `size` (default 10, máximo 50). Respuesta con `PagedResponse<T>`.
- El diseño del frontend muestra 4 por página; el tamaño lo manda el frontend, el backend solo impone el máximo.

### 4.6 Excepciones nuevas

Siguiendo el patrón de 3 parámetros (`errorCode`, `userMessage`, `technicalMessage`):

| Excepción | HTTP | `detail` | Mensaje (es) |
|---|---|---|---|
| `BusinessConflictException` (nueva, base de conflicto de negocio) | 409 | recibe `errorCode` | recibe `userMessage` |
| usos: `CASE_ALREADY_TAKEN` | 409 | `CASE_ALREADY_TAKEN` | "Otro administrador ya tomó esta solicitud." |
| `CASE_NOT_PENDING` | 409 | `CASE_NOT_PENDING` | "La solicitud ya no está pendiente." |
| `CASE_ALREADY_RESOLVED` | 409 | `CASE_ALREADY_RESOLVED` | "Esta solicitud ya fue resuelta y no puede modificarse." |
| `NotAssignedToAdminException` (nueva) | 403 | `NOT_ASSIGNED_TO_ADMIN` | "Solo el administrador asignado puede resolver esta solicitud." |

Agregar los handlers en `GlobalExceptionHandler` con `logger.warn(ex.getMessage())` (technicalMessage) antes de armar `ApiError`, como indica el estándar.

### 4.7 "Tomar solicitud" sin condición de carrera

Patrón obligatorio para `assign` en denuncias y reclamos: **actualización condicional atómica**, no leer-y-luego-guardar.

```java
@Modifying
@Query("""
    UPDATE MaterialReportCase c
       SET c.status = com.knowlink.api.admin.materialreports.data.enums.MaterialReportCaseStatus.IN_REVIEW,
           c.assignedAdmin = :admin, c.assignedAt = :now
     WHERE c.id = :id AND c.status = com.knowlink.api.admin.materialreports.data.enums.MaterialReportCaseStatus.PENDING
""")
int assignIfPending(@Param("id") UUID id, @Param("admin") User admin, @Param("now") LocalDateTime now);
```

- `0` filas afectadas → distinguir con una lectura: si no existe → `ResourceNotFoundException`; si ya está `IN_REVIEW`/cerrada → `CASE_ALREADY_TAKEN` o `CASE_NOT_PENDING`.
- Mismo criterio para resolver: `UPDATE … WHERE id = :id AND status = IN_REVIEW AND assigned_admin = :admin`. Si afecta 0 filas → `NotAssignedToAdminException` o `CASE_ALREADY_RESOLVED`.
- Complementar con `@Version` (optimistic locking) en la entidad de caso como red de seguridad.

### 4.8 Notificaciones

- Se publican **eventos de dominio** con `ApplicationEventPublisher` y se consumen con `@TransactionalEventListener(phase = AFTER_COMMIT)`, como en `UserNotificationListener`.
- El envío se hace por `IEmailService` (Facade de Brevo). Se agregan métodos de negocio nuevos a `IEmailService`/`EmailServiceImpl` y plantillas en `EmailBuilder` (ver specs 03 y 04). Un fallo de envío **no** revierte la operación (ya es el comportamiento del servicio de mail).
- US-30 criterio 3: el administrador **no tiene** centro de notificaciones; no se generan notificaciones para admins. El aviso de pendientes es el badge (spec 02).
- El centro de notificaciones in-app (US-35) no está en `main`: los eventos se diseñan para que ese módulo pueda suscribirse después sin tocar la lógica de admin.

### 4.9 Auditoría mínima (recomendado, fuera de los criterios de aceptación)

Por el manejo de datos personales (DNI, teléfono, legajo) y por la decisión irreversible sobre fondos, se propone una tabla simple:

```java
@Entity @Table(name = "admin_audit_log")
public class AdminAuditLog {
    UUID adminAuditLogId; UUID adminUserId;
    AdminAuditAction action;   // CONTACT_INFO_VIEWED, MATERIAL_DOWNLOADED, CASE_TAKEN, MATERIAL_REMOVED, REPORT_DISMISSED, CLAIM_RESOLVED
    String targetType;         // "MATERIAL_REPORT_CASE" | "SESSION_CLAIM"
    UUID targetId; LocalDateTime createdAt;
}
```

`IAdminAuditService.record(action, targetType, targetId)` se llama dentro de la misma transacción de la acción. **Decisión a confirmar con el equipo**: si se omite, al menos registrar `log.info` estructurado (adminId, acción, targetId) en las acciones sensibles. **Nunca** loguear los datos personales en sí.

---

## 5. Impacto en el frontend

- La resolución de "badge del sidebar" (Admin 01) queda definida en el spec 02: cantidad de casos `PENDING` de cada tipo.
- Los errores 409/403 de arriba llegan con `message` listo para mostrar en `toast.error`, como pide `docs/base-standards.md` del frontend.
- El identificador de listados y detalles es `caseId`/`claimId` (UUID), no `materialId`.

## 6. No hacer

- No crear un endpoint público ni un flujo de registro de administradores.
- No guardar contraseñas ni emails de admins en el repo, en `seed-*.sql` ni en logs.
- No confiar en el rol enviado por el cliente: el rol sale del JWT.
- No resolver la concurrencia con `synchronized` ni leyendo y guardando sin condición.
- No devolver entidades JPA: DTOs como `record`.
- No exponer IDs internos, stack traces ni `technicalMessage` en respuestas.
- No tocar `AdminFundsControllerImpl` en esta historia.
- No usar `@Autowired` en campos; inyección por constructor o `@RequiredArgsConstructor`.
- No agregar caché en los endpoints admin (los datos deben reflejar acciones recién hechas).

## 7. Tests

- `AdminSeederTest`: crea, idempotente, sin configuración, no loguea contraseña.
- `DateRangeResolverTest`: default 30 días, solo un extremo, invertido, rango excedido, zona horaria.
- `GlobalExceptionHandlerTest`: nuevos handlers (409 y 403) con `ApiError` correcto.
- Integración de seguridad (`@SpringBootTest` + `MockMvc`): 401 sin token, 403 con STUDENT/TUTOR, 200 con ADMIN sobre un endpoint admin.
- Test de concurrencia de `assign` (dos hilos, un solo ganador) en cuanto exista la entidad (spec 03).

## 8. Definition of Done

- [ ] `./mvnw verify -B` en verde
- [ ] `AdminSeeder` crea admins desde variables de entorno y es idempotente
- [ ] Login de admin devuelve `role: ADMIN` sin exigir perfil
- [ ] Rutas `/api/v1/admin/**` rechazan STUDENT/TUTOR (403) y anónimos (401)
- [ ] Excepciones y handlers de conflicto agregados y testeados
- [ ] `DateRangeResolver` y utilidades de paginación disponibles para los specs 02–04
- [ ] `docs/base-standards.md` actualizado (ADMIN implementado, paquete `admin`)
- [ ] Variables de entorno nuevas documentadas en README y `render.yaml`
