package com.knowlink.api.tutors.repositories;

import com.knowlink.api.tutors.data.models.TimeSlot;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface ITimeSlotRepository extends JpaRepository<TimeSlot, UUID> {
    @Query("SELECT t FROM TimeSlot t WHERE t.availabilityBlock.tutorProfile.tutorProfileId = :tutorProfileId")
    List<TimeSlot> findByTutorProfileId(@Param("tutorProfileId") UUID tutorProfileId);
}
