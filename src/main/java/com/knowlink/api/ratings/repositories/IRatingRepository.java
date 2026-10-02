package com.knowlink.api.ratings.repositories;

import com.knowlink.api.ratings.data.models.Rating;
import com.knowlink.api.bookings.data.enums.BookingStatus;
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
            AND r.visible = true
            """)
    List<Rating> findVisibleTutorRatings(@Param("tutorUserId") UUID tutorUserId);

    @Query("""
            SELECT AVG(r.score) FROM Rating r
            WHERE r.ratedUser.userId = :tutorUserId
            AND r.booking.tutor.userId = :tutorUserId
            AND r.visible = true
            """)
    Double calculateVisibleTutorAverage(@Param("tutorUserId") UUID tutorUserId);

    @Query("""
            SELECT COUNT(r) FROM Rating r
            WHERE r.ratedUser.userId = :tutorUserId
            AND r.booking.tutor.userId = :tutorUserId
            AND r.visible = true
            """)
    long countVisibleTutorRatings(@Param("tutorUserId") UUID tutorUserId);

    List<Rating> findByBookingBookingId(UUID bookingId);

    @EntityGraph(attributePaths = { "booking.tutor", "ratedUser" })
    @Query("""
                SELECT r FROM Rating r JOIN r.booking b
        WHERE r.visible = false
                AND b.bookingStatus = :completedStatus
        AND """ + RatingQueryFragments.DEADLINE_ELAPSED)
    List<Rating> findExpiredHiddenRatings(
        @Param("completedStatus") BookingStatus completedStatus,
        @Param("visibleBefore") LocalDateTime visibleBefore,
        @Param("visibleBeforeDate") LocalDate visibleBeforeDate,
        @Param("visibleBeforeTime") LocalTime visibleBeforeTime);

    @Query("""
            SELECT CASE WHEN COUNT(b) > 0 THEN true ELSE false END
            FROM Booking b
            WHERE b.bookingId = :bookingId
            AND b.bookingStatus = :completedStatus
            AND """ + RatingQueryFragments.DEADLINE_ELAPSED)
    boolean isBlindReviewDeadlineElapsed(
            @Param("bookingId") UUID bookingId,
            @Param("completedStatus") BookingStatus completedStatus,
            @Param("visibleBefore") LocalDateTime visibleBefore,
            @Param("visibleBeforeDate") LocalDate visibleBeforeDate,
            @Param("visibleBeforeTime") LocalTime visibleBeforeTime);
}