package com.knowlink.api.tutors.services.implementations;

import com.knowlink.api.auth.controllers.requests.TutorRegistrationRequest;
import com.knowlink.api.auth.controllers.requests.TutorSubjectRequest;
import com.knowlink.api.exceptions.custom_exceptions.DuplicateResourceException;
import com.knowlink.api.exceptions.custom_exceptions.ResourceNotFoundException;
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
        private final ITutorSubjectRepository subjectTutorRepository;

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

        @Override
        @Transactional(readOnly = true)
        public TutorSelfProfileResponse getSelfProfile(UUID tutorUserId) {
                TutorProfile tutorProfile = tutorProfileValidationService.findTutorProfileOrThrowException(tutorUserId);
                return tutorProfileMapper.toSelfProfileResponse(tutorProfile);
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
        public List<TutorSearchResponse> searchTutor(String query, UUID alumnoUserId) {

                List<TutorSubject> resultadosPorMateria = subjectTutorRepository
                                .findBySubject_NameContainingIgnoreCase(query);

                Map<UUID, List<TutorSubject>> agrupados = resultadosPorMateria.stream()
                                .collect(Collectors.groupingBy(
                                                mt -> mt.getTutorProfile().getUser().getUserId()));

                // Tutores que matchean por su propio nombre (no por materia): se agregan
                // con todas sus materias, sin pisar a los que ya matchearon por materia
                // arriba. Se excluyen tutores sin materias cargadas para no romper
                // TutorSearchMapper.from(), que requiere al menos una.
                List<TutorProfile> resultadosPorNombre = tutorProfileRepository
                                .findByUser_FullNameContainingIgnoreCase(query);

                for (TutorProfile tutorProfile : resultadosPorNombre) {
                        if (tutorProfile.getSubjects().isEmpty()) {
                                continue;
                        }
                        agrupados.putIfAbsent(tutorProfile.getUser().getUserId(), tutorProfile.getSubjects());
                }

                return agrupados.values()
                                .stream()
                                .map(TutorSearchMapper::from)
                                .toList();
        }

        @Override
        @Transactional
        public TutorSubjectResponse createTutorSubject(UUID tutorUserId, TutorSubjectRequest request) {
                TutorProfile tutorProfile = tutorProfileRepository.findByUserId(tutorUserId)
                                .orElseThrow(() -> new ResourceNotFoundException(
                                                "Tutor",
                                                
                                                "id",
                                                tutorUserId.toString()));

                Subject subject = subjectService.findByNameAndCareerOrThrowException(
                                request.subjectName(), tutorProfile.getCareer());

                boolean yaDictaEstaMateria = tutorProfile.getSubjects().stream()
                                .anyMatch(ts -> ts.getSubject().getSubjectId().equals(subject.getSubjectId()));

                if (yaDictaEstaMateria) {
                        throw new DuplicateResourceException("TutorSubject", "subject", request.subjectName());
                }

                TutorSubject tutorSubject = tutorSubjectMapper.toEntity(request, tutorProfile, subject);
                TutorSubject saved = tutorSubjectRepository.save(tutorSubject);

                return tutorProfileMapper.toSubjectResponse(saved);
        }
}