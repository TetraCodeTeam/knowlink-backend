package com.knowlink.api.tutors.services.implementations;

import com.knowlink.api.exceptions.custom_exceptions.ResourceNotFoundException;
import com.knowlink.api.tutors.data.dto.responses.*;
import com.knowlink.api.tutors.data.models.*;
import com.knowlink.api.tutors.repositories.*;
import com.knowlink.api.tutors.services.interfaces.TutorProfileService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class TutorProfileServiceImpl implements TutorProfileService {

    private final PerfilTutorRepository perfilTutorRepository;
    private final MateriaTutorRepository materiaTutorRepository;
    private final CalificacionRepository calificacionRepository;
    private final BloqueDisponibilidadRepository bloqueDisponibilidadRepository;
    private final MaterialAcademicoRepository materialAcademicoRepository;
    private final ReservaRepository reservaRepository;

    @Override
    public TutorProfileResponse getTutorProfile(UUID tutorUserId, UUID alumnoUserId) {
        PerfilTutor perfil = perfilTutorRepository.findByUserUserId(tutorUserId)
                .orElseThrow(() -> new ResourceNotFoundException("Tutor", "id", tutorUserId));

        List<TutorSubjectResponse> materias = materiaTutorRepository.findByPerfilTutorId(perfil.getId())
                .stream()
                .map(mt -> new TutorSubjectResponse(mt.getMateria().getNombre(), mt.getDescripcion(), mt.getModalidad(), mt.getTipoCompensacion(), mt.getPrecio()))
                .collect(Collectors.toList());

        List<TutorReviewResponse> reviews = calificacionRepository.findByPerfilTutorIdAndVisibleTrue(perfil.getId())
                .stream()
                .map(c -> new TutorReviewResponse(c.getPuntuacion(), c.getComentario(), c.getFechaCalificacion()))
                .collect(Collectors.toList());

        List<TutorAvailabilityResponse> disponibilidad = bloqueDisponibilidadRepository.findByPerfilTutorIdAndDisponibleTrue(perfil.getId())
                .stream()
                .map(b -> new TutorAvailabilityResponse(b.getDia(), b.getHoraInicio(), b.getHoraFin()))
                .collect(Collectors.toList());

        boolean tieneReserva = reservaRepository.existsByTutorUserUserIdAndAlumnoUserIdAndEstadoReservaIn(
                tutorUserId, alumnoUserId, List.of("RESERVADA", "EN_CURSO", "REALIZADA")
        );

        List<TutorMaterialResponse> materiales = List.of();
        if (tieneReserva) {
            materiales = materialAcademicoRepository.findByPerfilTutorIdAndDisponibleTrue(perfil.getId())
                    .stream()
                    .map(m -> new TutorMaterialResponse(m.getNombre(), m.getUrlArchivo(), m.getFechaSubida()))
                    .collect(Collectors.toList());
        }


        return new TutorProfileResponse(
                perfil.getUser().getUserId(),
                perfil.getUser().getFullName(),
                perfil.getBiografia(),
                perfil.getCarrera(),
                perfil.getFotoPerfil(),
                perfil.isVerificado(),
                perfil.getCalificacionPromedio(),
                materias,
                reviews,
                disponibilidad,
                materiales
        );
    }
}
