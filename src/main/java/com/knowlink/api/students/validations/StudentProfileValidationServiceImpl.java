package com.knowlink.api.students.validations;

import com.knowlink.api.exceptions.custom_exceptions.DuplicateResourceException;
import com.knowlink.api.students.repositories.IStudentProfileRepository;
import com.knowlink.api.users.data.models.User;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class StudentProfileValidationServiceImpl implements IStudentProfileValidationService {

    private final IStudentProfileRepository studentProfileRepository;

    @Override
    public void ifStudentProfileAlreadyExistsThrowException(User user) {
        if (studentProfileRepository.existsByUser_UserId(user.getUserId())) {
            throw new DuplicateResourceException(
                    "DUPLICATE_STUDENT_PROFILE",
                    "El usuario ya tiene un perfil de alumno",
                    String.format("user con id '%s' ya tiene student profile", user.getUserId())
            );
        }
    }
}