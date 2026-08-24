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

    public static TutorSearchResponse from(List<TutorSubject> tutorSubjectList, int totalReviews) {

        if (tutorSubjectList == null || tutorSubjectList.isEmpty()) {
            throw new IllegalArgumentException("La lista de materias del tutor no puede estar vacía.");
        }

        TutorProfile tutorProfile = tutorSubjectList.get(0).getTutorProfile();

        return new TutorSearchResponse(
                tutorProfile.getUser().getUserId(),
                tutorProfile.getUser().getFullName(),
                tutorProfile.getProfilePictureUrl(),
                tutorProfile.getAverageRating(),
                totalReviews,
                tutorSubjectList.stream()
                        .map(mt -> new SubjectSummary(
                                mt.getSubject().getName(),
                                mt.getSubject().getCareer().getName()))
                        .distinct()
                        .sorted(Comparator.comparing(SubjectSummary::name))
                        .toList()
        );
    }
}