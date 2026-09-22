package com.knowlink.api.timeslot.repositories;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;

import com.knowlink.api.timeslot.data.models.TimeSlot;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ITimeSlotRepository extends JpaRepository<TimeSlot, UUID> {
        @Query("SELECT t FROM TimeSlot t WHERE t.tutorProfileId = :tutorProfileId")
        List<TimeSlot> findByTutorProfileId(@Param("tutorProfileId") UUID tutorProfileId);

        @Query("""
                        SELECT ts FROM TimeSlot ts
                        WHERE ts.tutorProfileId = :tutorProfileId
                        AND ts.date BETWEEN :from AND :to
                        AND ts.status = com.knowlink.api.timeslot.data.enums.SlotStatus.AVAILABLE
                        ORDER BY ts.date, ts.startTime
                        """)
        List<TimeSlot> findAvailableInRange(
                        @Param("tutorProfileId") UUID tutorProfileId,
                        @Param("from") LocalDate from,
                        @Param("to") LocalDate to);

        @Modifying
        @Query("""
                        DELETE FROM TimeSlot ts
                        WHERE ts.tutorProfileId = :tutorProfileId
                        AND ts.date BETWEEN :from AND :to
                        AND ts.status = com.knowlink.api.timeslot.data.enums.SlotStatus.AVAILABLE
                        AND NOT EXISTS (SELECT 1 FROM Booking b WHERE b.timeSlot = ts)
                        """)
        void deleteAvailableInRange(
                        @Param("tutorProfileId") UUID tutorProfileId,
                        @Param("from") LocalDate from,
                        @Param("to") LocalDate to);

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
                        @Param("to") LocalDate to);

        @Lock(LockModeType.PESSIMISTIC_WRITE)
        @Query("""
                        SELECT ts FROM TimeSlot ts
                        WHERE ts.timeSlotId = :timeSlotId
                        AND ts.tutorProfileId = :tutorProfileId
                        """)
        Optional<TimeSlot> findByIdAndTutorProfileIdForUpdate(
                        @Param("timeSlotId") UUID timeSlotId,
                        @Param("tutorProfileId") UUID tutorProfileId);

        @Query("""
                        SELECT ts FROM TimeSlot ts
                        WHERE ts.tutorProfileId = :tutorProfileId
                        AND ts.date BETWEEN :from AND :to
                        AND (ts.date > :today OR (ts.date = :today AND ts.endTime > :nowTime))
                        ORDER BY ts.date, ts.startTime
                        """)
        List<TimeSlot> findInRange(
                        @Param("tutorProfileId") UUID tutorProfileId,
                        @Param("from") LocalDate from,
                        @Param("to") LocalDate to,
                        @Param("today") LocalDate today,
                        @Param("nowTime") LocalTime nowTime);

        @Modifying
        @Query("""
                        DELETE FROM TimeSlot ts
                        WHERE (ts.date < :today OR (ts.date = :today AND ts.endTime <= :nowTime))
                        AND NOT EXISTS (SELECT 1 FROM Booking b WHERE b.timeSlot = ts)
                        """)
        void deletePastUnbooked(@Param("today") LocalDate today, @Param("nowTime") LocalTime nowTime);
}
