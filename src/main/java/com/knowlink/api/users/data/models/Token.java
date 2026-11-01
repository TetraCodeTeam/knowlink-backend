package com.knowlink.api.users.data.models;

import com.knowlink.api.users.data.enums.TokenType;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

import static com.knowlink.api.users.utils.Constants.ACCOUNT_CONFIRMATION_TOKEN_HOURS;

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

    @Enumerated(EnumType.STRING)
    @Column(name = "token_type", nullable = false, length = 30,
            columnDefinition = "VARCHAR(30) NOT NULL DEFAULT 'ACCOUNT_CONFIRMATION'")
    @Builder.Default
    private TokenType tokenType = TokenType.ACCOUNT_CONFIRMATION;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private User user;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        if (tokenExpirationDate == null) {
            tokenExpirationDate = createdAt.plusHours(ACCOUNT_CONFIRMATION_TOKEN_HOURS);
        }
    }
}