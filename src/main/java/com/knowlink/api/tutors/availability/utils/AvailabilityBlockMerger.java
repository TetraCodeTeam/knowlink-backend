package com.knowlink.api.tutors.availability.utils;

import com.knowlink.api.tutors.availability.controllers.requests.AvailabilityBlockRequest;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public final class AvailabilityBlockMerger {

    private AvailabilityBlockMerger() {
    }

    // Combina bloques contiguos del mismo día en un único período continuo —
    // ej. 16:30-17:00 + 17:00-17:30 -> 16:30-17:30 — para no guardar como
    // fragmentos independientes lo que en la práctica es un solo bloque
    // ininterrumpido. Solo fusiona bloques con el mismo repeatWeekly: fusionar
    // uno que se repite con uno que no cambiaría silenciosamente el
    // comportamiento de repetición de alguno de los dos.
    public static List<AvailabilityBlockRequest> mergeContiguous(List<AvailabilityBlockRequest> blocks) {
        Map<LocalDate, List<AvailabilityBlockRequest>> byDate = blocks.stream()
                .collect(Collectors.groupingBy(block -> block.date()));

        List<AvailabilityBlockRequest> merged = new ArrayList<>();
        byDate.values().forEach(dayBlocks -> merged.addAll(mergeForDay(dayBlocks)));
        return merged;
    }

    private static List<AvailabilityBlockRequest> mergeForDay(List<AvailabilityBlockRequest> dayBlocks) {
        List<AvailabilityBlockRequest> sorted = dayBlocks.stream()
                .sorted(Comparator.comparing((AvailabilityBlockRequest block) -> block.startTime()))
                .toList();

        List<AvailabilityBlockRequest> result = new ArrayList<>();
        AvailabilityBlockRequest current = null;

        for (AvailabilityBlockRequest block : sorted) {
            if (current == null) {
                current = block;
                continue;
            }

            boolean isContiguous = block.startTime().equals(current.endTime());
            boolean sameRepeatPolicy = block.repeatWeekly().equals(current.repeatWeekly());

            if (isContiguous && sameRepeatPolicy) {
                current = new AvailabilityBlockRequest(
                        current.date(), current.startTime(), block.endTime(), current.repeatWeekly());
            } else {
                result.add(current);
                current = block;
            }
        }

        if (current != null) {
            result.add(current);
        }

        return result;
    }
}