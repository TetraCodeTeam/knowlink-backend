package com.knowlink.api.tutors.services.implementations;

import com.knowlink.api.security.enums.Role;
import com.knowlink.api.security.services.JwtService;
import com.knowlink.api.students.services.interfaces.IStudentProfileService;
import com.knowlink.api.users.services.interfaces.IUserProfileLookupService;
import com.knowlink.api.users.repositories.IUserRepository;
import com.knowlink.api.users.services.interfaces.IUserService;
import com.knowlink.api.auth.controllers.requests.TutorRegistrationRequest;
import com.knowlink.api.auth.controllers.requests.TutorSubjectRequest;
import com.knowlink.api.tutors.controllers.requests.UpdateTutorProfileRequest;
import com.knowlink.api.tutors.controllers.requests.UpdateTutorSubjectRequest;
import com.knowlink.api.tutors.data.enums.CompensationType;
import com.knowlink.api.exceptions.custom_exceptions.ResourceNotFoundException;
import com.knowlink.api.exceptions.custom_exceptions.ValidationException;
import com.knowlink.api.materials.service.interfaces.IProfileImageService;
import com.knowlink.api.tutors.availability.controllers.responses.AvailabilityBlockResponse;
import com.knowlink.api.tutors.availability.repositories.IAvailabilityBlockRepository;
import com.knowlink.api.tutors.controllers.responses.*;
import com.knowlink.api.tutors.data.mappers.TutorProfileMapper;
import com.knowlink.api.tutors.data.mappers.TutorSearchMapper;
import com.knowlink.api.tutors.data.mappers.TutorSubjectMapper;
import com.knowlink.api.tutors.data.models.*;
import com.knowlink.api.tutors.data.specifications.TutorSearchSpecifications;
import com.knowlink.api.tutors.repositories.*;
import com.knowlink.api.tutors.services.interfaces.ICareerService;
import com.knowlink.api.tutors.services.interfaces.ISubjectService;
import com.knowlink.api.tutors.services.interfaces.ITutorProfileService;
import com.knowlink.api.tutors.services.interfaces.ITutorSubjectAssemblyService;
import com.knowlink.api.tutors.validations.ITutorProfileValidationService;
import com.knowlink.api.ratings.repositories.*;
import com.knowlink.api.users.data.models.User;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.DayOfWeek;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
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
        private final IProfileImageService profileImageService;
        private final IUserRepository userRepository;

        @Override
        @Transactional(readOnly = true)
        public TutorProfileResponse getTutorProfile(UUID tutorUserId, UUID studentUserId) {
                TutorProfile tutorProfile = tutorProfileValidationService.findTutorProfileOrThrowException(tutorUserId);

                List<TutorSubjectResponse> subjectResponses = tutorSubjectRepository
                                .findActiveByTutorProfileId(tutorProfile.getTutorProfileId())
                                .stream()
                                .map(tutorProfileMapper::toSubjectResponse)
                                .collect(Collectors.toList());

                List<TutorReviewResponse> reviewResponses = ratingRepository
                                .findVisibleTutorRatings(tutorUserId)
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
        public TutorSelfProfileResponse updateProfile(UUID tutorUserId, UpdateTutorProfileRequest request) {
                TutorProfile tutorProfile = tutorProfileValidationService.findTutorProfileOrThrowException(tutorUserId);
                Career career = careerService.findByNameOrThrowException(request.career().trim());

                tutorProfileValidationService.ifCareerChangeBlockedBySubjectsThrowException(tutorProfile, career);
                tutorProfileValidationService.ifAddressRequiredForInPersonSubjectsThrowException(
                                tutorProfile, request.address());

                userService.updatePersonalData(tutorUserId, request.firstName(), request.lastName(),
                                request.dni(), request.phoneNumber());

                tutorProfile.setCareer(career);
                tutorProfile.setInstitutionalId(blankToNull(request.institutionalId()));
                tutorProfile.setBiography(blankToNull(request.biography()));
                tutorProfile.setAddress(blankToNull(request.address()));
                tutorProfile = tutorProfileRepository.save(tutorProfile);

                boolean hasStudentProfile = userProfileLookupService.hasStudentProfile(tutorUserId);
                return tutorProfileMapper.toSelfProfileResponse(tutorProfile, hasStudentProfile);
        }

        private String blankToNull(String value) {
                return value == null || value.isBlank() ? null : value.trim();
        }

        @Override
        @Transactional
        public TutorSelfProfileResponse uploadProfilePicture(UUID tutorUserId, MultipartFile file) {
                TutorProfile tutorProfile = tutorProfileValidationService.findTutorProfileOrThrowException(tutorUserId);

                profileImageService.deleteByUserId(tutorUserId);

                String imagePath = profileImageService.upload(file, tutorUserId);
                String signedUrl = profileImageService.generateSignedUrl(imagePath, 60 * 60 * 24 * 7);

                User user = userRepository.findById(tutorUserId)
                        .orElseThrow(() -> new ResourceNotFoundException(
                                "USER_NOT_FOUND", "Usuario no encontrado.",
                                String.format("User con id '%s' no existe", tutorUserId)));
                user.setProfilePictureUrl(signedUrl);
                userRepository.save(user);

                tutorProfile.setProfilePictureUrl(signedUrl);
                tutorProfile = tutorProfileRepository.save(tutorProfile);

                studentProfileService.syncProfilePicture(tutorUserId, signedUrl);

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
        public List<TutorSearchResponse> searchTutor(String query, UUID studentUserId, TutorSearchFilters filters) {
                validateMinRating(filters.minRating());

                Specification<TutorSubject> subjectSpec = TutorSearchSpecifications.subjectNameContains(query)
                                .and(TutorSearchSpecifications.subjectActive());
                if (filters.hasModality()) {
                        subjectSpec = subjectSpec.and(TutorSearchSpecifications.modalityIn(filters.modality()));
                }
                if (filters.hasCompensation()) {
                        subjectSpec = subjectSpec.and(TutorSearchSpecifications.compensationIn(filters.compensation()));
                }
                if (Boolean.TRUE.equals(filters.verifiedOnly())) {
                        subjectSpec = subjectSpec.and(TutorSearchSpecifications.tutorVerified());
                }
                if (filters.hasMinRating()) {
                        subjectSpec = subjectSpec.and(TutorSearchSpecifications.minAverageRating(filters.minRating()));
                }

                // Tutores que matchean por materia: la query dinámica combina la búsqueda
                // base de US-06A con los filtros presentes (AND). Los filtros de
                // verified/minRating se evalúan contra el tutor de la materia matcheada,
                // por lo que "verificado" cruza contra la materia buscada. La disponibilidad
                // se filtra en memoria (DAYOFWEEK difiere entre MySQL y H2, ver
                // TutorSearchSpecifications), con la misma semántica para ambas rutas.
                Map<UUID, List<TutorSubject>> groupedResults = new LinkedHashMap<>();
                for (TutorSubject tutorSubject : tutorSubjectRepository.findAll(subjectSpec)) {
                        TutorProfile tutorProfile = tutorSubject.getTutorProfile();
                        if (filters.hasAvailability()
                                        && !tutorHasAvailability(tutorProfile, filters.dayOfWeek())) {
                                continue;
                        }
                        groupedResults.computeIfAbsent(tutorProfile.getUser().getUserId(), k -> new ArrayList<>())
                                        .add(tutorSubject);
                }

                List<TutorProfile> nameMatches = tutorProfileRepository
                                .findByUser_FullNameContainingIgnoreCase(query);

                for (TutorProfile tutorProfile : nameMatches) {
                        List<TutorSubject> activeSubjects = tutorProfile.getSubjects().stream()
                                        .filter(TutorSubject::isActive)
                                        .toList();
                        if (activeSubjects.isEmpty()) {
                                continue;
                        }
                        if (Boolean.TRUE.equals(filters.verifiedOnly()) && !tutorProfile.isVerified()) {
                                continue;
                        }
                        if (filters.hasMinRating()
                                        && (tutorProfile.getAverageRating() == null
                                                        || tutorProfile.getAverageRating() < filters.minRating())) {
                                continue;
                        }
                        if (filters.hasAvailability()
                                        && !tutorHasAvailability(tutorProfile, filters.dayOfWeek())) {
                                continue;
                        }

                        List<TutorSubject> subjects = activeSubjects;
                        if (filters.hasModality() || filters.hasCompensation()) {
                                subjects = subjects.stream()
                                                .filter(ts -> TutorSearchSpecifications.matchesModality(
                                                                ts.getModality(), filters.modality()))
                                                .filter(ts -> TutorSearchSpecifications.matchesCompensation(
                                                                ts.getCompensationType(), filters.compensation()))
                                                .toList();
                                if (subjects.isEmpty()) {
                                        continue;
                                }
                        }

                        groupedResults.putIfAbsent(tutorProfile.getUser().getUserId(), subjects);
                }

                return groupedResults.values()
                                .stream()
                                .map(subjects -> {
                                        UUID tutorUserId = subjects.get(0).getTutorProfile().getUser().getUserId();
                                        int totalReviews = Math.toIntExact(
                                                        ratingRepository.countVisibleTutorRatings(tutorUserId));
                                        return TutorSearchMapper.from(subjects, totalReviews);
                                })
                                .toList();
        }

        private boolean tutorHasAvailability(TutorProfile tutorProfile, DayOfWeek dayOfWeek) {
                return availabilityBlockRepository.findAvailableByTutorProfileId(tutorProfile.getTutorProfileId())
                                .stream()
                                .anyMatch(block -> TutorSearchSpecifications.matchesAvailability(
                                                block, dayOfWeek, null));
        }


        /**
         * Estrategia definida para el edge case de calificacionMinima fuera de rango:
         * se devuelve 400 (Bad Request), no se clampea al rango válido. Documentado
         * también en Swagger.
         */
        private void validateMinRating(Double minRating) {
                if (minRating != null && (minRating < 1 || minRating > 5)) {
                        throw new ValidationException("La calificación mínima debe estar entre 1 y 5.");
                }
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
        public TutorSubjectResponse updateTutorSubject(UUID tutorUserId, UUID tutorSubjectId,
                        UpdateTutorSubjectRequest request) {
                TutorProfile tutorProfile = tutorProfileValidationService.findTutorProfileOrThrowException(tutorUserId);

                TutorSubject tutorSubject = tutorProfileValidationService
                                .findTutorSubjectOfTutorOrThrowException(tutorProfile, tutorSubjectId);

                tutorProfileValidationService.ifPaidPriceIsInvalidThrowException(
                                request.compensationType(), request.pricePerHour());

                tutorSubject.setModality(request.modality());
                tutorSubject.setCompensationType(request.compensationType());
                tutorSubject.setPricePerHour(
                                request.compensationType() == CompensationType.FREE ? null : request.pricePerHour());

                TutorSubject saved = tutorSubjectRepository.save(tutorSubject);
                return tutorProfileMapper.toSubjectResponse(saved);
        }

        @Override
        @Transactional
        public TutorSubjectResponse deactivateTutorSubject(UUID tutorUserId, UUID tutorSubjectId) {
                TutorSubject tutorSubject = findOwnedTutorSubjectForUpdateOrThrow(tutorUserId, tutorSubjectId);

                if (!tutorSubject.isActive()) {
                        return tutorProfileMapper.toSubjectResponse(tutorSubject); // idempotent
                }

                tutorProfileValidationService.ifSubjectHasActiveBookingsThrowException(tutorSubject);

                tutorSubject.setActive(false);
                TutorSubject saved = tutorSubjectRepository.save(tutorSubject);
                log.info("TutorSubject {} deactivated by tutor user {}", tutorSubjectId, tutorUserId);
                return tutorProfileMapper.toSubjectResponse(saved);
        }

        @Override
        @Transactional
        public TutorSubjectResponse reactivateTutorSubject(UUID tutorUserId, UUID tutorSubjectId) {
                TutorSubject tutorSubject = findOwnedTutorSubjectForUpdateOrThrow(tutorUserId, tutorSubjectId);

                if (tutorSubject.isActive()) {
                        return tutorProfileMapper.toSubjectResponse(tutorSubject); // idempotent
                }

                tutorSubject.setActive(true);
                TutorSubject saved = tutorSubjectRepository.save(tutorSubject);
                log.info("TutorSubject {} reactivated by tutor user {}", tutorSubjectId, tutorUserId);
                return tutorProfileMapper.toSubjectResponse(saved);
        }

        private TutorSubject findOwnedTutorSubjectForUpdateOrThrow(UUID tutorUserId, UUID tutorSubjectId) {
                TutorProfile tutorProfile = tutorProfileValidationService.findTutorProfileOrThrowException(tutorUserId);
                return tutorSubjectRepository
                                .findOwnedByIdForUpdate(tutorSubjectId, tutorProfile.getTutorProfileId())
                                .orElseThrow(() -> new ResourceNotFoundException(
                                                "TUTOR_SUBJECT_NOT_FOUND",
                                                "La materia no existe o no te pertenece.",
                                                String.format("TutorSubject '%s' not found for tutor user '%s'",
                                                                tutorSubjectId, tutorUserId)));
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