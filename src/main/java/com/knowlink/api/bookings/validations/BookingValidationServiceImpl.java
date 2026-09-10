package com.knowlink.api.bookings.validations;

import com.knowlink.api.exceptions.custom_exceptions.ResourceNotFoundException;
import com.knowlink.api.exceptions.custom_exceptions.UnauthorizedException;
import com.knowlink.api.exceptions.custom_exceptions.ValidationException;
import com.knowlink.api.shared.utils.AppTimeZone;
import com.knowlink.api.timeslot.data.models.TimeSlot;
import com.knowlink.api.bookings.data.enums.BookingStatus;
import com.knowlink.api.tutors.data.enums.Modality;
import com.knowlink.api.bookings.data.models.Booking;
import com.knowlink.api.tutors.data.models.TutorProfile;
import com.knowlink.api.bookings.repositories.IBookingRepository;
import com.knowlink.api.users.data.models.User;

import lombok.RequiredArgsConstructor;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class BookingValidationServiceImpl implements IBookingValidationService {

    private static final List<BookingStatus> ACTIVE_STATUSES = List.of(BookingStatus.PENDING, BookingStatus.BOOKED,
            BookingStatus.IN_PROGRESS);

    private static final Duration MAX_DAILY_SUBJECT_DURATION = Duration.ofHours(3);

    private final IBookingRepository bookingRepository;

    @Override
    public void validateModality(Modality requested, Modality subjectModality) {
        if (requested == Modality.BOTH) {
            throw new ValidationException("Debés elegir Virtual o Presencial para la clase, no ambas.");
        }

        boolean allowed = subjectModality == Modality.BOTH || subjectModality == requested;
        if (!allowed) {
            String label = subjectModality == Modality.VIRTUAL ? "virtual" : "presencial";
            throw new ValidationException(
                    String.format("Esta materia solo está disponible en modalidad %s.", label));
        }
    }

    @Override
    public void validateTimeWithinSlot(TimeSlot timeSlot, LocalTime startTime, LocalTime endTime) {
        if (!endTime.isAfter(startTime)) {
            throw new ValidationException("El horario de fin debe ser posterior al horario de inicio.");
        }
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
        LocalDateTime earliestBookable = LocalDateTime.now(AppTimeZone.ZONE).plusMinutes(minNoticeMinutes);

        if (sessionStart.isBefore(earliestBookable)) {
            double hours = minNoticeMinutes / 60.0;
            String hoursLabel = hours == Math.floor(hours)
                    ? String.valueOf((int) hours)
                    : String.valueOf(hours);
            throw new ValidationException(
                    String.format("Esta clase requiere reservarse con al menos %s horas de anticipación", hoursLabel));
        }
    }

    @Override
    public void validateNoOverlap(UUID timeSlotId, LocalTime startTime, LocalTime endTime) {
        List<Booking> activeBookings = bookingRepository.findActiveByTimeSlotId(timeSlotId, ACTIVE_STATUSES);

        boolean overlaps = activeBookings.stream()
                .anyMatch(b -> startTime.isBefore(b.getEndTime()) && endTime.isAfter(b.getStartTime()));

        if (overlaps) {
            throw new ValidationException("Este horario ya fue reservado o está siendo reservado por otro alumno.");
        }
    }

    @Override
    public void validateSingleActiveHold(User student) {
        boolean hasActiveHold = bookingRepository.existsByStudent_UserIdAndBookingStatus(
                student.getUserId(), BookingStatus.PENDING);

        if (hasActiveHold) {
            throw new ValidationException(
                    "Ya tenés una reserva pendiente de pago. Completala o esperá a que expire antes de reservar otra.");
        }
    }

    @Override
    public void validateDailySubjectCap(
            User student, UUID tutorSubjectId, LocalDate date, LocalTime startTime, LocalTime endTime) {

        List<Booking> sameDaySubjectBookings = bookingRepository.findActiveByStudentSubjectAndDate(
                student.getUserId(), tutorSubjectId, date, ACTIVE_STATUSES);

        Duration existing = sameDaySubjectBookings.stream()
                .map(b -> Duration.between(b.getStartTime(), b.getEndTime()))
                .reduce(Duration.ZERO, Duration::plus);

        Duration requested = Duration.between(startTime, endTime);

        if (existing.plus(requested).compareTo(MAX_DAILY_SUBJECT_DURATION) > 0) {
            throw new ValidationException(
                    "Superaste el máximo de 3 horas diarias de reserva para esta materia con este tutor.");
        }
    }

    @Override
    public void validateOwnership(Booking booking, UUID userId) {
        boolean isStudent = booking.getStudent().getUserId().equals(userId);
        boolean isTutor = booking.getTutor().getUserId().equals(userId);

        if (!isStudent && !isTutor) {
            // Mismo mensaje que "no existe" a propósito — no confirmar la
            // existencia de una reserva ajena a quien no tiene acceso a ella.
            throw new ResourceNotFoundException(
                    "BOOKING_NOT_FOUND",
                    "La reserva no existe.",
                    String.format("User %s tried to access booking %s without ownership", userId,
                            booking.getBookingId()));
        }
    }

    @Override
    public void validateCanSetVirtualLink(Booking booking, UUID tutorUserId) {
        if (!booking.getTutor().getUserId().equals(tutorUserId)) {
            throw new AccessDeniedException(
                    "No tenés permiso para modificar esta reserva.");
        }
        if (booking.getModality() != Modality.VIRTUAL) {
            throw new ValidationException("No se puede cargar un link de videollamada para una clase presencial.");
        }
        if (booking.getBookingStatus() != BookingStatus.BOOKED
                && booking.getBookingStatus() != BookingStatus.IN_PROGRESS) {
            throw new ValidationException(
                    "No se puede modificar el link de una clase que ya finalizó o fue cancelada.");
        }
    }
}
