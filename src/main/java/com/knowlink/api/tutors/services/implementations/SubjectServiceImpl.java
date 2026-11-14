package com.knowlink.api.tutors.services.implementations;

import com.knowlink.api.exceptions.custom_exceptions.ResourceNotFoundException;
import com.knowlink.api.tutors.data.models.Career;
import com.knowlink.api.tutors.data.models.Subject;
import com.knowlink.api.tutors.data.models.SubjectSummary;
import com.knowlink.api.tutors.repositories.ISubjectRepository;
import com.knowlink.api.tutors.services.interfaces.ISubjectService;
import com.knowlink.api.tutors.utils.SearchRanking;
import com.knowlink.api.users.services.interfaces.IUserProfileLookupService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class SubjectServiceImpl implements ISubjectService {

    private final ISubjectRepository subjectRepository;
    private final IUserProfileLookupService userProfileLookupService;

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

    @Override
    @Transactional(readOnly = true)
    public List<SubjectSummary> searchForStudent(UUID studentUserId, String query) {
        UUID institutionId = userProfileLookupService.findInstitutionIdByUserId(studentUserId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "STUDENT_PROFILE_NOT_FOUND",
                        "No encontramos tu perfil de alumno.",
                        String.format("Student profile not found for user '%s'", studentUserId)));

        Comparator<Subject> ranking = SearchRanking.comparator(query, Subject::getName);

        return subjectRepository.searchByInstitutionAndNameContaining(institutionId, query).stream()
                // The SQL LIKE is case-insensitive but follows the database collation for
                // accents; re-checking the match here keeps "algebra" finding "Algebra"
                // with an accent on every database, and drops rows only matched by the
                // LIKE wildcards.
                .filter(subject -> SearchRanking.rank(subject.getName(), query) != 3)
                .sorted(ranking.thenComparing(Subject::getSubjectId))
                .map(subject -> new SubjectSummary(
                        subject.getName(),
                        subject.getCareer() != null ? subject.getCareer().getName() : ""))
                .toList();
    }
}
