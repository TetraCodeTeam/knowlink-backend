package com.knowlink.api.admin.claims.services.interfaces;

import com.knowlink.api.admin.claims.controllers.requests.ResolveSessionClaimRequest;
import com.knowlink.api.admin.claims.controllers.responses.ClaimContactInfoResponse;
import com.knowlink.api.admin.claims.controllers.responses.SessionClaimDetailResponse;
import com.knowlink.api.admin.claims.controllers.responses.SessionClaimListResponse;
import com.knowlink.api.claims.data.enums.ClaimStatus;

import java.time.LocalDate;
import java.util.UUID;

public interface IAdminSessionClaimService {

    SessionClaimListResponse list(ClaimStatus status, boolean assignedToMe, LocalDate from, LocalDate to,
            Integer page, Integer size, UUID adminUserId);

    SessionClaimDetailResponse getDetail(UUID claimId, UUID adminUserId);

    SessionClaimDetailResponse assign(UUID claimId, UUID adminUserId);

    ClaimContactInfoResponse getContactInfo(UUID claimId, UUID adminUserId);

    SessionClaimDetailResponse resolve(UUID claimId, UUID adminUserId, ResolveSessionClaimRequest request);
}