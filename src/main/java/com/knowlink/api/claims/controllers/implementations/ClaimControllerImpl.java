package com.knowlink.api.claims.controllers.implementations;

import com.knowlink.api.claims.controllers.interfaces.IClaimController;
import com.knowlink.api.claims.controllers.requests.CreateClaimRequest;
import com.knowlink.api.claims.controllers.responses.ClaimAttachmentUrlResponse;
import com.knowlink.api.claims.controllers.responses.ClaimEligibilityResponse;
import com.knowlink.api.claims.controllers.responses.ClaimResponse;
import com.knowlink.api.claims.services.interfaces.ISessionClaimService;
import com.knowlink.api.security.models.UserPrincipal;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
public class ClaimControllerImpl implements IClaimController {

    private final ISessionClaimService sessionClaimService;

    @Override
    public ClaimResponse create(UserPrincipal principal, UUID bookingId, CreateClaimRequest request,
            List<MultipartFile> files) {
        return sessionClaimService.create(principal.getUser().getUserId(), bookingId, request, files);
    }

    @Override
    public ClaimEligibilityResponse getEligibility(UserPrincipal principal, UUID bookingId) {
        return sessionClaimService.getEligibility(principal.getUser().getUserId(), bookingId);
    }

    @Override
    public List<ClaimAttachmentUrlResponse> getAttachments(UserPrincipal principal, UUID bookingId, UUID claimId) {
        return sessionClaimService.getAttachments(
                principal.getUser().getUserId(),
                principal.getUser().getRole(),
                bookingId,
                claimId);
    }
}