package com.knowlink.api.ratings.repositories;

import com.knowlink.api.bookings.data.enums.BookingStatus;
import com.knowlink.api.bookings.data.models.Booking;
import com.knowlink.api.bookings.repositories.IBookingRepository;
import com.knowlink.api.ratings.data.models.Rating;
import com.knowlink.api.security.enums.Role;
import com.knowlink.api.timeslot.data.enums.SlotStatus;
import com.knowlink.api.timeslot.data.models.TimeSlot;
import com.knowlink.api.tutors.data.enums.CompensationType;
import com.knowlink.api.tutors.data.enums.Modality;
import com.knowlink.api.tutors.data.enums.TutorSubjectStatus;
import com.knowlink.api.tutors.data.models.Career;
import com.knowlink.api.tutors.data.models.Subject;
import com.knowlink.api.tutors.data.models.TutorProfile;
import com.knowlink.api.tutors.data.models.TutorSubject;
import com.knowlink.api.users.data.enums.AccountStatus;
import com.knowlink.api.users.data.models.User;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
@ActiveProfiles("test")
class IRatingRepositoryTest {

    @Autowired
    private IRatingRepository ratingRepository;
    @Autowired
    private IBookingRepository bookingRepository;
    @PersistenceContext
    private EntityManager entityManager;

    private User dualRoleUser;
    private User student;
    private User otherTutor;
    private TutorProfile dualRoleProfile;
    private TutorProfile otherTutorProfile;
    private TutorSubject dualRoleSubject;
    private TutorSubject otherTutorSubject;

    @BeforeEach
    void setUp() {
        Career career = persist(Career.builder().name("Test Career").build());
        dualRoleUser = persistUser("dual-role", Role.TUTOR);
        student = persistUser("student", Role.STUDENT);
        otherTutor = persistUser("other-tutor", Role.TUTOR);

        dualRoleProfile = persistTutorProfile(dualRoleUser, career);
        otherTutorProfile = persistTutorProfile(otherTutor, career);
        Subject subject = persist(Subject.builder()
                .name("Test Subject")
                .career(career)
                .isBasic(true)
                .build());
        dualRoleSubject = persistTutorSubject(dualRoleProfile, subject);
        otherTutorSubject = persistTutorSubject(otherTutorProfile, subject);
    }

    @Test
    void tutorAverageOnlyIncludesRatingsFromBookingsWhereUserWasTutor() {
        LocalDate sessionDate = LocalDate.of(2026, 9, 30);
        LocalDateTime completedAt = LocalDateTime.of(2026, 9, 30, 12, 0);
        Booking userAsStudent = persistBooking(
                dualRoleUser, otherTutor, otherTutorProfile, otherTutorSubject, sessionDate, completedAt);
        Booking userAsTutor = persistBooking(
                student, dualRoleUser, dualRoleProfile, dualRoleSubject, sessionDate, completedAt);

        persistRating(userAsStudent, otherTutor, dualRoleUser, 1, true);
        persistRating(userAsTutor, student, dualRoleUser, 5, true);
        entityManager.flush();

        LocalDateTime visibleBefore = LocalDateTime.of(2026, 10, 2, 10, 0);
        Double average = ratingRepository.calculateVisibleTutorAverage(
                dualRoleUser.getUserId(), visibleBefore, visibleBefore.toLocalDate(), visibleBefore.toLocalTime());
        List<Rating> visibleTutorRatings = ratingRepository.findVisibleTutorRatings(
                dualRoleUser.getUserId(), visibleBefore, visibleBefore.toLocalDate(), visibleBefore.toLocalTime());

        assertThat(average).isEqualTo(5.0);
        assertThat(visibleTutorRatings).extracting(Rating::getScore).containsExactly(5);
    }

    @Test
    void expiredRatingsWithNullConfirmedAtUseCompletedSessionEnd() {
        LocalDateTime visibleBefore = LocalDateTime.of(2026, 10, 2, 10, 0);
        Booking expiredBooking = persistBooking(
                student, dualRoleUser, dualRoleProfile, dualRoleSubject,
                LocalDate.of(2026, 10, 1), null);
        expiredBooking.setEndTime(LocalTime.of(9, 0));
        Booking recentBooking = persistBooking(
                otherTutor, dualRoleUser, dualRoleProfile, dualRoleSubject,
                LocalDate.of(2026, 10, 2), null);
        recentBooking.setEndTime(LocalTime.of(11, 0));
        persistRating(expiredBooking, student, dualRoleUser, 4, false);
        persistRating(recentBooking, otherTutor, dualRoleUser, 5, false);
        entityManager.flush();

        List<Rating> expiredRatings = ratingRepository.findExpiredHiddenRatings(
                visibleBefore, visibleBefore.toLocalDate(), visibleBefore.toLocalTime());

        assertThat(expiredRatings).extracting(rating -> rating.getBooking().getBookingId())
                .containsExactly(expiredBooking.getBookingId());
    }

    @Test
    void findByIdForUpdateLoadsBookingParticipants() {
        Booking booking = persistBooking(
                student, dualRoleUser, dualRoleProfile, dualRoleSubject,
                LocalDate.of(2026, 9, 30), LocalDateTime.of(2026, 9, 30, 12, 0));
        entityManager.flush();
        entityManager.clear();

        Booking loaded = bookingRepository.findByIdForUpdate(booking.getBookingId()).orElseThrow();

        assertThat(loaded.getStudent().getUserId()).isEqualTo(student.getUserId());
        assertThat(loaded.getTutor().getUserId()).isEqualTo(dualRoleUser.getUserId());
    }

        @Test
        void databaseRejectsDuplicateRatingFromSameParticipantAndBooking() {
                Booking booking = persistBooking(
                                student, dualRoleUser, dualRoleProfile, dualRoleSubject,
                                LocalDate.of(2026, 9, 30), LocalDateTime.of(2026, 9, 30, 12, 0));
                persistRating(booking, student, dualRoleUser, 4, false);
                entityManager.flush();

                assertThatThrownBy(() -> {
                        persistRating(booking, student, dualRoleUser, 5, false);
                        entityManager.flush();
                }).isInstanceOf(RuntimeException.class);
        }

    private User persistUser(String localName, Role role) {
        return persist(User.builder()
                .fullName(localName)
                .email(localName + "@ratings.test")
                .password("hashed")
                .role(role)
                .accountStatus(AccountStatus.ACTIVE)
                .build());
    }

    private TutorProfile persistTutorProfile(User user, Career career) {
        return persist(TutorProfile.builder().user(user).career(career).build());
    }

    private TutorSubject persistTutorSubject(TutorProfile tutorProfile, Subject subject) {
        return persist(TutorSubject.builder()
                .tutorProfile(tutorProfile)
                .subject(subject)
                .modality(Modality.VIRTUAL)
                .compensationType(CompensationType.PAID)
                .tutorSubjectStatus(TutorSubjectStatus.ACTIVE)
                .build());
    }

    private Booking persistBooking(User bookingStudent, User tutor, TutorProfile tutorProfile,
            TutorSubject tutorSubject, LocalDate sessionDate, LocalDateTime confirmedAt) {
        LocalTime endTime = LocalTime.of(12, 0);
        TimeSlot timeSlot = persist(TimeSlot.builder()
                .date(sessionDate)
                .startTime(endTime.minusHours(1))
                .endTime(endTime)
                .status(SlotStatus.OCCUPIED)
                .tutorProfileId(tutorProfile.getTutorProfileId())
                .build());
        return persist(Booking.builder()
                .amount(BigDecimal.TEN)
                .sessionDate(sessionDate)
                .startTime(endTime.minusHours(1))
                .endTime(endTime)
                .modality(Modality.VIRTUAL)
                .bookingStatus(BookingStatus.COMPLETED)
                .timeSlot(timeSlot)
                .tutorSubject(tutorSubject)
                .student(bookingStudent)
                .tutor(tutor)
                .confirmedAt(confirmedAt)
                .build());
    }

    private Rating persistRating(Booking booking, User rater, User rated, int score, boolean visible) {
        return persist(Rating.builder()
                .booking(booking)
                .raterUser(rater)
                .ratedUser(rated)
                .score(score)
                .comment(null)
                .ratingDate(LocalDateTime.of(2026, 10, 1, 12, 0))
                .visible(visible)
                .build());
    }

        private <T> T persist(T entity) {
                entityManager.persist(entity);
                return entity;
        }
}