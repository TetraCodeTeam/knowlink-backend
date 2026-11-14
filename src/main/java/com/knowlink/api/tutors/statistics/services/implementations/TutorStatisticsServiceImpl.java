package com.knowlink.api.tutors.statistics.services.implementations;

import com.knowlink.api.bookings.data.enums.BookingStatus;
import com.knowlink.api.claims.data.enums.ClaimStatus;
import com.knowlink.api.exceptions.custom_exceptions.ResourceNotFoundException;
import com.knowlink.api.payments.domain.FundsStatus;
import com.knowlink.api.shared.utils.AppTimeZone;
import com.knowlink.api.tutors.data.models.TutorProfile;
import com.knowlink.api.tutors.repositories.ITutorProfileRepository;
import com.knowlink.api.tutors.repositories.ITutorSubjectRepository;
import com.knowlink.api.tutors.statistics.controllers.responses.TutorStatisticsResponse;
import com.knowlink.api.tutors.statistics.data.enums.StatisticsPeriodType;
import com.knowlink.api.tutors.statistics.data.projections.ActiveClaimRow;
import com.knowlink.api.tutors.statistics.data.projections.TutorBookingRow;
import com.knowlink.api.tutors.statistics.data.projections.TutorSlotRow;
import com.knowlink.api.tutors.statistics.data.projections.TutorStatisticsInput;
import com.knowlink.api.tutors.statistics.repositories.ITutorStatisticsRepository;
import com.knowlink.api.tutors.statistics.services.interfaces.ITutorStatisticsService;
import com.knowlink.api.tutors.statistics.utils.StatisticsPeriod;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class TutorStatisticsServiceImpl implements ITutorStatisticsService {

    private final ITutorStatisticsRepository statisticsRepository;
    private final ITutorProfileRepository tutorProfileRepository;
    private final ITutorSubjectRepository tutorSubjectRepository;
    private final TutorStatisticsCalculator calculator;

    @Override
    @Transactional(readOnly = true)
    public TutorStatisticsResponse getStatistics(
            UUID tutorUserId, StatisticsPeriodType periodType, LocalDate from, LocalDate to) {
        StatisticsPeriod period = StatisticsPeriod.resolve(
                periodType, from, to, LocalDate.now(AppTimeZone.ZONE));
        StatisticsPeriod previous = period.previous();

        TutorProfile tutorProfile = tutorProfileRepository.findByUserId(tutorUserId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "TUTOR_PROFILE_NOT_FOUND",
                        "El perfil de tutor no existe.",
                        "Tutor profile not found for user: " + tutorUserId));
        UUID tutorProfileId = tutorProfile.getTutorProfileId();

        // Horas ofrecidas = horarios vigentes + los ya vencidos que el job de limpieza archivó.
        List<TutorSlotRow> offeredSlots = new ArrayList<>(
                statisticsRepository.findLiveSlotRows(tutorProfileId, period.from(), period.to()));
        offeredSlots.addAll(statisticsRepository.findArchivedSlotRows(tutorProfileId, period.from(), period.to()));

        List<TutorBookingRow> bookings = statisticsRepository.findBookingRows(tutorUserId, period.from(), period.to());

        TutorStatisticsInput input = new TutorStatisticsInput(
                period,
                statisticsRepository.countByStatus(
                        tutorUserId, previous.from(), previous.to(), BookingStatus.COMPLETED),
                bookings,
                statisticsRepository.findVisibleRatingRows(tutorUserId, period.from(), period.to()),
                offeredSlots,
                tutorSubjectRepository.findByTutorProfileId(tutorProfileId),
                activeClaimIdByBooking(bookings));

        log.info("Statistics computed for tutor {} ({} {} - {})",
                tutorUserId, period.type(), period.from(), period.to());
        return calculator.calculate(input);
    }

    /** Reclamo abierto de cada reserva con fondos retenidos; si hay varios, el más antiguo (el que abrió la disputa). */
    private Map<UUID, UUID> activeClaimIdByBooking(List<TutorBookingRow> bookings) {
        List<UUID> withheldBookingIds = bookings.stream()
                .filter(row -> row.fundsStatus() == FundsStatus.SUSPENDED_BY_CLAIM)
                .map(TutorBookingRow::bookingId)
                .toList();
        if (withheldBookingIds.isEmpty()) {
            return Map.of();
        }
        Map<UUID, UUID> claimIdByBooking = new HashMap<>();
        for (ActiveClaimRow claim : statisticsRepository.findActiveClaimRows(
                withheldBookingIds, ClaimStatus.ACTIVE_STATUSES)) {
            claimIdByBooking.putIfAbsent(claim.bookingId(), claim.claimId());
        }
        return claimIdByBooking;
    }
}
