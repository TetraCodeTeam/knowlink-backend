package com.knowlink.api.tutors.support.services.implementations;

import com.knowlink.api.claims.controllers.responses.ClaimAttachmentUrlResponse;
import com.knowlink.api.claims.controllers.responses.ClaimCommentResponse;
import com.knowlink.api.claims.data.enums.ClaimStatus;
import com.knowlink.api.claims.data.models.SessionClaim;
import com.knowlink.api.claims.data.models.SessionClaimComment;
import com.knowlink.api.claims.repositories.SessionClaimCommentRepository;
import com.knowlink.api.claims.repositories.SessionClaimRepository;
import com.knowlink.api.claims.services.interfaces.IClaimSupportDetailsService;
import com.knowlink.api.exceptions.custom_exceptions.DuplicateResourceException;
import com.knowlink.api.exceptions.custom_exceptions.ResourceNotFoundException;
import com.knowlink.api.notifications.data.enums.NotificationType;
import com.knowlink.api.notifications.services.interfaces.INotificationService;
import com.knowlink.api.security.enums.Role;
import com.knowlink.api.students.data.models.StudentProfile;
import com.knowlink.api.students.repositories.IStudentProfileRepository;
import com.knowlink.api.claims.controllers.requests.AddClaimCommentRequest;
import com.knowlink.api.tutors.support.controllers.responses.TutorSupportClaimDetailResponse;
import com.knowlink.api.tutors.support.controllers.responses.TutorSupportClaimResponse;
import com.knowlink.api.tutors.support.data.mappers.TutorSupportMapper;
import com.knowlink.api.tutors.support.services.interfaces.ITutorSessionSupportService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class TutorSessionSupportService implements ITutorSessionSupportService {

    private final SessionClaimRepository sessionClaimRepository;
    private final SessionClaimCommentRepository claimCommentRepository;
    private final IClaimSupportDetailsService claimSupportDetailsService;
    private final IStudentProfileRepository studentProfileRepository;
    private final INotificationService notificationService;
    private final TutorSupportMapper tutorSupportMapper;

    @Override
    @Transactional(readOnly = true)
    public List<TutorSupportClaimResponse> getClaims(UUID tutorUserId) {
        return sessionClaimRepository.findByBooking_Tutor_UserIdOrderByCreatedAtDesc(tutorUserId).stream()
                .map(tutorSupportMapper::toClaimResponse)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public TutorSupportClaimDetailResponse getClaimDetails(UUID tutorUserId, UUID claimId) {
        SessionClaim claim = findClaimOrThrow(tutorUserId, claimId);
        var booking = claim.getBooking();
        var student = booking.getStudent();
        StudentProfile studentProfile = studentProfileRepository.findByUserId(student.getUserId()).orElse(null);
        String studentCareer = studentProfile == null ? null : studentProfile.getCareer().getName();
        String studentPicture = studentProfile == null
                ? student.getProfilePictureUrl()
                : studentProfile.getProfilePictureUrl();

        return tutorSupportMapper.toClaimDetails(
                claim,
                studentPicture,
                studentCareer,
                claimSupportDetailsService.getFundsSummary(booking, claim.getStatus()),
                claimSupportDetailsService.getReviewProgress(claim),
                claimSupportDetailsService.getAttachments(claimId),
                claimSupportDetailsService.getComments(claimId));
    }

    @Override
    @Transactional
    public ClaimCommentResponse addClaimComment(UUID tutorUserId, UUID claimId, AddClaimCommentRequest request) {
        SessionClaim claim = findClaimOrThrow(tutorUserId, claimId);
        if (claim.getStatus() == ClaimStatus.RESOLVED) {
            throw new DuplicateResourceException(
                    "CLAIM_ALREADY_RESOLVED",
                    "No podés comentar un reclamo que ya fue resuelto.",
                    "Comment attempted on resolved claim " + claimId);
        }

        SessionClaimComment comment = claimCommentRepository.save(SessionClaimComment.builder()
                .claim(claim)
                .author(claim.getBooking().getTutor())
                .message(request.comment())
                .build());
        notificationService.createSupportNotification(
                claim.getBooking().getStudent().getUserId(),
                Role.STUDENT,
                NotificationType.SESSION_CLAIM_COMMENT,
                claim.getClaimId(),
                "Nuevo comentario en un reclamo",
                "El tutor agregó un comentario al reclamo de la sesión de "
                        + claim.getBooking().getTutorSubject().getSubject().getName() + ".");
        return claimSupportDetailsService.toCommentResponse(comment);
    }

    private SessionClaim findClaimOrThrow(UUID tutorUserId, UUID claimId) {
        return sessionClaimRepository.findByClaimIdAndTutorUserId(claimId, tutorUserId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "CLAIM_NOT_FOUND",
                        "El reclamo no existe.",
                        "Claim " + claimId + " not found for tutor " + tutorUserId));
    }
}
