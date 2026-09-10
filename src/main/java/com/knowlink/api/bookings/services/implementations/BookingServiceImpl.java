package com.knowlink.api.bookings.services.implementations;

import com.knowlink.api.events.services.BookingEventPublisher;
import com.knowlink.api.exceptions.custom_exceptions.ResourceNotFoundException;
import com.knowlink.api.exceptions.custom_exceptions.ValidationException;
import com.knowlink.api.bookings.controllers.requests.CreateBookingRequest;
import com.knowlink.api.bookings.controllers.responses.BookingHistoryDetailResponse;
import com.knowlink.api.bookings.controllers.responses.BookingHistoryItemResponse;
import com.knowlink.api.bookings.controllers.responses.BookingConfirmationResponse;
import com.knowlink.api.bookings.controllers.responses.BookingResponse;
import com.knowlink.api.bookings.data.enums.BookingHistoryCategory;
import com.knowlink.api.bookings.data.enums.BookingStatus;
import com.knowlink.api.bookings.data.mappers.BookingHistoryCategoryMapper;
import com.knowlink.api.security.enums.Role;
import com.knowlink.api.bookings.data.mappers.BookingMapper;
import com.knowlink.api.bookings.data.models.Hold;
import com.knowlink.api.bookings.repositories.IHoldRepository;
import com.knowlink.api.bookings.services.interfaces.IBookingConfirmationTokenService;
import com.knowlink.api.bookings.services.interfaces.IBookingService;
import com.knowlink.api.bookings.utils.BookingConstants;
import com.knowlink.api.bookings.validations.IBookingValidationService;
import com.knowlink.api.shared.responses.PagedResponse;
import com.knowlink.api.shared.utils.AppTimeZone;
import com.knowlink.api.tutors.data.enums.CompensationType;
import com.knowlink.api.bookings.data.models.Booking;
import com.knowlink.api.tutors.data.models.TutorSubject;
import com.knowlink.api.bookings.repositories.IBookingRepository;
import com.knowlink.api.tutors.repositories.ITutorSubjectRepository;
import com.knowlink.api.users.data.models.User;
import com.knowlink.api.users.services.interfaces.IUserService;
import com.knowlink.api.students.repositories.IStudentProfileRepository;
import com.knowlink.api.students.data.models.StudentProfile;
import com.knowlink.api.bookings.events.SessionConfirmedEvent;
import com.knowlink.api.bookings.data.enums.BookingStatus;

import lombok.RequiredArgsConstructor;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class BookingServiceImpl implements IBookingService {

        private final IBookingRepository bookingRepository;
        private final ITutorSubjectRepository tutorSubjectRepository;
        private final IHoldRepository holdRepository;
        private final IBookingValidationService bookingValidationService;
        private final IUserService userService;
        private final BookingMapper bookingMapper;
        private final BookingEventPublisher bookingEventPublisher;
        private final ApplicationEventPublisher applicationEventPublisher;
        private final IBookingConfirmationTokenService confirmationTokenService;
        private final IStudentProfileRepository studentProfileRepository;

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

                bookingEventPublisher.publish(
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

        @Override
        @Transactional(readOnly = true)
        public PagedResponse<BookingHistoryItemResponse> getHistory(UUID userId, Role role,
                        BookingHistoryCategory category, int page, int size) {
                List<BookingStatus> statuses = BookingHistoryCategoryMapper.toStatuses(category);
                boolean upcoming = category.isUpcoming();
                int safePage = Math.max(page, 0);
                int safeSize = Math.min(Math.max(size, 1), BookingConstants.MAX_HISTORY_PAGE_SIZE);
                Pageable pageable = PageRequest.of(safePage, safeSize);

                Page<Booking> bookings = role == Role.STUDENT
                                ? (upcoming ? bookingRepository.findHistoryByStudentAsc(userId, statuses, pageable)
                                                : bookingRepository.findHistoryByStudentDesc(userId, statuses,
                                                                pageable))
                                : (upcoming ? bookingRepository.findHistoryByTutorAsc(userId, statuses, pageable)
                                                : bookingRepository.findHistoryByTutorDesc(userId, statuses, pageable));

                Map<UUID, String> studentProfilePictureByUserId = (role == Role.TUTOR
                                && !bookings.getContent().isEmpty())
                                                ? studentProfileRepository.findByUserIdIn(
                                                                bookings.getContent().stream()
                                                                                .map(b -> b.getStudent().getUserId())
                                                                                .collect(Collectors.toSet()))
                                                                .stream().collect(HashMap::new,
                                                                                (map, sp) -> map.put(sp.getUser()
                                                                                                .getUserId(),
                                                                                                sp.getProfilePictureUrl()),
                                                                                (map, other) -> map.putAll(other))
                                                : Map.of();

                return PagedResponse.from(bookings.map(
                                booking -> bookingMapper.toListItem(booking, userId, studentProfilePictureByUserId)));
        }

        @Override
        @Transactional(readOnly = true)
        public BookingHistoryDetailResponse getDetail(UUID userId, UUID bookingId) {
                Booking booking = findBookingOrThrow(bookingId);
                bookingValidationService.validateOwnership(booking, userId);

                String otherPartyProfilePictureUrl = resolveOtherPartyProfilePicture(booking, userId);
                return bookingMapper.toDetail(booking, userId, otherPartyProfilePictureUrl);
        }

        @Override
        @Transactional
        public BookingHistoryDetailResponse setVirtualLink(UUID tutorUserId, UUID bookingId,
                        String virtualSessionLink) {
                Booking booking = findBookingOrThrow(bookingId);
                bookingValidationService.validateCanSetVirtualLink(booking, tutorUserId);
                booking.setVirtualSessionLink(virtualSessionLink);
                bookingRepository.save(booking);

                // acá el viewer siempre es el tutor (la validación de arriba lo exige), así que
                // la otra parte siempre es el alumno
                String otherPartyProfilePictureUrl = resolveOtherPartyProfilePicture(booking, tutorUserId);
                return bookingMapper.toDetail(booking, tutorUserId, otherPartyProfilePictureUrl);
        }

        private String resolveOtherPartyProfilePicture(Booking booking, UUID viewerUserId) {
                boolean viewerIsStudent = booking.getStudent().getUserId().equals(viewerUserId);
                if (viewerIsStudent) {
                        return booking.getTutorSubject().getTutorProfile().getProfilePictureUrl();
                }
                return studentProfileRepository.findByUserId(booking.getStudent().getUserId())
                                .map(StudentProfile::getProfilePictureUrl)
                                .orElse(null);
        }

        private Booking findBookingOrThrow(UUID bookingId) {
                return bookingRepository.findById(bookingId)
                                .orElseThrow(() -> new ResourceNotFoundException(
                                                "BOOKING_NOT_FOUND",
                                                "La reserva no existe.",
                                                "Booking not found for id: " + bookingId));
        }

        @Override
        @Transactional
        public BookingConfirmationResponse confirmSession(UUID tutorUserId, UUID bookingId, String rawToken) {
                Booking booking = findBookingOrThrow(bookingId);
                bookingValidationService.validateCanConfirmSession(booking, tutorUserId);

                if (!confirmationTokenService.matches(rawToken, booking.getConfirmationToken())) {
                        booking.setConfirmationTokenAttempts(booking.getConfirmationTokenAttempts() + 1);
                        bookingRepository.save(booking);
                        throw new ValidationException("El código ingresado no es válido. Verificá con tu alumno");
                }

                booking.setBookingStatus(BookingStatus.COMPLETED);
                booking.setConfirmedAt(LocalDateTime.now(AppTimeZone.ZONE));
                bookingRepository.save(booking);

                applicationEventPublisher.publishEvent(new SessionConfirmedEvent(booking));

                return bookingMapper.toConfirmationResponse(booking);
        }
}