package com.knowlink.api.tutors.controllers;

import com.knowlink.api.tutors.data.dto.responses.TutorProfileResponse;
import com.knowlink.api.tutors.services.interfaces.TutorProfileService;
import com.knowlink.api.users.data.models.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.security.core.Authentication;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

public class TutorControllerTest {

    @Mock
    private TutorProfileService service;

    @InjectMocks
    private TutorController controller;

    @BeforeEach
    void init() { MockitoAnnotations.openMocks(this); }

    @Test
    void accesoAutorizado() {
        UUID tutorId = UUID.randomUUID();
        UUID alumnoId = UUID.randomUUID();
        TutorProfileResponse resp = new TutorProfileResponse(tutorId, "Nombre Apellido", "bio", "carrera", "foto", true, 4.5, java.util.List.of(), java.util.List.of(), java.util.List.of(), java.util.List.of());
        when(service.getTutorProfile(eq(tutorId), eq(alumnoId))).thenReturn(resp);

        Authentication auth = mock(Authentication.class);
        User user = User.builder().userId(alumnoId).firstName("Nombre").lastName("Apellido").build();
        when(auth.getPrincipal()).thenReturn(user);

        var responseEntity = controller.getTutorProfile(tutorId, auth);
        assertEquals(200, responseEntity.getStatusCodeValue());
        verify(service).getTutorProfile(tutorId, alumnoId);
    }
}
