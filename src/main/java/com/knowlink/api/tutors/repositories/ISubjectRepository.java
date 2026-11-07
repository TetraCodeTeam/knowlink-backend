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

    Optional<Subject> findByInstitution_InstitutionIdAndName(UUID institutionId, String name);

    @Query("""
            SELECT s FROM Subject s
            WHERE s.institution.institutionId = :institutionId
              AND s.career.type = com.knowlink.api.tutors.data.enums.CareerType.SHARED
            ORDER BY s.name
            """)
    List<Subject> findSharedByInstitutionId(@Param("institutionId") UUID institutionId);

    /** Materias de una carrera + las compartidas de su misma institución (reemplaza findBasicAndByCareerId). */
    @Query("""
            SELECT s FROM Subject s
            WHERE s.career.careerId = :careerId
               OR (s.career.type = com.knowlink.api.tutors.data.enums.CareerType.SHARED
                   AND s.institution.institutionId =
                       (SELECT c.institution.institutionId FROM Career c WHERE c.careerId = :careerId))
            ORDER BY s.name
            """)
    List<Subject> findSharedAndByCareerId(@Param("careerId") UUID careerId);

    List<Subject> findByCareer_CareerIdOrderByName(UUID careerId);

    List<Subject> findByInstitution_InstitutionIdOrderByName(UUID institutionId);
}