package com.knowlink.api.config;

import com.knowlink.api.security.enums.Role;
import com.knowlink.api.users.data.enums.UserStatus;
import com.knowlink.api.users.data.models.User;
import com.knowlink.api.users.repositories.IUserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * SeedRunner para crear alumno de prueba en la base de datos.
 * Ejecuta al startup y crea un alumno solo si no existe.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class AlumnoSeedRunner implements CommandLineRunner {

    private final IUserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Override
    public void run(String... args) throws Exception {
        // Verificar si el alumno ya existe
        var alumnoOpt = userRepository.findByEmail("alumno.demo@knowlink.com");
        
        if (alumnoOpt.isPresent()) {
            User alumno = alumnoOpt.get();
            // Si el rol es diferente, actualizarlo
            if (!alumno.getRole().equals(Role.ALUMNO)) {
                alumno.setRole(Role.ALUMNO);
                userRepository.save(alumno);
                log.info("✓ Rol del alumno demo actualizado a ALUMNO");
            } else {
                log.info("Alumno demo ya existe con rol ALUMNO, se omite el seed.");
            }
            return;
        }

        // Crear usuario alumno
        User alumno = User.builder()
                .email("alumno.demo@knowlink.com")
                .password(passwordEncoder.encode("Password123!"))
                .firstName("Carlos")
                .lastName("López")
                .role(Role.ALUMNO)
                .status(UserStatus.ACTIVE)
                .build();

        userRepository.save(alumno);
        log.info("✓ Alumno demo creado: {} ({})", alumno.getFirstName() + " " + alumno.getLastName(), alumno.getEmail());
    }
}
