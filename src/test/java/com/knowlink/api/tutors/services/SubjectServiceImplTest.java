package com.knowlink.api.tutors.services;

import com.knowlink.api.exceptions.custom_exceptions.ResourceNotFoundException;
import com.knowlink.api.tutors.data.models.Career;
import com.knowlink.api.tutors.data.models.Institution;
import com.knowlink.api.tutors.data.models.Subject;
import com.knowlink.api.tutors.data.models.SubjectSummary;
import com.knowlink.api.tutors.repositories.ISubjectRepository;
import com.knowlink.api.tutors.services.implementations.SubjectServiceImpl;
import com.knowlink.api.users.services.interfaces.IUserProfileLookupService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SubjectServiceImplTest {

    @Mock
    private ISubjectRepository subjectRepository;

    @Mock
    private IUserProfileLookupService userProfileLookupService;

    @InjectMocks
    private SubjectServiceImpl subjectService;

    private static final UUID UTN_ID = UUID.randomUUID();
    private static final UUID UNVM_ID = UUID.randomUUID();
    private static final UUID STUDENT_ID = UUID.randomUUID();

    private static Institution institution(UUID id, String name) {
        return Institution.builder().institutionId(id).name(name).build();
    }

    private static Career career(Institution institution, String name) {
        return Career.builder().careerId(UUID.randomUUID()).name(name).institution(institution).build();
    }

    private static Subject subject(Institution institution, Career career, String name) {
        return Subject.builder().subjectId(UUID.randomUUID()).name(name)
                .institution(institution).career(career).build();
    }

    private void studentBelongsTo(UUID institutionId) {
        when(userProfileLookupService.findInstitutionIdByUserId(STUDENT_ID))
                .thenReturn(Optional.of(institutionId));
    }

    @Test
    void findByNameAndCareer_returnsTheExistingSubjectOfTheSameInstitution() {
        Institution utn = institution(UTN_ID, "UTN FRVM");
        Career utnCareer = career(utn, "Ingeniería en Sistemas");
        Subject existing = subject(utn, utnCareer, "Programación");
        when(subjectRepository.findByInstitution_InstitutionIdAndName(UTN_ID, "Programación"))
                .thenReturn(Optional.of(existing));

        assertThat(subjectService.findByNameAndCareerOrThrowException("Programación", utnCareer))
                .isSameAs(existing);
        verify(subjectRepository, never()).save(any());
    }

    @Test
    void findByNameAndCareer_createsTheSubjectWithTheCareerInstitution() {
        Institution utn = institution(UTN_ID, "UTN FRVM");
        Career utnCareer = career(utn, "Ingeniería en Sistemas");
        when(subjectRepository.findByInstitution_InstitutionIdAndName(UTN_ID, "Programación"))
                .thenReturn(Optional.empty());
        when(subjectRepository.save(any(Subject.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Subject created = subjectService.findByNameAndCareerOrThrowException("Programación", utnCareer);

        assertThat(created.getName()).isEqualTo("Programación");
        assertThat(created.getCareer()).isSameAs(utnCareer);
        assertThat(created.getInstitution()).isSameAs(utn);
    }

    @Test
    void findByNameAndCareer_resolvesByInstitutionSoTheSameNameCanExistInBoth() {
        Institution utn = institution(UTN_ID, "UTN FRVM");
        Institution unvm = institution(UNVM_ID, "UNVM");
        Career utnCareer = career(utn, "Ingeniería en Sistemas");
        Career unvmCareer = career(unvm, "Licenciatura en Administración");
        Subject utnSubject = subject(utn, utnCareer, "Programación");
        Subject unvmSubject = subject(unvm, unvmCareer, "Programación");
        when(subjectRepository.findByInstitution_InstitutionIdAndName(UTN_ID, "Programación"))
                .thenReturn(Optional.of(utnSubject));
        when(subjectRepository.findByInstitution_InstitutionIdAndName(UNVM_ID, "Programación"))
                .thenReturn(Optional.of(unvmSubject));

        assertThat(subjectService.findByNameAndCareerOrThrowException("Programación", utnCareer))
                .isSameAs(utnSubject);
        assertThat(subjectService.findByNameAndCareerOrThrowException("Programación", unvmCareer))
                .isSameAs(unvmSubject);
    }

    @Test
    void findByNameAndCareer_savesUnderTheLookedUpInstitution() {
        Institution unvm = institution(UNVM_ID, "UNVM");
        Career unvmCareer = career(unvm, "Licenciatura en Administración");
        when(subjectRepository.findByInstitution_InstitutionIdAndName(UNVM_ID, "Economía"))
                .thenReturn(Optional.empty());
        when(subjectRepository.save(any(Subject.class))).thenAnswer(invocation -> invocation.getArgument(0));

        subjectService.findByNameAndCareerOrThrowException("Economía", unvmCareer);

        ArgumentCaptor<Subject> captor = ArgumentCaptor.forClass(Subject.class);
        verify(subjectRepository).save(captor.capture());
        assertThat(captor.getValue().getInstitution().getInstitutionId()).isEqualTo(UNVM_ID);
    }

    @Test
    void searchForStudent_returnsSubjectsEvenWhenNoTutorTeachesThem() {
        Institution utn = institution(UTN_ID, "UTN FRVM");
        Career utnCareer = career(utn, "Ingeniería en Sistemas");
        Subject algebra = subject(utn, utnCareer, "Álgebra");
        studentBelongsTo(UTN_ID);
        when(subjectRepository.searchByInstitutionAndNameContaining(UTN_ID, "alg"))
                .thenReturn(List.of(algebra));

        assertThat(subjectService.searchForStudent(STUDENT_ID, "alg"))
                .containsExactly(new SubjectSummary("Álgebra", "Ingeniería en Sistemas"));
    }

    @Test
    void searchForStudent_scopesTheSearchToTheInstitutionOfTheStudent() {
        studentBelongsTo(UNVM_ID);
        when(subjectRepository.searchByInstitutionAndNameContaining(UNVM_ID, "prog"))
                .thenReturn(List.of());

        assertThat(subjectService.searchForStudent(STUDENT_ID, "prog")).isEmpty();

        verify(subjectRepository).searchByInstitutionAndNameContaining(UNVM_ID, "prog");
        verify(subjectRepository, never()).searchByInstitutionAndNameContaining(UTN_ID, "prog");
    }

    @Test
    void searchForStudent_ignoresCaseAndAccentsOfTheQuery() {
        Institution utn = institution(UTN_ID, "UTN FRVM");
        Career utnCareer = career(utn, "Ingeniería en Sistemas");
        Subject algebra = subject(utn, utnCareer, "Álgebra");
        studentBelongsTo(UTN_ID);
        when(subjectRepository.searchByInstitutionAndNameContaining(UTN_ID, "ALGEBRA"))
                .thenReturn(List.of(algebra));

        assertThat(subjectService.searchForStudent(STUDENT_ID, "ALGEBRA"))
                .extracting(SubjectSummary::name)
                .containsExactly("Álgebra");
    }

    @Test
    void searchForStudent_dropsSubjectsThatDoNotContainTheQuery() {
        Institution utn = institution(UTN_ID, "UTN FRVM");
        Career utnCareer = career(utn, "Ingeniería en Sistemas");
        studentBelongsTo(UTN_ID);
        when(subjectRepository.searchByInstitutionAndNameContaining(UTN_ID, "alg"))
                .thenReturn(List.of(
                        subject(utn, utnCareer, "Álgebra"),
                        subject(utn, utnCareer, "Física")));

        assertThat(subjectService.searchForStudent(STUDENT_ID, "alg"))
                .extracting(SubjectSummary::name)
                .containsExactly("Álgebra");
    }

    @Test
    void searchForStudent_ordersWithPrefixPriorityThenAlphabetically() {
        Institution utn = institution(UTN_ID, "UTN FRVM");
        Career utnCareer = career(utn, "Ingeniería en Sistemas");
        studentBelongsTo(UTN_ID);
        when(subjectRepository.searchByInstitutionAndNameContaining(UTN_ID, "a"))
                .thenReturn(List.of(
                        subject(utn, utnCareer, "Base de Datos"),
                        subject(utn, utnCareer, "Álgebra"),
                        subject(utn, utnCareer, "Análisis"),
                        subject(utn, utnCareer, "Cálculo")));

        assertThat(subjectService.searchForStudent(STUDENT_ID, "a"))
                .extracting(SubjectSummary::name)
                .containsExactly("Álgebra", "Análisis", "Base de Datos", "Cálculo");
    }

    @Test
    void searchForStudent_mapsAMissingCareerToAnEmptyName() {
        Institution utn = institution(UTN_ID, "UTN FRVM");
        Subject orphanCareer = Subject.builder()
                .subjectId(UUID.randomUUID())
                .name("Álgebra")
                .institution(utn)
                .career(null)
                .build();
        studentBelongsTo(UTN_ID);
        when(subjectRepository.searchByInstitutionAndNameContaining(UTN_ID, "alg"))
                .thenReturn(List.of(orphanCareer));

        assertThat(subjectService.searchForStudent(STUDENT_ID, "alg"))
                .containsExactly(new SubjectSummary("Álgebra", ""));
    }

    @Test
    void searchForStudent_withoutStudentProfile_throwsNotFound() {
        when(userProfileLookupService.findInstitutionIdByUserId(STUDENT_ID))
                .thenReturn(Optional.empty());

        Throwable thrown = catchThrowable(() -> subjectService.searchForStudent(STUDENT_ID, "alg"));

        assertThat(thrown).isNotNull().isInstanceOf(ResourceNotFoundException.class);
        ResourceNotFoundException notFound = (ResourceNotFoundException) thrown;
        assertThat(notFound.getErrorCode()).isEqualTo("STUDENT_PROFILE_NOT_FOUND");
        assertThat(notFound.getUserMessage()).isEqualTo("No encontramos tu perfil de alumno.");
        verify(subjectRepository, never()).searchByInstitutionAndNameContaining(any(), any());
    }

    @Test
    void searchForStudent_withoutStudentProfile_doesNotSwallowTheCause() {
        when(userProfileLookupService.findInstitutionIdByUserId(STUDENT_ID))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> subjectService.searchForStudent(STUDENT_ID, "alg"))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Student profile not found");
    }
}