# US-26 — Acceder a materiales de un tutor (Backend Spec)

## 1. Contexto

| Campo | Valor |
|---|---|
| ID Historia | US-26 |
| Sprint | 4 |
| Story Points | 5 |
| Prioridad | BAJA |
| Módulo | recursos |
| Tags | #RN-23 |
| Relacionadas | US-16 (carga de material), US-07 (perfil tutor), US-41 (sesión confirmada), US-27 (denuncia) |

**Historia:** Como alumno, quiero acceder y descargar los materiales de estudio de un tutor con quien concreté una sesión, para complementar mi aprendizaje más allá de la hora reservada.

**Regla de negocio clave (RN-23):** el acceso a materiales se desbloquea por **sesión completada** (no por reserva, no por calificación) y es **permanente e irrevocable**.

---

## 2. Stack

- Java 21 + Spring Boot 3.5
- MySQL 8
- Supabase Storage (ya integrado en US-16, se reutiliza — no se crea infraestructura nueva)
- Spring Security (JWT existente)

---

## 3. Alcance de esta historia

Esta spec cubre **solo la capa de control de acceso y listado/filtrado** sobre los materiales ya existentes (US-16). No se crea un nuevo sistema de almacenamiento ni un nuevo flujo de subida.

Quedan **fuera de alcance** de esta historia:
- La denuncia en sí (criterio 4 solo requiere exponer el `materialId` al endpoint de US-27 ya existente).
- Cambios al modelo de subida de materiales de US-16.

---

## 4. Modelo de datos

**No se agregan tablas nuevas.** El acceso se resuelve en tiempo de consulta a partir de la tabla de sesiones existente (US-41), evitando así tener que sincronizar un estado de "acceso desbloqueado" que podría desincronizarse con el historial real de sesiones.

Se asume (validar contra el modelo real de US-41) una tabla `sesion` con al menos:

```
sesion
- id (PK)
- alumno_id (FK -> usuario)
- tutor_id (FK -> usuario)
- materia_id (FK -> materia)
- estado (ENUM: PENDIENTE, CONFIRMADA, COMPLETADA, CANCELADA)
- fecha_sesion
```

Y la tabla `material` de US-16:

```
material
- id (PK)
- tutor_id (FK -> usuario)
- materia_id (FK -> materia)
- tipo (ENUM)
- url_storage
- ...
```

**Regla de acceso (derivada, sin persistencia adicional):**
```sql
EXISTS (
  SELECT 1 FROM sesion s
  WHERE s.alumno_id = :alumnoId
    AND s.tutor_id = :tutorId
    AND s.materia_id = :materiaId
    AND s.estado = 'COMPLETADA'
)
```
Como el estado `COMPLETADA` de una sesión no se revierte una vez alcanzado, esta consulta ya satisface el criterio 3 (permanencia) sin necesidad de una tabla `acceso_material` separada.

> ⚠️ **A confirmar con el equipo:** si en el modelo real la calificación de la sesión (post-COMPLETADA) dispara algún cambio de estado o crea un registro distinto, hay que verificar que ese cambio no excluya la sesión de esta query. Si existiera un estado posterior tipo `CALIFICADA`, ajustar el filtro a `estado IN ('COMPLETADA','CALIFICADA')`.

---

## 5. Criterios de aceptación (Gherkin)

### AC1 — Visibilidad de la pestaña de materiales

```gherkin
Feature: Visibilidad condicional de materiales de un tutor

  Scenario: Alumno con sesión completada ve la pestaña
    Given un alumno autenticado
    And el alumno tiene al menos una sesión en estado COMPLETADA con el tutor "X"
    When el alumno solicita GET /api/tutores/{tutorId}/materiales/acceso
    Then la respuesta indica accesoHabilitado = true

  Scenario: Alumno sin sesión completada no ve la pestaña
    Given un alumno autenticado
    And el alumno NO tiene ninguna sesión en estado COMPLETADA con el tutor "X"
    When el alumno solicita GET /api/tutores/{tutorId}/materiales/acceso
    Then la respuesta indica accesoHabilitado = false

  Scenario: Alumno con sesión solo reservada/confirmada (no completada)
    Given un alumno con una sesión en estado CONFIRMADA con el tutor "X"
    When el alumno solicita GET /api/tutores/{tutorId}/materiales/acceso
    Then la respuesta indica accesoHabilitado = false
```

### AC2 — Exploración, filtrado y descarga

```gherkin
Feature: Listado y descarga de materiales habilitados

  Scenario: Listar materiales de materias con sesión completada
    Given el alumno tiene sesión COMPLETADA con el tutor "X" en la materia "Álgebra"
    And el tutor "X" también tiene materiales publicados en la materia "Física" (sin sesión completada del alumno ahí)
    When el alumno solicita GET /api/tutores/{tutorId}/materiales
    Then la respuesta incluye solo materiales de la materia "Álgebra"
    And no incluye materiales de la materia "Física"

  Scenario: Filtrar materiales por materia
    Given el alumno tiene acceso habilitado a materiales del tutor "X" en las materias "Álgebra" (id 5) y "Cálculo" (id 8)
    When el alumno solicita GET /api/tutores/{tutorId}/materiales?materiaId=5
    Then la respuesta incluye solo materiales de la materia con id 5
    And no incluye materiales de la materia con id 8

  Scenario: Filtrar por una materia sin acceso habilitado
    Given el alumno tiene acceso habilitado a materiales del tutor "X" solo en la materia "Álgebra" (id 5)
    When el alumno solicita GET /api/tutores/{tutorId}/materiales?materiaId=9
    Then la respuesta es una lista vacía
    And no se produce un error 500 ni se filtra información de materiales de la materia 9

  Scenario: Descargar un material habilitado
    Given el alumno tiene acceso habilitado a un material "M1"
    When el alumno solicita GET /api/materiales/{materialId}/descarga
    Then el sistema retorna una URL firmada de Supabase Storage válida
    And el archivo se descarga sin error

  Scenario: Intento de descarga sin acceso habilitado
    Given el alumno NO tiene sesión COMPLETADA con el tutor dueño del material "M2"
    When el alumno solicita GET /api/materiales/{materialId}/descarga para "M2"
    Then el sistema responde 403 Forbidden
```

### AC3 — Permanencia del acceso

```gherkin
Feature: Acceso permanente

  Scenario: El acceso no se revoca con el tiempo
    Given el alumno desbloqueó acceso a materiales del tutor "X" hace 6 meses
    When el alumno solicita GET /api/tutores/{tutorId}/materiales
    Then la respuesta sigue incluyendo los materiales correspondientes

  Scenario: El acceso no se revoca al calificar la sesión
    Given el alumno tiene sesión COMPLETADA con el tutor "X"
    And el alumno califica la sesión
    When el alumno solicita GET /api/tutores/{tutorId}/materiales/acceso
    Then la respuesta sigue indicando accesoHabilitado = true
```

### AC4 — Opción de denuncia

```gherkin
Feature: Denuncia de material

  Scenario: Cada material expone su identificador para denuncia
    Given el alumno tiene acceso habilitado a materiales del tutor "X"
    When el alumno solicita GET /api/tutores/{tutorId}/materiales
    Then cada material en la respuesta incluye su "id"
    And ese "id" es válido como materialId para POST /api/denuncias (US-27)
```

---

## 6. Endpoints

| Método | Path | Descripción | Nuevo / Existente |
|---|---|---|---|
| GET | `/api/tutores/{tutorId}/materiales/acceso` | Devuelve `{ accesoHabilitado: boolean }` para mostrar/ocultar la pestaña | **Nuevo** |
| GET | `/api/tutores/{tutorId}/materiales?materiaId={materiaId}` | Lista materiales del tutor filtrados por acceso del alumno; `materiaId` opcional para acotar a una materia puntual | **Nuevo** (envuelve repositorio de US-16) |
| GET | `/api/materiales/{materialId}/descarga` | Genera URL firmada de descarga, validando acceso | **Existente (US-16), agregar validación de acceso** |

### Contratos de respuesta

```json
// GET /api/tutores/{tutorId}/materiales/acceso
{
  "accesoHabilitado": true
}
```

```json
// GET /api/tutores/{tutorId}/materiales
{
  "materiales": [
    {
      "id": 101,
      "titulo": "Guía de límites",
      "tipo": "PDF",
      "materiaId": 5,
      "materiaNombre": "Álgebra",
      "fechaPublicacion": "2026-03-10T14:00:00Z"
    }
  ]
}
```

---

## 7. Clases y ubicación (package `com.knowlink.api`)

```
com.knowlink.api.materiales
 ├── controller
 │    ├── MaterialController.java          (existente, US-16)
 │    └── MaterialAccesoController.java     [NUEVO] -> expone /acceso y GET listado filtrado
 ├── service
 │    ├── MaterialService.java              (existente, US-16 — subida/almacenamiento)
 │    └── MaterialAccesoService.java        [NUEVO] -> resuelve accesoHabilitado() y materiasConAcceso()
 ├── repository
 │    └── MaterialRepository.java           (existente — agregar método findByTutorIdAndMateriaIdIn)
 └── dto
      ├── AccesoMaterialesResponseDTO.java  [NUEVO]
      └── MaterialResumenDTO.java           [NUEVO]

com.knowlink.api.sesiones
 └── repository
      └── SesionRepository.java             (existente, US-41 — agregar método
                                              findMateriaIdsByAlumnoAndTutorAndEstado)
```

**Métodos nuevos a agregar:**

```java
// SesionRepository
@Query("SELECT DISTINCT s.materia.id FROM Sesion s " +
       "WHERE s.alumno.id = :alumnoId AND s.tutor.id = :tutorId AND s.estado = 'COMPLETADA'")
List<Long> findMateriaIdsConSesionCompletada(Long alumnoId, Long tutorId);
```

```java
// MaterialAccesoService
boolean tieneAccesoHabilitado(Long alumnoId, Long tutorId);
List<Material> obtenerMaterialesAccesibles(Long alumnoId, Long tutorId, Long materiaIdFiltro); // materiaIdFiltro puede ser null
void validarAccesoODenegar(Long alumnoId, Long materialId); // usado antes de generar URL de descarga
```

---

## 8. Reglas de negocio

- **RN-23 — Desbloqueo por sesión completada:** el acceso se otorga por materia, no de forma global al tutor. Un alumno con sesión completada en "Álgebra" con el tutor X no accede automáticamente a materiales de "Física" del mismo tutor si no completó sesión en esa materia.
- El estado que habilita el acceso es exclusivamente `COMPLETADA`. Estados `PENDIENTE`, `CONFIRMADA` y `CANCELADA` no otorgan acceso.
- El acceso, una vez otorgado, no depende de acciones posteriores (calificación, paso del tiempo, cancelación de sesiones futuras con el mismo tutor).

---

## 9. Seguridad

- Todos los endpoints requieren autenticación (JWT existente).
- `MaterialAccesoService.validarAccesoODenegar` debe invocarse **en el backend**, no confiar en que el frontend oculte la pestaña o el botón de descarga. El caso de prueba 4 (bypass de URL) es crítico: sin esta validación server-side, cualquier alumno autenticado podría descargar materiales de tutores con los que no tuvo sesión.
- El filtro `materiaId` **no debe usarse como fuente de verdad de autorización**: primero se calcula el conjunto de materias con acceso (vía `SesionRepository`), y luego se interseca con el `materiaId` recibido. Si el alumno pasa un `materiaId` fuera de ese conjunto, la respuesta es una lista vacía — nunca un 403 ni un error que indique si esa materia existe o si el tutor la dicta, para evitar enumeración de materias del tutor.
- Respuesta ante acceso no autorizado: `403 Forbidden` con mensaje genérico (no revelar si el material existe o no, para evitar enumeración).

---

## 10. Dependencias y warnings

- ⚠️ Depende de que **US-41** exponga un estado `COMPLETADA` consistente y consultable en la tabla `sesion`. Si esa historia aún no está mergeada en la branch de destino, este endpoint queda bloqueado.
- ⚠️ Depende de **US-16** para la existencia de `MaterialRepository` y del mecanismo de URL firmada de Supabase Storage — no reimplementar.
- ⚠️ **US-27 (denuncia)** debe estar disponible para que el criterio 4 sea end-to-end funcional; el backend de esta historia solo garantiza que el `materialId` viaje en el DTO, no implementa la denuncia.
- ⚠️ Confirmar con backend de US-16 si `MaterialRepository` ya tiene paginación — si no la tiene, evaluar agregarla acá para tutores con muchos materiales (no bloqueante para este sprint, pero dejar nota en el PR).

---

## 11. Qué NO hacer

- No crear una tabla `acceso_materiales` ni ningún mecanismo de persistencia de "desbloqueo" — el acceso se deriva en cada consulta desde `sesion`.
- No modificar el modelo o el flujo de subida de materiales (US-16).
- No implementar la lógica de denuncia en este alcance (US-27).
- No filtrar por calificación de la sesión bajo ningún concepto — la calificación es irrelevante para el acceso (criterio 3).
- No exponer en la respuesta de listado si el alumno tiene o no sesiones pendientes con el tutor en otras materias (fuera de alcance, posible fuga de información).

---

## 12. Definition of Done

- [ ] Endpoint `GET /api/tutores/{tutorId}/materiales/acceso` implementado y testeado
- [ ] Endpoint `GET /api/tutores/{tutorId}/materiales` con filtro opcional `materiaId` implementado y testeado
- [ ] Test cubriendo `materiaId` fuera del conjunto con acceso -> lista vacía, sin error ni fuga de información
- [ ] Validación de acceso agregada a `GET /api/materiales/{materialId}/descarga` (403 si no corresponde)
- [ ] `SesionRepository.findMateriaIdsConSesionCompletada` implementado con test unitario
- [ ] `MaterialAccesoService` con cobertura de tests unitarios para: acceso otorgado, acceso denegado, filtrado por `materiaId` (dentro y fuera del conjunto con acceso)
- [ ] Test de integración cubriendo AC1, AC2, AC3 y AC4 (caso positivo y negativo de cada uno)
- [ ] Test específico de bypass (caso de prueba 4): request directo al endpoint de descarga sin sesión completada devuelve 403
- [ ] Sin regresiones en endpoints existentes de US-16 (subida de materiales)
- [ ] Documentación de los 2 endpoints nuevos agregada al README/Swagger del módulo `recursos`