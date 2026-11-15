# Spec backend: el tutor no paga comisión (opción B)

Repo: `TetraCodeTeam/knowlink-backend` · rama base: `dev` · leer primero `docs/base-standards.md`.

## 1. Contexto y decisión

Observación de QA (ítem 8): una clase de $10.000 le mostraba al tutor $9.991 de "Ingresos netos generados".

Causa raíz:
- `BookingServiceImpl#calculateAmount` guarda en `Booking.amount` lo que paga el alumno: precio de la clase × (1 + `BookingConstants.SERVICE_FEE_RATE`) = $10.300.
- `FundsLedgerService#releaseToTutor` toma ese `booking.getAmount()` ($10.300) como `originalAmount` y le descuenta otro 3% (`knowlink.funds.retention-percentage`) = $309. El tutor recibe $9.991.
- Son dos cargos distintos y el segundo se calcula sobre una base que ya incluye el primero.

**Decisión de negocio (opción B):** la tarifa de servicio la paga solo el alumno. El tutor no paga comisión y recibe el precio completo de la clase.

| Concepto | Monto |
|---|---|
| Alumno paga (`Booking.amount`) | $10.300 |
| Tutor recibe | $10.000 |
| Plataforma retiene | $300 |

## 2. Regla de cálculo

```
montoTutor = Booking.amount / (1 + SERVICE_FEE_RATE)   // escala 2, HALF_UP
```

Esto recupera exactamente el precio de la clase. `Booking.amount` se redondea a 2 decimales al reservar; el error máximo es 0,005 / 1,03 < 0,005, así que al redividir y redondear a 2 decimales se obtiene el precio original siempre que ese precio tenga como máximo 2 decimales. No hace falta agregar columnas ni migrar (el esquema lo maneja Hibernate con `DDL_AUTO` y la base de producción todavía no existe).

La regla vive en UN solo lugar (punto 3.1) y la reutilizan el ledger y las estadísticas.

## 3. Cambios

### 3.1 Helper nuevo
Crear `src/main/java/com/knowlink/api/payments/domain/TutorPayout.java`:

```java
package com.knowlink.api.payments.domain;

import com.knowlink.api.bookings.utils.BookingConstants;
import java.math.BigDecimal;
import java.math.RoundingMode;

/** Monto que recibe el tutor a partir de lo que pagó el alumno (la tarifa de servicio la paga solo el alumno). */
public final class TutorPayout {

    private TutorPayout() {}

    public static BigDecimal fromStudentPayment(BigDecimal amountPaidByStudent) {
        if (amountPaidByStudent == null) {
            return BigDecimal.ZERO;
        }
        return amountPaidByStudent.divide(
                BigDecimal.ONE.add(BookingConstants.SERVICE_FEE_RATE), 2, RoundingMode.HALF_UP);
    }
}
```
Verificar que `BookingConstants` sea `public`; si no lo es, hacerlo público.

### 3.2 `payments/services/FundsLedgerService.java`
- Eliminar el campo `systemRetentionPercentage` y su `@Value("${knowlink.funds.retention-percentage:3.00}")`.
- `releaseToTutor`: `transferredAmount = TutorPayout.fromStudentPayment(originalAmount)`; `transfer.setSystemRetentionPercentage(BigDecimal.ZERO)`; el evento `FundsTransferProcessedEvent` recibe `BigDecimal.ZERO` como retención. Quitar `retentionAmount` y el log de retención.
- `createHeldTransfer`: `.systemRetentionPercentage(BigDecimal.ZERO)`.
- `calculateTutorTransferredAmount(originalAmount)`: pasa a delegar en `TutorPayout.fromStudentPayment(originalAmount)`. Lo usa `FundsResolutionService#resolveByAdminDecision` (reclamo a favor del tutor), que queda corregido sin tocarlo.
- Quitar imports que queden sin uso (`Value`, `RoundingMode` si corresponde).

### 3.3 `payments/data/models/FundsTransfer.java`
- La columna `system_retention_percentage` SE MANTIENE (historial y contrato de `FundsTransferResponseDTO`), pero ahora siempre vale 0. Cambiar el default de la línea ~37 de `new BigDecimal("3.00")` a `BigDecimal.ZERO`.
- No tocar `FundsTransferResponseDTO`: el campo `systemRetentionPercentage` sigue existiendo y devuelve 0.00.

### 3.4 `resources/application.yaml`
- Eliminar `knowlink.funds.retention-percentage` (línea ~80). Si `funds:` queda vacío, eliminarlo también. Buscar con `grep -rn "retention-percentage" .` que no quede ninguna referencia (incluidos `application-*.yaml` y `.env.example`).

### 3.5 `tutors/statistics/services/implementations/TutorStatisticsCalculator.java`
- Eliminar el constructor con `@Value("${knowlink.funds.retention-percentage:3.00}")` y el campo `commissionPercentage` (queda constructor sin argumentos). Corregir los lugares donde se instancia (tests y configuración).
- Eliminar `netOfCommission(TutorBookingRow)` y reemplazar sus usos (en `funds()` para `pendingRelease` y `withheldByClaim`, y en `netIncome()`) por `TutorPayout.fromStudentPayment(row.originalAmount())`.
- `released` sigue usando `row.transferredAmount()` (ya viene bien calculado desde el ledger).
- Quitar `commissionPercentage` de los constructores de `StatisticsSummaryResponse` y `FundsStatusResponse`.

### 3.6 DTOs y proyección
- `StatisticsSummaryResponse`: eliminar el componente `commissionPercentage`.
- `FundsStatusResponse`: eliminar el componente `commissionPercentage`.
- `TutorBookingRow`: eliminar el componente `retentionPercentage` y, en `ITutorStatisticsRepository` (línea ~44), quitar `ft.systemRetentionPercentage` del constructor de la proyección JPQL. Ajustar `TutorStatisticsRepositoryTest` (línea ~130).
- Actualizar el Javadoc de `StatisticsSummaryResponse` si menciona comisión.

### 3.7 Notificación al tutor
`notifications/listeners/FundsNotificationListener.java`, caso `RELEASED_SESSION_COMPLETED`:
- Quitar el sufijo `". (" + retentionLabel(event) + "% de retención aplicada)"`. El mensaje queda: `"Recibiste $X por la sesión de <materia> con <alumno>."`
- Eliminar el método `retentionLabel` si queda sin uso.

### 3.8 Documentación y seeds
- `docs/US-15-liberacion-fondos-backend.md` (línea ~157 y la sección del cálculo): reemplazar "3% fijo, parametrizable" por la regla de la sección 2.
- `docs/admin/admin-frontend-contract.md`, `docs/us-35-centro-notificaciones-backend.spec.md`, `docs/admin/us-50-reclamos-sesiones-backend.spec.md`: `grep -in "retenci\|comisi"` y corregir lo que describa la retención del tutor.
- `docs/seeds/*.sql`: en cada `INSERT INTO funds_transfer` con estado `RELEASED_TO_TUTOR`, poner `system_retention_percentage = 0.00` y `transferred_amount = original_amount / 1.03` (ej. 1030.00 → 1000.00). Si no, las estadísticas con datos demo mostrarían los montos viejos porque `released` usa `transferred_amount`.

## 4. Tests a ajustar o crear

Usar números que cierren exacto: `2060.00 → 2000.00` y `1030.00 → 1000.00`.

- `FundsLedgerServiceTest`: cambiar `amount("2100.00")` por `amount("2060.00")`; esperar `transferredAmount` = `2000.00` y `retentionPercentage` = `0`. Eliminar `injectRetention` y sus llamadas.
- Eliminar `FundsLedgerServiceInjectionTest` (verificaba el campo que se borra).
- Nuevo `TutorPayoutTest` (puro, sin Spring): `10300.00 → 10000.00`, `2060.00 → 2000.00`, `0 → 0.00`, `null → 0`, un caso con centavos (`3433.33 → 3333.33`).
- `TutorStatisticsCalculatorTest#netIncomeAddsReleasedAndPendingButNotWithheldFunds`: filas `funded(COMPLETED, RELEASED_TO_TUTOR, "1030.00", "1000.00")`, `funded(COMPLETED, HELD, "2060.00", "0.00")`, `funded(COMPLETED, SUSPENDED_BY_CLAIM, "4120.00", "0.00")`. Esperar `netIncome = 3000.00`, `released = 1000.00`, `pendingRelease = 2000.00`, `withheldByClaim = 4000.00`. Quitar la aserción de `commissionPercentage` (línea ~381) y los `retentionPercentage` del helper `funded`.
- `FundsResolutionServiceAdminDecisionTest`: el caso "a favor del tutor" debe esperar `original / 1.03`. Ajustar el builder (línea ~75) a retención 0.
- `FundsNotificationListenerTest#releaseCompleted_notifiesTutorOnlyWithRetention`: renombrar (ej. `releaseCompleted_notifiesTutorWithAmountReceived`) y quitar la aserción de "% de retención".
- Correr `./mvnw test` (o el comando del repo) y dejar la suite en verde.

## 5. Criterios de aceptación

1. Reserva de una clase de $10.000: `Booking.amount = 10300.00`.
2. Al liberar (`COMPLETED`): `funds_transfer.transferred_amount = 10000.00`, `system_retention_percentage = 0.00`.
3. `GET` de estadísticas del tutor: `summary.netIncome = 10000.00`; la respuesta ya no contiene `commissionPercentage` ni en `summary` ni en `funds`.
4. Reclamo resuelto a favor del tutor: recibe `10000.00`.
5. Notificación al tutor: "Recibiste $10.000 por la sesión de …" sin mención a retención.
6. `grep -rn "retention-percentage\|commissionPercentage" src docs` no devuelve nada.

## 6. Fuera de alcance (no tocar)

- Reembolsos al alumno: siguen devolviendo `originalAmount` completo ($10.300). Si el negocio quiere devolver la tarifa de servicio o no, es otra decisión.
- Cálculo del monto al reservar (`calculateAmount`) y `BookingConfigResponse`.
- Migración de datos: no hay base de producción todavía.

## 7. Convenciones (de `CLAUDE.md`)
Inyección por constructor, DTOs response como `record`, `@Slf4j` para logs, sin `System.out`. No agregar endpoints nuevos.
