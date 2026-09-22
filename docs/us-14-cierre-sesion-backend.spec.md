# SPEC — US-14: Cierre de Sesión (BACKEND)

> Uso: pasar este archivo completo a OpenCode (modelo DeepSeek V4 free) parado en el repo `TetraCodeTeam/knowlink-backend`. Contiene todo lo necesario para desarrollar la parte backend de esta historia de forma autónoma.

## Metadata de la historia

| Campo | Valor |
|---|---|
| ID | US-14 |
| Módulo | usuarios |
| Story Points | 1 |
| Prioridad | MEDIA |
| Tags | #RN-03 #JWT |
| Relacionadas | US-02 (inicio de sesión), US-34 (recuperar contraseña), US-40 (reactivar cuenta) |
| Repo | `TetraCodeTeam/knowlink-backend` |

## Historia de usuario

Como usuario registrado, quiero cerrar sesión de forma segura, para proteger mi cuenta cuando termine de usar la plataforma.

## Criterios de aceptación (Gherkin)

```gherkin
Feature: Cierre de sesión

  Scenario: AC-1 — Invalidación inmediata del token
    Given un usuario autenticado con un token de sesión válido
    When presiona el botón "Cerrar sesión"
    Then el token de sesión se invalida de inmediato
    And no puede volver a usarse para autenticar requests posteriores

  Scenario: AC-2 — Bloqueo de rutas protegidas tras logout
    Given un usuario que acaba de cerrar sesión
    When intenta acceder a cualquier ruta protegida
    Then el request al backend con el token invalidado responde 401
```

> Nota: el AC-2 completo (redirección visual a login) se valida en el frontend. Este spec de backend solo cubre la parte que le corresponde: que el token deje de ser válido para el servidor.

---

## Stack confirmado del repo

- Java 21 + Spring Boot 3.5
- MySQL 8 (Docker Compose disponible en el repo)
- Spring Security con JWT (`JWT_SECRET`, `JWT_EXPIRATION` configurados vía `.env`)
- Perfiles Spring: `development`, `production`, `test` (H2 en memoria, sin JWT real)
- Documentación de API en Swagger UI: `http://localhost:8080/swagger-ui.html`

Estructura de paquetes real del repo (respetarla, no crear una paralela):
```
src/main/java/com/knowlink/api/
├── auth/
│   ├── controllers/
│   └── services/
├── config/            # SecurityConfig, CORS, Async
├── exceptions/        # manejo global de errores
├── security/
│   ├── enums/
│   ├── filter/        # filtro JWT (OncePerRequestFilter)
│   ├── services/
│   └── utils/
└── users/
    ├── controllers/
    ├── data/
    ├── repositories/
    └── services/
```

### Estrategia de invalidación

JWT es stateless por diseño, así que "invalidar de inmediato" requiere una **blacklist/denylist de tokens** persistida en MySQL vía JPA (no hay Redis en el `docker-compose.yml` del proyecto). Se integra dentro del paquete `security/` existente, no como módulo aparte.

---

## Objetivo

Implementar el endpoint de logout que invalida el JWT actual, y extender el filtro de seguridad para que rechace tokens invalidados.

## Ubicación de los cambios (respetar la estructura existente)

- `auth/controllers/` → agregar el endpoint de logout (junto al de login de US-02).
- `auth/services/` → lógica de invalidación (revisar primero si ya existe un `AuthService` o similar para no duplicar).
- `security/filter/` → el `OncePerRequestFilter` de JWT ya existente es donde se agrega el chequeo contra blacklist.
- `security/services/` → nuevo `TokenBlacklistService` (o extender un servicio existente si ya hay algo de manejo de tokens ahí).
- `security/utils/` → si hay un `JwtUtils`/`JwtProvider`, ahí se extrae el `jti` y `exp` del token.
- Nueva entidad JPA + repositorio: puede ir en `security/` (es infraestructura de seguridad, no de dominio de usuario) o en `users/data` + `users/repositories` si el equipo prefiere agrupar por dominio. **Revisar convención ya usada en el repo antes de decidir.**

## Alcance funcional

1. **Endpoint `POST /api/auth/logout`**
   - Requiere `Authorization: Bearer <token>` válido (ya resuelto por el filtro de seguridad existente).
   - Extrae el `jti` (JWT ID) del token autenticado. **Si el JWT actual no incluye claim `jti`**, agregarlo al momento de firmar el token en el flujo de login (tocar el generador de JWT en `security/utils` o `auth/services`), porque es necesario para poder blacklistear tokens individuales sin blacklistear por usuario entero.
   - Persiste el `jti` en una nueva tabla `token_blacklist` vía JPA, junto con `expiresAt` (igual al `exp` original del token — así se puede limpiar después).
   - Responde `204 No Content`.
   - Si el token ya es inválido, responde `401` (esto ya lo debería manejar el `AuthenticationEntryPoint`/`ExceptionHandler` global existente en `exceptions/`).

2. **Extensión del filtro JWT (`security/filter/`)**
   - Después de validar firma y expiración del token, agregar chequeo: si el `jti` del token está en `token_blacklist` → no autenticar el request, dejar que la cadena de Spring Security responda `401` (usar el mismo mecanismo de rechazo que ya usa el filtro para tokens inválidos/expirados, revisar cómo está resuelto ahí para ser consistente).

3. **Entidad JPA — `TokenBlacklist`**
   ```java
   @Entity
   @Table(name = "token_blacklist")
   public class TokenBlacklist {
       @Id
       @GeneratedValue(strategy = GenerationType.IDENTITY)
       private Long id;

       @Column(nullable = false, unique = true)
       private String jti;

       @Column(name = "user_id", nullable = false)
       private Long userId;

       @Column(name = "expires_at", nullable = false)
       private LocalDateTime expiresAt;

       @Column(name = "created_at")
       private LocalDateTime createdAt = LocalDateTime.now();
   }
   ```
   - Repositorio `TokenBlacklistRepository extends JpaRepository<TokenBlacklist, Long>` con método `boolean existsByJti(String jti)`.
   - Dejar que Hibernate gestione la creación de la tabla según la config de `ddl-auto` que ya use el proyecto (revisar `application.yml`/`application.properties` por perfil antes de asumir).

4. **Job de limpieza (opcional, no bloqueante para el punto de esta historia)**
   - Un `@Scheduled` que borre registros con `expiresAt < now()`. Dejarlo como TODO comentado si no se implementa ahora, para no crecer la tabla indefinidamente.

## Criterios de aceptación → validación técnica

- AC-1: test de integración (`@SpringBootTest` o `MockMvc`) — login → logout → reintentar request a un endpoint protegido con el mismo token → `401`.
- AC-2: test de integración — logout → request a cualquier endpoint protegido existente del proyecto → `401`.

## Tests esperados (perfil `test` con H2)

- Unit: filtro JWT rechaza un token cuyo `jti` está en blacklist (mockeando `TokenBlacklistRepository`).
- Unit: `AuthService`/`TokenBlacklistService` persiste correctamente el registro en logout.
- Integración: flujo completo login → uso de token en endpoint protegido → logout → mismo token rechazado.
- Edge case: logout con token ya expirado → `401`, no `500`.
- Edge case: logout sin header `Authorization` → `401` (comportamiento estándar de Spring Security, no debería requerir código nuevo).

## No hacer

- No implementar logout "global" (cerrar todas las sesiones del usuario en todos los dispositivos) — no está en el alcance de esta historia.
- No tocar el flujo de refresh token si no existe uno ya implementado en el repo — no inventar esa funcionalidad.
- No cambiar el algoritmo de firma ni la config de `JWT_SECRET`/`JWT_EXPIRATION`.
- No modificar nada del repo `knowlink-frontend` — ese consumo está en un spec aparte.

## Definition of Done (backend)

- [ ] Endpoint `POST /api/auth/logout` implementado.
- [ ] Filtro JWT rechaza tokens en blacklist.
- [ ] Entidad, repositorio y (si aplica) claim `jti` agregados.
- [ ] Tests unitarios e integración pasando en perfil `test`.
- [ ] Documentado en Swagger (anotaciones ya usadas en otros endpoints del proyecto).
- [ ] Sin romper US-02 (login) ni el filtro de seguridad existente.
