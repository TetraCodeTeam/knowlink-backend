package com.knowlink.api.claims.services.implementations;

import com.knowlink.api.claims.controllers.responses.ClaimReviewProgressResponse;
import com.knowlink.api.claims.data.enums.ClaimStatus;
import com.knowlink.api.claims.data.models.SessionClaim;
import com.knowlink.api.claims.repositories.ClaimAttachmentRepository;
import com.knowlink.api.claims.repositories.SessionClaimCommentRepository;
import com.knowlink.api.claims.repositories.SessionClaimRepository;
import com.knowlink.api.materials.service.interfaces.ISupabaseStorageService;
import com.knowlink.api.payments.repositories.IFundsTransferRepository;
import com.knowlink.api.support.config.SupportProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ClaimSupportDetailsServiceImplTest {

    private static final LocalDateTime CREATED_AT = LocalDateTime.of(2026, 10, 20, 10, 0);

    @Mock
    private SessionClaimRepository sessionClaimRepository;

    @Mock
    private ClaimAttachmentRepository claimAttachmentRepository;

    @Mock
    private SessionClaimCommentRepository sessionClaimCommentRepository;

    @Mock
    private ISupabaseStorageService storageService;

    @Mock
    private IFundsTransferRepository fundsTransferRepository;

    private ClaimSupportDetailsServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new ClaimSupportDetailsServiceImpl(
                sessionClaimRepository,
                claimAttachmentRepository,
                sessionClaimCommentRepository,
                storageService,
                fundsTransferRepository,
                new SupportProperties("Equipo de Propiedad Intelectual", 24,
                        new SupportProperties.MaterialDefense(5, 10)));
    }

    @Test
    @DisplayName("getReviewProgress - el turno cuenta disputas pendientes y expone el total")
    void getReviewProgress_openDispute_countsDisputesNotClaims() {
        SessionClaim claim = claim(ClaimStatus.OPEN, null);
        when(sessionClaimRepository.countOpenDisputesInQueueThrough(CREATED_AT, claim.getClaimId()))
                .thenReturn(1L);
        when(sessionClaimRepository.countOpenDisputes()).thenReturn(5L);

        ClaimReviewProgressResponse progress = service.getReviewProgress(claim);

        assertThat(progress.currentPhase()).isEqualTo("MEDIATOR_ASSIGNMENT_QUEUED");
        assertThat(progress.queuePosition()).isEqualTo(1);
        assertThat(progress.queueTotal()).isEqualTo(5);
    }

    @Test
    @DisplayName("getReviewProgress - el reclamo de la contraparte hereda el turno del representante")
    void getReviewProgress_counterpartClaim_inheritsRepresentativeQueuePosition() {
        SessionClaim representative = claim(ClaimStatus.OPEN, null);
        SessionClaim counterpart = claim(ClaimStatus.OPEN, representative.getClaimId());
        when(sessionClaimRepository.findById(representative.getClaimId()))
                .thenReturn(Optional.of(representative));
        when(sessionClaimRepository.countOpenDisputesInQueueThrough(
                representative.getCreatedAt(), representative.getClaimId())).thenReturn(3L);
        when(sessionClaimRepository.countOpenDisputes()).thenReturn(4L);

        ClaimReviewProgressResponse progress = service.getReviewProgress(counterpart);

        assertThat(progress.queuePosition()).isEqualTo(3);
        assertThat(progress.queueTotal()).isEqualTo(4);
        verify(sessionClaimRepository).findById(representative.getClaimId());
        verify(sessionClaimRepository, never()).countOpenDisputesInQueueThrough(
                counterpart.getCreatedAt(), counterpart.getClaimId());
    }

    @Test
    @DisplayName("getReviewProgress - el turno baja cuando el caso anterior pasa a IN_REVIEW")
    void getReviewProgress_previousCaseAssigned_decreasesQueuePosition() {
        SessionClaim claim = claim(ClaimStatus.OPEN, null);
        when(sessionClaimRepository.countOpenDisputesInQueueThrough(CREATED_AT, claim.getClaimId()))
                .thenReturn(2L, 1L);
        when(sessionClaimRepository.countOpenDisputes()).thenReturn(2L, 1L);

        ClaimReviewProgressResponse before = service.getReviewProgress(claim);
        ClaimReviewProgressResponse after = service.getReviewProgress(claim);

        assertThat(before.queuePosition()).isEqualTo(2);
        assertThat(after.queuePosition()).isEqualTo(1);
    }

    @Test
    @DisplayName("getReviewProgress - IN_REVIEW no informa turno")
    void getReviewProgress_inReview_hasNullQueue() {
        SessionClaim claim = claim(ClaimStatus.IN_REVIEW, null);

        ClaimReviewProgressResponse progress = service.getReviewProgress(claim);

        assertThat(progress.currentPhase()).isEqualTo("IN_REVIEW");
        assertThat(progress.queuePosition()).isNull();
        assertThat(progress.queueTotal()).isNull();
        verify(sessionClaimRepository, never()).countOpenDisputesInQueueThrough(any(), any());
        verify(sessionClaimRepository, never()).countOpenDisputes();
    }

    @Test
    @DisplayName("getReviewProgress - RESOLVED no informa turno")
    void getReviewProgress_resolved_hasNullQueue() {
        SessionClaim claim = claim(ClaimStatus.RESOLVED, null);

        ClaimReviewProgressResponse progress = service.getReviewProgress(claim);

        assertThat(progress.currentPhase()).isEqualTo("RESOLVED");
        assertThat(progress.queuePosition()).isNull();
        assertThat(progress.queueTotal()).isNull();
        verify(sessionClaimRepository, never()).countOpenDisputesInQueueThrough(any(), any());
        verify(sessionClaimRepository, never()).countOpenDisputes();
    }

    @Test
    @DisplayName("getReviewProgress - si el representante no existe usa el reclamo actual")
    void getReviewProgress_missingRepresentative_fallsBackToCurrentClaim() {
        SessionClaim counterpart = claim(ClaimStatus.OPEN, UUID.randomUUID());
        when(sessionClaimRepository.findById(counterpart.getDisputeId())).thenReturn(Optional.empty());
        when(sessionClaimRepository.countOpenDisputesInQueueThrough(
                counterpart.getCreatedAt(), counterpart.getClaimId())).thenReturn(1L);
        when(sessionClaimRepository.countOpenDisputes()).thenReturn(1L);

        ClaimReviewProgressResponse progress = service.getReviewProgress(counterpart);

        assertThat(progress.queuePosition()).isEqualTo(1);
        assertThat(progress.queueTotal()).isEqualTo(1);
    }

    private SessionClaim claim(ClaimStatus status, UUID disputeId) {
        UUID claimId = UUID.randomUUID();
        return SessionClaim.builder()
                .claimId(claimId)
                .disputeId(disputeId != null ? disputeId : claimId)
                .status(status)
                .createdAt(CREATED_AT)
                .build();
    }
}