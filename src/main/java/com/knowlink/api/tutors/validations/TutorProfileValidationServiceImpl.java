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
        if (tutorProfileRepository.findByUserId(user.getUserId()).isPresent()) {
            throw new DuplicateResourceException("TutorProfile", "user", user.getUserId());
        }
    }
}