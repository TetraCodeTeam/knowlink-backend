package com.knowlink.api.admin.claims.controllers.implementations;

import com.knowlink.api.admin.claims.controllers.interfaces.IAdminSessionClaimController;
import com.knowlink.api.admin.claims.controllers.requests.ResolveSessionClaimRequest;
import com.knowlink.api.admin.claims.controllers.responses.ClaimContactInfoResponse;
import com.knowlink.api.admin.claims.controllers.responses.SessionClaimDetailResponse;
import com.knowlink.api.admin.claims.controllers.responses.SessionClaimListResponse;
import com.knowlink.api.admin.claims.services.interfaces.IAdminSessionClaimService;
import com.knowlink.api.claims.data.enums.ClaimStatus;
import com.knowlink.api.security.models.UserPrincipal;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
@Slf4j
@PreAuthorize("hasRole('ADMIN')")
public class AdminSessionClaimControllerImpl implements IAdminSessionClaimController {

    private final IAdminSessionClaimService adminSessionClaimService;

    @Override
    public SessionClaimListResponse list(UserPrincipal principal, ClaimStatus status, boolean assignedToMe,
            LocalDate from, LocalDate to, Integer page, Integer size) {
        return adminSessionClaimService.list(
                status, assignedToMe, from, to, page, size, principal.getUser().getUserId());
    }

    @Override
    public SessionClaimDetailResponse getDetail(UserPrincipal principal, UUID claimId) {
        return adminSessionClaimService.getDetail(claimId, principal.getUser().getUserId());
    }

    @Override
    public SessionClaimDetailResponse assign(UserPrincipal principal, UUID claimId) {
        return adminSessionClaimService.assign(claimId, principal.getUser().getUserId());
    }

    @Override
    public ResponseEntity<ClaimContactInfoResponse> getContactInfo(UserPrincipal principal, UUID claimId) {
        ClaimContactInfoResponse response = adminSessionClaimService.getContactInfo(
                claimId, principal.getUser().getUserId());
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(response);
    }

    @Override
    public SessionClaimDetailResponse resolve(UserPrincipal principal, UUID claimId,
            ResolveSessionClaimRequest request) {
        return adminSessionClaimService.resolve(claimId, principal.getUser().getUserId(), request);
    }
}