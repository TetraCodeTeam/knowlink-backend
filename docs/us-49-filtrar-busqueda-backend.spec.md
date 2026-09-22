# SPEC — US-49: Filtrar Resultados de Búsqueda — BACKEND

> Uso: pasar este archivo completo a OpenCode (modelo DeepSeek V4 free) parado en el repo `TetraCodeTeam/knowlink-backend`. Contiene todo lo necesario para desarrollar la parte backend de esta historia de forma autónoma.

## Metadata de la historia

| Campo | Valor |
|---|---|
| ID | US-49 |
| Sprint | 3 |
| Módulo | búsqueda |
| Story Points | 5 |
| Prioridad | MEDIA |
| Tags | #RN-06 #RN-07 |
| Relacionadas | US-06A (búsqueda base), US-07 (perfil tutor), US-22 (insignia verificado), US-08 (calificaciones) |
| Repo | `TetraCodeTeam/knowlink-backend` |

## Historia de usuario

Como alumno, quiero refinar los resultados de búsqueda usando filtros, para acotar las opciones según mis preferencias y disponibilidad.

## Criterios de aceptación (Gherkin)

```gherkin
Feature: Filtrar resultados de búsqueda

  Scenario: AC-1 — Filtros disponibles
    Given un alumno realizando una búsqueda de tutores
    Then puede filtrar por modalidad, tipo de compensación, disponibilidad horaria, tutor verificado y calificación mínima

  Scenario: AC-2 — Combinación de filtros (AND)
    Given un alumno que aplica más de un filtro simultáneamente
    When se ejecuta la búsqueda
    Then el backend devuelve únicamente tutores que cumplen TODOS los criterios seleccionados a la vez

  Scenario: AC-5 — Sin resultados por filtros
    Given una búsqueda base con resultados (US-06A)
    When se aplican filtros cuya combinación no matchea ningún tutor
    Then el backend distingue esta respuesta de la de "búsqueda base sin resultados", para que el frontend muestre "No se encontraron tutores para los filtros seleccionados"
```

> Nota: AC-3 (filtros visualmente destacados) y AC-4 (quitar filtros individualmente / limpiar todos) son de UI pura y se resuelven en el frontend, siempre que este spec de backend soporte pasar cualquier subconjunto de filtros (incluido ninguno) en el mismo request de búsqueda, para que "limpiar filtros" sea simplemente repetir la búsqueda base sin params de filtro.

---

## Stack confirmado del repo

- Java 21 + Spring Boot 3.5, MySQL 8, Spring Security con JWT.
- Perfiles Spring: `development`, `production`, `test` (H2 en memoria).
- Estructura de paquetes real conocida hasta ahora (usuarios); el módulo `búsqueda` es nuevo — crearlo siguiendo la misma convención de subcarpetas (`controllers/`, `data/`, `repositories/`, `services/`).

## ⚠️ Dependencias a verificar antes de empezar

Esta historia **extiende** el endpoint de búsqueda base de US-06A — no crea una búsqueda nueva desde cero. Antes de escribir código, el agente debe:

1. **Localizar el endpoint y servicio de búsqueda de US-06A** en el repo (probablemente en un paquete `busqueda/` o `search/` ya existente). Si existe, extenderlo con los nuevos query params de filtro. Si no existe todavía, este spec asume que hay que construir la búsqueda base y los filtros juntos — en ese caso, priorizar primero que la búsqueda base funcione (por materia/texto, según defina US-06A) antes de capar con filtros.
2. **Modelo de "modalidad" y "tipo de compensación" del tutor**: revisar si ya existen estos campos en el perfil de tutor (posiblemente de US-07). Si no existen, el agente deberá agregarlos a la entidad correspondiente — esto es una dependencia de modelo de datos, no solo de query.
3. **Insignia de verificación (US-22)**: revisar si ya existe un flag o entidad de "tutor verificado" asociado a materia (el criterio dice "verificado **en la materia buscada**", no verificado en general — esto puede requerir una relación tutor-materia-verificación, no solo un booleano en el tutor).
4. **Calificaciones (US-08)**: revisar cómo se calcula/almacena la calificación promedio del tutor (¿campo desnormalizado actualizado por trigger/servicio, o cálculo on-the-fly con `AVG()` sobre la tabla de reseñas?). El filtro por calificación mínima debe usar el mismo criterio de cálculo que ya use la vista de perfil de tutor, para no mostrar números inconsistentes entre pantallas.
5. **Disponibilidad horaria**: revisar cómo se modela la disponibilidad del tutor (¿tabla de franjas horarias por día de la semana?). Si no existe todavía, es la pieza más grande de esta historia — coordinarlo, ya que puede exceder el alcance de 5 story points si hay que modelarla desde cero.

Si alguna de estas dependencias (2-5) no está implementada, el agente debe: implementar la mínima necesaria para que el filtro funcione, dejar TODOs claros, y **priorizar los filtros más simples primero** (modalidad, compensación) antes que los que dependen de otras historias no confirmadas (verificado, calificación, disponibilidad), para entregar valor incremental.

## Objetivo

Extender el endpoint de búsqueda de tutores para aceptar filtros combinables (modalidad, compensación, disponibilidad horaria, verificado, calificación mínima), aplicados con lógica AND, y distinguir en la respuesta "sin resultados por filtro" de "sin resultados de búsqueda base".

## Ubicación de los cambios

- `busqueda/controllers/` → extender el endpoint de búsqueda existente (o crearlo si esta es la primera historia del módulo) agregando los nuevos query params.
- `busqueda/services/` → lógica de armado dinámico de la query/specification según los filtros presentes.
- `busqueda/data/` → DTO de request de búsqueda (agregar campos de filtro) y DTO de response.
- `busqueda/repositories/` → usar JPA Specifications o Querydsl (revisar qué usa ya el proyecto para queries dinámicas; si no usa ninguno, JPA Specifications es la opción más estándar en Spring Boot y la recomendada acá) para poder combinar filtros opcionales sin construir queries a mano con concatenación de strings.

## Alcance funcional

1. **Extensión del endpoint de búsqueda** (ej. `GET /api/busqueda/tutores?...`, ajustar al path real de US-06A)
   - Nuevos query params, todos opcionales:
     - `modalidad`: `PRESENCIAL | VIRTUAL`
     - `compensacion`: `GRATUITO | PAGO`
     - `diaSemana`: `LUNES..DOMINGO` (opcional, puede combinarse con franja horaria)
     - `franjaHoraria`: rango horario, formato a definir según cómo esté modelada la disponibilidad (ej. `MANANA | TARDE | NOCHE` si es por franjas fijas, o `horaDesde`/`horaHasta` si es rango libre — revisar convención ya usada si existe)
     - `soloVerificados`: boolean
     - `calificacionMinima`: número (1-5)
   - Todos los filtros presentes se combinan con **AND** (AC-2) — un tutor debe cumplir todos los filtros activos simultáneamente para aparecer.
   - Si no se envía ningún filtro, se comporta exactamente igual que la búsqueda base de US-06A (esto es lo que permite que "Limpiar filtros" en el frontend sea trivial).

2. **Construcción dinámica de la query**
   - Usar JPA Specifications: un `Specification<Tutor>` por cada filtro, combinados con `Specification.where(...).and(...)` solo para los filtros presentes en el request.
   - El filtro de "verificado en la materia buscada" debe cruzar contra la materia de la búsqueda base (no contra todas las materias del tutor), según la aclaración del criterio 1.
   - El filtro de calificación mínima debe reusar el mismo cálculo de promedio que use el resto del sistema (ver punto de dependencias arriba).

3. **Distinción "sin resultados por filtro" vs. "sin resultados de búsqueda base" (AC-5)**
   - El backend no necesita dos endpoints distintos: alcanza con que el frontend sepa si había o no filtros activos en el request que hizo. Documentar esto claramente en la respuesta o dejarlo como responsabilidad exclusiva del frontend (que ya sabe qué filtros mandó). **Definición elegida para este spec**: el backend simplemente devuelve una lista (potencialmente vacía) de resultados; la distinción del mensaje a mostrar es lógica de frontend basada en si había filtros activos en la request que generó la lista vacía. No se requiere un campo adicional en la response para esto, salvo que el equipo prefiera que el backend lo indique explícitamente (ej. `{ "results": [], "filtersApplied": true }`) — **opción recomendada** para no duplicar esa lógica en el cliente; usarla si no complica el contrato existente de US-06A.

## Criterios de aceptación → validación técnica

- AC-1: test de integración por cada filtro individual, verificando que acota correctamente los resultados.
- AC-2: test de integración con 2+ filtros combinados (ej. modalidad + verificado) → solo aparecen tutores que cumplen ambos.
- AC-5: test de integración — búsqueda base con resultados + filtro que no matchea ninguno → lista vacía, con el flag/indicador que el frontend necesita para diferenciar el mensaje.

## Tests esperados (perfil `test` con H2)

- Unit: cada `Specification` de filtro aislada (modalidad, compensación, día/franja, verificado, calificación mínima) sobre un dataset de tutores de prueba.
- Unit: combinación de `Specification`s con AND produce la intersección esperada, no la unión.
- Integración: request sin filtros devuelve el mismo resultado que el endpoint de búsqueda base de US-06A (no debe haber regresión).
- Integración: cada filtro individual y al menos una combinación de dos filtros.
- Edge case: `calificacionMinima` fuera de rango (ej. `6` o `-1`) → devolver `400` o clamplear al rango válido (definir explícitamente en el código cuál de las dos estrategias se usa, y documentarlo).
- Edge case: filtro de verificado sin que la materia tenga tutores verificados → lista vacía, no error.

## No hacer

- No implementar en este spec la búsqueda base (US-06A), el perfil de tutor (US-07), el sistema de insignias (US-22) ni el de calificaciones (US-08) — se asumen implementadas o en desarrollo por separado; este spec solo filtra sobre esos datos.
- No modificar nada del repo `knowlink-frontend` — ese consumo está en un spec aparte.
- No construir queries dinámicas con concatenación manual de strings SQL — usar Specifications/Querydsl para evitar inyección y mantener legibilidad.

## Definition of Done (backend)

- [ ] Endpoint de búsqueda extendido con los 5 filtros, todos opcionales y combinables con AND.
- [ ] Sin filtros aplicados, el comportamiento es idéntico a la búsqueda base de US-06A (sin regresión).
- [ ] Filtro de verificado cruza correctamente contra la materia buscada, no contra verificación general del tutor.
- [ ] Filtro de calificación usa el mismo cálculo que el resto del sistema.
- [ ] Respuesta permite al frontend distinguir "sin resultados por filtro" de "sin resultados base" (AC-5).
- [ ] Tests unitarios e integración pasando en perfil `test`.
