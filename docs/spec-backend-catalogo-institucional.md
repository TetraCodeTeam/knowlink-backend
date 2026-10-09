# Spec Backend — Catálogo multi-institución (Institución / Carrera / Materia)

## Metadata

- **Feature ID:** FEAT-CATALOGO-INSTITUCIONAL
- **Historias relacionadas:** US-01, US-06, US-42, US-43
- **Repo:** `knowlink-backend`
- **Rama sugerida:** `feat/catalogo-institucional`
- **Alcance:** MVP — soporta 2 instituciones cargadas manualmente por el equipo, con un catálogo chico de carreras y materias por cada una.

## Contexto para el agente

Hoy el catálogo de carreras y materias es global a toda la plataforma (US-43). Este cambio introduce la entidad **Institución** como raíz del catálogo: toda Carrera y toda Materia pasan a pertenecer a una Institución. Además, se resuelve el caso de materias transversales a varias carreras (ej. "Análisis Matemático I" cursada por Ingeniería en Sistemas e Ingeniería Industrial) mediante una **carrera reservada "Materias Compartidas"**, creada automáticamente al dar de alta cada institución — no mediante un campo booleano ni la ausencia de asociación a carrera.

---

## Decisiones de implementación (acordadas con el equipo — 2026-10-02)

Este spec se implementa **siguiendo las convenciones reales del repo** (`docs/base-standards.md`). Desviaciones respecto del texto original, ya acordadas:

| Punto | Spec original | Implementación |
|---|---|---|
| Naming de entidades/código | `Institucion`, `Carrera`, `Materia` | Inglés: `Institution`, `Career` (existente), `Subject` (existente) |
| Claves primarias | `Long` | `UUID` (`GenerationType.UUID`) |
| Rutas | `/api/catalogo/*` | `/api/v1/catalog/*` (regla `/api/v1/**` del repo) |
| Campo `tipo` de Carrera | `tipo` | `type`, valores `REGULAR` / `COMPARTIDA` (valores del contrato, sin cambios) |
| Contrato 409 duplicado de materia | `{"error","message","materiaExistenteId"}` | **Se mantiene tal cual** (excepción + handler dedicado); el resto de errores sigue con `ApiError{status,message,detail}` |
| Crear carrera con `tipo = COMPARTIDA` en el body | Tabla de endpoints: "se fuerza a REGULAR" | **Rechazo 400** con "No es posible crear carreras de tipo compartido manualmente" (prevalece el Gherkin y la sección "No hacer") |
| `GET /api/v1/catalog/subjects` | "Público (autenticado)" | **Público** (whitelist): el registro de tutor (US-42) lo consume sin sesión, igual que los endpoints `/subjects` actuales |
| Institución del usuario (US-06) | Columna `institucionId` en los perfiles | **Derivada de `career.institution`** — sin columna nueva en perfiles |
| Placement del módulo | `catalogo/` plano | Estructura del repo: `catalog/{controllers{interfaces,implementations,requests,responses}, data{models,enums}, repositories, services{interfaces,implementations}, config}` |
| Endpoints viejos `/api/v1/careers`, `/api/v1/subjects` | No mencionados | Se mantienen **deprecados** (anotación `@Deprecated` en Swagger) sobre el nuevo modelo, hasta migrar el frontend |
| Migración de datos | Script manual revisado por humano | **Se mantiene**: `docs/migrations/catalogo-institucional.sql`; no se ejecuta automáticamente |
---

## Modelo de datos

### Institucion
| Campo | Tipo | Notas |
|---|---|---|
| id | Long | PK |
| nombre | String | Único |
| createdAt | Timestamp | |

### Carrera
| Campo | Tipo | Notas |
|---|---|---|
| id | Long | PK |
| nombre | String | Único por institución |
| institucionId | Long | FK a Institucion |
| tipo | Enum (`REGULAR`, `COMPARTIDA`) | Ver nota abajo |

> **Importante:** la carrera reservada se identifica por `tipo = COMPARTIDA`, **nunca por nombre**. No matchear por `nombre == "Materias Compartidas"` en ninguna consulta ni validación — es frágil ante renombres o errores de tipeo. Toda consulta que necesite incluir la carrera transversal debe filtrar por `tipo`.

### Materia
| Campo | Tipo | Notas |
|---|---|---|
| id | Long | PK |
| nombre | String | Único por `(institucionId, nombre)` — no único globalmente |
| institucionId | Long | FK a Institucion (desnormalizado desde las carreras asociadas para simplificar la validación de unicidad y el filtrado) |
| createdAt | Timestamp | |

### MateriaCarrera (tabla intermedia)
| Campo | Tipo | Notas |
|---|---|---|
| materiaId | Long | FK compuesta |
| carreraId | Long | FK compuesta |

Relación N-a-N entre Materia y Carrera. Una materia asociada **únicamente** a la carrera `COMPARTIDA` de su institución es transversal a todas las carreras de esa institución. Una materia puede tener múltiples asociaciones simultáneas (ej. específica de una carrera + la compartida, aunque en la práctica no debería darse — no lo bloqueamos a nivel de datos, es una decisión de carga).

---

## Criterios de aceptación (Gherkin)

```gherkin
Feature: Catálogo de carreras y materias scoped por institución

  Scenario: Crear una institución genera automáticamente su carrera reservada
    Given no existe la institución "UNVM"
    When el administrador crea la institución "UNVM"
    Then el sistema crea la institución
    And el sistema crea automáticamente una carrera con tipo COMPARTIDA
      y nombre "Materias Compartidas" asociada a "UNVM"

  Scenario: No se puede crear manualmente una carrera de tipo COMPARTIDA
    Given existe la institución "UTN FRVM"
    When el administrador intenta registrar una carrera indicando tipo COMPARTIDA
    Then el sistema rechaza la operación
    And responde "No es posible crear carreras de tipo compartido manualmente"

  Scenario: No se puede eliminar ni renombrar la carrera reservada
    Given la institución "UTN FRVM" tiene su carrera reservada "Materias Compartidas"
    When el administrador intenta eliminarla o renombrarla
    Then el sistema rechaza la operación

  Scenario: Registrar una materia nueva sin conflicto de nombre
    Given la institución "UTN FRVM" existe con la carrera "Ingeniería en Sistemas"
    When el administrador registra la materia "Programación I" asociada a "Ingeniería en Sistemas"
    Then la materia se crea correctamente

  Scenario: Intentar registrar una materia con nombre ya existente en la misma institución
    Given existe la materia "Análisis Matemático I" en "UTN FRVM" asociada a "Ingeniería en Sistemas"
    When el administrador intenta crear una materia "Análisis Matemático I" en "UTN FRVM"
      asociada a la carrera reservada "Materias Compartidas"
    Then el sistema rechaza la creación con status 409
    And la respuesta incluye el id de la materia existente
    And el mensaje sugiere "Ya existe una materia con ese nombre en esta institución. ¿Querés agregarle esta carrera?"

  Scenario: Editar las asociaciones de carrera de una materia existente
    Given existe la materia "Análisis Matemático I" en "UTN FRVM" asociada solo a "Ingeniería en Sistemas"
    When el administrador la asocia también a "Materias Compartidas"
    Then la materia queda asociada a ambas carreras
    And el nombre de la materia permanece sin cambios

  Scenario: Registrar la misma materia con nombre repetido en instituciones distintas
    Given existe la materia "Programación I" en "UTN FRVM"
    When el administrador registra una materia "Programación I" en "UNVM"
    Then la creación se permite sin conflicto (la unicidad es por institución, no global)

  Scenario: Listar materias visibles para una carrera específica
    Given la institución "UTN FRVM" tiene la materia "Programación I" (asociada a Ingeniería en Sistemas)
      y la materia "Análisis Matemático I" (asociada a Materias Compartidas)
    When se consultan las materias visibles para un usuario de "Ingeniería en Sistemas" en "UTN FRVM"
    Then el resultado incluye "Programación I" y "Análisis Matemático I"
    And no incluye materias de otras instituciones

  Scenario: Búsqueda de materias y tutores filtrada por institución (US-06)
    Given un alumno pertenece a la institución "UTN FRVM"
    When busca "Análisis" en el buscador unificado
    Then los resultados de materias y tutores solo incluyen los de "UTN FRVM"

  Scenario: Registro de alumno con selección de institución (US-01)
    Given un visitante completa el formulario de registro
    When selecciona la institución "UTN FRVM"
    Then el selector de carrera se habilita mostrando solo carreras regulares de "UTN FRVM"
      (sin incluir la carrera reservada "Materias Compartidas" como opción seleccionable)

  Scenario: Registro de tutor con materias combinadas (US-42)
    Given un visitante se registra como tutor y selecciona la institución "UTN FRVM"
      y la carrera "Ingeniería en Sistemas"
    When abre el selector de materias
    Then ve las materias asociadas a "Ingeniería en Sistemas" y las asociadas a "Materias Compartidas" de "UTN FRVM"
```

---

## Endpoints

| Método | Ruta | Descripción | Rol |
|---|---|---|---|
| POST | `/api/catalogo/instituciones` | Crear institución (dispara creación automática de la carrera reservada) | ADMIN |
| GET | `/api/catalogo/instituciones` | Listar instituciones (uso público, selects de registro) | Público |
| POST | `/api/catalogo/instituciones/{institucionId}/carreras` | Crear carrera regular (`tipo` se fuerza a `REGULAR` server-side, ignorar si viene en el body) | ADMIN |
| GET | `/api/catalogo/instituciones/{institucionId}/carreras` | Listar carreras de una institución (incluye la reservada, marcada con `tipo: COMPARTIDA` en el response) | Público |
| POST | `/api/catalogo/instituciones/{institucionId}/materias` | Crear materia con lista de `carreraIds` | ADMIN |
| PATCH | `/api/catalogo/materias/{materiaId}/carreras` | Reemplazar o agregar asociaciones de carrera de una materia existente (`nombre` no se acepta en este endpoint) | ADMIN |
| GET | `/api/catalogo/materias?institucionId=&carreraId=` | Listar materias visibles para una carrera (incluye las de la carrera + las de `COMPARTIDA` de esa institución) | Público (autenticado) |

### Contrato de error en duplicado de materia

```json
409 Conflict
{
  "error": "MATERIA_DUPLICADA",
  "message": "Ya existe una materia con ese nombre en esta institución.",
  "materiaExistenteId": 42
}
```

El frontend usa `materiaExistenteId` para redirigir a la edición de asociaciones en vez de mostrar un error genérico.

---

## Impacto en endpoints existentes

- **`POST /auth/register` (US-01):** agregar `institucionId` (obligatorio) y `carreraId` (obligatorio, debe pertenecer a la institución indicada — validar server-side, no confiar en el frontend).
- **`POST /auth/register-tutor` (US-42):** mismo agregado que arriba, más: las `materiaIds` enviadas deben pertenecer a la carrera elegida o a la carrera `COMPARTIDA` de la misma institución — rechazar con 400 si alguna materia no cumple esto.
- **`GET /search` (US-06):** agregar filtro implícito por la `institucionId` del usuario autenticado que realiza la búsqueda — no es un query param que el cliente controle, se resuelve server-side a partir del usuario en sesión.

---

## Stack confirmado

- Java 21 + Spring Boot 3.5, MySQL 8
- Paquete base: `com.knowlink.api`
- Perfiles Spring existentes: `development`, `production`, `test` (H2 en memoria) — los tests de integración de este feature corren contra el perfil `test`

## Advertencia de dependencia — leer antes de implementar

No hay evidencia en el repo de una herramienta de migraciones versionadas (Flyway/Liquibase) en `pom.xml` al momento de este spec. Si el proyecto usa `ddl-auto` de Hibernate para generar el esquema, agregar `institucionId` como columna `NOT NULL` en `Carrera` y `Materia` **rompe el arranque** si ya hay carreras/materias existentes en la base sin ese valor. Antes de implementar:
1. Confirmar si hay Flyway/Liquibase configurado.
2. Si no lo hay, el agente debe generar un script de migración manual que: (a) cree la institución "UTN FRVM" por defecto, (b) cree su carrera reservada "Materias Compartidas", (c) actualice todas las carreras y materias existentes para apuntar a esa institución, antes de aplicar la constraint `NOT NULL`.
3. No proceder con la migración de datos sin que un humano del equipo la revise — no es un paso para automatizar sin supervisión.

---

## Placement de clases (nuevo módulo)

```
src/main/java/com/knowlink/api/
└── catalogo/                      # Módulo nuevo
    ├── controllers/
    │   ├── InstitucionController.java
    │   ├── CarreraController.java
    │   └── MateriaController.java
    ├── data/
    │   ├── Institucion.java
    │   ├── Carrera.java
    │   ├── CarreraTipo.java        # enum REGULAR, COMPARTIDA
    │   ├── Materia.java
    │   ├── InstitucionDTO.java
    │   ├── CarreraDTO.java
    │   ├── MateriaDTO.java
    │   ├── CrearMateriaRequest.java
    │   └── MateriaDuplicadaException.java  # o en /exceptions si siguen esa convención global
    ├── repositories/
    │   ├── InstitucionRepository.java
    │   ├── CarreraRepository.java
    │   └── MateriaRepository.java
    └── services/
        ├── InstitucionService.java   # incluye la lógica de auto-creación de carrera reservada
        ├── CarreraService.java
        └── MateriaService.java
```

Modificar (no crear nuevos archivos salvo que no existan ya):
- `users/services/` — el servicio de registro de alumno y tutor (US-01, US-42) pasa a depender de `CarreraService`/`MateriaService` para validar que `carreraId`/`materiaIds` pertenecen a la `institucionId` indicada.
- El servicio de búsqueda (US-06) — agregar el filtro por institución del usuario autenticado.

---

## No hacer

- No implementar alta de institución por autoservicio ni verificación por dominio de correo — fuera de alcance del MVP.
- No implementar flujo de propuesta de materias por parte de tutores.
- No crear un rol de administrador institucional (scoped por institución) — el equipo es administrador único de todas las instituciones.
- No permitir editar ni eliminar el `nombre` de una materia existente desde `PATCH /materias/{id}/carreras` — ese endpoint solo toca asociaciones.
- No permitir crear, eliminar ni renombrar una carrera de `tipo = COMPARTIDA` desde los endpoints de carrera regulares.
- No matchear la carrera reservada por `nombre` en ningún punto del código — siempre por `tipo`.
- No exponer `GET /api/catalogo/materias` sin filtro de institución — devolver 400 si falta `institucionId`.

## Definition of Done

- [x] Entidades, repositorios y servicios del módulo `catalogo` implementados según el placement de arriba
- [x] Constraint de unicidad `(institucionId, nombre)` en Materia validada a nivel de base de datos (no solo en service)
- [x] Creación de institución dispara la creación automática y atómica de su carrera reservada (misma transacción)
- [x] Endpoint `PATCH /materias/{id}/carreras` implementado y cubierto por tests
- [x] US-01 y US-42 actualizados para exigir y validar `institucionId`/`carreraId`/`materiaIds`
- [x] US-06 actualizado para filtrar resultados por institución del usuario autenticado
- [x] Script o plan de migración de datos existente revisado por un humano del equipo antes de mergear a `main`
- [x] Tests unitarios de `InstitucionService`, `CarreraService`, `MateriaService` (incluyendo el caso de duplicado y el de carrera reservada)
- [x] Tests de integración de los 7 endpoints nuevos (perfil `test`, H2)
- [x] Swagger/OpenAPI actualizado con los nuevos endpoints y el contrato de error 409
