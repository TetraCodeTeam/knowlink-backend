package com.knowlink.api.tutors.services;

import com.knowlink.api.notifications.data.enums.NotificationType;
import com.knowlink.api.notifications.services.interfaces.INotificationService;
import com.knowlink.api.ratings.repositories.IRatingRepository;
import com.knowlink.api.security.enums.Role;
import com.knowlink.api.tutors.config.SubjectVerificationProperties;
import com.knowlink.api.tutors.data.enums.TutorSubjectStatus;
import com.knowlink.api.tutors.data.models.Subject;
import com.knowlink.api.tutors.data.models.TutorProfile;
import com.knowlink.api.tutors.data.models.TutorSubject;
import com.knowlink.api.tutors.data.projections.SubjectVerificationAggregate;
import com.knowlink.api.tutors.repositories.ITutorProfileRepository;
import com.knowlink.api.tutors.repositories.ITutorSubjectRepository;
import com.knowlink.api.tutors.services.implementations.SubjectVerificationServiceImpl;
import com.knowlink.api.users.data.models.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SubjectVerificationServiceImplTest {

    private static final UUID TUTOR_USER_ID = UUID.randomUUID();

    @Mock
    private ITutorSubjectRepository tutorSubjectRepository;
    @Mock
    private ITutorProfileRepository tutorProfileRepository;
    @Mock
    private IRatingRepository ratingRepository;
    @Mock
    private INotificationService notificationService;

    private SubjectVerificationServiceImpl service;
    private TutorProfile tutorProfile;
    private TutorSubject subject;

    @BeforeEach
    void setUp() {
        service = serviceWith(new SubjectVerificationProperties(
                new BigDecimal("4.5"), new BigDecimal("5.0"), 1));
        tutorProfile = TutorProfile.builder()
                .user(User.builder().userId(TUTOR_USER_ID).fullName("Ana Tutor").build())
                .build();
        subject = TutorSubject.builder()
                .tutorSubjectId(UUID.randomUUID())
                .tutorProfile(tutorProfile)
                .subject(Subject.builder().name("Algebra").build())
                .tutorSubjectStatus(TutorSubjectStatus.PENDING)
                .build();
        when(tutorProfileRepository.findByUserId(TUTOR_USER_ID)).thenReturn(Optional.of(tutorProfile));
        when(tutorSubjectRepository.findByTutorProfileId(any())).thenReturn(List.of(subject));
    }

    private SubjectVerificationServiceImpl serviceWith(SubjectVerificationProperties properties) {
        return new SubjectVerificationServiceImpl(
                tutorSubjectRepository, tutorProfileRepository, ratingRepository,
                properties, notificationService);
    }

    private void givenRatings(long totalScore, long count) {
        List<SubjectVerificationAggregate> aggregates = count == 0
                ? List.of()
                : List.of(aggregate(subject.getTutorSubjectId(), totalScore, count));
        when(ratingRepository.findSubjectAggregatesByTutorId(TUTOR_USER_ID)).thenReturn(aggregates);
    }

    private SubjectVerificationAggregate aggregate(UUID tutorSubjectId, long totalScore, long ratingCount) {
        return new SubjectVerificationAggregate() {
            @Override
            public UUID getTutorSubjectId() {
                return tutorSubjectId;
            }

            @Override
            public Long getTotalScore() {
                return totalScore;
            }

            @Override
            public Long getRatingCount() {
                return ratingCount;
            }
        };
    }

    @Test
    @DisplayName("Sin calificaciones la materia pierde la verificacion heredada y queda con conteo en cero")
    void withoutRatings_clearsInheritedVerification() {
        // Legacy rows could carry a verification that no longer has ratings behind it.
        subject.setVerified(true);
        subject.setAverageRating(new BigDecimal("4.80"));
        subject.setRatingCount(3);
        tutorProfile.setVerified(true);
        givenRatings(0, 0);

        service.recalculateForTutor(TUTOR_USER_ID);

        assertThat(subject.isVerified()).isFalse();
        assertThat(subject.getAverageRating()).isNull();
        assertThat(subject.getRatingCount()).isZero();
        verify(tutorSubjectRepository).saveAll(List.of(subject));
        verify(notificationService).notifyUser(
                eq(TUTOR_USER_ID), eq(Role.TUTOR), eq(NotificationType.SUBJECT_VALIDATION_UPDATED),
                anyString(), anyString(), eq(subject.getTutorSubjectId()), anyString());
    }

    @Test
    @DisplayName("Promedio justo en el umbral (4.5) verifica la materia")
    void averageAtThreshold_verifiesTheSubject() {
        givenRatings(9, 2);

        service.recalculateForTutor(TUTOR_USER_ID);

        assertThat(subject.isVerified()).isTrue();
        assertThat(subject.getAverageRating()).isEqualByComparingTo("4.5");
        assertThat(subject.getRatingCount()).isEqualTo(2);
        assertThat(tutorProfile.isVerified()).isTrue();
        verify(notificationService).notifyUser(
                eq(TUTOR_USER_ID), eq(Role.TUTOR), eq(NotificationType.SUBJECT_VALIDATION_UPDATED),
                anyString(), anyString(), eq(subject.getTutorSubjectId()), anyString());
    }

    @Test
    @DisplayName("Promedio perfecto (5.0) verifica la materia")
    void perfectAverage_verifiesTheSubject() {
        givenRatings(10, 2);

        service.recalculateForTutor(TUTOR_USER_ID);

        assertThat(subject.isVerified()).isTrue();
        assertThat(subject.getAverageRating()).isEqualByComparingTo("5.0");
        assertThat(tutorProfile.isVerified()).isTrue();
    }

    @Test
    @DisplayName("Promedio 4.49 no verifica, aunque redondeado muestre 4.5")
    void averageJustBelowThreshold_doesNotVerifyTheSubject() {
        givenRatings(449, 100);

        service.recalculateForTutor(TUTOR_USER_ID);

        assertThat(subject.isVerified()).isFalse();
        assertThat(subject.getAverageRating()).isEqualByComparingTo("4.49");
        assertThat(tutorProfile.isVerified()).isFalse();
        verify(notificationService, never()).notifyUser(
                any(), any(), any(), anyString(), anyString(), any(), anyString());
    }

    @Test
    @DisplayName("Una materia verificada pierde la verificacion cuando el promedio baja")
    void aVerifiedSubjectLosesVerification_whenTheAverageDrops() {
        subject.setVerified(true);
        tutorProfile.setVerified(true);
        givenRatings(44, 10);

        service.recalculateForTutor(TUTOR_USER_ID);

        assertThat(subject.isVerified()).isFalse();
        assertThat(subject.getAverageRating()).isEqualByComparingTo("4.4");
        assertThat(tutorProfile.isVerified()).isFalse();
        verify(notificationService).notifyUser(
                eq(TUTOR_USER_ID), eq(Role.TUTOR), eq(NotificationType.SUBJECT_VALIDATION_UPDATED),
                anyString(), anyString(), eq(subject.getTutorSubjectId()), anyString());
    }

    @Test
    @DisplayName("Una materia sin verificar recupera la verificacion cuando el promedio sube")
    void anUnverifiedSubjectGainsVerification_whenTheAverageRises() {
        givenRatings(45, 10);

        service.recalculateForTutor(TUTOR_USER_ID);

        assertThat(subject.isVerified()).isTrue();
        assertThat(tutorProfile.isVerified()).isTrue();
        verify(notificationService, times(1)).notifyUser(
                eq(TUTOR_USER_ID), eq(Role.TUTOR), eq(NotificationType.SUBJECT_VALIDATION_UPDATED),
                anyString(), anyString(), eq(subject.getTutorSubjectId()), anyString());
    }

    @Test
    @DisplayName("Con min-ratings en3, dos calificaciones altas no verifican")
    void minimumRatingsIsRespected() {
        service = serviceWith(new SubjectVerificationProperties(
                new BigDecimal("4.5"), new BigDecimal("5.0"), 3));
        givenRatings(10, 2);

        service.recalculateForTutor(TUTOR_USER_ID);

        assertThat(subject.isVerified()).isFalse();
        assertThat(subject.getRatingCount()).isEqualTo(2);
        assertThat(subject.getAverageRating()).isEqualByComparingTo("5.0");
    }

    @Test
    @DisplayName("No persiste ni notifica cuando nada cambio")
    void doesNotPersistWhenNothingChanged() {
        subject.setVerified(true);
        subject.setRatingCount(2);
        subject.setAverageRating(new BigDecimal("4.50"));
        tutorProfile.setVerified(true);
        givenRatings(9, 2);

        service.recalculateForTutor(TUTOR_USER_ID);

        verify(tutorSubjectRepository, never()).saveAll(any());
        verify(notificationService, never()).notifyUser(
                any(), any(), any(), anyString(), anyString(), any(), anyString());
        verify(tutorProfileRepository, never()).save(any());
    }

    @Test
    @DisplayName("El badge del tutor exige una materia verificada y ademas activa")
    void tutorBadgeRequiresASubjectThatIsVerifiedAndActive() {
        subject.setVerified(true);
        subject.setActive(false);
        givenRatings(9, 2);

        service.recalculateForTutor(TUTOR_USER_ID);

        assertThat(subject.isVerified()).isTrue();
        assertThat(tutorProfile.isVerified()).isFalse();

        verify(tutorProfileRepository, never()).save(any());
    }

    @Test
    @DisplayName("El recálculo es idempotente: dos llamadas seguidas solo guardan una vez")
    void recalculationIsIdempotent() {
        givenRatings(9, 2);

        service.recalculateForTutor(TUTOR_USER_ID);
        service.recalculateForTutor(TUTOR_USER_ID);

        verify(tutorSubjectRepository, times(1)).saveAll(anyList());
        verify(notificationService, times(1)).notifyUser(
                eq(TUTOR_USER_ID), eq(Role.TUTOR), eq(NotificationType.SUBJECT_VALIDATION_UPDATED),
                anyString(), anyString(), eq(subject.getTutorSubjectId()), anyString());
    }
}