package com.knowlink.api.ratings.repositories;

import com.knowlink.api.ratings.data.models.Rating;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.time.LocalDateTime;
import java.util.UUID;

public interface IRatingRepository extends JpaRepository<Rating, UUID> {

    @Query("""
            SELECT r FROM Rating r
            WHERE r.ratedUser.userId = :ratedUserId
            AND (r.visible = true OR r.booking.confirmedAt <= :visibleBefore)
            """)
        List<Rating> findVisibleByRatedUserId(
            @Param("ratedUserId") UUID ratedUserId,
            @Param("visibleBefore") LocalDateTime visibleBefore);

        List<Rating> findByBookingBookingId(UUID bookingId);

        @Query("""
                SELECT r FROM Rating r
                WHERE r.visible = false
                AND r.booking.confirmedAt <= :visibleBefore
                """)
        List<Rating> findExpiredHiddenRatings(@Param("visibleBefore") LocalDateTime visibleBefore);
}