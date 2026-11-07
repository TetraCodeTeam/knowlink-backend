package com.knowlink.api.tutors.repositories;

import com.knowlink.api.tutors.data.models.Institution;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface IInstitutionRepository extends JpaRepository<Institution, UUID> {

    Optional<Institution> findByName(String name);

    List<Institution> findAllByOrderByNameAsc();
}
