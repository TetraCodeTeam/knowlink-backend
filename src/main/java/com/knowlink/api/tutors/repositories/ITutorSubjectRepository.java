package com.knowlink.api.tutors.repositories;

import com.knowlink.api.tutors.data.models.TutorSubject;

import jakarta.persistence.LockModeType;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;


public interface ITutorSubjectRepository extends JpaRepository<TutorSubject, UUID>,
        JpaSpecificationExecutor<TutorSubject> {

    @Query("SELECT ts FROM TutorSubject ts WHERE ts.tutorProfile.tutorProfileId = :tutorProfileId")
    List<TutorSubject> findByTutorProfileId(@Param("tutorProfileId") UUID tutorProfileId);

    List<TutorSubject> findBySubject_NameContainingIgnoreCase(String query);

    Optional<TutorSubject> findByTutorSubjectIdAndTutorProfile_TutorProfileId(UUID tutorSubjectId, UUID tutorProfileId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            SELECT ts FROM TutorSubject ts
            WHERE ts.tutorSubjectId = :tutorSubjectId
            AND ts.tutorProfile.tutorProfileId = :tutorProfileId
            """)
    Optional<TutorSubject> findOwnedByIdForUpdate(
            @Param("tutorSubjectId") UUID tutorSubjectId,
            @Param("tutorProfileId") UUID tutorProfileId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT ts FROM TutorSubject ts WHERE ts.tutorSubjectId = :tutorSubjectId")
    Optional<TutorSubject> findByIdForUpdate(@Param("tutorSubjectId") UUID tutorSubjectId);

    @Query("""
            SELECT ts FROM TutorSubject ts
            WHERE ts.tutorProfile.tutorProfileId = :tutorProfileId
            AND ts.active = true
            """)
    List<TutorSubject> findActiveByTutorProfileId(@Param("tutorProfileId") UUID tutorProfileId);
}
