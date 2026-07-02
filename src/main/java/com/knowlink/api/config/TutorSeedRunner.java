package com.knowlink.api.config;

import com.knowlink.api.security.enums.Role;
import com.knowlink.api.tutors.data.models.*;
import com.knowlink.api.tutors.repositories.*;
import com.knowlink.api.users.data.enums.UserStatus;
import com.knowlink.api.users.data.models.User;
import com.knowlink.api.users.repositories.IUserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

@Configuration
@RequiredArgsConstructor
@Slf4j
public class TutorSeedRunner {

    private final IUserRepository userRepository;
    private final PerfilTutorRepository perfilTutorRepository;
    private final MateriaRepository materiaRepository;
    private final MateriaTutorRepository materiaTutorRepository;
    private final CalificacionRepository calificacionRepository;
    private final BloqueDisponibilidadRepository bloqueDisponibilidadRepository;
    private final MaterialAcademicoRepository materialAcademicoRepository;
    private final PasswordEncoder passwordEncoder;

    @Bean
    public CommandLineRunner seedTutorDemo() {
        return args -> {
            String email = "tutor.demo@knowlink.com";
            var tutorOpt = userRepository.findByEmail(email);
            
            if (tutorOpt.isPresent()) {
                User tutor = tutorOpt.get();
                // Si el rol es diferente, actualizarlo
                if (!tutor.getRole().equals(Role.TUTOR)) {
                    tutor.setRole(Role.TUTOR);
                    userRepository.save(tutor);
                    log.info("✓ Rol del tutor demo actualizado a TUTOR");
                } else {
                    log.info("Tutor demo ya existe con rol TUTOR, se omite el seed.");
                }
                return;
            }

            User tutor = userRepository.save(User.builder()
                    .email(email)
                    .password(passwordEncoder.encode("Password123!"))
                    .firstName("Ana")
                    .lastName("García")
                    .profilePicture("https://example.com/avatar.png")
                    .role(Role.TUTOR)
                    .status(UserStatus.ACTIVE)
                    .createdAt(LocalDateTime.now())
                    .build());

            PerfilTutor perfil = perfilTutorRepository.save(PerfilTutor.builder()
                    .user(tutor)
                    .biografia("Tutora de matemáticas y programación con amplia experiencia.")
                    .calificacionPromedio(4.8)
                    .carrera("Ingeniería de Sistemas")
                    .fotoPerfil("https://example.com/tutor.png")
                    .verificado(true)
                    .tiempoMinAntelacionHoras(24)
                    .build());

            Materia matematica = materiaRepository.save(Materia.builder().nombre("Matemáticas").build());
            Materia programacion = materiaRepository.save(Materia.builder().nombre("Programación").build());

            materiaTutorRepository.save(MateriaTutor.builder()
                    .perfilTutor(perfil)
                    .materia(matematica)
                    .tipoCompensacion("Pago por sesión")
                    .precio(25.0)
                    .descripcion("Clases de álgebra y cálculo")
                    .modalidad("Virtual")
                    .estado("ACTIVA")
                    .build());

            materiaTutorRepository.save(MateriaTutor.builder()
                    .perfilTutor(perfil)
                    .materia(programacion)
                    .tipoCompensacion("Pago por sesión")
                    .precio(30.0)
                    .descripcion("Mentoría de Java y Spring Boot")
                    .modalidad("Presencial")
                    .estado("ACTIVA")
                    .build());

            calificacionRepository.save(Calificacion.builder()
                    .perfilTutor(perfil)
                    .puntuacion(5)
                    .comentario("Muy clara y paciente")
                    .fechaCalificacion(LocalDateTime.now())
                    .visible(true)
                    .build());

            bloqueDisponibilidadRepository.save(BloqueDisponibilidad.builder()
                    .perfilTutor(perfil)
                    .dia("Lunes")
                    .horaInicio(LocalTime.of(9, 0))
                    .horaFin(LocalTime.of(11, 0))
                    .disponible(true)
                    .build());

            bloqueDisponibilidadRepository.save(BloqueDisponibilidad.builder()
                    .perfilTutor(perfil)
                    .dia("Miércoles")
                    .horaInicio(LocalTime.of(15, 0))
                    .horaFin(LocalTime.of(17, 0))
                    .disponible(true)
                    .build());

            materialAcademicoRepository.save(MaterialAcademico.builder()
                    .perfilTutor(perfil)
                    .nombre("Guía de álgebra")
                    .urlArchivo("https://example.com/guia.pdf")
                    .fechaSubida(LocalDateTime.now())
                    .disponible(true)
                    .tipoMaterial("PDF")
                    .build());

            log.info("Seed de tutor demo creado con email: {}", email);
        };
    }
}
