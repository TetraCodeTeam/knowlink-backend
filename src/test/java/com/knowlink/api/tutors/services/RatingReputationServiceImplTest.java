package com.knowlink.api.tutors.services;

import com.knowlink.api.bookings.data.models.Booking;
import com.knowlink.api.ratings.data.models.Rating;
import com.knowlink.api.tutors.data.models.TutorProfile;
import com.knowlink.api.ratings.repositories.IRatingRepository;
import com.knowlink.api.tutors.repositories.ITutorProfileRepository;
import com.knowlink.api.ratings.services.implementations.RatingReputationServiceImpl;
import com.knowlink.api.users.data.models.User;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
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

    private static final ZoneId ZONE = ZoneId.of("America/Argentina/Cordoba");
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-10-02T14:00:00Z"), ZONE);

    @Mock
    private IRatingRepository ratingRepository;
    @Mock
    private ITutorProfileRepository tutorProfileRepository;
    private RatingReputationServiceImpl ratingReputationService;

    @org.junit.jupiter.api.BeforeEach
    void setUp() {
        ratingReputationService = new RatingReputationServiceImpl(
                ratingRepository, tutorProfileRepository, CLOCK);
    }

    @Test
    void refreshTutorAveragesUsesVisibleReviews() {
        User tutor = tutor();
        TutorProfile tutorProfile = TutorProfile.builder().user(tutor).build();
        Rating first = rating(tutor, 4);
        Rating second = rating(tutor, 5);
        when(tutorProfileRepository.findByUserId(tutor.getUserId())).thenReturn(Optional.of(tutorProfile));
        when(ratingRepository.calculateVisibleTutorAverage(any()))
            .thenReturn(4.5);

        ratingReputationService.refreshTutorAverages(List.of(first));

        assertEquals(4.5, tutorProfile.getAverageRating());
        verify(tutorProfileRepository).save(tutorProfile);
    }

    @Test
    void revealExpiredRatingsPublishesReviewsAndRefreshesTutorAverage() {
        User tutor = tutor();
        TutorProfile tutorProfile = TutorProfile.builder().user(tutor).build();
        Rating expiredRating = rating(tutor, 5);
        when(ratingRepository.findExpiredHiddenRatings(any(), any(LocalDateTime.class), any(), any()))
                .thenReturn(List.of(expiredRating));
        when(tutorProfileRepository.findByUserId(tutor.getUserId())).thenReturn(Optional.of(tutorProfile));
        when(ratingRepository.calculateVisibleTutorAverage(any()))
            .thenReturn(5.0);

        ratingReputationService.revealExpiredRatings();

        assertTrue(expiredRating.isVisible());
        assertEquals(5.0, tutorProfile.getAverageRating());
        verify(ratingRepository).saveAll(anyList());
        verify(tutorProfileRepository).save(tutorProfile);
    }

    private User tutor() {
        return User.builder().userId(UUID.randomUUID()).build();
    }

    private Rating rating(User tutor, int score) {
        return Rating.builder()
                .booking(Booking.builder().tutor(tutor).build())
                .ratedUser(tutor)
                .score(score)
                .build();
    }
}