package com.knowlink.api.materials.repositories;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.knowlink.api.materials.data.models.AcademicMaterial;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface IAcademicMaterialRepository extends JpaRepository<AcademicMaterial, UUID> {
    @Query("SELECT m FROM AcademicMaterial m WHERE m.tutorSubject.tutorProfile.tutorProfileId = :id AND m.available = true")
    List<AcademicMaterial> findAvailableByTutorProfileId(@Param("id") UUID id);

    @Query("SELECT m FROM AcademicMaterial m WHERE m.tutorSubject.subject.subjectId = :subjectId AND m.active = true")
    List<AcademicMaterial> findActiveBySubjectId(@Param("subjectId") UUID subjectId);

    Optional<AcademicMaterial> findByAcademicMaterialIdAndActiveTrue(UUID id);

       Optional<AcademicMaterial> findByAcademicMaterialIdAndActiveTrueAndAvailableTrue(UUID id);

    @Query("""
            SELECT material FROM AcademicMaterial material
            JOIN FETCH material.tutorSubject tutorSubject
            JOIN FETCH tutorSubject.subject subject
            JOIN FETCH subject.career
            JOIN FETCH tutorSubject.tutorProfile tutorProfile
            JOIN FETCH tutorProfile.user
            WHERE material.academicMaterialId = :materialId
            AND material.active = true
            AND tutorProfile.user.userId = :tutorUserId
            """)
    Optional<AcademicMaterial> findActiveByIdAndTutorUserId(
            @Param("materialId") UUID materialId,
            @Param("tutorUserId") UUID tutorUserId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
           UPDATE AcademicMaterial material
           SET material.reportsCount = material.reportsCount + 1
           WHERE material.academicMaterialId = :materialId
           AND material.active = true
           """)
    int incrementReportsCount(@Param("materialId") UUID materialId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
           UPDATE AcademicMaterial material
           SET material.available = false
           WHERE material.academicMaterialId = :materialId
           AND material.active = true
           """)
    int markUnavailable(@Param("materialId") UUID materialId);

    @Query("SELECT m FROM AcademicMaterial m " +
           "WHERE m.tutorSubject.tutorProfile.user.userId = :tutorUserId " +
           "AND m.tutorSubject.subject.subjectId IN :subjectIds " +
           "AND m.active = true AND m.available = true")
    List<AcademicMaterial> findAccessibleByTutorAndSubjectIds(
            @Param("tutorUserId") UUID tutorUserId,
            @Param("subjectIds") List<UUID> subjectIds);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT m FROM AcademicMaterial m WHERE m.academicMaterialId = :materialId")
    Optional<AcademicMaterial> findByIdForUpdate(@Param("materialId") UUID materialId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
           UPDATE AcademicMaterial material
           SET material.active = false, material.available = false
           WHERE material.academicMaterialId = :materialId
           """)
    int removeFromCatalog(@Param("materialId") UUID materialId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
           UPDATE AcademicMaterial material
           SET material.available = true
           WHERE material.academicMaterialId = :materialId
           """)
    int markAvailable(@Param("materialId") UUID materialId);}
