# US-52 — Agenda Semanal del Tutor (Backend)

> Spec generado a partir de la exploración real de `TetraCodeTeam/knowlink-backend`, rama `dev`. Ya existe una rama vacía `feat/tutor-weekly-schedule` (creada desde `dev`, sin commits propios todavía) — este spec está pensado para implementarse ahí.

## Metadata

| Campo | Valor |
|---|---|
| ID Historia | US-52 |
| Sprint | 4 |
| Épica | — |
| Story Points | 8 |
| Prioridad | MEDIA |
| Estado | Por Hacer |
| Repo / rama | `knowlink-backend`, rama `feat/tutor-weekly-schedule` (ya creada, base = `dev`) |
| Dependencias | US-09 (`tutors.availability` — solo lectura), US-10 (`bookings` — solo lectura) |
| Relacionada (no dependencia) | US-13 (Historial — vista alternativa en lista de la misma información) |

## Historia de usuario

Como tutor, quiero ver en un calendario semanal mis clases reservadas superpuestas a mis horarios disponibles, para tener de un vistazo mi carga de trabajo de la semana sin tener que cruzar dos pantallas distintas.

## Criterios de aceptación (Gherkin)

```gherkin
Feature: Agenda semanal del tutor

  Background:
    Given el tutor está autenticado con rol TUTOR
    And solicita su agenda para un rango de fechas (una semana)

  Scenario: Ver bloques de disponibilidad y clases reservadas juntos
    Given el tutor tiene bloques de disponibilidad y reservas activas en la semana solicitada
    When consulta GET /api/v1/tutors/me/weekly-schedule?from=X&to=Y
    Then la respuesta incluye availabilityBlocks y bookings como listas separadas
    And cada elemento trae su tipo implícito por el campo que lo contiene, permitiendo al frontend distinguirlos visualmente

  Scenario: Semana sin actividad
    Given el tutor no tiene bloques ni reservas en el rango solicitado
    When consulta la agenda semanal
    Then la respuesta es 200 con availabilityBlocks = [] y bookings = []
    And summary.confirmedBookingsCount = 0, summary.freeBlocksCount = 0, summary.nextClass = null

  Scenario: Resumen con actividad
    Given el tutor tiene 3 reservas con estado BOOKED y 5 bloques de disponibilidad libres en la semana
    When consulta la agenda semanal
    Then summary.confirmedBookingsCount = 3
    And summary.freeBlocksCount = 5
    And summary.nextClass contiene la reserva BOOKED más próxima desde el momento actual (zona horaria America/Argentina/Cordoba)

  Scenario: Navegación entre semanas, incluyendo semanas pasadas
    Given el tutor cambia el rango from/to a una semana anterior con clases ya dictadas
    When consulta la agenda semanal
    Then las reservas de esa semana se devuelven sin filtrarlas por estar en el pasado

  Scenario: Sin operaciones de escritura sobre disponibilidad
    Given el tutor consulta la agenda semanal
    Then el endpoint expuesto es únicamente GET
    And no existe ninguna ruta de creación/edición/eliminación de bloques bajo weekly-schedule
```

## Stack confirmado (verificado en el repo)

- Java 21, Spring Boot 3.5.3
- MySQL 8 vía Spring Data JPA
- `springdoc-openapi` para Swagger (`@Tag`, `@Operation`, `@ApiResponses`)
- Spring Security con `@PreAuthorize("hasRole('TUTOR')")` y `@AuthenticationPrincipal UserPrincipal`
- Lombok
- IDs de dominio en `UUID` (no `Long`)
- Sin nuevas tablas: **agregación de solo lectura** sobre `AvailabilityBlock` (US-09) y `Booking` (US-10)

## Lo que ya existe y se reutiliza

Esto no se reimplementa, se invoca:

| Necesidad | Clase / método existente |
|---|---|
| Resolver `TutorProfile` desde el `userId` autenticado | `ITutorProfileValidationService.findTutorProfileOrThrowException(UUID tutorUserId)` |
| Bloques de disponibilidad en un rango | `IAvailabilityBlockService.getBlocksInRange(UUID tutorUserId, LocalDate from, LocalDate to)` → `List<AvailabilityBlockResponse>` |
| Reservas activas en un rango | `IBookingRepository.findActiveBookingsInRange(UUID tutorUserId, LocalDate from, LocalDate to, List<BookingStatus> activeStatuses)` → `List<Booking>` |
| Grupo de estados "activos" | `BookingStatusGroups.ACTIVE` = `PENDING, BOOKED, IN_PROGRESS` (en `com.knowlink.api.tutors.availability.data.enums`) |
| Mapear `Booking` a DTO | `BookingMapper.toResponse(Booking)` → `BookingResponse` (ya existe, reutilizable tal cual) |
| Zona horaria de referencia para "ahora" | `AppTimeZone.ZONE` = `America/Argentina/Cordoba` |

`AvailabilityBlockResponse` (ya existente) y `BookingResponse` (ya existente) se reutilizan **sin modificarlos** en la respuesta del nuevo endpoint. No hace falta crear DTOs propios para los ítems de la grilla, solo para la envoltura y el resumen.

## Diseño del endpoint

Sigue la misma convención que `ITutorAvailabilityController` (`/api/v1/tutors/me/availability-blocks?from=&to=`):

```
GET /api/v1/tutors/me/weekly-schedule?from=YYYY-MM-DD&to=YYYY-MM-DD
```

- Protegido con `@PreAuthorize("hasRole('TUTOR')")`, `@AuthenticationPrincipal UserPrincipal principal`.
- `from`/`to` con `@DateTimeFormat(iso = DateTimeFormat.ISO.DATE)`, igual que en `ITutorAvailabilityController`.
- Response `200 OK`:

```json
{
  "from": "2026-09-01",
  "to": "2026-09-07",
  "availabilityBlocks": [
    { "availabilityBlockId": "...", "date": "2026-09-01", "startTime": "09:00", "endTime": "10:00", "repeatWeekly": true, "autoGenerated": false }
  ],
  "bookings": [
    { "bookingId": "...", "sessionDate": "2026-09-01", "startTime": "10:00", "endTime": "11:00", "amount": 1500.00, "modality": "VIRTUAL", "topic": "...", "bookingStatus": "BOOKED", "tutorSubjectName": "...", "tutorFullName": "...", "studentFullName": "..." }
  ],
  "summary": {
    "confirmedBookingsCount": 3,
    "freeBlocksCount": 5,
    "nextClass": {
      "bookingId": "...",
      "sessionDate": "2026-09-01",
      "startTime": "10:00",
      "endTime": "11:00"
    }
  }
}
```

- `summary.nextClass` es `null` cuando no hay reservas `BOOKED` futuras.

### DTOs nuevos (únicos que hace falta crear)

```
com.knowlink.api.tutors.schedule.controllers.responses.WeeklyScheduleResponse
com.knowlink.api.tutors.schedule.controllers.responses.WeeklyScheduleSummaryResponse
com.knowlink.api.tutors.schedule.controllers.responses.NextClassResponse
```

```java
public record WeeklyScheduleResponse(
        LocalDate from,
        LocalDate to,
        List<AvailabilityBlockResponse> availabilityBlocks,
        List<BookingResponse> bookings,
        WeeklyScheduleSummaryResponse summary
) {}

public record WeeklyScheduleSummaryResponse(
        int confirmedBookingsCount,
        int freeBlocksCount,
        NextClassResponse nextClass
) {}

public record NextClassResponse(
        UUID bookingId,
        LocalDate sessionDate,
        LocalTime startTime,
        LocalTime endTime
) {}
```

## Ubicación de clases / paquetes

Nuevo módulo hermano de `tutors.availability`, siguiendo exactamente la misma estructura interna que ya usa el resto del proyecto (`controllers/interfaces`, `controllers/implementations`, `controllers/responses`, `services/interfaces`, `services/implementations`):

```
com.knowlink.api.tutors.schedule
 ├── controllers
 │    ├── interfaces
 │    │    └── ITutorWeeklyScheduleController.java   (nuevo — solo GET)
 │    ├── implementations
 │    │    └── TutorWeeklyScheduleControllerImpl.java (nuevo)
 │    └── responses
 │         ├── WeeklyScheduleResponse.java            (nuevo)
 │         ├── WeeklyScheduleSummaryResponse.java      (nuevo)
 │         └── NextClassResponse.java                  (nuevo)
 └── services
      ├── interfaces
      │    └── ITutorWeeklyScheduleService.java        (nuevo)
      └── implementations
           └── TutorWeeklyScheduleServiceImpl.java     (nuevo — orquesta llamadas, no reimplementa lógica de negocio)
```

`TutorWeeklyScheduleServiceImpl` inyecta y reutiliza:
- `ITutorProfileValidationService` (resolver tutor)
- `IAvailabilityBlockService` (bloques del rango)
- `IAvailabilityBlockRepository` (solo para el flag `available`, ver advertencia abajo)
- `IBookingRepository` (reservas activas del rango)
- `BookingMapper` (mapear `Booking` → `BookingResponse`)

No debe inyectar ni tocar `IAvailabilityBlockRepository` ni `IBookingRepository` para escritura, y no debe usar `AvailabilityBlockServiceImpl` ni `BookingServiceImpl` directamente (son las implementaciones concretas de US-09/US-10; se depende de sus interfaces `IAvailabilityBlockService` / `IBookingRepository`, no de las clases).

## Advertencias de dependencia (encontradas leyendo el código real)

1. **`AvailabilityBlockResponse` no expone el flag `available`.** El record actual es `(availabilityBlockId, date, startTime, endTime, repeatWeekly, autoGenerated)` — no incluye `available`. Para calcular `freeBlocksCount` (bloques *libres*, no solo bloques existentes) hace falta ese flag. Dos opciones, a decidir con el equipo antes de implementar:
   - (a) Consultar `IAvailabilityBlockRepository.findInRange(...)` directamente desde `TutorWeeklyScheduleServiceImpl` para obtener las entidades completas y filtrar por `available == true` al calcular el resumen, sin tocar el DTO existente de US-09.
   - (b) Pedir que se agregue `available` a `AvailabilityBlockResponse` (impacta a US-09 y a quien ya consuma ese DTO en el frontend).
   - **Recomendado: opción (a)**, no modifica un contrato ya usado por otra historia.

2. **`IAvailabilityBlockRepository.findInRange` excluye bloques pasados por diseño** (filtra `ab.date > today OR (ab.date = today AND ab.endTime > nowTime)`), y además existe un job `deletePastOrphaned` que borra físicamente bloques pasados sin reserva asociada. Esto significa que, al navegar a una semana pasada, `availabilityBlocks` va a venir naturalmente vacío o parcial — **es el comportamiento esperado del dominio**, no un bug a corregir en esta historia. Las reservas (`bookings`) sí se preservan completas para semanas pasadas vía `findActiveBookingsInRange`. Confirmar con el equipo que este comportamiento (bloques pasados no visibles, reservas pasadas sí) es aceptable como respuesta a los criterios de aceptación 5 y 6.

3. **Definir qué cuenta como "reserva confirmada".** `BookingStatusGroups.ACTIVE` agrupa `PENDING, BOOKED, IN_PROGRESS`. El criterio de aceptación habla puntualmente de "reservas confirmadas", lo que sugiere específicamente `BookingStatus.BOOKED`, no todo el grupo `ACTIVE`. Para la grilla (`bookings`) puede tener sentido devolver todo `ACTIVE` (para que el tutor vea también lo `PENDING`/`IN_PROGRESS`), pero `summary.confirmedBookingsCount` y `summary.nextClass` deberían filtrar estrictamente por `BOOKED`. Confirmar este criterio con el equipo antes de implementar — está indicado así en este spec pero es una interpretación, no algo confirmado en la reunión.

4. **`getBlocksInRange` recibe `tutorUserId`, no `tutorProfileId`** — igual que `findActiveBookingsInRange`. No hace falta resolver `TutorProfile` para pasarlo a estos dos métodos; solo se necesita `tutorProfileId` si se toma la opción (a) del punto 1, ya que `IAvailabilityBlockRepository.findInRange` sí pide `tutorProfileId`.

## No hacer

- No crear, editar ni eliminar bloques de disponibilidad desde este módulo (vive en `tutors.availability`, US-09).
- No crear, cancelar ni reprogramar reservas desde este módulo (vive en `bookings`, US-10).
- No duplicar lógica de negocio ya existente en `AvailabilityBlockServiceImpl` ni en `BookingServiceImpl`; solo orquestar sus interfaces/repositorios de lectura.
- No modificar `AvailabilityBlockResponse` ni `BookingResponse` existentes.
- No crear nuevas entidades JPA ni tablas.
- No filtrar `bookings` fuera de la respuesta por estar en el pasado.
- No devolver 404/400 cuando una semana no tiene actividad: es un estado válido (200 con listas vacías y resumen en cero).

## Definition of Done

- [ ] `GET /api/v1/tutors/me/weekly-schedule?from=&to=` implementado y documentado con `@Operation`/`@ApiResponses` (Swagger)
- [ ] Reutiliza `ITutorProfileValidationService`, `IAvailabilityBlockService`, `IBookingRepository`, `BookingMapper`, `BookingStatusGroups`, `AppTimeZone` — sin duplicar lógica
- [ ] Resuelto y documentado el punto de `available` en `AvailabilityBlockResponse` (opción a o b, ver advertencia 1)
- [ ] Resuelto con el equipo qué estados cuentan como "confirmada" para el resumen (ver advertencia 3)
- [ ] Maneja semana sin actividad (200, listas vacías, resumen en cero, `nextClass: null`)
- [ ] Permite navegar a semanas pasadas sin filtrar `bookings`
- [ ] No expone ninguna operación de escritura bajo `tutors/me/weekly-schedule`
- [ ] Tests unitarios de `TutorWeeklyScheduleServiceImpl` (semana con actividad, semana vacía, cálculo de `nextClass`, cálculo de `freeBlocksCount`/`confirmedBookingsCount`)
- [ ] Test de integración del controller (200 con y sin actividad, 401/403 sin rol TUTOR)
- [ ] Code review aprobado
