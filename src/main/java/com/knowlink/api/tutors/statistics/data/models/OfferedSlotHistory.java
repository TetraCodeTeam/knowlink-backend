package com.knowlink.api.tutors.statistics.data.models;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;

/**
 * Horario ofrecido que ya pasó sin llegar a reservarse. PastAvailabilityCleanupJob borra esos
 * horarios de time_slot a diario; se archivan acá para poder calcular la ocupación de períodos pasados.
 */
@Entity
@Table(name = "offered_slot_history", indexes = @Index(
        name = "idx_offered_slot_history_tutor_date", columnList = "tutor_profile_id, date"))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OfferedSlotHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "offered_slot_history_id", updatable = false, nullable = false)
    private UUID offeredSlotHistoryId;

    @Column(name = "tutor_profile_id", nullable = false, updatable = false)
    private UUID tutorProfileId;

    @Column(name = "date", nullable = false, updatable = false)
    private LocalDate date;

    @Column(name = "start_time", nullable = false, updatable = false)
    private LocalTime startTime;

    @Column(name = "end_time", nullable = false, updatable = false)
    private LocalTime endTime;
}
