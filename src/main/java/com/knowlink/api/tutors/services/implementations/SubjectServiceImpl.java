package com.knowlink.api.tutors.services.implementations;

import com.knowlink.api.tutors.data.models.Career;
import com.knowlink.api.tutors.data.models.Subject;
import com.knowlink.api.tutors.repositories.ISubjectRepository;
import com.knowlink.api.tutors.services.interfaces.ISubjectService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class SubjectServiceImpl implements ISubjectService {

    private final ISubjectRepository subjectRepository;

    @Override
    @Transactional
    public Subject findByNameAndCareerOrThrowException(String name, Career career) {
        UUID institutionId = career.getInstitution().getInstitutionId();
        return subjectRepository.findByInstitution_InstitutionIdAndName(institutionId, name)
                .orElseGet(() -> subjectRepository.save(
                        Subject.builder()
                                .name(name)
                                .career(career)
                                .institution(career.getInstitution())
                                .build()));
    }

    @Override
    @Transactional(readOnly = true)
    public List<Subject> findSharedByInstitutionId(UUID institutionId) {
        return subjectRepository.findSharedByInstitutionId(institutionId);
    }

    @Override
    @Transactional(readOnly = true)
    public List<Subject> findByCareerId(UUID careerId) {
        return subjectRepository.findByCareer_CareerIdOrderByName(careerId);
    }

    @Override
    @Transactional(readOnly = true)
    public List<Subject> findByInstitutionId(UUID institutionId) {
        return subjectRepository.findByInstitution_InstitutionIdOrderByName(institutionId);
    }
}
