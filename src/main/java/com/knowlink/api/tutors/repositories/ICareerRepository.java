package com.knowlink.api.tutors.repositories;

import org.springframework.data.jpa.repository.JpaRepository;

import com.knowlink.api.catalog.data.enums.CareerType;
import com.knowlink.api.tutors.data.models.Career;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ICareerRepository extends JpaRepository<Career, UUID> {

    Optional<Career> findByName(String name);

    boolean existsByInstitutionInstitutionIdAndName(UUID institutionId, String name);

    List<Career> findByInstitutionInstitutionId(UUID institutionId);

    List<Career> findByInstitutionInstitutionIdAndType(UUID institutionId, CareerType type);
}