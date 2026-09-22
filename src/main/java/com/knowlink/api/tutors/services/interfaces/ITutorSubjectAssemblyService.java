package com.knowlink.api.tutors.services.interfaces;

import com.knowlink.api.auth.controllers.requests.TutorSubjectRequest;
import com.knowlink.api.tutors.data.models.Career;
import com.knowlink.api.tutors.data.models.TutorProfile;
import com.knowlink.api.tutors.data.models.TutorSubject;

import java.util.List;

public interface ITutorSubjectAssemblyService {

    List<TutorSubject> buildTutorSubjects(TutorProfile tutorProfile, Career career, List<TutorSubjectRequest> subjectRequests);
}
