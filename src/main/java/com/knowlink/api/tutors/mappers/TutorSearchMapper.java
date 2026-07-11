package com.knowlink.api.tutors.mappers;

import com.knowlink.api.tutors.data.dto.responses.TutorSearchResponse;
import com.knowlink.api.tutors.data.models.MateriaTutor;
import com.knowlink.api.tutors.data.models.PerfilTutor;

import java.util.List;

public final class TutorSearchMapper {

    private TutorSearchMapper() {
    }

    public static TutorSearchResponse from(List<MateriaTutor> materiasTutor) {

        if (materiasTutor == null || materiasTutor.isEmpty()) {
            throw new IllegalArgumentException("La lista de materias del tutor no puede estar vacía.");
        }

        PerfilTutor perfilTutor = materiasTutor.get(0).getPerfilTutor();

        return new TutorSearchResponse(
                perfilTutor.getUser().getUserId(),
                perfilTutor.getUser().getFullName(),
                perfilTutor.getFotoPerfil(),
                perfilTutor.getCalificacionPromedio(),
                4, // o getTotalReviews() según tu entidad
                materiasTutor.stream()
                        .map(mt -> mt.getMateria().getNombre())
                        .distinct()
                        .sorted()
                        .toList()
        );
    }
}