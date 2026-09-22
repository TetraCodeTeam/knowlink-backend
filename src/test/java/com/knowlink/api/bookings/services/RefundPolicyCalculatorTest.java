package com.knowlink.api.bookings.services;

import com.knowlink.api.bookings.data.enums.CancellationRole;
import com.knowlink.api.bookings.data.enums.RefundDestination;
import com.knowlink.api.bookings.data.enums.RefundPolicy;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class RefundPolicyCalculatorTest {

    private RefundPolicyCalculator calculator;

    @BeforeEach
    void setUp() {
        calculator = new RefundPolicyCalculator();
    }

    @Test
    @DisplayName("Tutor cancela - reembolso total al alumno sin importar la antelación")
    void tutor_cancel_alwaysRefundsStudent() {
        LocalDateTime ahora = LocalDateTime.of(2026, 9, 3, 10, 0);
        LocalDateTime fechaSesion = LocalDateTime.of(2026, 9, 3, 11, 0); // 1h de anticipación
        BigDecimal monto = new BigDecimal("1500.00");

        RefundPolicyCalculator.ResultadoPolitica resultado = calculator.calcular(
                CancellationRole.TUTOR, ahora, fechaSesion, monto);

        assertThat(resultado.refundDestination()).isEqualTo(RefundDestination.STUDENT);
        assertThat(resultado.refundPolicy()).isEqualTo(RefundPolicy.REFUND_TOTAL_STUDENT);
        assertThat(resultado.amount()).isEqualByComparingTo(monto);
        assertThat(resultado.hoursInAdvance()).isEqualTo(1L);
    }

    @Test
    @DisplayName("Alumno cancela con más de 12h - reembolso total al alumno")
    void student_cancel_moreThan12Hours_refundsStudent() {
        LocalDateTime ahora = LocalDateTime.of(2026, 9, 3, 10, 0);
        LocalDateTime fechaSesion = LocalDateTime.of(2026, 9, 4, 1, 0); // 15h
        BigDecimal monto = new BigDecimal("1500.00");

        RefundPolicyCalculator.ResultadoPolitica resultado = calculator.calcular(
                CancellationRole.STUDENT, ahora, fechaSesion, monto);

        assertThat(resultado.refundDestination()).isEqualTo(RefundDestination.STUDENT);
        assertThat(resultado.refundPolicy()).isEqualTo(RefundPolicy.REFUND_TOTAL_STUDENT);
        assertThat(resultado.amount()).isEqualByComparingTo(monto);
        assertThat(resultado.hoursInAdvance()).isEqualTo(15L);
    }

    @Test
    @DisplayName("Alumno cancela con menos de 12h - dinero va al tutor")
    void student_cancel_lessThan12Hours_transferToTutor() {
        LocalDateTime ahora = LocalDateTime.of(2026, 9, 3, 10, 0);
        LocalDateTime fechaSesion = LocalDateTime.of(2026, 9, 3, 21, 0); // 11h
        BigDecimal monto = new BigDecimal("1500.00");

        RefundPolicyCalculator.ResultadoPolitica resultado = calculator.calcular(
                CancellationRole.STUDENT, ahora, fechaSesion, monto);

        assertThat(resultado.refundDestination()).isEqualTo(RefundDestination.TUTOR);
        assertThat(resultado.refundPolicy()).isEqualTo(RefundPolicy.TRANSFER_TOTAL_TUTOR);
        assertThat(resultado.amount()).isEqualByComparingTo(monto);
        assertThat(resultado.hoursInAdvance()).isEqualTo(11L);
    }

    @Test
    @DisplayName("Alumno cancela exactamente a las 12h - reembolso total alumno (umbral inclusivo)")
    void student_cancel_exactly12Hours_refundsStudent() {
        LocalDateTime ahora = LocalDateTime.of(2026, 9, 3, 10, 0);
        LocalDateTime fechaSesion = LocalDateTime.of(2026, 9, 3, 22, 0); // exactamente 12h
        BigDecimal monto = new BigDecimal("1500.00");

        RefundPolicyCalculator.ResultadoPolitica resultado = calculator.calcular(
                CancellationRole.STUDENT, ahora, fechaSesion, monto);

        assertThat(resultado.refundDestination()).isEqualTo(RefundDestination.STUDENT);
        assertThat(resultado.refundPolicy()).isEqualTo(RefundPolicy.REFUND_TOTAL_STUDENT);
        assertThat(resultado.hoursInAdvance()).isEqualTo(12L);
    }
}
