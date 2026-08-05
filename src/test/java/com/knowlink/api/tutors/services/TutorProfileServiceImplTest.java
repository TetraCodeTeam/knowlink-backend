package com.knowlink.api.tutors.services;

import com.knowlink.api.security.enums.Role;
import com.knowlink.api.tutors.availability.repositories.IAvailabilityBlockRepository;
import com.knowlink.api.tutors.data.mappers.TutorProfileMapper;
import com.knowlink.api.tutors.data.mappers.TutorSubjectMapper;
import com.knowlink.api.tutors.data.models.Career;
import com.knowlink.api.tutors.data.models.Subject;
import com.knowlink.api.tutors.data.models.SubjectSummary;
import com.knowlink.api.tutors.data.models.TutorProfile;
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
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
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
    void searchTutor_matchesTutorsBySubjectName() {
        when(subjectTutorRepository.findBySubject_NameContainingIgnoreCase("Algebra"))
                .thenReturn(List.of(tutorSubject("Ana García", "Álgebra", "Ingeniería en Sistemas")));
        when(tutorProfileRepository.findByUser_FullNameContainingIgnoreCase("Algebra"))
                .thenReturn(List.of());

        List<TutorSearchResponse> result = service.searchTutor("Algebra", UUID.randomUUID());

        assertThat(result).hasSize(1);
        TutorSearchResponse response = result.get(0);
        assertThat(response.tutorId()).isEqualTo(tutorUserId);
        assertThat(response.fullName()).isEqualTo("Ana García");
        assertThat(response.subjects()).containsExactly(
                new SubjectSummary("Álgebra", "Ingeniería en Sistemas"));
    }

    @Test
    void searchTutor_matchesTutorsByFullName() {
        when(subjectTutorRepository.findBySubject_NameContainingIgnoreCase("Ana"))
                .thenReturn(List.of());
        when(tutorProfileRepository.findByUser_FullNameContainingIgnoreCase("Ana"))
                .thenReturn(List.of(tutorProfile("Ana García", "Ingeniería en Sistemas", List.of("Álgebra"))));

        List<TutorSearchResponse> result = service.searchTutor("Ana", UUID.randomUUID());

        assertThat(result).hasSize(1);
        assertThat(result.get(0).fullName()).isEqualTo("Ana García");
        assertThat(result.get(0).subjects()).hasSize(1);
    }

    @Test
    void searchTutor_mergesSubjectAndNameMatchesWithoutDuplicates() {
        TutorSubject bySubject = tutorSubject("Ana García", "Álgebra", "Ingeniería en Sistemas");
        TutorProfile byName = bySubject.getTutorProfile();

        when(subjectTutorRepository.findBySubject_NameContainingIgnoreCase("Ana"))
                .thenReturn(List.of(bySubject));
        when(tutorProfileRepository.findByUser_FullNameContainingIgnoreCase("Ana"))
                .thenReturn(List.of(byName));

        List<TutorSearchResponse> result = service.searchTutor("Ana", UUID.randomUUID());

        assertThat(result).hasSize(1);
        assertThat(result.get(0).fullName()).isEqualTo("Ana García");
    }

    @Test
    void searchTutor_skipsTutorsWithoutSubjectsInNameMatch() {
        TutorProfile noSubjects = tutorProfile("Ana García", "Ingeniería en Sistemas", List.of("Álgebra"));
        noSubjects.getSubjects().clear();

        when(subjectTutorRepository.findBySubject_NameContainingIgnoreCase("Ana"))
                .thenReturn(List.of());
        when(tutorProfileRepository.findByUser_FullNameContainingIgnoreCase("Ana"))
                .thenReturn(List.of(noSubjects));

        List<TutorSearchResponse> result = service.searchTutor("Ana", UUID.randomUUID());

        assertThat(result).isEmpty();
    }

    @Test
    void searchTutor_returnsEmptyWhenNothingMatches() {
        when(subjectTutorRepository.findBySubject_NameContainingIgnoreCase("xyz"))
                .thenReturn(List.of());
        when(tutorProfileRepository.findByUser_FullNameContainingIgnoreCase("xyz"))
                .thenReturn(List.of());

        List<TutorSearchResponse> result = service.searchTutor("xyz", UUID.randomUUID());

        assertThat(result).isEmpty();
    }
}
