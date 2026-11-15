package com.knowlink.api.payments.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class TutorPayoutTest {

    @Test
    @DisplayName("El tutor recibe el precio de la clase: lo que pago el alumno dividido por (1 + tarifa)")
    void recoversTheFullClassPriceFromTheStudentPayment() {
        assertThat(TutorPayout.fromStudentPayment(new BigDecimal("10300.00")))
                .isEqualByComparingTo("10000.00");
        assertThat(TutorPayout.fromStudentPayment(new BigDecimal("2060.00")))
                .isEqualByComparingTo("2000.00");
    }

    @Test
    @DisplayName("Montos con centavos se redondean a 2 decimales con HALF_UP")
    void roundsToTwoDecimalsHalfUp() {
        assertThat(TutorPayout.fromStudentPayment(new BigDecimal("3433.33")))
                .isEqualByComparingTo("3333.33");
    }

    @Test
    @DisplayName("Un pago de cero devuelve cero con dos decimales")
    void zeroPaymentPaysNothing() {
        assertThat(TutorPayout.fromStudentPayment(BigDecimal.ZERO)).isEqualByComparingTo("0.00");
    }

    @Test
    @DisplayName("Un pago null se trata como cero")
    void nullPaymentPaysNothing() {
        assertThat(TutorPayout.fromStudentPayment(null)).isEqualByComparingTo("0");
    }
}