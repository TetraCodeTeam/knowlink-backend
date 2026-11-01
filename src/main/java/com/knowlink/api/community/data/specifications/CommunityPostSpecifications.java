package com.knowlink.api.community.data.specifications;

import com.knowlink.api.community.data.enums.PostStatus;
import com.knowlink.api.community.data.enums.PostType;
import com.knowlink.api.community.data.filters.CommunityFeedFilter;
import com.knowlink.api.community.data.models.CommunityPost;
import com.knowlink.api.tutors.data.models.Subject;
import jakarta.persistence.criteria.Path;
import org.springframework.data.jpa.domain.Specification;

import java.time.LocalDateTime;

/**
 * Especificaciones JPA para filtrar el feed de la comunidad (US-59). {@link #fromFilter} arma la consulta
 * combinando con AND solo los criterios presentes, igual que {@code TutorSearchSpecifications}.
 */
public final class CommunityPostSpecifications {

    private CommunityPostSpecifications() {
    }

    /**
     * @param createdFrom inicio (inclusive) de la ventana de tiempo ya resuelta por el servicio; {@code null} = sin límite
     */
    public static Specification<CommunityPost> fromFilter(CommunityFeedFilter filter, LocalDateTime createdFrom) {
        Specification<CommunityPost> spec = notDeleted();
        if (filter.type() != null) {
            spec = spec.and(hasType(filter.type()));
        }
        if (createdFrom != null) {
            spec = spec.and(createdFrom(createdFrom));
        }
        if (filter.careerId() != null) {
            spec = spec.and(inCareer(filter));
        }
        if (filter.hasSubjects()) {
            spec = spec.and((root, cq, cb) -> root.get("subject").get("subjectId").in(filter.subjectIds()));
        }
        if (filter.status() != null) {
            spec = spec.and(hasStatus(filter.status()));
        }
        return spec;
    }

    private static Specification<CommunityPost> notDeleted() {
        return (root, cq, cb) -> cb.isNull(root.get("deletedAt"));
    }

    private static Specification<CommunityPost> hasType(PostType type) {
        return (root, cq, cb) -> cb.equal(root.get("type"), type);
    }

    private static Specification<CommunityPost> createdFrom(LocalDateTime from) {
        return (root, cq, cb) -> cb.greaterThanOrEqualTo(root.<LocalDateTime>get("createdAt"), from);
    }

    /**
     * Las materias básicas se guardan asociadas a una carrera "placeholder" (ver SubjectSeeder), por lo que filtrar
     * solo por {@code subject.career} las dejaría fuera de todas las carreras menos una.
     */
    private static Specification<CommunityPost> inCareer(CommunityFeedFilter filter) {
        return (root, cq, cb) -> {
            Path<Subject> subject = root.get("subject");
            return cb.or(
                    cb.isTrue(subject.<Boolean>get("isBasic")),
                    cb.equal(subject.get("career").get("careerId"), filter.careerId()));
        };
    }

    /** Solo los pedidos de ayuda se resuelven: los aportes quedan siempre OPEN y no son "preguntas abiertas". */
    private static Specification<CommunityPost> hasStatus(PostStatus status) {
        return (root, cq, cb) -> status == PostStatus.OPEN
                ? cb.and(cb.equal(root.get("status"), PostStatus.OPEN),
                        cb.equal(root.get("type"), PostType.HELP_REQUEST))
                : cb.equal(root.get("status"), status);
    }
}
