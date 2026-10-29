package com.knowlink.api.community.data.models;

import com.knowlink.api.security.enums.Role;
import com.knowlink.api.shared.utils.AppTimeZone;
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
@Table(name = "community_reply", indexes = {
        @Index(name = "idx_community_reply_post", columnList = "post_id, deleted_at, created_at"),
        @Index(name = "idx_community_reply_author", columnList = "author_id")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CommunityReply {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "reply_id", updatable = false, nullable = false)
    private UUID replyId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "post_id", nullable = false)
    private CommunityPost post;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "author_id", nullable = false)
    private User author;

    /** Rol activo del autor al responder: solo una respuesta como TUTOR puede ofrecer reservar una clase. */
    @Enumerated(EnumType.STRING)
    @Column(name = "author_role", nullable = false, length = 10)
    private Role authorRole;

    @Column(name = "content", nullable = false, columnDefinition = "TEXT")
    private String content;

    @Embedded
    private CommunityAttachment attachment;

    @Column(name = "include_booking_card", nullable = false)
    private boolean includeBookingCard;

    @Column(name = "booking_card_question", length = 150)
    private String bookingCardQuestion;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

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
