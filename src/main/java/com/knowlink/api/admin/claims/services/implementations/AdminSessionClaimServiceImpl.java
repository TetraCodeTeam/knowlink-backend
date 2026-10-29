package com.knowlink.api.admin.claims.services.implementations;

import com.knowlink.api.admin.claims.controllers.requests.ResolveSessionClaimRequest;
import com.knowlink.api.admin.claims.controllers.responses.AssignedAdminResponse;
import com.knowlink.api.admin.claims.controllers.responses.AutomaticOutcomeResponse;
import com.knowlink.api.admin.claims.controllers.responses.ClaimContactInfoResponse;
import com.knowlink.api.admin.claims.controllers.responses.ClaimPartyStatementResponse;
import com.knowlink.api.admin.claims.controllers.responses.ClaimPermissionsResponse;
import com.knowlink.api.admin.claims.controllers.responses.ClaimStatusCountsResponse;
import com.knowlink.api.admin.claims.controllers.responses.PartyContactResponse;
import com.knowlink.api.admin.claims.controllers.responses.SessionClaimDetailResponse;
import com.knowlink.api.admin.claims.controllers.responses.SessionClaimListItemResponse;
import com.knowlink.api.admin.claims.controllers.responses.SessionClaimListResponse;
import com.knowlink.api.admin.claims.data.enums.ClaimFavoredParty;
import com.knowlink.api.admin.claims.events.SessionClaimResolvedEvent;
import com.knowlink.api.admin.claims.repositories.IAdminSessionClaimRepository;
import com.knowlink.api.admin.claims.services.interfaces.IAdminSessionClaimService;
import com.knowlink.api.admin.common.AdminPagination;
import com.knowlink.api.admin.common.DateRange;
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
import com.knowlink.api.exceptions.custom_exceptions.NotAssignedToAdminException;
import com.knowlink.api.exceptions.custom_exceptions.ResourceNotFoundException;
import com.knowlink.api.exceptions.custom_exceptions.ValidationException;
import com.knowlink.api.payments.data.models.FundsTransfer;
import com.knowlink.api.payments.repositories.IFundsTransferRepository;
import com.knowlink.api.payments.services.FundsResolutionService;
import com.knowlink.api.security.enums.Role;
import com.knowlink.api.shared.responses.PagedResponse;
import com.knowlink.api.students.data.models.StudentProfile;
import com.knowlink.api.students.repositories.IStudentProfileRepository;
import com.knowlink.api.tutors.data.models.TutorProfile;
import com.knowlink.api.tutors.repositories.ITutorProfileRepository;
import com.knowlink.api.users.data.models.User;
import com.knowlink.api.users.repositories.IUserRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class AdminSessionClaimServiceImpl implements IAdminSessionClaimService {

    private static final String TARGET_TYPE = "SESSION_CLAIM";
    private static final String CASE_ALREADY_TAKEN = "CASE_ALREADY_TAKEN";
    private static final String CASE_ALREADY_TAKEN_MESSAGE = "Otro administrador ya tomó esta solicitud.";
    private static final String CASE_ALREADY_RESOLVED = "CASE_ALREADY_RESOLVED";
    private static final String CASE_ALREADY_RESOLVED_MESSAGE =
            "Esta solicitud ya fue resuelta y no puede modificarse.";
    private static final String CASE_NOT_IN_REVIEW = "CASE_NOT_IN_REVIEW";
    private static final String CASE_NOT_IN_REVIEW_MESSAGE = "La solicitud todavía no está en revisión.";

    private final IAdminSessionClaimRepository adminSessionClaimRepository;
    private final IFundsTransferRepository fundsTransferRepository;
    private final IBookingRepository bookingRepository;
    private final IUserRepository userRepository;
    private final ITutorProfileRepository tutorProfileRepository;
    private final IStudentProfileRepository studentProfileRepository;
    private final FundsResolutionService fundsResolutionService;
    private final IAdminAuditService adminAuditService;
    private final DateRangeResolver dateRangeResolver;
    private final ApplicationEventPublisher applicationEventPublisher;
    private final Clock clock;

    @Override
    @Transactional(readOnly = true)
    public SessionClaimListResponse list(ClaimStatus status, boolean assignedToMe, LocalDate from, LocalDate to,
            Integer page, Integer size, UUID adminUserId) {
        DateRange range = dateRangeResolver.resolve(from, to);
        LocalDateTime fromInclusive = range.fromInclusive();
        LocalDateTime toExclusive = range.toExclusive();
        Collection<ClaimStatus> statuses = status != null
                ? List.of(status)
                : List.of(ClaimStatus.OPEN, ClaimStatus.IN_REVIEW, ClaimStatus.RESOLVED);
        Pageable pageable = AdminPagination.of(page, size, Sort.unsorted());

        Page<SessionClaim> claims = assignedToMe
                ? adminSessionClaimRepository.findAdminPageAssignedTo(
                        statuses, fromInclusive, toExclusive, adminUserId, pageable)
                : adminSessionClaimRepository.findAdminPage(statuses, fromInclusive, toExclusive, pageable);

        Page<SessionClaimListItemResponse> mapped = claims.map(claim -> toListItem(claim, adminUserId));
        ClaimStatusCountsResponse statusCounts = new ClaimStatusCountsResponse(
                countByStatus(ClaimStatus.OPEN, assignedToMe, fromInclusive, toExclusive, adminUserId),
                countByStatus(ClaimStatus.IN_REVIEW, assignedToMe, fromInclusive, toExclusive, adminUserId),
                countByStatus(ClaimStatus.RESOLVED, assignedToMe, fromInclusive, toExclusive, adminUserId));

        return new SessionClaimListResponse(PagedResponse.from(mapped), statusCounts);
    }

    @Override
    @Transactional(readOnly = true)
    public SessionClaimDetailResponse getDetail(UUID claimId, UUID adminUserId) {
        return buildDetail(loadWithDetails(claimId), adminUserId);
    }

    @Override
    @Transactional
    public SessionClaimDetailResponse assign(UUID claimId, UUID adminUserId) {
        SessionClaim claim = adminSessionClaimRepository.findById(claimId)
                .orElseThrow(() -> claimNotFound(claimId));
        UUID disputeKey = disputeKey(claim);
        User admin = userRepository.findById(adminUserId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "ADMIN_NOT_FOUND",
                        "El administrador no existe.",
                        "Admin not found for id: " + adminUserId));

        int updated = adminSessionClaimRepository.assignIfOpen(disputeKey, admin, LocalDateTime.now(clock));
        if (updated == 0) {
            throw alreadyTakenConflict(disputeKey);
        }

        adminAuditService.record(adminUserId, AdminAuditAction.CASE_TAKEN, TARGET_TYPE, disputeKey);
        log.info("Admin {} took session claim dispute {}", adminUserId, disputeKey);

        return buildDetail(loadWithDetails(claimId), adminUserId);
    }

    /**
     * Personal data of both parties. Not marked read-only on purpose: the audit record must be
     * persisted in the same transaction.
     */
    @Override
    @Transactional
    public ClaimContactInfoResponse getContactInfo(UUID claimId, UUID adminUserId) {
        SessionClaim claim = loadWithDetails(claimId);
        if (claim.getStatus() != ClaimStatus.IN_REVIEW) {
            throw new BusinessConflictException(
                    CASE_NOT_IN_REVIEW,
                    CASE_NOT_IN_REVIEW_MESSAGE,
                    "Claim " + claimId + " is not in review (status " + claim.getStatus() + ")");
        }
        User assignedAdmin = claim.getAssignedAdmin();
        if (assignedAdmin == null || !assignedAdmin.getUserId().equals(adminUserId)) {
            throw new NotAssignedToAdminException(
                    "Claim " + claimId + " is not assigned to admin " + adminUserId);
        }

        Booking booking = claim.getBooking();
        ClaimContactInfoResponse response = new ClaimContactInfoResponse(
                toTutorContact(booking.getTutor()),
                toStudentContact(booking.getStudent()));

        UUID disputeKey = disputeKey(claim);
        adminAuditService.record(adminUserId, AdminAuditAction.CONTACT_INFO_VIEWED, TARGET_TYPE, disputeKey);
        log.info("Admin {} viewed contact info of session claim dispute {}", adminUserId, disputeKey);

        return response;
    }

    @Override
    @Transactional
    public SessionClaimDetailResponse resolve(UUID claimId, UUID adminUserId, ResolveSessionClaimRequest request) {
        SessionClaim claim = adminSessionClaimRepository.findByIdForUpdate(claimId)
                .orElseThrow(() -> claimNotFound(claimId));

        if (claim.getStatus() == ClaimStatus.RESOLVED) {
            throw new BusinessConflictException(
                    CASE_ALREADY_RESOLVED,
                    CASE_ALREADY_RESOLVED_MESSAGE,
                    "Claim " + claimId + " is already resolved");
        }
        if (claim.getStatus() == ClaimStatus.OPEN) {
            throw new BusinessConflictException(
                    CASE_NOT_IN_REVIEW,
                    "Tomá la solicitud antes de resolverla.",
                    "Claim " + claimId + " is still open");
        }
        User assignedAdmin = claim.getAssignedAdmin();
        if (assignedAdmin == null || !assignedAdmin.getUserId().equals(adminUserId)) {
            throw new NotAssignedToAdminException(
                    "Claim " + claimId + " is not assigned to admin " + adminUserId);
        }

        String justification = request.justification() == null ? null : request.justification().trim();
        if (justification == null || justification.isBlank()) {
            throw new ValidationException("La justificación de la resolución es obligatoria.");
        }

        UUID disputeKey = disputeKey(claim);
        Booking booking = claim.getBooking();
        UUID bookingId = booking.getBookingId();
        UUID tutorUserId = booking.getTutor().getUserId();
        UUID studentUserId = booking.getStudent().getUserId();
        String subjectName = booking.getTutorSubject().getSubject().getName();
        LocalDate sessionDate = booking.getSessionDate();
        ClaimReason studentReason = studentReason(disputeKey);

        BigDecimal movedAmount = fundsResolutionService.resolveByAdminDecision(
                booking, request.favoredParty(), disputeKey);

        LocalDateTime now = LocalDateTime.now(clock);
        int updated = adminSessionClaimRepository.resolveDispute(
                disputeKey, request.favoredParty(), justification, now);
        if (updated == 0) {
            throw new BusinessConflictException(
                    CASE_ALREADY_RESOLVED,
                    CASE_ALREADY_RESOLVED_MESSAGE,
                    "No claims could be resolved for dispute " + disputeKey);
        }

        BookingStatus finalStatus = finalBookingStatus(request.favoredParty(), studentReason);
        Booking managedBooking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "SESSION_NOT_FOUND",
                        "La sesión no existe.",
                        "Booking not found for id: " + bookingId));
        managedBooking.setBookingStatus(finalStatus);
        bookingRepository.save(managedBooking);

        applicationEventPublisher.publishEvent(new SessionClaimResolvedEvent(
                disputeKey,
                bookingId,
                tutorUserId,
                studentUserId,
                request.favoredParty(),
                justification,
                subjectName,
                sessionDate,
                movedAmount));

        adminAuditService.record(adminUserId, AdminAuditAction.CLAIM_RESOLVED, TARGET_TYPE, disputeKey);
        log.info("Admin {} resolved session claim dispute {} in favor of {}",
                adminUserId, disputeKey, request.favoredParty());

        return buildDetail(loadWithDetails(claimId), adminUserId);
    }

    private long countByStatus(ClaimStatus status, boolean assignedToMe, LocalDateTime from, LocalDateTime to,
            UUID adminUserId) {
        return assignedToMe
                ? adminSessionClaimRepository.countAdminByStatusAssignedTo(status, from, to, adminUserId)
                : adminSessionClaimRepository.countAdminByStatus(status, from, to);
    }

    private SessionClaimDetailResponse buildDetail(SessionClaim claim, UUID adminUserId) {
        UUID disputeKey = disputeKey(claim);
        List<SessionClaim> disputeClaims = adminSessionClaimRepository.findDisputeClaims(disputeKey);
        SessionClaim representative = disputeClaims.stream()
                .filter(disputeClaim -> disputeClaim.getClaimId().equals(disputeKey))
                .findFirst()
                .orElse(claim);
        SessionClaim summaryClaim = representative.getClaimId().equals(claim.getClaimId())
                ? claim
                : loadWithDetails(representative.getClaimId());

        Booking booking = summaryClaim.getBooking();
        BigDecimal heldAmount = fundsTransferRepository.findByBooking_BookingId(booking.getBookingId())
                .map(FundsTransfer::getOriginalAmount)
                .orElse(BigDecimal.ZERO);

        AutomaticOutcome automaticOutcome = representative.getAutomaticOutcome();
        AutomaticOutcomeResponse automaticOutcomeResponse = automaticOutcome == null
                ? null
                : new AutomaticOutcomeResponse(automaticOutcome, automaticOutcome.label());

        User assignedAdmin = summaryClaim.getAssignedAdmin();
        boolean assignedToMe = assignedAdmin != null && assignedAdmin.getUserId().equals(adminUserId);
        boolean inReview = summaryClaim.getStatus() == ClaimStatus.IN_REVIEW;
        ClaimPermissionsResponse permissions = new ClaimPermissionsResponse(
                summaryClaim.getStatus() == ClaimStatus.OPEN,
                inReview && assignedToMe,
                inReview && assignedToMe);

        return new SessionClaimDetailResponse(
                toListItem(summaryClaim, adminUserId),
                heldAmount,
                automaticOutcomeResponse,
                statementFor(disputeClaims, Role.TUTOR, booking.getTutor()),
                statementFor(disputeClaims, Role.STUDENT, booking.getStudent()),
                permissions);
    }

    private SessionClaimListItemResponse toListItem(SessionClaim claim, UUID adminUserId) {
        Booking booking = claim.getBooking();
        User assignedAdmin = claim.getAssignedAdmin();
        AssignedAdminResponse assignedAdminResponse = assignedAdmin == null
                ? null
                : new AssignedAdminResponse(assignedAdmin.getUserId(), assignedAdmin.getFullName());

        return new SessionClaimListItemResponse(
                claim.getClaimId(),
                booking.getTutorSubject().getSubject().getName(),
                booking.getTutor().getFullName(),
                booking.getStudent().getFullName(),
                booking.getSessionDate(),
                claim.getStatus(),
                claim.getCreatedAt(),
                assignedAdminResponse,
                assignedAdmin != null && assignedAdmin.getUserId().equals(adminUserId),
                claim.getResolvedAt(),
                claim.getFavoredParty(),
                claim.getResolutionJustification());
    }

    private ClaimPartyStatementResponse statementFor(List<SessionClaim> disputeClaims, Role role, User party) {
        return disputeClaims.stream()
                .filter(claim -> claim.getClaimantRole() == role)
                .findFirst()
                .map(claim -> new ClaimPartyStatementResponse(
                        claim.getClaimant().getFullName(),
                        claim.getReason().name(),
                        claim.getReason().displayLabel(),
                        claim.getComment()))
                .orElseGet(() -> new ClaimPartyStatementResponse(party.getFullName(), null, null, null));
    }

    private ClaimReason studentReason(UUID disputeKey) {
        return adminSessionClaimRepository.findDisputeClaims(disputeKey).stream()
                .filter(claim -> claim.getClaimantRole() == Role.STUDENT)
                .map(SessionClaim::getReason)
                .findFirst()
                .orElse(null);
    }

    private BookingStatus finalBookingStatus(ClaimFavoredParty favoredParty, ClaimReason studentReason) {
        if (favoredParty == ClaimFavoredParty.TUTOR) {
            return BookingStatus.COMPLETED;
        }
        return studentReason == ClaimReason.TUTOR_COULD_NOT_ATTEND
                ? BookingStatus.NOT_FULFILLED_BY_TUTOR
                : BookingStatus.SESSION_NOT_HELD;
    }

    private PartyContactResponse toTutorContact(User tutor) {
        TutorProfile profile = tutorProfileRepository.findByUserId(tutor.getUserId()).orElse(null);
        return new PartyContactResponse(
                tutor.getFullName(),
                tutor.getEmail(),
                tutor.getPhoneNumber(),
                tutor.getDni(),
                profile == null ? null : profile.getInstitutionalId(),
                profile == null || profile.getCareer() == null ? null : profile.getCareer().getName());
    }

    private PartyContactResponse toStudentContact(User student) {
        StudentProfile profile = studentProfileRepository.findByUserId(student.getUserId()).orElse(null);
        return new PartyContactResponse(
                student.getFullName(),
                student.getEmail(),
                student.getPhoneNumber(),
                student.getDni(),
                profile == null ? null : profile.getInstitutionalId(),
                profile == null || profile.getCareer() == null ? null : profile.getCareer().getName());
    }

    private SessionClaim loadWithDetails(UUID claimId) {
        return adminSessionClaimRepository.findByIdWithDetails(claimId)
                .orElseThrow(() -> claimNotFound(claimId));
    }

    private UUID disputeKey(SessionClaim claim) {
        return claim.getDisputeId() != null ? claim.getDisputeId() : claim.getClaimId();
    }

    private BusinessConflictException alreadyTakenConflict(UUID disputeKey) {
        ClaimStatus status = adminSessionClaimRepository.findById(disputeKey)
                .map(SessionClaim::getStatus)
                .orElseThrow(() -> claimNotFound(disputeKey));
        if (status == ClaimStatus.RESOLVED) {
            return new BusinessConflictException(
                    CASE_ALREADY_RESOLVED,
                    CASE_ALREADY_RESOLVED_MESSAGE,
                    "Claim dispute " + disputeKey + " is already resolved");
        }
        return new BusinessConflictException(
                CASE_ALREADY_TAKEN,
                CASE_ALREADY_TAKEN_MESSAGE,
                "Claim dispute " + disputeKey + " is not open anymore (status " + status + ")");
    }

    private ResourceNotFoundException claimNotFound(UUID claimId) {
        return new ResourceNotFoundException(
                "CLAIM_NOT_FOUND",
                "El reclamo no existe.",
                "Session claim not found for id: " + claimId);
    }
}