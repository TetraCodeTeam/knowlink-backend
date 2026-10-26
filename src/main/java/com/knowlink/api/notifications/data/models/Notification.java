package com.knowlink.api.notifications.data.models;

import com.knowlink.api.notifications.data.enums.NotificationType;
import com.knowlink.api.security.enums.Role;
import com.knowlink.api.shared.utils.AppTimeZone;
import com.knowlink.api.users.data.models.User;
import jakarta.persistence.Column;
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
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "user_notification", indexes = {
        @Index(name = "idx_user_notification_recipient_created", columnList = "recipient_user_id, recipient_role, created_at"),
        @Index(name = "idx_user_notification_recipient_read", columnList = "recipient_user_id, recipient_role, read_at")
}, uniqueConstraints = {
        @UniqueConstraint(name = "uk_user_notification_recipient_dedupe",
                columnNames = {"recipient_user_id", "dedupe_key"})
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Notification {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "notification_id", updatable = false, nullable = false)
    private UUID notificationId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "recipient_user_id", nullable = false)
    private User recipient;

    @Enumerated(EnumType.STRING)
    @Column(name = "recipient_role", nullable = false, length = 10)
    private Role recipientRole;

    @Enumerated(EnumType.STRING)
    @Column(name = "notification_type", nullable = false, length = 40)
    private NotificationType type;

    @Column(name = "resource_id")
    private UUID resourceId;

    @Column(name = "title", length = 120)
    private String title;

    @Column(name = "message", nullable = false, length = 500)
    private String message;

    @Column(name = "dedupe_key", length = 150)
    private String dedupeKey;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "read_at")
    private LocalDateTime readAt;

    @Column(name = "expires_at")
    private LocalDateTime expiresAt;

    @Column(name = "resolved_at")
    private LocalDateTime resolvedAt;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now(AppTimeZone.ZONE);
        }
    }
}