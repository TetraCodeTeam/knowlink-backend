# US-15 — Liberar fondos al tutor según resultado de sesión

## Metadata

| Campo | Valor |
|---|---|
| ID Historia | US-15 |
| Épica | Sí — cubre confirmación/ausencia y disputa entre partes |
| Story Points | 13 |
| Prioridad | ALTA |
| Módulo | pagos |
| Tags | #RN-14 #RN-16 |
| Historias relacionadas | US-41 (confirmación por token), US-44 (pago/retención inicial), US-46 (reclamos tutor — suspensión de fondos), US-47 (disputas alumno — suspensión de fondos), US-21 / US-22 (resolución admin de disputas) |
| Repo / branch | knowlink-backend (equivalente backend de `julian-salvucci`) |

**Convención de idioma:** identificadores de código (clases, enums, métodos, campos, paquetes) en **inglés**. Textos orientados al usuario (mensajes de notificación, conceptos de transferencia, labels de estado mostrados en la UI, mensajes de excepción) en **español**.

**Nota de alcance del proyecto:** no se integra Mercado Pago ni ninguna pasarela de pago real. La retención y liberación de fondos es simulada: el sistema mantiene un **ledger interno** (saldo retenido / saldo liberado) exclusivamente con fines académicos, sin movimiento de dinero real ni webhooks externos.

---

## Contexto de dominio

Esta historia es el motor de resolución económica de una reserva ya finalizada. Depende de que existan:
- Un monto retenido en el momento del pago (US-44), asociado a la reserva.
- Un mecanismo de confirmación de presencia por token dentro de un rango horario válido (US-41).
- Un flujo de confirmación de asistencia por ambas partes cuando no hay token o como paso posterior.
- Un estado de reclamo/disputa activable por tutor (US-46) o alumno (US-47).
- Una resolución administrativa que puede desbloquear la suspensión (US-21/US-22).

US-15 **no crea** la retención ni el flujo de reclamos: **consume** sus resultados y ejecuta la distribución final.

---

## Criterios de aceptación (Gherkin)

```gherkin
Feature: Liberación o devolución de fondos según resultado de sesión

  Background:
    Given una reserva "R" con un monto retenido "M" y retención del sistema del 3%
    And la sesión asociada a "R" ya finalizó (horario de fin transcurrido)

  Scenario: Tutor presente, alumno presente
    Given el tutor confirmó presencia
    And el alumno confirmó asistencia
    And no existe un reclamo activo sobre "R"
    When se ejecuta la resolución de fondos
    Then se transfiere el 97% de "M" al tutor (3% de retención del sistema)
    And la reserva "R" cambia a estado "Completada"
    And el estado de fondos de "R" pasa a "Liberado al tutor"
    And no puede volver a modificarse el estado de "R"

  Scenario: Tutor presente, alumno ausente
    Given el tutor confirmó presencia
    And el alumno confirmó ausencia
    And no existe un reclamo activo sobre "R"
    When se ejecuta la resolución de fondos
    Then se transfiere el 97% de "M" al tutor, dado que cumplió con su parte
    And la reserva "R" cambia a estado "Completada"
    And el estado de fondos de "R" pasa a "Liberado al tutor"

  Scenario: Tutor ausente, alumno presente
    Given el tutor confirmó ausencia
    And el alumno confirmó asistencia
    And no existe un reclamo activo sobre "R"
    When se ejecuta la resolución de fondos
    Then se devuelve el 100% de "M" al alumno
    And la reserva "R" cambia a estado "No cumplida por tutor"
    And el estado de fondos de "R" pasa a "Devuelto al alumno"

  Scenario: Tutor ausente, alumno ausente
    Given el tutor confirmó ausencia
    And el alumno confirmó ausencia
    And no existe un reclamo activo sobre "R"
    When se ejecuta la resolución de fondos
    Then se devuelve el 100% de "M" al alumno
    And la reserva "R" cambia a estado "Sesión no realizada"
    And el estado de fondos de "R" pasa a "Devuelto al alumno"

  Scenario: Token de presencia no ingresado en el rango válido
    Given el token de confirmación de presencia no fue ingresado dentro del rango horario válido
    When finaliza la ventana del token
    Then la reserva "R" avanza igualmente al flujo de confirmación de asistencia de ambas partes
    And la resolución de fondos queda pendiente de ese resultado, sin ejecutarse por defecto en ningún sentido

  Scenario: Notificación de finalización con ventana de reclamo
    Given la sesión asociada a "R" finalizó
    When el sistema dispara la notificación de finalización
    Then tanto el tutor como el alumno reciben una notificación con la opción de generar un reclamo
    And mientras exista un reclamo activo sobre "R", la resolución de fondos queda en estado "Suspendido por reclamo"
    And la transferencia no se ejecuta hasta que un administrador resuelva la disputa (US-21/US-22)

  Scenario: Notificación de transferencia ejecutada
    Given se ejecutó una transferencia (liberación o devolución) sobre "R"
    When la transferencia se confirma
    Then ambas partes reciben una notificación indicando el monto y el concepto de la operación

  Scenario: Inmutabilidad posterior a la transferencia
    Given la transferencia de fondos de "R" ya fue procesada
    When se intenta modificar el estado de "R" desde cualquier endpoint
    Then el sistema rechaza la operación indicando que la reserva es de solo lectura a partir de ese punto
```

---

## Stack confirmado

- Java 21 + Spring Boot 3.5
- MySQL 8 (persistencia de reserva, ledger de fondos y auditoría de transferencias)
- Sin integración de pasarela de pago externa (Mercado Pago descartado): retención/liberación resuelta íntegramente dentro del backend, mediante una entidad de ledger propia
- Notificaciones: reutilizar el mecanismo de notificaciones ya existente en el proyecto (mismo canal que otras historias de notificación; no crear uno nuevo)
- Programación de la resolución: `@Scheduled` o listener de evento de dominio disparado al completarse la confirmación de asistencia de ambas partes (a definir según lo que ya exista para el cierre de sesión; no introducir un nuevo sistema de colas)

---

## Modelo de datos propuesto

### Enum `BookingStatus` (extender el existente)
Agregar, si no existen ya, los valores terminales. El enum es código (inglés); el mapeo a texto visible para el usuario va en una capa de presentación/label, en español.

```java
public enum BookingStatus {
    // ...valores existentes...
    COMPLETED,               // label: "Completada"
    NOT_FULFILLED_BY_TUTOR,  // label: "No cumplida por tutor"
    SESSION_NOT_HELD         // label: "Sesión no realizada"
}
```

### Nuevo enum `FundsStatus`
```java
public enum FundsStatus {
    HELD,                  // label: "Retenido"
    SUSPENDED_BY_CLAIM,    // label: "Suspendido por reclamo"
    RELEASED_TO_TUTOR,     // label: "Liberado al tutor"
    REFUNDED_TO_STUDENT    // label: "Devuelto al alumno"
}
```

### Nuevo enum `FundsRecipient`
```java
public enum FundsRecipient {
    TUTOR,
    STUDENT
}
```

### Nueva entidad `FundsTransfer`
Registro de auditoría, uno por reserva, inmutable una vez creado con estado final.

| Campo (código) | Tipo | Notas |
|---|---|---|
| `id` | Long | PK |
| `bookingId` | Long | FK a Booking, único |
| `originalAmount` | BigDecimal | monto retenido en US-44 |
| `systemRetentionPercentage` | BigDecimal | 3% fijo, parametrizable en config |
| `transferredAmount` | BigDecimal | calculado |
| `recipient` | `FundsRecipient` | |
| `concept` | String | texto **en español** para la notificación, ej. `"Liberación por sesión completada"` |
| `fundsStatus` | `FundsStatus` | |
| `processedAt` | Instant | null hasta ejecutarse |
| `blockingClaimId` | Long (nullable) | referencia al reclamo activo si está suspendida |

No modelar movimiento de dinero real (sin cuenta bancaria, sin CBU, sin webhook): es un registro contable interno.

---

## Ubicación de clases / paquetes

```
com.knowlink.backend.payments
 ├── domain
 │    ├── FundsStatus.java
 │    ├── FundsRecipient.java
 │    └── FundsTransfer.java
 ├── repository
 │    └── FundsTransferRepository.java
 ├── service
 │    ├── FundsResolutionService.java   // orquesta la lógica de negocio de esta historia
 │    └── FundsLedgerService.java       // aplica el cálculo de porcentajes y persiste la transferencia
 ├── listener
 │    └── AttendanceConfirmationListener.java  // reacciona cuando ambas partes confirmaron (o venció el token)
 ├── exception
 │    └── ImmutableBookingException.java
 └── dto
      └── FundsTransferResponseDTO.java
```

`FundsResolutionService` depende de:
- `BookingRepository` (módulo reservas, ya existente)
- `PresenceConfirmationRepository` / lo que exista de US-41
- `ClaimRepository` (módulo reclamos, US-46/US-47) — solo lectura, para chequear reclamo activo
- `NotificationService` (módulo notificaciones, ya existente)

**No** crear un módulo nuevo de "pagos externos"; todo vive dentro de `payments` como lógica de ledger interno.

*Nota:* si en el codebase actual estas clases dependientes (`Booking`, `ClaimRepository`, etc.) ya existen con nombres en español, ajustar los nombres en esta lista a los reales — la convención de inglés aplica hacia adelante, no implica renombrar entidades ya construidas fuera de esta historia salvo que el equipo decida encarar esa migración aparte.

---

## Lógica de negocio (pseudocódigo de `FundsResolutionService`)

```java
public void resolve(Long bookingId) {
    Booking booking = bookingRepository.findByIdOrThrow(bookingId);

    if (booking.hasProcessedTransfer()) {
        throw new ImmutableBookingException(bookingId); // Criterio 8
        // mensaje de excepción en español: "La reserva ya tiene una transferencia procesada y no puede modificarse."
    }

    if (claimRepository.existsActiveClaim(bookingId)) {
        fundsLedgerService.markSuspendedByClaim(bookingId);
        return; // Criterio 6 — no se ejecuta transferencia
    }

    AttendanceConfirmation confirmation = confirmationRepository.findByBookingOrThrow(bookingId);
    boolean tutorPresent = confirmation.isTutorConfirmedPresence();
    boolean studentPresent = confirmation.isStudentConfirmedAttendance();

    if (tutorPresent) {
        // Criterios 1 y 2: el tutor cobra si él cumplió, sin importar al alumno
        fundsLedgerService.releaseToTutor(booking, BookingStatus.COMPLETED);
    } else {
        // Criterios 3 y 4: el tutor no cumplió, se devuelve siempre al alumno
        BookingStatus finalStatus = studentPresent
            ? BookingStatus.NOT_FULFILLED_BY_TUTOR
            : BookingStatus.SESSION_NOT_HELD;
        fundsLedgerService.refundToStudent(booking, finalStatus);
    }

    notificationService.notifyTransfer(booking); // Criterio 7 — texto de notificación en español
}
```

Puntos de negocio a remarcar en el código con comentario (no obvios a simple lectura del ticket):
- El resultado depende **solo** de si el tutor confirmó presencia. La asistencia del alumno solo decide qué estado textual final toma la reserva, nunca quién cobra.
- Si no hay confirmación de token en rango válido (Criterio 5), esto **no** dispara ninguna resolución de fondos por sí mismo: solo redirige al flujo de confirmación de asistencia manual. `FundsResolutionService.resolve()` se sigue disparando cuando ese flujo manual se completa, no antes.
- El chequeo de reclamo activo debe hacerse **en el momento de resolver**, no en el momento de la notificación de finalización, porque un reclamo puede crearse después de la notificación pero antes de que corra el job/listener.

---

## Endpoints propuestos

| Método | Ruta | Descripción | Acceso |
|---|---|---|---|
| GET | `/api/bookings/{id}/funds` | Consulta el estado de fondos y la transferencia (si existe) de una reserva | Tutor/Alumno de la reserva, Admin |
| POST | `/api/admin/bookings/{id}/funds/retry` | Reintenta la resolución tras levantarse una suspensión por reclamo (llamado desde el flujo de US-21/US-22, no expuesto a usuarios finales) | Admin |

No exponer un endpoint público para forzar la liberación manual desde tutor o alumno: la resolución es siempre automática o disparada por el admin tras resolver disputa.

Los mensajes de error de estos endpoints (403, 409, 404) deben responder en español, por ejemplo: `"No existe una transferencia de fondos asociada a esta reserva."`

---

## No hacer

- No integrar Mercado Pago, Stripe ni ninguna pasarela real de pagos.
- No modelar CBU, tarjetas, ni datos bancarios de ningún tipo.
- No permitir revertir ni editar un `FundsTransfer` una vez que su `fundsStatus` es `RELEASED_TO_TUTOR` o `REFUNDED_TO_STUDENT` (Criterio 8): el repositorio no debe exponer un método `update` para ese registro, solo `save` en creación.
- No ejecutar la transferencia si hay un reclamo activo, aunque la confirmación de asistencia ya esté completa (Criterio 6 tiene prioridad sobre la resolución normal).
- No decidir el destinatario de los fondos en base a la asistencia del alumno: el alumno nunca hace que el tutor pierda el cobro si el tutor confirmó presencia.
- No crear un nuevo sistema de notificaciones para esta historia: reusar el servicio existente.
- No asumir que la ausencia de token implica ausencia de alguna de las partes: son dos mecanismos independientes (Criterio 5).
- No mezclar idiomas dentro de un mismo identificador de código (por ejemplo, evitar `estadoFondos` o `refundToAlumno`); si una clase dependiente externa ya tiene nombre en español, no forzar una traducción parcial que rompa la referencia.

---

## Definition of Done

- [ ] Enum `FundsStatus`, `FundsRecipient` y estados terminales de `BookingStatus` agregados, con nombres de código en inglés
- [ ] Entidad `FundsTransfer` con migración de base de datos (Flyway/Liquibase, según lo ya usado en el proyecto)
- [ ] `FundsResolutionService` implementado cubriendo los 4 escenarios de combinación tutor/alumno
- [ ] Chequeo de reclamo activo bloqueando la transferencia, con estado `SUSPENDED_BY_CLAIM`
- [ ] Endpoint de reintento post-resolución de disputa integrado con el flujo de US-21/US-22
- [ ] Notificación a ambas partes al finalizar sesión (con opción de reclamo) y al ejecutarse la transferencia (con monto y concepto), con todo el texto visible al usuario en español
- [ ] Capa de labels/mensajes en español para los enums de código (`BookingStatus`, `FundsStatus`) usada en respuestas de API y notificaciones
- [ ] Inmutabilidad verificada: intento de modificar una reserva con transferencia procesada devuelve error explícito en español
- [ ] Tests unitarios de `FundsResolutionService` para los 4 escenarios + suspensión por reclamo + reintento post-disputa
- [ ] Test de integración cubriendo el flujo completo: confirmación de asistencia → resolución de fondos → notificación
- [ ] Documentación de la entidad `FundsTransfer` y sus estados en el spec de datos del módulo payments
