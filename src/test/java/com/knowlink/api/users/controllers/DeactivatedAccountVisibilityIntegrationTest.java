package com.knowlink.api.users.controllers;

import com.knowlink.api.tutors.data.models.Institution;
import com.knowlink.api.tutors.repositories.IInstitutionRepository;
import com.knowlink.api.testsupport.CatalogFixtures;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.knowlink.api.bookings.data.enums.BookingStatus;
import com.knowlink.api.bookings.data.models.Booking;
import com.knowlink.api.bookings.repositories.IBookingRepository;
import com.knowlink.api.community.data.enums.PostStatus;
import com.knowlink.api.community.data.enums.PostType;
import com.knowlink.api.community.data.models.CommunityPost;
import com.knowlink.api.community.repositories.ICommunityPostRepository;
import com.knowlink.api.materials.data.enums.MaterialType;
import com.knowlink.api.materials.data.models.AcademicMaterial;
import com.knowlink.api.materials.repositories.IAcademicMaterialRepository;
import com.knowlink.api.ratings.data.models.Rating;
import com.knowlink.api.ratings.repositories.IRatingRepository;
import com.knowlink.api.security.enums.Role;
import com.knowlink.api.security.models.UserPrincipal;
import com.knowlink.api.students.data.models.StudentProfile;
import com.knowlink.api.students.repositories.IStudentProfileRepository;
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
import com.knowlink.api.users.utils.UserNameUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class DeactivatedAccountVisibilityIntegrationTest {

    private static final String PASSWORD = "Password123!";

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private IUserRepository userRepository;
    @Autowired
    private IStudentProfileRepository studentProfileRepository;
    @Autowired
    private ITutorProfileRepository tutorProfileRepository;
    @Autowired
    private ITutorSubjectRepository tutorSubjectRepository;
    @Autowired
    private ICareerRepository careerRepository;

    @Autowired
    private IInstitutionRepository institutionRepository;

    private Institution institution;
    @Autowired
    private ISubjectRepository subjectRepository;
    @Autowired
    private ITimeSlotRepository timeSlotRepository;
    @Autowired
    private IBookingRepository bookingRepository;
    @Autowired
    private IRatingRepository ratingRepository;
    @Autowired
    private IAcademicMaterialRepository materialRepository;
    @Autowired
    private ICommunityPostRepository communityPostRepository;
    @Autowired
    private PasswordEncoder passwordEncoder;

    private Career career;
    private Subject subject;
    private User viewer;
    private User tutor;
    private TutorProfile tutorProfile;
    private TutorSubject tutorSubject;

    @BeforeEach
    void setUp() {
        institution = CatalogFixtures.saveInstitution(institutionRepository, "Test " + System.nanoTime());
        career = careerRepository.save(Career.builder().institution(institution)
                .name("Career " + UUID.randomUUID())
                .build());
        subject = subjectRepository.save(Subject.builder().institution(institution)
                .name("Visible " + UUID.randomUUID())
                
                .career(career)
                .build());

        viewer = saveUser("Viewer Student", Role.STUDENT, AccountStatus.ACTIVE);
        studentProfileRepository.save(StudentProfile.builder()
                .user(viewer)
                .career(career)
                .createdAt(LocalDateTime.now())
                .build());

        tutor = saveUser("Active Tutor", Role.TUTOR, AccountStatus.ACTIVE);
        tutorProfile = tutorProfileRepository.save(TutorProfile.builder()
                .user(tutor)
                .career(career)
                .verified(true)
                .averageRating(4.5)
                .build());
        tutorSubject = saveTutorSubject(tutorProfile, subject);
    }

    @Test
    @DisplayName("A deactivated tutor is not discoverable by subject nor by name")
    void search_doesNotReturnDeactivatedTutor() throws Exception {
        User deactivatedTutor = saveUser("Hidden Tutor " + UUID.randomUUID(), Role.TUTOR,
                AccountStatus.DEACTIVATED);
        Subject hiddenSubject = subjectRepository.save(Subject.builder().institution(institution)
                .name("Hidden Subject " + UUID.randomUUID())
                
                .career(career)
                .build());
        TutorProfile hiddenProfile = tutorProfileRepository.save(TutorProfile.builder()
                .user(deactivatedTutor)
                .career(career)
                .verified(true)
                .build());
        saveTutorSubject(hiddenProfile, hiddenSubject);

        mockMvc.perform(get("/api/v1/tutors/search/{query}", hiddenSubject.getName()).with(auth(viewer)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));

        mockMvc.perform(get("/api/v1/tutors/search/{query}", "Hidden Tutor").with(auth(viewer)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    @DisplayName("Public profile, ratings, calendar and SSE return 404 for a deactivated tutor")
    void publicTutorSurfaces_return404() throws Exception {
        User deactivatedTutor = saveUser("Hidden Tutor " + UUID.randomUUID(), Role.TUTOR,
                AccountStatus.DEACTIVATED);
        TutorProfile hiddenProfile = tutorProfileRepository.save(TutorProfile.builder()
                .user(deactivatedTutor)
                .career(career)
                .verified(true)
                .build());
        saveTutorSubject(hiddenProfile, subject);
        TimeSlot slot = saveSlot(hiddenProfile, LocalDate.now().plusDays(7), LocalTime.of(10, 0));

        mockMvc.perform(get("/api/v1/tutors/{id}/profile", deactivatedTutor.getUserId()).with(auth(viewer)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value("TUTOR_PROFILE_NOT_FOUND"));

        mockMvc.perform(get("/api/v1/tutors/{id}/ratings", deactivatedTutor.getUserId()).with(auth(viewer)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value("TUTOR_PROFILE_NOT_FOUND"));

        mockMvc.perform(get("/api/v1/tutors/{id}/booking-slots", deactivatedTutor.getUserId())
                        .param("from", slot.getDate().toString())
                        .param("to", slot.getDate().toString())
                        .with(auth(viewer)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value("TUTOR_PROFILE_NOT_FOUND"));

        mockMvc.perform(get("/api/v1/tutors/{id}/booking-slots/events", deactivatedTutor.getUserId())
                        .with(auth(viewer)))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("Creating a hold on a deactivated tutor returns 404")
    void createHold_onDeactivatedTutor_returns404() throws Exception {
        User deactivatedTutor = saveUser("Hidden Tutor " + UUID.randomUUID(), Role.TUTOR,
                AccountStatus.DEACTIVATED);
        TutorProfile hiddenProfile = tutorProfileRepository.save(TutorProfile.builder()
                .user(deactivatedTutor)
                .career(career)
                .verified(true)
                .build());
        TimeSlot slot = saveSlot(hiddenProfile, LocalDate.now().plusDays(7), LocalTime.of(10, 0));

        String body = objectMapper.writeValueAsString(Map.of(
                "slotId", slot.getTimeSlotId().toString(),
                "start", slot.getDate().atTime(10, 0).atZone(java.time.ZoneId.of("America/Argentina/Cordoba"))
                        .toInstant().toString(),
                "end", slot.getDate().atTime(11, 0).atZone(java.time.ZoneId.of("America/Argentina/Cordoba"))
                        .toInstant().toString()));

        mockMvc.perform(post("/api/v1/tutors/{id}/booking-slots/hold", deactivatedTutor.getUserId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body)
                        .with(auth(viewer)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value("TUTOR_PROFILE_NOT_FOUND"));
    }

    @Test
    @DisplayName("A deactivated tutor's materials are hidden from students and reappear after reactivation")
    void materials_ofDeactivatedTutor_areHiddenAndDataIsRetained() throws Exception {
        User deactivatedTutor = saveUser("Hidden Tutor " + UUID.randomUUID(), Role.TUTOR,
                AccountStatus.DEACTIVATED);
        TutorProfile hiddenProfile = tutorProfileRepository.save(TutorProfile.builder()
                .user(deactivatedTutor)
                .career(career)
                .verified(true)
                .build());
        TutorSubject hiddenSubject = saveTutorSubject(hiddenProfile, subject);
        createBooking(hiddenSubject, deactivatedTutor, viewer, BookingStatus.COMPLETED,
                LocalDate.now().minusDays(10), LocalTime.of(10, 0));
        AcademicMaterial material = materialRepository.save(AcademicMaterial.builder()
                .tutorSubject(hiddenSubject)
                .name("Material oculto")
                .originalFileName("material.pdf")
                .storagePath("path/material.pdf")
                .uploadedAt(LocalDateTime.now())
                .sizeInBytes(1024L)
                .available(true)
                .active(true)
                .materialType(MaterialType.PDF)
                .build());

        mockMvc.perform(get("/api/v1/materials/tutores/{id}/materiales", deactivatedTutor.getUserId())
                        .with(auth(viewer)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));

        mockMvc.perform(get("/api/v1/materials")
                        .param("subjectId", subject.getSubjectId().toString())
                        .with(auth(viewer)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.detail").value("NO_ACTIVE_RESERVATION"));

        // The data is preserved: after a manual reactivation the tutor sees the material again.
        deactivatedTutor.setAccountStatus(AccountStatus.ACTIVE);
        userRepository.save(deactivatedTutor);

        mockMvc.perform(get("/api/v1/materials")
                        .param("subjectId", subject.getSubjectId().toString())
                        .with(auth(deactivatedTutor)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].id").value(material.getAcademicMaterialId().toString()));
    }

    @Test
    @DisplayName("Community posts and rating comments by deactivated accounts are masked")
    void contentOfDeactivatedAccounts_isMasked() throws Exception {
        User deactivatedAuthor = saveUser("Masked Author", Role.STUDENT, AccountStatus.DEACTIVATED);
        deactivatedAuthor.setProfilePictureUrl("https://cdn.test/masked.png");
        userRepository.save(deactivatedAuthor);

        CommunityPost post = communityPostRepository.save(CommunityPost.builder()
                .author(deactivatedAuthor)
                .authorRole(Role.STUDENT)
                .type(PostType.HELP_REQUEST)
                .status(PostStatus.OPEN)
                .subject(subject)
                .title("Duda de un usuario desactivado")
                .content("Contenido conservado.")
                .build());

        mockMvc.perform(get("/api/v1/community/posts").with(auth(viewer)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].author.fullName").value(UserNameUtils.DEACTIVATED_DISPLAY_NAME))
                .andExpect(jsonPath("$.content[0].author.profilePictureUrl").value(nullValue()));

        Booking completedBooking = createBooking(tutorSubject, tutor, deactivatedAuthor, BookingStatus.COMPLETED,
                LocalDate.now().minusDays(5), LocalTime.of(12, 0));
        ratingRepository.save(Rating.builder()
                .booking(completedBooking)
                .ratedUser(tutor)
                .raterUser(deactivatedAuthor)
                .score(4)
                .comment("Comentario conservado.")
                .ratingDate(LocalDateTime.now().minusDays(4))
                .visible(true)
                .build());

        mockMvc.perform(get("/api/v1/tutors/{id}/ratings", tutor.getUserId()).with(auth(viewer)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.comments.content[0].author.fullName")
                        .value(UserNameUtils.DEACTIVATED_DISPLAY_NAME))
                .andExpect(jsonPath("$.comments.content[0].author.profilePictureUrl").value(nullValue()));
    }

    @Test
    @DisplayName("The podium does not include deactivated tutors")
    void podium_excludesDeactivatedTutors() throws Exception {
        User deactivatedTutor = saveUser("Hidden Tutor " + UUID.randomUUID(), Role.TUTOR,
                AccountStatus.DEACTIVATED);
        TutorProfile hiddenProfile = tutorProfileRepository.save(TutorProfile.builder()
                .user(deactivatedTutor)
                .career(career)
                .verified(true)
                .build());
        TutorSubject hiddenSubject = saveTutorSubject(hiddenProfile, subject);

        // Both tutors have completed sessions and ratings, so both would score if the account status was ignored.
        Booking activeBooking = createBooking(tutorSubject, tutor, viewer, BookingStatus.COMPLETED,
                LocalDate.now().minusDays(20), LocalTime.of(9, 0));
        ratingRepository.save(newRating(activeBooking, tutor, viewer, 5));
        Booking hiddenBooking = createBooking(hiddenSubject, deactivatedTutor, viewer, BookingStatus.COMPLETED,
                LocalDate.now().minusDays(21), LocalTime.of(9, 0));
        ratingRepository.save(newRating(hiddenBooking, deactivatedTutor, viewer, 5));

        String response = mockMvc.perform(get("/api/v1/community/podium").with(auth(viewer)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        JsonNode root = objectMapper.readTree(response);
        assertThat(root.toString()).contains(tutor.getUserId().toString());
        assertThat(root.toString()).doesNotContain(deactivatedTutor.getUserId().toString());
    }

    @Test
    @DisplayName("An unknown or deactivated tutor id returns the same 404 body")
    void profile_unknownAndDeactivated_shareSameResponse() throws Exception {
        User deactivatedTutor = saveUser("Hidden Tutor " + UUID.randomUUID(), Role.TUTOR,
                AccountStatus.DEACTIVATED);
        tutorProfileRepository.save(TutorProfile.builder()
                .user(deactivatedTutor)
                .career(career)
                .verified(true)
                .build());

        String deactivatedBody = mockMvc.perform(get("/api/v1/tutors/{id}/profile", deactivatedTutor.getUserId())
                        .with(auth(viewer)))
                .andExpect(status().isNotFound())
                .andReturn().getResponse().getContentAsString();

        String unknownBody = mockMvc.perform(get("/api/v1/tutors/{id}/profile", UUID.randomUUID())
                        .with(auth(viewer)))
                .andExpect(status().isNotFound())
                .andReturn().getResponse().getContentAsString();

        assertThat(deactivatedBody).isEqualTo(unknownBody);
    }

    private User saveUser(String fullName, Role role, AccountStatus status) {
        return userRepository.save(User.builder()
                .fullName(fullName)
                .email(fullName.toLowerCase().replace(' ', '.') + "." + UUID.randomUUID() + "@test.com")
                .password(passwordEncoder.encode(PASSWORD))
                .role(role)
                .accountStatus(status)
                .build());
    }

    private TutorSubject saveTutorSubject(TutorProfile profile, Subject subject) {
        return tutorSubjectRepository.save(TutorSubject.builder()
                .tutorProfile(profile)
                .subject(subject)
                .pricePerHour(new BigDecimal("2000.00"))
                .compensationType(CompensationType.PAID)
                .tutorSubjectStatus(TutorSubjectStatus.ACTIVE)
                .modality(Modality.VIRTUAL)
                .build());
    }

    private TimeSlot saveSlot(TutorProfile profile, LocalDate date, LocalTime startTime) {
        return timeSlotRepository.save(TimeSlot.builder()
                .date(date)
                .startTime(startTime)
                .endTime(startTime.plusHours(1))
                .status(SlotStatus.AVAILABLE)
                .tutorProfileId(profile.getTutorProfileId())
                .build());
    }

    private Booking createBooking(TutorSubject subject, User bookingTutor, User bookingStudent,
            BookingStatus status, LocalDate date, LocalTime startTime) {
        LocalTime endTime = startTime.plusHours(1);
        TimeSlot slot = timeSlotRepository.save(TimeSlot.builder()
                .date(date)
                .startTime(startTime)
                .endTime(endTime)
                .status(SlotStatus.OCCUPIED)
                .tutorProfileId(subject.getTutorProfile().getTutorProfileId())
                .build());
        return bookingRepository.save(Booking.builder()
                .amount(new BigDecimal("2100.00"))
                .sessionDate(date)
                .startTime(startTime)
                .endTime(endTime)
                .topic("Sesion de prueba")
                .modality(Modality.VIRTUAL)
                .bookingStatus(status)
                .timeSlot(slot)
                .tutorSubject(subject)
                .student(bookingStudent)
                .tutor(bookingTutor)
                .build());
    }

    private Rating newRating(Booking booking, User ratedUser, User raterUser, int score) {
        return Rating.builder()
                .booking(booking)
                .ratedUser(ratedUser)
                .raterUser(raterUser)
                .score(score)
                .comment("Comentario de prueba.")
                .ratingDate(LocalDateTime.now().minusDays(1))
                .visible(true)
                .build();
    }

    private RequestPostProcessor auth(User user) {
        UserPrincipal principal = new UserPrincipal(user);
        return SecurityMockMvcRequestPostProcessors.authentication(
                new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities()));
    }
}