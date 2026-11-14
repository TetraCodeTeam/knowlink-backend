package com.knowlink.api.tutors.availability.repositories;

import com.knowlink.api.bookings.data.enums.BookingStatus;
import com.knowlink.api.bookings.data.models.Booking;
import com.knowlink.api.security.enums.Role;
import com.knowlink.api.testsupport.CatalogFixtures;
import com.knowlink.api.timeslot.data.enums.SlotStatus;
import com.knowlink.api.timeslot.data.models.TimeSlot;
import com.knowlink.api.timeslot.repositories.ITimeSlotRepository;
import com.knowlink.api.tutors.availability.data.models.AvailabilityBlock;
import com.knowlink.api.tutors.data.enums.CompensationType;
import com.knowlink.api.tutors.data.enums.Modality;
import com.knowlink.api.tutors.data.enums.TutorSubjectStatus;
import com.knowlink.api.tutors.data.models.Career;
import com.knowlink.api.tutors.data.models.Institution;
import com.knowlink.api.tutors.data.models.Subject;
import com.knowlink.api.tutors.data.models.TutorProfile;
import com.knowlink.api.tutors.data.models.TutorSubject;
import com.knowlink.api.tutors.repositories.IInstitutionRepository;
import com.knowlink.api.users.data.enums.AccountStatus;
import com.knowlink.api.users.data.models.User;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Las dos consultas que sostienen el reemplazo semanal de la disponibilidad. Reservar no parte ni ocupa el
 * TimeSlot: la reserva apunta al slot de su bloque por FK, así que borrar ese bloque violaría
 * {@code time_slot.availability_block_id}. Por eso se verifica contra una base que hace cumplir las FK.
 */
@DataJpaTest
@ActiveProfiles("test")
class AvailabilityBlockRepositoryTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 10, 20); // martes
    private static final LocalTime NOW = LocalTime.of(12, 0);
    private static final LocalDate FROM = LocalDate.of(2026, 10, 19); // lunes
    private static final LocalDate TO = LocalDate.of(2026, 10, 25); // domingo

    @Autowired
    private IAvailabilityBlockRepository blockRepository;
    @Autowired
    private ITimeSlotRepository timeSlotRepository;
    @Autowired
    private IInstitutionRepository institutionRepository;
    @PersistenceContext
    private EntityManager entityManager;

    private User student;
    private User tutor;
    private TutorProfile tutorProfile;
    private TutorProfile otherTutorProfile;
    private TutorSubject tutorSubject;

    @BeforeEach
    void setUp() {
        Institution institution = CatalogFixtures.saveInstitution(institutionRepository, "Test " + System.nanoTime());
        Career career = persist(Career.builder().institution(institution).name("Career " + UUID.randomUUID()).build());
        student = persistUser("student", Role.STUDENT);
        tutor = persistUser("tutor", Role.TUTOR);
        tutorProfile = persist(TutorProfile.builder().user(tutor).career(career).build());
        otherTutorProfile = persist(TutorProfile.builder().user(persistUser("other", Role.TUTOR)).career(career).build());
        Subject subject = persist(Subject.builder().institution(institution).name("Subject").career(career).build());
        tutorSubject = persist(TutorSubject.builder()
                .tutorProfile(tutorProfile)
                .subject(subject)
                .modality(Modality.VIRTUAL)
                .compensationType(CompensationType.PAID)
                .tutorSubjectStatus(TutorSubjectStatus.ACTIVE)
                .build());
    }

    // ── findUpcomingLockedByBookings ─────────────────────────────────────────

    @Test
    @DisplayName("Un bloque futuro con una reserva en su slot está bloqueado")
    void aFutureBlockWithABookingIsLocked() {
        AvailabilityBlock locked = bookedBlock(LocalDate.of(2026, 10, 22), 16, 20, BookingStatus.BOOKED);
        entityManager.flush();

        assertThat(lockedIds()).containsExactly(locked.getAvailabilityBlockId());
    }

    @ParameterizedTest
    @EnumSource(BookingStatus.class)
    @DisplayName("Una reserva de cualquier estado bloquea: la FK no distingue entre activas y canceladas")
    void aBookingOfAnyStatusLocksItsBlock(BookingStatus status) {
        AvailabilityBlock block = bookedBlock(LocalDate.of(2026, 10, 22), 16, 20, status);
        entityManager.flush();

        assertThat(lockedIds()).containsExactly(block.getAvailabilityBlockId());
    }

    @Test
    @DisplayName("Un bloque futuro sin reservas no está bloqueado")
    void aFutureBlockWithoutBookingsIsNotLocked() {
        AvailabilityBlock free = block(tutorProfile, LocalDate.of(2026, 10, 23), 10, 14);
        slot(free, SlotStatus.AVAILABLE);
        entityManager.flush();

        assertThat(lockedIds()).isEmpty();
    }

    @Test
    @DisplayName("Un slot que no está libre bloquea aunque no tenga reserva")
    void aSlotThatIsNotAvailableLocksItsBlock() {
        AvailabilityBlock block = block(tutorProfile, LocalDate.of(2026, 10, 24), 10, 14);
        slot(block, SlotStatus.OCCUPIED);
        entityManager.flush();

        assertThat(lockedIds()).containsExactly(block.getAvailabilityBlockId());
    }

    @Test
    @DisplayName("Un bloque ya vencido no se considera, aunque tenga reservas (el editor no lo muestra)")
    void aFullyPastBlockIsIgnored() {
        bookedBlock(LocalDate.of(2026, 10, 19), 16, 20, BookingStatus.BOOKED); // ayer
        bookedBlock(TODAY, 8, 11, BookingStatus.BOOKED); // hoy, terminó a las 11:00 (< 12:00)
        entityManager.flush();

        assertThat(lockedIds()).isEmpty();
    }

    @Test
    @DisplayName("Un bloque de hoy al que todavía le queda tiempo sí está bloqueado")
    void aBlockOfTodayThatHasNotEndedIsLocked() {
        AvailabilityBlock running = bookedBlock(TODAY, 11, 18, BookingStatus.BOOKED);
        entityManager.flush();

        assertThat(lockedIds()).containsExactly(running.getAvailabilityBlockId());
    }

    @Test
    @DisplayName("Un bloque que termina justo ahora ya no cuenta: el límite es exclusivo")
    void aBlockEndingExactlyNowIsIgnored() {
        bookedBlock(TODAY, 8, 12, BookingStatus.BOOKED);
        entityManager.flush();

        assertThat(lockedIds()).isEmpty();
    }

    @Test
    @DisplayName("Solo cuenta el rango pedido y el tutor pedido")
    void onlyTheRequestedRangeAndTutorCount() {
        bookedBlock(LocalDate.of(2026, 10, 26), 16, 20, BookingStatus.BOOKED); // fuera del rango
        AvailabilityBlock otherTutorsBlock = block(otherTutorProfile, LocalDate.of(2026, 10, 22), 16, 20);
        slot(otherTutorsBlock, SlotStatus.OCCUPIED); // otro tutor, bloqueado para él pero no para este
        entityManager.flush();

        assertThat(lockedIds()).isEmpty();
    }

    @Test
    @DisplayName("Los bloqueados se devuelven ordenados por fecha y hora de inicio")
    void lockedBlocksAreOrderedByDateAndStart() {
        AvailabilityBlock thursdayLate = bookedBlock(LocalDate.of(2026, 10, 22), 16, 20, BookingStatus.BOOKED);
        AvailabilityBlock thursdayEarly = bookedBlock(LocalDate.of(2026, 10, 22), 9, 12, BookingStatus.BOOKED);
        AvailabilityBlock wednesday = bookedBlock(LocalDate.of(2026, 10, 21), 14, 18, BookingStatus.BOOKED);
        entityManager.flush();

        assertThat(lockedIds()).containsExactly(wednesday.getAvailabilityBlockId(),
                thursdayEarly.getAvailabilityBlockId(), thursdayLate.getAvailabilityBlockId());
    }

    // ── deleteWithoutSlotsInRange ────────────────────────────────────────────

    @Test
    @DisplayName("Borra los bloques sin slots del rango y conserva los que todavía respaldan uno")
    void deletesOnlyBlocksWithoutSlots() {
        AvailabilityBlock withoutSlots = block(tutorProfile, LocalDate.of(2026, 10, 22), 10, 14);
        AvailabilityBlock withSlot = block(tutorProfile, LocalDate.of(2026, 10, 23), 10, 14);
        slot(withSlot, SlotStatus.AVAILABLE);
        AvailabilityBlock outOfRange = block(tutorProfile, LocalDate.of(2026, 10, 27), 10, 14);
        AvailabilityBlock otherTutors = block(otherTutorProfile, LocalDate.of(2026, 10, 22), 10, 14);
        entityManager.flush();

        blockRepository.deleteWithoutSlotsInRange(tutorProfile.getTutorProfileId(), FROM, TO);
        entityManager.flush();
        entityManager.clear();

        assertThat(blockRepository.findAll()).extracting(AvailabilityBlock::getAvailabilityBlockId)
                .contains(withSlot.getAvailabilityBlockId(), outOfRange.getAvailabilityBlockId(),
                        otherTutors.getAvailabilityBlockId())
                .doesNotContain(withoutSlots.getAvailabilityBlockId());
    }

    @Test
    @DisplayName("Reemplazar la semana no viola la FK: se borra lo libre y se conserva el bloque de la reserva")
    void replacingTheWeekKeepsTheBlockOfABookedSlot() {
        AvailabilityBlock free = block(tutorProfile, LocalDate.of(2026, 10, 22), 10, 14);
        TimeSlot freeSlot = slot(free, SlotStatus.AVAILABLE);
        AvailabilityBlock booked = bookedBlock(LocalDate.of(2026, 10, 23), 16, 20, BookingStatus.BOOKED);
        entityManager.flush();
        UUID bookedSlotId = slotIdOf(booked);

        // Es exactamente lo que hace replaceWeekBlocks: primero los slots libres, después los bloques sin slots.
        timeSlotRepository.deleteAvailableInRange(tutorProfile.getTutorProfileId(), FROM, TO);
        blockRepository.deleteWithoutSlotsInRange(tutorProfile.getTutorProfileId(), FROM, TO);
        entityManager.flush();
        entityManager.clear();

        assertThat(entityManager.find(AvailabilityBlock.class, free.getAvailabilityBlockId())).isNull();
        assertThat(entityManager.find(TimeSlot.class, freeSlot.getTimeSlotId())).isNull();
        assertThat(entityManager.find(AvailabilityBlock.class, booked.getAvailabilityBlockId())).isNotNull();
        assertThat(entityManager.find(TimeSlot.class, bookedSlotId)).isNotNull();
        assertThat(entityManager.createQuery("SELECT COUNT(b) FROM Booking b", Long.class).getSingleResult())
                .isEqualTo(1L);
    }

    // ── Auxiliares ───────────────────────────────────────────────────────────

    /** Ids de los bloques bloqueados (las entidades no definen equals, y tras limpiar el contexto son otras instancias). */
    private List<UUID> lockedIds() {
        entityManager.clear();
        return blockRepository.findUpcomingLockedByBookings(tutorProfile.getTutorProfileId(), FROM, TO, TODAY, NOW)
                .stream()
                .map(AvailabilityBlock::getAvailabilityBlockId)
                .toList();
    }

    private AvailabilityBlock bookedBlock(LocalDate date, int startHour, int endHour, BookingStatus status) {
        AvailabilityBlock block = block(tutorProfile, date, startHour, endHour);
        TimeSlot slot = slot(block, SlotStatus.AVAILABLE);
        persist(Booking.builder()
                .amount(BigDecimal.TEN)
                .sessionDate(date)
                .startTime(LocalTime.of(startHour, 0))
                .endTime(LocalTime.of(startHour + 1, 0))
                .modality(Modality.VIRTUAL)
                .bookingStatus(status)
                .timeSlot(slot)
                .tutorSubject(tutorSubject)
                .student(student)
                .tutor(tutor)
                .build());
        return block;
    }

    private AvailabilityBlock block(TutorProfile profile, LocalDate date, int startHour, int endHour) {
        return persist(AvailabilityBlock.builder()
                .tutorProfile(profile)
                .date(date)
                .startTime(LocalTime.of(startHour, 0))
                .endTime(LocalTime.of(endHour, 0))
                .build());
    }

    private TimeSlot slot(AvailabilityBlock block, SlotStatus status) {
        return persist(TimeSlot.builder()
                .date(block.getDate())
                .startTime(block.getStartTime())
                .endTime(block.getEndTime())
                .status(status)
                .tutorProfileId(block.getTutorProfile().getTutorProfileId())
                .assignedBlock(block)
                .build());
    }

    private UUID slotIdOf(AvailabilityBlock block) {
        return entityManager.createQuery(
                        "SELECT ts.timeSlotId FROM TimeSlot ts WHERE ts.assignedBlock.availabilityBlockId = :id", UUID.class)
                .setParameter("id", block.getAvailabilityBlockId())
                .getSingleResult();
    }

    private User persistUser(String localName, Role role) {
        return persist(User.builder()
                .fullName(localName)
                .email(localName + "." + UUID.randomUUID() + "@availability.test")
                .password("hashed")
                .role(role)
                .accountStatus(AccountStatus.ACTIVE)
                .build());
    }

    private <T> T persist(T entity) {
        entityManager.persist(entity);
        return entity;
    }
}
