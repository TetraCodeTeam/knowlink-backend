package com.knowlink.api.ratings.services.implementations;

import com.knowlink.api.security.enums.Role;
import com.knowlink.api.shared.utils.AppTimeZone;
import com.knowlink.api.ratings.data.models.Rating;
import com.knowlink.api.tutors.data.models.TutorProfile;
import com.knowlink.api.ratings.repositories.IRatingRepository;
import com.knowlink.api.tutors.repositories.ITutorProfileRepository;
import com.knowlink.api.ratings.services.interfaces.IRatingReputationService;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.OptionalDouble;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class RatingReputationServiceImpl implements IRatingReputationService {

    private final IRatingRepository ratingRepository;
    private final ITutorProfileRepository tutorProfileRepository;

    @Scheduled(fixedRate = 60_000)
    @Transactional
    public void revealExpiredRatings() {
        LocalDateTime visibleBefore = LocalDateTime.now(AppTimeZone.ZONE).minusHours(24);
        List<Rating> expiredRatings = ratingRepository.findExpiredHiddenRatings(visibleBefore);
        if (expiredRatings.isEmpty()) {
            return;
        }

        expiredRatings.forEach(Rating::makeVisible);
        ratingRepository.saveAll(expiredRatings);
        refreshTutorAverages(expiredRatings);
    }

    @Override
    @Transactional
    public void refreshTutorAverages(List<Rating> ratings) {
        ratings.stream()
                .map(Rating::getRatedUser)
                .filter(user -> user.getRole() == Role.TUTOR)
                .map(user -> user.getUserId())
                .distinct()
                .forEach(this::refreshTutorAverage);
    }

    private void refreshTutorAverage(UUID tutorUserId) {
        TutorProfile tutorProfile = tutorProfileRepository.findByUserId(tutorUserId).orElse(null);
        if (tutorProfile == null) {
            return;
        }

        List<Rating> visibleRatings = ratingRepository.findVisibleByRatedUserId(
                tutorUserId, LocalDateTime.now(AppTimeZone.ZONE).minusHours(24));
        OptionalDouble average = visibleRatings.stream().mapToInt(Rating::getScore).average();
        tutorProfile.setAverageRating(average.isPresent() ? average.getAsDouble() : null);
        tutorProfileRepository.save(tutorProfile);
    }
}