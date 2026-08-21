package com.knowlink.api.tutors.services.implementations;

import com.knowlink.api.security.enums.Role;
import com.knowlink.api.security.services.JwtService;
import com.knowlink.api.students.services.interfaces.IStudentProfileService;
import com.knowlink.api.users.services.interfaces.IUserProfileLookupService;
import com.knowlink.api.users.services.interfaces.IUserService;
import com.knowlink.api.auth.controllers.requests.TutorRegistrationRequest;
import com.knowlink.api.auth.controllers.requests.TutorSubjectRequest;
import com.knowlink.api.tutors.availability.controllers.responses.AvailabilityBlockResponse;
import com.knowlink.api.tutors.availability.repositories.IAvailabilityBlockRepository;
import com.knowlink.api.tutors.controllers.responses.*;
import com.knowlink.api.tutors.data.mappers.TutorProfileMapper;
import com.knowlink.api.tutors.data.mappers.TutorSearchMapper;
import com.knowlink.api.tutors.data.mappers.TutorSubjectMapper;
import com.knowlink.api.tutors.data.models.*;
import com.knowlink.api.tutors.repositories.*;
import com.knowlink.api.tutors.services.interfaces.ICareerService;
import com.knowlink.api.tutors.services.interfaces.ISubjectService;
import com.knowlink.api.tutors.services.interfaces.ITutorProfileService;
import com.knowlink.api.tutors.services.interfaces.ITutorSubjectAssemblyService;
import com.knowlink.api.tutors.validations.ITutorProfileValidationService;
import com.knowlink.api.users.data.models.User;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class TutorProfileServiceImpl implements ITutorProfileService {

        private final ITutorSubjectRepository tutorSubjectRepository;
        private final IRatingRepository ratingRepository;
        private final IAvailabilityBlockRepository availabilityBlockRepository;
        private final ITutorProfileRepository tutorProfileRepository;
        private final ICareerService careerService;
        private final ISubjectService subjectService;
        private final ITutorProfileValidationService tutorProfileValidationService;
        private final TutorProfileMapper tutorProfileMapper;
        private final TutorSubjectMapper tutorSubjectMapper;
        private final ITutorSubjectAssemblyService tutorSubjectAssemblyService;
        private final IStudentProfileService studentProfileService;
        private final IUserService userService;
        private final JwtService jwtService;
        private final IUserProfileLookupService userProfileLookupService;

        @Override
        @Transactional(readOnly = true)
        public TutorProfileResponse getTutorProfile(UUID tutorUserId, UUID studentUserId) {
                TutorProfile tutorProfile = tutorProfileValidationService.findTutorProfileOrThrowException(tutorUserId);

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

                List<AvailabilityBlockResponse> availabilityResponses = availabilityBlockRepository
                                .findAvailableByTutorProfileId(tutorProfile.getTutorProfileId())
                                .stream()
                                .map(tutorProfileMapper::toAvailabilityResponse)
                                .collect(Collectors.toList());

                return tutorProfileMapper.toProfileResponse(
                                tutorProfile, subjectResponses, reviewResponses, availabilityResponses, List.of()); // Provisorio
                                                                                                                    // hasta
                                                                                                                    // implementar
                                                                                                                    // materiales.
        }

        @Override
        @Transactional
        public TutorProfile createProfile(User user, TutorRegistrationRequest request) {
                tutorProfileValidationService.ifTutorProfileAlreadyExistsThrowException(user);

                Career career = careerService.findByNameOrThrowException(request.career());
                TutorProfile tutorProfile = tutorProfileMapper.toEntity(user, career, request);

                tutorProfile.getSubjects().addAll(
                                tutorSubjectAssemblyService.buildTutorSubjects(tutorProfile, career,
                                                request.subjects()));

                return tutorProfileRepository.save(tutorProfile);
        }

        @Override
        @Transactional(readOnly = true)
        public TutorSelfProfileResponse getSelfProfile(UUID tutorUserId) {
                TutorProfile tutorProfile = tutorProfileValidationService.findTutorProfileOrThrowException(tutorUserId);
                boolean hasStudentProfile = userProfileLookupService.hasStudentProfile(tutorUserId);
                return tutorProfileMapper.toSelfProfileResponse(tutorProfile, hasStudentProfile);
        }

        @Override
        @Transactional
        public void updateMinNoticeMinutes(UUID tutorUserId, Integer minNoticeMinutes) {
                TutorProfile tutorProfile = tutorProfileValidationService.findTutorProfileOrThrowException(tutorUserId);
                tutorProfile.setMinNoticeMinutes(minNoticeMinutes);
                tutorProfileRepository.save(tutorProfile);
        }

        @Override
        @Transactional(readOnly = true)
        public Integer getMinNoticeMinutes(UUID tutorUserId) {
                TutorProfile tutorProfile = tutorProfileValidationService.findTutorProfileOrThrowException(tutorUserId);
                return tutorProfile.getMinNoticeMinutes();
        }

        @Override
        @Transactional(readOnly = true)
        public List<TutorSearchResponse> searchTutor(String query, UUID studentUserId) {

                List<TutorSubject> subjectMatches = tutorSubjectRepository
                                .findBySubject_NameContainingIgnoreCase(query);

                Map<UUID, List<TutorSubject>> groupedResults = subjectMatches.stream()
                                .collect(Collectors.groupingBy(
                                                tutorSubject -> tutorSubject.getTutorProfile().getUser().getUserId()));

                List<TutorProfile> nameMatches = tutorProfileRepository
                                .findByUser_FullNameContainingIgnoreCase(query);

                for (TutorProfile tutorProfile : nameMatches) {
                        if (tutorProfile.getSubjects().isEmpty()) {
                                continue;
                        }
                        groupedResults.putIfAbsent(tutorProfile.getUser().getUserId(), tutorProfile.getSubjects());
                }

                return groupedResults.values()
                                .stream()
                                .map(TutorSearchMapper::from)
                                .toList();
        }

        @Override
        @Transactional
        public TutorSubjectResponse createTutorSubject(UUID tutorUserId, TutorSubjectRequest request) {
                TutorProfile tutorProfile = tutorProfileValidationService.findTutorProfileOrThrowException(tutorUserId);

                tutorProfileValidationService.ifPaidSubjectHasInvalidPriceThrowException(request);

                Subject subject = subjectService.findByNameAndCareerOrThrowException(
                                request.subjectName(), tutorProfile.getCareer());

                tutorProfileValidationService.ifTutorAlreadyTeachesSubjectThrowException(tutorProfile, subject);

                TutorSubject tutorSubject = tutorSubjectMapper.toEntity(request, tutorProfile, subject);
                TutorSubject saved = tutorSubjectRepository.save(tutorSubject);

                return tutorProfileMapper.toSubjectResponse(saved);
        }

        @Override
        @Transactional
        public ActivateStudentRoleResponse activateStudentRole(UUID tutorUserId) {
                TutorProfile tutorProfile = tutorProfileValidationService.findTutorProfileOrThrowException(tutorUserId);
                User user = tutorProfile.getUser();

                studentProfileService.createProfileFromTutorData(user, tutorProfile);

                user = userService.updateUserRole(tutorUserId, Role.STUDENT);

                return new ActivateStudentRoleResponse(jwtService.generateToken(user));
        }
}