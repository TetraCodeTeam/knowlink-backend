package com.knowlink.api.claims.data.models;

import com.knowlink.api.bookings.data.models.Booking;
import com.knowlink.api.claims.data.enums.ClaimReason;
import com.knowlink.api.claims.data.enums.ClaimStatus;
import com.knowlink.api.security.enums.Role;
import com.knowlink.api.shared.utils.AppTimeZone;
import com.knowlink.api.users.data.models.User;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "session_claim", uniqueConstraints = @UniqueConstraint(name = "uk_session_claim_active_key", columnNames = {
        "active_key" }), indexes = {
                @Index(name = "idx_session_claim_booking_id", columnList = "booking_id"),
                @Index(name = "idx_session_claim_user_status", columnList = "user_id, status") })
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SessionClaim {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "claim_id", updatable = false, nullable = false)
    private UUID claimId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "booking_id", nullable = false)
    private Booking booking;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User claimant;

    @Enumerated(EnumType.STRING)
    @Column(name = "claimant_role", nullable = false, length = 10)
    private Role claimantRole;

    @Enumerated(EnumType.STRING)
    @Column(name = "reason", nullable = false, length = 40)
    private ClaimReason reason;

    @Column(name = "comment", length = 500)
    private String comment;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private ClaimStatus status;

    @Column(name = "active_key", length = 100)
    private String activeKey;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now(AppTimeZone.ZONE);
        this.updatedAt = LocalDateTime.now(AppTimeZone.ZONE);
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = LocalDateTime.now(AppTimeZone.ZONE);
    }
}