package com.knowlink.api.tutors.data.mappers;

import com.knowlink.api.auth.controllers.requests.TutorRegistrationRequest;
import com.knowlink.api.tutors.availability.controllers.responses.AvailabilityBlockResponse;
import com.knowlink.api.tutors.availability.data.models.AvailabilityBlock;
import com.knowlink.api.tutors.controllers.responses.TutorMaterialResponse;
import com.knowlink.api.tutors.controllers.responses.TutorProfileResponse;
import com.knowlink.api.tutors.controllers.responses.TutorReviewResponse;
import com.knowlink.api.tutors.controllers.responses.TutorSelfProfileResponse;
import com.knowlink.api.tutors.controllers.responses.TutorSubjectResponse;
import com.knowlink.api.tutors.data.models.AcademicMaterial;
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
                tutorSubject.getTutorSubjectId(),
                tutorSubject.getSubject().getName(),
                tutorSubject.getModality().name(),
                tutorSubject.getCompensationType().name(),
                tutorSubject.getPricePerHour(),
                tutorSubject.getTutorSubjectStatus().name(),
                null, // averageRating por materia - pendiente
                null // reviewCount por materia - pendiente
        );
    }

    public TutorReviewResponse toReviewResponse(Rating rating) {
        return new TutorReviewResponse(rating.getScore(), rating.getComment(), rating.getRatingDate());
    }

    public AvailabilityBlockResponse toAvailabilityResponse(AvailabilityBlock availabilityBlock) {
        return new AvailabilityBlockResponse(
                availabilityBlock.getAvailabilityBlockId(),
                availabilityBlock.getDate(),
                availabilityBlock.getStartTime(),
                availabilityBlock.getEndTime(),
                availabilityBlock.isRepeatWeekly());
    }

    public TutorMaterialResponse toMaterialResponse(AcademicMaterial academicMaterial) {
        return new TutorMaterialResponse(
                academicMaterial.getName(),
                academicMaterial.getFileUrl(),
                academicMaterial.getUploadedAt());
    }

    public TutorProfileResponse toProfileResponse(
            TutorProfile tutorProfile,
            List<TutorSubjectResponse> subjectResponses,
            List<TutorReviewResponse> reviewResponses,
            List<AvailabilityBlockResponse> availabilityResponses,
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
                materialResponses);
    }

    public TutorSelfProfileResponse toSelfProfileResponse(TutorProfile tutorProfile) {
        List<TutorSubjectResponse> subjects = tutorProfile.getSubjects().stream()
                .map(this::toSubjectResponse)
                .toList();

        return new TutorSelfProfileResponse(
                tutorProfile.getUser().getUserId(),
                tutorProfile.getUser().getFullName(),
                tutorProfile.getUser().getEmail(),
                tutorProfile.getUser().getPhoneNumber(),
                tutorProfile.getCareer().getName(),
                tutorProfile.getProfilePictureUrl(),
                tutorProfile.getBiography(),
                tutorProfile.getAddress(),
                tutorProfile.isMercadoPagoLinked(),
                tutorProfile.getAverageRating(),
                subjects);
    }
}