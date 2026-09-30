package com.knowlink.api.tutors.availability.validations;

import com.knowlink.api.exceptions.custom_exceptions.ValidationException;
import com.knowlink.api.tutors.availability.controllers.requests.AvailabilityBlockRequest;
import com.knowlink.api.tutors.availability.data.models.AvailabilityBlock;
import com.knowlink.api.tutors.availability.repositories.IAvailabilityBlockRepository;
import com.knowlink.api.tutors.availability.data.enums.BookingStatusGroups;
import com.knowlink.api.bookings.repositories.IBookingRepository;
import com.knowlink.api.shared.utils.PastTimeUtil;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AvailabilityBlockValidationServiceImpl implements IAvailabilityBlockValidationService {

    private final IBookingRepository bookingRepository;
    private final IAvailabilityBlockRepository availabilityBlockRepository;

    @Override
    public void validateWeekRequest(LocalDate weekStart, LocalDate weekEnd, List<AvailabilityBlockRequest> blocks) {
        if (weekStart.isAfter(weekEnd)) {
            throw new ValidationException("weekStart debe ser anterior o igual a weekEnd.");
        }
        if (blocks == null) {
            throw new ValidationException("Se requiere la lista de bloques.");
        }
        if (blocks.stream().anyMatch(b -> b == null
                || b.date() == null
                || b.date().isBefore(weekStart)
                || b.date().isAfter(weekEnd))) {
            throw new ValidationException("Todos los bloques deben estar dentro del rango de la semana indicada.");
        }
    }

    @Override
    public void validateBlocks(List<AvailabilityBlockRequest> blocks) {
        for (AvailabilityBlockRequest block : blocks) {
            if (!block.endTime().isAfter(block.startTime())) {
                throw new ValidationException("El horario de fin debe ser posterior al horario de inicio.");
            }
        }
        validateNoOverlaps(blocks);
    }

    @Override
    public Set<LocalDate> resolveProtectedDates(
            UUID tutorUserId, UUID tutorProfileId, List<AvailabilityBlockRequest> blocks,
            LocalDate weekStart, LocalDate weekEnd, LocalDateTime now) {

        List<LocalDate> bookedDates = bookingRepository.findActiveUpcomingBookingDatesInRange(
                tutorUserId, weekStart, weekEnd, BookingStatusGroups.ACTIVE, now.toLocalDate(), now.toLocalTime());

        if (bookedDates.isEmpty()) {
            return Set.of();
        }

        List<AvailabilityBlock> existingBlocks = availabilityBlockRepository.findRawInRange(
                tutorProfileId, weekStart, weekEnd);

        Map<LocalDate, Set<String>> existingByDate = existingBlocks.stream()
                // Ignora bloques ya completamente vencidos: el frontend nunca los
                // muestra (findInRange los filtra), así que nunca pueden formar
                // parte de lo que el tutor reenvía — compararlos genera falsos
                // positivos, como pasó con un bloque de una reserva ya finalizada.
                .filter(block -> !PastTimeUtil.isFullyPast(block.getDate(), block.getEndTime(), now))
                .collect(Collectors.groupingBy(
                        AvailabilityBlock::getDate,
                        Collectors.mapping(AvailabilityBlockValidationServiceImpl::signatureOf, Collectors.toSet())));

        Map<LocalDate, Set<String>> requestedByDate = blocks.stream()
                .collect(Collectors.groupingBy(
                        AvailabilityBlockRequest::date,
                        Collectors.mapping(AvailabilityBlockValidationServiceImpl::signatureOf, Collectors.toSet())));

        Set<LocalDate> protectedDates = new HashSet<>();

        for (LocalDate bookedDate : bookedDates) {
            Set<String> existing = existingByDate.getOrDefault(bookedDate, Set.of());
            Set<String> requested = requestedByDate.getOrDefault(bookedDate, Set.of());

            if (!existing.equals(requested)) {
                throw new ValidationException(String.format(
                        "No es posible modificar el %s: ese día tiene reservas activas.", bookedDate));
            }

            protectedDates.add(bookedDate);
        }

        return protectedDates;
    }

    private static String signatureOf(AvailabilityBlock block) {
        return signatureOf(block.getStartTime(), block.getEndTime(), block.isRepeatWeekly());
    }

    private static String signatureOf(AvailabilityBlockRequest request) {
        return signatureOf(request.startTime(), request.endTime(), Boolean.TRUE.equals(request.repeatWeekly()));
    }

    private static String signatureOf(LocalTime start, LocalTime end, boolean repeatWeekly) {
        return start + "|" + end + "|" + repeatWeekly;
    }

    private void validateNoOverlaps(List<AvailabilityBlockRequest> blocks) {
        Map<LocalDate, List<AvailabilityBlockRequest>> byDate = blocks.stream()
                .collect(Collectors.groupingBy(AvailabilityBlockRequest::date));

        for (List<AvailabilityBlockRequest> dayBlocks : byDate.values()) {
            List<AvailabilityBlockRequest> sorted = dayBlocks.stream()
                    .sorted(Comparator.comparing(AvailabilityBlockRequest::startTime))
                    .toList();

            for (int i = 1; i < sorted.size(); i++) {
                AvailabilityBlockRequest previous = sorted.get(i - 1);
                AvailabilityBlockRequest current = sorted.get(i);

                if (current.startTime().isBefore(previous.endTime())) {
                    throw new ValidationException("Este bloque se superpone con uno ya existente.");
                }
            }
        }
    }
}