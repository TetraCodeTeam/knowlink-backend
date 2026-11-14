package com.knowlink.api.tutors.statistics.controllers.responses;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * {@code withheldClaimIds}: reclamo abierto detrás de cada sesión con fondos retenidos (a lo sumo uno
 * por sesión), para que el panel pueda abrir el detalle en Soporte.
 */
public record FundsStatusResponse(
        FundsAmountResponse released,
        FundsAmountResponse pendingRelease,
        FundsAmountResponse withheldByClaim,
        List<UUID> withheldClaimIds,
        BigDecimal commissionPercentage) {
}
