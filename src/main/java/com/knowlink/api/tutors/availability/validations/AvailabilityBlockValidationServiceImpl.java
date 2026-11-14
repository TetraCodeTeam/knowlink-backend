package com.knowlink.api.tutors.availability.validations;

import com.knowlink.api.exceptions.custom_exceptions.ValidationException;
import com.knowlink.api.shared.utils.EndOfDay;
import com.knowlink.api.shared.utils.PastTimeUtil;
import com.knowlink.api.tutors.availability.controllers.requests.AvailabilityBlockRequest;
import com.knowlink.api.tutors.availability.data.models.AvailabilityBlock;

import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class AvailabilityBlockValidationServiceImpl implements IAvailabilityBlockValidationService {

    private static final DateTimeFormatter DAY_FORMAT = DateTimeFormatter.ofPattern("dd/MM");
    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm");

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

    // Antes se rechazaba todo el guardado si había CUALQUIER reserva activa en la semana, aunque fuera
    // de otro día, de otro horario o ya terminada. Ahora solo se protege lo que de verdad depende de
    // una reserva: los bloques que la respaldan. El resto de la semana se edita con normalidad.
    @Override
    public List<AvailabilityBlockRequest> excludeLockedBlocks(
            List<AvailabilityBlock> lockedBlocks, List<AvailabilityBlockRequest> requested, LocalDateTime now) {
        List<AvailabilityBlockRequest> editable = new ArrayList<>(requested);

        for (AvailabilityBlock locked : lockedBlocks) {
            // El editor recibe recortado el inicio de un bloque de hoy que ya empezó (ver
            // PastTimeUtil.effectiveStartTime) y lo reenvía así; también se acepta el inicio guardado.
            LocalTime shownStart = PastTimeUtil.effectiveStartTime(locked.getDate(), locked.getStartTime(), now);

            boolean returnedUnchanged = editable.removeIf(request -> isSameBlock(request, locked, shownStart));
            if (!returnedUnchanged) {
                throw new ValidationException(String.format(
                        "No es posible modificar ni quitar el bloque del %s de %s a %s: tiene reservas asociadas.",
                        locked.getDate().format(DAY_FORMAT),
                        shownStart.format(TIME_FORMAT),
                        EndOfDay.isEndOfDay(locked.getEndTime())
                                ? "24:00"
                                : locked.getEndTime().format(TIME_FORMAT)));
            }
        }

        return editable;
    }

    private static boolean isSameBlock(AvailabilityBlockRequest request, AvailabilityBlock locked, LocalTime shownStart) {
        return request.date().equals(locked.getDate())
                && (sameTime(request.startTime(), shownStart) || sameTime(request.startTime(), locked.getStartTime()))
                && sameTime(request.endTime(), locked.getEndTime());
    }

    // Se compara al segundo: el inicio llega como "HH:mm:ss" y el fin de día se guarda con microsegundos.
    private static boolean sameTime(LocalTime first, LocalTime second) {
        return first.truncatedTo(ChronoUnit.SECONDS).equals(second.truncatedTo(ChronoUnit.SECONDS));
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