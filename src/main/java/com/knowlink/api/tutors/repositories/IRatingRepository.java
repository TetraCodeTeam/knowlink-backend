package com.knowlink.api.tutors.repositories;

import com.knowlink.api.tutors.data.models.Rating;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface IRatingRepository extends JpaRepository<Rating, UUID> {

    @Query("""
            SELECT r FROM Rating r
            WHERE r.ratedUser.userId = :ratedUserId
            AND r.visible = true
            """)
    List<Rating> findVisibleByRatedUserId(@Param("ratedUserId") UUID ratedUserId);
}