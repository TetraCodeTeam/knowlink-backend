package com.knowlink.api.ratings.services.implementations;

import com.knowlink.api.ratings.data.models.Rating;
import com.knowlink.api.tutors.data.models.TutorProfile;
import com.knowlink.api.ratings.repositories.IRatingRepository;
import com.knowlink.api.tutors.repositories.ITutorProfileRepository;
import com.knowlink.api.ratings.services.interfaces.IRatingReputationService;
import com.knowlink.api.ratings.utils.RatingConstants;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class RatingReputationServiceImpl implements IRatingReputationService {

    private final IRatingRepository ratingRepository;
    private final ITutorProfileRepository tutorProfileRepository;
    private final Clock clock;

    @Scheduled(fixedDelay = 60_000)
    @Transactional
    public void revealExpiredRatings() {
        LocalDateTime visibleBefore = visibleBefore();
        List<Rating> expiredRatings = ratingRepository.findExpiredHiddenRatings(
                visibleBefore, visibleBefore.toLocalDate(), visibleBefore.toLocalTime());
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
            .filter(rating -> rating.getBooking().getTutor().getUserId()
                .equals(rating.getRatedUser().getUserId()))
            .map(rating -> rating.getBooking().getTutor().getUserId())
                .distinct()
                .forEach(this::refreshTutorAverage);
    }

    private void refreshTutorAverage(UUID tutorUserId) {
        TutorProfile tutorProfile = tutorProfileRepository.findByUserId(tutorUserId).orElse(null);
        if (tutorProfile == null) {
            return;
        }

        LocalDateTime visibleBefore = visibleBefore();
        Double average = ratingRepository.calculateVisibleTutorAverage(
                tutorUserId, visibleBefore, visibleBefore.toLocalDate(), visibleBefore.toLocalTime());
        tutorProfile.setAverageRating(average);
        tutorProfileRepository.save(tutorProfile);
    }

    private LocalDateTime visibleBefore() {
        return LocalDateTime.now(clock).minus(RatingConstants.BLIND_REVIEW_WINDOW);
    }
}