package com.knowlink.api.tutors.services.implementations;

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
    public List<Career> findAll() {
        return careerRepository.findAll();
    }
}