package com.knowlink.api.tutors.data.mappers;

import com.knowlink.api.auth.controllers.requests.TutorRegistrationRequest;
import com.knowlink.api.tutors.controllers.responses.TutorAvailabilityResponse;
import com.knowlink.api.tutors.controllers.responses.TutorMaterialResponse;
import com.knowlink.api.tutors.controllers.responses.TutorProfileResponse;
import com.knowlink.api.tutors.controllers.responses.TutorReviewResponse;
import com.knowlink.api.tutors.controllers.responses.TutorSubjectResponse;
import com.knowlink.api.tutors.data.models.AcademicMaterial;
import com.knowlink.api.tutors.data.models.AvailabilityBlock;
import com.knowlink.api.tutors.data.models.Career;
import com.knowlink.api.tutors.data.models.Rating;
import com.knowlink.api.tutors.data.models.TutorProfile;
import com.knowlink.api.tutors.data.models.TutorSubject;
import com.knowlink.api.users.data.models.User;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class TutorProfileMapper {

    public TutorProfile toEntity(User user, Career career, TutorRegistrationRequest request) {
        return TutorProfile.builder()
                .user(user)
                .career(career)
                .biography(request.biography())
                .profilePictureUrl(request.profilePictureUrl())
                .institutionalId(request.institutionalId())
                .address(request.address())
                .mercadoPagoLinked(false)
                .build();
    }

    public TutorSubjectResponse toSubjectResponse(TutorSubject tutorSubject) {
        return new TutorSubjectResponse(
                tutorSubject.getSubject().getName(),
                tutorSubject.getDescription(),
                tutorSubject.getModality().name(),
                tutorSubject.getCompensationType().name(),
                tutorSubject.getPricePerHour() != null ? tutorSubject.getPricePerHour().doubleValue() : null
        );
    }

    public TutorReviewResponse toReviewResponse(Rating rating) {
        return new TutorReviewResponse(rating.getScore(), rating.getComment(), rating.getRatingDate());
    }

    public TutorAvailabilityResponse toAvailabilityResponse(AvailabilityBlock availabilityBlock) {
        return new TutorAvailabilityResponse(
                availabilityBlock.getDayOfWeek().name(),
                availabilityBlock.getStartTime(),
                availabilityBlock.getEndTime()
        );
    }

    public TutorMaterialResponse toMaterialResponse(AcademicMaterial academicMaterial) {
        return new TutorMaterialResponse(
                academicMaterial.getName(),
                academicMaterial.getFileUrl(),
                academicMaterial.getUploadedAt()
        );
    }

    public TutorProfileResponse toProfileResponse(
            TutorProfile tutorProfile,
            List<TutorSubjectResponse> subjectResponses,
            List<TutorReviewResponse> reviewResponses,
            List<TutorAvailabilityResponse> availabilityResponses,
            List<TutorMaterialResponse> materialResponses) {
        return new TutorProfileResponse(
                tutorProfile.getUser().getUserId(),
                tutorProfile.getUser().getFullName(),
                tutorProfile.getBiography(),
                tutorProfile.getCareer().getName(),
                tutorProfile.getProfilePictureUrl(),
                tutorProfile.isVerified(),
                tutorProfile.getAverageRating(),
                subjectResponses,
                reviewResponses,
                availabilityResponses,
                materialResponses
        );
    }
}