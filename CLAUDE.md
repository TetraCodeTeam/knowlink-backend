# KnowLink Backend

> **Lee primero:** [`docs/base-standards.md`](docs/base-standards.md)
> Contiene el stack exacto, estructura de paquetes, dominio, convenciones y ejemplos de código.

---

## Instrucciones específicas para Claude

### Antes de generar código

1. Identificar a qué módulo pertenece la tarea (`auth`, `users`, u otro a crear)
2. Confirmar qué capa se está implementando (data → repository → service → controller)
3. Si la tarea involucra una entidad nueva, verificar si tiene relación con entidades existentes del dominio

### Reglas de generación

- Inyección **siempre por constructor** (o `@RequiredArgsConstructor` de Lombok)
- DTOs response como `record`, request como clase con anotaciones Jakarta Validation
- Errores: lanzar excepciones custom desde `services/`, manejarlas en `exceptions/` con `@ControllerAdvice`
- Optional: siempre `orElseThrow(ResourceNotFoundException::new)`, nunca `.get()`
- Logging: `@Slf4j` de Lombok + `log.info/warn/error(...)`, nunca `System.out.println`
- Todo endpoint nuevo: anotaciones `@Operation` y `@ApiResponse` de SpringDoc

### Cuándo pedir aclaraciones

Preguntar antes de implementar si no está claro:
- El módulo de destino de la nueva funcionalidad
- Si una entidad nueva necesita relación `@ManyToOne` / `@OneToMany` con entidades existentes
- Si el endpoint es público (`/auth/**`) o protegido (`/api/v1/**`)
- Qué perfil de Spring aplica para configuraciones específicas

### Contexto del equipo

Proyecto académico grupal desarrollado con múltiples IAs (Claude, Copilot, ChatGPT, Gemini). Todos los agentes apuntan a los mismos estándares en `docs/base-standards.md` para mantener consistencia entre integrantes del equipo.
