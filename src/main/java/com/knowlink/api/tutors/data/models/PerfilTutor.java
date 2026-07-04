package com.knowlink.api.tutors.data.models;

import com.knowlink.api.users.data.models.User;
import jakarta.persistence.*;
import lombok.*;

import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "perfil_tutor")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PerfilTutor {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "perfil_id", updatable = false, nullable = false)
    private UUID id;

    @OneToOne
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    private String biografia;
    private Double calificacionPromedio;
    private String carrera;
    private String fotoPerfil;
    private boolean verificado;
    private Integer tiempoMinAntelacionHoras;

    @OneToMany(mappedBy = "perfilTutor")
    private List<MateriaTutor> materias;
}
