package com.knowlink.api.community.repositories;

import com.knowlink.api.community.data.models.CommunityReplyHelpful;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ICommunityReplyHelpfulRepository extends JpaRepository<CommunityReplyHelpful, UUID> {

    boolean existsByReply_ReplyIdAndUser_UserId(UUID replyId, UUID userId);

    Optional<CommunityReplyHelpful> findByReply_ReplyIdAndUser_UserId(UUID replyId, UUID userId);

    long countByReply_ReplyId(UUID replyId);

    @Query("""
            SELECT h.reply.replyId FROM CommunityReplyHelpful h
            WHERE h.user.userId = :userId AND h.reply.replyId IN :replyIds
            """)
    List<UUID> findMarkedReplyIds(@Param("userId") UUID userId, @Param("replyIds") Collection<UUID> replyIds);
}
