package com.knowlink.api.ratings.repositories;

import com.knowlink.api.tutors.data.models.Institution;
import com.knowlink.api.tutors.repositories.IInstitutionRepository;
import com.knowlink.api.testsupport.CatalogFixtures;
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
import com.knowlink.api.tutors.data.projections.OverallRatingSummary;
import com.knowlink.api.tutors.data.projections.SubjectRatingSummary;
import com.knowlink.api.tutors.data.projections.SubjectVerificationAggregate;
import com.knowlink.api.users.data.enums.AccountStatus;
import com.knowlink.api.users.data.models.User;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
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
    private IInstitutionRepository institutionRepository;

    private Institution institution;
    private Career career;
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
        institution = CatalogFixtures.saveInstitution(institutionRepository, "Test " + System.nanoTime());
        career = persist(Career.builder().institution(institution).name("Test Career").build());
        dualRoleUser = persistUser("dual-role", Role.TUTOR);
        student = persistUser("student", Role.STUDENT);
        otherTutor = persistUser("other-tutor", Role.TUTOR);

        dualRoleProfile = persistTutorProfile(dualRoleUser, career);
        otherTutorProfile = persistTutorProfile(otherTutor, career);
        Subject subject = persist(Subject.builder().institution(institution)
                .name("Test Subject")
                .career(career)
                
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

        Double average = ratingRepository.calculateVisibleTutorAverage(dualRoleUser.getUserId());
        List<Rating> visibleTutorRatings = ratingRepository.findVisibleTutorRatings(dualRoleUser.getUserId());

        assertThat(average).isEqualTo(5.0);
        assertThat(visibleTutorRatings).extracting(Rating::getScore).containsExactly(5);
    }

    @Test
    void historyQueriesOnlyCountRatingsReceivedAsTutor() {
        LocalDate sessionDate = LocalDate.of(2026, 9, 30);
        LocalDateTime completedAt = LocalDateTime.of(2026, 9, 30, 12, 0);
        Booking bookingAsStudent = persistBooking(
                dualRoleUser, otherTutor, otherTutorProfile, otherTutorSubject, sessionDate, completedAt);
        Booking bookingAsTutor = persistBooking(
                student, dualRoleUser, dualRoleProfile, dualRoleSubject, sessionDate, completedAt);

        persistRating(bookingAsStudent, otherTutor, dualRoleUser, 1, true, "Rated while being the student.");
        persistRating(bookingAsTutor, student, dualRoleUser, 5, true, "Rated while being the tutor.");
        entityManager.flush();

        OverallRatingSummary summary = ratingRepository.findOverallSummaryByTutorId(dualRoleUser.getUserId());
        List<SubjectRatingSummary> bySubject = ratingRepository
                .findAverageGroupedBySubject(dualRoleUser.getUserId());
        Page<Rating> comments = ratingRepository.findCompletedComments(dualRoleUser.getUserId(),
                PageRequest.of(0, 10));
        Page<Rating> commentsBySubject = ratingRepository.findCompletedCommentsBySubject(
                dualRoleUser.getUserId(), dualRoleSubject.getSubject().getSubjectId(), PageRequest.of(0, 10));

        assertThat(summary.getRatingCount()).isEqualTo(1);
        assertThat(summary.getAverageScore()).isEqualTo(5.0);
        assertThat(bySubject).hasSize(1);
        assertThat(bySubject.get(0).getRatingCount()).isEqualTo(1L);
        assertThat(bySubject.get(0).getAverageScore()).isEqualTo(5.0);
        assertThat(comments.getTotalElements()).isEqualTo(1);
        assertThat(comments.getContent()).extracting(Rating::getComment)
                .containsExactly("Rated while being the tutor.");
        assertThat(commentsBySubject.getTotalElements()).isEqualTo(1);
        assertThat(commentsBySubject.getContent()).extracting(Rating::getComment)
                .containsExactly("Rated while being the tutor.");
    }

    @Test
    void historyCommentCountsOnlyIncludeRatingsWithComment() {
        LocalDate sessionDate = LocalDate.of(2026, 9, 30);
        LocalDateTime completedAt = LocalDateTime.of(2026, 9, 30, 12, 0);
        Booking withComment = persistBooking(
                student, dualRoleUser, dualRoleProfile, dualRoleSubject, sessionDate, completedAt);
        Booking withoutComment = persistBooking(
                student, dualRoleUser, dualRoleProfile, dualRoleSubject, sessionDate.plusDays(1), completedAt);

        persistRating(withComment, student, dualRoleUser, 5, true, "Buen trato.");
        persistRating(withoutComment, student, dualRoleUser, 4, true);
        entityManager.flush();

        OverallRatingSummary summary = ratingRepository.findOverallSummaryByTutorId(dualRoleUser.getUserId());
        List<SubjectRatingSummary> bySubject = ratingRepository
                .findAverageGroupedBySubject(dualRoleUser.getUserId());

        assertThat(summary.getRatingCount()).isEqualTo(2);
        assertThat(summary.getCommentCount()).isEqualTo(1);
        assertThat(bySubject).hasSize(1);
        assertThat(bySubject.get(0).getRatingCount()).isEqualTo(2L);
        assertThat(bySubject.get(0).getCommentCount()).isEqualTo(1L);
    }

    @Test
    void expiredRatingsWithNullConfirmedAtUseCompletedSessionEnd() {
        LocalDateTime visibleBefore = LocalDateTime.of(2026, 10, 2, 10, 0);
        Booking expiredBooking = persistBooking(
                student, dualRoleUser, dualRoleProfile, dualRoleSubject,
                LocalDate.of(2026, 10, 1), null);
        expiredBooking.setEndTime(LocalTime.of(9, 0));
        Booking sameDayBoundaryBooking = persistBooking(
                otherTutor, dualRoleUser, dualRoleProfile, dualRoleSubject,
                LocalDate.of(2026, 10, 2), null);
        sameDayBoundaryBooking.setEndTime(LocalTime.of(10, 0));
        Booking recentBooking = persistBooking(
                dualRoleUser, otherTutor, otherTutorProfile, otherTutorSubject,
                LocalDate.of(2026, 10, 2), null);
        recentBooking.setEndTime(LocalTime.of(10, 1));
        persistRating(expiredBooking, student, dualRoleUser, 4, false);
        persistRating(sameDayBoundaryBooking, otherTutor, dualRoleUser, 5, false);
        persistRating(recentBooking, otherTutor, dualRoleUser, 5, false);
        entityManager.flush();

        List<Rating> expiredRatings = ratingRepository.findExpiredHiddenRatings(
                BookingStatus.COMPLETED,
                visibleBefore,
                visibleBefore.toLocalDate(),
                visibleBefore.toLocalTime());

        assertThat(expiredRatings).extracting(rating -> rating.getBooking().getBookingId())
                .containsExactlyInAnyOrder(expiredBooking.getBookingId(), sameDayBoundaryBooking.getBookingId());
    }

    @Test
    void deadlineWaitsForSessionEndWhenConfirmationWasEarlier() {
        LocalDateTime visibleBefore = LocalDateTime.of(2026, 10, 1, 9, 59);
        Booking booking = persistBooking(
                student, dualRoleUser, dualRoleProfile, dualRoleSubject,
                LocalDate.of(2026, 10, 1), LocalDateTime.of(2026, 10, 1, 9, 0));
        booking.setEndTime(LocalTime.of(10, 0));
        persistRating(booking, student, dualRoleUser, 4, false);
        entityManager.flush();

        List<Rating> expiredRatings = ratingRepository.findExpiredHiddenRatings(
                BookingStatus.COMPLETED,
                visibleBefore,
                visibleBefore.toLocalDate(),
                visibleBefore.toLocalTime());

        assertThat(expiredRatings).isEmpty();
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
                }).isInstanceOf(jakarta.persistence.PersistenceException.class);
        }

    @Test
    void findBlockingBookings_includesPastUnratedCompletedForBothRolesInOrder() {
        LocalDateTime now = LocalDateTime.of(2026, 10, 20, 18, 0);
        LocalDate fromDate = LocalDate.of(2026, 10, 1);
        LocalTime fromTime = LocalTime.MIDNIGHT;

        Booking asStudent = persistGateBooking(dualRoleUser, otherTutor, otherTutorProfile, otherTutorSubject,
                LocalDate.of(2026, 10, 19), LocalTime.of(11, 0), BookingStatus.COMPLETED);
        Booking onlyOtherPartyRated = persistGateBooking(student, dualRoleUser, dualRoleProfile, dualRoleSubject,
                LocalDate.of(2026, 10, 20), LocalTime.of(16, 0), BookingStatus.COMPLETED);
        Booking asTutor = persistGateBooking(student, dualRoleUser, dualRoleProfile, dualRoleSubject,
                LocalDate.of(2026, 10, 20), LocalTime.of(17, 0), BookingStatus.COMPLETED);
        Booking atBoundary = persistGateBooking(dualRoleUser, otherTutor, otherTutorProfile, otherTutorSubject,
                LocalDate.of(2026, 10, 20), LocalTime.of(18, 0), BookingStatus.COMPLETED);

        Booking ratedByUser = persistGateBooking(student, dualRoleUser, dualRoleProfile, dualRoleSubject,
                LocalDate.of(2026, 10, 18), LocalTime.of(10, 0), BookingStatus.COMPLETED);
        persistRating(ratedByUser, dualRoleUser, student, 4, false);
        persistRating(onlyOtherPartyRated, student, dualRoleUser, 5, false);

        persistGateBooking(dualRoleUser, otherTutor, otherTutorProfile, otherTutorSubject,
                LocalDate.of(2026, 10, 20), LocalTime.of(15, 0), BookingStatus.BOOKED);
        persistGateBooking(dualRoleUser, otherTutor, otherTutorProfile, otherTutorSubject,
                LocalDate.of(2026, 9, 30), LocalTime.of(23, 0), BookingStatus.COMPLETED);
        persistGateBooking(dualRoleUser, otherTutor, otherTutorProfile, otherTutorSubject,
                LocalDate.of(2026, 10, 20), LocalTime.of(19, 0), BookingStatus.COMPLETED);
        entityManager.flush();

        List<Booking> blocking = ratingRepository.findBlockingBookings(
                dualRoleUser.getUserId(), BookingStatus.COMPLETED,
                now.toLocalDate(), now.toLocalTime(), fromDate, fromTime);

        assertThat(blocking).extracting(Booking::getBookingId).containsExactly(
                asStudent.getBookingId(),
                onlyOtherPartyRated.getBookingId(),
                asTutor.getBookingId(),
                atBoundary.getBookingId());
        assertThat(ratingRepository.existsBlockingBooking(
                dualRoleUser.getUserId(), BookingStatus.COMPLETED,
                now.toLocalDate(), now.toLocalTime(), fromDate, fromTime)).isTrue();
    }

    @Test
    void findBlockingBookings_ignoresUserWithNothingPending() {
        LocalDateTime now = LocalDateTime.of(2026, 10, 20, 18, 0);
        LocalDate fromDate = LocalDate.of(2026, 10, 1);

        Booking rated = persistGateBooking(dualRoleUser, otherTutor, otherTutorProfile, otherTutorSubject,
                LocalDate.of(2026, 10, 19), LocalTime.of(11, 0), BookingStatus.COMPLETED);
        persistRating(rated, dualRoleUser, otherTutor, 4, false);
        persistGateBooking(dualRoleUser, otherTutor, otherTutorProfile, otherTutorSubject,
                LocalDate.of(2026, 10, 20), LocalTime.of(19, 0), BookingStatus.COMPLETED);
        persistGateBooking(dualRoleUser, otherTutor, otherTutorProfile, otherTutorSubject,
                LocalDate.of(2026, 9, 30), LocalTime.of(10, 0), BookingStatus.COMPLETED);
        entityManager.flush();

        assertThat(ratingRepository.findBlockingBookings(
                dualRoleUser.getUserId(), BookingStatus.COMPLETED,
                now.toLocalDate(), now.toLocalTime(), fromDate, LocalTime.MIDNIGHT)).isEmpty();
        assertThat(ratingRepository.existsBlockingBooking(
                dualRoleUser.getUserId(), BookingStatus.COMPLETED,
                now.toLocalDate(), now.toLocalTime(), fromDate, LocalTime.MIDNIGHT)).isFalse();
    }

    @Test
    void findUpcomingUnratedCompleted_returnsEarliestFutureEnd() {
        LocalDateTime now = LocalDateTime.of(2026, 10, 20, 18, 0);
        LocalDate fromDate = LocalDate.of(2026, 10, 1);

        Booking first = persistGateBooking(dualRoleUser, otherTutor, otherTutorProfile, otherTutorSubject,
                LocalDate.of(2026, 10, 20), LocalTime.of(19, 0), BookingStatus.COMPLETED);
        Booking second = persistGateBooking(dualRoleUser, otherTutor, otherTutorProfile, otherTutorSubject,
                LocalDate.of(2026, 10, 21), LocalTime.of(10, 0), BookingStatus.COMPLETED);
        persistGateBooking(dualRoleUser, otherTutor, otherTutorProfile, otherTutorSubject,
                LocalDate.of(2026, 10, 20), LocalTime.of(17, 0), BookingStatus.COMPLETED);
        entityManager.flush();

        List<Booking> firstPage = ratingRepository.findUpcomingUnratedCompleted(
                dualRoleUser.getUserId(), BookingStatus.COMPLETED,
                now.toLocalDate(), now.toLocalTime(), fromDate, LocalTime.MIDNIGHT, PageRequest.of(0, 1));
        List<Booking> all = ratingRepository.findUpcomingUnratedCompleted(
                dualRoleUser.getUserId(), BookingStatus.COMPLETED,
                now.toLocalDate(), now.toLocalTime(), fromDate, LocalTime.MIDNIGHT, PageRequest.of(0, 10));

        assertThat(firstPage).extracting(Booking::getBookingId).containsExactly(first.getBookingId());
        assertThat(all).extracting(Booking::getBookingId)
                .containsExactly(first.getBookingId(), second.getBookingId());
    }

    @Test
    void findSubjectAggregatesByTutorId_groupsBySubjectAndAppliesCountingFilters() {
        Subject secondSubject = persist(Subject.builder().institution(institution)
                .name("Second Subject")
                .career(career)
                .build());
        TutorSubject secondTutorSubject = persistTutorSubject(dualRoleProfile, secondSubject);

        LocalDateTime completedAt = LocalDateTime.of(2026, 9, 30, 12, 0);
        // One booking per date: every booking owns its own time slot.
        Booking counts = persistBooking(student, dualRoleUser, dualRoleProfile, dualRoleSubject,
                LocalDate.of(2026, 9, 20), completedAt);
        Booking alsoCounts = persistBooking(otherTutor, dualRoleUser, dualRoleProfile, dualRoleSubject,
                LocalDate.of(2026, 9, 21), completedAt);
        Booking hidden = persistBooking(student, dualRoleUser, dualRoleProfile, dualRoleSubject,
                LocalDate.of(2026, 9, 22), completedAt);
        Booking notCompleted = persistBooking(student, dualRoleUser, dualRoleProfile, dualRoleSubject,
                LocalDate.of(2026, 9, 23), completedAt);
        notCompleted.setBookingStatus(BookingStatus.BOOKED);
        Booking crossRated = persistBooking(student, dualRoleUser, dualRoleProfile, dualRoleSubject,
                LocalDate.of(2026, 9, 24), completedAt);
        Booking otherSubjectBooking = persistBooking(student, dualRoleUser, dualRoleProfile, secondTutorSubject,
                LocalDate.of(2026, 9, 25), completedAt);

        persistRating(counts, student, dualRoleUser, 5, true);
        persistRating(alsoCounts, otherTutor, dualRoleUser, 4, true);
        persistRating(hidden, student, dualRoleUser, 1, false);
        persistRating(notCompleted, student, dualRoleUser, 1, true);
        // The tutor rating the student never counts for the tutor's subjects.
        persistRating(crossRated, dualRoleUser, student, 1, true);
        persistRating(otherSubjectBooking, student, dualRoleUser, 5, true);
        entityManager.flush();

        List<SubjectVerificationAggregate> aggregates =
                ratingRepository.findSubjectAggregatesByTutorId(dualRoleUser.getUserId());

        assertThat(aggregates).hasSize(2);
        SubjectVerificationAggregate firstSubject = aggregates.stream()
                .filter(aggregate -> aggregate.getTutorSubjectId()
                        .equals(dualRoleSubject.getTutorSubjectId()))
                .findFirst()
                .orElseThrow();
        assertThat(firstSubject.getTotalScore()).isEqualTo(9L);
        assertThat(firstSubject.getRatingCount()).isEqualTo(2L);

        SubjectVerificationAggregate otherSubject = aggregates.stream()
                .filter(aggregate -> aggregate.getTutorSubjectId()
                        .equals(secondTutorSubject.getTutorSubjectId()))
                .findFirst()
                .orElseThrow();
        assertThat(otherSubject.getTotalScore()).isEqualTo(5L);
        assertThat(otherSubject.getRatingCount()).isEqualTo(1L);
    }

    private Booking persistGateBooking(User bookingStudent, User tutor, TutorProfile tutorProfile,

            TutorSubject tutorSubject, LocalDate sessionDate, LocalTime endTime, BookingStatus status) {
        Booking booking = persistBooking(bookingStudent, tutor, tutorProfile, tutorSubject, sessionDate,
                sessionDate.atTime(endTime));
        booking.setStartTime(endTime.minusHours(1));
        booking.setEndTime(endTime);
        booking.setBookingStatus(status);
        return booking;
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
        return persistRating(booking, rater, rated, score, visible, null);
    }

    private Rating persistRating(Booking booking, User rater, User rated, int score, boolean visible,
            String comment) {
        return persist(Rating.builder()
                .booking(booking)
                .raterUser(rater)
                .ratedUser(rated)
                .score(score)
                .comment(comment)
                .ratingDate(LocalDateTime.of(2026, 10, 1, 12, 0))
                .visible(visible)
                .build());
    }

        private <T> T persist(T entity) {
                entityManager.persist(entity);
                return entity;
        }
}