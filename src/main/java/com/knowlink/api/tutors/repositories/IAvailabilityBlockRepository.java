package com.knowlink.api.tutors.repositories;

import com.knowlink.api.tutors.data.models.AvailabilityBlock;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface IAvailabilityBlockRepository extends JpaRepository<AvailabilityBlock, UUID> {

    @Query("""
            SELECT ab FROM AvailabilityBlock ab
            WHERE ab.tutorProfile.tutorProfileId = :tutorProfileId
            AND ab.available = true
            """)
    List<AvailabilityBlock> findAvailableByTutorProfileId(@Param("tutorProfileId") UUID tutorProfileId);
}