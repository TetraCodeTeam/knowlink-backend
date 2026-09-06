package com.knowlink.api.tutors.repositories;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.knowlink.api.tutors.data.models.AcademicMaterial;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface IAcademicMaterialRepository extends JpaRepository<AcademicMaterial, UUID> {
    @Query("SELECT m FROM AcademicMaterial m WHERE m.tutorSubject.tutorProfile.tutorProfileId = :id AND m.available = true")
    List<AcademicMaterial> findAvailableByTutorProfileId(@Param("id") UUID id);

    @Query("SELECT m FROM AcademicMaterial m WHERE m.tutorSubject.subject.subjectId = :subjectId AND m.active = true")
    List<AcademicMaterial> findActiveBySubjectId(@Param("subjectId") UUID subjectId);

    Optional<AcademicMaterial> findByAcademicMaterialIdAndActiveTrue(UUID id);

    @Query("SELECT m FROM AcademicMaterial m " +
           "WHERE m.tutorSubject.tutorProfile.user.userId = :tutorUserId " +
           "AND m.tutorSubject.subject.subjectId IN :subjectIds " +
           "AND m.active = true AND m.available = true")
    List<AcademicMaterial> findAccessibleByTutorAndSubjectIds(
            @Param("tutorUserId") UUID tutorUserId,
            @Param("subjectIds") List<UUID> subjectIds);
}