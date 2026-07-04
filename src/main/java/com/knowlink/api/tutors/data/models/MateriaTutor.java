package com.knowlink.api.tutors.data.models;

import jakarta.persistence.*;
import lombok.*;

import java.util.UUID;

@Entity
@Table(name = "materia_tutor")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MateriaTutor {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne
    @JoinColumn(name = "perfil_tutor_id", nullable = false)
    private PerfilTutor perfilTutor;

    @ManyToOne(cascade = CascadeType.PERSIST)
    @JoinColumn(name = "materia_id", nullable = false)
    private Materia materia;

    private String tipoCompensacion;
    private Double precio;
    private String descripcion;
    private String modalidad;
    private String estado;
}
