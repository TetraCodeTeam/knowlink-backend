package com.knowlink.api.tutors.data.models;

import com.knowlink.api.users.data.models.User;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "tutor_profile")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TutorProfile {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "tutor_profile_id", updatable = false, nullable = false)
    private UUID tutorProfileId;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false, unique = true)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "career_id", nullable = false)
    private Career career;

    @Column(name = "biography", columnDefinition = "TEXT")
    private String biography;

    @Column(name = "profile_picture_url", length = 2048)
    private String profilePictureUrl;

    @Column(name = "institutional_id")
    private String institutionalId;

    @Column(name = "address")
    private String address;

    @Column(name = "mercado_pago_linked", nullable = false)
    @Builder.Default
    private boolean mercadoPagoLinked = false;

    @Column(name = "average_rating")
    private Double averageRating;

    @Column(name = "min_notice_minutes")
    private Integer minNoticeMinutes;

    @Column(name = "verified", nullable = false)
    @Builder.Default
    private boolean verified = false;

    @OneToMany(mappedBy = "tutorProfile", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<TutorSubject> subjects = new ArrayList<>();

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}