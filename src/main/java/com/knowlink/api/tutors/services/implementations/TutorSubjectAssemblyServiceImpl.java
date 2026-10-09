package com.knowlink.api.tutors.services.implementations;

import com.knowlink.api.auth.controllers.requests.TutorSubjectRequest;
import com.knowlink.api.catalog.services.interfaces.ICatalogSubjectService;
import com.knowlink.api.tutors.data.mappers.TutorSubjectMapper;
import com.knowlink.api.tutors.data.models.Career;
import com.knowlink.api.tutors.data.models.Subject;
import com.knowlink.api.tutors.data.models.TutorProfile;
import com.knowlink.api.tutors.data.models.TutorSubject;
import com.knowlink.api.tutors.services.interfaces.ITutorSubjectAssemblyService;
import com.knowlink.api.tutors.validations.ITutorProfileValidationService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class TutorSubjectAssemblyServiceImpl implements ITutorSubjectAssemblyService {

    private final ICatalogSubjectService catalogSubjectService;
    private final TutorSubjectMapper tutorSubjectMapper;
    private final ITutorProfileValidationService tutorProfileValidationService;

    @Override
    public List<TutorSubject> buildTutorSubjects(TutorProfile tutorProfile, Career career, List<TutorSubjectRequest> subjectRequests) {
        return subjectRequests.stream()
                .map(subjectRequest -> {
                    tutorProfileValidationService.ifPaidSubjectHasInvalidPriceThrowException(subjectRequest);
                    Subject subject = catalogSubjectService.findSubjectTeachableInCareerOrThrow(subjectRequest.subjectId(), career);
                    return tutorSubjectMapper.toEntity(subjectRequest, tutorProfile, subject);
                })
                .toList();
    }
}