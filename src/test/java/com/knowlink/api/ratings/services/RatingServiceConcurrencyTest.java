package com.knowlink.api.ratings.services;

import com.knowlink.api.bookings.data.enums.BookingStatus;
import com.knowlink.api.bookings.data.models.Booking;
import com.knowlink.api.bookings.repositories.IBookingRepository;
import com.knowlink.api.bookings.validations.IBookingValidationService;
import com.knowlink.api.ratings.controllers.requests.CreateRatingRequest;
import com.knowlink.api.ratings.controllers.responses.RatingResponse;
import com.knowlink.api.ratings.data.models.Rating;
import com.knowlink.api.ratings.repositories.IRatingRepository;
import com.knowlink.api.ratings.services.interfaces.IRatingService;
import com.knowlink.api.security.enums.Role;
import com.knowlink.api.timeslot.data.enums.SlotStatus;
import com.knowlink.api.timeslot.data.models.TimeSlot;
import com.knowlink.api.timeslot.repositories.ITimeSlotRepository;
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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;

@SpringBootTest(properties = "confirmation-token.encryption-key=MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=")
@ActiveProfiles("test")
class RatingServiceConcurrencyTest {

    @Autowired
    private IRatingService ratingService;
    @Autowired
    private IBookingRepository bookingRepository;
    @Autowired
    private IRatingRepository ratingRepository;
    @Autowired
    private IUserRepository userRepository;
    @Autowired
    private ICareerRepository careerRepository;
    @Autowired
    private ISubjectRepository subjectRepository;
    @Autowired
    private ITutorProfileRepository tutorProfileRepository;
    @Autowired
    private ITutorSubjectRepository tutorSubjectRepository;
    @Autowired
    private ITimeSlotRepository timeSlotRepository;
    @MockitoBean
    private IBookingValidationService bookingValidationService;

    private User student;
    private User tutor;
    private Booking booking;

    @BeforeEach
    void setUp() {
        student = user("student", Role.STUDENT);
        tutor = user("tutor", Role.TUTOR);
        Career career = careerRepository.save(Career.builder().name("Career " + UUID.randomUUID()).build());
        TutorProfile tutorProfile = tutorProfileRepository.save(
                TutorProfile.builder().user(tutor).career(career).build());
        Subject subject = subjectRepository.save(Subject.builder()
                .name("Subject " + UUID.randomUUID())
                .career(career)
                .isBasic(true)
                .build());
        TutorSubject tutorSubject = tutorSubjectRepository.save(TutorSubject.builder()
                .tutorProfile(tutorProfile)
                .subject(subject)
                .modality(Modality.VIRTUAL)
                .compensationType(CompensationType.PAID)
                .tutorSubjectStatus(TutorSubjectStatus.ACTIVE)
                .build());
        LocalDate sessionDate = LocalDate.now();
        TimeSlot timeSlot = timeSlotRepository.save(TimeSlot.builder()
                .date(sessionDate)
                .startTime(LocalTime.now().minusHours(1))
                .endTime(LocalTime.now())
                .status(SlotStatus.OCCUPIED)
                .tutorProfileId(tutorProfile.getTutorProfileId())
                .build());
        booking = bookingRepository.save(Booking.builder()
                .amount(BigDecimal.TEN)
                .sessionDate(sessionDate)
                .startTime(timeSlot.getStartTime())
                .endTime(timeSlot.getEndTime())
                .modality(Modality.VIRTUAL)
                .bookingStatus(BookingStatus.COMPLETED)
                .confirmedAt(LocalDateTime.now())
                .timeSlot(timeSlot)
                .tutorSubject(tutorSubject)
                .student(student)
                .tutor(tutor)
                .build());
    }

    @Test
    void concurrentParticipantRatingsAreSerializedByBookingLock() throws Exception {
        CountDownLatch firstValidationEntered = new CountDownLatch(1);
        CountDownLatch releaseFirstValidation = new CountDownLatch(1);
        CountDownLatch secondValidationEntered = new CountDownLatch(1);
        AtomicInteger validationCalls = new AtomicInteger();
        doAnswer(invocation -> {
            if (validationCalls.getAndIncrement() == 0) {
                firstValidationEntered.countDown();
                if (!releaseFirstValidation.await(5, TimeUnit.SECONDS)) {
                    throw new IllegalStateException("Timed out waiting to release the first rating request");
                }
            } else {
                secondValidationEntered.countDown();
            }
            return null;
        }).when(bookingValidationService).validateOwnership(any(Booking.class), any(UUID.class));

        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<RatingResponse> firstResponse = executor.submit(() -> ratingService.submitRating(
                    student.getUserId(), booking.getBookingId(), new CreateRatingRequest(5, "Buena clase")));
            assertThat(firstValidationEntered.await(5, TimeUnit.SECONDS)).isTrue();

            CountDownLatch secondRequestStarted = new CountDownLatch(1);
            Future<RatingResponse> secondResponse = executor.submit(() -> {
                secondRequestStarted.countDown();
                return ratingService.submitRating(
                        tutor.getUserId(), booking.getBookingId(), new CreateRatingRequest(4, null));
            });
            assertThat(secondRequestStarted.await(5, TimeUnit.SECONDS)).isTrue();
            assertThat(secondValidationEntered.await(300, TimeUnit.MILLISECONDS)).isFalse();

            releaseFirstValidation.countDown();
            assertThat(firstResponse.get(5, TimeUnit.SECONDS).visible()).isFalse();
            assertThat(secondResponse.get(5, TimeUnit.SECONDS).visible()).isTrue();
            assertThat(secondValidationEntered.getCount()).isZero();

            List<Rating> savedRatings = ratingRepository.findByBookingBookingId(booking.getBookingId());
            assertThat(savedRatings).hasSize(2).allMatch(Rating::isVisible);
        } finally {
            releaseFirstValidation.countDown();
            executor.shutdownNow();
        }
    }

    private User user(String name, Role role) {
        return userRepository.save(User.builder()
                .fullName(name)
                .email(name + "." + UUID.randomUUID() + "@ratings.test")
                .password("hashed")
                .role(role)
                .accountStatus(AccountStatus.ACTIVE)
                .build());
    }
}