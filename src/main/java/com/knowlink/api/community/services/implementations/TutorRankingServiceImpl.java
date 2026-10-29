package com.knowlink.api.community.services.implementations;

import com.knowlink.api.community.controllers.responses.MyPodiumPositionResponse;
import com.knowlink.api.community.controllers.responses.RankedTutorResponse;
import com.knowlink.api.community.controllers.responses.TutorPodiumResponse;
import com.knowlink.api.community.data.enums.PodiumBadge;
import com.knowlink.api.community.data.models.TutorRankingEntry;
import com.knowlink.api.community.data.projections.TutorSubjectName;
import com.knowlink.api.community.repositories.ITutorRankingRepository;
import com.knowlink.api.community.services.interfaces.ITutorRankingService;
import com.knowlink.api.community.services.interfaces.ITutorRankingSnapshotService;
import com.knowlink.api.community.utils.CommunityConstants;
import com.knowlink.api.tutors.data.models.TutorProfile;
import com.knowlink.api.tutors.repositories.ITutorProfileRepository;
import com.knowlink.api.users.data.enums.AccountStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Los métodos de lectura no abren una transacción propia a propósito: la generación del ranking se hace en su
 * propia transacción (ver {@link ITutorRankingSnapshotService}) y cada consulta posterior ya trae sus
 * asociaciones inicializadas, así que no dependen de una sesión abierta.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class TutorRankingServiceImpl implements ITutorRankingService {

    private static final String UNKNOWN_TUTOR_NAME = "Tutor";

    private final ITutorRankingRepository rankingRepository;
    private final ITutorRankingSnapshotService snapshotService;
    private final ITutorProfileRepository tutorProfileRepository;
    private final Clock clock;

    @Override
    public TutorPodiumResponse getPodium() {
        LocalDate period = currentPeriod();
        ensureSnapshot(period);

        List<TutorRankingEntry> entries = rankingRepository
                .findByPeriodStartAndPositionLessThanEqualOrderByPositionAsc(period, CommunityConstants.TOP_RANKING_SIZE);
        List<UUID> tutorIds = entries.stream().map(entry -> entry.getTutor().getUserId()).toList();

        Map<UUID, TutorProfile> profiles = loadProfiles(tutorIds);
        Map<UUID, List<String>> subjects = loadSubjects(tutorIds);

        List<RankedTutorResponse> podium = new ArrayList<>();
        List<RankedTutorResponse> ranking = new ArrayList<>();
        for (TutorRankingEntry entry : entries) {
            UUID tutorId = entry.getTutor().getUserId();
            RankedTutorResponse response = toResponse(entry, profiles.get(tutorId),
                    subjects.getOrDefault(tutorId, List.of()));
            (entry.getPosition() <= CommunityConstants.PODIUM_SIZE ? podium : ranking).add(response);
        }
        return new TutorPodiumResponse(period, podium, ranking);
    }

    @Override
    public MyPodiumPositionResponse getMyPosition(UUID userId) {
        Optional<TutorProfile> profile = tutorProfileRepository.findWithCareerByUserIdIn(List.of(userId)).stream()
                .findFirst()
                .filter(tp -> tp.getUser().getAccountStatus() == AccountStatus.ACTIVE
                        && tp.getUser().getDeletedAt() == null);
        if (profile.isEmpty()) {
            return new MyPodiumPositionResponse(false, null, null, null, 0, 0);
        }

        String fullName = displayName(profile.get());
        String careerName = profile.get().getCareer().getName();

        LocalDate period = currentPeriod();
        ensureSnapshot(period);
        Optional<TutorRankingEntry> entry = rankingRepository.findByPeriodStartAndTutor_UserId(period, userId);
        if (entry.isEmpty()) {
            return new MyPodiumPositionResponse(true, null, fullName, careerName, 0, 0);
        }

        int position = entry.get().getPosition();
        int topSize = CommunityConstants.TOP_RANKING_SIZE;
        int positionsToTopTen = Math.max(0, position - topSize);
        int progressPercent = position <= topSize ? 100 : (int) Math.round(topSize * 100.0 / position);
        return new MyPodiumPositionResponse(true, position, fullName, careerName, positionsToTopTen, progressPercent);
    }

    private void ensureSnapshot(LocalDate period) {
        try {
            snapshotService.generateIfAbsent(period);
        } catch (DataAccessException ex) {
            // Lo habitual es que otra solicitud (o el job mensual) haya generado el mismo período al mismo
            // tiempo y la restricción única lo rechace: el ranking ya existe y se lee tal cual.
            log.warn("Tutor ranking for period {} was not generated by this request: {}", period, ex.getMessage());
        }
    }

    private LocalDate currentPeriod() {
        return LocalDate.now(clock).withDayOfMonth(1);
    }

    private Map<UUID, TutorProfile> loadProfiles(Collection<UUID> tutorIds) {
        if (tutorIds.isEmpty()) {
            return Map.of();
        }
        return tutorProfileRepository.findWithCareerByUserIdIn(tutorIds).stream()
                .collect(Collectors.toMap(tp -> tp.getUser().getUserId(), Function.identity()));
    }

    private Map<UUID, List<String>> loadSubjects(Collection<UUID> tutorIds) {
        if (tutorIds.isEmpty()) {
            return Map.of();
        }
        Map<UUID, Set<String>> names = new HashMap<>();
        for (TutorSubjectName row : rankingRepository.findActiveSubjectNames(tutorIds)) {
            names.computeIfAbsent(row.getTutorId(), id -> new LinkedHashSet<>()).add(row.getSubjectName());
        }
        return names.entrySet().stream().collect(Collectors.toMap(
                Map.Entry::getKey,
                e -> e.getValue().stream().limit(CommunityConstants.RANKING_SUBJECTS_LIMIT).toList()));
    }

    private RankedTutorResponse toResponse(TutorRankingEntry entry, TutorProfile profile, List<String> subjects) {
        int position = entry.getPosition();
        PodiumBadge badge = switch (position) {
            case 1 -> PodiumBadge.GOLD;
            case 2 -> PodiumBadge.SILVER;
            case 3 -> PodiumBadge.BRONZE;
            default -> null;
        };
        String picture = profile == null ? null
                : profile.getProfilePictureUrl() != null ? profile.getProfilePictureUrl()
                : profile.getUser().getProfilePictureUrl();
        return new RankedTutorResponse(
                entry.getTutor().getUserId(),
                position,
                profile == null ? displayName(entry) : displayName(profile),
                profile == null ? null : profile.getCareer().getName(),
                picture,
                entry.getAverageRating(),
                entry.getReviewsCount(),
                entry.getCompletedClasses(),
                profile != null && profile.isVerified(),
                badge,
                subjects);
    }

    private String displayName(TutorProfile profile) {
        String name = profile.getUser().getFullName();
        return name == null || name.isBlank() ? UNKNOWN_TUTOR_NAME : name;
    }

    private String displayName(TutorRankingEntry entry) {
        String name = entry.getTutor().getFullName();
        return name == null || name.isBlank() ? UNKNOWN_TUTOR_NAME : name;
    }
}
