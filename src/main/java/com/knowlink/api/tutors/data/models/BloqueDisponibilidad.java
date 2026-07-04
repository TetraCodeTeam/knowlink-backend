package com.knowlink.api.tutors.data.models;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalTime;
import java.util.UUID;

@Entity
@Table(name = "bloque_disponibilidad")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BloqueDisponibilidad {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    private String dia;
    private LocalTime horaInicio;
    private LocalTime horaFin;
    private boolean disponible;

    @ManyToOne
    @JoinColumn(name = "perfil_tutor_id")
    private PerfilTutor perfilTutor;
}
