package com.knowlink.api.tutors.data.specifications;

import com.knowlink.api.tutors.availability.data.models.AvailabilityBlock;
import com.knowlink.api.tutors.data.enums.CompensationType;
import com.knowlink.api.tutors.data.enums.Modality;
import com.knowlink.api.tutors.data.enums.TimeFrame;
import com.knowlink.api.tutors.data.models.Subject;
import com.knowlink.api.tutors.data.models.TutorProfile;
import com.knowlink.api.tutors.data.models.TutorSubject;
import jakarta.persistence.criteria.Path;
import org.springframework.data.jpa.domain.Specification;

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.Locale;
import java.util.Set;

/**
 * Especificaciones JPA para la búsqueda de tutores (US-49). Cada método
 * devuelve un {@code Specification<TutorSubject>} opcional que se combina con
 * AND para los filtros presentes en el request, evitando construir queries a
 * mano con concatenación de strings.
 *
 * Semántica:
 * - modality VIRTUAL/IN_PERSON matchea además subjects con modality BOTH.
 * - verified y minRating se evalúan sobre el tutor del TutorSubject matcheado
 *   por materia, por lo que "verificado" cruza contra la materia buscada.
 * - Disponibilidad horaria NO se resuelve en SQL: extraer el día de la semana
 *   de una fecha requiere la función DAYOFWEEK, que devuelve base distinta en
 *   MySQL (1=domingo) vs H2 (1=lunes). Para no acoplar el filtro a un dialecto
 *   puntual, se aplica en memoria sobre los bloques disponibles (ver
 *   {@link #matchesAvailability(AvailabilityBlock, DayOfWeek, TimeFrame)}),
 *   siempre con la misma semántica en tests (H2) y en producción (MySQL).
 */
public final class TutorSearchSpecifications {

    private TutorSearchSpecifications() {
    }

    public static Specification<TutorSubject> subjectNameContains(String query) {
        if (query == null || query.isBlank()) {
            return Specification.where(null);
        }
        return (root, cq, cb) -> {
            Path<Subject> subject = root.get("subject");
            String like = "%" + query.toLowerCase(Locale.ROOT) + "%";
            return cb.like(cb.lower(subject.<String>get("name")), like);
        };
    }

    public static Specification<TutorSubject> modalityIn(Modality modality) {
        if (modality == null) {
            return Specification.where(null);
        }
        return (root, cq, cb) -> {
            if (modality == Modality.BOTH) {
                return cb.equal(root.get("modality"), Modality.BOTH);
            }
            return root.get("modality").in(Set.of(modality, Modality.BOTH));
        };
    }

    public static Specification<TutorSubject> compensationIn(CompensationType compensation) {
        if (compensation == null) {
            return Specification.where(null);
        }
        return (root, cq, cb) -> cb.equal(root.get("compensationType"), compensation);
    }

    public static Specification<TutorSubject> tutorVerified() {
        return (root, cq, cb) -> {
            Path<TutorProfile> tutor = root.get("tutorProfile");
            return cb.isTrue(tutor.<Boolean>get("verified"));
        };
    }

    public static Specification<TutorSubject> minAverageRating(double minRating) {
        return (root, cq, cb) -> {
            Path<TutorProfile> tutor = root.get("tutorProfile");
            return cb.greaterThanOrEqualTo(tutor.<Double>get("averageRating"), minRating);
        };
    }

    public static boolean matchesModality(Modality subjectModality, Modality filter) {
        if (filter == null) {
            return true;
        }
        if (subjectModality == Modality.BOTH) {
            return true;
        }
        return subjectModality == filter;
    }

    public static boolean matchesCompensation(CompensationType subjectCompensation, CompensationType filter) {
        return filter == null || subjectCompensation == filter;
    }

    /**
     * Determina si un bloque de disponibilidad satisface los filtros de día y
     * franja (AND). Los filtros null son neutrales.
     */
    public static boolean matchesAvailability(AvailabilityBlock block, DayOfWeek dayOfWeek, TimeFrame timeFrame) {
        if (dayOfWeek != null && block.getDate().getDayOfWeek() != dayOfWeek) {
            return false;
        }
        if (timeFrame != null && !timingOverlaps(block.getStartTime(), block.getEndTime(), timeFrame)) {
            return false;
        }
        return true;
    }

    public static boolean timingOverlaps(LocalTime blockStart, LocalTime blockEnd, TimeFrame frame) {
        return blockStart.isBefore(frame.getEnd()) && blockEnd.isAfter(frame.getStart());
    }
}