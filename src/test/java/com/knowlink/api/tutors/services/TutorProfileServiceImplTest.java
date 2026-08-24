package com.knowlink.api.tutors.services;

import com.knowlink.api.security.enums.Role;
import com.knowlink.api.tutors.availability.repositories.IAvailabilityBlockRepository;
import com.knowlink.api.tutors.data.mappers.TutorProfileMapper;
import com.knowlink.api.tutors.data.mappers.TutorSubjectMapper;
import com.knowlink.api.tutors.data.models.Career;
import com.knowlink.api.tutors.data.models.Subject;
import com.knowlink.api.tutors.data.models.SubjectSummary;
import com.knowlink.api.tutors.data.models.TutorProfile;
import com.knowlink.api.tutors.data.models.TutorSearchFilters;
import com.knowlink.api.tutors.data.models.TutorSearchResponse;
import com.knowlink.api.tutors.data.models.TutorSubject;
import com.knowlink.api.tutors.repositories.IRatingRepository;
import com.knowlink.api.tutors.repositories.ITutorProfileRepository;
import com.knowlink.api.tutors.repositories.ITutorSubjectRepository;
import com.knowlink.api.tutors.services.implementations.TutorProfileServiceImpl;
import com.knowlink.api.tutors.services.interfaces.ICareerService;
import com.knowlink.api.tutors.services.interfaces.ISubjectService;
import com.knowlink.api.tutors.validations.ITutorProfileValidationService;
import com.knowlink.api.users.data.models.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.jpa.domain.Specification;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TutorProfileServiceImplTest {

    @Mock
    private ITutorSubjectRepository subjectTutorRepository;
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

    private TutorProfileServiceImpl service;

    private final UUID tutorUserId = UUID.randomUUID();

    private static final TutorSearchFilters NO_FILTERS = new TutorSearchFilters(null, null, null, null, null);

    private TutorSubject tutorSubject(String fullName, String subjectName, String careerName) {
        Career career = Career.builder().name(careerName).build();
        Subject subject = Subject.builder().name(subjectName).career(career).build();
        User user = User.builder().userId(tutorUserId).fullName(fullName).role(Role.TUTOR).build();
        TutorProfile profile = TutorProfile.builder()
                .tutorProfileId(UUID.randomUUID())
                .user(user)
                .career(career)
                .averageRating(4.5)
                .build();
        TutorSubject tutorSubject = TutorSubject.builder()
                .tutorSubjectId(UUID.randomUUID())
                .tutorProfile(profile)
                .subject(subject)
                .build();
        profile.getSubjects().add(tutorSubject);
        return tutorSubject;
    }

    private TutorProfile tutorProfile(String fullName, String careerName, List<String> subjectNames) {
        Career career = Career.builder().name(careerName).build();
        User user = User.builder().userId(tutorUserId).fullName(fullName).role(Role.TUTOR).build();
        TutorProfile profile = TutorProfile.builder()
                .tutorProfileId(UUID.randomUUID())
                .user(user)
                .career(career)
                .averageRating(4.0)
                .build();
        subjectNames.forEach(name -> {
            Subject subject = Subject.builder().name(name).career(career).build();
            TutorSubject ts = TutorSubject.builder().tutorProfile(profile).subject(subject).build();
            profile.getSubjects().add(ts);
        });
        return profile;
    }

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
                subjectTutorRepository);
    }

    @Test
    @DisplayName("CP-003.01 - Busqueda por nombre de materia devuelve el tutor que la dicta")
    void searchT_matchesTutorsBySubjectName() {
        when(subjectTutorRepository.findAll(any(Specification.class)))
                .thenReturn(List.of(tutorSubject("Ana García", "Álgebra", "Ingeniería en Sistemas")));
        when(tutorProfileRepository.findByUser_FullNameContainingIgnoreCase("Algebra"))
                .thenReturn(List.of());
        when(ratingRepository.findVisibleByRatedUserId(any())).thenReturn(List.of());

        List<TutorSearchResponse> result = service.searchTutor("Algebra", UUID.randomUUID(), NO_FILTERS);

        assertThat(result).hasSize(1);
        TutorSearchResponse response = result.get(0);
        assertThat(response.tutorId()).isEqualTo(tutorUserId);
        assertThat(response.fullName()).isEqualTo("Ana García");
        assertThat(response.subjects()).containsExactly(
                new SubjectSummary("Álgebra", "Ingeniería en Sistemas"));
    }

    @Test
    @DisplayName("CP-003.02 - Busqueda por nombre completo devuelve el tutor")
    void searchTutor_matchesTutorsByFullName() {
        when(subjectTutorRepository.findAll(any(Specification.class)))
                .thenReturn(List.of());
        when(tutorProfileRepository.findByUser_FullNameContainingIgnoreCase("Ana"))
                .thenReturn(List.of(tutorProfile("Ana García", "Ingeniería en Sistemas", List.of("Álgebra"))));
        when(ratingRepository.findVisibleByRatedUserId(any())).thenReturn(List.of());

        List<TutorSearchResponse> result = service.searchTutor("Ana", UUID.randomUUID(), NO_FILTERS);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).fullName()).isEqualTo("Ana García");
        assertThat(result.get(0).subjects()).hasSize(1);
    }

    @Test
    @DisplayName("CP-003.03 - Matches por materia y por nombre se unifican sin duplicados")
    void searchTutor_mergesSubjectAndNameMatchesWithoutDuplicates() {
        TutorSubject bySubject = tutorSubject("Ana García", "Álgebra", "Ingeniería en Sistemas");
        TutorProfile byName = bySubject.getTutorProfile();

        when(subjectTutorRepository.findAll(any(Specification.class)))
                .thenReturn(List.of(bySubject));
        when(tutorProfileRepository.findByUser_FullNameContainingIgnoreCase("Ana"))
                .thenReturn(List.of(byName));
        when(ratingRepository.findVisibleByRatedUserId(any())).thenReturn(List.of());

        List<TutorSearchResponse> result = service.searchTutor("Ana", UUID.randomUUID(), NO_FILTERS);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).fullName()).isEqualTo("Ana García");
    }

    @Test
    @DisplayName("CP-003.04 - Tutor sin materias cargadas se excluye del resultado")
    void searchTutor_skipsTutorsWithoutSubjectsInNameMatch() {
        TutorProfile noSubjects = tutorProfile("Ana García", "Ingeniería en Sistemas", List.of("Álgebra"));
        noSubjects.getSubjects().clear();

        when(subjectTutorRepository.findAll(any(Specification.class)))
                .thenReturn(List.of());
        when(tutorProfileRepository.findByUser_FullNameContainingIgnoreCase("Ana"))
                .thenReturn(List.of(noSubjects));

        List<TutorSearchResponse> result = service.searchTutor("Ana", UUID.randomUUID(), NO_FILTERS);

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("CP-003.05 - Query sin coincidencias devuelve lista vacia")
    void searchTutor_returnsEmptyWhenNothingMatches() {
        when(subjectTutorRepository.findAll(any(Specification.class)))
                .thenReturn(List.of());
        when(tutorProfileRepository.findByUser_FullNameContainingIgnoreCase("xyz"))
                .thenReturn(List.of());

        List<TutorSearchResponse> result = service.searchTutor("xyz", UUID.randomUUID(), NO_FILTERS);

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("CP-003.06 - La busqueda por materia es case-insensitive")
    void searchTutor_subjectMatch_isCaseInsensitive() {
        when(subjectTutorRepository.findAll(any(Specification.class)))
                .thenReturn(List.of(tutorSubject("Ana Garc\u00eda", "\u00c1lgebra", "Ingenier\u00eda en Sistemas")));
        when(tutorProfileRepository.findByUser_FullNameContainingIgnoreCase("algebra"))
                .thenReturn(List.of());
        when(ratingRepository.findVisibleByRatedUserId(any())).thenReturn(List.of());

        List<TutorSearchResponse> result = service.searchTutor("algebra", UUID.randomUUID(), NO_FILTERS);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).fullName()).isEqualTo("Ana Garc\u00eda");
    }

    @Test
    @DisplayName("CP-003.07 - Tutor con varias materias matcheadas se devuelve una sola vez con todas sus materias")
    void searchTutor_subjectMatch_groupedByTutor_withAllSubjects() {
        Career career = Career.builder().name("Ingenier\u00eda en Sistemas").build();
        User user = User.builder().userId(tutorUserId).fullName("Ana Garc\u00eda").role(Role.TUTOR).build();
        TutorProfile profile = TutorProfile.builder()
                .tutorProfileId(UUID.randomUUID())
                .user(user)
                .career(career)
                .averageRating(4.5)
                .build();
        TutorSubject algebra = TutorSubject.builder()
                .tutorSubjectId(UUID.randomUUID())
                .tutorProfile(profile)
                .subject(Subject.builder().name("\u00c1lgebra").career(career).build())
                .build();
        TutorSubject fisica = TutorSubject.builder()
                .tutorSubjectId(UUID.randomUUID())
                .tutorProfile(profile)
                .subject(Subject.builder().name("F\u00edsica").career(career).build())
                .build();
        profile.getSubjects().addAll(List.of(algebra, fisica));

        when(subjectTutorRepository.findAll(any(Specification.class)))
                .thenReturn(List.of(algebra, fisica));
        when(tutorProfileRepository.findByUser_FullNameContainingIgnoreCase("a"))
                .thenReturn(List.of());
        when(ratingRepository.findVisibleByRatedUserId(any())).thenReturn(List.of());

        List<TutorSearchResponse> result = service.searchTutor("a", UUID.randomUUID(), NO_FILTERS);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).subjects()).extracting(SubjectSummary::name)
                .containsExactlyInAnyOrder("\u00c1lgebra", "F\u00edsica");
    }

    @Test
    @DisplayName("CP-003.08 - totalReviews refleja la cantidad real de reseñas visibles del tutor")
    void searchTutor_totalReviewsComesFromVisibleRatings() {
        when(subjectTutorRepository.findAll(any(Specification.class)))
                .thenReturn(List.of(tutorSubject("Ana García", "Álgebra", "Ingeniería en Sistemas")));
        when(tutorProfileRepository.findByUser_FullNameContainingIgnoreCase("Algebra"))
                .thenReturn(List.of());
        when(ratingRepository.findVisibleByRatedUserId(tutorUserId))
                .thenReturn(List.of(
                        com.knowlink.api.tutors.data.models.Rating.builder().build(),
                        com.knowlink.api.tutors.data.models.Rating.builder().build()));

        List<TutorSearchResponse> result = service.searchTutor("Algebra", UUID.randomUUID(), NO_FILTERS);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).totalReviews()).isEqualTo(2);
    }
}
