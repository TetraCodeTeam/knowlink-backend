package com.knowlink.api.bookings.services.implementations;

import com.knowlink.api.bookings.data.models.Booking;
import com.knowlink.api.bookings.repositories.IBookingRepository;
import com.knowlink.api.bookings.utils.BookingConstants;
import com.knowlink.api.bookings.services.interfaces.IStudentScheduleValidationService;
import com.knowlink.api.exceptions.custom_exceptions.ValidationException;
import com.knowlink.api.users.data.models.User;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class StudentScheduleValidationServiceImpl implements IStudentScheduleValidationService {

    private final IBookingRepository bookingRepository;

    @Override
    public void validateNoTimeConflict(User student, LocalDate date, LocalTime startTime, LocalTime endTime) {
        List<Booking> sameDayBookings = bookingRepository.findActiveByStudentAndDate(
                student.getUserId(), date, BookingConstants.ACTIVE_STATUSES);

        Optional<Booking> conflict = sameDayBookings.stream()
                .filter(b -> startTime.isBefore(b.getEndTime()) && endTime.isAfter(b.getStartTime()))
                .findFirst();

        if (conflict.isPresent()) {
            String tutorName = conflict.get().getTutor().getFullName();
            throw new ValidationException(String.format(
                    "Ya tenés una clase reservada con %s en ese horario. Elegí un horario distinto.", tutorName));
        }
    }
}
