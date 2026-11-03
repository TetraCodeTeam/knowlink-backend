package com.knowlink.api.users.services;

import com.knowlink.api.exceptions.custom_exceptions.BusinessConflictException;
import com.knowlink.api.materials.service.interfaces.IMaterialService;
import com.knowlink.api.notifications.data.enums.NotificationType;
import com.knowlink.api.notifications.data.models.Notification;
import com.knowlink.api.notifications.repositories.NotificationRepository;
import com.knowlink.api.security.enums.Role;
import com.knowlink.api.tutors.data.enums.CompensationType;
import com.knowlink.api.tutors.data.enums.Modality;
import com.knowlink.api.tutors.data.enums.TutorSubjectStatus;
import com.knowlink.api.tutors.data.models.Career;
import com.knowlink.api.tutors.data.models.Subject;
import com.knowlink.api.tutors.data.models.TutorProfile;
import com.knowlink.api.tutors.data.models.TutorSubject;
import com.knowlink.api.tutors.repositories.ICareerRepository;
import com.knowlink.api.tutors.repositories.ISubjectRepository;
import com.knowlink.api.tutors.repositories.ITutorProfileRepository;
import com.knowlink.api.tutors.repositories.ITutorSubjectRepository;
import com.knowlink.api.users.data.enums.AccountStatus;
import com.knowlink.api.users.data.models.User;
import com.knowlink.api.users.repositories.IUserRepository;
import com.knowlink.api.users.services.interfaces.IAccountDeletionService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;

@SpringBootTest
@ActiveProfiles("test")
class UserAccountDeletionRollbackIntegrationTest {

    private static final String PASSWORD = "Password123!";
    private static final AtomicInteger DNI_SEQUENCE = new AtomicInteger(30_000_000);

    @Autowired
    private IAccountDeletionService accountDeletionService;
    @Autowired
    private IUserRepository userRepository;
    @Autowired
    private ITutorProfileRepository tutorProfileRepository;
    @Autowired
    private ITutorSubjectRepository tutorSubjectRepository;
    @Autowired
    private ICareerRepository careerRepository;
    @Autowired
    private ISubjectRepository subjectRepository;
    @Autowired
    private NotificationRepository notificationRepository;
    @Autowired
    private PasswordEncoder passwordEncoder;

    @MockitoBean
    private IMaterialService materialService;

    @Test
    @DisplayName("A failing IMaterialService.delete rolls the whole deletion back")
    void materialDeletionFailure_rollsBackTheAccountDeletion() {
        Career career = careerRepository.save(Career.builder()
                .name("Rollback Career " + UUID.randomUUID())
                .build());
        Subject subject = subjectRepository.save(Subject.builder()
                .name("Rollback Subject " + UUID.randomUUID())
                .isBasic(false)
                .career(career)
                .build());
        String originalEmail = "rollback." + UUID.randomUUID() + "@test.com";
        String originalDni = String.valueOf(DNI_SEQUENCE.getAndIncrement());
        User tutor = userRepository.save(User.builder()
                .fullName("Rollback Tutor")
                .firstName("Rollback")
                .lastName("Tutor")
                .email(originalEmail)
                .password(passwordEncoder.encode(PASSWORD))
                .dni(originalDni)
                .phoneNumber("+5491100000002")
                .role(Role.TUTOR)
                .accountStatus(AccountStatus.ACTIVE)
                .build());
        TutorProfile tutorProfile = tutorProfileRepository.save(TutorProfile.builder()
                .user(tutor)
                .career(career)
                .biography("Biografia intacta")
                .mercadoPagoLinked(true)
                .verified(true)
                .build());
        TutorSubject tutorSubject = tutorSubjectRepository.save(TutorSubject.builder()
                .tutorProfile(tutorProfile)
                .subject(subject)
                .description("Descripcion intacta")
                .pricePerHour(new BigDecimal("2500.00"))
                .compensationType(CompensationType.PAID)
                .tutorSubjectStatus(TutorSubjectStatus.ACTIVE)
                .modality(Modality.VIRTUAL)
                .build());
        Notification notification = notificationRepository.save(Notification.builder()
                .recipient(tutor)
                .recipientRole(Role.TUTOR)
                .type(NotificationType.SESSION_COMPLETED)
                .message("Notificacion que no debe borrarse")
                .dedupeKey("rollback-" + tutor.getUserId())
                .build());

        UUID materialId = UUID.randomUUID();
        when(materialService.findMaterialIdsByTutor(tutor.getUserId())).thenReturn(List.of(materialId));
        doThrow(new BusinessConflictException(
                "MATERIAL_HAS_REPORTS",
                "No pod\u00e9s eliminar un material con denuncias asociadas.",
                "Material has reports"))
                .when(materialService).delete(materialId, tutor.getUserId());

        assertThatThrownBy(() -> accountDeletionService.deleteAccount(tutor.getUserId(), PASSWORD))
                .isInstanceOf(BusinessConflictException.class);

        User afterFailure = userRepository.findById(tutor.getUserId()).orElseThrow();
        assertThat(afterFailure.getAccountStatus()).isEqualTo(AccountStatus.ACTIVE);
        assertThat(afterFailure.getEmail()).isEqualTo(originalEmail);
        assertThat(afterFailure.getDni()).isEqualTo(originalDni);
        assertThat(afterFailure.getFullName()).isEqualTo("Rollback Tutor");
        assertThat(afterFailure.getDeletedAt()).isNull();

        TutorProfile profileAfterFailure = tutorProfileRepository
                .findById(tutorProfile.getTutorProfileId()).orElseThrow();
        assertThat(profileAfterFailure.getBiography()).isEqualTo("Biografia intacta");
        assertThat(profileAfterFailure.isMercadoPagoLinked()).isTrue();
        assertThat(profileAfterFailure.isVerified()).isTrue();

        TutorSubject subjectAfterFailure = tutorSubjectRepository
                .findById(tutorSubject.getTutorSubjectId()).orElseThrow();
        assertThat(subjectAfterFailure.getDescription()).isEqualTo("Descripcion intacta");
        assertThat(subjectAfterFailure.isActive()).isTrue();

        assertThat(notificationRepository.findById(notification.getNotificationId())).isPresent();
    }
}