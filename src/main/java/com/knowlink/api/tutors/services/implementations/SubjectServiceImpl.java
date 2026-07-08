package com.knowlink.api.tutors.services.implementations;

import com.knowlink.api.tutors.data.models.Career;
import com.knowlink.api.tutors.data.models.Subject;
import com.knowlink.api.tutors.repositories.ISubjectRepository;
import com.knowlink.api.tutors.services.interfaces.ISubjectService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class SubjectServiceImpl implements ISubjectService {

    private final ISubjectRepository subjectRepository;

    @Override
    public Subject findByNameAndCareerOrThrowException(String name, Career career) {
        return subjectRepository.findByName(name)
                .orElseGet(() -> subjectRepository.save(
                        Subject.builder()
                                .name(name)
                                .isBasic(false)
                                .career(career)
                                .build()));
    }

    @Override
    public List<Subject> findBasicSubjects() {
        return subjectRepository.findByIsBasicTrue();
    }

    @Override
    public List<Subject> findByCareerId(UUID careerId) {
        return subjectRepository.findByCareer_CareerIdAndIsBasicFalse(careerId);
    }
}