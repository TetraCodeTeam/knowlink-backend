package com.knowlink.api.catalog.services;

import com.knowlink.api.catalog.controllers.requests.CreateCareerRequest;
import com.knowlink.api.catalog.controllers.responses.CatalogCareerResponse;
import com.knowlink.api.catalog.data.enums.CareerType;
import com.knowlink.api.catalog.data.models.Institution;
import com.knowlink.api.catalog.repositories.IInstitutionRepository;
import com.knowlink.api.catalog.services.implementations.CatalogCareerServiceImpl;
import com.knowlink.api.exceptions.custom_exceptions.DuplicateResourceException;
import com.knowlink.api.exceptions.custom_exceptions.ValidationException;
import com.knowlink.api.tutors.data.models.Career;
import com.knowlink.api.tutors.repositories.ICareerRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CatalogCareerServiceImplTest {

    @Mock
    private ICareerRepository careerRepository;

    @Mock
    private IInstitutionRepository institutionRepository;

    @InjectMocks
    private CatalogCareerServiceImpl catalogCareerService;

    private Institution institution(UUID id) {
        return Institution.builder().institutionId(id).name("UTN FRVM").build();
    }

    @Test
    @DisplayName("CP-043.04 - No se puede crear manualmente una carrera de tipo COMPARTIDA")
    void createCareer_sharedType_rejectedWithExactMessage() {
        UUID institutionId = UUID.randomUUID();
        when(institutionRepository.findById(institutionId)).thenReturn(Optional.of(institution(institutionId)));

        assertThatThrownBy(() -> catalogCareerService.createCareer(
                institutionId, new CreateCareerRequest("Materias Compartidas", CareerType.COMPARTIDA)))
                .isInstanceOf(ValidationException.class)
                .hasMessage("No es posible crear carreras de tipo compartido manualmente");

        verify(careerRepository, never()).save(any(Career.class));
    }

    @Test
    @DisplayName("CP-043.05 - El tipo de carrera creado se fuerza a REGULAR server-side")
    void createCareer_forcesRegularType() {
        UUID institutionId = UUID.randomUUID();
        Institution inst = institution(institutionId);
        when(institutionRepository.findById(institutionId)).thenReturn(Optional.of(inst));
        when(careerRepository.existsByInstitutionInstitutionIdAndName(institutionId, "Ingenieria en Sistemas"))
                .thenReturn(false);
        when(careerRepository.save(any(Career.class))).thenAnswer(invocation -> {
            Career career = invocation.getArgument(0);
            career.setCareerId(UUID.randomUUID());
            return career;
        });

        CatalogCareerResponse response = catalogCareerService.createCareer(
                institutionId, new CreateCareerRequest("Ingenieria en Sistemas", null));

        assertThat(response.type()).isEqualTo(CareerType.REGULAR);
        ArgumentCaptor<Career> captor = ArgumentCaptor.forClass(Career.class);
        verify(careerRepository).save(captor.capture());
        assertThat(captor.getValue().getType()).isEqualTo(CareerType.REGULAR);
        assertThat(captor.getValue().getInstitution()).isEqualTo(inst);
    }

    @Test
    @DisplayName("CP-043.06 - Carrera duplicada en la misma institucion se rechaza con 409")
    void createCareer_duplicateName_throwsDuplicateResource() {
        UUID institutionId = UUID.randomUUID();
        when(institutionRepository.findById(institutionId)).thenReturn(Optional.of(institution(institutionId)));
        when(careerRepository.existsByInstitutionInstitutionIdAndName(institutionId, "Contador Publico"))
                .thenReturn(true);

        assertThatThrownBy(() -> catalogCareerService.createCareer(
                institutionId, new CreateCareerRequest("Contador Publico", null)))
                .isInstanceOf(DuplicateResourceException.class);

        verify(careerRepository, never()).save(any(Career.class));
    }

    @Test
    @DisplayName("CP-043.07 - findInInstitutionOrThrow rechaza una carrera de otra institucion")
    void findInInstitutionOrThrow_careerFromAnotherInstitution_rejected() {
        UUID institutionId = UUID.randomUUID();
        UUID otherInstitutionId = UUID.randomUUID();
        Career career = Career.builder()
                .careerId(UUID.randomUUID())
                .name("Otra Carrera")
                .institution(Institution.builder().institutionId(otherInstitutionId).name("UNVM").build())
                .build();
        when(careerRepository.findById(career.getCareerId())).thenReturn(Optional.of(career));

        assertThatThrownBy(() -> catalogCareerService.findInInstitutionOrThrow(institutionId, career.getCareerId()))
                .isInstanceOf(ValidationException.class)
                .hasMessage("La carrera seleccionada no pertenece a la institución indicada.");
    }
}