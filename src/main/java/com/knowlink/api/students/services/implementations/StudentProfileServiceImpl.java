package com.knowlink.api.students.services.implementations;

import com.knowlink.api.exceptions.custom_exceptions.ResourceNotFoundException;
import com.knowlink.api.exceptions.custom_exceptions.ValidationException;
import com.knowlink.api.security.enums.Role;
import com.knowlink.api.security.services.JwtService;
import com.knowlink.api.students.controllers.requests.ActivateTutorRoleRequest;
import com.knowlink.api.students.controllers.responses.ActivateTutorRoleResponse;
import com.knowlink.api.students.controllers.responses.StudentSelfProfileResponse;
import com.knowlink.api.students.validations.IStudentProfileValidationService;
import com.knowlink.api.auth.controllers.requests.StudentRegistrationRequest;
import com.knowlink.api.students.data.mappers.StudentProfileMapper;
import com.knowlink.api.students.data.models.StudentProfile;
import com.knowlink.api.students.repositories.IStudentProfileRepository;
import com.knowlink.api.students.services.interfaces.IStudentProfileService;
import com.knowlink.api.tutors.data.mappers.TutorProfileMapper;
import com.knowlink.api.tutors.data.mappers.TutorSubjectMapper;
import com.knowlink.api.tutors.data.models.Career;
import com.knowlink.api.tutors.data.models.Subject;
import com.knowlink.api.tutors.data.models.TutorProfile;
import com.knowlink.api.tutors.data.models.TutorSubject;
import com.knowlink.api.tutors.repositories.ITutorProfileRepository;
import com.knowlink.api.tutors.services.interfaces.ICareerService;
import com.knowlink.api.tutors.services.interfaces.ISubjectService;
import com.knowlink.api.users.data.enums.AccountStatus;
import com.knowlink.api.users.data.models.User;
import com.knowlink.api.users.repositories.IUserRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

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
    private final TutorSubjectMapper tutorSubjectMapper;
    private final ISubjectService subjectService;

    @Override
    public StudentProfile createProfile(User user, StudentRegistrationRequest request) {
        studentProfileValidationService.ifStudentProfileAlreadyExistsThrowException(user);

        Career career = careerService.findByNameOrThrowException(request.career());
        StudentProfile studentProfile = studentProfileMapper.toEntity(user, career, request);

        return studentProfileRepository.save(studentProfile);
    }

    @Override
    @Transactional
    public StudentSelfProfileResponse getSelfProfile(UUID userId) {
        StudentProfile profile = studentProfileValidationService.getStudentProfileOrThrow(userId);
        User user = profile.getUser();
        boolean hasTutorProfile = tutorProfileRepository.existsByUser_UserId(userId);

        return studentProfileMapper.toSelfProfileResponse(user, profile, hasTutorProfile);
    }

    @Override
    public boolean hasProfile(UUID userId) {
        return studentProfileRepository.existsByUser_UserId(userId);
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

        if (!tutorProfileRepository.existsByUser_UserId(userId)) {
            if (request == null || request.subjects() == null || request.subjects().isEmpty()) {
                throw new ValidationException("Debés seleccionar al menos una materia.");
            }
            StudentProfile studentProfile = studentProfileValidationService.getStudentProfileOrThrow(userId);
            TutorProfile tutorProfile = tutorProfileMapper.toEntityFromStudentActivation(user, studentProfile, request);

            request.subjects().forEach(s -> {
                Subject subject = subjectService.findByNameAndCareerOrThrowException(
                        s.subjectName(), studentProfile.getCareer());
                TutorSubject tutorSubject = tutorSubjectMapper.toEntity(s, tutorProfile, subject);
                tutorProfile.getSubjects().add(tutorSubject);
            });

            tutorProfileRepository.save(tutorProfile);
        }

        user.setRole(Role.TUTOR);
        userRepository.save(user);

        return new ActivateTutorRoleResponse(jwtService.generateToken(user));
    }
}