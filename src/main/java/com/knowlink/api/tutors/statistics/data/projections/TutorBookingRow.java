package com.knowlink.api.tutors.statistics.data.projections;

import com.knowlink.api.bookings.data.enums.BookingStatus;
import com.knowlink.api.bookings.data.enums.CancellationRole;
import com.knowlink.api.bookings.data.enums.RefundPolicy;
import com.knowlink.api.payments.domain.FundsRecipient;
import com.knowlink.api.payments.domain.FundsStatus;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;

/** Reserva del tutor con el resultado de fondos y la cancelación (si existen) ya unidos. */
public record TutorBookingRow(
        UUID bookingId,
        LocalDate sessionDate,
        LocalTime startTime,
        LocalTime endTime,
        BookingStatus status,
        UUID studentId,
        UUID tutorSubjectId,
        CancellationRole cancelledBy,
        RefundPolicy refundPolicy,
        FundsStatus fundsStatus,
        FundsRecipient fundsRecipient,
        BigDecimal originalAmount,
        BigDecimal retentionPercentage,
        BigDecimal transferredAmount) {
}
