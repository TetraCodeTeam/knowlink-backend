package com.knowlink.api.tutors.repositories;

import com.knowlink.api.tutors.data.models.MaterialAcademico;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface MaterialAcademicoRepository extends JpaRepository<MaterialAcademico, UUID> {
    List<MaterialAcademico> findByPerfilTutorIdAndDisponibleTrue(UUID perfilTutorId);
}
