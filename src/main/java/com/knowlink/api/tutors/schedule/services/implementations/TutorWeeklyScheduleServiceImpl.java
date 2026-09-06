package com.knowlink.api.tutors.schedule.services.implementations;

import com.knowlink.api.bookings.controllers.responses.BookingResponse;
import com.knowlink.api.bookings.data.enums.BookingStatus;
import com.knowlink.api.bookings.data.mappers.BookingMapper;
import com.knowlink.api.bookings.data.models.Booking;
import com.knowlink.api.bookings.repositories.IBookingRepository;
import com.knowlink.api.tutors.availability.controllers.responses.AvailabilityBlockResponse;
import com.knowlink.api.tutors.availability.data.enums.BookingStatusGroups;
import com.knowlink.api.tutors.availability.data.models.AvailabilityBlock;
import com.knowlink.api.tutors.availability.repositories.IAvailabilityBlockRepository;
import com.knowlink.api.tutors.availability.services.interfaces.IAvailabilityBlockService;
import com.knowlink.api.tutors.data.models.TutorProfile;
import com.knowlink.api.tutors.schedule.controllers.responses.NextClassResponse;
import com.knowlink.api.tutors.schedule.controllers.responses.WeeklyScheduleResponse;
import com.knowlink.api.tutors.schedule.controllers.responses.WeeklyScheduleSummaryResponse;
import com.knowlink.api.tutors.schedule.services.interfaces.ITutorWeeklyScheduleService;
import com.knowlink.api.tutors.validations.ITutorProfileValidationService;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import com.knowlink.api.shared.utils.AppTimeZone;

@Service
@RequiredArgsConstructor
public class TutorWeeklyScheduleServiceImpl implements ITutorWeeklyScheduleService {

    private final ITutorProfileValidationService tutorProfileValidationService;
    private final IAvailabilityBlockService availabilityBlockService;
    private final IAvailabilityBlockRepository availabilityBlockRepository;
    private final IBookingRepository bookingRepository;
    private final BookingMapper bookingMapper;

    @Override
    @Transactional(readOnly = true)
    public WeeklyScheduleResponse getWeeklySchedule(UUID tutorUserId, LocalDate from, LocalDate to) {
        TutorProfile tutorProfile = tutorProfileValidationService.findTutorProfileOrThrowException(tutorUserId);

        List<AvailabilityBlockResponse> availabilityBlocks =
                availabilityBlockService.getBlocksInRange(tutorUserId, from, to);

        List<Booking> activeBookings =
                bookingRepository.findActiveBookingsInRange(tutorUserId, from, to, BookingStatusGroups.ACTIVE);

        List<BookingResponse> bookings = activeBookings.stream()
                .map(bookingMapper::toResponse)
                .toList();

        List<Booking> confirmedBookings = activeBookings.stream()
                .filter(b -> b.getBookingStatus() == BookingStatus.BOOKED)
                .toList();

        LocalDateTime now = LocalDateTime.now(AppTimeZone.ZONE);

        List<AvailabilityBlock> freeBlocks = availabilityBlockRepository.findInRange(
                tutorProfile.getTutorProfileId(), from, to, now.toLocalDate(), now.toLocalTime())
                .stream()
                .filter(AvailabilityBlock::isAvailable)
                .toList();

        NextClassResponse nextClass = confirmedBookings.stream()
                .filter(b -> b.getSessionDate().isAfter(now.toLocalDate())
                        || (b.getSessionDate().isEqual(now.toLocalDate())
                            && b.getStartTime().isAfter(now.toLocalTime())))
                .min((a, b) -> {
                    int dateCompare = a.getSessionDate().compareTo(b.getSessionDate());
                    if (dateCompare != 0) return dateCompare;
                    return a.getStartTime().compareTo(b.getStartTime());
                })
                .map(b -> new NextClassResponse(
                        b.getBookingId(),
                        b.getSessionDate(),
                        b.getStartTime(),
                        b.getEndTime()))
                .orElse(null);

        WeeklyScheduleSummaryResponse summary = new WeeklyScheduleSummaryResponse(
                confirmedBookings.size(),
                freeBlocks.size(),
                nextClass);

        return new WeeklyScheduleResponse(from, to, availabilityBlocks, bookings, summary);
    }
}
