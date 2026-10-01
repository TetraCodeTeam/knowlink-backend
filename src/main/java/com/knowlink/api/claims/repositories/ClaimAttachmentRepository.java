package com.knowlink.api.claims.repositories;

import com.knowlink.api.claims.data.models.ClaimAttachment;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface ClaimAttachmentRepository extends JpaRepository<ClaimAttachment, UUID> {

    List<ClaimAttachment> findByClaim_ClaimId(UUID claimId);
}