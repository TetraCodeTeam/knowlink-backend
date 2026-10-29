package com.knowlink.api.community.repositories;

import com.knowlink.api.community.data.models.CommunityPost;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ICommunityPostRepository extends JpaRepository<CommunityPost, UUID> {

    @EntityGraph(attributePaths = {"author", "subject"})
    Page<CommunityPost> findByDeletedAtIsNull(Pageable pageable);

    @EntityGraph(attributePaths = {"author", "subject"})
    Page<CommunityPost> findByAuthor_UserIdAndDeletedAtIsNull(UUID authorId, Pageable pageable);

    @EntityGraph(attributePaths = {"author", "subject"})
    @Query(value = """
            SELECT p FROM CommunityPost p
            WHERE p.author.userId = :authorId
              AND p.deletedAt IS NULL
              AND p.createdAt >= :from
              AND p.createdAt < :to
            """, countQuery = """
            SELECT COUNT(p) FROM CommunityPost p
            WHERE p.author.userId = :authorId
              AND p.deletedAt IS NULL
              AND p.createdAt >= :from
              AND p.createdAt < :to
            """)
    Page<CommunityPost> findByAuthorAndCreatedBetween(@Param("authorId") UUID authorId,
            @Param("from") LocalDateTime from, @Param("to") LocalDateTime to, Pageable pageable);

    @EntityGraph(attributePaths = {"author", "subject"})
    Optional<CommunityPost> findByPostIdAndDeletedAtIsNull(UUID postId);
}
