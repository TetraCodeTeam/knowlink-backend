package com.knowlink.api.bookings.data.models;

import com.knowlink.api.timeslot.data.models.TimeSlot;
import com.knowlink.api.users.data.models.User;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.UUID;

@Entity
@Table(name = "booking_hold")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Hold {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "hold_id", updatable = false, nullable = false)
    private UUID holdId;

    @Column(name = "start_time", nullable = false)
    private LocalTime startTime;

    @Column(name = "end_time", nullable = false)
    private LocalTime endTime;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "time_slot_id", nullable = false)
    private TimeSlot timeSlot;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "student_id", nullable = false)
    private User student;

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
    }
}