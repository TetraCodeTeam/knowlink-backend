package com.knowlink.api.timeslot.data.models;

import com.knowlink.api.timeslot.data.enums.SlotStatus;
import com.knowlink.api.tutors.availability.data.models.AvailabilityBlock;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;

@Entity
@Table(name = "time_slot")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TimeSlot {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "time_slot_id", updatable = false, nullable = false)
    private UUID timeSlotId;

    @Column(name = "date", nullable = false)
    private LocalDate date;

    @Column(name = "start_time", nullable = false)
    private LocalTime startTime;

    @Column(name = "end_time", nullable = false)
    private LocalTime endTime;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, columnDefinition = "VARCHAR(20)")
    private SlotStatus status;

    @Column(name = "tutor_profile_id", nullable = false, updatable = false)
    private UUID tutorProfileId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "availability_block_id")
    private AvailabilityBlock assignedBlock;
}