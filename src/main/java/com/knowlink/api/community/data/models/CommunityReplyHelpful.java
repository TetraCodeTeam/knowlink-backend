package com.knowlink.api.community.data.models;

import com.knowlink.api.shared.utils.AppTimeZone;
import com.knowlink.api.users.data.models.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.UUID;

/** Marca "Esto me sirvió" de un usuario sobre una respuesta. */
@Entity
@Table(name = "community_reply_helpful",
        uniqueConstraints = @UniqueConstraint(name = "uk_community_helpful_reply_user",
                columnNames = {"reply_id", "user_id"}),
        indexes = @Index(name = "idx_community_helpful_created", columnList = "created_at"))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CommunityReplyHelpful {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "helpful_id", updatable = false, nullable = false)
    private UUID helpfulId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reply_id", nullable = false)
    private CommunityReply reply;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now(AppTimeZone.ZONE);
        }
    }
}
