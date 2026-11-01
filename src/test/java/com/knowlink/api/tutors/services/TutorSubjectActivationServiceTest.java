package com.knowlink.api.tutors.services;

import com.knowlink.api.exceptions.custom_exceptions.BusinessConflictException;
import com.knowlink.api.exceptions.custom_exceptions.ResourceNotFoundException;
import com.knowlink.api.materials.service.interfaces.IProfileImageService;
import com.knowlink.api.ratings.repositories.IRatingRepository;
import com.knowlink.api.security.enums.Role;
import com.knowlink.api.security.services.JwtService;
import com.knowlink.api.students.services.interfaces.IStudentProfileService;
import com.knowlink.api.tutors.availability.repositories.IAvailabilityBlockRepository;
import com.knowlink.api.tutors.controllers.responses.TutorProfileResponse;
import com.knowlink.api.tutors.controllers.responses.TutorSubjectResponse;
import com.knowlink.api.tutors.data.enums.CompensationType;
import com.knowlink.api.tutors.data.enums.Modality;
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
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.jpa.domain.Specification;

import java.math.BigDecimal;
import java.time.Clock;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TutorSubjectActivationServiceTest {

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
    @Mock
    private Clock clock;
    @Spy
    private TutorProfileMapper tutorProfileMapper = new TutorProfileMapper();

    @InjectMocks
    private TutorProfileServiceImpl service;

    private static final TutorSearchFilters NO_FILTERS =
            new TutorSearchFilters(null, null, null, null, null, null);

    private final UUID tutorUserId = UUID.randomUUID();
    private final UUID tutorProfileId = UUID.randomUUID();
    private final UUID tutorSubjectId = UUID.randomUUID();

    private TutorProfile tutorProfile;
    private TutorSubject tutorSubject;

    @BeforeEach
    void setUp() {
        Career career = Career.builder().careerId(UUID.randomUUID()).name("Ingenieria en Sistemas").build();
        User user = User.builder()
                .userId(tutorUserId)
                .fullName("Ana Tutora")
                .role(Role.TUTOR)
                .build();
        tutorProfile = TutorProfile.builder()
                .tutorProfileId(tutorProfileId)
                .user(user)
                .career(career)
                .verified(true)
                .averageRating(4.5)
                .build();
        tutorSubject = TutorSubject.builder()
                .tutorSubjectId(tutorSubjectId)
                .tutorProfile(tutorProfile)
                .subject(Subject.builder().name("Base de Datos").career(career).build())
                .modality(Modality.VIRTUAL)
                .compensationType(CompensationType.PAID)
                .pricePerHour(new BigDecimal("2500.00"))
                .tutorSubjectStatus(TutorSubjectStatus.ACTIVE)
                .build();
        tutorProfile.getSubjects().add(tutorSubject);
    }

    private void givenOwnedSubject() {
        when(tutorProfileValidationService.findTutorProfileOrThrowException(tutorUserId)).thenReturn(tutorProfile);
        when(tutorSubjectRepository.findOwnedByIdForUpdate(tutorSubjectId, tutorProfileId))
                .thenReturn(Optional.of(tutorSubject));
    }

    @Test
    @DisplayName("deactivate marca active=false y conserva el estado de validacion")
    void deactivate_marksInactiveAndPreservesValidationStatus() {
        givenOwnedSubject();
        when(tutorSubjectRepository.save(tutorSubject)).thenReturn(tutorSubject);

        TutorSubjectResponse response = service.deactivateTutorSubject(tutorUserId, tutorSubjectId);

        assertThat(response.active()).isFalse();
        assertThat(response.verificationStatus()).isEqualTo("ACTIVE");
        assertThat(tutorSubject.isActive()).isFalse();
        assertThat(tutorSubject.getTutorSubjectStatus()).isEqualTo(TutorSubjectStatus.ACTIVE);
        verify(tutorProfileValidationService).ifSubjectHasActiveBookingsThrowException(tutorSubject);
        verify(tutorSubjectRepository).save(tutorSubject);
    }

    @Test
    @DisplayName("deactivate con reservas activas lanza 409 y no guarda cambios")
    void deactivate_withActiveBookings_throwsConflictAndDoesNotSave() {
        givenOwnedSubject();
        doThrow(new BusinessConflictException(
                "SUBJECT_HAS_ACTIVE_BOOKINGS", "No se puede desactivar.", "active bookings"))
                .when(tutorProfileValidationService).ifSubjectHasActiveBookingsThrowException(tutorSubject);

        assertThatThrownBy(() -> service.deactivateTutorSubject(tutorUserId, tutorSubjectId))
                .isInstanceOfSatisfying(BusinessConflictException.class,
                        ex -> assertThat(ex.getErrorCode()).isEqualTo("SUBJECT_HAS_ACTIVE_BOOKINGS"));

        assertThat(tutorSubject.isActive()).isTrue();
        verify(tutorSubjectRepository, never()).save(any());
    }

    @Test
    @DisplayName("deactivate sobre una materia ya inactiva es idempotente y no guarda")
    void deactivate_alreadyInactive_isIdempotent() {
        givenOwnedSubject();
        tutorSubject.setActive(false);

        TutorSubjectResponse response = service.deactivateTutorSubject(tutorUserId, tutorSubjectId);

        assertThat(response.active()).isFalse();
        verify(tutorSubjectRepository, never()).save(any());
    }

    @Test
    @DisplayName("reactivate marca active=true y conserva el estado de validacion")
    void reactivate_marksActiveAndPreservesValidationStatus() {
        givenOwnedSubject();
        tutorSubject.setActive(false);
        when(tutorSubjectRepository.save(tutorSubject)).thenReturn(tutorSubject);

        TutorSubjectResponse response = service.reactivateTutorSubject(tutorUserId, tutorSubjectId);

        assertThat(response.active()).isTrue();
        assertThat(response.verificationStatus()).isEqualTo("ACTIVE");
        assertThat(tutorSubject.isActive()).isTrue();
        verify(tutorSubjectRepository).save(tutorSubject);
    }

    @Test
    @DisplayName("reactivate sobre una materia ya activa es idempotente y no guarda")
    void reactivate_alreadyActive_isIdempotent() {
        givenOwnedSubject();

        TutorSubjectResponse response = service.reactivateTutorSubject(tutorUserId, tutorSubjectId);

        assertThat(response.active()).isTrue();
        verify(tutorSubjectRepository, never()).save(any());
    }

    @Test
    @DisplayName("materia inexistente o de otro tutor devuelve TUTOR_SUBJECT_NOT_FOUND")
    void deactivate_notOwned_throwsNotFound() {
        when(tutorProfileValidationService.findTutorProfileOrThrowException(tutorUserId)).thenReturn(tutorProfile);
        when(tutorSubjectRepository.findOwnedByIdForUpdate(tutorSubjectId, tutorProfileId))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.deactivateTutorSubject(tutorUserId, tutorSubjectId))
                .isInstanceOfSatisfying(ResourceNotFoundException.class,
                        ex -> assertThat(ex.getErrorCode()).isEqualTo("TUTOR_SUBJECT_NOT_FOUND"));
    }

    @Test
    @DisplayName("searchTutor por nombre solo devuelve las materias activas")
    void searchTutor_byTutorName_onlyIncludesActiveSubjects() {
        TutorSubject inactive = TutorSubject.builder()
                .tutorSubjectId(UUID.randomUUID())
                .tutorProfile(tutorProfile)
                .subject(Subject.builder().name("Programacion").career(tutorProfile.getCareer()).build())
                .modality(Modality.VIRTUAL)
                .compensationType(CompensationType.PAID)
                .tutorSubjectStatus(TutorSubjectStatus.ACTIVE)
                .active(false)
                .build();
        tutorProfile.getSubjects().add(inactive);

        when(tutorSubjectRepository.findAll(any(Specification.class))).thenReturn(List.of());
        when(tutorProfileRepository.findByUser_FullNameContainingIgnoreCase("Ana"))
                .thenReturn(List.of(tutorProfile));
        when(ratingRepository.countVisibleTutorRatings(tutorUserId)).thenReturn(0L);

        List<TutorSearchResponse> result = service.searchTutor("Ana", null, NO_FILTERS);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).subjects()).extracting(SubjectSummary::name)
                .containsExactly("Base de Datos");
    }

    @Test
    @DisplayName("searchTutor por nombre omite al tutor con todas sus materias inactivas")
    void searchTutor_byTutorName_allInactiveTutorIsExcluded() {
        tutorSubject.setActive(false);

        when(tutorSubjectRepository.findAll(any(Specification.class))).thenReturn(List.of());
        when(tutorProfileRepository.findByUser_FullNameContainingIgnoreCase("Ana"))
                .thenReturn(List.of(tutorProfile));

        List<TutorSearchResponse> result = service.searchTutor("Ana", null, NO_FILTERS);

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("getTutorProfile usa solo las materias activas del tutor")
    void getTutorProfile_onlyUsesActiveSubjects() {
        when(tutorProfileValidationService.findTutorProfileOrThrowException(tutorUserId)).thenReturn(tutorProfile);
        when(tutorSubjectRepository.findActiveByTutorProfileId(tutorProfileId))
                .thenReturn(List.of(tutorSubject));
        when(ratingRepository.findVisibleTutorRatings(tutorUserId)).thenReturn(List.of());
        when(availabilityBlockRepository.findAvailableByTutorProfileId(tutorProfileId)).thenReturn(List.of());

        TutorProfileResponse response = service.getTutorProfile(tutorUserId, null);

        assertThat(response.subjects()).hasSize(1);
        assertThat(response.subjects().get(0).active()).isTrue();
        verify(tutorSubjectRepository).findActiveByTutorProfileId(tutorProfileId);
    }
}
