package com.knowlink.api.claims.services.implementations;

import com.knowlink.api.bookings.data.models.Booking;
import com.knowlink.api.bookings.repositories.IBookingRepository;
import com.knowlink.api.claims.controllers.requests.CreateClaimRequest;
import com.knowlink.api.claims.controllers.responses.ClaimAttachmentResponse;
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
import com.knowlink.api.claims.services.interfaces.ISessionClaimService;
import com.knowlink.api.claims.utils.ClaimDeadlineCalculator;
import com.knowlink.api.exceptions.custom_exceptions.ClaimDeadlineExceededException;
import com.knowlink.api.exceptions.custom_exceptions.DuplicateResourceException;
import com.knowlink.api.exceptions.custom_exceptions.ResourceNotFoundException;
import com.knowlink.api.exceptions.custom_exceptions.SessionNotFinalizedException;
import com.knowlink.api.exceptions.custom_exceptions.SessionNotParticipantException;
import com.knowlink.api.exceptions.custom_exceptions.ValidationException;
import com.knowlink.api.payments.services.FundsLedgerService;
import com.knowlink.api.materials.exception.FormatNotAllowedException;
import com.knowlink.api.materials.service.interfaces.ISupabaseStorageService;
import com.knowlink.api.security.enums.Role;
import com.knowlink.api.users.data.models.User;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@Slf4j
public class SessionClaimServiceImpl implements ISessionClaimService {

    private static final String EVIDENCE_BUCKET = "claimEvidence";
    private static final int SIGNED_URL_EXPIRATION_SECONDS = 3600;
    private static final String ACTIVE_CLAIM_MESSAGE = "Ya tenés una disputa activa para esta sesión";
    private static final Set<String> ALLOWED_EXTENSIONS = Set.of("pdf", "jpg", "jpeg", "png");
    private static final Map<String, Set<String>> ALLOWED_CONTENT_TYPES = Map.of(
            "pdf", Set.of("application/pdf"),
            "jpg", Set.of("image/jpeg"),
            "jpeg", Set.of("image/jpeg"),
            "png", Set.of("image/png"));

    private final IBookingRepository bookingRepository;
    private final SessionClaimRepository sessionClaimRepository;
    private final ClaimAttachmentRepository claimAttachmentRepository;
    private final FundsLedgerService fundsLedgerService;
    private final ISupabaseStorageService storageService;
    private final ClaimDeadlineCalculator deadlineCalculator;
    private final int maxAttachments;
    private final long maxAttachmentBytes;

    public SessionClaimServiceImpl(
            IBookingRepository bookingRepository,
            SessionClaimRepository sessionClaimRepository,
            ClaimAttachmentRepository claimAttachmentRepository,
            FundsLedgerService fundsLedgerService,
            ISupabaseStorageService storageService,
            ClaimDeadlineCalculator deadlineCalculator,
            @Value("${knowlink.claims.max-attachments:3}") int maxAttachments,
            @Value("${knowlink.claims.max-attachment-mb:5}") long maxAttachmentMb) {
        this.bookingRepository = bookingRepository;
        this.sessionClaimRepository = sessionClaimRepository;
        this.claimAttachmentRepository = claimAttachmentRepository;
        this.fundsLedgerService = fundsLedgerService;
        this.storageService = storageService;
        this.deadlineCalculator = deadlineCalculator;
        this.maxAttachments = maxAttachments;
        this.maxAttachmentBytes = maxAttachmentMb * 1024 * 1024;
    }

    @Override
    @Transactional
    public ClaimResponse create(UUID userId, UUID bookingId, CreateClaimRequest request, List<MultipartFile> files) {
        Booking booking = findBookingOrThrow(bookingId);

        ClaimEligibilityResponse eligibility = evaluate(userId, booking, null);
        if (!eligibility.canClaim()) {
            throw toException(eligibility, bookingId, userId);
        }

        List<MultipartFile> attachments = normalize(files);
        validateAttachments(attachments);

        Role claimantRole = participantRole(userId, booking);
        User claimant = claimantRole == Role.STUDENT ? booking.getStudent() : booking.getTutor();
        String activeKey = bookingId + ":" + userId;

        SessionClaim claim = SessionClaim.builder()
                .booking(booking)
                .claimant(claimant)
                .claimantRole(claimantRole)
                .reason(request.reason())
                .comment(normalizeComment(request.comment()))
                .status(ClaimStatus.OPEN)
                .activeKey(activeKey)
                .build();

        SessionClaim savedClaim;
        try {
            savedClaim = sessionClaimRepository.saveAndFlush(claim);
        } catch (DataIntegrityViolationException ex) {
            log.warn("Active claim already exists for booking {} and user {}", bookingId, userId);
            throw activeClaimExists(bookingId, userId, ex);
        }

        List<String> uploadedPaths = new ArrayList<>();
        List<ClaimAttachment> attachmentEntities = new ArrayList<>();
        try {
            for (MultipartFile file : attachments) {
                uploadedPaths.add(storageService.upload(file, EVIDENCE_BUCKET, bookingId.toString()));
            }

            for (int i = 0; i < attachments.size(); i++) {
                MultipartFile file = attachments.get(i);
                attachmentEntities.add(ClaimAttachment.builder()
                        .claim(savedClaim)
                        .storagePath(uploadedPaths.get(i))
                        .originalFileName(file.getOriginalFilename())
                        .contentType(file.getContentType())
                        .sizeInBytes(file.getSize())
                        .build());
            }
            claimAttachmentRepository.saveAll(attachmentEntities);

            fundsLedgerService.markSuspendedByClaim(bookingId, savedClaim.getClaimId());
        } catch (RuntimeException ex) {
            cleanupUploads(uploadedPaths);
            throw ex;
        }

        log.info("Claim {} created for booking {} by user {}", savedClaim.getClaimId(), bookingId, userId);
        return toResponse(savedClaim, attachmentEntities);
    }

    @Override
    @Transactional(readOnly = true)
    public ClaimEligibilityResponse getEligibility(UUID userId, UUID bookingId) {
        Booking booking = findBookingOrThrow(bookingId);
        return evaluate(userId, booking, null);
    }

    @Override
    @Transactional(readOnly = true)
    public List<ClaimAttachmentUrlResponse> getAttachments(UUID userId, Role role, UUID bookingId, UUID claimId) {
        Booking booking = findBookingOrThrow(bookingId);
        if (participantRole(userId, booking) == null && role != Role.ADMIN) {
            throw new SessionNotParticipantException(
                    "User " + userId + " is not a participant of booking " + bookingId);
        }

        sessionClaimRepository.findByClaimIdAndBooking_BookingId(claimId, bookingId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "CLAIM_NOT_FOUND",
                        "El reclamo no existe.",
                        "Claim not found for id: " + claimId + " on booking " + bookingId));

        return claimAttachmentRepository.findByClaim_ClaimId(claimId).stream()
                .map(attachment -> new ClaimAttachmentUrlResponse(
                        attachment.getAttachmentId(),
                        attachment.getOriginalFileName(),
                        attachment.getContentType(),
                        attachment.getSizeInBytes(),
                        storageService.generateSignedUrl(EVIDENCE_BUCKET, attachment.getStoragePath(),
                                SIGNED_URL_EXPIRATION_SECONDS)))
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public Map<UUID, ClaimEligibilityResponse> evaluateForListing(UUID userId, Collection<Booking> bookings) {
        if (bookings.isEmpty()) {
            return Map.of();
        }

        Set<UUID> bookingIds = bookings.stream().map(Booking::getBookingId).collect(Collectors.toSet());
        Set<UUID> claimedBookingIds = sessionClaimRepository.findClaimedBookingIdsByUser(
                userId, ClaimStatus.OPEN, bookingIds);

        Map<UUID, ClaimEligibilityResponse> result = new HashMap<>();
        for (Booking booking : bookings) {
            boolean knownActiveClaim = claimedBookingIds.contains(booking.getBookingId());
            result.put(booking.getBookingId(), evaluate(userId, booking, knownActiveClaim));
        }
        return result;
    }

    private ClaimEligibilityResponse evaluate(UUID userId, Booking booking, Boolean knownActiveClaim) {
        boolean finalized = deadlineCalculator.isFinalized(booking);
        LocalDateTime claimableUntil = finalized ? deadlineCalculator.deadline(booking) : null;

        if (participantRole(userId, booking) == null) {
            return new ClaimEligibilityResponse(false, claimableUntil, ClaimBlockReason.SESSION_NOT_PARTICIPANT);
        }
        if (!finalized) {
            return new ClaimEligibilityResponse(false, null, ClaimBlockReason.SESSION_NOT_FINALIZED);
        }
        if (!deadlineCalculator.isWithinDeadline(booking)) {
            return new ClaimEligibilityResponse(false, claimableUntil, ClaimBlockReason.CLAIM_DEADLINE_EXCEEDED);
        }

        boolean activeClaim = knownActiveClaim != null
                ? knownActiveClaim
                : sessionClaimRepository.existsByBooking_BookingIdAndClaimant_UserIdAndStatus(
                        booking.getBookingId(), userId, ClaimStatus.OPEN);
        if (activeClaim) {
            return new ClaimEligibilityResponse(false, claimableUntil, ClaimBlockReason.ACTIVE_CLAIM_EXISTS);
        }
        return new ClaimEligibilityResponse(true, claimableUntil, null);
    }

    private RuntimeException toException(ClaimEligibilityResponse eligibility, UUID bookingId, UUID userId) {
        ClaimBlockReason reason = eligibility.blockReason();
        if (reason == ClaimBlockReason.SESSION_NOT_PARTICIPANT) {
            return new SessionNotParticipantException(
                    "User " + userId + " is not a participant of booking " + bookingId);
        }
        if (reason == ClaimBlockReason.SESSION_NOT_FINALIZED) {
            return new SessionNotFinalizedException("Booking " + bookingId + " is not finalized");
        }
        if (reason == ClaimBlockReason.CLAIM_DEADLINE_EXCEEDED) {
            return new ClaimDeadlineExceededException("Claim deadline exceeded for booking " + bookingId);
        }
        return activeClaimExists(bookingId, userId, null);
    }

    private DuplicateResourceException activeClaimExists(UUID bookingId, UUID userId, Throwable cause) {
        String technicalMessage = "Active claim already exists for booking " + bookingId + " and user " + userId;
        if (cause != null) {
            technicalMessage += ": " + cause.getMessage();
        }
        return new DuplicateResourceException("ACTIVE_CLAIM_EXISTS", ACTIVE_CLAIM_MESSAGE, technicalMessage);
    }

    private Role participantRole(UUID userId, Booking booking) {
        if (userId.equals(booking.getStudent().getUserId())) {
            return Role.STUDENT;
        }
        if (userId.equals(booking.getTutor().getUserId())) {
            return Role.TUTOR;
        }
        return null;
    }

    private Booking findBookingOrThrow(UUID bookingId) {
        return bookingRepository.findById(bookingId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "SESSION_NOT_FOUND",
                        "La sesión no existe.",
                        "Booking not found for id: " + bookingId));
    }

    private List<MultipartFile> normalize(List<MultipartFile> files) {
        if (files == null) {
            return List.of();
        }
        return files.stream().filter(Objects::nonNull).toList();
    }

    private void validateAttachments(List<MultipartFile> files) {
        if (files.size() > maxAttachments) {
            throw new ValidationException("No podés adjuntar más de " + maxAttachments + " archivos.");
        }
        for (MultipartFile file : files) {
            if (file.isEmpty()) {
                throw new ValidationException("El archivo adjunto no puede estar vacío.");
            }
            if (file.getSize() > maxAttachmentBytes) {
                throw new ValidationException(
                        "Cada archivo adjunto no puede superar los " + (maxAttachmentBytes / (1024 * 1024)) + "MB.");
            }
            validateFormat(file);
        }
    }

    private void validateFormat(MultipartFile file) {
        String originalFilename = file.getOriginalFilename();
        if (originalFilename == null) {
            throw new FormatNotAllowedException("El formato del archivo no está permitido");
        }
        String extension = getFileExtension(originalFilename).toLowerCase(Locale.ROOT);
        if (!ALLOWED_EXTENSIONS.contains(extension)) {
            throw new FormatNotAllowedException("El formato del archivo no está permitido");
        }
        String contentType = file.getContentType();
        if (contentType == null || !ALLOWED_CONTENT_TYPES.get(extension).contains(contentType)) {
            throw new FormatNotAllowedException("El formato del archivo no está permitido");
        }
    }

    private String getFileExtension(String filename) {
        int dot = filename.lastIndexOf('.');
        return dot < 0 ? "" : filename.substring(dot + 1);
    }

    private String normalizeComment(String comment) {
        if (comment == null || comment.isBlank()) {
            return null;
        }
        return comment.trim();
    }

    private void cleanupUploads(List<String> paths) {
        for (String path : paths) {
            try {
                storageService.delete(EVIDENCE_BUCKET, path);
            } catch (Exception ex) {
                log.warn("Failed to clean up uploaded attachment {}: {}", path, ex.getMessage());
            }
        }
    }

    private ClaimResponse toResponse(SessionClaim claim, List<ClaimAttachment> attachments) {
        List<ClaimAttachmentResponse> attachmentResponses = attachments.stream()
                .map(attachment -> new ClaimAttachmentResponse(
                        attachment.getAttachmentId(),
                        attachment.getOriginalFileName(),
                        attachment.getContentType(),
                        attachment.getSizeInBytes()))
                .toList();

        return new ClaimResponse(
                claim.getClaimId(),
                claim.getBooking().getBookingId(),
                claim.getReason(),
                claim.getComment(),
                claim.getStatus(),
                claim.getClaimantRole(),
                claim.getCreatedAt(),
                attachmentResponses);
    }
}