package com.knowlink.api.tutors.repositories;

import com.knowlink.api.tutors.data.models.BloqueDisponibilidad;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface BloqueDisponibilidadRepository extends JpaRepository<BloqueDisponibilidad, UUID> {
    List<BloqueDisponibilidad> findByPerfilTutorIdAndDisponibleTrue(UUID perfilTutorId);
}
