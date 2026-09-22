package com.knowlink.api.tutors.services.interfaces;

import com.knowlink.api.tutors.data.models.Career;
import com.knowlink.api.tutors.data.models.Subject;

import java.util.List;
import java.util.UUID;

public interface ISubjectService {
    Subject findByNameAndCareerOrThrowException(String name, Career career);
    List<Subject> findBasicSubjects();
    List<Subject> findByCareerId(UUID careerId);
}