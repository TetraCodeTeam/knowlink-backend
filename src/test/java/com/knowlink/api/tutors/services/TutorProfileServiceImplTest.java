package com.knowlink.api.tutors.services;

import com.knowlink.api.exceptions.custom_exceptions.ValidationException;
import com.knowlink.api.materials.service.interfaces.IProfileImageService;
import com.knowlink.api.ratings.repositories.IRatingRepository;
import com.knowlink.api.security.enums.Role;
import com.knowlink.api.security.services.JwtService;
import com.knowlink.api.students.services.interfaces.IStudentProfileService;
import com.knowlink.api.tutors.availability.data.models.AvailabilityBlock;
import com.knowlink.api.tutors.availability.repositories.IAvailabilityBlockRepository;
import com.knowlink.api.tutors.data.enums.CompensationType;
import com.knowlink.api.tutors.data.enums.Modality;
import com.knowlink.api.tutors.data.enums.TimeFrame;
import com.knowlink.api.tutors.data.enums.TutorSubjectStatus;
import com.knowlink.api.tutors.data.mappers.TutorProfileMapper;
import com.knowlink.api.tutors.data.mappers.TutorSubjectMapper;
import com.knowlink.api.tutors.data.models.Career;
import com.knowlink.api.tutors.data.models.Subject;
import com.knowlink.api.tutors.data.models.SubjectSummary;
import com.knowlink.api.tutors.data.models.TutorProfile;
import com.knowlink.api.tutors.data.models.TutorSearchFilters;
import com.knowlink.api.tutors.data.models.TutorSearchResponse;
import com.knowlink.api.tutors.data.models.TutorSubject;
import com.knowlink.api.tutors.repositories.ITutorProfileRepository;
import com.knowlink.api.tutors.repositories.ITutorSubjectRepository;
import com.knowlink.api.tutors.services.implementations.TutorProfileServiceImpl;
import com.knowlink.api.tutors.services.interfaces.ICareerService;
import com.knowlink.api.tutors.services.interfaces.ISubjectService;
import com.knowlink.api.tutors.services.interfaces.ITutorSubjectAssemblyService;
import com.knowlink.api.tutors.validations.ITutorProfileValidationService;
import com.knowlink.api.users.data.models.User;
import com.knowlink.api.users.repositories.IUserRepository;
import com.knowlink.api.users.services.interfaces.IUserProfileLookupService;
import com.knowlink.api.users.services.interfaces.IUserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.jpa.domain.Specification;

import java.time.Clock;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TutorProfileServiceImplTest {

    @Mock
    private ITutorSubjectRepository tutorSubjectRepository;
    @Mock
    private IRatingRepository ratingRepository;
    @Mock
    private IAvailabilityBlockRepository availabilityBlockRepository;
    @Mock
    private ITutorProfileRepository tutorProfileRepository;
    @Mock
    private ICareerService careerService;
    @Mock
    private ISubjectService subjectService;
    @Mock
    private ITutorProfileValidationService tutorProfileValidationService;
    @Mock
    private TutorProfileMapper tutorProfileMapper;
    @Mock
    private TutorSubjectMapper tutorSubjectMapper;
    @Mock
    private ITutorSubjectAssemblyService tutorSubjectAssemblyService;
    @Mock
    private IStudentProfileService studentProfileService;
    @Mock
    private IUserService userService;
    @Mock
    private JwtService jwtService;
    @Mock
    private IUserProfileLookupService userProfileLookupService;
    @Mock
    private IProfileImageService profileImageService;
    @Mock
    private IUserRepository userRepository;

    private final Clock clock = Clock.fixed(Instant.parse("2026-05-01T12:00:00Z"), ZoneOffset.UTC);

    private TutorProfileServiceImpl service;

    private static final TutorSearchFilters NO_FILTERS =
            new TutorSearchFilters(null, null, null, null, null, null);

    @BeforeEach
    void setUp() {
        service = new TutorProfileServiceImpl(
                tutorSubjectRepository,
                ratingRepository,
                availabilityBlockRepository,
                tutorProfileRepository,
                careerService,
                subjectService,
                tutorProfileValidationService,
                tutorProfileMapper,
                tutorSubjectMapper,
                tutorSubjectAssemblyService,
                studentProfileService,
                userService,
                jwtService,
                userProfileLookupService,
                profileImageService,
                userRepository,
                clock);
    }

    private TutorProfile tutorProfile(String fullName, double rating) {
        Career career = Career.builder().careerId(UUID.randomUUID()).name("Ingeniería en Sistemas").build();
        User user = User.builder()
                .userId(UUID.randomUUID())
                .fullName(fullName)
                .role(Role.TUTOR)
                .build();
        return TutorProfile.builder()
                .tutorProfileId(UUID.randomUUID())
                .user(user)
                .career(career)
                .averageRating(rating)
                .build();
    }

    private TutorSubject tutorSubject(TutorProfile profile, String subjectName, Modality modality,
            CompensationType compensation, TutorSubjectStatus status) {
        Subject subject = Subject.builder()
                .name(subjectName)
                .career(profile.getCareer())
                .build();
        TutorSubject tutorSubject = TutorSubject.builder()
                .tutorSubjectId(UUID.randomUUID())
                .tutorProfile(profile)
                .subject(subject)
                .modality(modality)
                .compensationType(compensation)
                .tutorSubjectStatus(status)
                .build();
        profile.getSubjects().add(tutorSubject);
        return tutorSubject;
    }

    @Test
    @DisplayName("CP-003.01 - Búsqueda por nombre de materia devuelve el tutor que la dicta")
    void searchTutor_matchesTutorsBySubjectName() {
        TutorProfile profile = tutorProfile("Ana García", 4.5);
        TutorSubject algebra = tutorSubject(profile, "Álgebra", Modality.VIRTUAL, CompensationType.PAID,
                TutorSubjectStatus.ACTIVE);

        when(tutorSubjectRepository.findAll(any(Specification.class))).thenReturn(List.of(algebra));
        when(tutorProfileRepository.findByUser_FullNameContainingIgnoreCase("Algebra")).thenReturn(List.of());

        List<TutorSearchResponse> result = service.searchTutor("Algebra", UUID.randomUUID(), NO_FILTERS);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).fullName()).isEqualTo("Ana García");
        assertThat(result.get(0).subjects()).containsExactly(
                new SubjectSummary("Álgebra", "Ingeniería en Sistemas"));
    }

    @Test
    @DisplayName("CP-003.02 - Búsqueda por nombre completo devuelve el tutor")
    void searchTutor_matchesTutorsByFullName() {
        TutorProfile profile = tutorProfile("Ana García", 4.5);
        tutorSubject(profile, "Álgebra", Modality.VIRTUAL, CompensationType.PAID, TutorSubjectStatus.ACTIVE);

        when(tutorSubjectRepository.findAll(any(Specification.class))).thenReturn(List.of());
        when(tutorProfileRepository.findByUser_FullNameContainingIgnoreCase("Ana")).thenReturn(List.of(profile));

        List<TutorSearchResponse> result = service.searchTutor("Ana", UUID.randomUUID(), NO_FILTERS);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).fullName()).isEqualTo("Ana García");
        assertThat(result.get(0).subjects()).hasSize(1);
    }

    @Test
    @DisplayName("CP-003.03 - Coincidencias por materia y por nombre se unifican sin duplicados")
    void searchTutor_mergesSubjectAndNameMatchesWithoutDuplicates() {
        TutorProfile profile = tutorProfile("Ana García", 4.5);
        TutorSubject algebra = tutorSubject(profile, "Álgebra", Modality.VIRTUAL, CompensationType.PAID,
                TutorSubjectStatus.ACTIVE);

        when(tutorSubjectRepository.findAll(any(Specification.class))).thenReturn(List.of(algebra));
        when(tutorProfileRepository.findByUser_FullNameContainingIgnoreCase("Ana")).thenReturn(List.of(profile));

        List<TutorSearchResponse> result = service.searchTutor("Ana", UUID.randomUUID(), NO_FILTERS);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).fullName()).isEqualTo("Ana García");
    }

    @Test
    @DisplayName("CP-003.04 - Tutor sin materias activas se excluye del resultado")
    void searchTutor_skipsTutorsWithoutActiveSubjectsInNameMatch() {
        TutorProfile profile = tutorProfile("Ana García", 4.5);
        TutorSubject inactive = tutorSubject(profile, "Álgebra", Modality.VIRTUAL, CompensationType.PAID,
                TutorSubjectStatus.ACTIVE);
        inactive.setActive(false);

        when(tutorSubjectRepository.findAll(any(Specification.class))).thenReturn(List.of());
        when(tutorProfileRepository.findByUser_FullNameContainingIgnoreCase("Ana")).thenReturn(List.of(profile));

        assertThat(service.searchTutor("Ana", UUID.randomUUID(), NO_FILTERS)).isEmpty();
    }

    @Test
    @DisplayName("CP-003.05 - Query sin coincidencias devuelve lista vacía")
    void searchTutor_returnsEmptyWhenNothingMatches() {
        when(tutorSubjectRepository.findAll(any(Specification.class))).thenReturn(List.of());
        when(tutorProfileRepository.findByUser_FullNameContainingIgnoreCase("xyz")).thenReturn(List.of());

        assertThat(service.searchTutor("xyz", UUID.randomUUID(), NO_FILTERS)).isEmpty();
    }

    @Test
    @DisplayName("Tutor con varias materias matcheadas se devuelve una sola vez con todas sus materias")
    void searchTutor_subjectMatch_groupedByTutor_withAllSubjects() {
        TutorProfile profile = tutorProfile("Ana García", 4.5);
        TutorSubject algebra = tutorSubject(profile, "Álgebra", Modality.VIRTUAL, CompensationType.PAID,
                TutorSubjectStatus.ACTIVE);
        TutorSubject fisica = tutorSubject(profile, "Física", Modality.VIRTUAL, CompensationType.PAID,
                TutorSubjectStatus.ACTIVE);

        when(tutorSubjectRepository.findAll(any(Specification.class))).thenReturn(List.of(algebra, fisica));
        when(tutorProfileRepository.findByUser_FullNameContainingIgnoreCase("a")).thenReturn(List.of());

        List<TutorSearchResponse> result = service.searchTutor("a", UUID.randomUUID(), NO_FILTERS);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).subjects()).extracting(SubjectSummary::name)
                .containsExactlyInAnyOrder("Álgebra", "Física");
    }

    @Test
    @DisplayName("verifiedOnly en la búsqueda por nombre usa el estado de validación de la materia")
    void searchTutor_verifiedOnlyByName_usesSubjectValidatedStatus() {
        TutorProfile validated = tutorProfile("Vera Activa", 4.5);
        tutorSubject(validated, "Álgebra", Modality.VIRTUAL, CompensationType.PAID, TutorSubjectStatus.ACTIVE);

        TutorProfile pending = tutorProfile("Pablo Pendiente", 4.5);
        tutorSubject(pending, "Álgebra", Modality.VIRTUAL, CompensationType.PAID, TutorSubjectStatus.PENDING);

        TutorSearchFilters filters = new TutorSearchFilters(null, null, null, null, true, null);

        when(tutorSubjectRepository.findAll(any(Specification.class))).thenReturn(List.of());
        when(tutorProfileRepository.findByUser_FullNameContainingIgnoreCase("Test"))
                .thenReturn(List.of(validated, pending));

        List<TutorSearchResponse> result = service.searchTutor("Test", UUID.randomUUID(), filters);

        assertThat(result).extracting(TutorSearchResponse::fullName).containsExactly("Vera Activa");
    }

    @Test
    @DisplayName("Con availability se consultan los bloques una sola vez para todos los candidatos (anti N+1)")
    void searchTutor_bySubject_filtersAvailabilityWithSingleBatchQuery() {
        TutorProfile ana = tutorProfile("Ana García", 4.5);
        TutorProfile beto = tutorProfile("Beto Ríos", 4.5);
        TutorSubject anaAlgebra = tutorSubject(ana, "Álgebra", Modality.VIRTUAL, CompensationType.PAID,
                TutorSubjectStatus.ACTIVE);
        TutorSubject betoAlgebra = tutorSubject(beto, "Álgebra", Modality.VIRTUAL, CompensationType.PAID,
                TutorSubjectStatus.ACTIVE);

        AvailabilityBlock tuesdayMorning = AvailabilityBlock.builder()
                .tutorProfile(ana)
                .date(LocalDate.of(2026, 5, 5))
                .startTime(LocalTime.of(9, 0))
                .endTime(LocalTime.of(11, 0))
                .available(true)
                .build();

        TutorSearchFilters filters = new TutorSearchFilters(
                null, null, Set.of(DayOfWeek.TUESDAY), TimeFrame.MORNING, null, null);

        when(tutorSubjectRepository.findAll(any(Specification.class)))
                .thenReturn(List.of(anaAlgebra, betoAlgebra));
        when(tutorProfileRepository.findByUser_FullNameContainingIgnoreCase("Algebra")).thenReturn(List.of());
        when(availabilityBlockRepository.findUpcomingAvailableByTutorProfileIds(
                anyCollection(), any(LocalDate.class), any(LocalTime.class)))
                .thenReturn(List.of(tuesdayMorning));

        List<TutorSearchResponse> result = service.searchTutor("Algebra", UUID.randomUUID(), filters);

        assertThat(result).extracting(TutorSearchResponse::fullName).containsExactly("Ana García");
        verify(availabilityBlockRepository, times(1))
                .findUpcomingAvailableByTutorProfileIds(anyCollection(), any(LocalDate.class), any(LocalTime.class));
    }

    @Test
    @DisplayName("minRating fuera de 1-5 lanza ValidationException")
    void searchTutor_minRatingOutOfRange_throwsValidationException() {
        TutorSearchFilters filters = new TutorSearchFilters(null, null, null, null, null, 6.0);

        assertThatThrownBy(() -> service.searchTutor("Algebra", UUID.randomUUID(), filters))
                .isInstanceOf(ValidationException.class);
    }
}
