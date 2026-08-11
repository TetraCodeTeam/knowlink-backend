package com.knowlink.api.tutors.data.models;

import com.knowlink.api.tutors.data.enums.CompensationType;
import com.knowlink.api.tutors.data.enums.Modality;
import com.knowlink.api.tutors.data.enums.TimeFrame;

import java.time.DayOfWeek;

/**
 * Filtros opcionales combinables con AND para la búsqueda de tutores (US-49).
 * Un record con todos los campos null equivale a la búsqueda base de US-06A
 * (sin filtros aplicados).
 */
public record TutorSearchFilters(
        Modality modality,
        CompensationType compensation,
        DayOfWeek dayOfWeek,
        TimeFrame timeFrame,
        Boolean verifiedOnly,
        Double minRating
) {

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
        return dayOfWeek != null || timeFrame != null;
    }

    public boolean hasMinRating() {
        return minRating != null;
    }
}