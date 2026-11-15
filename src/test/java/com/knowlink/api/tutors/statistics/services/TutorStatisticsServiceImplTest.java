package com.knowlink.api.tutors.statistics.services;

import com.knowlink.api.bookings.data.enums.BookingStatus;
import com.knowlink.api.claims.data.enums.ClaimStatus;
import com.knowlink.api.payments.domain.FundsRecipient;
import com.knowlink.api.payments.domain.FundsStatus;
import com.knowlink.api.tutors.data.enums.CompensationType;
import com.knowlink.api.tutors.data.enums.TutorSubjectStatus;
import com.knowlink.api.tutors.data.models.Subject;
import com.knowlink.api.tutors.data.models.TutorProfile;
import com.knowlink.api.tutors.data.models.TutorSubject;
import com.knowlink.api.tutors.repositories.ITutorProfileRepository;
import com.knowlink.api.tutors.repositories.ITutorSubjectRepository;
import com.knowlink.api.tutors.statistics.controllers.responses.TutorStatisticsResponse;
import com.knowlink.api.tutors.statistics.data.enums.StatisticsPeriodType;
import com.knowlink.api.tutors.statistics.data.projections.ActiveClaimRow;
import com.knowlink.api.tutors.statistics.data.projections.TutorBookingRow;
import com.knowlink.api.tutors.statistics.repositories.ITutorStatisticsRepository;
import com.knowlink.api.tutors.statistics.services.implementations.TutorStatisticsCalculator;
import com.knowlink.api.tutors.statistics.services.implementations.TutorStatisticsServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Cómo el servicio arma las filas de reclamos que el panel usa para abrir el detalle desde
 * «Estado de mis fondos». El cálculo de los montos se prueba en {@link TutorStatisticsCalculatorTest}.
 */
@ExtendWith(MockitoExtension.class)
class TutorStatisticsServiceImplTest {

    private static final LocalDate SESSION_DATE = LocalDate.of(2026, 10, 12);

    @Mock
    private ITutorStatisticsRepository statisticsRepository;
    @Mock
    private ITutorProfileRepository tutorProfileRepository;
    @Mock
    private ITutorSubjectRepository tutorSubjectRepository;

    private TutorStatisticsServiceImpl service;

    private final UUID tutorUserId = UUID.randomUUID();
    private final UUID paidSubjectId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        service = new TutorStatisticsServiceImpl(
                statisticsRepository,
                tutorProfileRepository,
                tutorSubjectRepository,
                new TutorStatisticsCalculator());

        UUID tutorProfileId = UUID.randomUUID();
        when(tutorProfileRepository.findByUserId(tutorUserId))
                .thenReturn(Optional.of(TutorProfile.builder().tutorProfileId(tutorProfileId).build()));
        when(tutorSubjectRepository.findByTutorProfileId(tutorProfileId)).thenReturn(List.of(paidSubject()));
        when(statisticsRepository.findLiveSlotRows(any(), any(), any())).thenReturn(List.of());
        when(statisticsRepository.findArchivedSlotRows(any(), any(), any())).thenReturn(List.of());
        when(statisticsRepository.countByStatus(any(), any(), any(), any())).thenReturn(0L);
        when(statisticsRepository.findVisibleRatingRows(any(), any(), any())).thenReturn(List.of());
    }

    @Test
    void looksUpClaimsOnlyForBookingsWithFundsSuspendedByAClaim() {
        TutorBookingRow suspended = row(FundsStatus.SUSPENDED_BY_CLAIM, BookingStatus.NOT_CONFIRMED, SESSION_DATE);
        TutorBookingRow released = row(FundsStatus.RELEASED_TO_TUTOR, BookingStatus.COMPLETED, SESSION_DATE);
        TutorBookingRow withoutFunds = row(null, BookingStatus.BOOKED, SESSION_DATE);
        when(statisticsRepository.findBookingRows(eq(tutorUserId), any(), any()))
                .thenReturn(List.of(suspended, released, withoutFunds));
        when(statisticsRepository.findActiveClaimRows(anyCollection(), anyCollection())).thenReturn(List.of());

        service.getStatistics(tutorUserId, StatisticsPeriodType.LAST_MONTH, null, null);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Collection<UUID>> bookingIds = ArgumentCaptor.forClass(Collection.class);
        @SuppressWarnings("unchecked")
        ArgumentCaptor<Collection<ClaimStatus>> statuses = ArgumentCaptor.forClass(Collection.class);
        verify(statisticsRepository).findActiveClaimRows(bookingIds.capture(), statuses.capture());
        assertThat(bookingIds.getValue()).containsExactly(suspended.bookingId());
        // Un reclamo resuelto ya no retiene fondos: solo interesan los abiertos o en revisión.
        assertThat(statuses.getValue()).containsExactlyInAnyOrderElementsOf(ClaimStatus.ACTIVE_STATUSES);
    }

    @Test
    void doesNotLookUpClaimsWhenNoFundsAreWithheld() {
        when(statisticsRepository.findBookingRows(eq(tutorUserId), any(), any())).thenReturn(List.of(
                row(FundsStatus.RELEASED_TO_TUTOR, BookingStatus.COMPLETED, SESSION_DATE)));

        TutorStatisticsResponse response = service.getStatistics(
                tutorUserId, StatisticsPeriodType.LAST_MONTH, null, null);

        verify(statisticsRepository, never()).findActiveClaimRows(anyCollection(), anyCollection());
        assertThat(response.funds().withheldClaimIds()).isEmpty();
    }

    @Test
    void keepsTheOldestActiveClaimOfEachWithheldBooking() {
        TutorBookingRow disputed = row(FundsStatus.SUSPENDED_BY_CLAIM, BookingStatus.NOT_CONFIRMED, SESSION_DATE);
        TutorBookingRow other = row(FundsStatus.SUSPENDED_BY_CLAIM, BookingStatus.NOT_CONFIRMED, SESSION_DATE.plusDays(2));
        UUID openedTheDispute = UUID.randomUUID();
        UUID answeredLater = UUID.randomUUID();
        UUID otherBookingClaim = UUID.randomUUID();
        when(statisticsRepository.findBookingRows(eq(tutorUserId), any(), any()))
                .thenReturn(List.of(disputed, other));
        // El repositorio los devuelve del más antiguo al más nuevo.
        when(statisticsRepository.findActiveClaimRows(anyCollection(), anyCollection())).thenReturn(List.of(
                new ActiveClaimRow(disputed.bookingId(), openedTheDispute),
                new ActiveClaimRow(disputed.bookingId(), answeredLater),
                new ActiveClaimRow(other.bookingId(), otherBookingClaim)));

        TutorStatisticsResponse response = service.getStatistics(
                tutorUserId, StatisticsPeriodType.LAST_MONTH, null, null);

        // Una sola referencia por sesión (la que abrió la disputa), la sesión más reciente primero.
        assertThat(response.funds().withheldByClaim().sessions()).isEqualTo(2);
        assertThat(response.funds().withheldClaimIds()).containsExactly(otherBookingClaim, openedTheDispute);
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private TutorSubject paidSubject() {
        return TutorSubject.builder()
                .tutorSubjectId(paidSubjectId)
                .subject(Subject.builder().name("Análisis de Sistemas").build())
                .compensationType(CompensationType.PAID)
                .tutorSubjectStatus(TutorSubjectStatus.ACTIVE)
                .active(true)
                .pricePerHour(new BigDecimal("15000"))
                .build();
    }

    private TutorBookingRow row(FundsStatus fundsStatus, BookingStatus status, LocalDate date) {
        boolean hasFunds = fundsStatus != null;
        return new TutorBookingRow(UUID.randomUUID(), date, LocalTime.of(9, 0), LocalTime.of(10, 0), status,
                UUID.randomUUID(), paidSubjectId, null, null, fundsStatus,
                hasFunds ? FundsRecipient.TUTOR : null,
                hasFunds ? new BigDecimal("1030.00") : null,
                hasFunds ? new BigDecimal("1000.00") : null);
    }
}
