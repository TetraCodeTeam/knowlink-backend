package com.knowlink.api.community.repositories;

import com.knowlink.api.community.data.models.TutorRankingEntry;
import com.knowlink.api.community.data.projections.TutorCountMetric;
import com.knowlink.api.community.data.projections.TutorRatingMetric;
import com.knowlink.api.community.data.projections.TutorSubjectName;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ITutorRankingRepository extends JpaRepository<TutorRankingEntry, UUID> {

    boolean existsByPeriodStart(LocalDate periodStart);

    long countByPeriodStart(LocalDate periodStart);

    @EntityGraph(attributePaths = {"tutor"})
    List<TutorRankingEntry> findByPeriodStartAndPositionLessThanEqualOrderByPositionAsc(
            LocalDate periodStart, int maxPosition);

    Optional<TutorRankingEntry> findByPeriodStartAndTutor_UserId(LocalDate periodStart, UUID tutorId);

    // ── Métricas para calcular el ranking ────────────────────────────────────

    @Query("""
            SELECT tp.user.userId FROM TutorProfile tp
            WHERE tp.user.accountStatus = com.knowlink.api.users.data.enums.AccountStatus.ACTIVE
              AND tp.user.deletedAt IS NULL
            """)
    List<UUID> findEligibleTutorIds();

    /** Respuestas de tutores elegidas como "mejor aporte" en publicaciones resueltas dentro de la ventana. */
    @Query("""
            SELECT r.author.userId AS tutorId, COUNT(p) AS total
            FROM CommunityPost p, CommunityReply r
            WHERE r.replyId = p.featuredReplyId
              AND p.deletedAt IS NULL
              AND r.deletedAt IS NULL
              AND r.authorRole = com.knowlink.api.security.enums.Role.TUTOR
              AND p.resolvedAt >= :from AND p.resolvedAt < :to
            GROUP BY r.author.userId
            """)
    List<TutorCountMetric> countBestAnswers(@Param("from") LocalDateTime from, @Param("to") LocalDateTime to);

    /** Marcas "Esto me sirvió" recibidas por respuestas de tutores dentro de la ventana. */
    @Query("""
            SELECT r.author.userId AS tutorId, COUNT(h) AS total
            FROM CommunityReplyHelpful h JOIN h.reply r JOIN r.post p
            WHERE r.deletedAt IS NULL
              AND p.deletedAt IS NULL
              AND r.authorRole = com.knowlink.api.security.enums.Role.TUTOR
              AND h.createdAt >= :from AND h.createdAt < :to
            GROUP BY r.author.userId
            """)
    List<TutorCountMetric> countHelpfulMarks(@Param("from") LocalDateTime from, @Param("to") LocalDateTime to);

    @Query("""
            SELECT b.tutor.userId AS tutorId, AVG(r.score) AS averageScore, COUNT(r) AS ratingCount
            FROM Rating r JOIN r.booking b
            WHERE r.ratedUser.userId = b.tutor.userId
              AND r.visible = true
              AND b.bookingStatus = com.knowlink.api.bookings.data.enums.BookingStatus.COMPLETED
            GROUP BY b.tutor.userId
            """)
    List<TutorRatingMetric> findRatingMetrics();

    @Query("""
            SELECT b.tutor.userId AS tutorId, COUNT(b) AS total
            FROM Booking b
            WHERE b.bookingStatus = com.knowlink.api.bookings.data.enums.BookingStatus.COMPLETED
            GROUP BY b.tutor.userId
            """)
    List<TutorCountMetric> countCompletedClasses();

    @Query("""
            SELECT tp.user.userId AS tutorId, s.name AS subjectName
            FROM TutorSubject ts JOIN ts.tutorProfile tp JOIN ts.subject s
            WHERE tp.user.userId IN :tutorIds
              AND ts.tutorSubjectStatus = com.knowlink.api.tutors.data.enums.TutorSubjectStatus.ACTIVE
            ORDER BY s.name ASC
            """)
    List<TutorSubjectName> findActiveSubjectNames(@Param("tutorIds") Collection<UUID> tutorIds);
}
