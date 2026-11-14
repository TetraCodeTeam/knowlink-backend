package com.knowlink.api.community.repositories;

import com.knowlink.api.community.data.models.CommunityPostHelpful;
import com.knowlink.api.community.data.projections.PostHelpfulCount;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ICommunityPostHelpfulRepository extends JpaRepository<CommunityPostHelpful, UUID> {

    boolean existsByPost_PostIdAndUser_UserId(UUID postId, UUID userId);

    Optional<CommunityPostHelpful> findByPost_PostIdAndUser_UserId(UUID postId, UUID userId);

    long countByPost_PostId(UUID postId);

    /** Marcas de varias publicaciones en una sola consulta (el feed no puede hacer una por publicación). */
    @Query("""
            SELECT h.post.postId AS postId, COUNT(h) AS total
            FROM CommunityPostHelpful h
            WHERE h.post.postId IN :postIds
            GROUP BY h.post.postId
            """)
    List<PostHelpfulCount> countByPostIds(@Param("postIds") Collection<UUID> postIds);

    @Query("""
            SELECT h.post.postId FROM CommunityPostHelpful h
            WHERE h.user.userId = :userId AND h.post.postId IN :postIds
            """)
    List<UUID> findMarkedPostIds(@Param("userId") UUID userId, @Param("postIds") Collection<UUID> postIds);

    void deleteByUser_UserId(UUID userId);
}
