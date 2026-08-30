package com.knowlink.api.users.services.implementations;

import com.knowlink.api.students.repositories.IStudentProfileRepository;
import com.knowlink.api.tutors.repositories.ITutorProfileRepository;
import com.knowlink.api.users.services.interfaces.IUserProfileLookupService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class UserProfileLookupServiceImpl implements IUserProfileLookupService {

    private final ITutorProfileRepository tutorProfileRepository;
    private final IStudentProfileRepository studentProfileRepository;

    @Override
    @Transactional(readOnly = true)
    public boolean hasTutorProfile(UUID userId) {
        return tutorProfileRepository.existsByUser_UserId(userId);
    }

    @Override
    @Transactional(readOnly = true)
    public boolean hasStudentProfile(UUID userId) {
        return studentProfileRepository.existsByUser_UserId(userId);
    }
}