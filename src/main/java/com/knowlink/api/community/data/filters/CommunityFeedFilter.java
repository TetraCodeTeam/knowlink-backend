package com.knowlink.api.community.data.filters;

import com.knowlink.api.community.data.enums.FeedPeriod;
import com.knowlink.api.community.data.enums.PostStatus;
import com.knowlink.api.community.data.enums.PostType;

import java.util.List;
import java.util.UUID;

/**
 * Criterios del feed de la comunidad (US-59). Todos son opcionales y se combinan con AND.
 *
 * @param type       tipo de publicación; {@code null} = dudas y aportes
 * @param period     ventana de tiempo; {@code null} = histórico
 * @param careerId   carrera del catálogo; incluye las materias básicas, que son comunes a todas las carreras
 * @param subjectIds una o más materias; vacío o {@code null} = todas
 * @param status     {@code OPEN} = solo preguntas abiertas (los aportes no tienen estado); {@code RESOLVED} = resueltas
 */
public record CommunityFeedFilter(
        PostType type,
        FeedPeriod period,
        UUID careerId,
        List<UUID> subjectIds,
        PostStatus status
) {

    public CommunityFeedFilter withType(PostType newType) {
        return new CommunityFeedFilter(newType, period, careerId, subjectIds, status);
    }

    public FeedPeriod periodOrAll() {
        return period == null ? FeedPeriod.ALL : period;
    }

    public boolean hasSubjects() {
        return subjectIds != null && !subjectIds.isEmpty();
    }
}
