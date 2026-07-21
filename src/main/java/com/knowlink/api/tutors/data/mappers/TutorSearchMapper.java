package com.knowlink.api.tutors.data.mappers;


import java.util.Comparator;
import java.util.List;

import com.knowlink.api.tutors.data.models.SubjectSummary;
import com.knowlink.api.tutors.data.models.TutorProfile;
import com.knowlink.api.tutors.data.models.TutorSearchResponse;
import com.knowlink.api.tutors.data.models.TutorSubject;

public final class TutorSearchMapper {

    private TutorSearchMapper() {
    }

    public static TutorSearchResponse from(List<TutorSubject> TutorSubjectList) {

        if (TutorSubjectList == null || TutorSubjectList.isEmpty()) {
            throw new IllegalArgumentException("La lista de materias del tutor no puede estar vacía.");
        }

        TutorProfile tutorProfile = TutorSubjectList.get(0).getTutorProfile();

        return new TutorSearchResponse(
                tutorProfile.getUser().getUserId(),
                tutorProfile.getUser().getFullName(),
                tutorProfile.getProfilePictureUrl(),
                tutorProfile.getAverageRating(),
                4, // o getTotalReviews() según tu entidad
                TutorSubjectList.stream()
                        .map(mt -> new SubjectSummary(
                                mt.getSubject().getName(),
                                mt.getSubject().getCareer().getName()))
                        .distinct()
                        .sorted(Comparator.comparing(SubjectSummary::name))
                        .toList()
        );
    }
}