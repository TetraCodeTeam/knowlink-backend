package com.knowlink.api.students.services.implementations;

import com.knowlink.api.auth.controllers.requests.StudentRegistrationRequest;
import com.knowlink.api.exceptions.custom_exceptions.ResourceNotFoundException;
import com.knowlink.api.exceptions.custom_exceptions.ValidationException;
import com.knowlink.api.security.enums.Role;
import com.knowlink.api.security.services.JwtService;
import com.knowlink.api.students.controllers.requests.ActivateTutorRoleRequest;
import com.knowlink.api.students.controllers.responses.ActivateTutorRoleResponse;
import com.knowlink.api.students.controllers.responses.StudentSelfProfileResponse;
import com.knowlink.api.students.data.mappers.StudentProfileMapper;
import com.knowlink.api.students.data.models.StudentProfile;
import com.knowlink.api.students.repositories.IStudentProfileRepository;
import com.knowlink.api.students.services.interfaces.IStudentProfileService;
import com.knowlink.api.students.validations.IStudentProfileValidationService;
import com.knowlink.api.tutors.data.mappers.TutorProfileMapper;
import com.knowlink.api.tutors.data.models.Career;
import com.knowlink.api.tutors.data.models.TutorProfile;
import com.knowlink.api.tutors.repositories.ITutorProfileRepository;
import com.knowlink.api.tutors.services.interfaces.ICareerService;
import com.knowlink.api.tutors.services.interfaces.ITutorSubjectAssemblyService;
import com.knowlink.api.users.data.enums.AccountStatus;
import com.knowlink.api.users.data.models.User;
import com.knowlink.api.users.repositories.IUserRepository;
import com.knowlink.api.users.services.interfaces.IUserProfileLookupService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class StudentProfileServiceImpl implements IStudentProfileService {

    private final IStudentProfileRepository studentProfileRepository;
    private final ICareerService careerService;
    private final StudentProfileMapper studentProfileMapper;
    private final IStudentProfileValidationService studentProfileValidationService;
    private final ITutorProfileRepository tutorProfileRepository;
    private final IUserRepository userRepository;
    private final JwtService jwtService;
    private final TutorProfileMapper tutorProfileMapper;
    private final ITutorSubjectAssemblyService tutorSubjectAssemblyService;
    private final IUserProfileLookupService userProfileLookupService;

    @Override
    @Transactional
    public StudentProfile createProfile(User user, StudentRegistrationRequest request) {
        studentProfileValidationService.ifStudentProfileAlreadyExistsThrowException(user);

        Career career = careerService.findByNameOrThrowException(request.career());
        StudentProfile studentProfile = studentProfileMapper.toEntity(user, career, request);

        return studentProfileRepository.save(studentProfile);
    }

    @Override
    @Transactional(readOnly = true)
    public StudentSelfProfileResponse getSelfProfile(UUID userId) {
        StudentProfile profile = studentProfileValidationService.getStudentProfileOrThrow(userId);
        User user = profile.getUser();
        boolean hasTutorProfile = userProfileLookupService.hasTutorProfile(userId);

        return studentProfileMapper.toSelfProfileResponse(user, profile, hasTutorProfile);
    }

    @Override
    @Transactional
    public StudentProfile createProfileFromTutorData(User user, TutorProfile tutorProfile) {
        if (studentProfileRepository.existsByUser_UserId(user.getUserId())) {
            return studentProfileValidationService.getStudentProfileOrThrow(user.getUserId());
        }
        return studentProfileRepository.save(studentProfileMapper.toEntityFromTutorProfile(user, tutorProfile));
    }

    @Override
    @Transactional
    public ActivateTutorRoleResponse activateTutorRole(UUID userId, ActivateTutorRoleRequest request) {
        User user = userRepository.findByUserIdAndAccountStatusNot(userId, AccountStatus.DELETED)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "USER_NOT_FOUND", "Usuario no encontrado.",
                        String.format("User con id '%s' no existe", userId)));

        if (!userProfileLookupService.hasTutorProfile(userId)) {
            if (request == null || request.subjects() == null || request.subjects().isEmpty()) {
                throw new ValidationException("Debés seleccionar al menos una materia.");
            }
            StudentProfile studentProfile = studentProfileValidationService.getStudentProfileOrThrow(userId);
            TutorProfile tutorProfile = tutorProfileMapper.toEntityFromStudentActivation(user, studentProfile, request);

            tutorProfile.getSubjects().addAll(
                    tutorSubjectAssemblyService.buildTutorSubjects(tutorProfile, studentProfile.getCareer(), request.subjects()));

            // Persistencia directa (no ITutorProfileService): TutorProfileServiceImpl ya depende
            // de IStudentProfileService, así que inyectar ITutorProfileService acá crearía una
            // dependencia circular entre ambos beans (falla el arranque de Spring con constructor injection).
            tutorProfileRepository.save(tutorProfile);
        }

        user.setRole(Role.TUTOR);
        userRepository.save(user);

        return new ActivateTutorRoleResponse(jwtService.generateToken(user));
    }
}