# US-11 — Cancelar una Reserva (Backend Spec)

## 0. Metadata

| Campo | Valor |
|---|---|
| ID Historia | US-11 |
| Sprint | 4 |
| Story Points | 5 |
| Prioridad | ALTA |
| Módulo | reservas |
| Relacionadas | US-10 (reserva), US-44 (pago), US-15 (liberación de fondos), US-04 (restricción de desactivación/eliminación de cuenta por reservas activas) |
| Tags | #RN-12 #RN-13 |

**Historia:** Como alumno o tutor, quiero poder cancelar una reserva confirmada, para gestionar imprevistos respetando las políticas de antelación definidas.

---

## 1. Stack

- Java 21
- Spring Boot 3.5
- MySQL 8
- Spring Data JPA / Hibernate
- Spring Security (autorización por rol + ownership)
- Bean Validation (jakarta.validation)
- Spring Events (`ApplicationEventPublisher`) para desacoplar notificación y liberación de slot del flujo transaccional principal

---

## 2. Alcance

Incluye:
- Endpoint de cancelación de una reserva `CONFIRMADA`.
- Cálculo de política de reembolso según rol de quien cancela y antelación respecto al horario de la sesión.
- Liberación del slot horario asociado.
- Disparo de notificación a ambas partes.

No incluye (fuera de alcance de esta historia):
- Ejecución real de la transferencia/reembolso de dinero (eso es responsabilidad de US-44/US-15; esta historia solo **registra la intención** y delega la ejecución).
- Lógica de reprogramación de reserva (no confundir "cancelar" con "reprogramar").
- Cancelaciones de reservas ya finalizadas o ya canceladas (deben rechazarse, ver sección 6).

---

## 3. Modelo de datos

Se asume que la entidad `Reserva` ya existe (US-10). Cambios necesarios:

### 3.1 Entidad `Reserva` (ajustes)

Agregar (si no existen):

```java
@Enumerated(EnumType.STRING)
private EstadoReserva estado; // CONFIRMADA, CANCELADA, FINALIZADA, etc.

private LocalDateTime fechaHoraSesion; // horario de la sesión reservada (debería ya existir)

private LocalDateTime canceladaEn;

@Enumerated(EnumType.STRING)
private RolCancelacion canceladaPor; // ALUMNO, TUTOR (null si no está cancelada)

@Enumerated(EnumType.STRING)
private DestinoReembolso destinoReembolso; // ALUMNO, TUTOR (null si no está cancelada)
```

### 3.2 Nuevos enums

```java
public enum RolCancelacion { ALUMNO, TUTOR }

public enum DestinoReembolso { ALUMNO, TUTOR }
```

### 3.3 Entidad `CancelacionReserva` (nueva, tabla de auditoría)

Registrar cada cancelación de forma explícita, independiente del estado mutable de `Reserva`, para trazabilidad y para que US-15 (liberación de fondos) tenga un evento consumible.

```java
@Entity
@Table(name = "cancelacion_reserva")
public class CancelacionReserva {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "reserva_id", nullable = false)
    private Reserva reserva;

    @Enumerated(EnumType.STRING)
    private RolCancelacion canceladaPor;

    @Enumerated(EnumType.STRING)
    private DestinoReembolso destinoReembolso;

    private BigDecimal monto;

    private Long horasAnticipacion; // horas entre cancelación y fechaHoraSesion, redondeado hacia abajo

    private LocalDateTime creadaEn;
}
```

> Nota de diseño: `horasAnticipacion` se persiste (no se recalcula después) para que la auditoría sea inmutable aunque cambien reglas de negocio en el futuro.

---

## 4. Diseño de API

### 4.1 Endpoint

```
POST /api/reservas/{reservaId}/cancelaciones
```

Se usa un sub-recurso `cancelaciones` en lugar de `PATCH /reservas/{id}` porque la cancelación es un evento de negocio con su propio registro de auditoría (ver 3.3), no una simple actualización de campo.

### 4.2 Request

No requiere body (el actor y su rol se derivan del `Authentication` del contexto de seguridad, no del payload, para evitar spoofing de rol).

```json
{}
```

Si el equipo prefiere permitir un motivo opcional de cancelación (no pedido explícitamente por los AC, pero común en UX):

```json
{
  "motivo": "string, opcional, máx 280 caracteres"
}
```

> Confirmar con el equipo si `motivo` entra en el alcance de esta historia o queda para una futura. Por defecto: **no incluir** (ver sección 8, "No hacer").

### 4.3 Response 200 OK

```json
{
  "reservaId": "uuid",
  "estado": "CANCELADA",
  "canceladaPor": "ALUMNO",
  "destinoReembolso": "ALUMNO",
  "monto": 1500.00,
  "horasAnticipacion": 18,
  "canceladaEn": "2026-09-03T14:32:00Z",
  "politicaAplicada": "REEMBOLSO_TOTAL_ALUMNO"
}
```

`politicaAplicada` es un enum descriptivo para que el frontend arme el mensaje sin tener que reimplementar la regla de negocio (ver 6.4).

### 4.4 Errores

| Código | Caso |
|---|---|
| 401 | No autenticado |
| 403 | El usuario autenticado no es el alumno ni el tutor de la reserva |
| 404 | Reserva inexistente |
| 409 | Reserva no está en estado `CONFIRMADA` (ya cancelada, ya finalizada, etc.) |
| 422 | La sesión ya ocurrió (`fechaHoraSesion` en el pasado) — no tiene sentido "cancelar" algo que ya pasó |

---

## 5. Endpoint de previsualización (soporta AC #1)

El AC #1 exige mostrar al usuario la política aplicable **antes** de confirmar. Esto implica un endpoint de solo lectura para que el frontend arme el diálogo de confirmación sin ejecutar la cancelación:

```
GET /api/reservas/{reservaId}/cancelaciones/preview
```

Response 200:

```json
{
  "reservaId": "uuid",
  "rolSolicitante": "ALUMNO",
  "horasAnticipacion": 18,
  "destinoReembolso": "ALUMNO",
  "monto": 1500.00,
  "politicaAplicada": "REEMBOLSO_TOTAL_ALUMNO"
}
```

Este endpoint **no** debe tener efectos secundarios (no persiste nada). Reutiliza el mismo servicio de cálculo de política que el endpoint de confirmación (ver 6.5), para evitar que preview y ejecución diverjan.

Si el AC #1 se resuelve enteramente en el frontend con datos que ya tiene (ej. si el frontend ya conoce `fechaHoraSesion` y el rol), este endpoint es opcional. Se documenta igual porque es la forma más segura de garantizar que el mensaje mostrado coincide exactamente con lo que el backend va a ejecutar.

---

## 6. Reglas de negocio (RN-12, RN-13)

### 6.1 Gherkin — AC #1 (confirmación previa)

```gherkin
Feature: Cancelación de reserva con confirmación previa

  Scenario: El usuario cierra el diálogo de confirmación sin confirmar
    Given una reserva CONFIRMADA del usuario autenticado
    When el usuario abre el diálogo de cancelación y lo cierra sin confirmar
    Then no se envía ninguna solicitud de cancelación al backend
    And la reserva permanece en estado CONFIRMADA
```

> Este escenario es puramente frontend (no se dispara ningún request). El backend solo garantiza que, si nunca llega el POST, el estado no cambia — no requiere lógica adicional más allá del comportamiento por defecto.

### 6.2 Gherkin — AC #2 y #3 (cancelación por alumno)

```gherkin
Feature: Cancelación de reserva por el alumno

  Scenario: Alumno cancela con más de 12 horas de anticipación
    Given una reserva CONFIRMADA con fechaHoraSesion dentro de más de 12 horas
    And el usuario autenticado es el alumno de la reserva
    When el alumno solicita la cancelación
    Then la reserva pasa a estado CANCELADA
    And se registra destinoReembolso = ALUMNO
    And el monto reembolsado es el 100% del monto pagado

  Scenario: Alumno cancela con menos de 12 horas de anticipación
    Given una reserva CONFIRMADA con fechaHoraSesion dentro de menos de 12 horas
    And el usuario autenticado es el alumno de la reserva
    When el alumno solicita la cancelación
    Then la reserva pasa a estado CANCELADA
    And se registra destinoReembolso = TUTOR
    And el monto transferido es el 100% del monto pagado
```

### 6.3 Gherkin — AC #4 (cancelación por tutor)

```gherkin
Feature: Cancelación de reserva por el tutor

  Scenario: Tutor cancela una reserva, sin importar la antelación
    Given una reserva CONFIRMADA
    And el usuario autenticado es el tutor de la reserva
    When el tutor solicita la cancelación
    Then la reserva pasa a estado CANCELADA
    And se registra destinoReembolso = ALUMNO
    And el monto reembolsado es el 100% del monto pagado
    And esto ocurre independientemente de horasAnticipacion
```

### 6.4 Tabla de decisión (para implementar como método puro, testeable)

| Rol que cancela | Antelación | destinoReembolso | % |
|---|---|---|---|
| ALUMNO | ≥ 12h | ALUMNO | 100% |
| ALUMNO | < 12h | TUTOR | 100% (del monto, pero va al tutor) |
| TUTOR | cualquiera | ALUMNO | 100% |

**Regla de umbral:** "más de 12 horas" (AC #2) vs "menos de 12 horas" (AC #3) — el límite exacto de 12:00:00 no está definido por la historia. Se asume `>= 12h` cae en la política de reembolso total al alumno (interpretación conservadora a favor del alumno). **Confirmar con Product Owner** — es una ambigüedad real, no una suposición trivial.

### 6.5 Servicio de dominio (puro, sin dependencias de infraestructura)

Aislar el cálculo en una clase sin dependencias de Spring/JPA para que sea trivialmente testeable por unit test:

```java
public class PoliticaCancelacionCalculator {

    private static final long UMBRAL_HORAS = 12;

    public ResultadoPolitica calcular(
            RolCancelacion rol,
            LocalDateTime ahora,
            LocalDateTime fechaHoraSesion,
            BigDecimal montoPagado) {

        long horasAnticipacion = Duration.between(ahora, fechaHoraSesion).toHours();

        if (rol == RolCancelacion.TUTOR) {
            return new ResultadoPolitica(
                DestinoReembolso.ALUMNO, montoPagado, horasAnticipacion,
                PoliticaAplicada.REEMBOLSO_TOTAL_ALUMNO);
        }

        // rol == ALUMNO
        if (horasAnticipacion >= UMBRAL_HORAS) {
            return new ResultadoPolitica(
                DestinoReembolso.ALUMNO, montoPagado, horasAnticipacion,
                PoliticaAplicada.REEMBOLSO_TOTAL_ALUMNO);
        } else {
            return new ResultadoPolitica(
                DestinoReembolso.TUTOR, montoPagado, horasAnticipacion,
                PoliticaAplicada.TRANSFERENCIA_TOTAL_TUTOR);
        }
    }
}
```

`ahora` se inyecta como parámetro (no `LocalDateTime.now()` interno) para que el cálculo sea determinístico en tests.

### 6.6 Gherkin — AC #6 (liberación del slot)

```gherkin
Feature: Liberación de slot tras cancelación

  Scenario: El slot vuelve a estar disponible
    Given una reserva CONFIRMADA que ocupa un slot horario
    When la reserva es cancelada (por alumno o tutor)
    Then el slot asociado queda disponible para nuevas reservas
    And otro alumno puede reservar ese mismo bloque sin conflicto
```

Implementación: si el slot es una entidad separada (`SlotDisponibilidad` o similar de US-10), su estado debe volver a `DISPONIBLE` en la misma transacción que la cancelación, no en un proceso asíncrono — para que el AC #6/caso de prueba #5 sea consistente inmediatamente después del commit.

---

## 7. Ubicación de clases (paquetes)

> Ajustado a la convención real del repo `knowlink-backend` (ver `auth/` y `users/` en el código actual): carpetas en **plural** (`controllers`, `services`, `repositories`) y una carpeta **`data/`** única que agrupa entidades, DTOs y enums — no se separan en `model/`/`dto/` como en un layout genérico. Al momento de escribir este spec el módulo `reserva` todavía **no existe** en el repo; esta historia lo crea desde cero siguiendo el mismo patrón que `users/`.

```
com.knowlink.api.reserva
 ├── controllers/
 │    └── CancelacionReservaController.java
 ├── services/
 │    ├── CancelacionReservaService.java          (orquesta: valida, calcula, persiste, publica evento)
 │    └── PoliticaCancelacionCalculator.java       (lógica pura, sin dependencias de Spring)
 ├── data/
 │    ├── Reserva.java                             (entidad ya existente de US-10, se extiende)
 │    ├── CancelacionReserva.java                  (entidad nueva)
 │    ├── EstadoReserva.java                       (enum)
 │    ├── RolCancelacion.java                      (enum)
 │    ├── DestinoReembolso.java                    (enum)
 │    ├── CancelacionPreviewResponse.java          (dto)
 │    ├── CancelacionResponse.java                 (dto)
 │    └── CancelarReservaRequest.java              (dto, si se decide incluir "motivo")
 ├── repositories/
 │    └── CancelacionReservaRepository.java
 └── events/
      └── ReservaCanceladaEvent.java               (payload: reservaId, destinoReembolso, monto, slotId)
```

Consumidores del evento `ReservaCanceladaEvent` (en otros módulos, fuera del alcance de esta historia pero deben existir/coordinarse — mismo patrón `controllers/services/data/repositories` cuando se creen):

```
com.knowlink.api.pago            → @EventListener en services/ que dispara la transferencia/reembolso real (US-44/US-15)
com.knowlink.api.notificacion    → @EventListener en services/ que envía la notificación a ambas partes (AC #5)
com.knowlink.api.disponibilidad  → @EventListener (o lógica síncrona en el mismo service, ver 6.6) que libera el slot
```

Transversales ya existentes en el repo, reutilizar sin duplicar:

- `com.knowlink.api.security` — extracción del usuario autenticado (JWT) para resolver ownership (alumno/tutor) sin confiar en el body.
- `com.knowlink.api.exceptions` — manejo global de errores; los 401/403/404/409/422 de la sección 4.4 deben mapearse a las excepciones/handlers ya definidos ahí, no crear un manejador nuevo por módulo.
- `com.knowlink.api.config` — si el evento requiere ejecución async, revisar la config de `Async` ya presente antes de agregar un `@EnableAsync` propio.

---

## 8. Integraciones y advertencias de dependencia

- **US-44 (pago) y US-15 (liberación de fondos):** esta historia **no ejecuta** la transferencia de dinero. Publica `ReservaCanceladaEvent` con `destinoReembolso` y `monto`; la ejecución real es responsabilidad de esas historias. **No implementar lógica de pasarela de pago acá.** Si US-44/US-15 todavía no están implementadas, el listener puede no existir aún — el evento debe publicarse igual (para no bloquear esta historia) y quedar documentado como pendiente de consumo.
- **US-04 (restricción de desactivación/eliminación de cuenta):** esa historia depende de que exista una forma de consultar "reservas activas" de un usuario. Esta historia no necesita hacer nada especial al respecto, pero **no cambiar el significado de `EstadoReserva.CANCELADA`** de forma que rompa esa consulta (una reserva cancelada no debe contar como activa).
- **Autorización:** validar ownership contra el `usuarioId` del `Authentication`, no confiar en ningún id de alumno/tutor que venga en el body o en query params.
- **Concurrencia:** dos requests simultáneos de cancelación sobre la misma reserva deben resultar en un solo cambio efectivo; usar el estado `CONFIRMADA` como precondición de la transacción (`UPDATE ... WHERE estado = 'CONFIRMADA'`) o un lock optimista (`@Version`) sobre `Reserva` para que el segundo request reciba 409.

---

## 9. Qué NO hacer

- No ejecutar transferencias de dinero reales en este servicio (delegar vía evento a US-44/US-15).
- No permitir cancelar reservas en estado distinto de `CONFIRMADA`.
- No derivar el rol del actor (alumno/tutor) desde el body del request; siempre desde el contexto de seguridad.
- No hardcodear el umbral de 12 horas en múltiples lugares — vive únicamente en `PoliticaCancelacionCalculator`.
- No liberar el slot en un proceso asíncrono separado de la transacción de cancelación (rompe el caso de prueba #5).
- No agregar el campo `motivo` al modelo salvo que se confirme con el equipo que está en alcance.
- No reutilizar el endpoint de cancelación como endpoint de "reprogramación" ni mezclar ambas lógicas.

---

## 10. Definition of Done

- [ ] Endpoint `POST /api/reservas/{id}/cancelaciones` implementado con las 3 validaciones de estado/ownership/timing.
- [ ] Endpoint `GET /api/reservas/{id}/cancelaciones/preview` implementado y reutiliza `PoliticaCancelacionCalculator`.
- [ ] `PoliticaCancelacionCalculator` cubierto por unit tests para los 3 casos de la tabla de decisión (6.4), incluyendo el caso límite exacto de 12h.
- [ ] Entidad `CancelacionReserva` persistida correctamente, con `horasAnticipacion` congelado al momento de la cancelación.
- [ ] Slot liberado en la misma transacción (test de integración: otro alumno puede reservar el mismo bloque inmediatamente después).
- [ ] Evento `ReservaCanceladaEvent` publicado con el payload correcto en los 3 escenarios.
- [ ] Manejo de errores 401/403/404/409/422 cubierto por tests.
- [ ] Control de concurrencia (lock optimista o condición en el UPDATE) verificado con test de doble cancelación simultánea.
- [ ] Umbral de 12 horas confirmado con Product Owner (documentado en este spec, sección 6.4).
- [ ] Código ubicado según el paquete definido en la sección 7.

---

## 11. Casos de prueba (trazabilidad con la historia)

| # | Caso | Resultado esperado | Cubierto por |
|---|---|---|---|
| 1 | Alumno abre y cierra el diálogo sin confirmar | Reserva permanece CONFIRMADA | Comportamiento frontend; sin request al backend |
| 2 | Alumno cancela con >12h de anticipación | Reembolso 100% al alumno | `PoliticaCancelacionCalculatorTest`, `CancelacionReservaServiceIT` |
| 3 | Alumno cancela con <12h de anticipación | Dinero al tutor | `PoliticaCancelacionCalculatorTest`, `CancelacionReservaServiceIT` |
| 4 | Tutor cancela (cualquier antelación) | Reembolso 100% al alumno | `PoliticaCancelacionCalculatorTest`, `CancelacionReservaServiceIT` |
| 5 | Otro alumno reserva el slot liberado | Reserva se concreta sin inconvenientes | `SlotDisponibilidadIT` (test de integración cross-módulo) |
