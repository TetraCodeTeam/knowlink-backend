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
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class TutorSubjectUpdateControllerTest {

    private static final String SUBJECT_URL = "/api/v1/tutors/subjects/{tutorSubjectId}";
    private static final String TUTOR_SUBJECT_NOT_FOUND = "TUTOR_SUBJECT_NOT_FOUND";

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
    private TutorSubject tutorSubject;
    private UUID tutorSubjectId;

    @BeforeEach
    void seedData() {
        career = careerRepository.save(Career.builder()
                .name("Carrera edición " + UUID.randomUUID())
                .build());

        Subject algebra = subjectRepository.save(Subject.builder()
                .name("Materia edición " + UUID.randomUUID())
                .isBasic(true)
                .career(career)
                .build());

        tutorUser = saveUser("Ana Edit Subject", "ana.edit.subject@test.com", Role.TUTOR);

        tutorProfile = tutorProfileRepository.save(TutorProfile.builder()
                .user(tutorUser)
                .career(career)
                .build());

        tutorSubject = tutorSubjectRepository.save(TutorSubject.builder()
                .tutorProfile(tutorProfile)
                .subject(algebra)
                .pricePerHour(new BigDecimal("5000.00"))
                .compensationType(CompensationType.PAID)
                .tutorSubjectStatus(TutorSubjectStatus.ACTIVE)
                .modality(Modality.VIRTUAL)
                .build());

        tutorSubjectId = tutorSubject.getTutorSubjectId();
    }

    private User saveUser(String fullName, String email, Role role) {
        return userRepository.save(User.builder()
                .fullName(fullName)
                .email(email)
                .password("hashed")
                .role(role)
                .accountStatus(AccountStatus.ACTIVE)
                .build());
    }

    private TutorSubject saveSubjectFor(TutorProfile profile, TutorSubjectStatus status, Modality modality,
            CompensationType compensationType, String pricePerHour) {
        Subject subject = subjectRepository.save(Subject.builder()
                .name("Materia " + UUID.randomUUID())
                .isBasic(true)
                .career(career)
                .build());

        return tutorSubjectRepository.save(TutorSubject.builder()
                .tutorProfile(profile)
                .subject(subject)
                .pricePerHour(pricePerHour == null ? null : new BigDecimal(pricePerHour))
                .compensationType(compensationType)
                .tutorSubjectStatus(status)
                .modality(modality)
                .build());
    }

    private RequestPostProcessor asUser(Role role) {
        User user = User.builder()
                .fullName("Usuario " + role)
                .email("auth." + role + ".edit.subject@test.com")
                .password("hashed")
                .role(role)
                .accountStatus(AccountStatus.ACTIVE)
                .build();
        UserPrincipal principal = new UserPrincipal(user);
        return authentication(
                new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities()));
    }

    private RequestPostProcessor asTutor() {
        UserPrincipal principal = new UserPrincipal(tutorUser);
        return authentication(
                new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities()));
    }

    private String body(String modality, String compensationType, String pricePerHour) {
        return "{\"modality\":" + jsonValue(modality)
                + ",\"compensationType\":" + jsonValue(compensationType)
                + ",\"pricePerHour\":" + pricePerHour + "}";
    }

    private String jsonValue(String value) {
        return value == null ? "null" : "\"" + value + "\"";
    }

    private ResultActions putSubject(UUID id, String content, RequestPostProcessor auth) throws Exception {
        return mockMvc.perform(put(SUBJECT_URL, id)
                .contentType(MediaType.APPLICATION_JSON)
                .content(content)
                .with(auth));
    }

    private TutorSubject reloadSubject(UUID id) {
        entityManager.flush();
        entityManager.clear();
        return tutorSubjectRepository.findById(id).orElseThrow();
    }

    @Test
    @DisplayName("CP-053.1 - 200: cambia precio y modalidad de la materia del tutor")
    void updateSubject_changesPriceAndModality() throws Exception {
        putSubject(tutorSubjectId, body("BOTH", "PAID", "8000.00"), asTutor())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tutorSubjectId").value(tutorSubjectId.toString()))
                .andExpect(jsonPath("$.subjectName").value(tutorSubject.getSubject().getName()))
                .andExpect(jsonPath("$.modality").value("BOTH"))
                .andExpect(jsonPath("$.compensationType").value("PAID"))
                .andExpect(jsonPath("$.pricePerHour").value(8000.00));

        TutorSubject updated = reloadSubject(tutorSubjectId);
        assertThat(updated.getModality()).isEqualTo(Modality.BOTH);
        assertThat(updated.getCompensationType()).isEqualTo(CompensationType.PAID);
        assertThat(updated.getPricePerHour()).isEqualByComparingTo("8000.00");
    }

    @Test
    @DisplayName("CP-053.2 - 200: pasar de pago a gratuito deja el precio en null")
    void updateSubject_freeCompensationClearsPrice() throws Exception {
        putSubject(tutorSubjectId, body("VIRTUAL", "FREE", "3000.00"), asTutor())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.compensationType").value("FREE"))
                .andExpect(jsonPath("$.pricePerHour").isEmpty());

        TutorSubject updated = reloadSubject(tutorSubjectId);
        assertThat(updated.getCompensationType()).isEqualTo(CompensationType.FREE);
        assertThat(updated.getPricePerHour()).isNull();
    }

    @Test
    @DisplayName("CP-053.3 - 400: precio inválido en materia paga no modifica la materia")
    void updateSubject_invalidPriceForPaidSubject() throws Exception {
        for (String price : List.of("0", "-10.00", "null")) {
            putSubject(tutorSubjectId, body("BOTH", "PAID", price), asTutor())
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.detail").value("VALIDATION_ERROR"));
        }

        TutorSubject unchanged = reloadSubject(tutorSubjectId);
        assertThat(unchanged.getModality()).isEqualTo(Modality.VIRTUAL);
        assertThat(unchanged.getCompensationType()).isEqualTo(CompensationType.PAID);
        assertThat(unchanged.getPricePerHour()).isEqualByComparingTo("5000.00");
    }

    @Test
    @DisplayName("CP-053.4 - 400: body sin modalidad no modifica la materia")
    void updateSubject_missingModality() throws Exception {
        String content = "{\"compensationType\":\"PAID\",\"pricePerHour\":8000.00}";

        putSubject(tutorSubjectId, content, asTutor())
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("VALIDATION_ERROR"));

        TutorSubject unchanged = reloadSubject(tutorSubjectId);
        assertThat(unchanged.getModality()).isEqualTo(Modality.VIRTUAL);
        assertThat(unchanged.getPricePerHour()).isEqualByComparingTo("5000.00");
    }

    @Test
    @DisplayName("CP-053.5 - 200: el subjectName enviado en el body se ignora")
    void updateSubject_ignoresSubjectName() throws Exception {
        String originalName = tutorSubject.getSubject().getName();
        String content = "{\"subjectName\":\"Materia Hackeada\",\"modality\":\"BOTH\",\"compensationType\":\"PAID\","
                + "\"pricePerHour\":8000.00}";

        putSubject(tutorSubjectId, content, asTutor())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.subjectName").value(originalName));

        TutorSubject updated = reloadSubject(tutorSubjectId);
        assertThat(updated.getSubject().getName()).isEqualTo(originalName);
    }

    @Test
    @DisplayName("CP-053.6 - 404: la materia de otro tutor no se puede editar")
    void updateSubject_fromAnotherTutorReturnsNotFound() throws Exception {
        User otherTutor = saveUser("Otro Tutor", "otro.edit.subject@test.com", Role.TUTOR);
        TutorProfile otherProfile = tutorProfileRepository.save(TutorProfile.builder()
                .user(otherTutor)
                .career(career)
                .build());
        TutorSubject otherSubject = saveSubjectFor(otherProfile, TutorSubjectStatus.ACTIVE, Modality.IN_PERSON,
                CompensationType.PAID, "4000.00");
        UUID otherSubjectId = otherSubject.getTutorSubjectId();

        putSubject(otherSubjectId, body("VIRTUAL", "PAID", "9000.00"), asTutor())
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value(TUTOR_SUBJECT_NOT_FOUND));

        TutorSubject unchanged = reloadSubject(otherSubjectId);
        assertThat(unchanged.getModality()).isEqualTo(Modality.IN_PERSON);
        assertThat(unchanged.getPricePerHour()).isEqualByComparingTo("4000.00");
    }

    @Test
    @DisplayName("CP-053.7 - 404: materia inexistente")
    void updateSubject_notFound() throws Exception {
        putSubject(UUID.randomUUID(), body("BOTH", "PAID", "8000.00"), asTutor())
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value(TUTOR_SUBJECT_NOT_FOUND));
    }

    @Test
    @DisplayName("CP-053.8 - 403: un estudiante no puede editar materias")
    void updateSubject_forbiddenForStudent() throws Exception {
        putSubject(tutorSubjectId, body("BOTH", "PAID", "8000.00"), asUser(Role.STUDENT))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("CP-053.9 - el estado de validación se conserva al editar")
    void updateSubject_preservesValidationStatus() throws Exception {
        TutorSubject pendingSubject = saveSubjectFor(tutorProfile, TutorSubjectStatus.PENDING, Modality.VIRTUAL,
                CompensationType.PAID, "3000.00");
        UUID pendingSubjectId = pendingSubject.getTutorSubjectId();

        putSubject(tutorSubjectId, body("BOTH", "PAID", "8000.00"), asTutor())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.verificationStatus").value("ACTIVE"));

        putSubject(pendingSubjectId, body("BOTH", "PAID", "8500.00"), asTutor())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.verificationStatus").value("PENDING"));

        assertThat(reloadSubject(tutorSubjectId).getTutorSubjectStatus()).isEqualTo(TutorSubjectStatus.ACTIVE);
        assertThat(reloadSubject(pendingSubjectId).getTutorSubjectStatus()).isEqualTo(TutorSubjectStatus.PENDING);
    }

    @Test
    @DisplayName("CP-053.10 - una reserva confirmada conserva su importe y modalidad")
    void updateSubject_doesNotAffectExistingBooking() throws Exception {
        User student = saveUser("Estudiante Reserva", "estudiante.edit.subject@test.com", Role.STUDENT);
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

        Booking booking = bookingRepository.save(Booking.builder()
                .amount(new BigDecimal("1200.00"))
                .sessionDate(sessionDate)
                .startTime(startTime)
                .endTime(endTime)
                .topic("Reserva previa")
                .modality(Modality.IN_PERSON)
                .bookingStatus(BookingStatus.BOOKED)
                .timeSlot(timeSlot)
                .tutorSubject(tutorSubject)
                .student(student)
                .tutor(tutorUser)
                .build());
        UUID bookingId = booking.getBookingId();

        putSubject(tutorSubjectId, body("VIRTUAL", "PAID", "9000.00"), asTutor())
                .andExpect(status().isOk());

        entityManager.flush();
        entityManager.clear();

        Booking unchanged = bookingRepository.findById(bookingId).orElseThrow();
        assertThat(unchanged.getAmount()).isEqualByComparingTo("1200.00");
        assertThat(unchanged.getModality()).isEqualTo(Modality.IN_PERSON);
        assertThat(reloadSubject(tutorSubjectId).getModality()).isEqualTo(Modality.VIRTUAL);
    }

    @Test
    @DisplayName("CP-053.11 - el perfil propio refleja los cambios inmediatamente")
    void updateSubject_isVisibleInOwnProfile() throws Exception {
        putSubject(tutorSubjectId, body("BOTH", "PAID", "8000.00"), asTutor())
                .andExpect(status().isOk());

        entityManager.flush();
        entityManager.clear();

        mockMvc.perform(get("/api/v1/tutors/me/profile").with(asTutor()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.subjects[0].tutorSubjectId").value(tutorSubjectId.toString()))
                .andExpect(jsonPath("$.subjects[0].modality").value("BOTH"))
                .andExpect(jsonPath("$.subjects[0].compensationType").value("PAID"))
                .andExpect(jsonPath("$.subjects[0].pricePerHour").value(8000.00));
    }
}
