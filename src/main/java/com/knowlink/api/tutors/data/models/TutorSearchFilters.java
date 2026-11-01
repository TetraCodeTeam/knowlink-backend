package com.knowlink.api.tutors.data.models;

import com.knowlink.api.tutors.data.enums.CompensationType;
import com.knowlink.api.tutors.data.enums.Modality;
import com.knowlink.api.tutors.data.enums.TimeFrame;

import java.time.DayOfWeek;
import java.util.Set;

/**
 * Filtros opcionales combinables con AND para la búsqueda de tutores (US-49).
 * Un record con todos los campos null equivale a la búsqueda base de US-06A
 * (sin filtros aplicados).
 *
 * Dentro de un mismo filtro multivaluado ({@code daysOfWeek}) los valores se
 * combinan con OR; entre filtros distintos, con AND.
 */
public record TutorSearchFilters(
        Modality modality,
        CompensationType compensation,
        Set<DayOfWeek> daysOfWeek,
        TimeFrame timeFrame,
        Boolean verifiedOnly,
        Double minRating
) {

    public TutorSearchFilters {
        daysOfWeek = daysOfWeek == null ? Set.of() : Set.copyOf(daysOfWeek);
    }

    public boolean hasAny() {
        return hasModality() || hasCompensation() || hasAvailability()
                || Boolean.TRUE.equals(verifiedOnly) || hasMinRating();
    }

    public boolean hasModality() {
        return modality != null;
    }

    public boolean hasCompensation() {
        return compensation != null;
    }

    public boolean hasAvailability() {
        return (daysOfWeek != null && !daysOfWeek.isEmpty()) || timeFrame != null;
    }

    public boolean hasMinRating() {
        return minRating != null;
    }
}
