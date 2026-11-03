package com.knowlink.api.users.services.implementations;

import com.knowlink.api.bookings.services.interfaces.IHoldService;
import com.knowlink.api.community.services.interfaces.ICommunityPostService;
import com.knowlink.api.community.services.interfaces.ICommunityReplyService;
import com.knowlink.api.community.services.interfaces.ITutorRankingService;
import com.knowlink.api.exceptions.custom_exceptions.ResourceNotFoundException;
import com.knowlink.api.materials.service.interfaces.IMaterialService;
import com.knowlink.api.notifications.services.interfaces.INotificationService;
import com.knowlink.api.security.enums.Role;
import com.knowlink.api.students.repositories.IStudentProfileRepository;
import com.knowlink.api.tutors.data.models.TutorProfile;
import com.knowlink.api.tutors.data.models.TutorSubject;
import com.knowlink.api.tutors.repositories.ITutorProfileRepository;
import com.knowlink.api.tutors.repositories.ITutorSubjectRepository;
import com.knowlink.api.users.data.enums.AccountStatus;
import com.knowlink.api.users.data.models.User;
import com.knowlink.api.users.events.AccountDeletedEvent;
import com.knowlink.api.users.services.interfaces.IAccountDeletionService;
import com.knowlink.api.users.services.interfaces.ITokenService;
import com.knowlink.api.users.services.interfaces.IUserService;
import com.knowlink.api.users.validation.IUserValidationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class AccountDeletionServiceImpl implements IAccountDeletionService {

    private final IUserService userService;
    private final IUserValidationService userValidationService;
    private final ITokenService tokenService;
    private final IHoldService holdService;
    private final INotificationService notificationService;
    private final ICommunityPostService communityPostService;
    private final ICommunityReplyService communityReplyService;
    private final ITutorRankingService tutorRankingService;
    private final IMaterialService materialService;
    private final IStudentProfileRepository studentProfileRepository;
    private final ITutorProfileRepository tutorProfileRepository;
    private final ITutorSubjectRepository tutorSubjectRepository;
    private final PasswordEncoder passwordEncoder;
    private final ApplicationEventPublisher eventPublisher;

    @Override
    @Transactional
    public void deleteAccount(UUID userId, String rawPassword) {
        // Lock first, then validate: this serializes against hold/booking creation.
        User user = userService.lockForUpdateOrThrowException(userId);

        if (user.getRole() == Role.ADMIN) {
            throw new AccessDeniedException("ADMIN accounts cannot be deleted through this endpoint");
        }
        if (user.getAccountStatus() == AccountStatus.DELETED) {
            throw new ResourceNotFoundException(
                    "USER_NOT_FOUND",
                    "Usuario no encontrado.",
                    String.format("user '%s' is already deleted", userId));
        }
        if (!passwordEncoder.matches(rawPassword, user.getPassword())) {
            throw new BadCredentialsException("Invalid password");
        }

        userValidationService.ifUserHasActiveBookingsThrowException(userId);
        userValidationService.ifUserHasPendingObligationsThrowException(userId);

        // Storage paths are collected before clearing the data so they can be removed after commit.
        List<String> storagePaths = new ArrayList<>();
        storagePaths.addAll(communityPostService.softDeleteAllByAuthor(userId));
        storagePaths.addAll(communityReplyService.softDeleteAllByAuthor(userId));
        communityReplyService.removeHelpfulMarksByUser(userId);
        tutorRankingService.removeEntriesForTutor(userId);

        holdService.deleteAllForStudent(userId);
        notificationService.deleteAllForRecipient(userId);
        tokenService.deleteTokensByUser(user);
        tokenService.deletePasswordResetTokensByUser(user);

        clearStudentProfile(userId);
        clearTutorProfileAndSubjects(userId);
        deleteTutorMaterials(userId);

        userService.anonymizeForDeletion(user);

        eventPublisher.publishEvent(new AccountDeletedEvent(userId, List.copyOf(storagePaths)));
        log.info("Account permanently deleted for userId '{}'", userId);
    }

    private void clearStudentProfile(UUID userId) {
        studentProfileRepository.findByUserId(userId).ifPresent(profile -> {
            profile.setProfilePictureUrl(null);
            profile.setInstitutionalId(null);
            studentProfileRepository.save(profile);
        });
    }

    private void clearTutorProfileAndSubjects(UUID userId) {
        tutorProfileRepository.findByUserId(userId).ifPresent(profile -> {
            profile.setBiography(null);
            profile.setProfilePictureUrl(null);
            profile.setInstitutionalId(null);
            profile.setAddress(null);
            profile.setMercadoPagoLinked(false);
            profile.setVerified(false);
            tutorProfileRepository.save(profile);
            deactivateSubjects(profile);
        });
    }

    private void deactivateSubjects(TutorProfile profile) {
        List<TutorSubject> subjects = tutorSubjectRepository.findByTutorProfileId(profile.getTutorProfileId());
        for (TutorSubject subject : subjects) {
            subject.setDescription(null);
            subject.setActive(false);
            tutorSubjectRepository.save(subject);
        }
    }

    private void deleteTutorMaterials(UUID userId) {
        for (UUID materialId : materialService.findMaterialIdsByTutor(userId)) {
            materialService.delete(materialId, userId);
        }
    }
}