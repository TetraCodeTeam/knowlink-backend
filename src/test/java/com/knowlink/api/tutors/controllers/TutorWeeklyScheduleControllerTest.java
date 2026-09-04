package com.knowlink.api.tutors.controllers;

import com.knowlink.api.security.enums.Role;
import com.knowlink.api.security.models.UserPrincipal;
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
import java.util.UUID;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class TutorWeeklyScheduleControllerTest {

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

    private UUID tutorUserId;
    private User tutorUser;

    @BeforeEach
    void seedData() {
        Career career = careerRepository.findByName("Ingeniería en Sistemas")
                .orElseGet(() -> careerRepository.save(
                        Career.builder().name("Ingeniería en Sistemas").build()));

        Subject algebra = subjectRepository.findByName("Algebra")
                .orElseGet(() -> subjectRepository.save(
                        Subject.builder().name("Algebra").isBasic(true).career(career).build()));

        tutorUser = userRepository.save(User.builder()
                .fullName("Ana García")
                .email("ana.schedule@test.com")
                .password("hashed")
                .role(Role.TUTOR)
                .accountStatus(AccountStatus.ACTIVE)
                .build());
        tutorUserId = tutorUser.getUserId();

        TutorProfile profile = tutorProfileRepository.save(TutorProfile.builder()
                .user(tutorUser)
                .career(career)
                .averageRating(4.5)
                .build());

        tutorSubjectRepository.save(TutorSubject.builder()
                .tutorProfile(profile)
                .subject(algebra)
                .compensationType(CompensationType.PAID)
                .tutorSubjectStatus(TutorSubjectStatus.ACTIVE)
                .modality(Modality.VIRTUAL)
                .build());
    }

    private RequestPostProcessor asUser(Role role) {
        User user = User.builder()
                .fullName("Usuario Autenticado")
                .email("auth@test.com")
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

    @Test
    @DisplayName("CP-052.10 - 200: tutor autenticado obtiene agenda semanal con listas vacias")
    void getWeeklySchedule_returns200WithEmptyLists() throws Exception {
        LocalDate from = LocalDate.now().plusWeeks(1).with(java.time.DayOfWeek.MONDAY);
        LocalDate to = from.plusDays(6);

        mockMvc.perform(get("/api/v1/tutors/me/weekly-schedule")
                        .param("from", from.toString())
                        .param("to", to.toString())
                        .with(asTutor()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.from").value(from.toString()))
                .andExpect(jsonPath("$.to").value(to.toString()))
                .andExpect(jsonPath("$.availabilityBlocks").isArray())
                .andExpect(jsonPath("$.bookings").isArray())
                .andExpect(jsonPath("$.summary.confirmedBookingsCount").value(0))
                .andExpect(jsonPath("$.summary.freeBlocksCount").value(0))
                .andExpect(jsonPath("$.summary.nextClass").isEmpty());
    }

    @Test
    @DisplayName("CP-052.11 - 403: un estudiante autenticado no puede acceder a la agenda del tutor")
    void getWeeklySchedule_returns403_whenCalledByStudent() throws Exception {
        LocalDate from = LocalDate.now().plusWeeks(1).with(java.time.DayOfWeek.MONDAY);
        LocalDate to = from.plusDays(6);

        mockMvc.perform(get("/api/v1/tutors/me/weekly-schedule")
                        .param("from", from.toString())
                        .param("to", to.toString())
                        .with(asUser(Role.STUDENT)))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("CP-052.12 - 401: un usuario anonimo no esta autenticado")
    void getWeeklySchedule_returns401_whenCalledByAnonymous() throws Exception {
        LocalDate from = LocalDate.now().plusWeeks(1).with(java.time.DayOfWeek.MONDAY);
        LocalDate to = from.plusDays(6);

        mockMvc.perform(get("/api/v1/tutors/me/weekly-schedule")
                        .param("from", from.toString())
                        .param("to", to.toString()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("CP-052.13 - 200: endpoint acepta solo GET")
    void getWeeklySchedule_getOnly() throws Exception {
        LocalDate from = LocalDate.now().plusWeeks(1).with(java.time.DayOfWeek.MONDAY);
        LocalDate to = from.plusDays(6);

        mockMvc.perform(get("/api/v1/tutors/me/weekly-schedule")
                        .param("from", from.toString())
                        .param("to", to.toString())
                        .with(asTutor()))
                .andExpect(status().isOk());
    }
}
