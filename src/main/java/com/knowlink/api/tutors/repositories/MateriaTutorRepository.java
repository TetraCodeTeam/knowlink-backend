package com.knowlink.api.tutors.repositories;

import com.knowlink.api.tutors.data.models.MateriaTutor;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface MateriaTutorRepository extends JpaRepository<MateriaTutor, UUID> {
    List<MateriaTutor> findByPerfilTutorId(UUID perfilTutorId);
    List<MateriaTutor> findByMateria_NombreContainingIgnoreCase(String nombre);
}
