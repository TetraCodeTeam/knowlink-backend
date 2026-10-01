package com.knowlink.api.tutors.repositories;

import com.knowlink.api.tutors.data.models.Rating;
import com.knowlink.api.tutors.data.projections.OverallRatingSummary;
import com.knowlink.api.tutors.data.projections.SubjectRatingSummary;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
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

    @Query("""
            SELECT AVG(r.score) AS averageScore,
                   COUNT(r) AS ratingCount
            FROM Rating r
            JOIN r.booking b
            WHERE r.ratedUser.userId = :tutorId
              AND r.visible = true
              AND b.bookingStatus = com.knowlink.api.bookings.data.enums.BookingStatus.COMPLETED
            """)
    OverallRatingSummary findOverallSummaryByTutorId(@Param("tutorId") UUID tutorId);

    @Query("""
            SELECT s.subjectId AS subjectId,
                   s.name AS subjectName,
                   AVG(r.score) AS averageScore,
                   COUNT(r) AS ratingCount
            FROM Rating r
            JOIN r.booking b
            JOIN b.tutorSubject ts
            JOIN ts.subject s
            WHERE r.ratedUser.userId = :tutorId
              AND r.visible = true
              AND b.bookingStatus = com.knowlink.api.bookings.data.enums.BookingStatus.COMPLETED
            GROUP BY s.subjectId, s.name
            ORDER BY s.name
            """)
    List<SubjectRatingSummary> findAverageGroupedBySubject(@Param("tutorId") UUID tutorId);

    @Query(value = """
            SELECT r
            FROM Rating r
            JOIN FETCH r.booking b
            JOIN FETCH b.tutorSubject ts
            JOIN FETCH ts.subject s
            WHERE r.ratedUser.userId = :tutorId
              AND r.visible = true
              AND b.bookingStatus = com.knowlink.api.bookings.data.enums.BookingStatus.COMPLETED
              AND r.comment IS NOT NULL
            ORDER BY r.ratingDate DESC, r.ratingId DESC
            """, countQuery = """
            SELECT COUNT(r)
            FROM Rating r
            JOIN r.booking b
            WHERE r.ratedUser.userId = :tutorId
              AND r.visible = true
              AND b.bookingStatus = com.knowlink.api.bookings.data.enums.BookingStatus.COMPLETED
              AND r.comment IS NOT NULL
            """)
    Page<Rating> findCompletedComments(@Param("tutorId") UUID tutorId, Pageable pageable);

    @Query(value = """
            SELECT r
            FROM Rating r
            JOIN FETCH r.booking b
            JOIN FETCH b.tutorSubject ts
            JOIN FETCH ts.subject s
            WHERE r.ratedUser.userId = :tutorId
              AND r.visible = true
              AND b.bookingStatus = com.knowlink.api.bookings.data.enums.BookingStatus.COMPLETED
              AND r.comment IS NOT NULL
              AND s.subjectId = :subjectId
            ORDER BY r.ratingDate DESC, r.ratingId DESC
            """, countQuery = """
            SELECT COUNT(r)
            FROM Rating r
            JOIN r.booking b
            JOIN b.tutorSubject ts
            JOIN ts.subject s
            WHERE r.ratedUser.userId = :tutorId
              AND r.visible = true
              AND b.bookingStatus = com.knowlink.api.bookings.data.enums.BookingStatus.COMPLETED
              AND r.comment IS NOT NULL
              AND s.subjectId = :subjectId
            """)
    Page<Rating> findCompletedCommentsBySubject(@Param("tutorId") UUID tutorId,
            @Param("subjectId") UUID subjectId, Pageable pageable);
}