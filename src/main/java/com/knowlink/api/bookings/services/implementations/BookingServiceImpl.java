package com.knowlink.api.bookings.services.implementations;

import com.knowlink.api.events.services.BookingEventPublisher;
import com.knowlink.api.exceptions.custom_exceptions.ResourceNotFoundException;
import com.knowlink.api.exceptions.custom_exceptions.ValidationException;
import com.knowlink.api.bookings.controllers.requests.CreateBookingRequest;
import com.knowlink.api.bookings.controllers.responses.BookingResponse;
import com.knowlink.api.bookings.data.mappers.BookingMapper;
import com.knowlink.api.bookings.data.models.Hold;
import com.knowlink.api.bookings.repositories.IHoldRepository;
import com.knowlink.api.bookings.services.interfaces.IBookingService;
import com.knowlink.api.bookings.utils.BookingConstants;
import com.knowlink.api.bookings.validations.IBookingValidationService;
import com.knowlink.api.shared.utils.AppTimeZone;
import com.knowlink.api.tutors.data.enums.CompensationType;
import com.knowlink.api.bookings.data.models.Booking;
import com.knowlink.api.tutors.data.models.TutorSubject;
import com.knowlink.api.bookings.repositories.IBookingRepository;
import com.knowlink.api.tutors.repositories.ITutorSubjectRepository;
import com.knowlink.api.users.data.models.User;
import com.knowlink.api.users.services.interfaces.IUserService;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class BookingServiceImpl implements IBookingService {

        private final IBookingRepository bookingRepository;
        private final ITutorSubjectRepository tutorSubjectRepository;
        private final IHoldRepository holdRepository;
        private final IBookingValidationService bookingValidationService;
        private final IUserService userService;
        private final BookingMapper bookingMapper;
        private final BookingEventPublisher eventPublisher;

        @Override
        @Transactional
        public BookingResponse createBooking(UUID studentUserId, CreateBookingRequest request) {
                User student = userService.findByIdOrThrowException(studentUserId);

                UUID timeSlotId = UUID.fromString(request.bookingSlotId().split("__")[0]);
                LocalTime startTime = request.start().atZone(AppTimeZone.ZONE).toLocalTime();
                LocalTime endTime = request.end().atZone(AppTimeZone.ZONE).toLocalTime();

                // Lockeamos la fila del Hold antes de leerla, para que no compita contra el
                // job de expiración (BookingHoldExpirationJob) que también puede estar
                // intentando borrar este mismo hold en simultáneo.
                Hold hold = holdRepository
                                .findActiveHoldForWindowForUpdate(studentUserId, timeSlotId, startTime, endTime)
                                .orElseThrow(() -> new ResourceNotFoundException(
                                                "HOLD_NOT_FOUND",
                                                "Tu selección de horario expiró o ya no es válida. Elegí un horario nuevamente.",
                                                "No active hold for student " + studentUserId + " on timeSlot "
                                                                + timeSlotId));

                if (hold.getExpiresAt().isBefore(LocalDateTime.now(AppTimeZone.ZONE))) {
                        holdRepository.delete(hold);
                        throw new ValidationException(
                                        "Tu tiempo para completar la reserva expiró. Elegí un horario nuevamente.");
                }

                TutorSubject tutorSubject = tutorSubjectRepository.findById(request.tutorSubjectId())
                                .orElseThrow(() -> new ResourceNotFoundException(
                                                "TUTOR_SUBJECT_NOT_FOUND",
                                                "La materia seleccionada no existe.",
                                                "TutorSubject not found for id: " + request.tutorSubjectId()));

                bookingValidationService.validateModality(request.modality(), tutorSubject.getModality());
                bookingValidationService.validateDailySubjectCap(
                                student, tutorSubject.getTutorSubjectId(), hold.getTimeSlot().getDate(), startTime,
                                endTime);

                boolean isFree = tutorSubject.getCompensationType() == CompensationType.FREE;
                BigDecimal amount = isFree ? BigDecimal.ZERO : calculateAmount(tutorSubject, startTime, endTime);

                Booking booking = bookingMapper.toEntity(hold, tutorSubject, student, startTime, endTime, amount,
                                request);

                bookingRepository.save(booking);
                holdRepository.delete(hold); // el hold se "consume" al confirmarse la reserva

                eventPublisher.publish(
                                tutorSubject.getTutorProfile().getTutorProfileId(),
                                timeSlotId,
                                "RESERVED",
                                request.start(),
                                request.end());

                return bookingMapper.toResponse(booking);
        }

        private BigDecimal calculateAmount(TutorSubject tutorSubject, LocalTime startTime, LocalTime endTime) {
                long minutes = Duration.between(startTime, endTime).toMinutes();
                BigDecimal hours = BigDecimal.valueOf(minutes).divide(BigDecimal.valueOf(60));
                BigDecimal base = tutorSubject.getPricePerHour().multiply(hours);
                return base.multiply(BigDecimal.ONE.add(BookingConstants.SERVICE_FEE_RATE)).setScale(2,
                                RoundingMode.HALF_UP);
        }
}