package com.knowlink.api.tutors.controllers;

import com.knowlink.api.bookings.data.enums.BookingStatus;
import com.knowlink.api.bookings.data.models.Booking;
import com.knowlink.api.bookings.repositories.IBookingRepository;
import com.knowlink.api.security.enums.Role;
import com.knowlink.api.security.models.UserPrincipal;
import com.knowlink.api.timeslot.data.enums.SlotStatus;
import com.knowlink.api.timeslot.data.models.TimeSlot;
import com.knowlink.api.timeslot.repositories.ITimeSlotRepository;
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
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class TutorSubjectActivationControllerTest {

    private static final String DEACTIVATE_URL = "/api/v1/tutors/subjects/{tutorSubjectId}/deactivate";
    private static final String REACTIVATE_URL = "/api/v1/tutors/subjects/{tutorSubjectId}/reactivate";

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
    private IBookingRepository bookingRepository;

    @Autowired
    private ITimeSlotRepository timeSlotRepository;

    @Autowired
    private EntityManager entityManager;

    private Career career;
    private User tutorUser;
    private TutorProfile tutorProfile;
    private Subject subjectBaseDeDatos;
    private Subject subjectProgramacion;
    private TutorSubject baseDeDatos;
    private TutorSubject programacion;

    @BeforeEach
    void seedData() {
        career = careerRepository.save(Career.builder()
                .name("Carrera activacion " + UUID.randomUUID())
                .build());

        subjectBaseDeDatos = subjectRepository.save(Subject.builder()
                .name("Base de Datos " + UUID.randomUUID())
                .isBasic(true)
                .career(career)
                .build());

        subjectProgramacion = subjectRepository.save(Subject.builder()
                .name("Programacion " + UUID.randomUUID())
                .isBasic(true)
                .career(career)
                .build());

        tutorUser = userRepository.save(User.builder()
                .fullName("Tutor Activacion " + UUID.randomUUID())
                .email("tutor.activation." + UUID.randomUUID() + "@test.com")
                .password("hashed")
                .role(Role.TUTOR)
                .accountStatus(AccountStatus.ACTIVE)
                .build());

        tutorProfile = tutorProfileRepository.save(TutorProfile.builder()
                .user(tutorUser)
                .career(career)
                .verified(true)
                .averageRating(4.5)
                .build());

        baseDeDatos = saveSubject(subjectBaseDeDatos, true);
        programacion = saveSubject(subjectProgramacion, true);
    }

    private TutorSubject saveSubject(Subject subject, boolean active) {
        TutorSubject tutorSubject = TutorSubject.builder()
                .tutorProfile(tutorProfile)
                .subject(subject)
                .pricePerHour(new BigDecimal("2500.00"))
                .compensationType(CompensationType.PAID)
                .tutorSubjectStatus(TutorSubjectStatus.ACTIVE)
                .modality(Modality.VIRTUAL)
                .active(active)
                .build();
        tutorProfile.getSubjects().add(tutorSubject);
        return tutorSubjectRepository.save(tutorSubject);
    }

    private User saveUser(Role role) {
        return userRepository.save(User.builder()
                .fullName("Usuario " + role + " " + UUID.randomUUID())
                .email("user." + role + "." + UUID.randomUUID() + "@test.com")
                .password("hashed")
                .role(role)
                .accountStatus(AccountStatus.ACTIVE)
                .build());
    }

    private RequestPostProcessor as(User user) {
        UserPrincipal principal = new UserPrincipal(user);
        return authentication(
                new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities()));
    }

    private RequestPostProcessor asTutor() {
        return as(tutorUser);
    }

    private RequestPostProcessor asStudent() {
        return as(saveUser(Role.STUDENT));
    }

    private ResultActions patchSubject(String url, UUID id, RequestPostProcessor auth) throws Exception {
        return mockMvc.perform(patch(url, id).with(auth));
    }

    private TutorSubject reload(UUID id) {
        entityManager.flush();
        entityManager.clear();
        return tutorSubjectRepository.findById(id).orElseThrow();
    }

    private void createBooking(TutorSubject tutorSubject, BookingStatus status) {
        User student = saveUser(Role.STUDENT);
        LocalDate sessionDate = LocalDate.now().plusDays(7);
        LocalTime startTime = LocalTime.of(10, 0);
        LocalTime endTime = LocalTime.of(11, 0);

        TimeSlot timeSlot = timeSlotRepository.save(TimeSlot.builder()
                .date(sessionDate)
                .startTime(startTime)
                .endTime(endTime)
                .status(SlotStatus.OCCUPIED)
                .tutorProfileId(tutorProfile.getTutorProfileId())
                .build());

        bookingRepository.save(Booking.builder()
                .amount(new BigDecimal("1200.00"))
                .sessionDate(sessionDate)
                .startTime(startTime)
                .endTime(endTime)
                .topic("Reserva de prueba")
                .modality(Modality.VIRTUAL)
                .bookingStatus(status)
                .timeSlot(timeSlot)
                .tutorSubject(tutorSubject)
                .student(student)
                .tutor(tutorUser)
                .build());
        entityManager.flush();
    }

    @Test
    @DisplayName("200: desactiva solo la materia indicada y conserva la validacion")
    void deactivate_returnsActiveFalse_andDoesNotAffectOtherSubjects() throws Exception {
        patchSubject(DEACTIVATE_URL, baseDeDatos.getTutorSubjectId(), asTutor())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tutorSubjectId").value(baseDeDatos.getTutorSubjectId().toString()))
                .andExpect(jsonPath("$.verificationStatus").value("ACTIVE"))
                .andExpect(jsonPath("$.active").value(false));

        assertThat(reload(baseDeDatos.getTutorSubjectId()).isActive()).isFalse();
        assertThat(reload(baseDeDatos.getTutorSubjectId()).getTutorSubjectStatus())
                .isEqualTo(TutorSubjectStatus.ACTIVE);
        assertThat(reload(programacion.getTutorSubjectId()).isActive()).isTrue();
    }

    @Test
    @DisplayName("200: desactivar una materia ya inactiva es idempotente")
    void deactivate_isIdempotent() throws Exception {
        patchSubject(DEACTIVATE_URL, baseDeDatos.getTutorSubjectId(), asTutor())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(false));

        patchSubject(DEACTIVATE_URL, baseDeDatos.getTutorSubjectId(), asTutor())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(false));

        assertThat(reload(baseDeDatos.getTutorSubjectId()).isActive()).isFalse();
    }

    @Test
    @DisplayName("200: reactiva una materia desactivada")
    void reactivate_returnsActiveTrue() throws Exception {
        baseDeDatos.setActive(false);
        tutorSubjectRepository.saveAndFlush(baseDeDatos);

        patchSubject(REACTIVATE_URL, baseDeDatos.getTutorSubjectId(), asTutor())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(true));

        assertThat(reload(baseDeDatos.getTutorSubjectId()).isActive()).isTrue();
    }

    @Test
    @DisplayName("409: no desactiva una materia con reservas activas")
    void deactivate_withActiveBooking_returns409() throws Exception {
        createBooking(baseDeDatos, BookingStatus.BOOKED);

        patchSubject(DEACTIVATE_URL, baseDeDatos.getTutorSubjectId(), asTutor())
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("SUBJECT_HAS_ACTIVE_BOOKINGS"));

        assertThat(reload(baseDeDatos.getTutorSubjectId()).isActive()).isTrue();
    }

    @Test
    @DisplayName("200: una reserva completada no bloquea la desactivacion")
    void deactivate_withCompletedBooking_returns200() throws Exception {
        createBooking(baseDeDatos, BookingStatus.COMPLETED);

        patchSubject(DEACTIVATE_URL, baseDeDatos.getTutorSubjectId(), asTutor())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(false));

        assertThat(reload(baseDeDatos.getTutorSubjectId()).isActive()).isFalse();
    }

    @Test
    @DisplayName("404: no se puede desactivar la materia de otro tutor")
    void deactivate_fromAnotherTutor_returns404() throws Exception {
        User otherUser = saveUser(Role.TUTOR);
        TutorProfile otherProfile = tutorProfileRepository.save(TutorProfile.builder()
                .user(otherUser)
                .career(career)
                .build());
        TutorSubject otherSubject = tutorSubjectRepository.save(TutorSubject.builder()
                .tutorProfile(otherProfile)
                .subject(subjectBaseDeDatos)
                .compensationType(CompensationType.PAID)
                .tutorSubjectStatus(TutorSubjectStatus.ACTIVE)
                .modality(Modality.VIRTUAL)
                .build());

        patchSubject(DEACTIVATE_URL, otherSubject.getTutorSubjectId(), asTutor())
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value("TUTOR_SUBJECT_NOT_FOUND"));

        assertThat(reload(otherSubject.getTutorSubjectId()).isActive()).isTrue();
    }

    @Test
    @DisplayName("401: sin token no se puede desactivar")
    void deactivate_anonymous_returns401() throws Exception {
        mockMvc.perform(patch(DEACTIVATE_URL, baseDeDatos.getTutorSubjectId()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("403: un estudiante no puede desactivar materias")
    void deactivate_asStudent_returns403() throws Exception {
        patchSubject(DEACTIVATE_URL, baseDeDatos.getTutorSubjectId(), asStudent())
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("El perfil propio incluye las materias desactivadas con active=false")
    void ownProfile_includesInactiveSubjects() throws Exception {
        baseDeDatos.setActive(false);
        tutorSubjectRepository.saveAndFlush(baseDeDatos);

        mockMvc.perform(get("/api/v1/tutors/me/profile").with(asTutor()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.subjects", hasSize(2)))
                .andExpect(jsonPath("$.subjects[?(@.active == false)]", hasSize(1)));
    }

    @Test
    @DisplayName("El perfil publico excluye las materias desactivadas")
    void publicProfile_excludesInactiveSubjects() throws Exception {
        baseDeDatos.setActive(false);
        tutorSubjectRepository.saveAndFlush(baseDeDatos);

        mockMvc.perform(get("/api/v1/tutors/{userId}/profile", tutorUser.getUserId()).with(asStudent()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.subjects", hasSize(1)))
                .andExpect(jsonPath("$.subjects[0].tutorSubjectId")
                        .value(programacion.getTutorSubjectId().toString()))
                .andExpect(jsonPath("$.subjects[0].active").value(true));
    }

    @Test
    @DisplayName("La busqueda por materia no devuelve materias desactivadas")
    void search_excludesInactiveSubjects() throws Exception {
        baseDeDatos.setActive(false);
        tutorSubjectRepository.saveAndFlush(baseDeDatos);

        mockMvc.perform(get("/api/v1/tutors/search/{query}", subjectBaseDeDatos.getName()).with(asStudent()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    @DisplayName("La busqueda por nombre omite al tutor con todas sus materias inactivas")
    void search_byTutorName_excludesTutorWithAllSubjectsInactive() throws Exception {
        baseDeDatos.setActive(false);
        programacion.setActive(false);
        tutorSubjectRepository.saveAllAndFlush(java.util.List.of(baseDeDatos, programacion));

        mockMvc.perform(get("/api/v1/tutors/search/{query}", tutorUser.getFullName()).with(asStudent()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));
    }
}
