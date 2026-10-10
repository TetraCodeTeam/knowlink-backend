package com.knowlink.api.claims.services.implementations;

import com.knowlink.api.bookings.data.enums.BookingStatus;
import com.knowlink.api.bookings.data.models.Booking;
import com.knowlink.api.bookings.repositories.IBookingRepository;
import com.knowlink.api.claims.controllers.requests.CreateClaimRequest;
import com.knowlink.api.claims.controllers.responses.ClaimAttachmentUrlResponse;
import com.knowlink.api.claims.controllers.responses.ClaimEligibilityResponse;
import com.knowlink.api.claims.controllers.responses.ClaimResponse;
import com.knowlink.api.claims.data.enums.ClaimBlockReason;
import com.knowlink.api.claims.data.enums.ClaimReason;
import com.knowlink.api.claims.data.enums.ClaimStatus;
import com.knowlink.api.claims.data.models.ClaimAttachment;
import com.knowlink.api.claims.data.models.SessionClaim;
import com.knowlink.api.claims.repositories.ClaimAttachmentRepository;
import com.knowlink.api.claims.repositories.SessionClaimRepository;
import com.knowlink.api.claims.utils.ClaimDeadlineCalculator;
import com.knowlink.api.exceptions.custom_exceptions.ClaimDeadlineExceededException;
import com.knowlink.api.exceptions.custom_exceptions.ClaimReasonNotAllowedException;
import com.knowlink.api.exceptions.custom_exceptions.DuplicateResourceException;
import com.knowlink.api.exceptions.custom_exceptions.ResourceNotFoundException;
import com.knowlink.api.exceptions.custom_exceptions.SessionAlreadyConfirmedException;
import com.knowlink.api.exceptions.custom_exceptions.SessionNotClaimableException;
import com.knowlink.api.exceptions.custom_exceptions.SessionNotFinalizedException;
import com.knowlink.api.exceptions.custom_exceptions.SessionNotParticipantException;
import com.knowlink.api.exceptions.custom_exceptions.ValidationException;
import com.knowlink.api.payments.services.FundsLedgerService;
import com.knowlink.api.materials.exception.FormatNotAllowedException;
import com.knowlink.api.materials.service.interfaces.ISupabaseStorageService;
import com.knowlink.api.security.enums.Role;
import com.knowlink.api.shared.utils.AppTimeZone;
import com.knowlink.api.users.data.models.User;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SessionClaimServiceImplTest {

    private static final LocalDate SESSION_DATE = LocalDate.of(2026, 9, 30);
    private static final LocalTime SESSION_END = LocalTime.of(10, 0);
    private static final LocalDateTime CLAIMABLE_UNTIL = LocalDateTime.of(2026, 10, 1, 10, 0);
    private static final LocalDateTime WITHIN_DEADLINE_NOW = LocalDateTime.of(2026, 10, 1, 9, 0);
    private static final LocalDateTime EXACT_DEADLINE_NOW = LocalDateTime.of(2026, 10, 1, 10, 0);
    private static final LocalDateTime AFTER_DEADLINE_NOW = LocalDateTime.of(2026, 10, 1, 10, 1);
    private static final LocalDateTime DURING_SESSION_NOW = LocalDateTime.of(2026, 9, 30, 9, 0);
    private static final String ACTIVE_CLAIM_MESSAGE = "Ya tenés una disputa activa para esta sesión";

    @Mock
    private IBookingRepository bookingRepository;

    @Mock
    private SessionClaimRepository sessionClaimRepository;

    @Mock
    private ClaimAttachmentRepository claimAttachmentRepository;

    @Mock
    private FundsLedgerService fundsLedgerService;

    @Mock
    private ISupabaseStorageService storageService;

    private SessionClaimServiceImpl service;

    private UUID studentId;
    private UUID tutorId;
    private Booking booking;

    @BeforeEach
    void setUp() {
        service = newService(WITHIN_DEADLINE_NOW);
        studentId = UUID.randomUUID();
        tutorId = UUID.randomUUID();
        User student = User.builder().userId(studentId).fullName("Student Test").build();
        User tutor = User.builder().userId(tutorId).fullName("Tutor Test").build();
        booking = Booking.builder()
                .bookingId(UUID.randomUUID())
                .sessionDate(SESSION_DATE)
                .startTime(LocalTime.of(8, 0))
                .endTime(SESSION_END)
                .bookingStatus(BookingStatus.NOT_CONFIRMED)
                .student(student)
                .tutor(tutor)
                .build();
        lenient().when(bookingRepository.findById(booking.getBookingId())).thenReturn(Optional.of(booking));
        lenient().when(bookingRepository.findByIdForUpdate(booking.getBookingId())).thenReturn(Optional.of(booking));
    }

    private SessionClaimServiceImpl newService(LocalDateTime now) {
        Clock clock = Clock.fixed(now.atZone(AppTimeZone.ZONE).toInstant(), AppTimeZone.ZONE);
        return new SessionClaimServiceImpl(
                bookingRepository,
                sessionClaimRepository,
                claimAttachmentRepository,
                fundsLedgerService,
                storageService,
                new ClaimDeadlineCalculator(clock, 24),
                3,
                5);
    }

    private void stubNoActiveClaim() {
        when(sessionClaimRepository.existsByBooking_BookingIdAndClaimant_UserIdAndStatus(
                eq(booking.getBookingId()), any(UUID.class), eq(ClaimStatus.OPEN))).thenReturn(false);
    }

    private void stubSaveAndFlush() {
        when(sessionClaimRepository.saveAndFlush(any(SessionClaim.class))).thenAnswer(invocation -> {
            SessionClaim claim = invocation.getArgument(0);
            claim.setClaimId(UUID.randomUUID());
            claim.setCreatedAt(WITHIN_DEADLINE_NOW);
            return claim;
        });
    }

    private MockMultipartFile pdfFile(String name) {
        return new MockMultipartFile("files", name, "application/pdf", "pdf-content".getBytes(StandardCharsets.UTF_8));
    }

    @Test
    @DisplayName("Crear - alumno dentro del plazo: 201 con OPEN, rol STUDENT y fondos suspendidos")
    void create_studentWithinDeadline_returnsOpenClaimAndSuspendsFunds() {
        stubNoActiveClaim();
        stubSaveAndFlush();

        ClaimResponse response = service.create(studentId, booking.getBookingId(),
                new CreateClaimRequest(ClaimReason.TUTOR_COULD_NOT_ATTEND, null), null);

        ArgumentCaptor<SessionClaim> captor = ArgumentCaptor.forClass(SessionClaim.class);
        verify(sessionClaimRepository).saveAndFlush(captor.capture());
        SessionClaim saved = captor.getValue();
        assertThat(saved.getStatus()).isEqualTo(ClaimStatus.OPEN);
        assertThat(saved.getClaimantRole()).isEqualTo(Role.STUDENT);
        assertThat(saved.getClaimant().getUserId()).isEqualTo(studentId);
        assertThat(saved.getReason()).isEqualTo(ClaimReason.TUTOR_COULD_NOT_ATTEND);
        assertThat(saved.getComment()).isNull();
        assertThat(saved.getActiveKey()).isEqualTo(booking.getBookingId() + ":" + studentId);
        verify(fundsLedgerService).markSuspendedByClaim(booking.getBookingId(), saved.getClaimId());
        verifyNoInteractions(storageService);

        assertThat(response.id()).isEqualTo(saved.getClaimId());
        assertThat(response.bookingId()).isEqualTo(booking.getBookingId());
        assertThat(response.status()).isEqualTo(ClaimStatus.OPEN);
        assertThat(response.claimantRole()).isEqualTo(Role.STUDENT);
        assertThat(response.attachments()).isEmpty();
    }

    @Test
    @DisplayName("Crear - tutor dentro del plazo: rol TUTOR y comentario normalizado")
    void create_tutorWithinDeadline_savesTrimmedComment() {
        stubNoActiveClaim();
        stubSaveAndFlush();

        ClaimResponse response = service.create(tutorId, booking.getBookingId(),
                new CreateClaimRequest(ClaimReason.I_COULD_NOT_ATTEND, "   late session   "), null);

        ArgumentCaptor<SessionClaim> captor = ArgumentCaptor.forClass(SessionClaim.class);
        verify(sessionClaimRepository).saveAndFlush(captor.capture());
        assertThat(captor.getValue().getClaimantRole()).isEqualTo(Role.TUTOR);
        assertThat(captor.getValue().getComment()).isEqualTo("late session");
        assertThat(response.comment()).isEqualTo("late session");
    }

    @Test
    @DisplayName("Crear - exactamente en el limite del plazo: reclamo aceptado")
    void create_atExactDeadline_returnsCreated() {
        service = newService(EXACT_DEADLINE_NOW);
        stubNoActiveClaim();
        stubSaveAndFlush();

        ClaimResponse response = service.create(studentId, booking.getBookingId(),
                new CreateClaimRequest(ClaimReason.TUTOR_COULD_NOT_ATTEND, null), null);

        assertThat(response.status()).isEqualTo(ClaimStatus.OPEN);
    }

    @Test
    @DisplayName("Crear - plazo vencido: 422 CLAIM_DEADLINE_EXCEEDED")
    void create_afterDeadline_throwsClaimDeadlineExceeded() {
        service = newService(AFTER_DEADLINE_NOW);

        assertThatThrownBy(() -> service.create(studentId, booking.getBookingId(),
                new CreateClaimRequest(ClaimReason.TUTOR_COULD_NOT_ATTEND, null), null))
                .isInstanceOf(ClaimDeadlineExceededException.class)
                .hasFieldOrPropertyWithValue("errorCode", "CLAIM_DEADLINE_EXCEEDED");

        verify(sessionClaimRepository, never()).saveAndFlush(any());
        verifyNoInteractions(fundsLedgerService);
    }

    @Test
    @DisplayName("Crear - sesion en curso (antes de la hora de fin): 422 SESSION_NOT_FINALIZED")
    void create_sessionStillInProgress_throwsSessionNotFinalized() {
        service = newService(DURING_SESSION_NOW);
        booking.setBookingStatus(BookingStatus.IN_PROGRESS);

        assertThatThrownBy(() -> service.create(studentId, booking.getBookingId(),
                new CreateClaimRequest(ClaimReason.TUTOR_COULD_NOT_ATTEND, null), null))
                .isInstanceOf(SessionNotFinalizedException.class)
                .hasFieldOrPropertyWithValue("errorCode", "SESSION_NOT_FINALIZED");

        verify(sessionClaimRepository, never()).saveAndFlush(any());
    }

    @Test
    @DisplayName("Crear - sesion terminada por horario pero sin estado final (BOOKED/IN_PROGRESS): reclamo aceptado")
    void create_endedByScheduleWithoutFinalStatus_returnsCreated() {
        booking.setBookingStatus(BookingStatus.IN_PROGRESS);
        stubNoActiveClaim();
        stubSaveAndFlush();

        ClaimResponse response = service.create(studentId, booking.getBookingId(),
                new CreateClaimRequest(ClaimReason.TUTOR_COULD_NOT_ATTEND, null), null);

        assertThat(response.status()).isEqualTo(ClaimStatus.OPEN);
    }

    @Test
    @DisplayName("Crear - sesion confirmada con token (COMPLETED): 422 SESSION_ALREADY_CONFIRMED")
    void create_sessionConfirmedWithToken_throwsSessionAlreadyConfirmed() {
        booking.setBookingStatus(BookingStatus.COMPLETED);
        booking.setConfirmedAt(LocalDateTime.of(2026, 9, 30, 9, 30));

        assertThatThrownBy(() -> service.create(studentId, booking.getBookingId(),
                new CreateClaimRequest(ClaimReason.TUTOR_COULD_NOT_ATTEND, null), null))
                .isInstanceOf(SessionAlreadyConfirmedException.class)
                .hasFieldOrPropertyWithValue("errorCode", "SESSION_ALREADY_CONFIRMED");

        verify(sessionClaimRepository, never()).saveAndFlush(any());
        verifyNoInteractions(fundsLedgerService);
    }

    @Test
    @DisplayName("Crear - sesion confirmada con token durante la clase: bloquea por confirmada, no por en curso")
    void create_confirmedWhileStillInProgress_throwsSessionAlreadyConfirmed() {
        service = newService(DURING_SESSION_NOW);
        booking.setBookingStatus(BookingStatus.COMPLETED);
        booking.setConfirmedAt(LocalDateTime.of(2026, 9, 30, 9, 30));

        assertThatThrownBy(() -> service.create(studentId, booking.getBookingId(),
                new CreateClaimRequest(ClaimReason.TUTOR_COULD_NOT_ATTEND, null), null))
                .isInstanceOf(SessionAlreadyConfirmedException.class);

        verify(sessionClaimRepository, never()).saveAndFlush(any());
    }

    @Test
    @DisplayName("Crear - sesion cancelada: 422 SESSION_NOT_CLAIMABLE")
    void create_cancelledSession_throwsSessionNotClaimable() {
        booking.setBookingStatus(BookingStatus.CANCELLED);

        assertThatThrownBy(() -> service.create(studentId, booking.getBookingId(),
                new CreateClaimRequest(ClaimReason.TUTOR_COULD_NOT_ATTEND, null), null))
                .isInstanceOf(SessionNotClaimableException.class)
                .hasFieldOrPropertyWithValue("errorCode", "SESSION_NOT_CLAIMABLE");

        verify(sessionClaimRepository, never()).saveAndFlush(any());
    }

    @Test
    @DisplayName("Crear - alumno reclama con motivo de tutor ausente: aceptado")
    void create_studentWithTutorCouldNotAttend_returnsCreated() {
        stubNoActiveClaim();
        stubSaveAndFlush();

        service.create(studentId, booking.getBookingId(),
                new CreateClaimRequest(ClaimReason.TUTOR_COULD_NOT_ATTEND, null), null);

        ArgumentCaptor<SessionClaim> captor = ArgumentCaptor.forClass(SessionClaim.class);
        verify(sessionClaimRepository).saveAndFlush(captor.capture());
        assertThat(captor.getValue().getReason()).isEqualTo(ClaimReason.TUTOR_COULD_NOT_ATTEND);
        assertThat(captor.getValue().getClaimantRole()).isEqualTo(Role.STUDENT);
    }

    @Test
    @DisplayName("Crear - tutor reclama con motivo de alumno ausente: aceptado")
    void create_tutorWithStudentCouldNotAttend_returnsCreated() {
        stubNoActiveClaim();
        stubSaveAndFlush();

        service.create(tutorId, booking.getBookingId(),
                new CreateClaimRequest(ClaimReason.STUDENT_COULD_NOT_ATTEND, null), null);

        ArgumentCaptor<SessionClaim> captor = ArgumentCaptor.forClass(SessionClaim.class);
        verify(sessionClaimRepository).saveAndFlush(captor.capture());
        assertThat(captor.getValue().getReason()).isEqualTo(ClaimReason.STUDENT_COULD_NOT_ATTEND);
        assertThat(captor.getValue().getClaimantRole()).isEqualTo(Role.TUTOR);
    }

    @Test
    @DisplayName("Crear - alumno con motivo de alumno ausente: 422 CLAIM_REASON_NOT_ALLOWED_FOR_ROLE")
    void create_studentWithStudentCouldNotAttend_throwsReasonNotAllowed() {
        assertThatThrownBy(() -> service.create(studentId, booking.getBookingId(),
                new CreateClaimRequest(ClaimReason.STUDENT_COULD_NOT_ATTEND, null), null))
                .isInstanceOf(ClaimReasonNotAllowedException.class)
                .hasFieldOrPropertyWithValue("errorCode", "CLAIM_REASON_NOT_ALLOWED_FOR_ROLE");

        verify(sessionClaimRepository, never()).saveAndFlush(any());
        verifyNoInteractions(fundsLedgerService);
    }

    @Test
    @DisplayName("Crear - tutor con motivo de tutor ausente: 422 CLAIM_REASON_NOT_ALLOWED_FOR_ROLE")
    void create_tutorWithTutorCouldNotAttend_throwsReasonNotAllowed() {
        assertThatThrownBy(() -> service.create(tutorId, booking.getBookingId(),
                new CreateClaimRequest(ClaimReason.TUTOR_COULD_NOT_ATTEND, null), null))
                .isInstanceOf(ClaimReasonNotAllowedException.class)
                .hasFieldOrPropertyWithValue("errorCode", "CLAIM_REASON_NOT_ALLOWED_FOR_ROLE");

        verify(sessionClaimRepository, never()).saveAndFlush(any());
    }

    @Test
    @DisplayName("Crear - usuario ajeno a la sesion: 403 SESSION_NOT_PARTICIPANT")
    void create_notParticipant_throwsSessionNotParticipant() {
        UUID outsiderId = UUID.randomUUID();

        assertThatThrownBy(() -> service.create(outsiderId, booking.getBookingId(),
                new CreateClaimRequest(ClaimReason.TUTOR_COULD_NOT_ATTEND, null), null))
                .isInstanceOf(SessionNotParticipantException.class)
                .hasFieldOrPropertyWithValue("errorCode", "SESSION_NOT_PARTICIPANT");

        verify(sessionClaimRepository, never()).saveAndFlush(any());
    }

    @Test
    @DisplayName("Crear - sesion inexistente: 404 SESSION_NOT_FOUND")
    void create_bookingNotFound_throwsResourceNotFound() {
        UUID missingId = UUID.randomUUID();
        when(bookingRepository.findByIdForUpdate(missingId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.create(studentId, missingId,
                new CreateClaimRequest(ClaimReason.TUTOR_COULD_NOT_ATTEND, null), null))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasFieldOrPropertyWithValue("errorCode", "SESSION_NOT_FOUND");
    }

    @Test
    @DisplayName("Crear - reclamo activo previo: 409 con mensaje exacto")
    void create_activeClaimExists_throwsDuplicateWithExactMessage() {
        when(sessionClaimRepository.existsByBooking_BookingIdAndClaimant_UserIdAndStatus(
                booking.getBookingId(), studentId, ClaimStatus.OPEN)).thenReturn(true);

        assertThatThrownBy(() -> service.create(studentId, booking.getBookingId(),
                new CreateClaimRequest(ClaimReason.TUTOR_COULD_NOT_ATTEND, null), null))
                .isInstanceOf(DuplicateResourceException.class)
                .hasFieldOrPropertyWithValue("errorCode", "ACTIVE_CLAIM_EXISTS")
                .hasFieldOrPropertyWithValue("userMessage", ACTIVE_CLAIM_MESSAGE);

        verify(sessionClaimRepository, never()).saveAndFlush(any());
    }

    @Test
    @DisplayName("Crear - violacion de clave unica por carrera: 409 con mensaje exacto")
    void create_uniqueKeyViolation_mapsToDuplicateWithExactMessage() {
        stubNoActiveClaim();
        when(sessionClaimRepository.saveAndFlush(any(SessionClaim.class)))
                .thenThrow(new DataIntegrityViolationException("uk_session_claim_active_key"));

        assertThatThrownBy(() -> service.create(studentId, booking.getBookingId(),
                new CreateClaimRequest(ClaimReason.TUTOR_COULD_NOT_ATTEND, null), null))
                .isInstanceOf(DuplicateResourceException.class)
                .hasFieldOrPropertyWithValue("errorCode", "ACTIVE_CLAIM_EXISTS")
                .hasFieldOrPropertyWithValue("userMessage", ACTIVE_CLAIM_MESSAGE);

        verify(fundsLedgerService, never()).markSuspendedByClaim(any(), any());
    }

    @Test
    @DisplayName("Crear - mas de 3 adjuntos: 400 VALIDATION_ERROR")
    void create_moreThanMaxAttachments_throwsValidation() {
        stubNoActiveClaim();
        List<MultipartFile> files = List.of(pdfFile("a.pdf"), pdfFile("b.pdf"), pdfFile("c.pdf"), pdfFile("d.pdf"));

        assertThatThrownBy(() -> service.create(studentId, booking.getBookingId(),
                new CreateClaimRequest(ClaimReason.TUTOR_COULD_NOT_ATTEND, null), files))
                .isInstanceOf(ValidationException.class)
                .hasMessage("No podés adjuntar más de 3 archivos.");

        verify(sessionClaimRepository, never()).saveAndFlush(any());
    }

    @Test
    @DisplayName("Crear - adjunto vacio: 400 VALIDATION_ERROR")
    void create_emptyAttachment_throwsValidation() {
        stubNoActiveClaim();
        MockMultipartFile empty = new MockMultipartFile("files", "a.pdf", "application/pdf", new byte[0]);

        assertThatThrownBy(() -> service.create(studentId, booking.getBookingId(),
                new CreateClaimRequest(ClaimReason.TUTOR_COULD_NOT_ATTEND, null), List.of(empty)))
                .isInstanceOf(ValidationException.class)
                .hasMessage("El archivo adjunto no puede estar vacío.");

        verify(sessionClaimRepository, never()).saveAndFlush(any());
    }

    @Test
    @DisplayName("Crear - adjunto mayor a 5MB: 400 VALIDATION_ERROR")
    void create_oversizeAttachment_throwsValidation() {
        stubNoActiveClaim();
        MockMultipartFile big = new MockMultipartFile("files", "big.pdf", "application/pdf", new byte[6 * 1024 * 1024]);

        assertThatThrownBy(() -> service.create(studentId, booking.getBookingId(),
                new CreateClaimRequest(ClaimReason.TUTOR_COULD_NOT_ATTEND, null), List.of(big)))
                .isInstanceOf(ValidationException.class)
                .hasMessage("Cada archivo adjunto no puede superar los 5MB.");
    }

    @Test
    @DisplayName("Crear - formato no permitido: 400 FORMAT_NOT_ALLOWED")
    void create_disallowedFormat_throwsFormatNotAllowed() {
        stubNoActiveClaim();
        MockMultipartFile zip = new MockMultipartFile("files", "evidence.zip", "application/zip", "zip".getBytes(StandardCharsets.UTF_8));

        assertThatThrownBy(() -> service.create(studentId, booking.getBookingId(),
                new CreateClaimRequest(ClaimReason.TUTOR_COULD_NOT_ATTEND, null), List.of(zip)))
                .isInstanceOf(FormatNotAllowedException.class)
                .hasMessage("El formato del archivo no está permitido");

        verify(sessionClaimRepository, never()).saveAndFlush(any());
    }

    @Test
    @DisplayName("Crear - adjuntos validos: se suben al bucket claimEvidence y se persisten")
    void create_allowedAttachments_uploadAndPersist() {
        stubNoActiveClaim();
        stubSaveAndFlush();
        when(storageService.upload(any(MultipartFile.class), eq("claimEvidence"), eq(booking.getBookingId().toString())))
                .thenAnswer(invocation -> "claimEvidence/" + booking.getBookingId() + "/"
                        + invocation.getArgument(0, MultipartFile.class).getOriginalFilename());
        when(claimAttachmentRepository.saveAll(any())).thenAnswer(invocation -> invocation.getArgument(0));

        ClaimResponse response = service.create(studentId, booking.getBookingId(),
                new CreateClaimRequest(ClaimReason.TUTOR_COULD_NOT_ATTEND, null),
                List.of(pdfFile("evidence.pdf"),
                        new MockMultipartFile("files", "photo.png", "image/png", "png".getBytes(StandardCharsets.UTF_8))));

        verify(storageService, times(2)).upload(any(MultipartFile.class), eq("claimEvidence"), eq(booking.getBookingId().toString()));
        ArgumentCaptor<Iterable<ClaimAttachment>> captor = ArgumentCaptor.forClass(Iterable.class);
        verify(claimAttachmentRepository).saveAll(captor.capture());
        List<ClaimAttachment> persisted = new ArrayList<>();
        captor.getValue().forEach(persisted::add);
        assertThat(persisted).hasSize(2);
        assertThat(persisted).extracting(ClaimAttachment::getOriginalFileName).containsExactly("evidence.pdf", "photo.png");
        assertThat(response.attachments()).hasSize(2);
        assertThat(response.attachments()).extracting(a -> a.fileName()).containsExactly("evidence.pdf", "photo.png");
        verify(fundsLedgerService).markSuspendedByClaim(eq(booking.getBookingId()), any(UUID.class));
    }

    @Test
    @DisplayName("Crear - falla al subir un adjunto: limpia los subidos y propaga el error")
    void create_uploadFailure_cleansUpUploadedAndRethrows() {
        stubNoActiveClaim();
        stubSaveAndFlush();
        when(storageService.upload(any(MultipartFile.class), anyString(), anyString()))
                .thenReturn("claimEvidence/path-1")
                .thenThrow(new RuntimeException("storage down"));

        assertThatThrownBy(() -> service.create(studentId, booking.getBookingId(),
                new CreateClaimRequest(ClaimReason.TUTOR_COULD_NOT_ATTEND, null),
                List.of(pdfFile("a.pdf"), pdfFile("b.pdf"))))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("storage down");

        verify(storageService).delete("claimEvidence", "claimEvidence/path-1");
        verify(fundsLedgerService, never()).markSuspendedByClaim(any(), any());
    }

    @Test
    @DisplayName("Crear - falla al retener fondos: limpia todos los adjuntos y propaga el error")
    void create_retentionFailure_cleansUpAllUploadsAndRethrows() {
        stubNoActiveClaim();
        stubSaveAndFlush();
        when(storageService.upload(any(MultipartFile.class), anyString(), anyString()))
                .thenReturn("claimEvidence/path-1");
        when(claimAttachmentRepository.saveAll(any())).thenAnswer(invocation -> invocation.getArgument(0));
        doThrow(new RuntimeException("ledger down")).when(fundsLedgerService)
                .markSuspendedByClaim(any(), any());

        assertThatThrownBy(() -> service.create(studentId, booking.getBookingId(),
                new CreateClaimRequest(ClaimReason.TUTOR_COULD_NOT_ATTEND, null), List.of(pdfFile("a.pdf"))))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("ledger down");

        verify(storageService).delete("claimEvidence", "claimEvidence/path-1");
    }

    @Test
    @DisplayName("Elegibilidad - dentro del plazo sin reclamo: puede reclamar hasta la fecha limite")
    void getEligibility_canClaimTrue_returnsClaimableUntil() {
        stubNoActiveClaim();

        ClaimEligibilityResponse eligibility = service.getEligibility(studentId, booking.getBookingId());

        assertThat(eligibility.canClaim()).isTrue();
        assertThat(eligibility.claimableUntil()).isEqualTo(CLAIMABLE_UNTIL);
        assertThat(eligibility.blockReason()).isNull();
    }

    @Test
    @DisplayName("Elegibilidad - reclamo activo: bloqueo ACTIVE_CLAIM_EXISTS")
    void getEligibility_activeClaim_blocksWithActiveClaimExists() {
        when(sessionClaimRepository.existsByBooking_BookingIdAndClaimant_UserIdAndStatus(
                booking.getBookingId(), studentId, ClaimStatus.OPEN)).thenReturn(true);

        ClaimEligibilityResponse eligibility = service.getEligibility(studentId, booking.getBookingId());

        assertThat(eligibility.canClaim()).isFalse();
        assertThat(eligibility.blockReason()).isEqualTo(ClaimBlockReason.ACTIVE_CLAIM_EXISTS);
        assertThat(eligibility.claimableUntil()).isEqualTo(CLAIMABLE_UNTIL);
    }

    @Test
    @DisplayName("Elegibilidad - plazo vencido: bloqueo CLAIM_DEADLINE_EXCEEDED")
    void getEligibility_expired_blocksWithClaimDeadlineExceeded() {
        service = newService(AFTER_DEADLINE_NOW);

        ClaimEligibilityResponse eligibility = service.getEligibility(studentId, booking.getBookingId());

        assertThat(eligibility.canClaim()).isFalse();
        assertThat(eligibility.blockReason()).isEqualTo(ClaimBlockReason.CLAIM_DEADLINE_EXCEEDED);
        assertThat(eligibility.claimableUntil()).isEqualTo(CLAIMABLE_UNTIL);
    }

    @Test
    @DisplayName("Elegibilidad - sesion en curso: bloqueo SESSION_NOT_FINALIZED sin fecha")
    void getEligibility_sessionNotEnded_blocksWithoutClaimableUntil() {
        service = newService(DURING_SESSION_NOW);
        booking.setBookingStatus(BookingStatus.IN_PROGRESS);

        ClaimEligibilityResponse eligibility = service.getEligibility(studentId, booking.getBookingId());

        assertThat(eligibility.canClaim()).isFalse();
        assertThat(eligibility.blockReason()).isEqualTo(ClaimBlockReason.SESSION_NOT_FINALIZED);
        assertThat(eligibility.claimableUntil()).isNull();
    }

    @Test
    @DisplayName("Elegibilidad - sesion confirmada con token: bloqueo SESSION_ALREADY_CONFIRMED sin fecha")
    void getEligibility_sessionConfirmed_blocksWithAlreadyConfirmed() {
        booking.setBookingStatus(BookingStatus.COMPLETED);
        booking.setConfirmedAt(LocalDateTime.of(2026, 9, 30, 9, 30));

        ClaimEligibilityResponse eligibility = service.getEligibility(studentId, booking.getBookingId());

        assertThat(eligibility.canClaim()).isFalse();
        assertThat(eligibility.blockReason()).isEqualTo(ClaimBlockReason.SESSION_ALREADY_CONFIRMED);
        assertThat(eligibility.claimableUntil()).isNull();
    }

    @Test
    @DisplayName("Elegibilidad - estado que no admite reclamos (resuelto por fondos): bloqueo SESSION_NOT_CLAIMABLE")
    void getEligibility_nonClaimableStatus_blocksWithNotClaimable() {
        booking.setBookingStatus(BookingStatus.NOT_FULFILLED_BY_TUTOR);

        ClaimEligibilityResponse eligibility = service.getEligibility(studentId, booking.getBookingId());

        assertThat(eligibility.canClaim()).isFalse();
        assertThat(eligibility.blockReason()).isEqualTo(ClaimBlockReason.SESSION_NOT_CLAIMABLE);
        assertThat(eligibility.claimableUntil()).isNull();
    }

    @Test
    @DisplayName("Elegibilidad - devuelve los motivos permitidos segun el rol")
    void getEligibility_returnsAllowedReasonsByRole() {
        stubNoActiveClaim();

        ClaimEligibilityResponse student = service.getEligibility(studentId, booking.getBookingId());
        ClaimEligibilityResponse tutor = service.getEligibility(tutorId, booking.getBookingId());

        assertThat(student.allowedReasons())
                .containsExactly(ClaimReason.TUTOR_COULD_NOT_ATTEND, ClaimReason.I_COULD_NOT_ATTEND);
        assertThat(tutor.allowedReasons())
                .containsExactly(ClaimReason.STUDENT_COULD_NOT_ATTEND, ClaimReason.I_COULD_NOT_ATTEND);
    }

    @Test
    @DisplayName("Elegibilidad - usuario ajeno: sin motivos permitidos")
    void getEligibility_notParticipant_hasNoAllowedReasons() {
        ClaimEligibilityResponse eligibility = service.getEligibility(UUID.randomUUID(), booking.getBookingId());

        assertThat(eligibility.blockReason()).isEqualTo(ClaimBlockReason.SESSION_NOT_PARTICIPANT);
        assertThat(eligibility.allowedReasons()).isEmpty();
    }

    @Test
    @DisplayName("hasActiveClaim - delega en el repositorio por reserva (cualquiera de las partes)")
    void hasActiveClaim_delegatesToRepository() {
        when(sessionClaimRepository.existsByBooking_BookingIdAndStatus(booking.getBookingId(), ClaimStatus.OPEN))
                .thenReturn(true);

        assertThat(service.hasActiveClaim(booking.getBookingId())).isTrue();
    }

    @Test
    @DisplayName("Listado - evalua todo el lote con una sola consulta (sin N+1)")
    void evaluateForListing_batchesSingleQuery() {
        UUID otherBookingId = UUID.randomUUID();
        Booking other = Booking.builder()
                .bookingId(otherBookingId)
                .sessionDate(SESSION_DATE)
                .startTime(LocalTime.of(8, 0))
                .endTime(SESSION_END)
                .bookingStatus(BookingStatus.NOT_CONFIRMED)
                .student(booking.getStudent())
                .tutor(booking.getTutor())
                .build();
        when(sessionClaimRepository.findClaimedBookingIdsByUser(
                eq(studentId), eq(ClaimStatus.OPEN), any()))
                .thenReturn(Set.of(booking.getBookingId()));

        var result = service.evaluateForListing(studentId, List.of(booking, other));

        verify(sessionClaimRepository).findClaimedBookingIdsByUser(eq(studentId), eq(ClaimStatus.OPEN), any());
        verify(sessionClaimRepository, never()).existsByBooking_BookingIdAndClaimant_UserIdAndStatus(any(), any(), any());
        assertThat(result).hasSize(2);
        assertThat(result.get(booking.getBookingId()).canClaim()).isFalse();
        assertThat(result.get(booking.getBookingId()).blockReason()).isEqualTo(ClaimBlockReason.ACTIVE_CLAIM_EXISTS);
        assertThat(result.get(otherBookingId).canClaim()).isTrue();
    }

    @Test
    @DisplayName("Adjuntos - participante: devuelve URL firmada de lectura")
    void getAttachments_participant_returnsSignedUrls() {
        UUID claimId = UUID.randomUUID();
        SessionClaim claim = SessionClaim.builder().claimId(claimId).booking(booking).build();
        when(sessionClaimRepository.findByClaimIdAndBooking_BookingId(claimId, booking.getBookingId()))
                .thenReturn(Optional.of(claim));
        ClaimAttachment attachment = ClaimAttachment.builder()
                .attachmentId(UUID.randomUUID())
                .claim(claim)
                .storagePath("claimEvidence/path-1")
                .originalFileName("evidence.pdf")
                .contentType("application/pdf")
                .sizeInBytes(123L)
                .build();
        when(claimAttachmentRepository.findByClaim_ClaimId(claimId)).thenReturn(List.of(attachment));
        when(storageService.generateSignedUrl("claimEvidence", "claimEvidence/path-1", 3600))
                .thenReturn("https://signed.url/evidence");

        List<ClaimAttachmentUrlResponse> attachments = service.getAttachments(
                studentId, Role.STUDENT, booking.getBookingId(), claimId);

        assertThat(attachments).hasSize(1);
        assertThat(attachments.get(0).fileName()).isEqualTo("evidence.pdf");
        assertThat(attachments.get(0).sizeBytes()).isEqualTo(123L);
        assertThat(attachments.get(0).signedUrl()).isEqualTo("https://signed.url/evidence");
    }

    @Test
    @DisplayName("Adjuntos - administrador tambien puede leer los adjuntos")
    void getAttachments_admin_returnsSignedUrls() {
        UUID claimId = UUID.randomUUID();
        SessionClaim claim = SessionClaim.builder().claimId(claimId).booking(booking).build();
        when(sessionClaimRepository.findByClaimIdAndBooking_BookingId(claimId, booking.getBookingId()))
                .thenReturn(Optional.of(claim));
        when(claimAttachmentRepository.findByClaim_ClaimId(claimId)).thenReturn(List.of());

        List<ClaimAttachmentUrlResponse> attachments = service.getAttachments(
                UUID.randomUUID(), Role.ADMIN, booking.getBookingId(), claimId);

        assertThat(attachments).isEmpty();
    }

    @Test
    @DisplayName("Adjuntos - usuario ajeno: 403 SESSION_NOT_PARTICIPANT")
    void getAttachments_notParticipant_throwsSessionNotParticipant() {
        UUID claimId = UUID.randomUUID();
        UUID outsiderId = UUID.randomUUID();

        assertThatThrownBy(() -> service.getAttachments(outsiderId, Role.STUDENT, booking.getBookingId(), claimId))
                .isInstanceOf(SessionNotParticipantException.class)
                .hasFieldOrPropertyWithValue("errorCode", "SESSION_NOT_PARTICIPANT");
    }

    @Test
    @DisplayName("Adjuntos - reclamo inexistente: 404 CLAIM_NOT_FOUND")
    void getAttachments_claimNotFound_throwsResourceNotFound() {
        UUID claimId = UUID.randomUUID();
        when(sessionClaimRepository.findByClaimIdAndBooking_BookingId(claimId, booking.getBookingId()))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getAttachments(studentId, Role.STUDENT, booking.getBookingId(), claimId))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasFieldOrPropertyWithValue("errorCode", "CLAIM_NOT_FOUND");
    }
}