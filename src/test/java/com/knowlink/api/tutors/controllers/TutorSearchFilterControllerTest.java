package com.knowlink.api.tutors.controllers;

import com.knowlink.api.security.enums.Role;
import com.knowlink.api.security.models.UserPrincipal;
import com.knowlink.api.tutors.availability.data.models.AvailabilityBlock;
import com.knowlink.api.tutors.availability.repositories.IAvailabilityBlockRepository;
import com.knowlink.api.tutors.data.enums.CompensationType;
import com.knowlink.api.tutors.data.enums.Modality;
import com.knowlink.api.tutors.data.enums.TutorSubjectStatus;
import com.knowlink.api.tutors.data.models.Career;
import com.knowlink.api.tutors.data.models.Subject;
import com.knowlink.api.tutors.data.models.TutorProfile;
import com.knowlink.api.tutors.data.models.TutorSubject;
import com.knowlink.api.tutors.repositories.ICareerRepository;
import com.knowlink.api.tutors.repositories.ISubjectRepository;
import com.knowlink.api.tutors.repositories.ITutorProfileRepository;
import com.knowlink.api.tutors.repositories.ITutorSubjectRepository;
import com.knowlink.api.users.data.enums.AccountStatus;
import com.knowlink.api.users.data.models.User;
import com.knowlink.api.users.repositories.IUserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalTime;

import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class TutorSearchFilterControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ICareerRepository careerRepository;

    @Autowired
    private ISubjectRepository subjectRepository;

    @Autowired
    private IUserRepository userRepository;

    @Autowired
    private ITutorProfileRepository tutorProfileRepository;

    @Autowired
    private ITutorSubjectRepository tutorSubjectRepository;

    @Autowired
    private IAvailabilityBlockRepository availabilityBlockRepository;

    @BeforeEach
    void seedData() {
        Career career = careerRepository.findByName("Ingeniería en Sistemas")
                .orElseGet(() -> careerRepository.save(
                        Career.builder().name("Ingeniería en Sistemas").build()));

        Subject algebra = subject(career, "Algebra");
        Subject fisica = subject(career, "Fisica");
        Subject quimica = subject(career, "Quimica");

        tutor("Ana García", career, algebra, false, 4.5, Modality.VIRTUAL, CompensationType.PAID, null, null);
        tutor("María Díaz", career, algebra, true, 4.8, Modality.VIRTUAL, CompensationType.FREE,
                LocalDate.of(2026, 1, 5), LocalTime.of(9, 0));
        tutor("Carlos López", career, fisica, false, 4.0, Modality.IN_PERSON, CompensationType.PAID, null, null);
        tutor("Luis Pérez", career, fisica, true, 4.2, Modality.BOTH, CompensationType.PAID,
                LocalDate.of(2026, 1, 7), LocalTime.of(19, 0));
        tutor("Elena Ruiz", career, quimica, false, 3.5, Modality.VIRTUAL, CompensationType.PAID, null, null);

        userRepository.save(User.builder()
                .fullName("Estudiante")
                .email("student@test.com")
                .password("hashed")
                .role(Role.STUDENT)
                .accountStatus(AccountStatus.ACTIVE)
                .build());

        availabilityBlockRepository.flush();
    }

    private Subject subject(Career career, String name) {
        return subjectRepository.findByName(name)
                .orElseGet(() -> subjectRepository.save(
                        Subject.builder().name(name).isBasic(true).career(career).build()));
    }

    private void tutor(String fullName, Career career, Subject subject, boolean verified, double rating,
            Modality modality, CompensationType compensation, LocalDate availDate, LocalTime availTime) {
        User user = userRepository.save(User.builder()
                .fullName(fullName)
                .email(fullName.replace(" ", ".").toLowerCase() + "@test.com")
                .password("hashed")
                .role(Role.TUTOR)
                .accountStatus(AccountStatus.ACTIVE)
                .build());
        TutorProfile profile = tutorProfileRepository.save(TutorProfile.builder()
                .user(user)
                .career(career)
                .verified(verified)
                .averageRating(rating)
                .build());
        TutorSubject ts = TutorSubject.builder()
                .tutorProfile(profile)
                .subject(subject)
                .modality(modality)
                .compensationType(compensation)
                .tutorSubjectStatus(TutorSubjectStatus.ACTIVE)
                .build();
        profile.getSubjects().add(ts);
        tutorSubjectRepository.save(ts);

        if (availDate != null && availTime != null) {
            availabilityBlockRepository.save(AvailabilityBlock.builder()
                    .date(availDate)
                    .startTime(availTime)
                    .endTime(availTime.plusHours(2))
                    .available(true)
                    .repeatWeekly(true)
                    .tutorProfile(profile)
                    .build());
        }
    }

    private RequestPostProcessor asStudent() {
        User user = User.builder()
                .fullName("Alumno Autenticado")
                .email("auth.student@test.com")
                .password("hashed")
                .role(Role.STUDENT)
                .accountStatus(AccountStatus.ACTIVE)
                .build();
        UserPrincipal principal = new UserPrincipal(user);
        return authentication(
                new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities()));
    }

    @Test
    @DisplayName("Sin filtros devuelve lo mismo que la búsqueda base de US-06A")
    void search_withoutFilters_returnsBaseResults() throws Exception {
        mockMvc.perform(get("/api/v1/tutors/search/Algebra").with(asStudent()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[*].fullName",
                        containsInAnyOrder("Ana García", "María Díaz")));
    }

    @Test
    @DisplayName("Filtro por modalidad VIRTUAL excluye tutores IN_PERSON")
    void search_filterByModalityVirtual() throws Exception {
        mockMvc.perform(get("/api/v1/tutors/search/Fisica?modality=VIRTUAL").with(asStudent()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].fullName").value("Luis Pérez"));

        mockMvc.perform(get("/api/v1/tutors/search/Fisica?modality=IN_PERSON").with(asStudent()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[*].fullName", containsInAnyOrder("Carlos López", "Luis Pérez")));
    }

    @Test
    @DisplayName("Filtro por compensación acota los resultados")
    void search_filterByCompensation() throws Exception {
        mockMvc.perform(get("/api/v1/tutors/search/Algebra?compensation=PAGO").with(asStudent()))
                .andExpect(status().isBadRequest());

        mockMvc.perform(get("/api/v1/tutors/search/Algebra?compensation=FREE").with(asStudent()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].fullName").value("María Díaz"));
    }

    @Test
    @DisplayName("Filtro soloVerificados devuelve solo tutores verificados")
    void search_filterByVerifiedOnly() throws Exception {
        mockMvc.perform(get("/api/v1/tutors/search/Algebra?verifiedOnly=true").with(asStudent()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].fullName").value("María Díaz"));
    }

    @Test
    @DisplayName("Filtro por calificación mínima")
    void search_filterByMinRating() throws Exception {
        mockMvc.perform(get("/api/v1/tutors/search/Algebra?minRating=4.5").with(asStudent()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[*].fullName", containsInAnyOrder("Ana García", "María Díaz")));

        mockMvc.perform(get("/api/v1/tutors/search/Algebra?minRating=4.6").with(asStudent()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].fullName").value("María Díaz"));
    }

    @Test
    @DisplayName("Filtro por disponibilidad horaria (día y franja)")
    void search_filterByAvailability() throws Exception {
        mockMvc.perform(get("/api/v1/tutors/search/Algebra?dayOfWeek=MONDAY&timeFrame=MORNING").with(asStudent()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].fullName").value("María Díaz"));

        mockMvc.perform(get("/api/v1/tutors/search/Fisica?dayOfWeek=WEDNESDAY&timeFrame=EVENING").with(asStudent()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].fullName").value("Luis Pérez"));
    }

    @Test
    @DisplayName("AC-2: múltiples filtros se combinan con AND (intersección)")
    void search_multipleFilters_areAnded() throws Exception {
        mockMvc.perform(get("/api/v1/tutors/search/Algebra?modality=VIRTUAL&verifiedOnly=true").with(asStudent()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].fullName").value("María Díaz"));
    }

    @Test
    @DisplayName("AC-5: búsqueda base con resultados + filtros sin match devuelve lista vacía")
    void search_filtersWithNoMatch_returnEmptyList() throws Exception {
        mockMvc.perform(get("/api/v1/tutors/search/Algebra").with(asStudent()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)));

        mockMvc.perform(get("/api/v1/tutors/search/Algebra?modality=IN_PERSON").with(asStudent()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    @DisplayName("Edge: verificados inexistentes en la materia dan lista vacía, no error")
    void search_verifiedWithoutMatches_returnsEmptyList() throws Exception {
        mockMvc.perform(get("/api/v1/tutors/search/Quimica?verifiedOnly=true").with(asStudent()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    @DisplayName("Edge: calificacionMinima fuera de rango devuelve 400")
    void search_minRatingOutOfRange_returns400() throws Exception {
        mockMvc.perform(get("/api/v1/tutors/search/Algebra?minRating=6").with(asStudent()))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/v1/tutors/search/Algebra?minRating=-1").with(asStudent()))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Edge: valor de enum no reconocido devuelve 400")
    void search_invalidModalityValue_returns400() throws Exception {
        mockMvc.perform(get("/api/v1/tutors/search/Algebra?modality=PRESENCIAL").with(asStudent()))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Búsqueda por nombre con filtros acota las materias del tutor")
    void search_tutorNameWithFilters_filtersItsSubjects() throws Exception {
        mockMvc.perform(get("/api/v1/tutors/search/Ana").with(asStudent()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].fullName").value("Ana García"));

        mockMvc.perform(get("/api/v1/tutors/search/Ana?compensation=FREE").with(asStudent()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));

        mockMvc.perform(get("/api/v1/tutors/search/María?verifiedOnly=true").with(asStudent()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].fullName").value("María Díaz"));

        mockMvc.perform(get("/api/v1/tutors/search/Ana?verifiedOnly=true").with(asStudent()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));
    }
}