package com.knowlink.api.bookings.validations;

import com.knowlink.api.exceptions.custom_exceptions.ValidationException;
import com.knowlink.api.shared.utils.AppTimeZone;
import com.knowlink.api.timeslot.data.models.TimeSlot;
import com.knowlink.api.bookings.data.models.Hold;
import com.knowlink.api.bookings.repositories.IHoldRepository;
import com.knowlink.api.tutors.availability.data.enums.BookingStatusGroups;
import com.knowlink.api.bookings.data.models.Booking;
import com.knowlink.api.tutors.data.models.TutorProfile;
import com.knowlink.api.bookings.repositories.IBookingRepository;
import com.knowlink.api.users.data.models.User;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class HoldValidationServiceImpl implements IHoldValidationService {

    private static final Duration BOOKING_DURATION = Duration.ofHours(1);

    private final IHoldRepository holdRepository;
    private final IBookingRepository bookingRepository;

    @Override
    public void validateExactDuration(LocalTime startTime, LocalTime endTime) {
        if (!Duration.between(startTime, endTime).equals(BOOKING_DURATION)) {
            throw new ValidationException("Las clases se reservan en bloques de exactamente 1 hora.");
        }
    }

    @Override
    public void validateTimeWithinSlot(TimeSlot timeSlot, LocalTime startTime, LocalTime endTime) {
        if (startTime.isBefore(timeSlot.getStartTime()) || endTime.isAfter(timeSlot.getEndTime())) {
            throw new ValidationException("El horario seleccionado está fuera del bloque disponible del tutor.");
        }
    }

    @Override
    public void validateMinNotice(TutorProfile tutorProfile, TimeSlot timeSlot, LocalTime startTime) {
        Integer minNoticeMinutes = tutorProfile.getMinNoticeMinutes();
        if (minNoticeMinutes == null || minNoticeMinutes <= 0) {
            return;
        }

        LocalDateTime sessionStart = LocalDateTime.of(timeSlot.getDate(), startTime);
        if (sessionStart.isBefore(LocalDateTime.now(AppTimeZone.ZONE).plusMinutes(minNoticeMinutes))) {
            double hours = minNoticeMinutes / 60.0;
            String hoursLabel = hours == Math.floor(hours) ? String.valueOf((int) hours) : String.valueOf(hours);
            throw new ValidationException(
                    String.format("Esta clase requiere reservarse con al menos %s horas de anticipación", hoursLabel));
        }
    }

    @Override
    public void validateNoOverlap(UUID timeSlotId, LocalTime startTime, LocalTime endTime) {
        List<Hold> overlappingHolds = holdRepository.findOverlapping(timeSlotId, startTime, endTime);
        if (!overlappingHolds.isEmpty()) {
            throw new ValidationException("Este horario ya fue seleccionado por otro alumno.");
        }

        List<Booking> overlappingBookings = bookingRepository
                .findActiveByTimeSlotId(timeSlotId, BookingStatusGroups.ACTIVE)
                .stream()
                .filter(b -> startTime.isBefore(b.getEndTime()) && endTime.isAfter(b.getStartTime()))
                .toList();

        if (!overlappingBookings.isEmpty()) {
            throw new ValidationException("Este horario ya fue reservado por otro alumno.");
        }
    }

    @Override
    public void validateSingleActiveHold(User student) {
        if (holdRepository.existsActiveHoldForStudent(student.getUserId(), LocalDateTime.now(AppTimeZone.ZONE))) {
            throw new ValidationException(
                    "Ya tenés una selección de horario en curso. Completá o cancelá esa reserva antes de elegir otra.");
        }
    }
}
