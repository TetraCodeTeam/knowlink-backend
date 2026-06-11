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
├── auth/                  # Módulo de autenticación
│   ├── controllers/       # @RestController: login, registro, refresh token
│   └── services/          # Lógica de autenticación
├── config/                # Configuración Spring: Security, CORS, Async, Caché
├── exceptions/            # @ControllerAdvice — manejo global de errores
├── security/              # Infraestructura JWT
│   ├── enums/             # Ej: roles, tipos de token
│   ├── filter/            # JwtAuthenticationFilter
│   ├── services/          # UserDetailsService, etc.
│   └── utils/             # JwtUtils (generación/validación de tokens)
└── users/                 # Módulo de usuarios
    ├── controllers/       # @RestController: CRUD de usuarios y perfiles
    ├── data/              # @Entity JPA + DTOs
    ├── repositories/      # Interfaces JpaRepository
    └── services/          # Lógica de negocio de usuarios
```

Cada nuevo módulo de dominio replica esta misma estructura de 4 capas.

---

## Dominio del sistema

### Entidades principales

| Entidad | Descripción |
|---|---|
| `Usuario` | Base: email, password hash, rol, estado activo |
| `PerfilAlumno` | Composición de `Usuario`; datos del alumno (intereses, nivel) |
| `PerfilTutor` | Composición de `Usuario`; materias, tarifa por hora, disponibilidad, valoración |
| `Reserva` | Sesión entre alumno y tutor; tiene fecha, duración, estado, modalidad |
| `Material` | Recurso subido por un tutor (archivo o link) |
| `Calificacion` | Reseña del alumno al tutor post-sesión (puntaje + comentario) |
| `Pago` | Registro de pago asociado a una `Reserva` |
| `Notificacion` | Notificación del sistema hacia un usuario |
| `Denuncia` | Reporte de conducta entre usuarios |

### Roles (enum)

```java
ALUMNO, TUTOR, ADMIN
```

### Estados típicos de Reserva

```java
PENDIENTE, CONFIRMADA, CANCELADA, COMPLETADA
```

---

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

- **Response** → `record` de Java (inmutable, conciso):
  ```java
  public record ReservaResponse(Long id, String fecha, String estado) {}
  ```
- **Request** → clase con validaciones Jakarta:
  ```java
  public class CrearReservaRequest {
      @NotNull Long tutorId;
      @NotBlank String fecha;
  }
  ```

### Manejo de errores

- Centralizado en `exceptions/` mediante `@ControllerAdvice`
- Lanzar excepciones personalizadas desde servicios, nunca manejarlas en controllers
- Formato de respuesta de error siempre:
  ```json
  { "message": "Reserva no encontrada", "status": 404 }
  ```
- Usar `orElseThrow()` en Optional, nunca `get()` sin verificar

### Logging

- SLF4J siempre. Con Lombok: `@Slf4j` en la clase, luego `log.info(...)`.
- Nunca `System.out.println`.

### Documentación Swagger

Todo endpoint nuevo lleva anotaciones:
```java
@Operation(summary = "Crear reserva")
@ApiResponse(responseCode = "201", description = "Reserva creada")
```

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

## CI/CD

Archivo: `.github/workflows/ci.yml`  
Trigger: push y PR a `main` y `develop`  
Pasos: checkout → Java 21 (temurin) → `./mvnw verify -B` → upload surefire reports  
Actions: `checkout@v5`, `setup-java@v4`, `upload-artifact@v6`
