package com.knowlink.api.tutors.validations;

import com.knowlink.api.exceptions.custom_exceptions.DuplicateResourceException;
import com.knowlink.api.tutors.repositories.ITutorProfileRepository;
import com.knowlink.api.users.data.models.User;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class TutorProfileValidationServiceImpl implements ITutorProfileValidationService {

    private final ITutorProfileRepository tutorProfileRepository;

    @Override
    public void ifTutorProfileAlreadyExistsThrowException(User user) {
        if (tutorProfileRepository.existsByUser_UserId(user.getUserId())) {
            throw new DuplicateResourceException(
                    "DUPLICATE_TUTOR_PROFILE",
                    "El usuario ya tiene un perfil de tutor",
                    String.format("user con id '%s' ya tiene tutor profile", user.getUserId()));
        }
    }
}