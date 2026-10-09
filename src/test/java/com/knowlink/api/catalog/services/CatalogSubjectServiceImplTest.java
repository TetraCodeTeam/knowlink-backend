package com.knowlink.api.catalog.services;

import com.knowlink.api.catalog.data.enums.AssociationMode;
import com.knowlink.api.catalog.data.enums.CareerType;
import com.knowlink.api.catalog.data.models.Institution;
import com.knowlink.api.catalog.repositories.IInstitutionRepository;
import com.knowlink.api.catalog.services.implementations.CatalogSubjectServiceImpl;
import com.knowlink.api.exceptions.custom_exceptions.DuplicatedSubjectException;
import com.knowlink.api.exceptions.custom_exceptions.ValidationException;
import com.knowlink.api.tutors.data.models.Career;
import com.knowlink.api.tutors.data.models.Subject;
import com.knowlink.api.tutors.repositories.ICareerRepository;
import com.knowlink.api.tutors.repositories.ISubjectRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CatalogSubjectServiceImplTest {

    @Mock
    private ISubjectRepository subjectRepository;

    @Mock
    private ICareerRepository careerRepository;

    @Mock
    private IInstitutionRepository institutionRepository;

    @InjectMocks
    private CatalogSubjectServiceImpl catalogSubjectService;

    private final UUID institutionId = UUID.randomUUID();

    private Institution institution() {
        return Institution.builder().institutionId(institutionId).name("UTN FRVM").build();
    }

    private Career career(String name, CareerType type, Institution inst) {
        return Career.builder()
                .careerId(UUID.randomUUID())
                .name(name)
                .institution(inst)
                .type(type)
                .build();
    }

    @Test
    @DisplayName("CP-043.08 - Materia duplicada en la institucion devuelve 409 con el id de la materia existente")
    void createSubject_duplicateInInstitution_throwsWithExistingId() {
        UUID existingId = UUID.randomUUID();
        Career sistemas = career("Ingenieria en Sistemas", CareerType.REGULAR, institution());
        Subject existing = Subject.builder().subjectId(existingId).name("Analisis Matematico I").build();
        when(institutionRepository.findById(institutionId)).thenReturn(Optional.of(institution()));
        when(careerRepository.findAllById(List.of(sistemas.getCareerId()))).thenReturn(List.of(sistemas));
        when(subjectRepository.existsByInstitutionInstitutionIdAndName(institutionId, "Analisis Matematico I"))
                .thenReturn(true);
        when(subjectRepository.findByNameAndInstitutionInstitutionId("Analisis Matematico I", institutionId))
                .thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> catalogSubjectService.createSubject(
                institutionId, "Analisis Matematico I", List.of(sistemas.getCareerId())))
                .isInstanceOf(DuplicatedSubjectException.class)
                .satisfies(ex -> {
                    DuplicatedSubjectException duplicated = (DuplicatedSubjectException) ex;
                    assertThat(duplicated.getErrorCode()).isEqualTo("MATERIA_DUPLICADA");
                    assertThat(duplicated.getUserMessage()).isEqualTo(
                            "Ya existe una materia con ese nombre en esta institución. ¿Querés agregarle esta carrera?");
                    assertThat(duplicated.getExistingSubjectId()).isEqualTo(existingId);
                });

        verify(subjectRepository, never()).save(any(Subject.class));
    }

    @Test
    @DisplayName("CP-043.09 - No se pueden asociar materias a carreras de otra institucion")
    void createSubject_careerFromAnotherInstitution_rejected() {
        Institution unvm = Institution.builder().institutionId(UUID.randomUUID()).name("UNVM").build();
        Career foreignCareer = career("Profesorado de Matematica", CareerType.REGULAR, unvm);
        when(institutionRepository.findById(institutionId)).thenReturn(Optional.of(institution()));
        when(careerRepository.findAllById(List.of(foreignCareer.getCareerId())))
                .thenReturn(List.of(foreignCareer));

        assertThatThrownBy(() -> catalogSubjectService.createSubject(
                institutionId, "Materia Nueva", List.of(foreignCareer.getCareerId())))
                .isInstanceOf(ValidationException.class)
                .hasMessage("Solo podés asociar materias a carreras de la misma institución.");

        verify(subjectRepository, never()).save(any(Subject.class));
    }

    @Test
    @DisplayName("CP-043.10 - ADD agrega carreras sin quitar las existentes y no modifica el nombre")
    void updateSubjectCareers_addKeepsExistingCareersAndName() {
        Institution inst = institution();
        Career sistemas = career("Ingenieria en Sistemas", CareerType.REGULAR, inst);
        Career shared = career("Materias Compartidas", CareerType.COMPARTIDA, inst);
        Subject subject = Subject.builder()
                .subjectId(UUID.randomUUID())
                .name("Analisis Matematico I")
                .isBasic(false)
                .institution(inst)
                .careers(new LinkedHashSet<>(Set.of(sistemas)))
                .build();
        when(subjectRepository.findById(subject.getSubjectId())).thenReturn(Optional.of(subject));
        when(careerRepository.findAllById(List.of(shared.getCareerId()))).thenReturn(List.of(shared));
        when(subjectRepository.save(any(Subject.class))).thenAnswer(invocation -> invocation.getArgument(0));

        var response = catalogSubjectService.updateSubjectCareers(
                subject.getSubjectId(), List.of(shared.getCareerId()), AssociationMode.ADD);

        assertThat(response.name()).isEqualTo("Analisis Matematico I");
        assertThat(response.careerIds())
                .containsExactlyInAnyOrder(sistemas.getCareerId(), shared.getCareerId());
    }

    @Test
    @DisplayName("CP-043.11 - REPLACE reemplaza todas las asociaciones de carrera")
    void updateSubjectCareers_replaceSetsOnlyInformedCareers() {
        Institution inst = institution();
        Career sistemas = career("Ingenieria en Sistemas", CareerType.REGULAR, inst);
        Career shared = career("Materias Compartidas", CareerType.COMPARTIDA, inst);
        Subject subject = Subject.builder()
                .subjectId(UUID.randomUUID())
                .name("Analisis Matematico I")
                .isBasic(false)
                .institution(inst)
                .careers(new LinkedHashSet<>(Set.of(sistemas)))
                .build();
        when(subjectRepository.findById(subject.getSubjectId())).thenReturn(Optional.of(subject));
        when(careerRepository.findAllById(List.of(shared.getCareerId()))).thenReturn(List.of(shared));
        when(subjectRepository.save(any(Subject.class))).thenAnswer(invocation -> invocation.getArgument(0));

        var response = catalogSubjectService.updateSubjectCareers(
                subject.getSubjectId(), List.of(shared.getCareerId()), AssociationMode.REPLACE);

        assertThat(response.careerIds()).containsExactly(shared.getCareerId());
    }

    @Test
    @DisplayName("CP-043.12 - Una materia asociada a la carrera reservada es ensenanza valida de la carrera")
    void findSubjectTeachable_sharedCareerAssociation_allowed() {
        Institution inst = institution();
        Career sistemas = career("Ingenieria en Sistemas", CareerType.REGULAR, inst);
        Career shared = career("Materias Compartidas", CareerType.COMPARTIDA, inst);
        Subject subject = Subject.builder()
                .subjectId(UUID.randomUUID())
                .name("Analisis Matematico I")
                .isBasic(true)
                .institution(inst)
                .careers(new LinkedHashSet<>(Set.of(shared)))
                .build();
        when(subjectRepository.findById(subject.getSubjectId())).thenReturn(Optional.of(subject));

        Subject result = catalogSubjectService.findSubjectTeachableInCareerOrThrow(
                subject.getSubjectId(), sistemas);

        assertThat(result).isEqualTo(subject);
    }

    @Test
    @DisplayName("CP-043.13 - Una materia de otra carrera no asociada ni compartida se rechaza")
    void findSubjectTeachable_unrelatedCareer_rejected() {
        Institution inst = institution();
        Career sistemas = career("Ingenieria en Sistemas", CareerType.REGULAR, inst);
        Career civil = career("Ingenieria Civil", CareerType.REGULAR, inst);
        Subject subject = Subject.builder()
                .subjectId(UUID.randomUUID())
                .name("Programacion")
                .isBasic(false)
                .institution(inst)
                .careers(new LinkedHashSet<>(Set.of(sistemas)))
                .build();
        when(subjectRepository.findById(subject.getSubjectId())).thenReturn(Optional.of(subject));

        assertThatThrownBy(() -> catalogSubjectService.findSubjectTeachableInCareerOrThrow(
                subject.getSubjectId(), civil))
                .isInstanceOf(ValidationException.class)
                .hasMessage(
                        "La materia seleccionada no está asociada a esta carrera ni a las materias compartidas de la institución.");
    }
}