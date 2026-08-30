package com.knowlink.api.bookings.repositories;

import com.knowlink.api.bookings.data.enums.BookingStatus;
import com.knowlink.api.bookings.data.models.Booking;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public interface IBookingRepository extends JpaRepository<Booking, UUID> {

        @Query("""
                        SELECT b FROM Booking b
                        WHERE b.timeSlot.timeSlotId = :timeSlotId
                        AND b.bookingStatus IN :statuses
                        """)
        List<Booking> findActiveByTimeSlotId(
                        @Param("timeSlotId") UUID timeSlotId,
                        @Param("statuses") List<BookingStatus> statuses);

        boolean existsByStudent_UserIdAndBookingStatus(UUID studentUserId, BookingStatus status);

        @Query("""
                        SELECT b FROM Booking b
                        WHERE b.student.userId = :studentUserId
                        AND b.tutorSubject.tutorSubjectId = :tutorSubjectId
                        AND b.sessionDate = :date
                        AND b.bookingStatus IN :statuses
                        """)
        List<Booking> findActiveByStudentSubjectAndDate(
                        @Param("studentUserId") UUID studentUserId,
                        @Param("tutorSubjectId") UUID tutorSubjectId,
                        @Param("date") LocalDate date,
                        @Param("statuses") List<BookingStatus> statuses);

        @Query("""
                        SELECT CASE WHEN COUNT(b) > 0 THEN true ELSE false END
                        FROM Booking b
                        WHERE b.tutor.userId = :tutorUserId
                        AND b.sessionDate BETWEEN :from AND :to
                        AND b.bookingStatus IN :activeStatuses
                        """)
        boolean existsActiveBookingInRange(
                        @Param("tutorUserId") UUID tutorUserId,
                        @Param("from") LocalDate from,
                        @Param("to") LocalDate to,
                        @Param("activeStatuses") List<BookingStatus> activeStatuses);

        @Query("""
                        SELECT b FROM Booking b
                        WHERE b.tutor.userId = :tutorUserId
                        AND b.sessionDate BETWEEN :from AND :to
                        AND b.bookingStatus IN :activeStatuses
                        """)
        List<Booking> findActiveBookingsInRange(
                        @Param("tutorUserId") UUID tutorUserId,
                        @Param("from") LocalDate from,
                        @Param("to") LocalDate to,
                        @Param("activeStatuses") List<BookingStatus> activeStatuses);
}
