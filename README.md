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
| `FRONTEND_URL` | URL del frontend (ej. `http://localhost:5173`) |
| `APP_URL` | URL de la API (ej. `http://localhost:8080`) |

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

## Perfil de desarrollo vs producción

El proyecto usa Spring Profiles:

- `development` — caché deshabilitado, valores de configuración en `.env`
- `production` — caché Caffeine activo, requiere variables de entorno del sistema
- `test` — H2 en memoria, sin JWT real

Para forzar un perfil:
```bash
./mvnw spring-boot:run -Dspring-boot.run.profiles=development
```
