package com.knowlink.api.community.services.implementations;

import com.knowlink.api.community.controllers.responses.RankedTutorResponse;
import com.knowlink.api.community.controllers.responses.TutorPodiumResponse;
import com.knowlink.api.community.data.enums.PodiumBadge;
import com.knowlink.api.community.data.models.TutorRankingEntry;
import com.knowlink.api.community.repositories.ITutorRankingRepository;
import com.knowlink.api.community.services.interfaces.ITutorRankingSnapshotService;
import com.knowlink.api.tutors.repositories.ITutorProfileRepository;
import com.knowlink.api.users.data.models.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TutorRankingServiceImplTest {

    private static final LocalDate PERIOD = LocalDate.of(2026, 11, 1);

    @Mock
    private ITutorRankingRepository rankingRepository;
    @Mock
    private ITutorRankingSnapshotService snapshotService;
    @Mock
    private ITutorProfileRepository tutorProfileRepository;

    private TutorRankingServiceImpl service;

    @BeforeEach
    void setUp() {
        Clock clock = Clock.fixed(Instant.parse("2026-11-14T15:00:00Z"), java.time.ZoneOffset.UTC);
        service = new TutorRankingServiceImpl(rankingRepository, snapshotService, tutorProfileRepository, clock);
    }

    @Test
    @DisplayName("El podio y la tabla exponen el puntaje y sus componentes de comunidad")
    void podiumAndRankingExposeScoreAndItsCommunityComponents() {
        TutorRankingEntry first = entry(1, "Julián", 83.5, 3, 15, 4.5);
        TutorRankingEntry second = entry(2, "Sofía", 75.0, 3, 12, 4.0);
        TutorRankingEntry third = entry(3, "Mateo", 69.0, 2, 12, 5.0);
        TutorRankingEntry fourth = entry(4, "Luciana", 50.25, 1, 9, null);
        stubEntries(first, second, third, fourth);

        TutorPodiumResponse response = service.getPodium();

        assertThat(response.periodStart()).isEqualTo(PERIOD);
        assertThat(response.podium()).extracting(RankedTutorResponse::position).containsExactly(1, 2, 3);
        assertThat(response.ranking()).extracting(RankedTutorResponse::position).containsExactly(4);

        RankedTutorResponse top = response.podium().get(0);
        assertThat(top.fullName()).isEqualTo("Julián");
        assertThat(top.score()).isEqualTo(83.5);
        assertThat(top.bestAnswers()).isEqualTo(3);
        assertThat(top.helpfulMarks()).isEqualTo(15);
        assertThat(top.averageRating()).isEqualTo(4.5);

        RankedTutorResponse inTable = response.ranking().get(0);
        assertThat(inTable.score()).isEqualTo(50.25);
        assertThat(inTable.bestAnswers()).isEqualTo(1);
        assertThat(inTable.helpfulMarks()).isEqualTo(9);
        assertThat(inTable.averageRating()).isNull();
    }

    @Test
    @DisplayName("Solo los tres primeros puestos llevan insignia")
    void onlyThePodiumHasBadges() {
        stubEntries(entry(1, "A", 30, 3, 0, null), entry(2, "B", 20, 2, 0, null),
                entry(3, "C", 10, 1, 0, null), entry(4, "D", 5, 0, 0, null));

        TutorPodiumResponse response = service.getPodium();

        assertThat(response.podium()).extracting(RankedTutorResponse::badge)
                .containsExactly(PodiumBadge.GOLD, PodiumBadge.SILVER, PodiumBadge.BRONZE);
        assertThat(response.ranking()).extracting(RankedTutorResponse::badge).containsOnlyNulls();
    }

    @Test
    @DisplayName("El ranking del mes en curso se genera si todavía no existe")
    void currentMonthSnapshotIsGeneratedWhenAbsent() {
        stubEntries();

        service.getPodium();

        verify(snapshotService).generateIfAbsent(PERIOD);
    }

    @Test
    @DisplayName("Sin tutores en el ranking devuelve el podio y la tabla vacíos")
    void emptyRankingReturnsEmptyLists() {
        stubEntries();

        TutorPodiumResponse response = service.getPodium();

        assertThat(response.podium()).isEmpty();
        assertThat(response.ranking()).isEmpty();
    }

    private void stubEntries(TutorRankingEntry... entries) {
        when(rankingRepository.findByPeriodStartAndPositionLessThanEqualOrderByPositionAsc(any(), anyInt()))
                .thenReturn(List.of(entries));
        if (entries.length > 0) {
            when(tutorProfileRepository.findWithCareerByUserIdIn(anyCollection())).thenReturn(List.of());
            when(rankingRepository.findActiveSubjectNames(anyCollection())).thenReturn(List.of());
        }
    }

    private TutorRankingEntry entry(int position, String name, double score, int bestAnswers, int helpfulMarks,
            Double averageRating) {
        return TutorRankingEntry.builder()
                .periodStart(PERIOD)
                .tutor(User.builder().userId(UUID.randomUUID()).fullName(name).build())
                .position(position)
                .score(score)
                .bestAnswers(bestAnswers)
                .helpfulMarks(helpfulMarks)
                .averageRating(averageRating)
                .reviewsCount(averageRating == null ? 0 : 5)
                .completedClasses(10)
                .build();
    }
}
