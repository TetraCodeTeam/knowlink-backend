package com.knowlink.api.ratings.services.implementations;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.knowlink.api.bookings.data.enums.BookingStatus;
import com.knowlink.api.bookings.data.models.Booking;
import com.knowlink.api.ratings.controllers.responses.PendingRatingItemResponse;
import com.knowlink.api.ratings.controllers.responses.PendingRatingsResponse;
import com.knowlink.api.ratings.gate.RatingGateProperties;
import com.knowlink.api.ratings.repositories.IRatingRepository;
import com.knowlink.api.ratings.services.interfaces.IRatingGateService;
import com.knowlink.api.security.enums.Role;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class RatingGateServiceImpl implements IRatingGateService {

    private final IRatingRepository ratingRepository;
    private final RatingGateProperties properties;
    private final Clock clock;
    private final Cache<UUID, Boolean> clearCache;

    public RatingGateServiceImpl(IRatingRepository ratingRepository, RatingGateProperties properties, Clock clock) {
        this.ratingRepository = ratingRepository;
        this.properties = properties;
        this.clock = clock;
        this.clearCache = Caffeine.newBuilder()
                .expireAfterWrite(Duration.ofSeconds(properties.getClearCacheSeconds()))
                .maximumSize(10_000)
                .build();
    }

    @Override
    public boolean isBlocked(UUID userId) {
        Optional<LocalDateTime> enforcedFrom = properties.enforcedFromAsDateTime();
        if (enforcedFrom.isEmpty()) {
            return false;
        }
        if (clearCache.getIfPresent(userId) != null) {
            return false;
        }

        LocalDateTime from = enforcedFrom.orElseThrow();
        LocalDateTime now = LocalDateTime.now(clock);
        boolean blocked = ratingRepository.existsBlockingBooking(
                userId,
                BookingStatus.COMPLETED,
                now.toLocalDate(),
                now.toLocalTime(),
                from.toLocalDate(),
                from.toLocalTime());

        if (!blocked) {
            clearCache.put(userId, Boolean.TRUE);
        }
        return blocked;
    }

    @Override
    @Transactional(readOnly = true)
    public PendingRatingsResponse getPending(UUID userId) {
        Optional<LocalDateTime> enforcedFrom = properties.enforcedFromAsDateTime();
        if (enforcedFrom.isEmpty()) {
            return new PendingRatingsResponse(List.of(), null);
        }

        LocalDateTime from = enforcedFrom.orElseThrow();
        LocalDateTime now = LocalDateTime.now(clock);

        List<PendingRatingItemResponse> blocking = ratingRepository.findBlockingBookings(
                userId,
                BookingStatus.COMPLETED,
                now.toLocalDate(),
                now.toLocalTime(),
                from.toLocalDate(),
                from.toLocalTime())
                .stream()
                .map(booking -> toItem(booking, userId))
                .toList();

        LocalDateTime nextBlockAt = ratingRepository.findUpcomingUnratedCompleted(
                userId,
                BookingStatus.COMPLETED,
                now.toLocalDate(),
                now.toLocalTime(),
                from.toLocalDate(),
                from.toLocalTime(),
                PageRequest.of(0, 1))
                .stream()
                .findFirst()
                .map(booking -> LocalDateTime.of(booking.getSessionDate(), booking.getEndTime()))
                .orElse(null);

        return new PendingRatingsResponse(blocking, nextBlockAt);
    }

    private PendingRatingItemResponse toItem(Booking booking, UUID userId) {
        boolean isStudent = booking.getStudent().getUserId().equals(userId);
        Role raterRole = isStudent ? Role.STUDENT : Role.TUTOR;
        String otherPartyFullName = isStudent
                ? booking.getTutor().getFullName()
                : booking.getStudent().getFullName();
        return new PendingRatingItemResponse(
                booking.getBookingId(),
                raterRole,
                booking.getTutorSubject().getSubject().getName(),
                otherPartyFullName,
                booking.getSessionDate(),
                booking.getStartTime(),
                booking.getEndTime());
    }
}
