package com.knowlink.api.payments.data.models;

import com.knowlink.api.bookings.data.models.Booking;
import com.knowlink.api.payments.domain.FundsRecipient;
import com.knowlink.api.payments.domain.FundsStatus;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "funds_transfer", uniqueConstraints = @UniqueConstraint(name = "uk_funds_transfer_booking", columnNames = {"booking_id"}))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FundsTransfer {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "funds_transfer_id", updatable = false, nullable = false)
    private UUID fundsTransferId;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "booking_id", nullable = false, unique = true)
    private Booking booking;

    @Column(name = "original_amount", nullable = false, precision = 10, scale = 2)
    private BigDecimal originalAmount;

    @Column(name = "system_retention_percentage", nullable = false, precision = 5, scale = 2)
    @Builder.Default
    private BigDecimal systemRetentionPercentage = new BigDecimal("3.00");

    @Column(name = "transferred_amount", nullable = false, precision = 10, scale = 2)
    private BigDecimal transferredAmount;

    @Enumerated(EnumType.STRING)
    @Column(name = "recipient", nullable = false)
    private FundsRecipient recipient;

    @Column(name = "concept", nullable = false, length = 500)
    private String concept;

    @Enumerated(EnumType.STRING)
    @Column(name = "funds_status", nullable = false)
    private FundsStatus fundsStatus;

    @Column(name = "processed_at")
    private Instant processedAt;

    @Column(name = "blocking_claim_id")
    private UUID blockingClaimId;
}