package com.knowlink.api.ratings.data.enums;

public enum RatingStatus {
    NOT_AVAILABLE,
    PENDING,
    SUBMITTED,

    /**
     * @deprecated no se emite; se retira en una historia posterior.
     */
    @Deprecated
    EXPIRED
}
