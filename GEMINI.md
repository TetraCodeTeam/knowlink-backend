# KnowLink Backend

> **Lee primero:** [`docs/base-standards.md`](docs/base-standards.md)

---

## Contexto para Gemini

**Proyecto:** REST API de KnowLink — plataforma de tutorías académicas  
**Stack:** Java 21 · Spring Boot 3.5.3 · Maven Wrapper · JPA/Hibernate · MySQL 8 · Spring Security + JWT · Lombok · SpringDoc OpenAPI 2.3 · Caffeine Cache  
**Paquete base:** `com.knowlink.api`

### Convenciones críticas

- Módulos en `src/main/java/com/knowlink/api/{modulo}/` con 4 capas: `data/`, `repositories/`, `services/`, `controllers/`
- Inyección siempre por constructor (`@RequiredArgsConstructor` de Lombok es válido)
- DTOs: `record` para responses, clases con anotaciones Jakarta para requests
- Manejo de errores: excepciones custom + `@ControllerAdvice` en `exceptions/`
- Logging: `@Slf4j` de Lombok — no `System.out.println`
- Todo endpoint nuevo debe tener anotaciones Swagger (`@Operation`, `@ApiResponse`)
- Perfiles de Spring: `development` (local + .env), `test` (H2 in-memory), `production` (Caffeine activo)

Consultar `docs/base-standards.md` para el dominio completo del sistema (entidades, roles, estados, ejemplos de código).
