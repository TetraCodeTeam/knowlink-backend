package com.knowlink.api.tutors.availability.validations;

import com.knowlink.api.exceptions.custom_exceptions.ValidationException;
import com.knowlink.api.tutors.availability.controllers.requests.AvailabilityBlockRequest;
import com.knowlink.api.tutors.availability.data.enums.BookingStatusGroups;
import com.knowlink.api.tutors.repositories.IBookingRepository;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AvailabilityBlockValidationServiceImpl implements IAvailabilityBlockValidationService {

    private final IBookingRepository bookingRepository;

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
    public void validateNoActiveBookingsInRange(UUID tutorUserId, LocalDate from, LocalDate to) {
        if (bookingRepository.existsActiveBookingInRange(tutorUserId, from, to, BookingStatusGroups.ACTIVE)) {
            throw new ValidationException(
                    "No es posible reemplazar la disponibilidad: hay reservas activas en el rango indicado.");
        }
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