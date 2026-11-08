package com.knowlink.api.tutors.services.implementations;

import com.knowlink.api.exceptions.custom_exceptions.ResourceNotFoundException;
import com.knowlink.api.tutors.data.models.Institution;
import com.knowlink.api.tutors.repositories.IInstitutionRepository;
import com.knowlink.api.tutors.services.interfaces.IInstitutionService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class InstitutionServiceImpl implements IInstitutionService {

    private final IInstitutionRepository institutionRepository;

    @Override
    @Transactional(readOnly = true)
    public List<Institution> findAll() {
        return institutionRepository.findAllByOrderByNameAsc();
    }

    @Override
    @Transactional(readOnly = true)
    public Institution findByIdOrThrowException(UUID institutionId) {
        return institutionRepository.findById(institutionId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "INSTITUTION_NOT_FOUND",
                        "Institución no encontrada.",
                        String.format("institution con id '%s' no existe", institutionId)));
    }
}