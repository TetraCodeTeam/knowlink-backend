package com.knowlink.api.claims.services.interfaces;

import com.knowlink.api.bookings.data.models.Booking;
import com.knowlink.api.claims.controllers.requests.CreateClaimRequest;
import com.knowlink.api.claims.controllers.responses.ClaimAttachmentUrlResponse;
import com.knowlink.api.claims.controllers.responses.ClaimEligibilityResponse;
import com.knowlink.api.claims.controllers.responses.ClaimResponse;
import com.knowlink.api.security.enums.Role;

import org.springframework.web.multipart.MultipartFile;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public interface ISessionClaimService {

    ClaimResponse create(UUID userId, UUID bookingId, CreateClaimRequest request, List<MultipartFile> files);

    ClaimEligibilityResponse getEligibility(UUID userId, UUID bookingId);

    List<ClaimAttachmentUrlResponse> getAttachments(UUID userId, Role role, UUID bookingId, UUID claimId);

    Map<UUID, ClaimEligibilityResponse> evaluateForListing(UUID userId, Collection<Booking> bookings);
}