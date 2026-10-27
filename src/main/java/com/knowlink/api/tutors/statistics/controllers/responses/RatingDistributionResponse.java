package com.knowlink.api.tutors.statistics.controllers.responses;

import java.util.List;

/** {@code buckets} siempre trae las 5 estrellas, de 5 a 1; {@code average} es null sin reseñas. */
public record RatingDistributionResponse(Double average, int total, List<RatingBucketResponse> buckets) {
}
