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