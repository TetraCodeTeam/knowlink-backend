package com.knowlink.api.tutors.services.implementations;

import com.knowlink.api.auth.controllers.requests.TutorRegistrationRequest;
import com.knowlink.api.exceptions.custom_exceptions.ResourceNotFoundException;
import com.knowlink.api.tutors.controllers.responses.*;
import com.knowlink.api.tutors.data.enums.BookingStatus;
import com.knowlink.api.tutors.data.mappers.TutorProfileMapper;
import com.knowlink.api.tutors.data.mappers.TutorSubjectMapper;
import com.knowlink.api.tutors.data.models.*;
import com.knowlink.api.tutors.repositories.*;
import com.knowlink.api.tutors.services.interfaces.ICareerService;
import com.knowlink.api.tutors.services.interfaces.ISubjectService;
import com.knowlink.api.tutors.services.interfaces.ITutorProfileService;
import com.knowlink.api.tutors.validations.ITutorProfileValidationService;
import com.knowlink.api.users.data.models.User;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class TutorProfileServiceImpl implements ITutorProfileService {

        private final ITutorSubjectRepository tutorSubjectRepository;
        private final IRatingRepository ratingRepository;
        private final IAvailabilityBlockRepository availabilityBlockRepository;
        private final IAcademicMaterialRepository academicMaterialRepository;
        private final IBookingRepository bookingRepository;
        private final ITutorProfileRepository tutorProfileRepository;
        private final ICareerService careerService;
        private final ISubjectService subjectService;
        private final ITutorProfileValidationService tutorProfileValidationService;
        private final TutorProfileMapper tutorProfileMapper;
        private final TutorSubjectMapper tutorSubjectMapper;

        @Override
        @Transactional(readOnly = true)
        public TutorProfileResponse getTutorProfile(UUID tutorUserId, UUID studentUserId) {
                TutorProfile tutorProfile = tutorProfileRepository.findByUserId(tutorUserId)
                                .orElseThrow(() -> new ResourceNotFoundException(
                                                "TUTOR_NOT_FOUND",
                                                "Perfil de tutor no encontrado.",
                                                String.format("tutor profile con userId '%s' no existe", tutorUserId)));

                List<TutorSubjectResponse> subjectResponses = tutorSubjectRepository
                                .findByTutorProfileId(tutorProfile.getTutorProfileId())
                                .stream()
                                .map(tutorProfileMapper::toSubjectResponse)
                                .collect(Collectors.toList());

                List<TutorReviewResponse> reviewResponses = ratingRepository
                                .findVisibleByRatedUserId(tutorUserId)
                                .stream()
                                .map(tutorProfileMapper::toReviewResponse)
                                .collect(Collectors.toList());

                List<TutorAvailabilityResponse> availabilityResponses = availabilityBlockRepository
                                .findAvailableByTutorProfileId(tutorProfile.getTutorProfileId())
                                .stream()
                                .map(tutorProfileMapper::toAvailabilityResponse)
                                .collect(Collectors.toList());

                List<TutorMaterialResponse> materialResponses = hasActiveBooking(tutorUserId, studentUserId)
                                ? academicMaterialRepository
                                                .findAvailableByTutorProfileId(tutorProfile.getTutorProfileId())
                                                .stream()
                                                .map(tutorProfileMapper::toMaterialResponse)
                                                .collect(Collectors.toList())
                                : List.of();

                return tutorProfileMapper.toProfileResponse(
                                tutorProfile, subjectResponses, reviewResponses, availabilityResponses,
                                materialResponses);
        }

        private boolean hasActiveBooking(UUID tutorUserId, UUID studentUserId) {
                return bookingRepository.existsActiveBooking(
                                tutorUserId, studentUserId,
                                List.of(BookingStatus.BOOKED, BookingStatus.IN_PROGRESS, BookingStatus.COMPLETED));
        }

        @Override
        public TutorProfile createProfile(User user, TutorRegistrationRequest request) {
                tutorProfileValidationService.ifTutorProfileAlreadyExistsThrowException(user);

                Career career = careerService.findByNameOrThrowException(request.career());

                TutorProfile tutorProfile = tutorProfileMapper.toEntity(user, career, request);

                List<TutorSubject> tutorSubjects = request.subjects().stream()
                                .map(subjectRequest -> {
                                        Subject subject = subjectService.findByNameAndCareerOrThrowException(
                                                        subjectRequest.subjectName(), career);
                                        return tutorSubjectMapper.toEntity(subjectRequest, tutorProfile, subject);
                                })
                                .toList();

                tutorProfile.getSubjects().addAll(tutorSubjects);
                return tutorProfileRepository.save(tutorProfile);
        }
}