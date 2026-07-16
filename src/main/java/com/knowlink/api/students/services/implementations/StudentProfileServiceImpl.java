package com.knowlink.api.students.services.implementations;
import com.knowlink.api.students.validations.IStudentProfileValidationService;
import com.knowlink.api.auth.controllers.requests.StudentRegistrationRequest;
import com.knowlink.api.students.data.mappers.StudentProfileMapper;
import com.knowlink.api.students.data.models.StudentProfile;
import com.knowlink.api.students.repositories.IStudentProfileRepository;
import com.knowlink.api.students.services.interfaces.IStudentProfileService;
import com.knowlink.api.tutors.data.models.Career;
import com.knowlink.api.tutors.services.interfaces.ICareerService;
import com.knowlink.api.users.data.models.User;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class StudentProfileServiceImpl implements IStudentProfileService {

    private final IStudentProfileRepository studentProfileRepository;
    private final ICareerService careerService;
    private final StudentProfileMapper studentProfileMapper;
    private final IStudentProfileValidationService studentProfileValidationService;

    @Override
    public StudentProfile createProfile(User user, StudentRegistrationRequest request) {
        studentProfileValidationService.ifStudentProfileAlreadyExistsThrowException(user);

        Career career = careerService.findByNameOrThrowException(request.career());
        StudentProfile studentProfile = studentProfileMapper.toEntity(user, career, request);

        return studentProfileRepository.save(studentProfile);
    }
}