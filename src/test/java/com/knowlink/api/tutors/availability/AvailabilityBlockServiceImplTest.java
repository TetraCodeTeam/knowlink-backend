package com.knowlink.api.tutors.availability;

import com.knowlink.api.exceptions.custom_exceptions.ValidationException;
import com.knowlink.api.timeslot.data.mappers.TimeSlotGenerator;
import com.knowlink.api.timeslot.data.models.TimeSlot;
import com.knowlink.api.timeslot.repositories.ITimeSlotRepository;
import com.knowlink.api.tutors.availability.controllers.requests.AvailabilityBlockRequest;
import com.knowlink.api.tutors.availability.controllers.responses.AvailabilityBlockResponse;
import com.knowlink.api.tutors.availability.data.mappers.AvailabilityBlockMapper;
import com.knowlink.api.tutors.availability.data.models.AvailabilityBlock;
import com.knowlink.api.tutors.availability.repositories.IAvailabilityBlockRepository;
import com.knowlink.api.tutors.availability.repositories.IAvailabilityWeekCustomizationRepository;
import com.knowlink.api.tutors.availability.services.implementations.AvailabilityBlockServiceImpl;
import com.knowlink.api.tutors.availability.utils.AvailabilityConstants;
import com.knowlink.api.tutors.availability.validations.AvailabilityBlockValidationServiceImpl;
import com.knowlink.api.tutors.data.models.TutorProfile;
import com.knowlink.api.tutors.validations.ITutorProfileValidationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.temporal.TemporalAdjusters;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Reemplazo semanal de la disponibilidad: solo los bloques con reservas asociadas quedan protegidos; el resto
 * de la semana se reemplaza con normalidad. Las fechas son futuras para no depender del reloj.
 */
@ExtendWith(MockitoExtension.class)
class AvailabilityBlockServiceImplTest {

    private static final LocalDate WEEK_START =
            LocalDate.now().with(TemporalAdjusters.next(DayOfWeek.MONDAY)).plusWeeks(2);
    private static final LocalDate WEEK_END = WEEK_START.plusDays(6);
    private static final LocalDate TUESDAY = WEEK_START.plusDays(1);
    private static final LocalDate WEDNESDAY = WEEK_START.plusDays(2);

    @Mock
    private IAvailabilityBlockRepository blockRepository;
    @Mock
    private IAvailabilityWeekCustomizationRepository customizationRepository;
    @Mock
    private ITutorProfileValidationService tutorProfileValidationService;
    @Mock
    private ITimeSlotRepository timeSlotRepository;

    private AvailabilityBlockServiceImpl service;

    private final UUID tutorUserId = UUID.randomUUID();
    private final UUID tutorProfileId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        TutorProfile profile = TutorProfile.builder().tutorProfileId(tutorProfileId).build();
        when(tutorProfileValidationService.findTutorProfileOrThrowException(tutorUserId)).thenReturn(profile);
        service = new AvailabilityBlockServiceImpl(blockRepository, customizationRepository,
                tutorProfileValidationService, new AvailabilityBlockValidationServiceImpl(),
                new AvailabilityBlockMapper(), timeSlotRepository, new TimeSlotGenerator());
    }

    @Test
    @DisplayName("Sin bloques con reservas, se reemplaza toda la semana: se borran los libres y se guardan los nuevos")
    void replacesTheWholeWeekWhenNothingIsLocked() {
        AvailabilityBlock stored = block(TUESDAY, 10, 14);
        when(blockRepository.findInRange(eq(tutorProfileId), eq(WEEK_START), eq(WEEK_END), any(), any()))
                .thenReturn(List.of(stored));

        List<AvailabilityBlockResponse> response = service.replaceWeekBlocks(
                tutorUserId, WEEK_START, WEEK_END, List.of(request(TUESDAY, 10, 14, false)));

        // Primero los slots libres sin reservas y recién después los bloques que quedaron sin slots.
        InOrder order = inOrder(timeSlotRepository, blockRepository);
        order.verify(timeSlotRepository).deleteAvailableInRange(tutorProfileId, WEEK_START, WEEK_END);
        order.verify(blockRepository).deleteWithoutSlotsInRange(tutorProfileId, WEEK_START, WEEK_END);
        order.verify(blockRepository).saveAll(any());

        List<AvailabilityBlock> saved = savedBlocks();
        assertThat(saved).hasSize(1);
        assertThat(saved.get(0).getDate()).isEqualTo(TUESDAY);
        assertThat(saved.get(0).getStartTime()).isEqualTo(LocalTime.of(10, 0));
        assertThat(saved.get(0).getEndTime()).isEqualTo(LocalTime.of(14, 0));
        assertThat(savedSlots()).hasSize(1);
        assertThat(savedSlots().get(0).getAssignedBlock()).isSameAs(saved.get(0));
        assertThat(response).hasSize(1);
        assertThat(response.get(0).date()).isEqualTo(TUESDAY);
    }

    @Test
    @DisplayName("Un bloque con reservas que vuelve sin cambios se conserva: no se vuelve a crear")
    void aLockedBlockReturnedUnchangedIsNotRecreated() {
        lock(WEEK_START, WEEK_END, block(TUESDAY, 16, 20));

        service.replaceWeekBlocks(tutorUserId, WEEK_START, WEEK_END,
                List.of(request(TUESDAY, 16, 20, false), request(WEDNESDAY, 10, 14, false)));

        assertThat(savedBlocks()).extracting(AvailabilityBlock::getDate).containsExactly(WEDNESDAY);
        assertThat(savedSlots()).hasSize(1);
    }

    @Test
    @DisplayName("Quitar un bloque con reservas se rechaza antes de borrar o guardar nada")
    void removingALockedBlockIsRejectedBeforeTouchingAnything() {
        lock(WEEK_START, WEEK_END, block(TUESDAY, 16, 20));

        assertThatThrownBy(() -> service.replaceWeekBlocks(tutorUserId, WEEK_START, WEEK_END,
                List.of(request(WEDNESDAY, 10, 14, false))))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("tiene reservas asociadas");

        verify(timeSlotRepository, never()).deleteAvailableInRange(any(), any(), any());
        verify(blockRepository, never()).deleteWithoutSlotsInRange(any(), any(), any());
        verify(blockRepository, never()).saveAll(any());
        verify(timeSlotRepository, never()).saveAll(any());
    }

    @Test
    @DisplayName("Un bloque nuevo pegado a uno con reservas no se fusiona con él")
    void aNewBlockContiguousToALockedOneIsNotMergedWithIt() {
        lock(WEEK_START, WEEK_END, block(TUESDAY, 16, 20));

        service.replaceWeekBlocks(tutorUserId, WEEK_START, WEEK_END,
                List.of(request(TUESDAY, 16, 20, false), request(TUESDAY, 20, 22, false)));

        List<AvailabilityBlock> saved = savedBlocks();
        assertThat(saved).hasSize(1);
        assertThat(saved.get(0).getStartTime()).isEqualTo(LocalTime.of(20, 0));
        assertThat(saved.get(0).getEndTime()).isEqualTo(LocalTime.of(22, 0));
    }

    @Test
    @DisplayName("Una semana vacía también se puede guardar: se borra y no se crea nada")
    void clearingTheWeekDeletesWithoutCreatingAnything() {
        service.replaceWeekBlocks(tutorUserId, WEEK_START, WEEK_END, List.of());

        verify(timeSlotRepository).deleteAvailableInRange(tutorProfileId, WEEK_START, WEEK_END);
        verify(blockRepository).deleteWithoutSlotsInRange(tutorProfileId, WEEK_START, WEEK_END);
        assertThat(savedBlocks()).isEmpty();
    }

    // ── Repetición semanal ───────────────────────────────────────────────────

    @Test
    @DisplayName("Con repetición, una semana futura con bloques con reservas se salta: ni se limpia ni se copia")
    void repeatingSkipsFutureWeeksThatHaveLockedBlocks() {
        LocalDate lockedWeekStart = WEEK_START.plusWeeks(2);
        LocalDate lockedWeekEnd = WEEK_END.plusWeeks(2);
        lock(lockedWeekStart, lockedWeekEnd, block(lockedWeekStart.plusDays(1), 16, 20));

        service.replaceWeekBlocks(tutorUserId, WEEK_START, WEEK_END, List.of(request(TUESDAY, 10, 14, true)));

        // La semana protegida no se toca; las demás sí.
        verify(timeSlotRepository, never()).deleteAvailableInRange(tutorProfileId, lockedWeekStart, lockedWeekEnd);
        verify(blockRepository, never()).deleteWithoutSlotsInRange(tutorProfileId, lockedWeekStart, lockedWeekEnd);
        verify(timeSlotRepository).deleteAvailableInRange(tutorProfileId, WEEK_START.plusWeeks(1), WEEK_END.plusWeeks(1));
        verify(blockRepository).deleteWithoutSlotsInRange(tutorProfileId, WEEK_START.plusWeeks(1), WEEK_END.plusWeeks(1));

        // El bloque original y las copias de todas las semanas menos la protegida.
        List<AvailabilityBlock> saved = savedBlocks();
        assertThat(saved).hasSize(1 + AvailabilityConstants.REPEAT_WEEKS_AHEAD - 1);
        assertThat(saved).extracting(AvailabilityBlock::getDate)
                .doesNotContain(TUESDAY.plusWeeks(2))
                .contains(TUESDAY, TUESDAY.plusWeeks(1), TUESDAY.plusWeeks(3));
    }

    @Test
    @DisplayName("Con repetición, una semana futura personalizada también se salta")
    void repeatingSkipsCustomizedFutureWeeks() {
        lenient().when(customizationRepository.existsByTutorProfile_TutorProfileIdAndWeekStart(
                tutorProfileId, WEEK_START.plusWeeks(1))).thenReturn(true);

        service.replaceWeekBlocks(tutorUserId, WEEK_START, WEEK_END, List.of(request(TUESDAY, 10, 14, true)));

        verify(timeSlotRepository, never())
                .deleteAvailableInRange(tutorProfileId, WEEK_START.plusWeeks(1), WEEK_END.plusWeeks(1));
        assertThat(savedBlocks()).extracting(AvailabilityBlock::getDate)
                .doesNotContain(TUESDAY.plusWeeks(1))
                .contains(TUESDAY, TUESDAY.plusWeeks(2));
    }

    @Test
    @DisplayName("Un bloque con reservas no genera copias aunque se pida repetir: conserva su propia configuración")
    void aLockedBlockDoesNotGenerateRepeatCopies() {
        lock(WEEK_START, WEEK_END, block(TUESDAY, 16, 20));

        service.replaceWeekBlocks(tutorUserId, WEEK_START, WEEK_END,
                List.of(request(TUESDAY, 16, 20, true), request(WEDNESDAY, 10, 14, true)));

        // Solo el bloque nuevo (miércoles) se repite; el martes con reservas queda como estaba.
        assertThat(savedBlocks()).extracting(AvailabilityBlock::getDate)
                .contains(WEDNESDAY, WEDNESDAY.plusWeeks(1))
                .doesNotContain(TUESDAY, TUESDAY.plusWeeks(1));
    }

    @Test
    @DisplayName("Sin repetición no se consultan las semanas futuras")
    void withoutRepeatingFutureWeeksAreNotInspected() {
        service.replaceWeekBlocks(tutorUserId, WEEK_START, WEEK_END, List.of(request(TUESDAY, 10, 14, false)));

        verify(blockRepository, times(1)).findUpcomingLockedByBookings(any(), any(), any(), any(), any());
        verify(timeSlotRepository, times(1)).deleteAvailableInRange(any(), any(), any());
        assertThat(savedBlocks()).hasSize(1);
    }

    // ── Auxiliares ───────────────────────────────────────────────────────────

    private void lock(LocalDate from, LocalDate to, AvailabilityBlock... locked) {
        // lenient: el servicio consulta varias semanas y cada test solo declara la que le importa.
        lenient().when(blockRepository.findUpcomingLockedByBookings(eq(tutorProfileId), eq(from), eq(to), any(), any()))
                .thenReturn(List.of(locked));
    }

    @SuppressWarnings("unchecked")
    private List<AvailabilityBlock> savedBlocks() {
        ArgumentCaptor<List<AvailabilityBlock>> captor = ArgumentCaptor.forClass(List.class);
        verify(blockRepository).saveAll(captor.capture());
        return captor.getValue();
    }

    @SuppressWarnings("unchecked")
    private List<TimeSlot> savedSlots() {
        ArgumentCaptor<List<TimeSlot>> captor = ArgumentCaptor.forClass(List.class);
        verify(timeSlotRepository).saveAll(captor.capture());
        return captor.getValue();
    }

    private AvailabilityBlock block(LocalDate date, int startHour, int endHour) {
        return AvailabilityBlock.builder()
                .availabilityBlockId(UUID.randomUUID())
                .date(date)
                .startTime(LocalTime.of(startHour, 0))
                .endTime(LocalTime.of(endHour, 0))
                .build();
    }

    private AvailabilityBlockRequest request(LocalDate date, int startHour, int endHour, boolean repeat) {
        return new AvailabilityBlockRequest(date, LocalTime.of(startHour, 0), LocalTime.of(endHour, 0), repeat);
    }
}
