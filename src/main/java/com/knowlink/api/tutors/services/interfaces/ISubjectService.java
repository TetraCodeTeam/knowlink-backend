package com.knowlink.api.tutors.services.interfaces;

import com.knowlink.api.tutors.data.models.Subject;

import java.util.List;
import java.util.UUID;

public interface ISubjectService {
    List<Subject> findBasicSubjects();
    List<Subject> findByCareerId(UUID careerId);
}