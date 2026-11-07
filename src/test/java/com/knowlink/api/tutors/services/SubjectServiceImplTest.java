package com.knowlink.api.tutors.services;

import com.knowlink.api.tutors.data.models.Career;
import com.knowlink.api.tutors.data.models.Institution;
import com.knowlink.api.tutors.data.models.Subject;
import com.knowlink.api.tutors.repositories.ISubjectRepository;
import com.knowlink.api.tutors.services.implementations.SubjectServiceImpl;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SubjectServiceImplTest {

    @Mock
    private ISubjectRepository subjectRepository;

    @InjectMocks
    private SubjectServiceImpl subjectService;

    private static final UUID UTN_ID = UUID.randomUUID();
    private static final UUID UNVM_ID = UUID.randomUUID();

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
}
