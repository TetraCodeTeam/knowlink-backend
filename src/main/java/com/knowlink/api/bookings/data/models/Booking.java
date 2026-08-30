package com.knowlink.api.bookings.data.models;

import com.knowlink.api.bookings.data.enums.BookingStatus;
import com.knowlink.api.timeslot.data.models.TimeSlot;
import com.knowlink.api.tutors.data.enums.Modality;
import com.knowlink.api.tutors.data.models.TutorSubject;
import com.knowlink.api.users.data.models.User;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.UUID;

@Entity
@Table(name = "booking", uniqueConstraints = @UniqueConstraint(
        name = "uk_active_booking_slot_start",
        columnNames = {"time_slot_id", "start_time"}
))
@Getter 
@Setter 
@NoArgsConstructor 
@AllArgsConstructor 
@Builder
public class Booking {

     @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "booking_id", updatable = false, nullable = false)
    private UUID bookingId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "amount", nullable = false, precision = 10, scale = 2)
    private BigDecimal amount;

    @Column(name = "session_date", nullable = false)
    private LocalDate sessionDate;

    @Column(name = "start_time", nullable = false)
    private LocalTime startTime;

    @Column(name = "end_time", nullable = false)
    private LocalTime endTime;

    @Column(name = "topic", columnDefinition = "TEXT")
    private String topic;

    @Enumerated(EnumType.STRING)
    @Column(name = "modality", nullable = false)
    private Modality modality;

    @Column(name = "confirmation_token_expiration")
    private LocalDateTime confirmationTokenExpiration;

    @Column(name = "virtual_session_link")
    private String virtualSessionLink;

    @Column(name = "confirmation_token")
    private String confirmationToken;

    @Enumerated(EnumType.STRING)
    @Column(name = "booking_status", nullable = false)
    private BookingStatus bookingStatus;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "time_slot_id", nullable = false)
    private TimeSlot timeSlot;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "tutor_subject_id", nullable = false)
    private TutorSubject tutorSubject;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "student_id", nullable = false)
    private User student;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "tutor_id", nullable = false)
    private User tutor;

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
    }
}