package com.knowlink.api.admin.claims;

import com.knowlink.api.admin.claims.controllers.requests.ResolveSessionClaimRequest;
import com.knowlink.api.admin.claims.controllers.responses.ClaimContactInfoResponse;
import com.knowlink.api.admin.claims.controllers.responses.SessionClaimDetailResponse;
import com.knowlink.api.admin.claims.controllers.responses.SessionClaimListItemResponse;
import com.knowlink.api.admin.claims.controllers.responses.SessionClaimListResponse;
import com.knowlink.api.admin.claims.data.enums.ClaimFavoredParty;
import com.knowlink.api.admin.claims.events.SessionClaimResolvedEvent;
import com.knowlink.api.admin.claims.repositories.IAdminSessionClaimRepository;
import com.knowlink.api.admin.claims.services.implementations.AdminSessionClaimServiceImpl;
import com.knowlink.api.admin.common.DateRangeResolver;
import com.knowlink.api.admin.common.audit.AdminAuditAction;
import com.knowlink.api.admin.common.audit.IAdminAuditService;
import com.knowlink.api.bookings.data.enums.BookingStatus;
import com.knowlink.api.bookings.data.models.Booking;
import com.knowlink.api.bookings.repositories.IBookingRepository;
import com.knowlink.api.claims.data.enums.AutomaticOutcome;
import com.knowlink.api.claims.data.enums.ClaimReason;
import com.knowlink.api.claims.data.enums.ClaimStatus;
import com.knowlink.api.claims.data.models.SessionClaim;
import com.knowlink.api.exceptions.custom_exceptions.BusinessConflictException;
import com.knowlink.api.exceptions.custom_exceptions.FundsExecutionException;
import com.knowlink.api.exceptions.custom_exceptions.NotAssignedToAdminException;
import com.knowlink.api.exceptions.custom_exceptions.ResourceNotFoundException;
import com.knowlink.api.exceptions.custom_exceptions.ValidationException;
import com.knowlink.api.payments.data.models.FundsTransfer;
import com.knowlink.api.payments.domain.FundsRecipient;
import com.knowlink.api.payments.domain.FundsStatus;
import com.knowlink.api.payments.repositories.IFundsTransferRepository;
import com.knowlink.api.payments.services.FundsResolutionService;
import com.knowlink.api.security.enums.Role;
import com.knowlink.api.shared.utils.AppTimeZone;
import com.knowlink.api.students.repositories.IStudentProfileRepository;
import com.knowlink.api.tutors.data.models.Career;
import com.knowlink.api.tutors.data.models.Subject;
import com.knowlink.api.tutors.data.models.TutorSubject;
import com.knowlink.api.tutors.repositories.ITutorProfileRepository;
import com.knowlink.api.users.data.models.User;
import com.knowlink.api.users.repositories.IUserRepository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AdminSessionClaimServiceImplTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-10-28T12:00:00Z"), AppTimeZone.ZONE);

    @Mock
    private IAdminSessionClaimRepository adminSessionClaimRepository;

    @Mock
    private IFundsTransferRepository fundsTransferRepository;

    @Mock
    private IBookingRepository bookingRepository;

    @Mock
    private IUserRepository userRepository;

    @Mock
    private ITutorProfileRepository tutorProfileRepository;

    @Mock
    private IStudentProfileRepository studentProfileRepository;

    @Mock
    private FundsResolutionService fundsResolutionService;

    @Mock
    private IAdminAuditService adminAuditService;

    @Mock
    private ApplicationEventPublisher applicationEventPublisher;

    private AdminSessionClaimServiceImpl service;

    private UUID adminId;
    private UUID otherAdminId;
    private UUID bookingId;
    private UUID claimId;

    private User admin;
    private User otherAdmin;
    private User tutor;
    private User student;
    private Booking booking;
    private SessionClaim claim;

    @BeforeEach
    void setUp() {
        adminId = UUID.randomUUID();
        otherAdminId = UUID.randomUUID();
        bookingId = UUID.randomUUID();
        claimId = UUID.randomUUID();

        admin = User.builder()
                .userId(adminId).fullName("Admin Uno").email("admin.uno@test.com").role(Role.ADMIN).build();
        otherAdmin = User.builder()
                .userId(otherAdminId).fullName("Admin Dos").email("admin.dos@test.com").role(Role.ADMIN).build();
        tutor = User.builder()
                .userId(UUID.randomUUID()).fullName("Tutor Test").email("tutor@test.com")
                .phoneNumber("3511234567").dni("30111222").role(Role.TUTOR).build();
        student = User.builder()
                .userId(UUID.randomUUID()).fullName("Alumno Test").email("alumno@test.com")
                .phoneNumber("3517654321").dni("40111222").role(Role.STUDENT).build();

        Career career = Career.builder().careerId(UUID.randomUUID()).name("Licenciatura en Sistemas").build();
        Subject subject = Subject.builder().subjectId(UUID.randomUUID()).name("Algoritmos").career(career).build();
        TutorSubject tutorSubject = TutorSubject.builder()
                .tutorSubjectId(UUID.randomUUID()).subject(subject).build();

        booking = Booking.builder()
                .bookingId(bookingId)
                .amount(new BigDecimal("1000.00"))
                .sessionDate(LocalDate.of(2026, 10, 20))
                .startTime(LocalTime.of(10, 0))
                .endTime(LocalTime.of(11, 0))
                .bookingStatus(BookingStatus.NOT_CONFIRMED)
                .tutorSubject(tutorSubject)
                .tutor(tutor)
                .student(student)
                .build();

        claim = SessionClaim.builder()
                .claimId(claimId)
                .booking(booking)
                .claimant(student)
                .claimantRole(Role.STUDENT)
                .reason(ClaimReason.TUTOR_COULD_NOT_ATTEND)
                .comment("Esperé quince minutos")
                .status(ClaimStatus.IN_REVIEW)
                .disputeId(claimId)
                .automaticOutcome(AutomaticOutcome.TUTOR_ABSENT_REFUND_TO_STUDENT)
                .assignedAdmin(admin)
                .createdAt(LocalDateTime.of(2026, 10, 20, 12, 0))
                .build();

        service = new AdminSessionClaimServiceImpl(
                adminSessionClaimRepository,
                fundsTransferRepository,
                bookingRepository,
                userRepository,
                tutorProfileRepository,
                studentProfileRepository,
                fundsResolutionService,
                adminAuditService,
                new DateRangeResolver(CLOCK),
                applicationEventPublisher,
                CLOCK);
    }

    private FundsTransfer suspendedTransfer() {
        return FundsTransfer.builder()
                .fundsTransferId(UUID.randomUUID())
                .booking(booking)
                .originalAmount(new BigDecimal("1000.00"))
                .transferredAmount(BigDecimal.ZERO)
                .recipient(FundsRecipient.TUTOR)
                .concept("Fondos retenidos para reserva")
                .fundsStatus(FundsStatus.SUSPENDED_BY_CLAIM)
                .blockingClaimId(claimId)
                .build();
    }

    private void stubDetail() {
        when(adminSessionClaimRepository.findByIdWithDetails(claimId)).thenReturn(Optional.of(claim));
        when(adminSessionClaimRepository.findDisputeClaims(claimId)).thenReturn(List.of(claim));
        when(fundsTransferRepository.findByBooking_BookingId(bookingId))
                .thenReturn(Optional.of(suspendedTransfer()));
    }

    @Test
    @DisplayName("Listado sin filtros: últimos 30 días, todos los estados y contadores por estado")
    void list_defaultRangeUsesLastThirtyDaysAndAllStatuses() {
        Pageable pageable = PageRequest.of(0, 10);
        when(adminSessionClaimRepository.findAdminPage(anyCollection(), any(), any(), any()))
                .thenReturn(new PageImpl<>(List.of(claim), pageable, 1));
        when(adminSessionClaimRepository.countAdminByStatus(eq(ClaimStatus.OPEN), any(), any())).thenReturn(2L);
        when(adminSessionClaimRepository.countAdminByStatus(eq(ClaimStatus.IN_REVIEW), any(), any())).thenReturn(3L);
        when(adminSessionClaimRepository.countAdminByStatus(eq(ClaimStatus.RESOLVED), any(), any())).thenReturn(4L);

        SessionClaimListResponse response = service.list(null, false, null, null, 0, 10, adminId);

        assertThat(response.page().content()).hasSize(1);
        SessionClaimListItemResponse item = response.page().content().get(0);
        assertThat(item.claimId()).isEqualTo(claimId);
        assertThat(item.subjectName()).isEqualTo("Algoritmos");
        assertThat(item.assignedToMe()).isTrue();
        assertThat(response.statusCounts().open()).isEqualTo(2);
        assertThat(response.statusCounts().inReview()).isEqualTo(3);
        assertThat(response.statusCounts().resolved()).isEqualTo(4);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Collection<ClaimStatus>> statusesCaptor = ArgumentCaptor.forClass(Collection.class);
        verify(adminSessionClaimRepository).findAdminPage(
                statusesCaptor.capture(),
                eq(LocalDateTime.of(2026, 9, 29, 0, 0)),
                eq(LocalDateTime.of(2026, 10, 29, 0, 0)),
                any());
        assertThat(statusesCaptor.getValue())
                .containsExactlyInAnyOrder(ClaimStatus.OPEN, ClaimStatus.IN_REVIEW, ClaimStatus.RESOLVED);
    }

    @Test
    @DisplayName("Listado con estado: solo se consulta ese estado")
    void list_withStatusFilterUsesSingleStatus() {
        when(adminSessionClaimRepository.findAdminPage(anyCollection(), any(), any(), any()))
                .thenReturn(new PageImpl<>(List.of(claim)));

        service.list(ClaimStatus.IN_REVIEW, false, null, null, 0, 10, adminId);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Collection<ClaimStatus>> statusesCaptor = ArgumentCaptor.forClass(Collection.class);
        verify(adminSessionClaimRepository).findAdminPage(
                statusesCaptor.capture(), any(), any(), any());
        assertThat(statusesCaptor.getValue()).containsExactly(ClaimStatus.IN_REVIEW);
    }

    @Test
    @DisplayName("Listado con assignedToMe usa la consulta del administrador actual")
    void list_assignedToMeUsesAssignedQuery() {
        when(adminSessionClaimRepository.findAdminPageAssignedTo(anyCollection(), any(), any(), eq(adminId), any()))
                .thenReturn(new PageImpl<>(List.of(claim)));

        service.list(null, true, LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 28), 0, 10, adminId);

        verify(adminSessionClaimRepository).findAdminPageAssignedTo(
                anyCollection(), eq(LocalDateTime.of(2026, 10, 1, 0, 0)),
                eq(LocalDateTime.of(2026, 10, 29, 0, 0)), eq(adminId), any());
        verify(adminSessionClaimRepository, never()).findAdminPage(anyCollection(), any(), any(), any());
    }

    @Test
    @DisplayName("Detalle OPEN: solo se puede tomar")
    void getDetail_openClaimOnlyCanTake() {
        claim.setStatus(ClaimStatus.OPEN);
        claim.setAssignedAdmin(null);
        stubDetail();

        SessionClaimDetailResponse detail = service.getDetail(claimId, adminId);

        assertThat(detail.permissions().canTake()).isTrue();
        assertThat(detail.permissions().canViewContact()).isFalse();
        assertThat(detail.permissions().canResolve()).isFalse();
        assertThat(detail.summary().assignedAdmin()).isNull();
        assertThat(detail.heldAmount()).isEqualByComparingTo("1000.00");
        assertThat(detail.automaticOutcome().code()).isEqualTo(AutomaticOutcome.TUTOR_ABSENT_REFUND_TO_STUDENT);
        assertThat(detail.automaticOutcome().label()).isEqualTo("Tutor ausente — reintegro al alumno");
        assertThat(detail.student().reasonCode()).isEqualTo("TUTOR_COULD_NOT_ATTEND");
        assertThat(detail.student().reasonLabel()).isEqualTo("El tutor no pudo asistir");
        assertThat(detail.student().message()).isEqualTo("Esperé quince minutos");
        assertThat(detail.tutor().fullName()).isEqualTo("Tutor Test");
        assertThat(detail.tutor().reasonCode()).isNull();
        assertThat(detail.tutor().message()).isNull();
    }

    @Test
    @DisplayName("Detalle IN_REVIEW asignado al administrador actual: permisos completos")
    void getDetail_inReviewAssignedToMeHasFullPermissions() {
        stubDetail();

        SessionClaimDetailResponse detail = service.getDetail(claimId, adminId);

        assertThat(detail.permissions().canTake()).isFalse();
        assertThat(detail.permissions().canViewContact()).isTrue();
        assertThat(detail.permissions().canResolve()).isTrue();
        assertThat(detail.summary().assignedToMe()).isTrue();
        assertThat(detail.summary().assignedAdmin().fullName()).isEqualTo("Admin Uno");
    }

    @Test
    @DisplayName("Detalle IN_REVIEW asignado a otro administrador: solo lectura")
    void getDetail_inReviewAssignedToOtherIsReadOnly() {
        claim.setAssignedAdmin(otherAdmin);
        stubDetail();

        SessionClaimDetailResponse detail = service.getDetail(claimId, adminId);

        assertThat(detail.permissions().canTake()).isFalse();
        assertThat(detail.permissions().canViewContact()).isFalse();
        assertThat(detail.permissions().canResolve()).isFalse();
        assertThat(detail.summary().assignedToMe()).isFalse();
    }

    @Test
    @DisplayName("Detalle de sesión sin fondos: heldAmount 0 y resultado automático nulo")
    void getDetail_freeSessionHasZeroHeldAmount() {
        claim.setAutomaticOutcome(null);
        when(adminSessionClaimRepository.findByIdWithDetails(claimId)).thenReturn(Optional.of(claim));
        when(adminSessionClaimRepository.findDisputeClaims(claimId)).thenReturn(List.of(claim));
        when(fundsTransferRepository.findByBooking_BookingId(bookingId)).thenReturn(Optional.empty());

        SessionClaimDetailResponse detail = service.getDetail(claimId, adminId);

        assertThat(detail.heldAmount()).isEqualByComparingTo("0");
        assertThat(detail.automaticOutcome()).isNull();
    }

    @Test
    @DisplayName("Tomar un reclamo OPEN lo asigna al administrador y audita")
    void assign_openClaimAssignsAndAudits() {
        claim.setStatus(ClaimStatus.OPEN);
        claim.setAssignedAdmin(null);
        when(adminSessionClaimRepository.findById(claimId)).thenReturn(Optional.of(claim));
        when(userRepository.findById(adminId)).thenReturn(Optional.of(admin));
        when(adminSessionClaimRepository.assignIfOpen(eq(claimId), eq(admin), any())).thenReturn(1);
        stubDetail();

        service.assign(claimId, adminId);

        verify(adminSessionClaimRepository).assignIfOpen(claimId, admin, LocalDateTime.now(CLOCK));
        verify(adminAuditService).record(adminId, AdminAuditAction.CASE_TAKEN, "SESSION_CLAIM", claimId);
        verify(fundsResolutionService, never()).resolveByAdminDecision(any(), any(), any());
    }

    @Test
    @DisplayName("Tomar un reclamo ya tomado devuelve CASE_ALREADY_TAKEN")
    void assign_alreadyTakenThrowsConflict() {
        claim.setStatus(ClaimStatus.IN_REVIEW);
        when(adminSessionClaimRepository.findById(claimId)).thenReturn(Optional.of(claim));
        when(userRepository.findById(adminId)).thenReturn(Optional.of(admin));
        when(adminSessionClaimRepository.assignIfOpen(eq(claimId), eq(admin), any())).thenReturn(0);
        when(adminSessionClaimRepository.findById(claimId)).thenReturn(Optional.of(claim));

        assertThatThrownBy(() -> service.assign(claimId, adminId))
                .isInstanceOf(BusinessConflictException.class)
                .satisfies(ex -> assertThat(((BusinessConflictException) ex).getErrorCode())
                        .isEqualTo("CASE_ALREADY_TAKEN"));
    }

    @Test
    @DisplayName("Tomar un reclamo resuelto devuelve CASE_ALREADY_RESOLVED")
    void assign_resolvedThrowsAlreadyResolved() {
        claim.setStatus(ClaimStatus.RESOLVED);
        when(adminSessionClaimRepository.findById(claimId)).thenReturn(Optional.of(claim));
        when(userRepository.findById(adminId)).thenReturn(Optional.of(admin));

        assertThatThrownBy(() -> service.assign(claimId, adminId))
                .isInstanceOf(BusinessConflictException.class)
                .satisfies(ex -> assertThat(((BusinessConflictException) ex).getErrorCode())
                        .isEqualTo("CASE_ALREADY_RESOLVED"));
    }

    @Test
    @DisplayName("Tomar un reclamo inexistente devuelve 404 CLAIM_NOT_FOUND")
    void assign_unknownClaimThrowsNotFound() {
        when(adminSessionClaimRepository.findById(claimId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.assign(claimId, adminId))
                .isInstanceOf(ResourceNotFoundException.class)
                .satisfies(ex -> assertThat(((ResourceNotFoundException) ex).getErrorCode())
                        .isEqualTo("CLAIM_NOT_FOUND"));
    }

    @Test
    @DisplayName("Contacto de un reclamo OPEN devuelve CASE_NOT_IN_REVIEW")
    void getContactInfo_openThrowsNotInReview() {
        claim.setStatus(ClaimStatus.OPEN);
        when(adminSessionClaimRepository.findByIdWithDetails(claimId)).thenReturn(Optional.of(claim));

        assertThatThrownBy(() -> service.getContactInfo(claimId, adminId))
                .isInstanceOf(BusinessConflictException.class)
                .satisfies(ex -> assertThat(((BusinessConflictException) ex).getErrorCode())
                        .isEqualTo("CASE_NOT_IN_REVIEW"));
        verify(adminAuditService, never()).record(any(), any(), any(), any());
    }

    @Test
    @DisplayName("Contacto de un reclamo asignado a otro administrador devuelve 403")
    void getContactInfo_otherAdminThrowsNotAssigned() {
        claim.setAssignedAdmin(otherAdmin);
        when(adminSessionClaimRepository.findByIdWithDetails(claimId)).thenReturn(Optional.of(claim));

        assertThatThrownBy(() -> service.getContactInfo(claimId, adminId))
                .isInstanceOf(NotAssignedToAdminException.class);
        verify(adminAuditService, never()).record(any(), any(), any(), any());
    }

    @Test
    @DisplayName("Contacto de un reclamo asignado devuelve ambas partes y audita")
    void getContactInfo_assignedReturnsContactsAndAudits() {
        stubDetail();

        ClaimContactInfoResponse response = service.getContactInfo(claimId, adminId);

        assertThat(response.tutor().fullName()).isEqualTo("Tutor Test");
        assertThat(response.tutor().email()).isEqualTo("tutor@test.com");
        assertThat(response.tutor().phoneNumber()).isEqualTo("3511234567");
        assertThat(response.tutor().dni()).isEqualTo("30111222");
        assertThat(response.student().fullName()).isEqualTo("Alumno Test");
        verify(adminAuditService).record(adminId, AdminAuditAction.CONTACT_INFO_VIEWED, "SESSION_CLAIM", claimId);
    }

    @Test
    @DisplayName("Resolver un reclamo OPEN devuelve CASE_NOT_IN_REVIEW")
    void resolve_openThrowsNotInReview() {
        claim.setStatus(ClaimStatus.OPEN);
        when(adminSessionClaimRepository.findByIdForUpdate(claimId)).thenReturn(Optional.of(claim));

        assertThatThrownBy(() -> service.resolve(claimId, adminId,
                new ResolveSessionClaimRequest(ClaimFavoredParty.TUTOR, "Justificación")))
                .isInstanceOf(BusinessConflictException.class)
                .satisfies(ex -> assertThat(((BusinessConflictException) ex).getErrorCode())
                        .isEqualTo("CASE_NOT_IN_REVIEW"));
        verify(fundsResolutionService, never()).resolveByAdminDecision(any(), any(), any());
    }

    @Test
    @DisplayName("Resolver un reclamo ya resuelto devuelve CASE_ALREADY_RESOLVED")
    void resolve_resolvedThrowsAlreadyResolved() {
        claim.setStatus(ClaimStatus.RESOLVED);
        when(adminSessionClaimRepository.findByIdForUpdate(claimId)).thenReturn(Optional.of(claim));

        assertThatThrownBy(() -> service.resolve(claimId, adminId,
                new ResolveSessionClaimRequest(ClaimFavoredParty.TUTOR, "Justificación")))
                .isInstanceOf(BusinessConflictException.class)
                .satisfies(ex -> assertThat(((BusinessConflictException) ex).getErrorCode())
                        .isEqualTo("CASE_ALREADY_RESOLVED"));
    }

    @Test
    @DisplayName("Resolver un reclamo asignado a otro administrador devuelve 403")
    void resolve_otherAdminThrowsNotAssigned() {
        claim.setAssignedAdmin(otherAdmin);
        when(adminSessionClaimRepository.findByIdForUpdate(claimId)).thenReturn(Optional.of(claim));

        assertThatThrownBy(() -> service.resolve(claimId, adminId,
                new ResolveSessionClaimRequest(ClaimFavoredParty.TUTOR, "Justificación")))
                .isInstanceOf(NotAssignedToAdminException.class);
    }

    @Test
    @DisplayName("Resolver con justificación en blanco devuelve VALIDATION_ERROR sin tocar los fondos")
    void resolve_blankJustificationThrowsValidation() {
        when(adminSessionClaimRepository.findByIdForUpdate(claimId)).thenReturn(Optional.of(claim));

        assertThatThrownBy(() -> service.resolve(claimId, adminId,
                new ResolveSessionClaimRequest(ClaimFavoredParty.TUTOR, "   ")))
                .isInstanceOf(ValidationException.class);
        verify(fundsResolutionService, never()).resolveByAdminDecision(any(), any(), any());
        verify(adminSessionClaimRepository, never()).resolveDispute(any(), any(), any(), any());
    }

    @Test
    @DisplayName("Resolver a favor del tutor libera fondos, completa la reserva y publica el evento")
    void resolve_favoredTutorReleasesFundsAndCompletesBooking() {
        when(adminSessionClaimRepository.findByIdForUpdate(claimId)).thenReturn(Optional.of(claim));
        when(adminSessionClaimRepository.findDisputeClaims(claimId)).thenReturn(List.of(claim));
        when(fundsResolutionService.resolveByAdminDecision(booking, ClaimFavoredParty.TUTOR, claimId))
                .thenReturn(new BigDecimal("970.00"));
        when(adminSessionClaimRepository.resolveDispute(eq(claimId), eq(ClaimFavoredParty.TUTOR), eq("Resuelvo a favor del tutor"), any()))
                .thenReturn(2);
        when(bookingRepository.findById(bookingId)).thenReturn(Optional.of(booking));
        stubDetail();

        service.resolve(claimId, adminId,
                new ResolveSessionClaimRequest(ClaimFavoredParty.TUTOR, "  Resuelvo a favor del tutor  "));

        assertThat(booking.getBookingStatus()).isEqualTo(BookingStatus.COMPLETED);
        verify(bookingRepository).save(booking);
        verify(adminSessionClaimRepository).resolveDispute(
                claimId, ClaimFavoredParty.TUTOR, "Resuelvo a favor del tutor", LocalDateTime.now(CLOCK));
        verify(adminAuditService).record(adminId, AdminAuditAction.CLAIM_RESOLVED, "SESSION_CLAIM", claimId);

        ArgumentCaptor<SessionClaimResolvedEvent> eventCaptor =
                ArgumentCaptor.forClass(SessionClaimResolvedEvent.class);
        verify(applicationEventPublisher).publishEvent(eventCaptor.capture());
        SessionClaimResolvedEvent event = eventCaptor.getValue();
        assertThat(event.claimId()).isEqualTo(claimId);
        assertThat(event.bookingId()).isEqualTo(bookingId);
        assertThat(event.favoredParty()).isEqualTo(ClaimFavoredParty.TUTOR);
        assertThat(event.justification()).isEqualTo("Resuelvo a favor del tutor");
        assertThat(event.subjectName()).isEqualTo("Algoritmos");
        assertThat(event.movedAmount()).isEqualByComparingTo("970.00");
    }

    @Test
    @DisplayName("Resolver a favor del alumno con motivo de tutor ausente deja NOT_FULFILLED_BY_TUTOR")
    void resolve_favoredStudentWithTutorAbsentReasonSetsNotFulfilled() {
        when(adminSessionClaimRepository.findByIdForUpdate(claimId)).thenReturn(Optional.of(claim));
        when(adminSessionClaimRepository.findDisputeClaims(claimId)).thenReturn(List.of(claim));
        when(fundsResolutionService.resolveByAdminDecision(booking, ClaimFavoredParty.STUDENT, claimId))
                .thenReturn(new BigDecimal("1000.00"));
        when(adminSessionClaimRepository.resolveDispute(any(), any(), any(), any())).thenReturn(1);
        when(bookingRepository.findById(bookingId)).thenReturn(Optional.of(booking));
        stubDetail();

        service.resolve(claimId, adminId,
                new ResolveSessionClaimRequest(ClaimFavoredParty.STUDENT, "El tutor no asistió"));

        assertThat(booking.getBookingStatus()).isEqualTo(BookingStatus.NOT_FULFILLED_BY_TUTOR);
    }

    @Test
    @DisplayName("Resolver a favor del alumno con otro motivo deja SESSION_NOT_HELD")
    void resolve_favoredStudentWithOtherReasonSetsSessionNotHeld() {
        claim.setReason(ClaimReason.I_COULD_NOT_ATTEND);
        when(adminSessionClaimRepository.findByIdForUpdate(claimId)).thenReturn(Optional.of(claim));
        when(adminSessionClaimRepository.findDisputeClaims(claimId)).thenReturn(List.of(claim));
        when(fundsResolutionService.resolveByAdminDecision(booking, ClaimFavoredParty.STUDENT, claimId))
                .thenReturn(new BigDecimal("1000.00"));
        when(adminSessionClaimRepository.resolveDispute(any(), any(), any(), any())).thenReturn(1);
        when(bookingRepository.findById(bookingId)).thenReturn(Optional.of(booking));
        stubDetail();

        service.resolve(claimId, adminId,
                new ResolveSessionClaimRequest(ClaimFavoredParty.STUDENT, "No hubo sesión"));

        assertThat(booking.getBookingStatus()).isEqualTo(BookingStatus.SESSION_NOT_HELD);
    }

    @Test
    @DisplayName("Resolver una sesión gratuita no mueve fondos y publica movedAmount 0")
    void resolve_freeSessionMovesNoFunds() {
        when(adminSessionClaimRepository.findByIdForUpdate(claimId)).thenReturn(Optional.of(claim));
        when(adminSessionClaimRepository.findDisputeClaims(claimId)).thenReturn(List.of(claim));
        when(fundsResolutionService.resolveByAdminDecision(booking, ClaimFavoredParty.TUTOR, claimId))
                .thenReturn(BigDecimal.ZERO);
        when(adminSessionClaimRepository.resolveDispute(any(), any(), any(), any())).thenReturn(1);
        when(bookingRepository.findById(bookingId)).thenReturn(Optional.of(booking));
        when(adminSessionClaimRepository.findByIdWithDetails(claimId)).thenReturn(Optional.of(claim));
        when(fundsTransferRepository.findByBooking_BookingId(bookingId)).thenReturn(Optional.empty());

        SessionClaimDetailResponse detail = service.resolve(claimId, adminId,
                new ResolveSessionClaimRequest(ClaimFavoredParty.TUTOR, "Sesión gratuita"));

        assertThat(detail.heldAmount()).isEqualByComparingTo("0");
        ArgumentCaptor<SessionClaimResolvedEvent> eventCaptor =
                ArgumentCaptor.forClass(SessionClaimResolvedEvent.class);
        verify(applicationEventPublisher).publishEvent(eventCaptor.capture());
        assertThat(eventCaptor.getValue().movedAmount()).isEqualByComparingTo("0");
    }

    @Test
    @DisplayName("Falla de fondos: se propaga el error y el reclamo, la reserva y el evento quedan intactos")
    void resolve_whenFundsExecutionFails_keepsClaimInReview() {
        when(adminSessionClaimRepository.findByIdForUpdate(claimId)).thenReturn(Optional.of(claim));
        when(adminSessionClaimRepository.findDisputeClaims(claimId)).thenReturn(List.of(claim));
        when(fundsResolutionService.resolveByAdminDecision(booking, ClaimFavoredParty.TUTOR, claimId))
                .thenThrow(new FundsExecutionException(
                        "Gateway rejected the transfer", new RuntimeException("timeout")));

        assertThatThrownBy(() -> service.resolve(claimId, adminId,
                new ResolveSessionClaimRequest(ClaimFavoredParty.TUTOR, "Clase dictada")))
                .isInstanceOf(FundsExecutionException.class);

        assertThat(claim.getStatus()).isEqualTo(ClaimStatus.IN_REVIEW);
        assertThat(claim.getFavoredParty()).isNull();
        assertThat(claim.getResolvedAt()).isNull();
        assertThat(claim.getResolutionJustification()).isNull();
        assertThat(booking.getBookingStatus()).isEqualTo(BookingStatus.NOT_CONFIRMED);

        verify(adminSessionClaimRepository, never()).resolveDispute(any(), any(), any(), any());
        verify(bookingRepository, never()).save(any());
        verify(applicationEventPublisher, never()).publishEvent(any(SessionClaimResolvedEvent.class));
        verify(adminAuditService, never()).record(any(), any(), any(), any());
    }
}