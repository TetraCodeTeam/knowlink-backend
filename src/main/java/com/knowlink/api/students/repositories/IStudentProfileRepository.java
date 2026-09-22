package com.knowlink.api.students.repositories;

import com.knowlink.api.students.data.models.StudentProfile;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface IStudentProfileRepository extends JpaRepository<StudentProfile, UUID> {

    boolean existsByUser_UserId(UUID userId);

    @Query("SELECT sp FROM StudentProfile sp WHERE sp.user.userId = :userId")
    Optional<StudentProfile> findByUserId(@Param("userId") UUID userId);

    @Query("SELECT sp FROM StudentProfile sp WHERE sp.user.userId IN :userIds")
    List<StudentProfile> findByUserIdIn(@Param("userIds") Collection<UUID> userIds);
}