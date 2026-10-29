package com.knowlink.api.community.data.models;

import com.knowlink.api.shared.utils.AppTimeZone;
import com.knowlink.api.users.data.models.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Fila del ranking mensual de tutores. Se calcula una sola vez por período (el primer día de cada mes)
 * y no se modifica entre actualizaciones.
 */
@Entity
@Table(name = "community_tutor_ranking",
        uniqueConstraints = @UniqueConstraint(name = "uk_community_ranking_period_tutor",
                columnNames = {"period_start", "tutor_id"}),
        indexes = @Index(name = "idx_community_ranking_period_position", columnList = "period_start, rank_position"))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TutorRankingEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "ranking_entry_id", updatable = false, nullable = false)
    private UUID rankingEntryId;

    /** Primer día del mes al que pertenece el ranking. */
    @Column(name = "period_start", nullable = false)
    private LocalDate periodStart;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "tutor_id", nullable = false)
    private User tutor;

    @Column(name = "rank_position", nullable = false)
    private int position;

    @Column(name = "score", nullable = false)
    private double score;

    @Column(name = "best_answers", nullable = false)
    private int bestAnswers;

    @Column(name = "helpful_marks", nullable = false)
    private int helpfulMarks;

    @Column(name = "average_rating")
    private Double averageRating;

    @Column(name = "reviews_count", nullable = false)
    private int reviewsCount;

    @Column(name = "completed_classes", nullable = false)
    private int completedClasses;

    @Column(name = "generated_at", nullable = false, updatable = false)
    private LocalDateTime generatedAt;

    @PrePersist
    protected void onCreate() {
        if (generatedAt == null) {
            generatedAt = LocalDateTime.now(AppTimeZone.ZONE);
        }
    }
}
