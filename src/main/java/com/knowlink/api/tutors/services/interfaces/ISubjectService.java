package com.knowlink.api.tutors.services.interfaces;

import com.knowlink.api.tutors.data.models.Career;
import com.knowlink.api.tutors.data.models.Subject;
import com.knowlink.api.tutors.data.models.SubjectSummary;

import java.util.List;
import java.util.UUID;

public interface ISubjectService {

    Subject findByNameAndCareerOrThrowException(String name, Career career);

    List<Subject> findSharedByInstitutionId(UUID institutionId);

    List<Subject> findByCareerId(UUID careerId);

    List<Subject> findByInstitutionId(UUID institutionId);

    /**
     * Catalog subjects of the student's institution whose name contains the
     * query, whether or not a tutor teaches them (US-06). The scope is the
     * institution of the student and the results follow the shared search
     * ranking.
     *
     * @throws com.knowlink.api.exceptions.custom_exceptions.ResourceNotFoundException
     *         when the user has no student profile to resolve the institution.
     */
    List<SubjectSummary> searchForStudent(UUID studentUserId, String query);
}
