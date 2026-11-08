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

    /**
     * Shared subjects of an institution, with the career eagerly loaded so that
     * Subject#isBasic() works (the mapping runs outside the transaction).
     */
    @Query("""
            SELECT s FROM Subject s
            JOIN FETCH s.career
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

    /**
     * Subjects of a single career with the career eagerly loaded: Subject#isBasic()
     * reads career.type and the response mapping happens outside the transaction
     * (spring.jpa.open-in-view is disabled).
     */
    @Query("""
            SELECT s FROM Subject s
            JOIN FETCH s.career
            WHERE s.career.careerId = :careerId
            ORDER BY s.name
            """)
    List<Subject> findByCareer_CareerIdOrderByName(@Param("careerId") UUID careerId);

    List<Subject> findByInstitution_InstitutionIdOrderByName(UUID institutionId);
}