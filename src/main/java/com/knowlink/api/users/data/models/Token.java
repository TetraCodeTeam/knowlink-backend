package com.knowlink.api.users.data.models;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "tokens")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
@ToString(onlyExplicitlyIncluded = true)
public class Token {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "token_id", updatable = false, nullable = false)
    @EqualsAndHashCode.Include
    @ToString.Include
    private UUID tokenId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "token_expiration_date", nullable = false)
    private LocalDateTime tokenExpirationDate;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private User user;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        tokenExpirationDate = LocalDateTime.now().plusHours(24);
    }
}