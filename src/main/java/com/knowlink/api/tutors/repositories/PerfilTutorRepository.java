package com.knowlink.api.tutors.repositories;

import com.knowlink.api.tutors.data.models.PerfilTutor;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface PerfilTutorRepository extends JpaRepository<PerfilTutor, UUID> {
    Optional<PerfilTutor> findByUserUserId(UUID userId);
}
