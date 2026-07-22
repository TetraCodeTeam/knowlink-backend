package com.knowlink.api.tutors.availability.repositories;

import com.knowlink.api.tutors.availability.data.models.AvailabilityWeekCustomization;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.UUID;

public interface IAvailabilityWeekCustomizationRepository extends JpaRepository<AvailabilityWeekCustomization, UUID> {
    boolean existsByTutorProfile_TutorProfileIdAndWeekStart(UUID tutorProfileId, LocalDate weekStart);
    void deleteByTutorProfile_TutorProfileIdAndWeekStart(UUID tutorProfileId, LocalDate weekStart);
}