package com.knowlink.api.community.repositories;

import com.knowlink.api.community.data.models.CommunityReply;
import com.knowlink.api.community.data.projections.PostReplyCount;
import com.knowlink.api.community.data.projections.ReplyHelpfulCount;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ICommunityReplyRepository extends JpaRepository<CommunityReply, UUID> {

    @EntityGraph(attributePaths = {"author"})
    List<CommunityReply> findByPost_PostIdAndDeletedAtIsNullOrderByCreatedAtAsc(UUID postId);

    @EntityGraph(attributePaths = {"author"})
    List<CommunityReply> findByReplyIdInAndDeletedAtIsNull(Collection<UUID> replyIds);

    @EntityGraph(attributePaths = {"author"})
    Optional<CommunityReply> findByReplyIdAndPost_PostIdAndDeletedAtIsNull(UUID replyId, UUID postId);

    @Query("""
            SELECT r.post.postId AS postId, COUNT(r) AS total
            FROM CommunityReply r
            WHERE r.post.postId IN :postIds AND r.deletedAt IS NULL
            GROUP BY r.post.postId
            """)
    List<PostReplyCount> countRepliesByPostIds(@Param("postIds") Collection<UUID> postIds);

    /** Respuestas de usuarios distintos del autor de la publicación. */
    @Query("""
            SELECT r.post.postId AS postId, COUNT(r) AS total
            FROM CommunityReply r
            WHERE r.post.postId IN :postIds
              AND r.deletedAt IS NULL
              AND r.author.userId <> r.post.author.userId
            GROUP BY r.post.postId
            """)
    List<PostReplyCount> countRepliesFromOthersByPostIds(@Param("postIds") Collection<UUID> postIds);

    boolean existsByPost_PostIdAndAuthor_UserIdNotAndDeletedAtIsNull(UUID postId, UUID authorId);

    @Query("""
            SELECT r.post.postId AS postId,
                   r.replyId AS replyId,
                   (SELECT COUNT(h) FROM CommunityReplyHelpful h WHERE h.reply = r) AS total
            FROM CommunityReply r
            WHERE r.post.postId IN :postIds AND r.deletedAt IS NULL
            """)
    List<ReplyHelpfulCount> countHelpfulByPostIds(@Param("postIds") Collection<UUID> postIds);
}
