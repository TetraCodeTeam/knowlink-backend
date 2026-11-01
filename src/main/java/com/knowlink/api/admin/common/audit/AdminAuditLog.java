package com.knowlink.api.admin.common.audit;

import com.knowlink.api.shared.utils.AppTimeZone;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
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
@Table(name = "admin_audit_log", indexes = {
        @Index(name = "idx_admin_audit_admin_created", columnList = "admin_user_id, created_at"),
        @Index(name = "idx_admin_audit_target", columnList = "target_type, target_id")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AdminAuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "admin_audit_log_id", updatable = false, nullable = false)
    private UUID adminAuditLogId;

    @Column(name = "admin_user_id", nullable = false)
    private UUID adminUserId;

    @Enumerated(EnumType.STRING)
    @Column(name = "action", nullable = false, length = 40)
    private AdminAuditAction action;

    @Column(name = "target_type", nullable = false, length = 40)
    private String targetType;

    @Column(name = "target_id", nullable = false)
    private UUID targetId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now(AppTimeZone.ZONE);
        }
    }
}