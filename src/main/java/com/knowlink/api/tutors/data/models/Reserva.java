package com.knowlink.api.tutors.data.models;

import com.knowlink.api.users.data.models.User;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "reservas")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Reserva {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne
    @JoinColumn(name = "perfil_tutor_id", nullable = false)
    private PerfilTutor tutor;

    @ManyToOne
    @JoinColumn(name = "alumno_id", nullable = false)
    private User alumno;

    private String estadoReserva;
    private LocalDateTime fechaSesion;
}
