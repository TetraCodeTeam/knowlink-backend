# KnowLink Backend — Estándares del proyecto

## Contexto

KnowLink es una plataforma de tutorías académicas que conecta alumnos con tutores.
Este repositorio es la **REST API** del sistema.

- Repo backend: https://github.com/TetraCodeTeam/knowlink-backend
- Repo frontend: https://github.com/TetraCodeTeam/knowlink-frontend
- API docs (local): http://localhost:8080/swagger-ui.html
- OpenAPI JSON: http://localhost:8080/api-docs

---

## Stack exacto

| Componente | Versión / detalle |
|---|---|
| Java | 21 |
| Spring Boot | 3.5.3 |
| Maven | 3.9+ (usar siempre `./mvnw`, nunca `mvn` directo) |
| Spring Data JPA + Hibernate | incluido en Boot |
| MySQL | 8.1.0 (`mysql-connector-j`) — producción |
| H2 | in-memory — perfil `test` únicamente |
| Spring Security | incluido en Boot |
| JWT | `io.jsonwebtoken` (jjwt) 0.11.5 |
| Caché | Caffeine 3.1.8 vía Spring Cache |
| OpenAPI / Swagger | springdoc-openapi-starter-webmvc-ui 2.3.0 |
| Actuator | incluido (health checks para Docker) |
| Lombok | 1.18.36 (`provided` — no genera bytecode en prod) |
| Variables de entorno | `spring-dotenv` 3.0.0 (carga `.env` automáticamente) |
| Tests | Spring Boot Test + Spring Security Test + H2 |
| groupId / artifactId | `com.knowlink` / `api` |

---

## Estructura de paquetes

```
src/main/java/com/knowlink/api/
├── auth/                          # Módulo de autenticación
│   ├── controllers/
│   │   ├── interfaces/            # IAuthController (contrato + docs Swagger)
│   │   ├── implementations/       # AuthControllerImpl
│   │   ├── requests/               # records: UserRegistrationRequest, TutorRegistrationRequest, etc.
│   │   └── responses/              # records: AuthResponse
│   └── services/
│       ├── interfaces/            # IAuthService
│       └── implementations/       # AuthServiceImpl
├── config/                        # SecurityConfig, OpenApiConfig
├── exceptions/                    # @ControllerAdvice — manejo global de errores
├── security/
│   ├── enums/                     # Role
│   ├── filter/                    # JwtAuthenticationFilter
│   ├── models/                    # UserPrincipal (implements UserDetails)
│   ├── services/                  # JwtService
│   └── utils/                     # SecurityConstants (whitelist pública/swagger)
├── users/                         # Módulo de usuarios
│   ├── controllers/{interfaces,implementations,requests,responses}/
│   ├── data/
│   │   ├── models/                 # User, Token
│   │   ├── enums/                  # AccountStatus
│   │   └── mappers/                # UserMapper
│   ├── events/                     # UserRegisteredEvent, PasswordResetRequestedEvent, ResendConfirmationEvent
│   ├── repositories/
│   └── services/{interfaces,implementations}/
└── tutors/                        # Módulo de tutores (carreras, materias, perfiles, reservas)
    ├── controllers/{interfaces,implementations,responses,requests}/
    ├── data/
    │   ├── models/                 # TutorProfile, Career, Subject, TutorSubject, AvailabilityBlock,
    │   │                           # Booking, Rating, AcademicMaterial
    │   ├── enums/                  # Modality, CompensationType, TutorSubjectStatus, BookingStatus
    │   └── mappers/                # TutorProfileMapper, TutorSubjectMapper
    ├── repositories/
    ├── services/{interfaces,implementations}/
    ├── validations/                # ITutorProfileValidationService
    └── config/                     # CareerSeeder, SubjectSeeder (CommandLineRunner)
```

Cada nuevo módulo de dominio replica esta misma estructura, **incluyendo la separación `interfaces/` + `implementations/`** tanto en `controllers/` como en `services/` (no es solo `controllers/` y `services/` a secas).

---

## Dominio del sistema

### Entidades principales

| Entidad | Descripción |
|---|---|
| `User` | Base: email, password hash, rol, estado de cuenta (soft-delete con `deletedAt`) |
| `TutorProfile` | Composición 1:1 con `User` (rol TUTOR); carrera, biografía |
| `Career` | Catálogo de carreras (seed data vía `CareerSeeder`) |
| `Subject` | Catálogo de materias, asociadas a una `Career`; distingue básicas (`isBasic`) de específicas |
| `TutorSubject` | Relación entre `TutorProfile` y `Subject`: modalidad, tipo de compensación, precio, estado |
| `AvailabilityBlock` | Bloque de disponibilidad semanal de un tutor |
| `Booking` | Sesión entre alumno y tutor: horario, estado, link de sesión virtual |
| `AcademicMaterial` | Recurso subido por un tutor, asociado a un `TutorSubject` |
| `Rating` | Reseña entre usuarios (rater/rated), visible u oculta |
| `Token` | Token de un solo uso para confirmación de cuenta / reset de contraseña (expira a las N horas) |

> Nota: las entidades y todo el código se nombran en **inglés** (`User`, no `Usuario`) — es la convención real del proyecto. El español se reserva para lo que ve el usuario final: mensajes de Zod en el front, y (por decisión de equipo) la documentación de Swagger.

### Roles (enum)

```java
STUDENT, TUTOR, ADMIN
```

> `ADMIN` todavía no está implementado — agregarlo a esta lista cuando se implemente el módulo de administración.

### Estados de Booking (enum `BookingStatus`)

```java
BOOKED, IN_PROGRESS, COMPLETED, CANCELLED, NOT_CONFIRMED
```

## Convenciones de código

### Naming

| Elemento | Convención | Ejemplo |
|---|---|---|
| Clases | PascalCase | `ReservaService`, `PerfilTutorController` |
| Métodos y variables | camelCase | `obtenerReservasPorAlumno()` |
| Constantes | UPPER_SNAKE_CASE | `JWT_EXPIRATION_MS` |
| Tablas BD | snake_case en `@Table` | `@Table(name = "perfil_tutor")` |
| Endpoints | kebab-case plural | `/api/v1/perfiles-tutor` |
| Paquetes | lowercase, singular | `com.knowlink.api.users` |

### Estructura de un módulo nuevo

Al crear un módulo, siempre los 4 paquetes en este orden:

1. `data/` — primero la entidad JPA y los DTOs
2. `repositories/` — interfaz que extiende `JpaRepository<Entidad, Long>`
3. `services/` — lógica de negocio, inyectar repositorio por constructor
4. `controllers/` — endpoints REST, inyectar servicio por constructor

### Inyección de dependencias

**Siempre por constructor**, nunca `@Autowired` en campos:

```java
// ✅ Correcto
@Service
public class ReservaService {
    private final ReservaRepository reservaRepository;

    public ReservaService(ReservaRepository reservaRepository) {
        this.reservaRepository = reservaRepository;
    }
}

// ❌ Incorrecto
@Autowired
private ReservaRepository reservaRepository;
```

Con Lombok se puede usar `@RequiredArgsConstructor` en lugar del constructor explícito.

### DTOs

- **Response y Request → siempre `record` de Java** (inmutables), con validaciones Jakarta en el caso de los Request:
```java
  public record TutorSubjectRequest(
          @NotBlank(message = "Subject name is required")
          String subjectName,

          @NotNull(message = "Modality is required")
          Modality modality,

          @DecimalMin(value = "0.01", message = "Price must be greater than zero")
          BigDecimal pricePerHour
  ) {}
```
- Los mensajes de las anotaciones de validación (`@NotBlank`, `@Size`, etc.) van **en inglés**, consistente con el resto del código. El español queda reservado para: (a) los mensajes que arma Zod en el front, y (b) la documentación de Swagger (`@Operation`, `@Tag`), por decisión de equipo.

### Manejo de errores

- Centralizado en `exceptions/` mediante `@ControllerAdvice`.
- Lanzar excepciones personalizadas desde servicios, nunca manejarlas en controllers.
- Formato de respuesta de error real:
```json
  { "status": 409, "message": "Este correo ya está registrado.", "detail": "DUPLICATE_EMAIL" }
```
  (`status` + `message` con texto seguro para mostrar al usuario final + `detail` con un código estable de categoría — nunca información interna como IDs, valores de campo o nombres de entidad).
- Las excepciones que representan errores de negocio recurrentes (`ResourceNotFoundException`, `DuplicateResourceException`) reciben **3 parámetros** en el constructor: `errorCode` (string fijo tipo `USER_NOT_FOUND`, va a `ApiError.detail`), `userMessage` (texto en español, seguro para el cliente, va a `ApiError.message`) y `technicalMessage` (detalle interno con los valores específicos — IDs, emails, etc. — solo para logs, nunca se expone en la respuesta).
- El handler correspondiente debe loguear `ex.getMessage()` (el technicalMessage) con `logger.warn(...)` antes de construir el `ApiError`, y usar `ex.getUserMessage()` / `ex.getErrorCode()` para la respuesta:
```java
  @ExceptionHandler(ResourceNotFoundException.class)
  public ResponseEntity<ApiError> handleNotFound(ResourceNotFoundException ex) {
      logger.warn(ex.getMessage());
      ApiError error = new ApiError(HttpStatus.NOT_FOUND.value(), ex.getUserMessage(), ex.getErrorCode());
      return ResponseEntity.status(HttpStatus.NOT_FOUND).body(error);
  }
```
- Para excepciones más simples que no necesitan este patrón de 3 parámetros (`UnauthorizedException`, `TokenExpiredException`, `ValidationException`, etc.), el mensaje pasado al constructor ya debe ser el texto final para el usuario — el `errorCode` se define directo como string literal en el `ApiError` del handler, no hace falta agregarlo a la excepción.
- El handler catch-all (`RuntimeException`/`Exception`) nunca debe exponer `ex.getMessage()` en la respuesta — solo en el log (`logger.error`). La respuesta al cliente usa siempre un mensaje genérico fijo en español (`"Ocurrió un error inesperado. Intentá nuevamente más tarde."`), para no filtrar detalles de implementación (stack traces, nombres de tabla, etc.).
- Usar `orElseThrow()` en `Optional`, nunca `get()` sin verificar.

### Logging

- SLF4J siempre. Con Lombok: `@Slf4j` en la clase, luego `log.info(...)`.
- Nunca `System.out.println`.

### Documentación Swagger

Todo endpoint nuevo lleva anotaciones `@Operation` / `@ApiResponse` / `@Tag`.

**Decisión de equipo: la documentación de Swagger se mantiene en español**, a diferencia del resto del código (nombres de clases, métodos, mensajes de validación internos), que va en inglés. No traducir `@Operation(summary = ...)` ni `@Tag(description = ...)` a menos que se decida lo contrario explícitamente.

La autenticación JWT está documentada vía `OpenApiConfig` con un `@SecurityScheme` tipo Bearer, lo que habilita el botón "Authorize" en Swagger UI:

```java
@Configuration
@OpenAPIDefinition(security = @SecurityRequirement(name = "bearerAuth"))
@SecurityScheme(name = "bearerAuth", type = SecuritySchemeType.HTTP, scheme = "bearer", bearerFormat = "JWT")
public class OpenApiConfig {}
```

### Identificadores

Todas las entidades usan `UUID` como clave primaria, generado por Hibernate (`GenerationType.UUID`), **no** `Long` autoincremental:

```java
@Id
@GeneratedValue(strategy = GenerationType.UUID)
@Column(name = "tutor_subject_id", updatable = false, nullable = false)
private UUID tutorSubjectId;
```

Convención de nombre de columna: `<entidad>_id` en snake_case (`tutor_subject_id`, `career_id`, `subject_id`).

---

## Perfiles de Spring

| Perfil | Cuándo se usa | BD | Caché |
|---|---|---|---|
| `development` | local con `.env` | MySQL | deshabilitada |
| `production` | deploy; variables de entorno del sistema | MySQL | Caffeine activo |
| `test` | tests automáticos | H2 in-memory | deshabilitada |

Activar perfil:
```bash
./mvnw spring-boot:run -Dspring-boot.run.profiles=development
```

---

## Notificaciones por email

Flujos de confirmación de cuenta y reset de contraseña son **asíncronos, basados en eventos** (`ApplicationEventPublisher` + `@EventListener`), no llamadas directas al servicio de mail desde el service de negocio:

```java
eventPublisher.publishEvent(new UserRegisteredEvent(newUser, confirmationToken));
```

Dependencia: `spring-boot-starter-mail`. Variables de entorno nuevas en `.env`:

```
MAIL_HOST=smtp.gmail.com
MAIL_PORT=587
MAIL_USERNAME=...
MAIL_PASSWORD=...
```
---

## Variables de entorno (`.env` en local)

```
DB_HOST=localhost
DB_PORT=3306
DB_NAME=knowlink
DB_USER=...
DB_PASSWORD=...
JWT_SECRET=<base64, mínimo 256 bits>
JWT_EXPIRATION=86400000
FRONTEND_URL=http://localhost:5173
APP_URL=http://localhost:8080
```

---

## Endpoints base

| Ámbito | Prefijo | Auth requerida |
|---|---|---|
| Autenticación | `/auth/**` | No |
| API protegida | `/api/v1/**` | Sí (Bearer JWT) |
| Actuator | `/actuator/**` | Configurable |
| Swagger UI | `/swagger-ui.html` | No (dev) |

---

## Comandos frecuentes

```bash
./mvnw spring-boot:run                          # Ejecutar local
./mvnw test                                     # Solo tests
./mvnw verify -B                                # Build completo + tests (lo que corre el CI)
docker compose up -d                            # Levantar MySQL + backend
```
---

## Seed data (catálogos)

Entidades de catálogo sin ABM propio todavía (`Career`, `Subject`) se precargan al arrancar la app mediante `CommandLineRunner`, no con `data.sql` ni migraciones:

```java
@Component
@Order(1) // los seeders con dependencias entre sí usan @Order para garantizar orden de ejecución
@RequiredArgsConstructor
public class CareerSeeder implements CommandLineRunner {
    @Override
    public void run(String... args) {
        if (careerRepository.count() > 0) return; // idempotente
        // ...
    }
}
```

Ubicación: `<modulo>/config/<Entidad>Seeder.java`. Reemplazar por un ABM real + endpoints cuando el sprint correspondiente lo contemple.

---

## CI/CD

Archivo: `.github/workflows/ci.yml`  
Trigger: push y PR a `main` y `develop`  
Pasos: checkout → Java 21 (temurin) → `./mvnw verify -B` → upload surefire reports  
Actions: `checkout@v5`, `setup-java@v4`, `upload-artifact@v6`
