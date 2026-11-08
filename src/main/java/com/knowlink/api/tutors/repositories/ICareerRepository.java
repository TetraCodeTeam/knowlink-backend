package com.knowlink.api.tutors.repositories;

import com.knowlink.api.tutors.data.enums.CareerType;
import com.knowlink.api.tutors.data.models.Career;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ICareerRepository extends JpaRepository<Career, UUID> {

    Optional<Career> findByInstitution_InstitutionIdAndName(UUID institutionId, String name);

    List<Career> findByInstitution_InstitutionIdAndTypeOrderByName(UUID institutionId, CareerType type);

    Optional<Career> findByInstitution_InstitutionIdAndType(UUID institutionId, CareerType type);
}
