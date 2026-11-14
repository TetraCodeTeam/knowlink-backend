package com.knowlink.api.tutors.statistics.repositories;

import com.knowlink.api.bookings.data.enums.BookingStatus;
import com.knowlink.api.bookings.data.models.Booking;
import com.knowlink.api.claims.data.enums.ClaimStatus;
import com.knowlink.api.tutors.statistics.data.projections.ActiveClaimRow;
import com.knowlink.api.tutors.statistics.data.projections.TutorBookingRow;
import com.knowlink.api.tutors.statistics.data.projections.TutorRatingRow;
import com.knowlink.api.tutors.statistics.data.projections.TutorSlotRow;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

/**
 * Lecturas de solo consulta para el panel de estadísticas del tutor. Todas filtran por la fecha
 * de la sesión (no por la de cierre, calificación o cobro) para que un mismo período sea coherente
 * entre métricas.
 */
public interface ITutorStatisticsRepository extends Repository<Booking, UUID> {

    /**
     * Reservas del período. Además de las que tienen la fecha de la clase dentro del rango, incluye
     * las canceladas durante el período cuya clase era posterior a su fin (como el período no pasa de
     * hoy, es una clase futura): si no, una cancelación de hoy para mañana no aparecía hasta que
     * llegaba ese día. Cada reserva se devuelve una sola vez.
     */
    default List<TutorBookingRow> findBookingRows(UUID tutorUserId, LocalDate from, LocalDate to) {
        return findBookingRowsOfPeriod(
                tutorUserId, from, to, from.atStartOfDay(), to.plusDays(1).atStartOfDay());
    }

    @Query("""
            SELECT new com.knowlink.api.tutors.statistics.data.projections.TutorBookingRow(
                    b.bookingId, b.sessionDate, b.startTime, b.endTime, b.bookingStatus,
                    b.student.userId, b.tutorSubject.tutorSubjectId,
                    bc.cancelledBy, bc.refundPolicy,
                    ft.fundsStatus, ft.recipient, ft.originalAmount,
                    ft.systemRetentionPercentage, ft.transferredAmount)
            FROM Booking b
            LEFT JOIN BookingCancellation bc ON bc.booking.bookingId = b.bookingId
            LEFT JOIN FundsTransfer ft ON ft.booking.bookingId = b.bookingId
            WHERE b.tutor.userId = :tutorUserId
              AND (b.sessionDate BETWEEN :from AND :to
                   OR (b.sessionDate > :to
                       AND b.bookingStatus = com.knowlink.api.bookings.data.enums.BookingStatus.CANCELLED
                       AND bc.createdAt >= :cancelledFrom
                       AND bc.createdAt < :cancelledBefore))
            """)
    List<TutorBookingRow> findBookingRowsOfPeriod(
            @Param("tutorUserId") UUID tutorUserId,
            @Param("from") LocalDate from,
            @Param("to") LocalDate to,
            @Param("cancelledFrom") LocalDateTime cancelledFrom,
            @Param("cancelledBefore") LocalDateTime cancelledBefore);

    /**
     * Reclamos todavía abiertos o en revisión de las reservas indicadas, del más antiguo al más
     * nuevo: el primero de cada reserva es el que abrió la disputa.
     */
    @Query("""
            SELECT new com.knowlink.api.tutors.statistics.data.projections.ActiveClaimRow(
                    c.booking.bookingId, c.claimId)
            FROM SessionClaim c
            WHERE c.booking.bookingId IN :bookingIds
              AND c.status IN :statuses
            ORDER BY c.createdAt ASC, c.claimId ASC
            """)
    List<ActiveClaimRow> findActiveClaimRows(
            @Param("bookingIds") Collection<UUID> bookingIds,
            @Param("statuses") Collection<ClaimStatus> statuses);

    @Query("""
            SELECT COUNT(b) FROM Booking b
            WHERE b.tutor.userId = :tutorUserId
              AND b.sessionDate BETWEEN :from AND :to
              AND b.bookingStatus = :status
            """)
    long countByStatus(
            @Param("tutorUserId") UUID tutorUserId,
            @Param("from") LocalDate from,
            @Param("to") LocalDate to,
            @Param("status") BookingStatus status);

    /** Solo calificaciones visibles (US-18) de sesiones completadas que recibió el tutor. */
    @Query("""
            SELECT new com.knowlink.api.tutors.statistics.data.projections.TutorRatingRow(
                    r.score, b.tutorSubject.tutorSubjectId)
            FROM Rating r
            JOIN r.booking b
            WHERE r.ratedUser.userId = :tutorUserId
              AND b.tutor.userId = :tutorUserId
              AND r.visible = true
              AND b.bookingStatus = com.knowlink.api.bookings.data.enums.BookingStatus.COMPLETED
              AND b.sessionDate BETWEEN :from AND :to
            """)
    List<TutorRatingRow> findVisibleRatingRows(
            @Param("tutorUserId") UUID tutorUserId,
            @Param("from") LocalDate from,
            @Param("to") LocalDate to);

    @Query("""
            SELECT new com.knowlink.api.tutors.statistics.data.projections.TutorSlotRow(
                    ts.date, ts.startTime, ts.endTime)
            FROM TimeSlot ts
            WHERE ts.tutorProfileId = :tutorProfileId
              AND ts.date BETWEEN :from AND :to
            """)
    List<TutorSlotRow> findLiveSlotRows(
            @Param("tutorProfileId") UUID tutorProfileId,
            @Param("from") LocalDate from,
            @Param("to") LocalDate to);

    @Query("""
            SELECT new com.knowlink.api.tutors.statistics.data.projections.TutorSlotRow(
                    h.date, h.startTime, h.endTime)
            FROM OfferedSlotHistory h
            WHERE h.tutorProfileId = :tutorProfileId
              AND h.date BETWEEN :from AND :to
            """)
    List<TutorSlotRow> findArchivedSlotRows(
            @Param("tutorProfileId") UUID tutorProfileId,
            @Param("from") LocalDate from,
            @Param("to") LocalDate to);
}
