package com.knowlink.api.tutors.services.implementations;

import com.knowlink.api.exceptions.custom_exceptions.ResourceNotFoundException;
import com.knowlink.api.tutors.data.models.Career;
import com.knowlink.api.tutors.repositories.ICareerRepository;
import com.knowlink.api.tutors.services.interfaces.ICareerService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class CareerServiceImpl implements ICareerService {

    private final ICareerRepository careerRepository;

    @Override
    public Career findByNameOrThrowException(String name) {
        return careerRepository.findByName(name)
                .orElseThrow(() -> new ResourceNotFoundException(
                                                "CAREER_NOT_FOUND",
                                                "Carrera no encontrada.",
                                                String.format("career con name '%s' no existe", name)));
    }

    @Override
    public List<Career> findAll() {
        return careerRepository.findAll();
    }
}