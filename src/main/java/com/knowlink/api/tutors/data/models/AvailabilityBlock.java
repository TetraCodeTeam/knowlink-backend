package com.knowlink.api.tutors.data.models;

import jakarta.persistence.*;
import lombok.*;

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "availability_block")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AvailabilityBlock {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "availability_block_id", updatable = false, nullable = false)
    private UUID availabilityBlockId;

    @Enumerated(EnumType.STRING)
    @Column(name = "day_of_week", nullable = false)
    private DayOfWeek dayOfWeek;

    @Column(name = "start_time", nullable = false)
    private LocalTime startTime;

    @Column(name = "end_time", nullable = false)
    private LocalTime endTime;

    @Column(name = "available", nullable = false)
    private boolean available;

    @Column(name = "min_slot_duration_minutes", nullable = false)
    private Integer minSlotDurationMinutes;

    @Column(name = "repeat_weekly", nullable = false)
    @Builder.Default
    private boolean repeatWeekly = false;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "tutor_profile_id", nullable = false)
    private TutorProfile tutorProfile;

    @OneToMany(mappedBy = "availabilityBlock", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<TimeSlot> generatedSlots = new ArrayList<>();
}