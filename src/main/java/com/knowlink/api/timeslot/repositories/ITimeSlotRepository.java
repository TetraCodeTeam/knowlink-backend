package com.knowlink.api.timeslot.repositories;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.knowlink.api.timeslot.data.models.TimeSlot;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public interface ITimeSlotRepository extends JpaRepository<TimeSlot, UUID> {
    @Query("SELECT t FROM TimeSlot t WHERE t.tutorProfileId = :tutorProfileId")
    List<TimeSlot> findByTutorProfileId(@Param("tutorProfileId") UUID tutorProfileId);

    @Query("""
            SELECT ts FROM TimeSlot ts
            WHERE ts.tutorProfileId = :tutorProfileId
            AND ts.date BETWEEN :from AND :to
            AND ts.status = 'AVAILABLE'
            ORDER BY ts.date, ts.startTime
            """)
    List<TimeSlot> findAvailableInRange(
            @Param("tutorProfileId") UUID tutorProfileId,
            @Param("from") LocalDate from,
            @Param("to") LocalDate to
    );

    @Modifying
    @Query("""
            DELETE FROM TimeSlot ts
            WHERE ts.tutorProfileId = :tutorProfileId
            AND ts.date BETWEEN :from AND :to
            AND ts.status = 'AVAILABLE'
            """)
    void deleteAvailableInRange(
            @Param("tutorProfileId") UUID tutorProfileId,
            @Param("from") LocalDate from,
            @Param("to") LocalDate to
    );

    @Modifying
    @Query("""
            DELETE FROM TimeSlot ts
            WHERE ts.assignedBlock.availabilityBlockId = :blockId
            AND ts.date BETWEEN :from AND :to
            AND ts.status = 'AVAILABLE'
            """)
    void deleteAvailableInRangeForBlock(
            @Param("blockId") UUID blockId,
            @Param("from") LocalDate from,
            @Param("to") LocalDate to
    );
}
