package com.knowlink.api.community.services.implementations;

import com.knowlink.api.community.data.models.TutorRankingEntry;
import com.knowlink.api.community.data.projections.TutorCountMetric;
import com.knowlink.api.community.data.projections.TutorRatingMetric;
import com.knowlink.api.community.repositories.ITutorRankingRepository;
import com.knowlink.api.community.services.interfaces.ITutorRankingSnapshotService;
import com.knowlink.api.community.utils.CommunityConstants;
import com.knowlink.api.users.repositories.IUserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Calcula el ranking mensual de tutores. Puntaje = mejores aportes (respuesta destacada en una publicación
 * resuelta) y marcas "Esto me sirvió" recibidas durante el mes anterior, más la calificación promedio vigente.
 * Solo participan tutores con cuenta activa y puntaje mayor a cero.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class TutorRankingSnapshotServiceImpl implements ITutorRankingSnapshotService {

    private final ITutorRankingRepository rankingRepository;
    private final IUserRepository userRepository;

    private record Candidate(UUID tutorId, int bestAnswers, int helpfulMarks, Double averageRating,
            int reviewsCount, int completedClasses, double score) {
    }

    @Override
    @Transactional
    public void generateIfAbsent(LocalDate periodStart) {
        if (rankingRepository.existsByPeriodStart(periodStart)) {
            return;
        }

        List<UUID> eligibleTutorIds = rankingRepository.findEligibleTutorIds();
        if (eligibleTutorIds.isEmpty()) {
            return;
        }

        LocalDateTime windowEnd = periodStart.atStartOfDay();
        LocalDateTime windowStart = periodStart.minusMonths(1).atStartOfDay();

        Map<UUID, Long> bestAnswers = toCounts(rankingRepository.countBestAnswers(windowStart, windowEnd));
        Map<UUID, Long> helpfulMarks = toCounts(rankingRepository.countHelpfulMarks(windowStart, windowEnd));
        Map<UUID, Long> completedClasses = toCounts(rankingRepository.countCompletedClasses());
        Map<UUID, TutorRatingMetric> ratings = rankingRepository.findRatingMetrics().stream()
                .collect(Collectors.toMap(TutorRatingMetric::getTutorId, Function.identity()));

        List<Candidate> candidates = new ArrayList<>();
        for (UUID tutorId : eligibleTutorIds) {
            int best = bestAnswers.getOrDefault(tutorId, 0L).intValue();
            int helpful = helpfulMarks.getOrDefault(tutorId, 0L).intValue();
            TutorRatingMetric rating = ratings.get(tutorId);
            Double average = rating == null ? null : rating.getAverageScore();
            int reviews = rating == null ? 0 : (int) rating.getRatingCount();
            double score = best * CommunityConstants.BEST_ANSWER_WEIGHT
                    + helpful * CommunityConstants.HELPFUL_MARK_WEIGHT
                    + (average == null ? 0.0 : average * CommunityConstants.AVERAGE_RATING_WEIGHT);
            if (score > 0) {
                candidates.add(new Candidate(tutorId, best, helpful, average, reviews,
                        completedClasses.getOrDefault(tutorId, 0L).intValue(), Math.round(score * 100.0) / 100.0));
            }
        }

        candidates.sort(Comparator
                .comparingDouble(Candidate::score).reversed()
                .thenComparing(Comparator.comparingInt(Candidate::completedClasses).reversed())
                .thenComparing(Candidate::tutorId));

        List<TutorRankingEntry> entries = new ArrayList<>();
        for (int i = 0; i < candidates.size(); i++) {
            Candidate candidate = candidates.get(i);
            entries.add(TutorRankingEntry.builder()
                    .periodStart(periodStart)
                    .tutor(userRepository.getReferenceById(candidate.tutorId()))
                    .position(i + 1)
                    .score(candidate.score())
                    .bestAnswers(candidate.bestAnswers())
                    .helpfulMarks(candidate.helpfulMarks())
                    .averageRating(candidate.averageRating())
                    .reviewsCount(candidate.reviewsCount())
                    .completedClasses(candidate.completedClasses())
                    .build());
        }
        rankingRepository.saveAll(entries);
        log.info("Tutor ranking generated for period {} with {} tutors", periodStart, entries.size());
    }

    private Map<UUID, Long> toCounts(List<TutorCountMetric> metrics) {
        return metrics.stream().collect(Collectors.toMap(TutorCountMetric::getTutorId, TutorCountMetric::getTotal));
    }
}
