package com.knowlink.api.tutors.availability.data.models;

import com.knowlink.api.tutors.data.models.TutorProfile;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "availability_week_customization", uniqueConstraints = @UniqueConstraint(columnNames = {
        "tutor_profile_id", "week_start" }))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AvailabilityWeekCustomization {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "availability_week_customization_id", updatable = false, nullable = false)
    private UUID availabilityWeekCustomizationId;

    @Column(name = "week_start", nullable = false)
    private LocalDate weekStart;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "tutor_profile_id", nullable = false)
    private TutorProfile tutorProfile;
}