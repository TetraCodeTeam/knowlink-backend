package com.knowlink.api.ratings.services.implementations;

import com.knowlink.api.ratings.controllers.requests.CreateRatingRequest;
import com.knowlink.api.ratings.controllers.responses.RatingResponse;
import com.knowlink.api.bookings.data.enums.BookingStatus;
import com.knowlink.api.ratings.data.mappers.RatingMapper;
import com.knowlink.api.bookings.data.models.Booking;
import com.knowlink.api.bookings.repositories.IBookingRepository;
import com.knowlink.api.ratings.services.interfaces.IRatingService;
import com.knowlink.api.bookings.validations.IBookingValidationService;
import com.knowlink.api.exceptions.custom_exceptions.ResourceNotFoundException;
import com.knowlink.api.exceptions.custom_exceptions.ValidationException;
import com.knowlink.api.shared.utils.AppTimeZone;
import com.knowlink.api.ratings.data.models.Rating;
import com.knowlink.api.ratings.repositories.IRatingRepository;
import com.knowlink.api.ratings.services.interfaces.IRatingReputationService;
import com.knowlink.api.users.data.models.User;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class RatingServiceImpl implements IRatingService {

    private static final String SUBMITTED_MESSAGE = "Tu calificación quedó registrada de forma definitiva.";

    private final IBookingRepository bookingRepository;
    private final IRatingRepository ratingRepository;
    private final IRatingReputationService ratingReputationService;
    private final IBookingValidationService bookingValidationService;
    private final RatingMapper ratingMapper;

    @Override
    @Transactional
    public RatingResponse submitRating(UUID userId, UUID bookingId, CreateRatingRequest request) {
        Booking booking = bookingRepository.findByIdForUpdate(bookingId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "BOOKING_NOT_FOUND",
                        "La reserva no existe.",
                        "Booking not found for rating: " + bookingId));

        bookingValidationService.validateOwnership(booking, userId);
        if (booking.getBookingStatus() != BookingStatus.COMPLETED) {
            throw new ValidationException("Solo podés calificar una sesión realizada.");
        }

        boolean isStudent = booking.getStudent().getUserId().equals(userId);
        User rater = isStudent ? booking.getStudent() : booking.getTutor();
        User rated = isStudent ? booking.getTutor() : booking.getStudent();
        List<Rating> existingRatings = ratingRepository.findByBookingBookingId(bookingId);

        if (existingRatings.stream().anyMatch(rating -> rating.getRaterUser().getUserId().equals(userId))) {
            throw new ValidationException("Esta calificación ya fue registrada de forma definitiva y no puede modificarse.");
        }

        LocalDateTime now = LocalDateTime.now(AppTimeZone.ZONE);
        boolean deadlineElapsed = booking.getConfirmedAt() != null
                && !booking.getConfirmedAt().plusHours(24).isAfter(now);
        boolean visible = !existingRatings.isEmpty() || deadlineElapsed;

        Rating rating = ratingMapper.toEntity(booking, rater, rated, request, now, visible);

        if (visible) {
            List<Rating> ratingsToPublish = new ArrayList<>(existingRatings);
            ratingsToPublish.forEach(Rating::makeVisible);
            ratingsToPublish.add(rating);
            ratingRepository.saveAll(ratingsToPublish);
            ratingReputationService.refreshTutorAverages(ratingsToPublish);
        } else {
            ratingRepository.save(rating);
        }

        return new RatingResponse(visible, SUBMITTED_MESSAGE);
    }
}