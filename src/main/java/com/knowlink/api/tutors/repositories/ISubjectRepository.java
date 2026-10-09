package com.knowlink.api.tutors.repositories;

import com.knowlink.api.catalog.data.enums.CareerType;
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

    boolean existsByInstitutionInstitutionIdAndName(UUID institutionId, String name);

    Optional<Subject> findByNameAndInstitutionInstitutionId(String name, UUID institutionId);

    List<Subject> findByInstitutionInstitutionId(UUID institutionId);

    @Query("SELECT s FROM Subject s WHERE s.isBasic = true")
    List<Subject> findBasicSubjects();

    @Query("""
            SELECT DISTINCT s FROM Subject s
            JOIN s.careers c
            WHERE c.careerId = :careerId
            AND s.isBasic = false
            """)
    List<Subject> findNonBasicByCareerId(@Param("careerId") UUID careerId);

    @Query("""
            SELECT DISTINCT s FROM Subject s
            JOIN s.careers c
            WHERE s.institution.institutionId = :institutionId
            AND (c.careerId = :careerId OR c.type = :sharedType)
            """)
    List<Subject> findVisibleByInstitutionAndCareer(@Param("institutionId") UUID institutionId,
            @Param("careerId") UUID careerId,
            @Param("sharedType") CareerType sharedType);
}