package com.knowlink.api.claims.services.implementations;

import com.knowlink.api.claims.controllers.responses.ClaimAttachmentUrlResponse;
import com.knowlink.api.claims.controllers.responses.ClaimCommentResponse;
import com.knowlink.api.claims.controllers.responses.ClaimFundsSummaryResponse;
import com.knowlink.api.claims.controllers.responses.ClaimReviewProgressResponse;
import com.knowlink.api.claims.data.enums.ClaimStatus;
import com.knowlink.api.claims.data.models.SessionClaim;
import com.knowlink.api.claims.data.models.SessionClaimComment;
import com.knowlink.api.claims.repositories.ClaimAttachmentRepository;
import com.knowlink.api.claims.repositories.SessionClaimCommentRepository;
import com.knowlink.api.claims.repositories.SessionClaimRepository;
import com.knowlink.api.claims.services.interfaces.IClaimSupportDetailsService;
import com.knowlink.api.claims.utils.ClaimSupportConstants;
import com.knowlink.api.bookings.data.models.Booking;
import com.knowlink.api.materials.service.interfaces.ISupabaseStorageService;
import com.knowlink.api.materials.utils.StorageConstants;
import com.knowlink.api.payments.data.models.FundsTransfer;
import com.knowlink.api.payments.domain.FundsStatus;
import com.knowlink.api.payments.repositories.IFundsTransferRepository;
import com.knowlink.api.shared.utils.AppTimeZone;
import com.knowlink.api.support.config.SupportProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ClaimSupportDetailsServiceImpl implements IClaimSupportDetailsService {

    private final SessionClaimRepository sessionClaimRepository;
    private final ClaimAttachmentRepository claimAttachmentRepository;
    private final SessionClaimCommentRepository sessionClaimCommentRepository;
    private final ISupabaseStorageService storageService;
    private final IFundsTransferRepository fundsTransferRepository;
    private final SupportProperties supportProperties;

    @Override
    public ClaimFundsSummaryResponse getFundsSummary(Booking booking, ClaimStatus claimStatus) {
        FundsTransfer transfer = fundsTransferRepository.findByBooking_BookingId(booking.getBookingId())
                .orElse(null);
        BigDecimal originalAmount = transfer == null ? booking.getAmount() : transfer.getOriginalAmount();
        FundsStatus status = transfer == null ? null : transfer.getFundsStatus();
        boolean activeClaim = ClaimStatus.ACTIVE_STATUSES.contains(claimStatus);
        boolean transferHeld = status == FundsStatus.HELD || status == FundsStatus.SUSPENDED_BY_CLAIM;
        boolean retained = activeClaim || transferHeld;
        BigDecimal protectedAmount = retained ? originalAmount : BigDecimal.ZERO;
        BigDecimal protectedPercentage = originalAmount.signum() == 0
                ? BigDecimal.ZERO
                : protectedAmount.multiply(BigDecimal.valueOf(100))
                        .divide(originalAmount, 2, RoundingMode.HALF_UP);

        return new ClaimFundsSummaryResponse(
                originalAmount,
                protectedAmount,
                protectedPercentage,
                status,
                retained,
                ClaimSupportConstants.CURRENCY);
    }

    @Override
    public ClaimReviewProgressResponse getReviewProgress(SessionClaim claim) {
        String phase = switch (claim.getStatus()) {
            case OPEN -> "MEDIATOR_ASSIGNMENT_QUEUED";
            case IN_REVIEW -> "IN_REVIEW";
            case RESOLVED -> "RESOLVED";
        };
        LocalDateTime dueAt = claim.getCreatedAt().plusHours(supportProperties.claimResponseHours());
        long remainingMinutes = Math.max(0,
                Duration.between(LocalDateTime.now(AppTimeZone.ZONE), dueAt).toMinutes());
        Integer queuePosition = claim.getStatus() == ClaimStatus.OPEN
                ? Math.toIntExact(sessionClaimRepository.countOpenClaimsInQueueThrough(
                        claim.getCreatedAt(), claim.getClaimId()))
                : null;

        return new ClaimReviewProgressResponse(phase, queuePosition, dueAt, remainingMinutes);
    }

    @Override
    public List<ClaimAttachmentUrlResponse> getAttachments(UUID claimId) {
        return claimAttachmentRepository.findByClaim_ClaimId(claimId).stream()
                .map(attachment -> new ClaimAttachmentUrlResponse(
                        attachment.getAttachmentId(),
                        attachment.getOriginalFileName(),
                        attachment.getContentType(),
                        attachment.getSizeInBytes(),
                        storageService.generateSignedUrl(
                                ClaimSupportConstants.EVIDENCE_BUCKET,
                                attachment.getStoragePath(),
                                StorageConstants.SIGNED_URL_EXPIRATION_SECONDS)))
                .toList();
    }

    @Override
    public List<ClaimCommentResponse> getComments(UUID claimId) {
        return sessionClaimCommentRepository.findByClaim_ClaimIdOrderByCreatedAtAsc(claimId).stream()
                .map(this::toCommentResponse)
                .toList();
    }

    @Override
    public ClaimCommentResponse toCommentResponse(SessionClaimComment comment) {
        return new ClaimCommentResponse(
                comment.getCommentId(),
                comment.getAuthor().getFullName(),
                comment.getAuthor().getRole(),
                comment.getMessage(),
                comment.getCreatedAt());
    }
}
