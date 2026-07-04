package com.knowlink.api.tutors.repositories;

import com.knowlink.api.tutors.data.models.Reserva;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface ReservaRepository extends JpaRepository<Reserva, UUID> {
    boolean existsByTutorUserUserIdAndAlumnoUserIdAndEstadoReservaIn(java.util.UUID tutorUserId, java.util.UUID alumnoId, List<String> estados);
}
