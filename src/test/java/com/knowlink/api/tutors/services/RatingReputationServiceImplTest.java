package com.knowlink.api.tutors.services;

import com.knowlink.api.security.enums.Role;
import com.knowlink.api.ratings.data.models.Rating;
import com.knowlink.api.tutors.data.models.TutorProfile;
import com.knowlink.api.ratings.repositories.IRatingRepository;
import com.knowlink.api.tutors.repositories.ITutorProfileRepository;
import com.knowlink.api.ratings.services.implementations.RatingReputationServiceImpl;
import com.knowlink.api.users.data.models.User;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RatingReputationServiceImplTest {

    @Mock
    private IRatingRepository ratingRepository;
    @Mock
    private ITutorProfileRepository tutorProfileRepository;
    @InjectMocks
    private RatingReputationServiceImpl ratingReputationService;

    @Test
    void refreshTutorAveragesUsesVisibleReviews() {
        User tutor = tutor();
        TutorProfile tutorProfile = TutorProfile.builder().user(tutor).build();
        Rating first = rating(tutor, 4);
        Rating second = rating(tutor, 5);
        when(tutorProfileRepository.findByUserId(tutor.getUserId())).thenReturn(Optional.of(tutorProfile));
        when(ratingRepository.findVisibleByRatedUserId(any(), any(LocalDateTime.class)))
                .thenReturn(List.of(first, second));

        ratingReputationService.refreshTutorAverages(List.of(first));

        assertEquals(4.5, tutorProfile.getAverageRating());
        verify(tutorProfileRepository).save(tutorProfile);
    }

    @Test
    void revealExpiredRatingsPublishesReviewsAndRefreshesTutorAverage() {
        User tutor = tutor();
        TutorProfile tutorProfile = TutorProfile.builder().user(tutor).build();
        Rating expiredRating = rating(tutor, 5);
        when(ratingRepository.findExpiredHiddenRatings(any(LocalDateTime.class)))
                .thenReturn(List.of(expiredRating));
        when(tutorProfileRepository.findByUserId(tutor.getUserId())).thenReturn(Optional.of(tutorProfile));
        when(ratingRepository.findVisibleByRatedUserId(any(), any(LocalDateTime.class)))
                .thenReturn(List.of(expiredRating));

        ratingReputationService.revealExpiredRatings();

        assertTrue(expiredRating.isVisible());
        assertEquals(5.0, tutorProfile.getAverageRating());
        verify(ratingRepository).saveAll(anyList());
        verify(tutorProfileRepository).save(tutorProfile);
    }

    private User tutor() {
        return User.builder().userId(UUID.randomUUID()).role(Role.TUTOR).build();
    }

    private Rating rating(User tutor, int score) {
        return Rating.builder().ratedUser(tutor).score(score).build();
    }
}