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

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.temporal.TemporalAdjusters;

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

    // Fechas relativas para que los bloques "futuros" sigan siéndolo en cualquier ejecución.
    private static final LocalDate NEXT_MONDAY = upcoming(DayOfWeek.MONDAY);
    private static final LocalDate NEXT_TUESDAY = upcoming(DayOfWeek.TUESDAY);
    private static final LocalDate NEXT_WEDNESDAY = upcoming(DayOfWeek.WEDNESDAY);
    private static final LocalDate NEXT_FRIDAY = upcoming(DayOfWeek.FRIDAY);
    private static final LocalDate PAST_FRIDAY = nextFridayInThePast();

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

    private static LocalDate upcoming(DayOfWeek day) {
        return LocalDate.now().plusWeeks(1).with(TemporalAdjusters.nextOrSame(day));
    }

    private static LocalDate nextFridayInThePast() {
        return LocalDate.now().minusWeeks(3).with(TemporalAdjusters.nextOrSame(DayOfWeek.FRIDAY));
    }

    @BeforeEach
    void seedData() {
        Career career = careerRepository.findByName("Ingeniería en Sistemas")
                .orElseGet(() -> careerRepository.save(
                        Career.builder().name("Ingeniería en Sistemas").build()));

        Subject algebra = subject(career, "Algebra");
        Subject fisica = subject(career, "Fisica");
        Subject quimica = subject(career, "Quimica");
        Subject sistemas = subject(career, "Sistemas Operativos");
        Subject baseDeDatos = subject(career, "Base de Datos");
        Subject historia = subject(career, "Historia");

        // Ana: materia PENDING (no verificada), con bloques lunes a la tarde y martes a la noche.
        TutorProfile ana = tutor("Ana García", career, 4.5);
        tutorSubject(ana, algebra, Modality.VIRTUAL, CompensationType.PAID, TutorSubjectStatus.PENDING);
        availabilityBlock(ana, NEXT_MONDAY, LocalTime.of(14, 0), LocalTime.of(16, 0), true);
        availabilityBlock(ana, NEXT_TUESDAY, LocalTime.of(19, 0), LocalTime.of(21, 0), true);

        // María: materia ACTIVE, bloque martes a la mañana.
        TutorProfile maria = tutor("María Díaz", career, 4.8);
        tutorSubject(maria, algebra, Modality.VIRTUAL, CompensationType.FREE, TutorSubjectStatus.ACTIVE);
        availabilityBlock(maria, NEXT_TUESDAY, LocalTime.of(9, 0), LocalTime.of(11, 0), true);

        TutorProfile carlos = tutor("Carlos López", career, 4.0);
        tutorSubject(carlos, fisica, Modality.IN_PERSON, CompensationType.PAID, TutorSubjectStatus.ACTIVE);

        // Luis: bloque miércoles a la noche.
        TutorProfile luis = tutor("Luis Pérez", career, 4.2);
        tutorSubject(luis, fisica, Modality.BOTH, CompensationType.PAID, TutorSubjectStatus.ACTIVE);
        availabilityBlock(luis, NEXT_WEDNESDAY, LocalTime.of(19, 0), LocalTime.of(21, 0), true);

        TutorProfile elena = tutor("Elena Ruiz", career, 3.5);
        tutorSubject(elena, quimica, Modality.VIRTUAL, CompensationType.PAID, TutorSubjectStatus.PENDING);

        // Diego y Sofía: mismo tema, distinto estado de validación.
        TutorProfile diego = tutor("Diego Sosa", career, 4.6);
        tutorSubject(diego, sistemas, Modality.VIRTUAL, CompensationType.FREE, TutorSubjectStatus.ACTIVE);

        TutorProfile sofia = tutor("Sofía Vargas", career, 4.0);
        tutorSubject(sofia, sistemas, Modality.VIRTUAL, CompensationType.PAID, TutorSubjectStatus.PENDING);
        // ACTIVE en otra materia: no la vuelve "verificada" para Sistemas Operativos.
        tutorSubject(sofia, baseDeDatos, Modality.VIRTUAL, CompensationType.FREE, TutorSubjectStatus.ACTIVE);

        // Historia: un bloque pasado y uno no disponible no deben contar como disponibilidad.
        TutorProfile pablo = tutor("Pablo Antiguo", career, 4.0);
        tutorSubject(pablo, historia, Modality.VIRTUAL, CompensationType.PAID, TutorSubjectStatus.ACTIVE);
        availabilityBlock(pablo, PAST_FRIDAY, LocalTime.of(9, 0), LocalTime.of(11, 0), true);

        TutorProfile raul = tutor("Raúl Oculto", career, 4.0);
        tutorSubject(raul, historia, Modality.VIRTUAL, CompensationType.PAID, TutorSubjectStatus.ACTIVE);
        availabilityBlock(raul, NEXT_FRIDAY, LocalTime.of(9, 0), LocalTime.of(11, 0), false);

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

    private TutorProfile tutor(String fullName, Career career, double rating) {
        User user = userRepository.save(User.builder()
                .fullName(fullName)
                .email(fullName.replace(" ", ".").toLowerCase() + "@test.com")
                .password("hashed")
                .role(Role.TUTOR)
                .accountStatus(AccountStatus.ACTIVE)
                .build());
        return tutorProfileRepository.save(TutorProfile.builder()
                .user(user)
                .career(career)
                .averageRating(rating)
                .build());
    }

    private void tutorSubject(TutorProfile profile, Subject subject, Modality modality,
            CompensationType compensation, TutorSubjectStatus status) {
        TutorSubject ts = TutorSubject.builder()
                .tutorProfile(profile)
                .subject(subject)
                .modality(modality)
                .compensationType(compensation)
                .tutorSubjectStatus(status)
                .build();
        profile.getSubjects().add(ts);
        tutorSubjectRepository.save(ts);
    }

    private void availabilityBlock(TutorProfile profile, LocalDate date, LocalTime start, LocalTime end,
            boolean available) {
        availabilityBlockRepository.save(AvailabilityBlock.builder()
                .date(date)
                .startTime(start)
                .endTime(end)
                .available(available)
                .repeatWeekly(true)
                .tutorProfile(profile)
                .build());
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
    @DisplayName("Filtro verifiedOnly filtra por el estado de validación de la materia buscada")
    void search_filterByVerifiedOnly_usesSubjectStatus() throws Exception {
        mockMvc.perform(get("/api/v1/tutors/search/Algebra?verifiedOnly=true").with(asStudent()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].fullName").value("María Díaz"));

        // Sofía tiene Base de Datos ACTIVE pero Sistemas Operativos PENDING.
        mockMvc.perform(get("/api/v1/tutors/search/{query}", "Sistemas Operativos").with(asStudent()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[*].fullName", containsInAnyOrder("Diego Sosa", "Sofía Vargas")));

        mockMvc.perform(get("/api/v1/tutors/search/{query}?verifiedOnly=true", "Sistemas Operativos").with(asStudent()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].fullName").value("Diego Sosa"));
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
    @DisplayName("Filtro por días de la semana y franja horaria sobre el mismo bloque")
    void search_filterByAvailability() throws Exception {
        mockMvc.perform(get("/api/v1/tutors/search/Algebra?daysOfWeek=TUESDAY&timeFrame=MORNING").with(asStudent()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].fullName").value("María Díaz"));

        mockMvc.perform(get("/api/v1/tutors/search/Fisica?daysOfWeek=WEDNESDAY&timeFrame=EVENING").with(asStudent()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].fullName").value("Luis Pérez"));

        // Ana tiene bloque el lunes pero a la tarde: día + franja deben coincidir en el mismo bloque.
        mockMvc.perform(get("/api/v1/tutors/search/Algebra?daysOfWeek=MONDAY&timeFrame=MORNING").with(asStudent()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));

        mockMvc.perform(get("/api/v1/tutors/search/Algebra?timeFrame=MORNING").with(asStudent()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].fullName").value("María Díaz"));
    }

    @Test
    @DisplayName("Varios días se combinan con OR, repetidos o separados por coma")
    void search_multipleDays_areOred() throws Exception {
        mockMvc.perform(get("/api/v1/tutors/search/Algebra?daysOfWeek=MONDAY&daysOfWeek=TUESDAY").with(asStudent()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[*].fullName", containsInAnyOrder("Ana García", "María Díaz")));

        mockMvc.perform(get("/api/v1/tutors/search/Algebra?daysOfWeek=MONDAY,TUESDAY").with(asStudent()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[*].fullName", containsInAnyOrder("Ana García", "María Díaz")));

        mockMvc.perform(get("/api/v1/tutors/search/Algebra?daysOfWeek=WEDNESDAY").with(asStudent()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    @DisplayName("Bloques pasados o no disponibles no cuentan como disponibilidad")
    void search_availability_ignoresPastAndUnavailableBlocks() throws Exception {
        mockMvc.perform(get("/api/v1/tutors/search/Historia").with(asStudent()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)));

        mockMvc.perform(get("/api/v1/tutors/search/Historia?daysOfWeek=FRIDAY&timeFrame=MORNING").with(asStudent()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    @DisplayName("AC-2: múltiples filtros se combinan con AND (intersección)")
    void search_multipleFilters_areAnded() throws Exception {
        mockMvc.perform(get("/api/v1/tutors/search/Algebra?modality=VIRTUAL&verifiedOnly=true").with(asStudent()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].fullName").value("María Díaz"));

        mockMvc.perform(get("/api/v1/tutors/search/Algebra?modality=VIRTUAL&compensation=FREE&verifiedOnly=true"
                + "&minRating=4.5&daysOfWeek=TUESDAY&timeFrame=MORNING").with(asStudent()))
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
    void search_invalidEnumValues_return400() throws Exception {
        mockMvc.perform(get("/api/v1/tutors/search/Algebra?modality=PRESENCIAL").with(asStudent()))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/v1/tutors/search/Algebra?daysOfWeek=FUNDAY").with(asStudent()))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/v1/tutors/search/Algebra?timeFrame=NIGHT").with(asStudent()))
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

        // Ana no tiene ninguna materia en estado ACTIVE (verificado por materia).
        mockMvc.perform(get("/api/v1/tutors/search/Ana?verifiedOnly=true").with(asStudent()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));
    }
}
