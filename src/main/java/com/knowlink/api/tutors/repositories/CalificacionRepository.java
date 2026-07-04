package com.knowlink.api.tutors.repositories;

import com.knowlink.api.tutors.data.models.Calificacion;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface CalificacionRepository extends JpaRepository<Calificacion, UUID> {
    List<Calificacion> findByPerfilTutorIdAndVisibleTrue(UUID perfilTutorId);
}
