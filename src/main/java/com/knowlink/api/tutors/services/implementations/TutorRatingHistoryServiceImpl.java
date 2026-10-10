package com.knowlink.api.tutors.services.implementations;

import com.knowlink.api.tutors.controllers.responses.PagedCommentsResponse;
import com.knowlink.api.tutors.controllers.responses.RatingCommentResponse;
import com.knowlink.api.tutors.controllers.responses.SubjectAverageResponse;
import com.knowlink.api.tutors.controllers.responses.TutorRatingHistoryResponse;
import com.knowlink.api.ratings.data.models.Rating;
import com.knowlink.api.tutors.data.projections.OverallRatingSummary;
import com.knowlink.api.tutors.data.projections.SubjectRatingSummary;
import com.knowlink.api.ratings.repositories.IRatingRepository;
import com.knowlink.api.tutors.services.interfaces.ITutorRatingHistoryService;
import com.knowlink.api.tutors.validations.ITutorProfileValidationService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class TutorRatingHistoryServiceImpl implements ITutorRatingHistoryService {

    static final int DEFAULT_PAGE_SIZE = 10;
    static final int MAX_PAGE_SIZE = 50;

    private final IRatingRepository ratingRepository;
    private final ITutorProfileValidationService tutorProfileValidationService;

    @Override
    @Transactional(readOnly = true)
    public TutorRatingHistoryResponse getRatingHistory(UUID tutorId, UUID subjectId, int page, int size) {
        tutorProfileValidationService.findTutorProfileOrThrowException(tutorId);

        Pageable pageable = PageRequest.of(Math.max(page, 0),
                Math.min(Math.max(size, 1), MAX_PAGE_SIZE));

        OverallRatingSummary summary = ratingRepository.findOverallSummaryByTutorId(tutorId);
        long totalRatings = summary == null || summary.getRatingCount() == null
                ? 0L
                : summary.getRatingCount();
        Double averageRating = totalRatings == 0L || summary.getAverageScore() == null
                ? null
                : roundOneDecimal(summary.getAverageScore());

        List<SubjectAverageResponse> subjects = ratingRepository.findAverageGroupedBySubject(tutorId)
                .stream()
                .map(TutorRatingHistoryServiceImpl::toSubjectAverage)
                .toList();

        Page<Rating> comments = subjectId == null
                ? ratingRepository.findCompletedComments(tutorId, pageable)
                : ratingRepository.findCompletedCommentsBySubject(tutorId, subjectId, pageable);

        PagedCommentsResponse pagedComments = new PagedCommentsResponse(
                comments.getContent().stream()
                        .map(TutorRatingHistoryServiceImpl::toComment)
                        .toList(),
                comments.getNumber(),
                comments.getSize(),
                comments.getTotalElements(),
                comments.getTotalPages());

        return new TutorRatingHistoryResponse(tutorId, averageRating, totalRatings, subjects, pagedComments);
    }

    private static SubjectAverageResponse toSubjectAverage(SubjectRatingSummary summary) {
        return new SubjectAverageResponse(
                summary.getSubjectId(),
                summary.getSubjectName(),
                summary.getAverageScore() == null ? null : roundOneDecimal(summary.getAverageScore()),
                summary.getRatingCount() == null ? 0L : summary.getRatingCount());
    }

    private static RatingCommentResponse toComment(Rating rating) {
        return new RatingCommentResponse(
                rating.getRatingId(),
                rating.getBooking().getTutorSubject().getSubject().getSubjectId(),
                rating.getBooking().getTutorSubject().getSubject().getName(),
                rating.getScore(),
                rating.getComment(),
                rating.getRatingDate());
    }

    private static Double roundOneDecimal(Double value) {
        return BigDecimal.valueOf(value).setScale(1, RoundingMode.HALF_UP).doubleValue();
    }
}