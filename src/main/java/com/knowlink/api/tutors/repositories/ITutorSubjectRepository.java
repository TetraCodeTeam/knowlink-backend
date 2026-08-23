package com.knowlink.api.tutors.repositories;

import com.knowlink.api.tutors.data.models.TutorSubject;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;


public interface ITutorSubjectRepository extends JpaRepository<TutorSubject, UUID>,
        JpaSpecificationExecutor<TutorSubject> {

    @Query("SELECT ts FROM TutorSubject ts WHERE ts.tutorProfile.tutorProfileId = :tutorProfileId")
    List<TutorSubject> findByTutorProfileId(@Param("tutorProfileId") UUID tutorProfileId);

    List<TutorSubject> findBySubject_NameContainingIgnoreCase(String query);
}