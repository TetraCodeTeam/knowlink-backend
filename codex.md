# KnowLink Backend

> **Lee primero:** [`docs/base-standards.md`](docs/base-standards.md)

---

## Contexto rápido para ChatGPT

**Proyecto:** REST API de KnowLink — plataforma de tutorías académicas  
**Stack:** Java 21 · Spring Boot 3.5.3 · Maven (`./mvnw`) · JPA/Hibernate · MySQL 8 · Spring Security · JWT (jjwt 0.11.5) · Lombok · SpringDoc OpenAPI · Caffeine Cache  
**groupId:** `com.knowlink` | **base package:** `com.knowlink.api`

### Reglas no negociables

1. Inyección por constructor, nunca `@Autowired` en campos
2. DTOs response = `record` Java; DTOs request = clase con validaciones Jakarta
3. Errores centralizados en `exceptions/` con `@ControllerAdvice`; nunca manejar en controllers
4. `orElseThrow()` siempre, nunca `Optional.get()`
5. Logging con `@Slf4j`, nunca `System.out.println`
6. Todo endpoint: anotaciones `@Operation`/`@ApiResponse` de SpringDoc
7. No hardcodear valores — `@Value("${propiedad}")` o `@ConfigurationProperties`
8. Tests usan perfil `test` con H2 in-memory, no necesitan MySQL ni variables `.env`

### Estructura de módulo

```
{modulo}/data/ → {modulo}/repositories/ → {modulo}/services/ → {modulo}/controllers/
```

Nunca saltear capas (controller no habla con repository directamente).
