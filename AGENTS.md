# KnowLink Backend

> **Lee primero:** [`docs/base-standards.md`](docs/base-standards.md)

---

## Reglas para GitHub Copilot

### Estructura obligatoria al crear un módulo

```
src/main/java/com/knowlink/api/{modulo}/
├── data/           # Entidad @Entity + DTOs (record para response, clase para request)
├── repositories/   # Interface extends JpaRepository<Entidad, Long>
├── services/       # @Service con lógica de negocio
└── controllers/    # @RestController con rutas /api/v1/{modulo}
```

### Lo que siempre debe hacer

- Inyección por constructor (no `@Autowired` en campos)
- `@RequiredArgsConstructor` de Lombok si el constructor es solo para inyección
- `orElseThrow()` en todos los Optional, nunca `.get()`
- `@Slf4j` para logging, nunca `System.out.println`
- `@Operation` + `@ApiResponse` en todos los endpoints nuevos
- Registrar endpoints nuevos en la configuración de Spring Security (`config/`)

### Lo que nunca debe hacer

- Lógica de negocio en controllers
- Acceso al repository desde el controller (saltear la capa service)
- Hardcodear URLs, credenciales o configuraciones — usar `@Value` o `@ConfigurationProperties`
- Usar `any` o casteos sin verificación de tipo
- `Optional.get()` sin verificación previa

### Convención de commits

```
feat: descripción en español
fix: descripción en español
refactor: descripción en español
test: descripción en español
ci: descripción en español
docs: descripción en español
```
