package com.knowlink.api.bookings.repositories;

import com.knowlink.api.bookings.data.models.Hold;

import jakarta.persistence.LockModeType;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface IHoldRepository extends JpaRepository<Hold, UUID> {

        boolean existsByStudent_UserId(UUID studentUserId);

        @Query("""
                        SELECT h FROM Hold h
                        WHERE h.timeSlot.timeSlotId = :timeSlotId
                        AND h.startTime < :endTime
                        AND h.endTime > :startTime
                        """)
        List<Hold> findOverlapping(
                        @Param("timeSlotId") UUID timeSlotId,
                        @Param("startTime") LocalTime startTime,
                        @Param("endTime") LocalTime endTime);

        @Query("""
                        SELECT h FROM Hold h
                        WHERE h.student.userId = :studentUserId
                        AND h.timeSlot.timeSlotId = :timeSlotId
                        AND h.startTime = :startTime
                        AND h.endTime = :endTime
                        """)
        Optional<Hold> findActiveHoldForWindow(
                        @Param("studentUserId") UUID studentUserId,
                        @Param("timeSlotId") UUID timeSlotId,
                        @Param("startTime") LocalTime startTime,
                        @Param("endTime") LocalTime endTime);

        @Query("SELECT h FROM Hold h WHERE h.expiresAt < :now")
        List<Hold> findExpired(@Param("now") LocalDateTime now);

        @Lock(LockModeType.PESSIMISTIC_WRITE)
        @Query("""
                        SELECT h FROM Hold h
                        WHERE h.student.userId = :studentUserId
                        AND h.timeSlot.timeSlotId = :timeSlotId
                        AND h.startTime = :startTime
                        AND h.endTime = :endTime
                        """)
        Optional<Hold> findActiveHoldForWindowForUpdate(
                        @Param("studentUserId") UUID studentUserId,
                        @Param("timeSlotId") UUID timeSlotId,
                        @Param("startTime") LocalTime startTime,
                        @Param("endTime") LocalTime endTime);

        @Lock(LockModeType.PESSIMISTIC_WRITE)
        @Query("SELECT h FROM Hold h WHERE h.holdId = :holdId")
        Optional<Hold> findByIdForUpdate(@Param("holdId") UUID holdId);

        @Query("""
                        SELECT h FROM Hold h
                        WHERE h.timeSlot.tutorProfileId = :tutorProfileId
                        AND h.timeSlot.date BETWEEN :from AND :to
                        """)
        List<Hold> findActiveInRange(
                        @Param("tutorProfileId") UUID tutorProfileId,
                        @Param("from") LocalDate from,
                        @Param("to") LocalDate to);

        @Query("""
                        SELECT CASE WHEN COUNT(h) > 0 THEN true ELSE false END
                        FROM Hold h
                        WHERE h.student.userId = :studentUserId
                        AND h.expiresAt > :now
                        """)
        boolean existsActiveHoldForStudent(
                        @Param("studentUserId") UUID studentUserId,
                        @Param("now") LocalDateTime now);
}