package com.knowlink.api.bookings.services;

import com.knowlink.api.bookings.data.enums.CancellationRole;
import com.knowlink.api.bookings.data.enums.RefundDestination;
import com.knowlink.api.bookings.data.enums.RefundPolicy;

import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;

@Component
public class RefundPolicyCalculator {

    private static final long UMBRAL_HORAS = 12;

    public ResultadoPolitica calcular(
            CancellationRole rol,
            LocalDateTime ahora,
            LocalDateTime fechaHoraSesion,
            BigDecimal montoPagado) {

        long horasAnticipacion = Duration.between(ahora, fechaHoraSesion).toHours();

        if (rol == CancellationRole.TUTOR) {
            return new ResultadoPolitica(
                    RefundDestination.STUDENT, montoPagado, horasAnticipacion,
                    RefundPolicy.REFUND_TOTAL_STUDENT);
        }

        if (horasAnticipacion >= UMBRAL_HORAS) {
            return new ResultadoPolitica(
                    RefundDestination.STUDENT, montoPagado, horasAnticipacion,
                    RefundPolicy.REFUND_TOTAL_STUDENT);
        } else {
            return new ResultadoPolitica(
                    RefundDestination.TUTOR, montoPagado, horasAnticipacion,
                    RefundPolicy.TRANSFER_TOTAL_TUTOR);
        }
    }

    public record ResultadoPolitica(
            RefundDestination refundDestination,
            BigDecimal amount,
            Long hoursInAdvance,
            RefundPolicy refundPolicy
    ) {}
}
