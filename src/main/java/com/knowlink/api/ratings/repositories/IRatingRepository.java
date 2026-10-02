package com.knowlink.api.ratings.repositories;

import com.knowlink.api.ratings.data.models.Rating;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.UUID;

public interface IRatingRepository extends JpaRepository<Rating, UUID> {

    @Query("""
            SELECT r FROM Rating r
        WHERE r.ratedUser.userId = :tutorUserId
        AND r.booking.tutor.userId = :tutorUserId
        AND (r.visible = true
        OR r.booking.confirmedAt <= :visibleBefore
        OR (r.booking.confirmedAt IS NULL AND (
            r.booking.sessionDate < :visibleBeforeDate
            OR (r.booking.sessionDate = :visibleBeforeDate
            AND r.booking.endTime <= :visibleBeforeTime)
        )))
            """)
    List<Rating> findVisibleTutorRatings(
        @Param("tutorUserId") UUID tutorUserId,
        @Param("visibleBefore") LocalDateTime visibleBefore,
        @Param("visibleBeforeDate") LocalDate visibleBeforeDate,
        @Param("visibleBeforeTime") LocalTime visibleBeforeTime);

    @Query("""
        SELECT AVG(r.score) FROM Rating r
        WHERE r.ratedUser.userId = :tutorUserId
        AND r.booking.tutor.userId = :tutorUserId
        AND (r.visible = true
        OR r.booking.confirmedAt <= :visibleBefore
        OR (r.booking.confirmedAt IS NULL AND (
            r.booking.sessionDate < :visibleBeforeDate
            OR (r.booking.sessionDate = :visibleBeforeDate
            AND r.booking.endTime <= :visibleBeforeTime)
        )))
        """)
    Double calculateVisibleTutorAverage(
        @Param("tutorUserId") UUID tutorUserId,
        @Param("visibleBefore") LocalDateTime visibleBefore,
        @Param("visibleBeforeDate") LocalDate visibleBeforeDate,
        @Param("visibleBeforeTime") LocalTime visibleBeforeTime);

    List<Rating> findByBookingBookingId(UUID bookingId);

    @EntityGraph(attributePaths = { "booking.tutor", "ratedUser" })
    @Query("""
        SELECT r FROM Rating r
        WHERE r.visible = false
        AND r.booking.bookingStatus = com.knowlink.api.bookings.data.enums.BookingStatus.COMPLETED
        AND (r.booking.confirmedAt <= :visibleBefore
        OR (r.booking.confirmedAt IS NULL AND (
            r.booking.sessionDate < :visibleBeforeDate
            OR (r.booking.sessionDate = :visibleBeforeDate
            AND r.booking.endTime <= :visibleBeforeTime)
        )))
        """)
    List<Rating> findExpiredHiddenRatings(
        @Param("visibleBefore") LocalDateTime visibleBefore,
        @Param("visibleBeforeDate") LocalDate visibleBeforeDate,
        @Param("visibleBeforeTime") LocalTime visibleBeforeTime);
}