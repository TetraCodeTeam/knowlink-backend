package com.knowlink.api.tutors.statistics.repositories;

import com.knowlink.api.tutors.statistics.data.models.OfferedSlotHistory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.UUID;

public interface IOfferedSlotHistoryRepository extends JpaRepository<OfferedSlotHistory, UUID> {

    @Modifying
    @Query("DELETE FROM OfferedSlotHistory h WHERE h.date < :cutoff")
    void deleteOlderThan(@Param("cutoff") LocalDate cutoff);
}
