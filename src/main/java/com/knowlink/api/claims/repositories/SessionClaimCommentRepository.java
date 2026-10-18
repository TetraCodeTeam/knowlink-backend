package com.knowlink.api.claims.repositories;

import com.knowlink.api.claims.data.models.SessionClaimComment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface SessionClaimCommentRepository extends JpaRepository<SessionClaimComment, UUID> {

    List<SessionClaimComment> findByClaim_ClaimIdOrderByCreatedAtAsc(UUID claimId);
}
