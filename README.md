# KnowLink Backend

REST API para la plataforma KnowLink, construida con Java 21 + Spring Boot 3.5.

## Requisitos

- Java 21
- Maven 3.9+
- MySQL 8.0 (o Docker)

## Configuración del entorno

Variables obligatorias en `.env`:

| Variable | Descripción |
|---|---|
| `DB_HOST` | Host de MySQL (ej. `localhost`) |
| `DB_PORT` | Puerto de MySQL (ej. `3306`) |
| `DB_NAME` | Nombre de la base de datos |
| `DB_USER` | Usuario de MySQL |
| `DB_PASSWORD` | Contraseña de MySQL |
| `JWT_SECRET` | Clave secreta para firmar JWT (base64, 256+ bits) |
| `JWT_EXPIRATION` | Expiración del token en ms (ej. `86400000`) |
| `RATING_GATE_ENFORCED_FROM` | Fecha/hora ISO local desde la que se exige calificar antes de usar la API (ej. `2026-11-10T00:00:00`, hora de Argentina). Vacía = bloqueo apagado |
| `FRONTEND_URL` | URL del frontend (ej. `http://localhost:5173`) |
| `APP_URL` | URL de la API (ej. `http://localhost:8080`) |
| `KNOWLINK_ADMIN_ACCOUNTS_0_EMAIL` | Email del primer administrador (opcional) |
| `KNOWLINK_ADMIN_ACCOUNTS_0_PASSWORD` | Contraseña del primer administrador (opcional, nunca se commitea) |
| `KNOWLINK_ADMIN_ACCOUNTS_0_FULL_NAME` | Nombre visible del primer administrador |
| `KNOWLINK_ADMIN_ACCOUNTS_1_*` | Segundo administrador opcional (mismas tres variables) |

## Ejecución local

```bash
./mvnw spring-boot:run
```

La API queda disponible en `http://localhost:8080`.

## Documentación de la API

| URL | Descripción |
|---|---|
| `http://localhost:8080/swagger-ui.html` | Swagger UI interactivo |
| `http://localhost:8080/api-docs` | OpenAPI JSON |
| [docs/admin/admin-frontend-contract.md](docs/admin/admin-frontend-contract.md) | Contrato HTTP del panel de administración |

## Ejecución de tests

```bash
# Todos los tests
./mvnw test

# Build completo con tests
./mvnw verify
```

## Docker

```bash
# Levantar MySQL + backend con Docker Compose
docker compose up -d

# Solo el backend (MySQL externo)
docker build -t knowlink-backend .
docker run -p 8080:8080 --env-file .env knowlink-backend
```

## Estructura del proyecto

```
src/main/java/com/knowlink/api/
├── auth/                 # Módulo de autenticación
│   ├── controllers/
│   ├── services/
├── config/               # Configuración Spring (Security, CORS, Async)
├── exceptions/           # Manejo global de errores
├── security/             # JWT, filtros, entrypoints
│   ├── enums/
│   ├── filter/
│   ├── services/
│   └── utils/
└── users/                # Módulo de usuarios
    ├── controllers/
    ├── data/
    ├── repositories/
    └── services/
```

## Calificación obligatoria post-sesión (US-20b)

Mientras `RATING_GATE_ENFORCED_FROM` esté configurada, todo usuario (alumno o tutor) con una sesión
`COMPLETED` cuyo horario de fin ya pasó y que todavía no calificó recibe **423 `RATING_REQUIRED`**
en el resto de la API. El bloqueo es por `userId`, no por el rol del token, y persiste hasta calificar.

### Orden de despliegue obligatorio

1. Desplegar el backend con `RATING_GATE_ENFORCED_FROM` **vacía** (bloqueo apagado).
2. Desplegar el frontend que maneja el 423 y consulta `GET /api/v1/ratings/pending`.
3. Configurar `RATING_GATE_ENFORCED_FROM` con la fecha/hora de corte (hora de Argentina) y reiniciar el servicio.

Si el backend bloquea antes de que el frontend maneje el 423, los usuarios quedan sin forma de salir del bloqueo.

### Migración de datos (ejecutar antes de mergear a main)

Las notificaciones `RATING_ENABLED` creadas por la versión anterior tienen `expires_at` y se verían vencidas:

```sql
UPDATE user_notification SET expires_at = NULL WHERE notification_type = 'RATING_ENABLED';
```

### Kill switch

Dejar `RATING_GATE_ENFORCED_FROM` vacía o con un valor inválido desactiva el bloqueo por completo
(y `GET /api/v1/ratings/pending` devuelve `blocking` vacío). El chequeo del interceptor es *fail-open*:
si la verificación falla, el request continúa y el error queda en el log.

### Endpoints que nunca responden 423

| Método | Ruta |
|---|---|
| GET | `/api/v1/ratings/pending` |
| POST | `/api/v1/bookings/{bookingId}/ratings` |
| POST | `/api/v1/auth/logout` |
| OPTIONS | cualquier ruta (preflight CORS) |

`ADMIN` nunca es bloqueado. Las rutas públicas y las que no son `/api/v1/**` no pasan por el chequeo.

## Perfil de desarrollo vs producción

El proyecto usa Spring Profiles:

- `development` — caché deshabilitado, valores de configuración en `.env`
- `production` — caché Caffeine activo, requiere variables de entorno del sistema
- `test` — H2 en memoria, sin JWT real

Para forzar un perfil:
```bash
./mvnw spring-boot:run -Dspring-boot.run.profiles=development
```
