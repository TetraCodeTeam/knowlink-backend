package com.knowlink.api.community.services.implementations;

import com.knowlink.api.community.data.models.TutorRankingEntry;
import com.knowlink.api.community.data.projections.TutorCountMetric;
import com.knowlink.api.community.data.projections.TutorRatingMetric;
import com.knowlink.api.community.repositories.ITutorRankingRepository;
import com.knowlink.api.users.data.models.User;
import com.knowlink.api.users.repositories.IUserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

/**
 * Fija la fórmula del puntaje del ranking mensual:
 * {@code 10 × mejores aportes + 2 × marcas "Esto me sirvió" + 5 × calificación promedio}.
 */
@ExtendWith(MockitoExtension.class)
class TutorRankingSnapshotServiceImplTest {

    private static final LocalDate PERIOD = LocalDate.of(2026, 11, 1);

    @Mock
    private ITutorRankingRepository rankingRepository;
    @Mock
    private IUserRepository userRepository;
    @InjectMocks
    private TutorRankingSnapshotServiceImpl service;

    @Captor
    private ArgumentCaptor<List<TutorRankingEntry>> entriesCaptor;

    private final UUID julian = UUID.randomUUID();
    private final UUID carolina = UUID.randomUUID();
    private final UUID ricardo = UUID.randomUUID();
    private final UUID tutorWithoutActivity = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        lenient().when(rankingRepository.existsByPeriodStart(PERIOD)).thenReturn(false);
        lenient().when(userRepository.getReferenceById(any(UUID.class)))
                .thenAnswer(invocation -> User.builder().userId(invocation.getArgument(0)).build());
        lenient().when(rankingRepository.countBestAnswers(any(), any())).thenReturn(List.of());
        lenient().when(rankingRepository.countHelpfulMarks(any(), any())).thenReturn(List.of());
        lenient().when(rankingRepository.countCompletedClasses()).thenReturn(List.of());
        lenient().when(rankingRepository.findRatingMetrics()).thenReturn(List.of());
    }

    @Test
    @DisplayName("El puntaje suma 10 por mejor aporte, 2 por marca y 5 por la calificación promedio")
    void scoreCombinesBestAnswersHelpfulMarksAndAverageRating() {
        when(rankingRepository.findEligibleTutorIds()).thenReturn(List.of(julian));
        when(rankingRepository.countBestAnswers(any(), any())).thenReturn(List.of(count(julian, 3)));
        when(rankingRepository.countHelpfulMarks(any(), any())).thenReturn(List.of(count(julian, 15)));
        when(rankingRepository.findRatingMetrics()).thenReturn(List.of(rating(julian, 4.5, 9)));

        service.generateIfAbsent(PERIOD);

        TutorRankingEntry entry = savedEntries().get(0);
        // 3 × 10 + 15 × 2 + 4,5 × 5 = 30 + 30 + 22,5
        assertThat(entry.getScore()).isEqualTo(82.5);
        assertThat(entry.getBestAnswers()).isEqualTo(3);
        assertThat(entry.getHelpfulMarks()).isEqualTo(15);
        assertThat(entry.getAverageRating()).isEqualTo(4.5);
        assertThat(entry.getReviewsCount()).isEqualTo(9);
        assertThat(entry.getPeriodStart()).isEqualTo(PERIOD);
        assertThat(entry.getPosition()).isEqualTo(1);
    }

    @Test
    @DisplayName("Un tutor sin calificaciones puntúa solo por su actividad en la comunidad")
    void tutorWithoutRatingsScoresOnlyFromCommunityActivity() {
        when(rankingRepository.findEligibleTutorIds()).thenReturn(List.of(carolina));
        when(rankingRepository.countBestAnswers(any(), any())).thenReturn(List.of(count(carolina, 1)));
        when(rankingRepository.countHelpfulMarks(any(), any())).thenReturn(List.of(count(carolina, 7)));

        service.generateIfAbsent(PERIOD);

        TutorRankingEntry entry = savedEntries().get(0);
        // 1 × 10 + 7 × 2, sin aporte de calificación
        assertThat(entry.getScore()).isEqualTo(24.0);
        assertThat(entry.getAverageRating()).isNull();
        assertThat(entry.getReviewsCount()).isZero();
    }

    @Test
    @DisplayName("Un tutor con solo calificaciones también participa: 5 × el promedio")
    void tutorWithOnlyRatingsStillParticipates() {
        when(rankingRepository.findEligibleTutorIds()).thenReturn(List.of(ricardo));
        when(rankingRepository.findRatingMetrics()).thenReturn(List.of(rating(ricardo, 5.0, 1)));

        service.generateIfAbsent(PERIOD);

        TutorRankingEntry entry = savedEntries().get(0);
        assertThat(entry.getScore()).isEqualTo(25.0);
        assertThat(entry.getBestAnswers()).isZero();
        assertThat(entry.getHelpfulMarks()).isZero();
    }

    @Test
    @DisplayName("Los tutores que no suman puntaje no entran al ranking")
    void tutorsWithZeroScoreAreExcluded() {
        when(rankingRepository.findEligibleTutorIds()).thenReturn(List.of(julian, tutorWithoutActivity));
        when(rankingRepository.countBestAnswers(any(), any())).thenReturn(List.of(count(julian, 1)));

        service.generateIfAbsent(PERIOD);

        assertThat(savedEntries()).extracting(entry -> entry.getTutor().getUserId()).containsExactly(julian);
    }

    @Test
    @DisplayName("Se ordena por puntaje descendente y las posiciones son consecutivas")
    void entriesAreOrderedByScoreDescendingWithConsecutivePositions() {
        when(rankingRepository.findEligibleTutorIds()).thenReturn(List.of(carolina, julian, ricardo));
        when(rankingRepository.countBestAnswers(any(), any()))
                .thenReturn(List.of(count(carolina, 1), count(julian, 3), count(ricardo, 2)));

        service.generateIfAbsent(PERIOD);

        List<TutorRankingEntry> entries = savedEntries();
        assertThat(entries).extracting(entry -> entry.getTutor().getUserId())
                .containsExactly(julian, ricardo, carolina);
        assertThat(entries).extracting(TutorRankingEntry::getPosition).containsExactly(1, 2, 3);
        assertThat(entries).extracting(TutorRankingEntry::getScore).containsExactly(30.0, 20.0, 10.0);
    }

    @Test
    @DisplayName("Con el mismo puntaje gana quien concretó más clases")
    void tieOnScoreIsBrokenByCompletedClasses() {
        when(rankingRepository.findEligibleTutorIds()).thenReturn(List.of(carolina, julian));
        when(rankingRepository.countBestAnswers(any(), any()))
                .thenReturn(List.of(count(carolina, 2), count(julian, 2)));
        when(rankingRepository.countCompletedClasses())
                .thenReturn(List.of(count(carolina, 1), count(julian, 10)));

        service.generateIfAbsent(PERIOD);

        assertThat(savedEntries()).extracting(entry -> entry.getTutor().getUserId())
                .containsExactly(julian, carolina);
    }

    @Test
    @DisplayName("Las respuestas y las marcas se cuentan en la ventana del mes anterior")
    void communityActivityIsCountedOverThePreviousMonth() {
        when(rankingRepository.findEligibleTutorIds()).thenReturn(List.of(julian));

        service.generateIfAbsent(PERIOD);

        LocalDateTime from = LocalDateTime.of(2026, 10, 1, 0, 0);
        LocalDateTime to = LocalDateTime.of(2026, 11, 1, 0, 0);
        verify(rankingRepository).countBestAnswers(from, to);
        verify(rankingRepository).countHelpfulMarks(from, to);
    }

    @Test
    @DisplayName("Un período ya generado no se recalcula")
    void existingPeriodIsNeverRegenerated() {
        when(rankingRepository.existsByPeriodStart(PERIOD)).thenReturn(true);

        service.generateIfAbsent(PERIOD);

        verify(rankingRepository).existsByPeriodStart(PERIOD);
        verify(rankingRepository, never()).saveAll(any());
        verifyNoMoreInteractions(rankingRepository);
    }

    @Test
    @DisplayName("Sin tutores elegibles no se guarda nada")
    void noEligibleTutorsSavesNothing() {
        when(rankingRepository.findEligibleTutorIds()).thenReturn(List.of());

        service.generateIfAbsent(PERIOD);

        verify(rankingRepository, never()).saveAll(any());
    }

    private List<TutorRankingEntry> savedEntries() {
        verify(rankingRepository).saveAll(entriesCaptor.capture());
        return entriesCaptor.getValue();
    }

    private static TutorCountMetric count(UUID tutorId, long total) {
        return new TutorCountMetric() {
            @Override
            public UUID getTutorId() {
                return tutorId;
            }

            @Override
            public long getTotal() {
                return total;
            }
        };
    }

    private static TutorRatingMetric rating(UUID tutorId, Double average, long ratingCount) {
        return new TutorRatingMetric() {
            @Override
            public UUID getTutorId() {
                return tutorId;
            }

            @Override
            public Double getAverageScore() {
                return average;
            }

            @Override
            public long getRatingCount() {
                return ratingCount;
            }
        };
    }
}
