package com.knowlink.api.tutors.data.mappers;

import com.knowlink.api.auth.controllers.requests.TutorSubjectRequest;
import com.knowlink.api.tutors.data.enums.TutorSubjectStatus;
import com.knowlink.api.tutors.data.models.Subject;
import com.knowlink.api.tutors.data.models.TutorProfile;
import com.knowlink.api.tutors.data.models.TutorSubject;
import org.springframework.stereotype.Component;

@Component
public class TutorSubjectMapper {

    public TutorSubject toEntity(TutorSubjectRequest request, TutorProfile tutorProfile, Subject subject) {
        return TutorSubject.builder()
                .tutorProfile(tutorProfile)
                .subject(subject)
                .modality(request.modality())
                .compensationType(request.compensationType())
                .pricePerHour(request.pricePerHour())
                .tutorSubjectStatus(TutorSubjectStatus.PENDING)
                .build();
    }
}