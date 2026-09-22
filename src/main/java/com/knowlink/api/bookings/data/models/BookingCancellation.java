package com.knowlink.api.bookings.data.models;

import com.knowlink.api.bookings.data.enums.CancellationRole;
import com.knowlink.api.bookings.data.enums.RefundDestination;
import com.knowlink.api.bookings.data.enums.RefundPolicy;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "booking_cancellation")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BookingCancellation {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "cancellation_id", updatable = false, nullable = false)
    private UUID cancellationId;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "booking_id", nullable = false)
    private Booking booking;

    @Enumerated(EnumType.STRING)
    @Column(name = "cancelled_by", nullable = false)
    private CancellationRole cancelledBy;

    @Enumerated(EnumType.STRING)
    @Column(name = "refund_destination", nullable = false)
    private RefundDestination refundDestination;

    @Enumerated(EnumType.STRING)
    @Column(name = "refund_policy", nullable = false)
    private RefundPolicy refundPolicy;

    @Column(name = "amount", nullable = false, precision = 10, scale = 2)
    private BigDecimal amount;

    @Column(name = "hours_in_advance", nullable = false)
    private Long hoursInAdvance;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
    }
}
