package com.knowlink.api.tutors.repositories;

import com.knowlink.api.tutors.data.enums.BookingStatus;
import com.knowlink.api.tutors.data.models.Booking;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface IBookingRepository extends JpaRepository<Booking, UUID> {

    @Query("""
            SELECT CASE WHEN COUNT(b) > 0 THEN true ELSE false END
            FROM Booking b
            WHERE b.tutor.userId = :tutorUserId
            AND b.student.userId = :studentUserId
            AND b.bookingStatus IN :statuses
            """)
    boolean existsActiveBooking(
            @Param("tutorUserId") UUID tutorUserId,
            @Param("studentUserId") UUID studentUserId,
            @Param("statuses") List<BookingStatus> statuses);
}