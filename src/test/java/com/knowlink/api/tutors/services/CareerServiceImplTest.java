package com.knowlink.api.tutors.services;

import com.knowlink.api.exceptions.custom_exceptions.ResourceNotFoundException;
import com.knowlink.api.tutors.data.enums.CareerType;
import com.knowlink.api.tutors.data.models.Career;
import com.knowlink.api.tutors.data.models.Institution;
import com.knowlink.api.tutors.repositories.ICareerRepository;
import com.knowlink.api.tutors.services.implementations.CareerServiceImpl;
import com.knowlink.api.tutors.services.interfaces.IInstitutionService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CareerServiceImplTest {

    @Mock
    private ICareerRepository careerRepository;

    @Mock
    private IInstitutionService institutionService;

    @InjectMocks
    private CareerServiceImpl careerService;

    private static final UUID UTN_ID = UUID.randomUUID();
    private static final UUID UNVM_ID = UUID.randomUUID();

    private static Institution institution(UUID id, String name) {
        return Institution.builder().institutionId(id).name(name).build();
    }

    private static Career career(Institution institution, String name, CareerType type) {
        return Career.builder().careerId(UUID.randomUUID()).name(name)
                .institution(institution).type(type).build();
    }

    @Test
    void findRegularByName_rejectsSharedCareer() {
        Institution utn = institution(UTN_ID, "UTN FRVM");
        when(careerRepository.findByInstitution_InstitutionIdAndName(UTN_ID, "Materias Compartidas"))
                .thenReturn(Optional.of(career(utn, "Materias Compartidas", CareerType.SHARED)));

        assertThatThrownBy(() -> careerService.findRegularByNameAndInstitutionOrThrowException(
                "Materias Compartidas", UTN_ID))
                .isInstanceOfSatisfying(ResourceNotFoundException.class,
                        ex -> assertThat(ex.getErrorCode()).isEqualTo("CAREER_NOT_FOUND"));
    }

    @Test
    void findRegularByName_rejectsCareerFromAnotherInstitution() {
        when(careerRepository.findByInstitution_InstitutionIdAndName(UNVM_ID, "Ingeniería Civil"))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> careerService.findRegularByNameAndInstitutionOrThrowException(
                "Ingeniería Civil", UNVM_ID))
                .isInstanceOfSatisfying(ResourceNotFoundException.class,
                        ex -> assertThat(ex.getErrorCode()).isEqualTo("CAREER_NOT_FOUND"));
    }

    @Test
    void findRegularByName_returnsRegularCareerOfTheInstitution() {
        Institution utn = institution(UTN_ID, "UTN FRVM");
        Career regular = career(utn, "Ingeniería en Sistemas", CareerType.REGULAR);
        when(careerRepository.findByInstitution_InstitutionIdAndName(UTN_ID, "Ingeniería en Sistemas"))
                .thenReturn(Optional.of(regular));

        assertThat(careerService.findRegularByNameAndInstitutionOrThrowException(
                "Ingeniería en Sistemas", UTN_ID)).isSameAs(regular);
    }

    @Test
    void findOrCreateSharedCareer_returnsExistingWithoutSaving() {
        Institution utn = institution(UTN_ID, "UTN FRVM");
        Career shared = career(utn, "Materias Compartidas", CareerType.SHARED);
        when(careerRepository.findByInstitution_InstitutionIdAndType(UTN_ID, CareerType.SHARED))
                .thenReturn(Optional.of(shared));

        assertThat(careerService.findOrCreateSharedCareer(utn)).isSameAs(shared);
        verify(careerRepository, never()).save(any());
    }

    @Test
    void findOrCreateSharedCareer_createsTheSharedCareerOnce() {
        Institution utn = institution(UTN_ID, "UTN FRVM");
        when(careerRepository.findByInstitution_InstitutionIdAndType(UTN_ID, CareerType.SHARED))
                .thenReturn(Optional.empty());
        when(careerRepository.save(any(Career.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Career created = careerService.findOrCreateSharedCareer(utn);

        assertThat(created.getName()).isEqualTo("Materias Compartidas");
        assertThat(created.getType()).isEqualTo(CareerType.SHARED);
        assertThat(created.getInstitution()).isSameAs(utn);
    }

    @Test
    void findRegularByInstitution_validatesTheInstitutionAndOnlyReturnsRegularCareers() {
        Institution utn = institution(UTN_ID, "UTN FRVM");
        Career regular = career(utn, "Ingeniería en Sistemas", CareerType.REGULAR);
        when(institutionService.findByIdOrThrowException(UTN_ID)).thenReturn(utn);
        when(careerRepository.findByInstitution_InstitutionIdAndTypeOrderByName(UTN_ID, CareerType.REGULAR))
                .thenReturn(List.of(regular));

        assertThat(careerService.findRegularByInstitution(UTN_ID)).containsExactly(regular);
    }
}
