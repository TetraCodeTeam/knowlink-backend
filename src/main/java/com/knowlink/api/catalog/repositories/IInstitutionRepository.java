package com.knowlink.api.catalog.repositories;

import com.knowlink.api.catalog.data.models.Institution;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface IInstitutionRepository extends JpaRepository<Institution, UUID> {

    Optional<Institution> findByName(String name);

    boolean existsByName(String name);
}