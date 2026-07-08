package com.knowlink.api.tutors.repositories;

import com.knowlink.api.tutors.data.models.Subject;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ISubjectRepository extends JpaRepository<Subject, UUID> {

    Optional<Subject> findByName(String name);

    @Query("SELECT s FROM Subject s WHERE s.isBasic = true")
    List<Subject> findBasicSubjects();

    @Query("""
            SELECT s FROM Subject s
            WHERE s.career.careerId = :careerId
            AND s.isBasic = false
            """)
    List<Subject> findNonBasicByCareerId(@Param("careerId") UUID careerId);
}