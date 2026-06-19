package com.knowlink.api.tutors.services;

import com.knowlink.api.tutors.data.dto.responses.TutorProfileResponse;
import com.knowlink.api.tutors.data.models.PerfilTutor;
import com.knowlink.api.tutors.repositories.*;
import com.knowlink.api.tutors.services.implementations.TutorProfileServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

public class TutorProfileServiceTest {

    @Mock
    private PerfilTutorRepository perfilRepo;
    @Mock
    private MateriaTutorRepository materiaRepo;
    @Mock
    private CalificacionRepository calificacionRepo;
    @Mock
    private BloqueDisponibilidadRepository bloqueRepo;
    @Mock
    private MaterialAcademicoRepository materialRepo;
    @Mock
    private ReservaRepository reservaRepo;

    @InjectMocks
    private TutorProfileServiceImpl service;

    @BeforeEach
    void init() { MockitoAnnotations.openMocks(this); }

    @Test
    void tutorExiste() {
        UUID tutorUserId = UUID.randomUUID();
        PerfilTutor perfil = PerfilTutor.builder().id(UUID.randomUUID()).biografia("bio").build();
        when(perfilRepo.findByUserUserId(tutorUserId)).thenReturn(Optional.of(perfil));

        when(materiaRepo.findByPerfilTutorId(any())).thenReturn(java.util.List.of());
        when(calificacionRepo.findByPerfilTutorIdAndVisibleTrue(any())).thenReturn(java.util.List.of());
        when(bloqueRepo.findByPerfilTutorIdAndDisponibleTrue(any())).thenReturn(java.util.List.of());
        when(reservaRepo.existsByTutorUserUserIdAndAlumnoUserIdAndEstadoReservaIn(any(), any(), any())).thenReturn(false);

        TutorProfileResponse resp = service.getTutorProfile(tutorUserId, UUID.randomUUID());
        assertNotNull(resp);
    }

    @Test
    void tutorNoExiste() {
        UUID tutorUserId = UUID.randomUUID();
        when(perfilRepo.findByUserUserId(tutorUserId)).thenReturn(Optional.empty());
        assertThrows(RuntimeException.class, () -> service.getTutorProfile(tutorUserId, UUID.randomUUID()));
    }
}
