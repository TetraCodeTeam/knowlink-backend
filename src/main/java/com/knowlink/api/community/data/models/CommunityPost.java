package com.knowlink.api.community.data.models;

import com.knowlink.api.community.data.enums.PostStatus;
import com.knowlink.api.community.data.enums.PostType;
import com.knowlink.api.security.enums.Role;
import com.knowlink.api.shared.utils.AppTimeZone;
import com.knowlink.api.tutors.data.models.Subject;
import com.knowlink.api.users.data.models.User;
import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "community_post", indexes = {
        @Index(name = "idx_community_post_feed", columnList = "deleted_at, created_at"),
        @Index(name = "idx_community_post_author", columnList = "author_id, created_at")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CommunityPost {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "post_id", updatable = false, nullable = false)
    private UUID postId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "author_id", nullable = false)
    private User author;

    /** Rol activo del autor al publicar: define cómo se muestra al autor en el hilo. */
    @Enumerated(EnumType.STRING)
    @Column(name = "author_role", nullable = false, length = 10)
    private Role authorRole;

    @Enumerated(EnumType.STRING)
    @Column(name = "post_type", nullable = false, length = 20)
    private PostType type;

    @Enumerated(EnumType.STRING)
    @Column(name = "post_status", nullable = false, length = 20)
    private PostStatus status;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "subject_id", nullable = false)
    private Subject subject;

    @Column(name = "title", nullable = false, length = 150)
    private String title;

    @Column(name = "content", nullable = false, columnDefinition = "TEXT")
    private String content;

    @Embedded
    private CommunityAttachment attachment;

    /** Respuesta destacada, fijada al momento de marcar la publicación como resuelta. */
    @Column(name = "featured_reply_id")
    private UUID featuredReplyId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "resolved_at")
    private LocalDateTime resolvedAt;

    @Column(name = "edited_at")
    private LocalDateTime editedAt;

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now(AppTimeZone.ZONE);
        }
    }
}
