package com.knowlink.api.tutors.services;

import com.knowlink.api.bookings.data.models.Booking;
import com.knowlink.api.exceptions.custom_exceptions.ResourceNotFoundException;
import com.knowlink.api.tutors.controllers.responses.RatingCommentResponse;
import com.knowlink.api.tutors.controllers.responses.TutorRatingHistoryResponse;
import com.knowlink.api.ratings.data.models.Rating;
import com.knowlink.api.tutors.data.models.Subject;
import com.knowlink.api.tutors.data.models.TutorSubject;
import com.knowlink.api.tutors.data.projections.OverallRatingSummary;
import com.knowlink.api.tutors.data.projections.SubjectRatingSummary;
import com.knowlink.api.ratings.repositories.IRatingRepository;
import com.knowlink.api.tutors.services.implementations.TutorRatingHistoryServiceImpl;
import com.knowlink.api.tutors.validations.ITutorProfileValidationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TutorRatingHistoryServiceImplTest {

    @Mock
    private IRatingRepository ratingRepository;

    @Mock
    private ITutorProfileValidationService tutorProfileValidationService;

    private TutorRatingHistoryServiceImpl service;

    private final UUID tutorId = UUID.randomUUID();
    private final UUID subjectId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        service = new TutorRatingHistoryServiceImpl(ratingRepository, tutorProfileValidationService);
    }

    private OverallRatingSummary summary(Double averageScore, Long ratingCount) {
        return new OverallRatingSummary() {
            @Override
            public Double getAverageScore() {
                return averageScore;
            }

            @Override
            public Long getRatingCount() {
                return ratingCount;
            }
        };
    }

    private SubjectRatingSummary subjectSummary(UUID subjectId, String subjectName, Double averageScore,
            Long ratingCount) {
        return new SubjectRatingSummary() {
            @Override
            public UUID getSubjectId() {
                return subjectId;
            }

            @Override
            public String getSubjectName() {
                return subjectName;
            }

            @Override
            public Double getAverageScore() {
                return averageScore;
            }

            @Override
            public Long getRatingCount() {
                return ratingCount;
            }
        };
    }

    private Page<Rating> ratingPage(List<Rating> ratings, int total) {
        return new PageImpl<>(ratings, PageRequest.of(0, 10), total);
    }

    private Rating ratingWithComment(UUID ratingId, UUID subjectId, String subjectName, Integer score,
            String comment, LocalDateTime ratingDate) {
        Subject subject = Subject.builder().subjectId(subjectId).name(subjectName).build();
        TutorSubject tutorSubject = TutorSubject.builder().subject(subject).build();
        Booking booking = Booking.builder().tutorSubject(tutorSubject).build();
        return Rating.builder()
                .ratingId(ratingId)
                .booking(booking)
                .score(score)
                .comment(comment)
                .ratingDate(ratingDate)
                .visible(true)
                .build();
    }

    @Test
    @DisplayName("CA1: averages rounded to one decimal with real totals (never hardcoded)")
    void averagesAreRoundedToOneDecimal() {
        UUID firstSubjectId = UUID.randomUUID();
        UUID secondSubjectId = UUID.randomUUID();
        when(tutorProfileValidationService.findTutorProfileOrThrowException(tutorId))
                .thenReturn(null);
        when(ratingRepository.findOverallSummaryByTutorId(tutorId)).thenReturn(summary(4.26, 14L));
        when(ratingRepository.findAverageGroupedBySubject(tutorId)).thenReturn(List.of(
                subjectSummary(firstSubjectId, "Analisis Matematico I", 4.84, 9L),
                subjectSummary(secondSubjectId, "Fisica I", 4.24, 5L)));
        when(ratingRepository.findCompletedComments(eq(tutorId), any(Pageable.class)))
                .thenReturn(ratingPage(List.of(), 0));

        TutorRatingHistoryResponse response = service.getRatingHistory(tutorId, null, 0, 10);

        assertThat(response.tutorId()).isEqualTo(tutorId);
        assertThat(response.averageRating()).isEqualTo(4.3);
        assertThat(response.totalRatings()).isEqualTo(14L);
        assertThat(response.subjects()).hasSize(2);
        assertThat(response.subjects().get(0).subjectId()).isEqualTo(firstSubjectId);
        assertThat(response.subjects().get(0).name()).isEqualTo("Analisis Matematico I");
        assertThat(response.subjects().get(0).average()).isEqualTo(4.8);
        assertThat(response.subjects().get(0).count()).isEqualTo(9L);
        assertThat(response.subjects().get(1).average()).isEqualTo(4.2);
        assertThat(response.subjects().get(1).count()).isEqualTo(5L);
    }

    @Test
    @DisplayName("CA4: tutor without ratings returns empty state with null averageRating")
    void tutorWithoutRatingsReturnsEmptyState() {
        when(tutorProfileValidationService.findTutorProfileOrThrowException(tutorId))
                .thenReturn(null);
        when(ratingRepository.findOverallSummaryByTutorId(tutorId)).thenReturn(null);
        when(ratingRepository.findAverageGroupedBySubject(tutorId)).thenReturn(List.of());
        when(ratingRepository.findCompletedComments(eq(tutorId), any(Pageable.class)))
                .thenReturn(ratingPage(List.of(), 0));

        TutorRatingHistoryResponse response = service.getRatingHistory(tutorId, null, 0, 10);

        assertThat(response.averageRating()).isNull();
        assertThat(response.totalRatings()).isZero();
        assertThat(response.subjects()).isEmpty();
        assertThat(response.comments().content()).isEmpty();
        assertThat(response.comments().totalElements()).isZero();
        assertThat(response.comments().totalPages()).isZero();
    }

    @Test
    @DisplayName("Page size is clamped to 50 and negative page to 0")
    void pageAndSizeAreClamped() {
        when(tutorProfileValidationService.findTutorProfileOrThrowException(tutorId))
                .thenReturn(null);
        when(ratingRepository.findOverallSummaryByTutorId(tutorId)).thenReturn(null);
        when(ratingRepository.findAverageGroupedBySubject(tutorId)).thenReturn(List.of());
        when(ratingRepository.findCompletedComments(eq(tutorId), any(Pageable.class)))
                .thenReturn(ratingPage(List.of(), 0));

        service.getRatingHistory(tutorId, null, -3, 999);

        ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
        verify(ratingRepository).findCompletedComments(eq(tutorId), captor.capture());
        assertThat(captor.getValue().getPageNumber()).isZero();
        assertThat(captor.getValue().getPageSize()).isEqualTo(50);
    }

    @Test
    @DisplayName("Unknown tutor propagates ResourceNotFoundException (404)")
    void unknownTutorPropagatesNotFound() {
        UUID unknownTutorId = UUID.randomUUID();
        doThrow(new ResourceNotFoundException(
                "TUTOR_PROFILE_NOT_FOUND",
                "Este tutor no esta registrado.",
                "TutorProfile not found for userId: " + unknownTutorId))
                .when(tutorProfileValidationService).findTutorProfileOrThrowException(unknownTutorId);

        assertThatThrownBy(() -> service.getRatingHistory(unknownTutorId, null, 0, 10))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    @DisplayName("CA6: subjectId filters comments only, subject averages stay complete")
    void subjectFilterOnlyAffectsComments() {
        when(tutorProfileValidationService.findTutorProfileOrThrowException(tutorId))
                .thenReturn(null);
        when(ratingRepository.findOverallSummaryByTutorId(tutorId)).thenReturn(summary(4.0, 3L));
        when(ratingRepository.findAverageGroupedBySubject(tutorId)).thenReturn(List.of(
                subjectSummary(UUID.randomUUID(), "Analisis Matematico I", 4.5, 2L),
                subjectSummary(UUID.randomUUID(), "Fisica I", 3.0, 1L)));
        when(ratingRepository.findCompletedCommentsBySubject(eq(tutorId), eq(subjectId), any(Pageable.class)))
                .thenReturn(ratingPage(List.of(), 0));

        TutorRatingHistoryResponse response = service.getRatingHistory(tutorId, subjectId, 0, 10);

        assertThat(response.subjects()).hasSize(2);
        verify(ratingRepository, never()).findCompletedComments(any(UUID.class), any(Pageable.class));
    }

    @Test
    @DisplayName("CA2: every comment carries subjectId and subjectName from its session")
    void commentIncludesSubjectData() {
        UUID ratingId = UUID.randomUUID();
        UUID commentSubjectId = UUID.randomUUID();
        LocalDateTime ratingDate = LocalDateTime.of(2026, 9, 28, 19, 30);
        Rating rating = ratingWithComment(ratingId, commentSubjectId, "Analisis Matematico I", 5,
                "Explica muy claro.", ratingDate);

        when(tutorProfileValidationService.findTutorProfileOrThrowException(tutorId))
                .thenReturn(null);
        when(ratingRepository.findOverallSummaryByTutorId(tutorId)).thenReturn(summary(5.0, 1L));
        when(ratingRepository.findAverageGroupedBySubject(tutorId)).thenReturn(List.of());
        when(ratingRepository.findCompletedComments(eq(tutorId), any(Pageable.class)))
                .thenReturn(ratingPage(List.of(rating), 1));

        TutorRatingHistoryResponse response = service.getRatingHistory(tutorId, null, 0, 10);

        assertThat(response.comments().content()).hasSize(1);
        RatingCommentResponse comment = response.comments().content().get(0);
        assertThat(comment.id()).isEqualTo(ratingId);
        assertThat(comment.subjectId()).isEqualTo(commentSubjectId);
        assertThat(comment.subjectName()).isEqualTo("Analisis Matematico I");
        assertThat(comment.score()).isEqualTo(5);
        assertThat(comment.comment()).isEqualTo("Explica muy claro.");
        assertThat(comment.ratingDate()).isEqualTo(ratingDate);
        assertThat(response.comments().totalElements()).isEqualTo(1L);
    }

    @Test
    @DisplayName("Averages and counts come from repository queries, not in-memory calculation")
    void averagesComeFromDatabaseQueries() {
        when(tutorProfileValidationService.findTutorProfileOrThrowException(tutorId))
                .thenReturn(null);
        when(ratingRepository.findOverallSummaryByTutorId(tutorId)).thenReturn(summary(4.6, 14L));
        when(ratingRepository.findAverageGroupedBySubject(tutorId)).thenReturn(List.of());
        when(ratingRepository.findCompletedComments(eq(tutorId), any(Pageable.class)))
                .thenReturn(ratingPage(List.of(), 0));

        service.getRatingHistory(tutorId, null, 0, 10);

        verify(ratingRepository).findOverallSummaryByTutorId(tutorId);
        verify(ratingRepository).findAverageGroupedBySubject(tutorId);
        verify(ratingRepository).findCompletedComments(eq(tutorId), any(Pageable.class));
    }
}