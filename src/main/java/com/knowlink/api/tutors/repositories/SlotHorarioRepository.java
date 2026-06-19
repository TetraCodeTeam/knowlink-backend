package com.knowlink.api.tutors.repositories;

import com.knowlink.api.tutors.data.models.SlotHorario;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface SlotHorarioRepository extends JpaRepository<SlotHorario, UUID> {
    List<SlotHorario> findByPerfilTutorId(UUID perfilTutorId);
}
