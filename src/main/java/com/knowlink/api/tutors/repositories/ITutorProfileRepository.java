package com.knowlink.api.tutors.repositories;

import com.knowlink.api.tutors.data.models.TutorProfile;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ITutorProfileRepository extends JpaRepository<TutorProfile, UUID> {

    @Query("SELECT tp FROM TutorProfile tp WHERE tp.user.userId = :userId")
    Optional<TutorProfile> findByUserId(@Param("userId") UUID userId);
 
    List<TutorProfile> findByUser_FullNameContainingIgnoreCase(String fullName);
}