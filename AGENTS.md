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

Implementa la siguiente Historia de Usuario:

US 07 - Ver perfil de un tutor como alumno

Como alumno, quiero ver el perfil completo de un tutor, para evaluar si es el tutor adecuado antes de reservar una sesión.

Criterios de aceptación:

* El alumno autenticado puede visualizar:

  * materias
  * modalidades
  * tipo de compensación
  * calificaciones y comentarios
  * horarios disponibles

* Los datos personales de contacto del tutor NO deben aparecer en el perfil público.

* Los datos de contacto solo pueden visualizarse cuando exista una reserva confirmada entre alumno y tutor.

* Los materiales cargados por el tutor solo son visibles para alumnos que tengan una reserva confirmada con dicho tutor.

Genera:

1. Diseño del endpoint.
2. DTOs.
3. Service.
4. Controller.
5. Repositories necesarios.
6. Validaciones de autorización.
7. Swagger/OpenAPI.
8. Tests.
9. Cambios necesarios en SecurityConfig.

Explica cualquier decisión de diseño que no esté explícita en la Historia de Usuario.
