package com.knowlink.api.catalog.services;

import com.knowlink.api.catalog.data.enums.CareerType;
import com.knowlink.api.catalog.data.models.Institution;
import com.knowlink.api.catalog.repositories.IInstitutionRepository;
import com.knowlink.api.catalog.services.implementations.InstitutionServiceImpl;
import com.knowlink.api.catalog.services.interfaces.ICatalogCareerService;
import com.knowlink.api.exceptions.custom_exceptions.DuplicateResourceException;
import com.knowlink.api.exceptions.custom_exceptions.ResourceNotFoundException;
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
class InstitutionServiceImplTest {

    @Mock
    private IInstitutionRepository institutionRepository;

    @Mock
    private ICareerRepository careerRepository;

    @InjectMocks
    private InstitutionServiceImpl institutionService;

    @Test
    @DisplayName("CP-043.01 - Crear institucion genera automaticamente la carrera reservada COMPARTIDA")
    void createInstitution_createsSharedCareerAutomatically() {
        Institution saved = Institution.builder()
                .institutionId(UUID.randomUUID())
                .name("UTN FRVM")
                .build();
        when(institutionRepository.existsByName("UTN FRVM")).thenReturn(false);
        when(institutionRepository.save(any(Institution.class))).thenReturn(saved);

        Institution result = institutionService.createInstitution("UTN FRVM");

        assertThat(result).isEqualTo(saved);
        ArgumentCaptor<Career> captor = ArgumentCaptor.forClass(Career.class);
        verify(careerRepository).save(captor.capture());
        Career sharedCareer = captor.getValue();
        assertThat(sharedCareer.getName()).isEqualTo(ICatalogCareerService.SHARED_CAREER_NAME);
        assertThat(sharedCareer.getType()).isEqualTo(CareerType.COMPARTIDA);
        assertThat(sharedCareer.getInstitution()).isEqualTo(saved);
    }

    @Test
    @DisplayName("CP-043.02 - Nombre de institucion duplicado se rechaza con DuplicateResourceException")
    void createInstitution_duplicateName_rejected() {
        when(institutionRepository.existsByName("UNVM")).thenReturn(true);

        assertThatThrownBy(() -> institutionService.createInstitution("UNVM"))
                .isInstanceOf(DuplicateResourceException.class);

        verify(careerRepository, never()).save(any(Career.class));
    }

    @Test
    @DisplayName("CP-043.03 - findOrThrow con id inexistente lanza ResourceNotFoundException")
    void findOrThrow_unknownId_throwsNotFound() {
        UUID unknown = UUID.randomUUID();
        when(institutionRepository.findById(unknown)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> institutionService.findOrThrow(unknown))
                .isInstanceOf(ResourceNotFoundException.class);
    }
}